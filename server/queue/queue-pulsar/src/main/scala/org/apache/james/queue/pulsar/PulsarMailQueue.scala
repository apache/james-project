/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ************************************************************** */

package org.apache.james.queue.pulsar

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink, Source, SourceQueueWithComplete, StreamConverters}
import akka.stream.{Attributes, OverflowStrategy}
import akka.util.Timeout
import akka.{Done, NotUsed}
import com.sksamuel.pulsar4s._
import com.sksamuel.pulsar4s.akka.streams
import com.sksamuel.pulsar4s.akka.streams.{CommittableMessage, Control}
import org.apache.james.backends.pulsar.{PulsarClients, PulsarReader}
import org.apache.james.blob.api.{BlobId, ObjectNotFoundException, Store}
import org.apache.james.blob.mail.MimeMessagePartsId
import org.apache.james.core.{MailAddress, MaybeSender}
import org.apache.james.metrics.api.{GaugeRegistry, MetricFactory}
import org.apache.james.queue.api.MailQueue.MailQueueItem.CompletionStatus
import org.apache.james.queue.api.MailQueue._
import org.apache.james.queue.api._
import org.apache.james.queue.pulsar.EnqueueId.EnqueueId
import org.apache.james.server.core.MailImpl
import org.apache.mailet._
import org.apache.pulsar.client.admin.PulsarAdminException.NotFoundException
import org.apache.pulsar.client.api.{Schema, SubscriptionInitialPosition, SubscriptionType}
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import play.api.libs.json._

import java.time.{Instant, ZonedDateTime, Duration => JavaDuration}
import java.util.concurrent.TimeUnit
import java.util.{Date, UUID}
import javax.mail.MessagingException
import javax.mail.internet.MimeMessage
import scala.concurrent._
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._
import scala.math.Ordered.orderingToOrdered
import scala.util.Failure

private[pulsar] object serializers {
  implicit val headerFormat: Format[Header] = Json.format[Header]
  implicit val enqueueIdFormat: Format[EnqueueId] = new Format[EnqueueId] {
    override def writes(o: EnqueueId): JsValue = JsString(o.value)

    override def reads(json: JsValue): JsResult[EnqueueId] =
      json.validate[String].map(EnqueueId.apply).flatMap(_.fold(JsError.apply, JsSuccess(_)))
  }
  implicit val mailMetadataFormat: Format[MailMetadata] = Json.format[MailMetadata]
}

private[pulsar] object schemas {
  implicit val schema: Schema[String] = Schema.STRING
}

/**
 * In order to implement removal of mails from the queue, `PulsarMailQueue` makes use of a topic
 * in which some filters are pushed. That way, all instances of `PulsarMailQueue` in a cluster
 * eventually start dropping mails matching filters, effectively removing them from mail processing.
 *
 * The filtering is handled by a `FilterStage` Actor which maintains a set of active filters published by
 * the `remove` method. It is responsible for dropping filters that can no longer match any message, and providing
 * a consistent view of the messages that will be processed to the `browse` method.
 *
 * A filter cannot remove messages that are enqueued after the call to the `remove` method.
 */
class PulsarMailQueue(
                       config: PulsarMailQueueConfiguration,
                       pulsar: PulsarClients,
                       blobIdFactory: BlobId.Factory,
                       mimeMessageStore: Store[MimeMessage, MimeMessagePartsId],
                       mailQueueItemDecoratorFactory: MailQueueItemDecoratorFactory,
                       metricFactory: MetricFactory,
                       gaugeRegistry: GaugeRegistry,
                       system: ActorSystem
                     ) extends MailQueue with ManageableMailQueue {

  import schemas._
  import serializers._

  type MessageAsJson = String

  private val logger = LoggerFactory.getLogger(this.getClass)

  private val awaitTimeout = 10.seconds

  gaugeRegistry.register(QUEUE_SIZE_METRIC_NAME_PREFIX + config.name, () => getSize)
  private val dequeueMetrics = metricFactory.generate(DEQUEUED_METRIC_NAME_PREFIX + config.name.asString)
  private val enqueueMetric = metricFactory.generate(ENQUEUED_METRIC_NAME_PREFIX + config.name.asString)

  private implicit val implicitSystem: ActorSystem = system
  private implicit val ec: ExecutionContextExecutor = system.dispatcher
  private implicit val implicitBlobIdFactory: BlobId.Factory = blobIdFactory
  private implicit val client: PulsarAsyncClient = pulsar.asyncClient
  private val admin = pulsar.adminClient

  private val outTopic = Topic(s"persistent://${config.pulsar.namespace.asString}/James-${config.name.asString()}")
  private val scheduledTopic = Topic(s"persistent://${config.pulsar.namespace.asString}/${config.name.asString()}-scheduled")
  private val filterTopic = Topic(s"persistent://${config.pulsar.namespace.asString}/pmq-filter-${config.name.asString()}")
  private val filterScheduledTopic = Topic(s"persistent://${config.pulsar.namespace.asString}/pmq-filter-scheduled-${config.name.asString()}")
  private val subscription = Subscription("subscription-" + config.name.asString())
  private val scheduledSubscription = Subscription("scheduled-subscription-" + config.name.asString())

  private val outTopicProducer = client.producer(ProducerConfig(outTopic, enableBatching = Some(false)))
  private val scheduledTopicProducer = client.producer(ProducerConfig(scheduledTopic, enableBatching = Some(false)))

  private val filterProducer = client.producer(ProducerConfig(filterTopic, enableBatching = Some(false)))
  private val filterScheduledProducer = client.producer(ProducerConfig(filterScheduledTopic, enableBatching = Some(false)))

  private def completingSinkOf[U](producer: Producer[U]): Sink[(ProducerMessage[U], Promise[Done]), NotUsed] =
    Flow.fromFunction[(ProducerMessage[U], Promise[Done]), Unit] {
      case (message, promise) => producer
        .sendAsync(message)
        .onComplete(enqueued => promise.complete(enqueued.map(_ => Done)))
    }
      .to(Sink.ignore)

  private def sinkOf[U](producer: Producer[U]): Sink[ProducerMessage[U], NotUsed] =
    Flow.fromFunction[ProducerMessage[U], Unit](producer.sendAsync(_)).to(Sink.ignore)

  def debugLogger[T](loggerName: String): Flow[T, T, NotUsed] =
    Flow.apply[T]
      .log(loggerName)
      .addAttributes(
        Attributes.logLevels(
          onElement = Attributes.LogLevels.Debug,
          onFinish = Attributes.LogLevels.Debug,
          onFailure = Attributes.LogLevels.Error)
      )

  private val saveMail = (mail: Mail, duration: Duration, enqueued: Promise[Done]) =>
    Source.fromPublisher(saveMimeMessage(mail.getMessage))
      .map { partsId =>
        val mailMetadata = MailMetadata.of(EnqueueId.generate(), mail, partsId)
        val payload = Json.stringify(Json.toJson(mailMetadata))
        (payload, duration, enqueued)
      }

  private val buildProducerMessage =
    Flow.fromFunction[(MessageAsJson, Duration, Promise[Done]), (ProducerMessage[MessageAsJson], Promise[Done])] {
      case (payload, duration, enqueued) =>
        duration match {
          case _: Duration.Infinite =>
            (ProducerMessage(payload) -> enqueued)
          case duration: FiniteDuration =>
            val deliverAt = ZonedDateTime.now().plus(duration.toJava).toInstant
            (DefaultProducerMessage(key = None, value = payload, deliverAt = Some(deliverAt.toEpochMilli), eventTime = Some(EventTime(deliverAt.toEpochMilli))) -> enqueued)
        }
    }

  private def isScheduled(producerMessage: ProducerMessage[_]) = producerMessage.deliverAt.isDefined

  /**
   * All messages are first enqueued here
   */
  private val enqueueFlow: RunnableGraph[SourceQueueWithComplete[(Mail, Duration, Promise[Done])]] =
    Source
      .queue[(Mail, Duration, Promise[Done])](config.enqueueBufferSize, OverflowStrategy.backpressure, config.maxEnqueueConcurrency)
      .flatMapConcat(saveMail.tupled)
      .via(buildProducerMessage)
      .wireTap(_ => enqueueMetric.increment())
      .via(debugLogger("enqueue"))
      .divertTo(completingSinkOf(scheduledTopicProducer), { case (message, _) => isScheduled(message) })
      .to(completingSinkOf(outTopicProducer))

  /**
   * Scheduled messages go through this source when delay expires
   */
  private val requeueFlow: RunnableGraph[SourceQueueWithComplete[ProducerMessage[MessageAsJson]]] = Source
    .queue[ProducerMessage[MessageAsJson]](config.requeueBufferSize, OverflowStrategy.backpressure)
    .via(debugLogger("requeue"))
    .to(sinkOf(outTopicProducer))

  private def buildConsumer(subscription: Subscription, topic: Topic): Consumer[String] =
    client.consumer(
      ConsumerConfig(
        subscriptionName = subscription,
        topics = Seq(topic),
        subscriptionType = Some(SubscriptionType.Shared),
        subscriptionInitialPosition = Some(SubscriptionInitialPosition.Earliest),
        negativeAckRedeliveryDelay = Some(1.second)
      )
    )

  def consumer(): Consumer[String] = buildConsumer(subscription, outTopic)

  def scheduledConsumer(): Consumer[String] = buildConsumer(scheduledSubscription, scheduledTopic)


  private val filterScheduledStage: ActorRef = system.actorOf(FilterStage.props)
  private val requeueMessage = Flow.apply[CommittableMessage[String]]
    .via(filteringFlow(filterScheduledStage))
    .flatMapConcat { case (_, _, message) => Source.future(requeue.offer(ProducerMessage(message.message.value)).map(_ => message)) }
    .flatMapConcat(message => Source.future(message.ack(cumulative = false)))
    .toMat(Sink.ignore)(Keep.none)

  private val requeueScheduledMessages =
    streams.committableSource(scheduledConsumer)
      .toMat(requeueMessage)(Keep.left) //use of toMat to keep reference of Control which would be lost by direct usage of flatMapConcat

  private val filterStage: ActorRef = system.actorOf(FilterStage.props)
  private val counter: Sink[Any, Future[Int]] =
    Sink.fold[Int, Any](0)((acc, _) => acc + 1)

  private val dequeueFlow: RunnableGraph[(Control, Publisher[MailQueueItem])] = {
    implicit val timeout: Timeout = Timeout(1, TimeUnit.SECONDS)
    streams.committableSource(consumer)
      .via(filteringFlow(filterStage))
      .map { case (mail, partsId, message) => new PulsarMailQueueItem(mail, partsId, message) }
      .map(mailQueueItemDecoratorFactory.decorate(_, config.name))
      .alsoTo(counter)
      // akka streams virtual publisher handles a subscription timeout to the
      // exposed publisher which will terminate the stream if the timeout is not
      // honored. To do so, the akka stream implementation actually subscribes to
      // the publisher.
      // asPublisher thus requires either :
      // * fanout=true
      // * or to disable the subscription timeout mechanism
      // see akka.stream.impl.VirtualPublisher.onSubscriptionTimeout

      .via(debugLogger("dequeueFlow"))
      .toMat(Sink.asPublisher[MailQueue.MailQueueItem](true).withAttributes(Attributes.inputBuffer(initial = 1, max = 1)))(Keep.both)
  }

  private def filteringFlow(filterActor: ActorRef) = {
    implicit val timeout: Timeout = Timeout(1, TimeUnit.SECONDS)
    Flow.apply[CommittableMessage[String]].map(message =>
      (Json.fromJson[MailMetadata](Json.parse(message.message.value)).get,
        message)
    ).ask[(Option[MailMetadata], Option[MimeMessagePartsId], CommittableMessage[String])](filterActor)
      .flatMapConcat {
        case (None, Some(partsId), committableMessage) =>
          Source.lazyFuture(() => committableMessage.ack())
            .flatMapConcat(_ =>
              deleteMimeMessage(partsId)
                .flatMapConcat(_ => Source.empty)
            )
        case (Some(metadata), _, committableMessage) =>
          val partsId = metadata.partsId
          Source
            .fromPublisher(readMimeMessage(partsId))
            .collect { case Some(message) => message }
            .map(message => (readMail(metadata, message), partsId, committableMessage))
      }
  }


  class PulsarMailQueueItem(mail: Mail, partsId: MimeMessagePartsId, message: CommittableMessage[String]) extends MailQueueItem {
    override val getMail: Mail = mail

    override def done(success: CompletionStatus): Unit = success match {
      case CompletionStatus.SUCCESS =>
        dequeueMetrics.increment()
        Await.ready(message.ack(cumulative = false), awaitTimeout)
        val eventualDone = deleteMimeMessage(partsId).run()

        eventualDone.onComplete {
          case Failure(e) => logger.error("Failed to delete parts {} for mail {}", partsId, mail.getName(), e)
          case _ => logger.trace("Deleted parts {} for mail {}", partsId, mail.getName())
        }
      case CompletionStatus.RETRY =>
        Await.ready(message.nack(), awaitTimeout)
      case CompletionStatus.REJECT =>
        Await.ready(message.nack(), awaitTimeout)
    }
  }

  def registerDequeueSubscription(): Unit = consumer().close()

  def registerScheduledSubscription(): Unit = scheduledConsumer().close()

  // make sure the subscription exists on the server so we can read the size
  registerDequeueSubscription()
  registerScheduledSubscription()
  // the lazy makes the process testable by enforcing some level of determinism for tests
  private lazy val (dequeueControl: Control, dequeuePublisher: Publisher[MailQueueItem], scheduledConsumerControl: Control) = startDequeuing()
  private val enqueue: SourceQueueWithComplete[(Mail, Duration, Promise[Done])] = enqueueFlow.run()
  private val requeue: SourceQueueWithComplete[ProducerMessage[MessageAsJson]] = requeueFlow.run()
  private val filtersCommandFlowControl: Control =
    filtersCommandFlow(
      filterTopic,
      Subscription("filter-subscription-" + config.name.asString() + "-" + UUID.randomUUID().toString),
      filterStage
    ).run()
  private val scheduledFiltersCommandFlowControl: Control =
    filtersCommandFlow(
      filterScheduledTopic,
      Subscription("filter-scheduled-subscription-" + config.name.asString() + "-" + UUID.randomUUID().toString),
      filterScheduledStage
    ).run()

  private def startDequeuing() = {
    val (dequeueControl: Control, dequeuePublisher: Publisher[MailQueueItem]) = dequeueFlow.run()
    val scheduledConsumerControl: Control = requeueScheduledMessages.run()
    (dequeueControl, dequeuePublisher, scheduledConsumerControl)
  }

  /**
   * For now, filtersCommandFlow always rereads the whole topic from the start by using a random subscription name.
   * Filters are never removed from the topic.
   * This means that the FilterStage will get slower to start as the number of filter increases, it will also consume
   * an increasing amount of RAM until the first mail is processed which will invalidate and purge the expired filters.
   *
   * @see [[FilterStage]]
   */
  private def filtersCommandFlow(topic: Topic, filterSubscription: Subscription, filteringStage: ActorRef) = {
    val logInvalidFilterPayload = Flow.apply[JsResult[Filter]]
      .collectType[JsError]
      .map(error => "unable to parse filter" + Json.prettyPrint(JsError.toJson(error)))
      .log("filterFlow")
      .addAttributes(Attributes.logLevels(onElement = Attributes.LogLevels.Error)).to(Sink.ignore)

    streams.source(() =>
      client.consumer(
        ConsumerConfig(
          subscriptionName = filterSubscription,
          topics = Seq(topic),
          subscriptionType = Some(SubscriptionType.Shared),
          subscriptionInitialPosition = Some(SubscriptionInitialPosition.Earliest),
        )
      )
    ).map(message => Json.fromJson[Filter](Json.parse(message.value)))
      .divertTo(logInvalidFilterPayload, when = _.isError)
      .map(_.get)
      .via(debugLogger("filterFlow"))
      .to(Sink.foreach(filter => filteringStage ! filter))
  }


  /**
   * @inheritdoc
   */
  override val getName: MailQueueName = config.name

  /**
   * @inheritdoc
   */
  override def enQueue(mail: Mail, delay: JavaDuration): Unit = syncEnqueue(mail, delay.toScala)

  /**
   * @inheritdoc
   */
  override def enQueue(mail: Mail): Unit = syncEnqueue(mail, Duration.Undefined)

  private def syncEnqueue(mail: Mail, delay: Duration): Unit = {
    metricFactory.decorateSupplierWithTimerMetric(
      ENQUEUED_TIMER_METRIC_NAME_PREFIX + config.name.asString,
      () => Await.result(internalEnqueue(mail, delay), awaitTimeout)
    )
  }

  /**
   * @inheritdoc
   */
  override def enqueueReactive(mail: Mail): Publisher[Void] = {
    metricFactory.decoratePublisherWithTimerMetric(
      ENQUEUED_TIMER_METRIC_NAME_PREFIX + config.name.asString,
      Source.lazyFuture(() => internalEnqueue(mail, Duration.Undefined)).runWith(Sink.asPublisher[Void](fanout = true))
    )
  }

  private def internalEnqueue(mail: Mail, delay: Duration) = {
    val enqueueCompletion = Promise[Done]()
    for {
      offer <- enqueue.offer((mail, delay, enqueueCompletion))
      enqueueCompleted <- enqueueCompletion.future
    } yield null
  }

  /**
   * @inheritdoc
   */
  override def deQueue(): Publisher[MailQueue.MailQueueItem] = dequeuePublisher

  /**
   * @inheritdoc
   */
  override def close(): Unit = {
    enqueue.complete()
    requeue.complete()
    dequeueControl.stop()
    scheduledConsumerControl.stop()
    filtersCommandFlowControl.stop()
    scheduledFiltersCommandFlowControl.stop()
  }

  /**
   * @inheritdoc
   */
  override def getSize: Long = getSize(outTopic, subscription) + getSize(scheduledTopic, scheduledSubscription)


  private def getSize(topic: Topic, subscription: Subscription): Long = {
    try {
      val subscriptions = admin.topics().getStats(topic.name).getSubscriptions
      val maybeStats = Option(subscriptions.get(subscription.name))
      maybeStats.map(_.getMsgBacklog).getOrElse(0)
    } catch {
      case _: NotFoundException => 0L
    }
  }

  /**
   * @inheritdoc
   */
  override def flush(): Long = {
    def lastScheduledMessageId(f: MessageId => Long): Long = lastMessage(scheduledTopic)
      .map(_.messageId)
      .fold(0L)(f)

    lastScheduledMessageId { lastMessageId =>
      val flushStart = Instant.now()

      def isScheduledAfterFlush(message: ConsumerMessage[String]) = Instant.ofEpochMilli(message.eventTime.value).isAfter(flushStart)

      def putMessageInOutTopic(message: ConsumerMessage[String]) = requeue.offer(ProducerMessage(message.value))

      //prevents normal deque flow of scheduled messages by moving the scheduled subscription to last know message
      admin.topics().resetCursor(scheduledTopic.name, scheduledSubscription.name, lastMessageId)

      val copy = read(
        ReaderConfig(scheduledTopic, startMessage = Message(MessageId.earliest), startMessageIdInclusive = true)
      )
        .filter(isScheduledAfterFlush) //avoid duplicate delivery of messages which are already handled by scheduledSubscription
        .filter(_.messageId.underlying <= lastMessageId.underlying) //stop copying message at lastMessageId
        .runFold(0L) { (counter, message) =>
          putMessageInOutTopic(message)
          counter + 1
        }
      Await.result(copy, Duration.Inf)
    }
  }

  private def read(config: ReaderConfig)(implicit executionContext: ExecutionContext): Source[ConsumerMessage[String], NotUsed] =
    Source.unfoldResourceAsync[ConsumerMessage[String], Reader[String]](
      create = () => {
        Future.successful(
          client.reader(
            config = config
          )
        )
      },
      read = reader => {
        if (reader.hasMessageAvailable) reader.nextAsync.map(Some(_))
        else Future.successful(None)
      },
      close = reader => reader.closeAsync.map(_ => Done))


  /**
   * @inheritdoc
   */
  override def clear(): Long = {
    val count = getSize()
    admin.topics().delete(outTopic.name, true)
    admin.topics().delete(scheduledTopic.name, true)
    count
  }

  private def lastMessage(topic: Topic): Option[ConsumerMessage[String]] = {
    val reader = client.reader(
      config = ReaderConfig(topic,
        startMessage = Message(MessageId.latest),
        startMessageIdInclusive = true)
    )

    if (reader.hasMessageAvailable)
      reader.next(awaitTimeout)
    else
      None
  }

  /**
   * Remove all mails from the queue that match
   *
   * The intent of remove is to allow operators to clear some emails from the mailqueue
   * in an emergency situation such as a DOS attempt or a configuration error which creates a mail loop
   * or a bounce loop.
   *
   * The chosen implementation is to insert a filter which will very quickly dequeue matching emails while continuing
   * to process normal traffic. This means that the count() method will not immediately account for the removed emails
   * but will eventually converge once all the matching emails in the mailqueue have been processed.
   *
   * We will use actor interop patterns from akka stream to interoperate with a filtering actor
   * https://doc.akka.io/docs/akka/current/stream/actor-interop.html
   *
   * @return 0 because this is an async and distributed process running on several nodes on the cluster
   */
  override def remove(`type`: ManageableMailQueue.Type, value: String): Long = {
    val maybeLastSequenceId = lastMessage(outTopic).map(_.sequenceId)
    val maybeLastScheduledSequenceId = lastMessage(scheduledTopic).map(_.sequenceId)

    val filter = buildFilter(`type`, value, maybeLastSequenceId)
    val scheduledFilter = buildFilter(`type`, value, maybeLastScheduledSequenceId)

    filter.foreach(publishFilter(filterProducer))
    scheduledFilter.foreach(publishFilter(filterScheduledProducer))

    0
  }

  private def buildFilter(`type`: ManageableMailQueue.Type, value: String, maybeLastSequenceId: Option[SequenceId]): Option[Filter] = {
    maybeLastSequenceId.map { lastSequenceId =>
      import ManageableMailQueue.Type
      `type` match {
        case Type.Sender =>
          Filter.BySender(value, lastSequenceId)
        case Type.Recipient =>
          Filter.ByRecipient(value, lastSequenceId)
        case Type.Name =>
          Filter.ByName(value, lastSequenceId)
      }

    }
  }

  /**
   * The publish filter implementation optimizes for the local/single instance case.
   *
   * This is reliant on the FilterStage implementation being able to deduplicate
   * filters. The current implementation defined filters as value objects and stores
   * them in a Set which will effectively dedpulicate them.
   *
   * @see org.apache.james.queue.pulsar.FilterStage.filters
   * @param producer
   * @param filter
   */
  private def publishFilter(producer: Producer[String])(filter: Filter): Unit = {
    import Filter._
    // Optimizes for the local/single instance case, the duplicated filter
    // received through pulsar will be eliminated by the filter stage as
    // filters are stored in a set @see org.apache.james.queue.pulsar.FilterStage.filters
    filterStage ! filter
    producer.send(Json.stringify(Json.toJson(filter)))
  }

  private def jsonStringToMailMetadata(json: String): MailMetadata =
    Json.fromJson[MailMetadata](Json.parse(json)).get

  /**
   * @inheritdoc
   */
  override def browse(): ManageableMailQueue.MailQueueIterator = {
    val outTopicReader = PulsarReader.forTopic(outTopic, lastMessage(outTopic).map(_.sequenceId))
    val scheduledTopicReader = PulsarReader.forTopic(scheduledTopic, lastMessage(scheduledTopic).map(_.sequenceId))

    implicit val timeout: Timeout = Timeout(1, TimeUnit.SECONDS)

    val outSource = outTopicReader
      .map(message => (jsonStringToMailMetadata(message.value), message))
      .via(debugLogger("browse-out"))
      .ask[Option[MailMetadata]](filterStage)

    val scheduledSource = scheduledTopicReader
      .map(message => (jsonStringToMailMetadata(message.value), message))
      .via(debugLogger("browse-scheduled"))
      .ask[Option[MailMetadata]](filterScheduledStage)

    val browseableMails: Source[Mail, NotUsed] = outSource.concat(scheduledSource)
      .collect { case Some(value) => value }
      .flatMapConcat(metadata => {
        val partsId = MimeMessagePartsId.builder()
          .headerBlobId(blobIdFactory.from(metadata.headerBlobId))
          .bodyBlobId(blobIdFactory.from(metadata.bodyBlobId))
          .build()
        Source.fromPublisher(readMimeMessage(partsId))
          .collect { case Some(message) => message }
          .map(message => readMail(metadata, message))
      })

    new ManageableMailQueue.MailQueueIterator() {
      private val javaStream = browseableMails.runWith(StreamConverters.asJavaStream[Mail]())
      private val iterator = javaStream.iterator()

      /**
       * @inheritdoc
       */
      override def close(): Unit = javaStream.close()

      override def hasNext: Boolean = iterator.hasNext

      override def next(): ManageableMailQueue.MailQueueItemView = new ManageableMailQueue.DefaultMailQueueItemView(iterator.next())
    }
  }

  private def readMail(mailMetadata: MailMetadata, mimeMessage: MimeMessage): Mail = {
    val builder = MailImpl
      .builder
      .name(mailMetadata.name)
      .sender(mailMetadata.sender.map(MaybeSender.getMailSender).getOrElse(MaybeSender.nullSender))
      .addRecipients(mailMetadata.recipients.map(new MailAddress(_)).asJavaCollection)
      .remoteAddr(mailMetadata.remoteAddr)
      .remoteHost(mailMetadata.remoteHost)
      .mimeMessage(mimeMessage)

    mailMetadata.state.foreach(builder.state)
    mailMetadata.errorMessage.foreach(builder.errorMessage)

    mailMetadata.lastUpdated.map(Date.from).foreach(builder.lastUpdated)

    mailMetadata.attributes.foreach { case (name, value) => builder.addAttribute(new Attribute(AttributeName.of(name), AttributeValue.fromJsonString(value))) }

    builder.addAllHeadersForRecipients(retrievePerRecipientHeaders(mailMetadata.perRecipientHeaders))

    builder.build
  }

  private def retrievePerRecipientHeaders(perRecipientHeaders: Map[String, Iterable[Header]]): PerRecipientHeaders = {
    val result = new PerRecipientHeaders()
    perRecipientHeaders.foreach { case (key, value) =>
      value.foreach(headers => {
        headers.values.foreach(header => {
          val builder = PerRecipientHeaders.Header.builder().name(headers.key).value(header)
          result.addHeaderForRecipient(builder, new MailAddress(key))
        })
      })
    }
    result
  }

  @throws[MailQueue.MailQueueException]
  private def saveMimeMessage(mimeMessage: MimeMessage): Publisher[MimeMessagePartsId] =
    try {
      mimeMessageStore.save(mimeMessage)
    } catch {
      case e: MessagingException =>
        throw new MailQueue.MailQueueException("Error while saving blob", e)
    }

  private def readMimeMessage(partsId: MimeMessagePartsId): Publisher[Option[MimeMessage]] =
    try {
      mimeMessageStore.read(partsId)
        .map[Option[MimeMessage]](Some(_))
        .onErrorReturn(classOf[ObjectNotFoundException], None)
    } catch {
      case e: MessagingException =>
        throw new MailQueue.MailQueueException("Error while reading blob", e)
    }

  private def deleteMimeMessage(partsId: MimeMessagePartsId): Source[Void, NotUsed] = {
    def doDelete() =
      try {
        mimeMessageStore.delete(partsId)
      } catch {
        case e: MessagingException =>
          throw new MailQueue.MailQueueException("Error while deleting blob", e)
      }

    Source.fromPublisher(doDelete())
  }

}