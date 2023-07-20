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

package org.apache.james.queue.pulsar

import java.util
import java.util.Optional
import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorSystem
import javax.annotation.PreDestroy
import javax.inject.Inject
import javax.mail.internet.MimeMessage
import org.apache.james.backends.pulsar.{PulsarClients, PulsarConfiguration}
import org.apache.james.blob.api.{BlobId, Store}
import org.apache.james.blob.mail.MimeMessagePartsId
import org.apache.james.metrics.api.{GaugeRegistry, MetricFactory}
import org.apache.james.queue.api.{MailQueueFactory, MailQueueItemDecoratorFactory, MailQueueName}

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.util.Try

class PulsarMailQueueFactory @Inject()(pulsarConfiguration: PulsarConfiguration,
  pulsarClients:PulsarClients,
  blobIdFactory: BlobId.Factory,
  mimeMessageStore: Store[MimeMessage, MimeMessagePartsId],
  mailQueueItemDecoratorFactory: MailQueueItemDecoratorFactory,
  metricFactory: MetricFactory,
  gaugeRegistry: GaugeRegistry
) extends MailQueueFactory[PulsarMailQueue] {
  private val queues: AtomicReference[Map[MailQueueName, PulsarMailQueue]] = new AtomicReference(Map.empty)
  private val admin = pulsarClients.adminClient
  private val system: ActorSystem = ActorSystem("pulsar-mailqueue")

  @PreDestroy
  def stop(): Unit = {
    queues.getAndUpdate(map => {
      map.values.foreach(_.close())
      map.empty
    })
    system.terminate()
  }

  override def getQueue(name: MailQueueName, count: MailQueueFactory.PrefetchCount): Optional[PulsarMailQueue] = {
    Try(admin.topics().getInternalInfo(s"persistent://${pulsarConfiguration.namespace.asString}/James-${name.asString()}")).toOption.map(_ =>
      createQueue(name, count)
    ).toJava
  }

  override def createQueue(name: MailQueueName, count: MailQueueFactory.PrefetchCount): PulsarMailQueue = {
    queues.updateAndGet(map => {
      val queue = map.get(name)
        .fold(new PulsarMailQueue(
          PulsarMailQueueConfiguration(name, pulsarConfiguration),
          pulsarClients,
          blobIdFactory,
          mimeMessageStore,
          mailQueueItemDecoratorFactory,
          metricFactory,
          gaugeRegistry,
          system)
        )(identity)
      map + (name -> queue)
    })(name)
  }

  override def listCreatedMailQueues(): util.Set[MailQueueName] =
    admin.topics()
      .getList(pulsarConfiguration.namespace.asString)
      .asScala
      .filter(_.startsWith(s"persistent://${pulsarConfiguration.namespace.asString}/James-"))
      .map(_.replace(s"persistent://${pulsarConfiguration.namespace.asString}/James-", ""))
      .map(MailQueueName.of)
      .toSet
      .asJava
}
