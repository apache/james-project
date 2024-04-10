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
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.JMAPTestingConstants.{DOMAIN, LOCALHOST_IP}
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ANDRE, BOB, BOB_PASSWORD, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.ImapKeywordsConsistencyContract.bobInboxPath
import org.apache.james.mailbox.DefaultMailboxes
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.{MailboxConstants, MailboxPath, MessageId}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.modules.protocols.ImapGuiceProbe
import org.apache.james.utils.{DataProbeImpl, TestIMAPClient}
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.junit.jupiter.api.{BeforeEach, Disabled, Test}
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

import scala.jdk.CollectionConverters._

object ImapKeywordsConsistencyContract {
  private val bobInboxPath = MailboxPath.forUser(BOB, DefaultMailboxes.INBOX)
}

trait ImapKeywordsConsistencyContract {
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

  @ParameterizedTest
  @ValueSource(strings = Array(
    DefaultMailboxes.INBOX,
    DefaultMailboxes.ARCHIVE
  ))
  def emailGetShouldUnionKeywordsWhenInconsistencyCreatedViaImap(mailbox: String, server: GuiceJamesServer): Unit = {
    // Given the user has a message "m1" in "inbox" mailbox with subject "My awesome subject", content "This is the content"
    val messageId = appendMessageToInbox(server)

    // And bob copies "m1" from mailbox "inbox" to mailbox "archive"
    copyMessageFromInboxToArchive(server, messageId)
    awaitAtMostOneMinute.until(() => listMessageIdsArchive(server).size == 1)

    // And the user has an open IMAP connection with mailbox "<mailbox>" selected
    imapClient.connect(LOCALHOST_IP, server.getProbe(classOf[ImapGuiceProbe]).getImapPort)
      .login(BOB, BOB_PASSWORD)
      .select(mailbox)

    // And the user set flags via IMAP to "(\Flagged)" for all messages in mailbox "<mailbox>"
    imapClient.setFlagsForAllMessagesInMailbox("\\Flagged")

    // When the user ask for message "m1"
    // Then no error is returned
    // And the list should contain 1 message
    // And the id of the message is "m1"
    // And the keywords of the message is "(\Flagged)"
    val ids = listMessageIds().asScala.toList

    assertThat(ids.size).isEqualTo(1)
    assertThat(ids).isEqualTo(List(messageId.serialize))

    val idString = concatMessageIds(ids)
    val response = getMessagesByIds(idString)

    assertThatJson(response)
      .inPath("methodResponses[0][1].list[0].keywords")
      .isEqualTo("{\"$flagged\": true}")
  }

  @Disabled("No intersection on JMAP RFC, all keywords are just unioned")
  @ParameterizedTest
  @ValueSource(strings = Array(
    DefaultMailboxes.INBOX,
    DefaultMailboxes.ARCHIVE
  ))
  def emailGetShouldIntersectDraftWhenInconsistencyCreatedViaImap(mailbox: String, server: GuiceJamesServer): Unit = {
    // Given the user has a message "m1" in "inbox" mailbox with subject "My awesome subject", content "This is the content"
    val messageId = appendMessageToInbox(server)

    // And user copies "m1" from mailbox "inbox" to mailbox "archive"
    copyMessageFromInboxToArchive(server, messageId)
    awaitAtMostOneMinute.until(() => listMessageIdsArchive(server).size == 1)

    // And the user has an open IMAP connection with mailbox "<mailbox>" selected
    imapClient.connect(LOCALHOST_IP, server.getProbe(classOf[ImapGuiceProbe]).getImapPort)
      .login(BOB, BOB_PASSWORD)
      .select(mailbox)

    // And the user set flags via IMAP to "(\Draft)" for all messages in mailbox "<mailbox>"
    imapClient.setFlagsForAllMessagesInMailbox("\\Draft")

    // When the user ask for message "m1"
    // Then no error is returned
    // And the list should contain 1 message
    // And the id of the message is "m1"
    // And the keywords of the message is <keyword>
    val ids = listMessageIds().asScala.toList

    assertThat(ids.size).isEqualTo(1)
    assertThat(ids).isEqualTo(List(messageId.serialize))

    val idString = concatMessageIds(ids)
    val response = getMessagesByIds(idString)

    assertThatJson(response)
      .inPath("methodResponses[0][1].list[0].keywords")
      .isEqualTo("")
  }

  @Test
  def emailQueryShouldReturnMatchingMessageIdWhenMatchingInAtLeastOneMailbox(server: GuiceJamesServer): Unit = {
    // Given the user has a message "m1" in "inbox" mailbox with subject "My awesome subject", content "This is the content"
    val messageId = appendMessageToInbox(server)

    // And user copies "m1" from mailbox "inbox" to mailbox "archive"
    copyMessageFromInboxToArchive(server, messageId)
    awaitAtMostOneMinute.until(() => listMessageIdsArchive(server).size == 1)

    // And the user has an open IMAP connection with mailbox "archive" selected
    imapClient.connect(LOCALHOST_IP, server.getProbe(classOf[ImapGuiceProbe]).getImapPort)
      .login(BOB, BOB_PASSWORD)
      .select(DefaultMailboxes.ARCHIVE)

    // And the user set flags via IMAP to "(\Flagged)" for all messages in mailbox "archive"
    imapClient.setFlagsForAllMessagesInMailbox("\\Flagged")

    // When the user asks for message list with flag "$Flagged"
    val ids = listMessageIdsBykeyword("$Flagged").asScala.toList

    // Then the message list has size 1
    // And the message list contains "m1"
    assertThat(ids.size).isEqualTo(1)
    assertThat(ids).isEqualTo(List(messageId.serialize))
  }

  @Test
  def emailQueryInSpecificMailboxShouldReturnMessageIdWhenMatching(server: GuiceJamesServer): Unit = {
    // Given the user has a message "m1" in "inbox" mailbox with subject "My awesome subject", content "This is the content"
    val messageId = appendMessageToInbox(server)

    // And user copies "m1" from mailbox "inbox" to mailbox "archive"
    copyMessageFromInboxToArchive(server, messageId)
    awaitAtMostOneMinute.until(() => listMessageIdsArchive(server).size == 1)

    // And the user has an open IMAP connection with mailbox "archive" selected
    imapClient.connect(LOCALHOST_IP, server.getProbe(classOf[ImapGuiceProbe]).getImapPort)
      .login(BOB, BOB_PASSWORD)
      .select(DefaultMailboxes.ARCHIVE)

    // And the user set flags via IMAP to "(\Flagged)" for all messages in mailbox "archive"
    imapClient.setFlagsForAllMessagesInMailbox("\\Flagged")

    // When user asks for message list in mailbox "archive" with flag "$Flagged"
    val ids = listMessageIdsByMailboxAndKeyword(server, DefaultMailboxes.ARCHIVE, "$Flagged").asScala.toList

    // Then the message list has size 1
    // And the message list contains "m1"
    assertThat(ids.size).isEqualTo(1)
    assertThat(ids).isEqualTo(List(messageId.serialize))
  }

  @Test
  def emailQueryInSpecificMailboxShouldSkipMessageIdWhenNotMatching(server: GuiceJamesServer): Unit = {
    // Given the user has a message "m1" in "inbox" mailbox with subject "My awesome subject", content "This is the content"
    val messageId = appendMessageToInbox(server)

    // And user copies "m1" from mailbox "inbox" to mailbox "archive"
    copyMessageFromInboxToArchive(server, messageId)
    awaitAtMostOneMinute.until(() => listMessageIdsArchive(server).size == 1)

    // And the user has an open IMAP connection with mailbox "archive" selected
    imapClient.connect(LOCALHOST_IP, server.getProbe(classOf[ImapGuiceProbe]).getImapPort)
      .login(BOB, BOB_PASSWORD)
      .select(DefaultMailboxes.ARCHIVE)

    // And the user set flags via IMAP to "(\Flagged)" for all messages in mailbox "archive"
    imapClient.setFlagsForAllMessagesInMailbox("\\Flagged")

    // When user asks for message list in mailbox "inbox" with flag "$Flagged"
    val ids = listMessageIdsByMailboxAndKeyword(server, DefaultMailboxes.INBOX, "$Flagged").asScala.toList

    // Then the message list is empty
    assertThat(ids.size).isEqualTo(0)
  }

  @Test
  def emailSetShouldSucceedToSolveKeywordsConflictsIntroducedViaImapUponFlagsAddition(server: GuiceJamesServer): Unit = {
    // Given the user has a message "m1" in "inbox" mailbox with subject "My awesome subject", content "This is the content"
    val messageId = appendMessageToInbox(server)

    // And user copies "m1" from mailbox "inbox" to mailbox "archive"
    copyMessageFromInboxToArchive(server, messageId)
    awaitAtMostOneMinute.until(() => listMessageIdsArchive(server).size == 1)

    // And the user has an open IMAP connection with mailbox "archive" selected
    imapClient.connect(LOCALHOST_IP, server.getProbe(classOf[ImapGuiceProbe]).getImapPort)
      .login(BOB, BOB_PASSWORD)
      .select(DefaultMailboxes.ARCHIVE)

    // And the user set flags via IMAP to "(\Flagged)" for all messages in mailbox "archive"
    imapClient.setFlagsForAllMessagesInMailbox("\\Flagged")

    // When user sets flags "$Flagged" on message "m1"
    emailSetFlags(messageId, "$Flagged")

    // Then user asks for message list in mailbox "archive" with flag "$Flagged"
    val idsArchive = listMessageIdsByMailboxAndKeyword(server, DefaultMailboxes.ARCHIVE, "$Flagged").asScala.toList

    // And the message list has size 1
    assertThat(idsArchive.size).isEqualTo(1)

    // And the message list contains "m1"
    assertThat(idsArchive).isEqualTo(List(messageId.serialize))

    // And user asks for message list in mailbox "inbox" with flag "$Flagged"
    val idsInbox = listMessageIdsByMailboxAndKeyword(server, DefaultMailboxes.INBOX, "$Flagged").asScala.toList

    // And the message list has size 1
    assertThat(idsInbox.size).isEqualTo(1)

    // And the message list contains "m1"
    assertThat(idsInbox).isEqualTo(List(messageId.serialize))
  }

  @Test
  def emailSetShouldIgnoreKeywordsConflictIntroducedViaImapUponFlagsDeletionWithEmailQuery(server: GuiceJamesServer): Unit = {
    // Given the user has a message "m1" in "inbox" mailbox with subject "My awesome subject", content "This is the content"
    val messageId = appendMessageToInbox(server)

    // And user copies "m1" from mailbox "inbox" to mailbox "archive"
    copyMessageFromInboxToArchive(server, messageId)
    awaitAtMostOneMinute.until(() => listMessageIdsArchive(server).size == 1)

    // And the user has an open IMAP connection with mailbox "archive" selected
    imapClient.connect(LOCALHOST_IP, server.getProbe(classOf[ImapGuiceProbe]).getImapPort)
      .login(BOB, BOB_PASSWORD)
      .select(DefaultMailboxes.ARCHIVE)

    // And the user set flags via IMAP to "(\Flagged)" for all messages in mailbox "archive"
    imapClient.setFlagsForAllMessagesInMailbox("\\Flagged")

    // When user sets flags "$Answered" on message "m1"
    emailSetFlags(messageId, "$Answered")

    // Then user asks for message list in mailbox "archive" with flag "$Flagged"
    val idsArchive = listMessageIdsByMailboxAndKeyword(server, DefaultMailboxes.ARCHIVE, "$Flagged").asScala.toList

    // And the message list is empty
    assertThat(idsArchive.size).isEqualTo(0)

    // And user asks for message list in mailbox "inbox" with flag "$Flagged"
    val idsInbox = listMessageIdsByMailboxAndKeyword(server, DefaultMailboxes.INBOX, "$Flagged").asScala.toList

    // And the message list is empty
    assertThat(idsInbox.size).isEqualTo(0)

    // Then user asks for message list in mailbox "archive" with flag "$Answered"
    val idsAnsweredArchive = listMessageIdsByMailboxAndKeyword(server, DefaultMailboxes.ARCHIVE, "$Answered").asScala.toList

    // And the message list has size 1
    assertThat(idsAnsweredArchive.size).isEqualTo(1)

    // And the message list contains "m1"
    assertThat(idsAnsweredArchive).isEqualTo(List(messageId.serialize))

    // And user asks for message list in mailbox "inbox" with flag "$Answered"
    val idsAnsweredInbox = listMessageIdsByMailboxAndKeyword(server, DefaultMailboxes.INBOX, "$Answered").asScala.toList

    // And the message list has size 1
    assertThat(idsAnsweredInbox.size).isEqualTo(1)

    // And the message list contains "m1"
    assertThat(idsAnsweredInbox).isEqualTo(List(messageId.serialize))
  }

  @Test
  def emailSetShouldIgnoreKeywordsConflictIntroducedViaImapUponFlagsDeletionWithEmailGet(server: GuiceJamesServer): Unit = {
    // Given the user has a message "m1" in "inbox" mailbox with subject "My awesome subject", content "This is the content"
    val messageId = appendMessageToInbox(server)

    // And user copies "m1" from mailbox "inbox" to mailbox "archive"
    copyMessageFromInboxToArchive(server, messageId)
    awaitAtMostOneMinute.until(() => listMessageIdsArchive(server).size == 1)

    // And the user has an open IMAP connection with mailbox "archive" selected
    imapClient.connect(LOCALHOST_IP, server.getProbe(classOf[ImapGuiceProbe]).getImapPort)
      .login(BOB, BOB_PASSWORD)
      .select(DefaultMailboxes.ARCHIVE)

    // And the user set flags via IMAP to "(\Flagged)" for all messages in mailbox "archive"
    imapClient.setFlagsForAllMessagesInMailbox("\\Flagged")

    // When user sets flags "$Answered" on message "m1"
    emailSetFlags(messageId, "$Answered")

    // Then the user ask for message "m1"
    // And no error is returned
    // And the list should contain 1 message
    // And the id of the message is "m1"
    // And the keywords of the message is $Answered
    val ids = listMessageIds().asScala.toList

    assertThat(ids.size).isEqualTo(1)
    assertThat(ids).isEqualTo(List(messageId.serialize))

    val idString = concatMessageIds(ids)
    val response = getMessagesByIds(idString)

    assertThatJson(response)
      .inPath("methodResponses[0][1].list[0].keywords")
      .isEqualTo("{\"$answered\": true}")
  }

  private def appendMessageToInbox(server: GuiceJamesServer): MessageId = {
    val message = Message.Builder
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
    val archiveMailboxId = server.getProbe(classOf[MailboxProbeImpl])
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
    val inboxId = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(MailboxConstants.USER_NAMESPACE, BOB.asString, DefaultMailboxes.INBOX)
    val archiveMailboxId = server.getProbe(classOf[MailboxProbeImpl])
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

  def listMessageIds(): util.ArrayList[String] = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$ACCOUNT_ID"
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

  def listMessageIdsBykeyword(keyword: String): util.ArrayList[String] = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$ACCOUNT_ID",
         |      "filter" : {
         |        "hasKeyword": "$keyword"
         |      }
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

  def listMessageIdsArchive(server: GuiceJamesServer): util.ArrayList[String] = {
    val archiveMailboxId = server.getProbe(classOf[MailboxProbeImpl])
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

  def listMessageIdsByMailboxAndKeyword(server: GuiceJamesServer, mailbox: String, keyword: String): util.ArrayList[String] = {
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(MailboxConstants.USER_NAMESPACE, BOB.asString, mailbox)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$ACCOUNT_ID",
         |      "filter": {
         |        "inMailbox": "${mailboxId.serialize}",
         |        "hasKeyword": "$keyword"
         |      }
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

  def getMessagesByIds(idString: String): String = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |     "Email/get",
         |     {
         |       "accountId": "$ACCOUNT_ID",
         |       "ids": [$idString]
         |     },
         |     "c1"]]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString
  }

  private def emailSetFlags(messageId: MessageId, flag: String): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "update": {
         |        "${messageId.serialize}":{
         |          "keywords": {
         |            "$flag": true
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

  private def concatMessageIds(ids: List[String]): String =
    ids.map(id => "\"" + id + "\"")
      .mkString(",")
}
