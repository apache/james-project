/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.jmap.method

import java.time.ZonedDateTime
import java.util.Date
import java.util.function.Consumer

import com.google.common.collect.ImmutableList
import eu.timepit.refined.auto._
import javax.inject.Inject
import javax.mail.Flags
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE, JMAP_MAIL}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{ClientId, Id, Invocation, ServerId, SetError, State, UTCDate}
import org.apache.james.jmap.json.{EmailSetSerializer, ResponseSerializer}
import org.apache.james.jmap.mail.EmailSet.{EmailCreationId, UnparsedMessageId}
import org.apache.james.jmap.mail.KeywordsFactory.LENIENT_KEYWORDS_FACTORY
import org.apache.james.jmap.mail.{DestroyIds, EmailCreationRequest, EmailCreationResponse, EmailSet, EmailSetRequest, EmailSetResponse, MailboxIds, ValidatedEmailSetUpdate}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MessageManager.{AppendCommand, FlagsUpdateMode}
import org.apache.james.mailbox.exception.MailboxNotFoundException
import org.apache.james.mailbox.model.{ComposedMessageIdWithMetaData, DeleteResult, MailboxId, MessageId, MessageRange}
import org.apache.james.mailbox.{MailboxManager, MailboxSession, MessageIdManager, MessageManager}
import org.apache.james.metrics.api.MetricFactory
import play.api.libs.json.{JsError, JsObject, JsSuccess}
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

import scala.jdk.CollectionConverters._

case class MessageNotFoundExeception(messageId: MessageId) extends Exception

class EmailSetMethod @Inject()(serializer: EmailSetSerializer,
                               messageIdManager: MessageIdManager,
                               mailboxManager: MailboxManager,
                               messageIdFactory: MessageId.Factory,
                               val metricFactory: MetricFactory,
                               val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[EmailSetRequest] {

  case class DestroyResults(results: Seq[DestroyResult]) {
    def destroyed: Option[DestroyIds] =
      Option(results.flatMap{
        case result: DestroySuccess => Some(result.messageId)
        case _ => None
      }.map(EmailSet.asUnparsed))
        .filter(_.nonEmpty)
        .map(DestroyIds)

    def notDestroyed: Option[Map[UnparsedMessageId, SetError]] =
      Option(results.flatMap{
        case failure: DestroyFailure => Some((failure.unparsedMessageId, failure.asMessageSetError))
        case _ => None
      }.toMap)
        .filter(_.nonEmpty)
  }

  object DestroyResult {
    def from(deleteResult: DeleteResult): Seq[DestroyResult] = {
      val success: Seq[DestroySuccess] = deleteResult.getDestroyed.asScala.toSeq
        .map(DestroySuccess)
      val notFound: Seq[DestroyResult] = deleteResult.getNotFound.asScala.toSeq
        .map(id => DestroyFailure(EmailSet.asUnparsed(id), MessageNotFoundExeception(id)))

      success ++ notFound
    }
  }

  trait DestroyResult
  case class DestroySuccess(messageId: MessageId) extends DestroyResult
  case class DestroyFailure(unparsedMessageId: UnparsedMessageId, e: Throwable) extends DestroyResult {
    def asMessageSetError: SetError = e match {
      case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(s"$unparsedMessageId is not a messageId: ${e.getMessage}"))
      case e: MessageNotFoundExeception => SetError.notFound(SetErrorDescription(s"Cannot find message with messageId: ${e.messageId.serialize()}"))
      case _ => SetError.serverFail(SetErrorDescription(e.getMessage))
    }
  }

  case class CreationResults(results: Seq[CreationResult]) {
    def created: Option[Map[EmailCreationId, EmailCreationResponse]] =
      Option(results.flatMap{
        case result: CreationSuccess => Some((result.clientId, result.response))
        case _ => None
      }.toMap)
        .filter(_.nonEmpty)

    def notCreated: Option[Map[EmailCreationId, SetError]] = {
      Option(results.flatMap{
        case failure: CreationFailure => Some((failure.clientId, failure.asMessageSetError))
        case _ => None
      }
        .toMap)
        .filter(_.nonEmpty)
    }
  }
  trait CreationResult
  case class CreationSuccess(clientId: EmailCreationId, response: EmailCreationResponse) extends CreationResult
  case class CreationFailure(clientId: EmailCreationId, e: Throwable) extends CreationResult {
    def asMessageSetError: SetError = e match {
      case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(e.getMessage))
      case e: MailboxNotFoundException => SetError.notFound(SetErrorDescription("Mailbox " + e.getMessage))
      case _ => SetError.serverFail(SetErrorDescription(e.getMessage))
    }
  }

  trait UpdateResult
  case class UpdateSuccess(messageId: MessageId) extends UpdateResult
  case class UpdateFailure(unparsedMessageId: UnparsedMessageId, e: Throwable) extends UpdateResult {
    def asMessageSetError: SetError = e match {
      case e: IllegalArgumentException => SetError.invalidPatch(SetErrorDescription(s"Message $unparsedMessageId update is invalid: ${e.getMessage}"))
      case _: MailboxNotFoundException => SetError.notFound(SetErrorDescription(s"Mailbox not found"))
      case e: MessageNotFoundExeception => SetError.notFound(SetErrorDescription(s"Cannot find message with messageId: ${e.messageId.serialize()}"))
      case _ => SetError.serverFail(SetErrorDescription(e.getMessage))
    }
  }
  case class UpdateResults(results: Seq[UpdateResult]) {
    def updated: Option[Map[MessageId, Unit]] =
      Option(results.flatMap{
        case result: UpdateSuccess => Some(result.messageId, ())
        case _ => None
      }.toMap)
        .filter(_.nonEmpty)

    def notUpdated: Option[Map[UnparsedMessageId, SetError]] =
      Option(results.flatMap{
        case failure: UpdateFailure => Some((failure.unparsedMessageId, failure.asMessageSetError))
        case _ => None
      }.toMap)
        .filter(_.nonEmpty)
  }

  override val methodName: MethodName = MethodName("Email/set")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, JMAP_MAIL)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: EmailSetRequest): SMono[InvocationWithContext] = {
    for {
      destroyResults <- destroy(request, mailboxSession)
      updateResults <- update(request, mailboxSession)
      created <- create(request, mailboxSession)
    } yield InvocationWithContext(
      invocation = Invocation(
        methodName = invocation.invocation.methodName,
        arguments = Arguments(serializer.serialize(EmailSetResponse(
          accountId = request.accountId,
          newState = State.INSTANCE,
          created = created.created,
          notCreated = created.notCreated,
          updated = updateResults.updated,
          notUpdated = updateResults.notUpdated,
          destroyed = destroyResults.destroyed,
          notDestroyed = destroyResults.notDestroyed))),
        methodCallId = invocation.invocation.methodCallId),
      processingContext = created.created.getOrElse(Map())
        .foldLeft(invocation.processingContext)({
          case (processingContext, (clientId, response)) =>
            Id.validate(response.id.serialize)
              .fold(_ => processingContext,
                serverId => processingContext.recordCreatedId(ClientId(clientId), ServerId(serverId)))
        }))
  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): SMono[EmailSetRequest] = asEmailSetRequest(invocation.arguments)

  private def asEmailSetRequest(arguments: Arguments): SMono[EmailSetRequest] =
    serializer.deserialize(arguments.value) match {
      case JsSuccess(emailSetRequest, _) => SMono.just(emailSetRequest)
      case errors: JsError => SMono.raiseError(new IllegalArgumentException(ResponseSerializer.serialize(errors).toString))
    }

  private def destroy(emailSetRequest: EmailSetRequest, mailboxSession: MailboxSession): SMono[DestroyResults] = {
    if (emailSetRequest.destroy.isDefined) {
      val messageIdsValidation: Seq[Either[DestroyFailure, MessageId]] = emailSetRequest.destroy.get.value
        .map(unparsedId => EmailSet.parse(messageIdFactory)(unparsedId).toEither
          .left.map(e => DestroyFailure(unparsedId, e)))
      val messageIds: Seq[MessageId] = messageIdsValidation.flatMap {
        case Right(messageId) => Some(messageId)
        case _ => None
      }
      val parsingErrors: Seq[DestroyFailure] = messageIdsValidation.flatMap {
        case Left(e) => Some(e)
        case _ => None
      }

      SMono.fromCallable(() => messageIdManager.delete(messageIds.toList.asJava, mailboxSession))
        .map(DestroyResult.from)
        .subscribeOn(Schedulers.elastic())
        .onErrorResume(e => SMono.just(messageIds.map(id => DestroyFailure(EmailSet.asUnparsed(id), e))))
        .map(_ ++ parsingErrors)
        .map(DestroyResults)
    } else {
      SMono.just(DestroyResults(Seq()))
    }
  }

  private def create(request: EmailSetRequest, mailboxSession: MailboxSession): SMono[CreationResults] =
    SFlux.fromIterable(request.create.getOrElse(Map()))
      .concatMap {
        case (clientId, json) => serializer.deserializeCreationRequest(json)
          .fold(e => SMono.just[CreationResult](CreationFailure(clientId, new IllegalArgumentException(e.toString))),
            creationRequest => create(clientId, creationRequest, mailboxSession))
      }.collectSeq()
      .map(CreationResults)

  private def create(clientId: EmailCreationId, request: EmailCreationRequest, mailboxSession: MailboxSession): SMono[CreationResult] = {
    val mailboxIds: List[MailboxId] = request.mailboxIds.value
    if (mailboxIds.size != 1) {
      SMono.just(CreationFailure(clientId, new IllegalArgumentException("mailboxIds need to have size 1")))
    } else {
      request.toMime4JMessage
        .fold(e => SMono.just(CreationFailure(clientId, e)),
          message => SMono.fromCallable[CreationResult](() => {
            val appendResult = mailboxManager.getMailbox(mailboxIds.head, mailboxSession)
              .appendMessage(AppendCommand.builder()
                .recent()
                .withFlags(request.keywords.map(_.asFlags).getOrElse(new Flags()))
                .withInternalDate(Date.from(request.receivedAt.getOrElse(UTCDate(ZonedDateTime.now())).asUTC.toInstant))
                .build(message),
                mailboxSession)
            CreationSuccess(clientId, EmailCreationResponse(appendResult.getId.getMessageId))
          })
            .subscribeOn(Schedulers.elastic())
            .onErrorResume(e => SMono.just[CreationResult](CreationFailure(clientId, e))))
    }
  }

  private def update(emailSetRequest: EmailSetRequest, mailboxSession: MailboxSession): SMono[UpdateResults] = {
    emailSetRequest.update
      .filter(_.nonEmpty)
      .map(update(_, mailboxSession))
      .getOrElse(SMono.just(UpdateResults(Seq())))
  }

  private def update(updates: Map[UnparsedMessageId, JsObject], session: MailboxSession): SMono[UpdateResults] = {
    val validatedUpdates: List[Either[UpdateFailure, (MessageId, ValidatedEmailSetUpdate)]] = updates
      .map({
        case (unparsedMessageId, json) => EmailSet.parse(messageIdFactory)(unparsedMessageId)
          .toEither
          .left.map(e => UpdateFailure(unparsedMessageId, e))
          .flatMap(id => serializer.deserializeEmailSetUpdate(json)
            .asEither.left.map(e => new IllegalArgumentException(e.toString))
            .flatMap(_.validate)
            .fold(e => Left(UpdateFailure(unparsedMessageId, e)),
              emailSetUpdate => Right((id, emailSetUpdate))))
      })
      .toList
    val failures: List[UpdateFailure] = validatedUpdates.flatMap({
      case Left(e) => Some(e)
      case _ => None
    })
    val validUpdates: List[(MessageId, ValidatedEmailSetUpdate)] = validatedUpdates.flatMap({
      case Right(pair) => Some(pair)
      case _ => None
    })

    for {
      updates <- SFlux.fromPublisher(messageIdManager.messagesMetadata(validUpdates.map(_._1).asJavaCollection, session))
        .collectMultimap(metaData => metaData.getComposedMessageId.getMessageId)
        .flatMap(metaData => {
          doUpdate(validUpdates, metaData, session)
        })
    } yield {
      UpdateResults(updates ++ failures)
    }
  }

  private def doUpdate(validUpdates: List[(MessageId, ValidatedEmailSetUpdate)],
                       metaData: Map[MessageId, Traversable[ComposedMessageIdWithMetaData]],
                       session: MailboxSession): SMono[Seq[UpdateResult]] = {
    val sameUpdate: Boolean = validUpdates.map(_._2).distinctBy(_.update).size == 1
    val singleMailbox: Boolean = metaData.values.flatten.map(_.getComposedMessageId.getMailboxId).toSet.size == 1

    if (sameUpdate && singleMailbox && validUpdates.size > 3) {
      val update: ValidatedEmailSetUpdate = validUpdates.map(_._2).headOption.get
      val ranges: List[MessageRange] = asRanges(metaData)
      val mailboxId: MailboxId = metaData.values.flatten.map(_.getComposedMessageId.getMailboxId).headOption.get

      if (update.update.isOnlyFlagAddition) {
        updateFlagsByRange(mailboxId, update.update.keywordsToAdd.get.asFlags, ranges, metaData, FlagsUpdateMode.ADD, session)
      } else if (update.update.isOnlyFlagRemoval) {
        updateFlagsByRange(mailboxId, update.update.keywordsToRemove.get.asFlags, ranges, metaData, FlagsUpdateMode.REMOVE, session)
      } else if (update.update.isOnlyMove) {
        moveByRange(mailboxId, update, ranges, metaData, session)
      } else {
        updateEachMessage(validUpdates, metaData, session)
      }
    } else {
      updateEachMessage(validUpdates, metaData, session)
    }
  }

  private def asRanges(metaData: Map[MessageId, Traversable[ComposedMessageIdWithMetaData]]) =
    MessageRange.toRanges(metaData.values
      .flatten.map(_.getComposedMessageId.getUid)
      .toList.asJava)
      .asScala.toList

  private def updateFlagsByRange(mailboxId: MailboxId,
                                 flags: Flags,
                                 ranges: List[MessageRange],
                                 metaData: Map[MessageId, Traversable[ComposedMessageIdWithMetaData]],
                                 updateMode: FlagsUpdateMode,
                                 session: MailboxSession): SMono[Seq[UpdateResult]] = {
    val mailboxMono: SMono[MessageManager] = SMono.fromCallable(() => mailboxManager.getMailbox(mailboxId, session))

    mailboxMono.flatMap(mailbox => updateByRange(ranges, metaData,
      range => mailbox.setFlags(flags, updateMode, range, session)))
      .subscribeOn(Schedulers.elastic())
  }

  private def moveByRange(mailboxId: MailboxId,
                          update: ValidatedEmailSetUpdate,
                          ranges: List[MessageRange],
                          metaData: Map[MessageId, Traversable[ComposedMessageIdWithMetaData]],
                          session: MailboxSession): SMono[Seq[UpdateResult]] = {
    val targetId: MailboxId = update.update.mailboxIds.get.value.headOption.get

    updateByRange(ranges, metaData,
      range => mailboxManager.moveMessages(range, mailboxId, targetId, session))
  }

  private def updateByRange(ranges: List[MessageRange],
                            metaData: Map[MessageId, Traversable[ComposedMessageIdWithMetaData]],
                            operation: Consumer[MessageRange]): SMono[Seq[UpdateResult]] = {

    SFlux.fromIterable(ranges)
      .concatMap(range => {
        val messageIds = metaData.filter(entry => entry._2.exists(composedId => range.includes(composedId.getComposedMessageId.getUid)))
          .keys
          .toSeq
        SMono.fromCallable[Seq[UpdateResult]](() => {
          operation.accept(range)
          messageIds.map(UpdateSuccess)
        })
          .onErrorResume(e => SMono.just(messageIds.map(id => UpdateFailure(EmailSet.asUnparsed(id), e))))
          .subscribeOn(Schedulers.elastic())
      })
      .reduce(Seq(), _ ++ _)
  }

  private def updateEachMessage(validUpdates: List[(MessageId, ValidatedEmailSetUpdate)],
                                metaData: Map[MessageId, Traversable[ComposedMessageIdWithMetaData]],
                                session: MailboxSession): SMono[Seq[UpdateResult]] =
    SFlux.fromIterable(validUpdates)
      .concatMap[UpdateResult]({
        case (messageId, updatePatch) =>
          updateSingleMessage(messageId, updatePatch, metaData.get(messageId).toList.flatten, session)
      })
      .collectSeq()

  private def updateSingleMessage(messageId: MessageId, update: ValidatedEmailSetUpdate, storedMetaData: List[ComposedMessageIdWithMetaData], session: MailboxSession): SMono[UpdateResult] = {
    val mailboxIds: MailboxIds = MailboxIds(storedMetaData.map(metaData => metaData.getComposedMessageId.getMailboxId))
    val originFlags: Flags = storedMetaData
      .foldLeft[Flags](new Flags())((flags: Flags, m: ComposedMessageIdWithMetaData) => {
        flags.add(m.getFlags)
        flags
      })

    if (mailboxIds.value.isEmpty) {
      SMono.just[UpdateResult](UpdateFailure(EmailSet.asUnparsed(messageId), MessageNotFoundExeception(messageId)))
    } else {
      updateFlags(messageId, update, mailboxIds, originFlags, session)
        .flatMap {
          case failure: UpdateFailure => SMono.just[UpdateResult](failure)
          case _: UpdateSuccess => updateMailboxIds(messageId, update, mailboxIds, session)
        }
        .onErrorResume(e => SMono.just[UpdateResult](UpdateFailure(EmailSet.asUnparsed(messageId), e)))
        .switchIfEmpty(SMono.just[UpdateResult](UpdateSuccess(messageId)))
    }
  }

  private def updateMailboxIds(messageId: MessageId, update: ValidatedEmailSetUpdate, mailboxIds: MailboxIds, session: MailboxSession): SMono[UpdateResult] = {
    val targetIds = update.mailboxIdsTransformation.apply(mailboxIds)
    if (targetIds.equals(mailboxIds)) {
      SMono.just[UpdateResult](UpdateSuccess(messageId))
    } else {
      SMono.fromCallable(() => messageIdManager.setInMailboxes(messageId, targetIds.value.asJava, session))
        .subscribeOn(Schedulers.elastic())
        .`then`(SMono.just[UpdateResult](UpdateSuccess(messageId)))
        .onErrorResume(e => SMono.just[UpdateResult](UpdateFailure(EmailSet.asUnparsed(messageId), e)))
        .switchIfEmpty(SMono.just[UpdateResult](UpdateSuccess(messageId)))
    }
  }

  private def updateFlags(messageId: MessageId, update: ValidatedEmailSetUpdate, mailboxIds: MailboxIds, originalFlags: Flags, session: MailboxSession): SMono[UpdateResult] = {
    val newFlags = update.keywordsTransformation
      .apply(LENIENT_KEYWORDS_FACTORY.fromFlags(originalFlags).get)
      .asFlagsWithRecentAndDeletedFrom(originalFlags)

    if (newFlags.equals(originalFlags)) {
      SMono.just[UpdateResult](UpdateSuccess(messageId))
    } else {
      SMono.fromCallable(() =>
        messageIdManager.setFlags(newFlags, FlagsUpdateMode.REPLACE, messageId, ImmutableList.copyOf(mailboxIds.value.asJavaCollection), session))
        .subscribeOn(Schedulers.elastic())
        .`then`(SMono.just[UpdateResult](UpdateSuccess(messageId)))
    }
  }
}