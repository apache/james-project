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

import java.util.concurrent.TimeUnit

import com.github.fge.lambdas.Throwing
import org.apache.commons.net.smtp.SMTPClient
import org.apache.james.GuiceJamesServer
import org.apache.james.core.MailAddress
import org.apache.james.dnsservice.api.InMemoryDNSService
import org.apache.james.jmap.JMAPTestingConstants.{DOMAIN, LOCALHOST_IP, calmlyAwait}
import org.apache.james.jmap.JmapGuiceProbe
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.jmap.rfc8621.contract.VacationRelayIntegrationTest.{PASSWORD, REASON, USER_WITH_DOMAIN}
import org.apache.james.junit.categories.BasicFeature
import org.apache.james.mailbox.DefaultMailboxes
import org.apache.james.mailbox.model.MailboxConstants
import org.apache.james.mock.smtp.server.model.Mail
import org.apache.james.mock.smtp.server.testing.MockSmtpServerExtension
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.modules.protocols.SmtpGuiceProbe
import org.apache.james.utils.DataProbeImpl
import org.apache.james.vacation.api.VacationPatch
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.experimental.categories.Category
import org.junit.jupiter.api.{AfterEach, BeforeEach, Test}

object VacationRelayIntegrationTest {
  private val USER = "benwa"
  private val USER_WITH_DOMAIN = USER + '@' + DOMAIN
  private val PASSWORD = "secret"
  private val REASON = "Message explaining my wonderful vacations"
}

trait VacationRelayIntegrationTest {
  def getFakeSmtp: MockSmtpServerExtension
  def getInMemoryDns: InMemoryDNSService

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    getInMemoryDns.registerMxRecord("yopmail.com", getFakeSmtp.getMockSmtp.getIPAddress)

    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN)
      .addUser(USER_WITH_DOMAIN, PASSWORD)

    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USER_WITH_DOMAIN, DefaultMailboxes.SENT)
    mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USER_WITH_DOMAIN, DefaultMailboxes.INBOX)
  }

  @AfterEach
  def teardown(server: GuiceJamesServer): Unit = {
    getFakeSmtp.getMockSmtp.getConfigurationClient.clearMails()
    getFakeSmtp.getMockSmtp.getConfigurationClient.clearBehaviors()
  }

  @Category(Array(classOf[BasicFeature]))
  @Test
  def forwardingAnEmailShouldWork(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[JmapGuiceProbe])
      .modifyVacation(AccountId.fromString(USER_WITH_DOMAIN), VacationPatch.builder.isEnabled(true).textBody(REASON).build)

    val externalMail = "ray@yopmail.com"

    val smtpClient = new SMTPClient

    smtpClient.connect(LOCALHOST_IP, server.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort.getValue)
    smtpClient.helo(DOMAIN)
    smtpClient.setSender(externalMail)
    smtpClient.rcpt("<" + USER_WITH_DOMAIN + ">")
    smtpClient.sendShortMessageData("Reply-To: <" + externalMail + ">\r\n\r\ncontent")
    calmlyAwait.atMost(1, TimeUnit.MINUTES).untilAsserted(() => {
      val mails = getFakeSmtp.getMockSmtp.getConfigurationClient.listMails
      assertThat(mails).hasSize(1)
      SoftAssertions.assertSoftly(Throwing.consumer((softly: SoftAssertions) => {
        softly.assertThat(mails.get(0).getEnvelope.getFrom).isEqualTo(MailAddress.nullSender)
        softly.assertThat(mails.get(0).getEnvelope.getRecipients).containsOnly(Mail.Recipient.builder.address(new MailAddress(externalMail)).build)
        softly.assertThat(mails.get(0).getMessage).contains(REASON)
      }))
    })
  }
}
