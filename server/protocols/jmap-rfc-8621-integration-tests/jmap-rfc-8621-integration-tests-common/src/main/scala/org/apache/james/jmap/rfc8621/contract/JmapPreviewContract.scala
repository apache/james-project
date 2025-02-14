/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.rfc8621.contract

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Optional
import java.util.concurrent.TimeUnit

import com.google.common.collect.ImmutableList
import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import io.restassured.RestAssured.requestSpecification
import jakarta.inject.Inject
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.api.projections.{MessageFastViewPrecomputedProperties, MessageFastViewProjection}
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ANDRE, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.JmapPreviewContract.createTestMessage
import org.apache.james.mailbox.DefaultMailboxes
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.{MailboxPath, MessageId, MessageRange}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.{DataProbeImpl, GuiceProbe}
import org.awaitility.Awaitility
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.SMono

import scala.jdk.OptionConverters.RichOption

class MessageFastViewProjectionProbe @Inject() (messageFastViewProjection: MessageFastViewProjection) extends GuiceProbe {
  def retrieve(messageId: MessageId): Optional[MessageFastViewPrecomputedProperties] =
    SMono.fromPublisher(messageFastViewProjection.retrieve(messageId)).blockOption().toJava
}

class JmapPreviewProbeModule extends AbstractModule {
  override protected def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[GuiceProbe])
      .addBinding()
      .to(classOf[MessageFastViewProjectionProbe])
  }
}

object JmapPreviewContract {
  private def createTestMessage: Message = Message.Builder
    .of
    .setSubject("test")
    .setSender(ANDRE.asString())
    .setFrom(ANDRE.asString())
    .setSubject("World domination \r\n" +
      " and this is also part of the header")
    .setBody("testmail", StandardCharsets.UTF_8)
    .build
}

trait JmapPreviewContract {
  private lazy val slowPacedPollInterval = Duration.ofMillis(100)
  private lazy val calmlyAwait = Awaitility.`with`
    .pollInterval(slowPacedPollInterval)
    .and.`with`.pollDelay(slowPacedPollInterval)
    .await
  private lazy val awaitAtMostTenSeconds = calmlyAwait.atMost(10, TimeUnit.SECONDS)

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  @Test
  def jmapPreviewShouldBeWellRemovedWhenDeleteMailbox(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox("#private", BOB.asString, DefaultMailboxes.INBOX)

    val messageId = mailboxProbe.appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder()
        .build(createTestMessage))
      .getMessageId

    val messageFastViewProjectionProbe: MessageFastViewProjectionProbe = server.getProbe(classOf[MessageFastViewProjectionProbe])
    awaitAtMostTenSeconds.until(() => messageFastViewProjectionProbe.retrieve(messageId).isPresent)

    mailboxProbe.deleteMailbox("#private", BOB.asString, DefaultMailboxes.INBOX)
    awaitAtMostTenSeconds.until(() => messageFastViewProjectionProbe.retrieve(messageId).isEmpty)
  }

  @Test
  def jmapPreviewShouldBeWellRemovedWhenDeleteMessage(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox("#private", BOB.asString, DefaultMailboxes.INBOX)

    val composedMessageId = mailboxProbe.appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder()
        .build(createTestMessage))

    val messageFastViewProjectionProbe: MessageFastViewProjectionProbe = server.getProbe(classOf[MessageFastViewProjectionProbe])
    awaitAtMostTenSeconds.until(() => messageFastViewProjectionProbe.retrieve(composedMessageId.getMessageId).isPresent)

    mailboxProbe.deleteMessage(ImmutableList.of(composedMessageId.getUid), MailboxPath.inbox(BOB), BOB)
    awaitAtMostTenSeconds.until(() => messageFastViewProjectionProbe.retrieve(composedMessageId.getMessageId).isEmpty)
  }

  @Test
  def shouldKeepPreviewWhenExpungedAndStillReferenced(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox("#private", BOB.asString, DefaultMailboxes.INBOX)
    mailboxProbe.createMailbox("#private", BOB.asString, "otherBox")

    val composedMessageId = mailboxProbe.appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder()
      .build(createTestMessage))

    mailboxProbe.moveMessages(MessageRange.all, MailboxPath.inbox(BOB), MailboxPath.forUser(BOB, "otherBox"), BOB)

    val messageFastViewProjectionProbe: MessageFastViewProjectionProbe = server.getProbe(classOf[MessageFastViewProjectionProbe])
    awaitAtMostTenSeconds.until(() => messageFastViewProjectionProbe.retrieve(composedMessageId.getMessageId).isPresent)
  }
}
