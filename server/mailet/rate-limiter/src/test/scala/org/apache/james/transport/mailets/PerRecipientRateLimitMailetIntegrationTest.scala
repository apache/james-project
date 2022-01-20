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

package org.apache.james.transport.mailets

import java.io.File
import java.util.concurrent.TimeUnit

import org.apache.james.core.builder.MimeMessageBuilder
import org.apache.james.mailbox.model.{MultimailboxesSearchQuery, SearchQuery}
import org.apache.james.mailets.TemporaryJamesServer
import org.apache.james.mailets.configuration.Constants.{DEFAULT_DOMAIN, LOCALHOST_IP, PASSWORD}
import org.apache.james.mailets.configuration.{Constants, MailetConfiguration, MailetContainer, ProcessorConfiguration}
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.modules.protocols.{ImapGuiceProbe, SmtpGuiceProbe}
import org.apache.james.rate.limiter.memory.MemoryRateLimiterModule
import org.apache.james.transport.mailets.PerRecipientRateLimitMailetIntegrationTest._
import org.apache.james.transport.matchers.All
import org.apache.james.utils.{DataProbeImpl, SMTPMessageSender, SpoolerProbe, TestIMAPClient}
import org.apache.mailet.base.test.FakeMail
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.{AfterEach, BeforeEach, Test};

object PerRecipientRateLimitMailetIntegrationTest {
  val RECIPIENT: String = "recipient@" + DEFAULT_DOMAIN
  val RECIPIENT2: String = "recipient2@" + DEFAULT_DOMAIN
  val SENDER: String = "sender@" + DEFAULT_DOMAIN
  val SENDER2: String = "sender2@" + DEFAULT_DOMAIN
  val RATE_LIMIT_STATE: String = "rate_limit_state"
}

class PerRecipientRateLimitMailetIntegrationTest {
  var messageSender: SMTPMessageSender = _
  var imapClient: TestIMAPClient = _
  var jamesServer: TemporaryJamesServer = _

  @BeforeEach
  def setUp(@TempDir temporaryFolder: File): Unit = {

    val rateLimitProcessor: ProcessorConfiguration = ProcessorConfiguration.builder()
      .state(RATE_LIMIT_STATE)
      .addMailet(MailetConfiguration.builder
        .matcher(classOf[All])
        .mailet(classOf[PerRecipientRateLimitMailet])
        .addProperty("duration", "60s")
        .addProperty("count", "1")
        .build())
      .addMailet(MailetConfiguration.TO_TRANSPORT)
      .build()

    val mailetContainer: MailetContainer.Builder = TemporaryJamesServer.defaultMailetContainerConfiguration()
      .putProcessor(ProcessorConfiguration.root()
        .addMailet(MailetConfiguration.builder
          .matcher(classOf[All])
          .mailet(classOf[ToProcessor])
          .addProperty("processor", RATE_LIMIT_STATE)
          .build))
      .putProcessor(rateLimitProcessor)

    jamesServer = TemporaryJamesServer.builder()
      .withMailetContainer(mailetContainer)
      .withOverrides(new MemoryRateLimiterModule())
      .build(temporaryFolder)
    jamesServer.start()

    val dataProbe: DataProbeImpl = jamesServer.getProbe(classOf[DataProbeImpl])
    dataProbe.addDomain(DEFAULT_DOMAIN)
    dataProbe.addUser(RECIPIENT, PASSWORD)
    dataProbe.addUser(RECIPIENT2, PASSWORD)
    dataProbe.addUser(SENDER, PASSWORD)
    dataProbe.addUser(SENDER2, PASSWORD)

    messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);
    imapClient = new TestIMAPClient;
  }

  @AfterEach
  def tearDown(): Unit = {
    if (jamesServer != null) {
      jamesServer.shutdown()
    }
  }

  private def awaitToFirstMessage(): Unit =
    imapClient.connect(LOCALHOST_IP, jamesServer.getProbe(classOf[ImapGuiceProbe]).getImapPort)
      .login(RECIPIENT, PASSWORD)
      .select(TestIMAPClient.INBOX)
      .awaitMessage(Constants.awaitAtMostOneMinute)

  @Test
  def recipientShouldReceivedEmailWhenRateLimitAcceptable(): Unit = {
    messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(SENDER, PASSWORD)
      .sendMessage(FakeMail.builder
        .name("name")
        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder
          .addToRecipient(RECIPIENT)
          .setSender(SENDER)
          .setText("Content1"))
        .sender(SENDER)
        .recipient(RECIPIENT))

    awaitToFirstMessage()

    assertThat(imapClient.readFirstMessage()).contains("Content1")
  }

  @Test
  def recipientShouldNotReceivedEmailWhenRateLimitExceeded(): Unit = {
    // acceptable
    messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(SENDER, PASSWORD)
      .sendMessage(FakeMail.builder
        .name("name")
        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder
          .addToRecipient(RECIPIENT)
          .setSender(SENDER)
          .setText("Content1"))
        .sender(SENDER)
        .recipient(RECIPIENT))

    awaitToFirstMessage()

    // exceeded
    messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(SENDER, PASSWORD)
      .sendMessage(FakeMail.builder
        .name("name")
        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder
          .addToRecipient(RECIPIENT)
          .setSender(SENDER)
          .setText("Content2"))
        .sender(SENDER)
        .recipient(RECIPIENT))

    Constants.awaitAtMostOneMinute.untilAsserted(() => jamesServer.getProbe(classOf[SpoolerProbe]).processingFinished)

    val receivedMessageIds = jamesServer.getProbe(classOf[MailboxProbeImpl])
      .searchMessage(MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.all())).build, RECIPIENT, 100)

    assertThat(receivedMessageIds.size()).isEqualTo(1)
  }

  @Test
  def rateLimitShouldBeAppliedPerRecipient(): Unit = {
    // acceptable
    messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(SENDER, PASSWORD)
      .sendMessage(FakeMail.builder
        .name("name")
        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder
          .addToRecipient(RECIPIENT)
          .setSender(SENDER)
          .setText("Content1"))
        .sender(SENDER)
        .recipient(RECIPIENT))

    awaitToFirstMessage()

    // RECIPIENT: exceeded, RECIPIENT2: acceptable
    messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(SENDER, PASSWORD)
      .sendMessage(FakeMail.builder
        .name("name")
        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder
          .addToRecipient(RECIPIENT, RECIPIENT2)
          .setSender(SENDER)
          .setText("Content2"))
        .sender(SENDER)
        .recipients(RECIPIENT, RECIPIENT2))

    Constants.awaitAtMostOneMinute.untilAsserted(() => jamesServer.getProbe(classOf[SpoolerProbe]).processingFinished)

    val mailboxProbe: MailboxProbeImpl = jamesServer.getProbe(classOf[MailboxProbeImpl])

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(mailboxProbe.searchMessage(MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.all())).build, RECIPIENT, 100).size())
        .isEqualTo(1)
      softly.assertThat(mailboxProbe.searchMessage(MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.all())).build, RECIPIENT2, 100).size())
        .isEqualTo(1)
    })
  }

  @Test
  def allRecipientShouldNotReceivedEmailWhenAllRateLimitExceeded(): Unit = {
    // acceptable
    messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(SENDER, PASSWORD)
      .sendMessage(FakeMail.builder
        .name("name")
        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder
          .addToRecipient(RECIPIENT, RECIPIENT2)
          .setSender(SENDER)
          .setText("Content1"))
        .sender(SENDER)
        .recipients(RECIPIENT, RECIPIENT2))

    awaitToFirstMessage()

    // exceeded all
    messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(SENDER, PASSWORD)
      .sendMessage(FakeMail.builder
        .name("name")
        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder
          .addToRecipient(RECIPIENT, RECIPIENT2)
          .setSender(SENDER)
          .setText("Content2"))
        .sender(SENDER)
        .recipients(RECIPIENT, RECIPIENT2))

    Constants.awaitAtMostOneMinute.untilAsserted(() => jamesServer.getProbe(classOf[SpoolerProbe]).processingFinished)
    val mailboxProbe: MailboxProbeImpl = jamesServer.getProbe(classOf[MailboxProbeImpl])

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(mailboxProbe.searchMessage(MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.all())).build, RECIPIENT, 100).size())
        .isEqualTo(1)
      softly.assertThat(mailboxProbe.searchMessage(MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.all())).build, RECIPIENT2, 100).size())
        .isEqualTo(1)
    })
  }

  @Test
  def rateLimitShouldNotBeAppliedPerSender(): Unit = {
    // acceptable. SENDER
    messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(SENDER, PASSWORD)
      .sendMessage(FakeMail.builder
        .name("name")
        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder
          .addToRecipient(RECIPIENT)
          .setSender(SENDER)
          .setText("Content1"))
        .sender(SENDER)
        .recipient(RECIPIENT))

    awaitToFirstMessage()

    // exceeded. SENDER2
    messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(SENDER2, PASSWORD)
      .sendMessage(FakeMail.builder
        .name("name")
        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder
          .addToRecipient(RECIPIENT)
          .setSender(SENDER2)
          .setText("Content2"))
        .sender(SENDER2)
        .recipient(RECIPIENT))

    TimeUnit.SECONDS.sleep(3)
    Constants.awaitAtMostOneMinute.untilAsserted(() => jamesServer.getProbe(classOf[SpoolerProbe]).processingFinished)

    val receivedMessageIds = jamesServer.getProbe(classOf[MailboxProbeImpl])
      .searchMessage(MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.all())).build, RECIPIENT, 100)

    assertThat(receivedMessageIds.size()).isEqualTo(1)
  }

}
