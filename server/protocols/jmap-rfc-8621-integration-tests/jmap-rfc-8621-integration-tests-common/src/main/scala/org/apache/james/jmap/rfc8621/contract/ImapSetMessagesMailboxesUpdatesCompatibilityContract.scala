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
import java.util
import java.util.concurrent.TimeUnit

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured
import io.restassured.RestAssured.`given`
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.JMAPTestingConstants.{DOMAIN, LOCALHOST_IP}
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ANDRE, BOB, BOB_PASSWORD, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.ImapSetMessagesMailboxesUpdatesCompatibilityContract.bobInboxPath
import org.apache.james.mailbox.DefaultMailboxes
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.{MailboxConstants, MailboxId, MailboxPath, MessageId}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.modules.protocols.ImapGuiceProbe
import org.apache.james.utils.{DataProbeImpl, TestIMAPClient}
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.junit.jupiter.api.{BeforeEach, Test}

object ImapSetMessagesMailboxesUpdatesCompatibilityContract {
  private val bobInboxPath = MailboxPath.forUser(BOB, DefaultMailboxes.INBOX)
}

trait ImapSetMessagesMailboxesUpdatesCompatibilityContract {
  private lazy val slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS
  private lazy val calmlyAwait = Awaitility.`with`
    .pollInterval(slowPacedPollInterval)
    .and.`with`.pollDelay(slowPacedPollInterval)
    .await
  private lazy val awaitAtMostOneMinute = calmlyAwait.atMost(1, TimeUnit.MINUTES)

  def imapClient: TestIMAPClient

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN)
      .addUser(BOB.asString, BOB_PASSWORD)

    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB.asString, DefaultMailboxes.INBOX)
    mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB.asString, DefaultMailboxes.ARCHIVE)
    mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB.asString, DefaultMailboxes.TRASH)

    RestAssured.requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  @Test
  def messageMovedByJmapIsSeenMovedByImap(server: GuiceJamesServer): Unit = {
    // Given the user has a message "m1" in "inbox" with subject "My awesome subject", content "This is the content"
    val messageId: MessageId = appendMessageToInbox(server)

    // When the user moves "m1" to user mailbox "archive"
    moveMessageFromInboxToArchive(server, messageId)

    // Then the user has a IMAP message in mailbox "archive"
    imapClient.connect(LOCALHOST_IP, server.getProbe(classOf[ImapGuiceProbe]).getImapPort)
      .login(BOB, BOB_PASSWORD)
      .select(DefaultMailboxes.ARCHIVE)
      .awaitMessageCount(awaitAtMostOneMinute, 1)

    // And the user does not have a IMAP message in mailbox "inbox"
    imapClient.select(DefaultMailboxes.INBOX)
      .awaitMessageCount(awaitAtMostOneMinute, 0)
  }

  @Test
  def messageCopiedByJmapIsSeenAsCopiedByImap(server: GuiceJamesServer): Unit = {
    // Given the user has a message "m1" in "inbox" with subject "My awesome subject", content "This is the content"
    val messageId: MessageId = appendMessageToInbox(server)

    // When the user copies "m1" from mailbox "inbox" to mailbox "archive"
    copyMessageFromInboxToArchive(server, messageId)

    // Then the user has a IMAP message in mailbox "archive"
    imapClient.connect(LOCALHOST_IP, server.getProbe(classOf[ImapGuiceProbe]).getImapPort)
      .login(BOB, BOB_PASSWORD)
      .select(DefaultMailboxes.ARCHIVE)
      .awaitMessageCount(awaitAtMostOneMinute, 1)

    // And the user has a IMAP message in mailbox "inbox"
    imapClient.select(DefaultMailboxes.INBOX)
      .awaitMessageCount(awaitAtMostOneMinute, 1)
  }

  @Test
  def imapClientShouldBeNotifiedWhenSelectingMailboxWhereMessageMovedByJmap(server: GuiceJamesServer): Unit = {
    // Given the user has a message "m1" in "inbox" mailbox with subject "My awesome subject", content "This is the content"
    val messageId: MessageId = appendMessageToInbox(server)

    // When the user moves "m1" to user mailbox "archive"
    moveMessageFromInboxToArchive(server, messageId)

    // Then the user has a IMAP notification about 1 new message when selecting mailbox "archive"
    imapClient.connect(LOCALHOST_IP, server.getProbe(classOf[ImapGuiceProbe]).getImapPort)
      .login(BOB, BOB_PASSWORD)
      .select(DefaultMailboxes.ARCHIVE)

    assertThat(imapClient.userGetNotifiedForNewMessagesWhenSelectingMailbox(1))
      .isTrue
  }

  @Test
  def imapClientShouldBeNotifiedOnCurrentMailboxWhenMessageMovedByJmapToTheSameMailbox(server: GuiceJamesServer): Unit = {
    // Given the user has a message "m1" in "inbox" mailbox with subject "My awesome subject", content "This is the content"
    val messageId: MessageId = appendMessageToInbox(server)

    // Given the user has an open IMAP connection with mailbox "archive" selected
    imapClient.connect(LOCALHOST_IP, server.getProbe(classOf[ImapGuiceProbe]).getImapPort)
      .login(BOB, BOB_PASSWORD)
      .select(DefaultMailboxes.ARCHIVE)

    // When the user moves "m1" to user mailbox "archive"
    moveMessageFromInboxToArchive(server, messageId)

    // Then mailbox "archive" contains 1 messages
    awaitAtMostOneMinute.until(() => listMessageIdsArchive(server).size == 1)

    // Then the user has a IMAP RECENT and a notification about 1 new messages on connection for mailbox "archive"
    assertThat(imapClient.userGetNotifiedForNewMessages(1))
      .isTrue
  }

  @Test
  def whenMessageCopiedByImapShouldBeSeenByJmapAndNotifiedOnImapWithDestinationMailboxAlreadySelected(server: GuiceJamesServer): Unit = {
    // Given the user has a message "m1" in "inbox" mailbox with subject "My awesome subject", content "This is the content"
    appendMessageToInbox(server)

    // Given the user has an open IMAP connection with mailbox "archive" selected
    imapClient.connect(LOCALHOST_IP, server.getProbe(classOf[ImapGuiceProbe]).getImapPort)
      .login(BOB, BOB_PASSWORD)
      .select(DefaultMailboxes.ARCHIVE)

    // When the user copy by IMAP first message of "inbox" to mailbox "archive"
    val imapClientCopy: TestIMAPClient = new TestIMAPClient
    imapClientCopy.connect(LOCALHOST_IP, server.getProbe(classOf[ImapGuiceProbe]).getImapPort)
      .login(BOB.asString, BOB_PASSWORD)
      .select(DefaultMailboxes.INBOX)
      .copyFirstMessage(DefaultMailboxes.ARCHIVE)
    imapClientCopy.close()

    // Then mailbox "archive" contains 1 messages
    awaitAtMostOneMinute.until(() => listMessageIdsArchive(server).size == 1)

    // Then the user has a IMAP RECENT and a notification about 1 new messages on connection for mailbox "mailbox"
    assertThat(imapClient.userGetNotifiedForNewMessages(1))
      .isTrue
  }

  @Test
  def whenMessageMovedByJmapThemImapClientWithSourceMailboxSelectedWillNotBeNotified(server: GuiceJamesServer): Unit = {
    // Given the user has a message "m1" in "inbox" mailbox with subject "My awesome subject", content "This is the content"
    val messageId: MessageId = appendMessageToInbox(server)

    // Given the user has an open IMAP connection with mailbox "inbox" selected
    imapClient.connect(LOCALHOST_IP, server.getProbe(classOf[ImapGuiceProbe]).getImapPort)
      .login(BOB, BOB_PASSWORD)
      .select(DefaultMailboxes.INBOX)

    // When the user moves "m1" to user mailbox "archive"
    moveMessageFromInboxToArchive(server, messageId)
    awaitAtMostOneMinute.until(() => listMessageIdsArchive(server).size == 1)

    // Then the user has IMAP EXPUNGE and a notification for 1 message sequence number on connection for mailbox "inbox"
    assertThat(imapClient.userGetNotifiedForDeletion(1)).isTrue
  }

  private def appendMessageToInbox(server: GuiceJamesServer): MessageId = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(ANDRE.asString)
      .setFrom("ANDRE <" + ANDRE.asString + ">")
      .setTo(BOB.asString)
      .setSubject("My awesome subject")
      .setBody("This is the content", StandardCharsets.UTF_8)
      .build

    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), bobInboxPath, AppendCommand.builder().build(message))
      .getMessageId
  }

  def moveMessageFromInboxToArchive(server: GuiceJamesServer, messageId: MessageId): Unit = {
    val archiveMailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(MailboxConstants.USER_NAMESPACE, BOB.asString, DefaultMailboxes.ARCHIVE)

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "update": {
         |        "${messageId.serialize}": {
         |          "mailboxIds": {
         |            "${archiveMailboxId.serialize}": true
         |          }
         |        }
         |      }
         |    }, "c1"]]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
  }

  def copyMessageFromInboxToArchive(server: GuiceJamesServer, messageId: MessageId): Unit = {
    val inboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(MailboxConstants.USER_NAMESPACE, BOB.asString, DefaultMailboxes.INBOX)
    val archiveMailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(MailboxConstants.USER_NAMESPACE, BOB.asString, DefaultMailboxes.ARCHIVE)

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "update": {
         |        "${messageId.serialize}": {
         |          "mailboxIds": {
         |            "${inboxId.serialize}": true,
         |            "${archiveMailboxId.serialize}": true
         |          }
         |        }
         |      }
         |    }, "c1"]]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
  }

  def listMessageIdsArchive(server: GuiceJamesServer): util.ArrayList[String] = {
    val archiveMailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(MailboxConstants.USER_NAMESPACE, BOB.asString, DefaultMailboxes.ARCHIVE)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$ACCOUNT_ID",
         |      "filter": {"inMailbox": "${archiveMailboxId.serialize}"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post()
    .`then`
      .statusCode(SC_OK)
      .extract
      .body
      .path("methodResponses[0][1].ids")
  }
}
