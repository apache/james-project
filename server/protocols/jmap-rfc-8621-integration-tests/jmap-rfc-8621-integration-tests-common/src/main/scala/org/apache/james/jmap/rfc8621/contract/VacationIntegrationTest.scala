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
import java.util.concurrent.TimeUnit

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured
import io.restassured.RestAssured.`given`
import io.restassured.builder.ResponseSpecBuilder
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.JMAPTestingConstants.DOMAIN
import org.apache.james.jmap.JmapGuiceProbe
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ANDRE, ANDRE_ACCOUNT_ID, ANDRE_PASSWORD, BOB, BOB_PASSWORD, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.VacationIntegrationTest.{andreDraftsPath, html_reason, original_message_text_body, reason}
import org.apache.james.junit.categories.BasicFeature
import org.apache.james.mailbox.DefaultMailboxes
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.{MailboxConstants, MailboxId, MailboxPath, MessageId}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.apache.james.vacation.api.VacationPatch
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.junit.experimental.categories.Category
import org.junit.jupiter.api.{BeforeEach, Test}

object VacationIntegrationTest {
  private val reason = "Message explaining my wonderful vacations"
  private val html_reason = "<b>" + reason + "</b>"
  private val original_message_text_body = "Hello someone, and thank you for joining example.com!"
  private val andreDraftsPath = MailboxPath.forUser(ANDRE, DefaultMailboxes.DRAFTS)
}

trait VacationIntegrationTest {
  private lazy val slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS
  private lazy val calmlyAwait = Awaitility.`with`
    .pollInterval(slowPacedPollInterval)
    .and.`with`.pollDelay(slowPacedPollInterval)
    .await
  private lazy val awaitAtMostTenSeconds = calmlyAwait.atMost(10, TimeUnit.SECONDS)

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN)
      .addUser(BOB.asString, BOB_PASSWORD)
      .addUser(ANDRE.asString, ANDRE_PASSWORD)

    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB.asString, DefaultMailboxes.SENT)
    mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ANDRE.asString, DefaultMailboxes.SENT)

    mailboxProbe.createMailbox(andreDraftsPath)

    RestAssured.requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  @Category(Array(classOf[BasicFeature]))
  @Test
  def jmapVacationShouldGenerateAReplyWhenActive(server: GuiceJamesServer): Unit = {
    /* Test scenario :
      - bob sets a Vacation on its account
      - andre matthieu@mydomain.tld sends bob a mail
      - bob should well receive this mail
      - andre should well receive a notification about bob vacation
    */

    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val bobInboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB.asString, DefaultMailboxes.INBOX)
    val andreInboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ANDRE.asString, DefaultMailboxes.INBOX)

    // Bob sets a Vacation on its account
    setVacationResponse()

    // When
    // andre sends Bob a mail
    andreSendMailToBob(server)

    // Then
    // Bob should well receive this mail
    isMessageReceived(server, ACCOUNT_ID, bobInboxId, BOB, BOB_PASSWORD, original_message_text_body)
    // andre should well receive a notification about user 1 vacation
    isMessageReceived(server, ANDRE_ACCOUNT_ID, andreInboxId, ANDRE, ANDRE_PASSWORD, reason)
  }

  @Test
  def jmapVacationShouldGenerateAReplyEvenWhenNoText(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val bobInboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB.asString, DefaultMailboxes.INBOX)
    val andreInboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ANDRE.asString, DefaultMailboxes.INBOX)

    server.getProbe(classOf[JmapGuiceProbe])
      .modifyVacation(AccountId.fromUsername(BOB), VacationPatch.builder.isEnabled(true).build)
    // When
    andreSendMailToBob(server)
    // Then
    // Bob should well receive this mail
    isMessageReceived(server, ACCOUNT_ID, bobInboxId, BOB, BOB_PASSWORD, original_message_text_body)

    // Andre should well receive a notification about user 1 vacation
    isMessageReceived(server, ANDRE_ACCOUNT_ID, andreInboxId, ANDRE, ANDRE_PASSWORD, "")
  }

  @Test
  def jmapVacationShouldHaveSupportForHtmlMail(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val bobInboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB.asString, DefaultMailboxes.INBOX)
    val andreInboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ANDRE.asString, DefaultMailboxes.INBOX)

    setHtmlVacationResponse()
    // When
    andreSendMailToBob(server)
    // Then
    isMessageReceived(server, ACCOUNT_ID, bobInboxId, BOB, BOB_PASSWORD, original_message_text_body)
    isMessageReceived(server, ANDRE_ACCOUNT_ID, andreInboxId, ANDRE, ANDRE_PASSWORD, reason)
  }

  @Test
  def jmapVacationShouldNotGenerateAReplyWhenInactive(server: GuiceJamesServer): Unit = {
    /* Test scenario :
        - Andre sends User 1 a mail
        - Bob should well receive this mail
        - Andre should not receive a notification
    */
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val bobInboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB.asString, DefaultMailboxes.INBOX)
    val andreInboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ANDRE.asString, DefaultMailboxes.INBOX)

    // When
    // Andre sends User 1 a mail
    andreSendMailToBob(server)
    // Then
    // Bob should well receive this mail
    isMessageReceived(server, ACCOUNT_ID, bobInboxId, BOB, BOB_PASSWORD, original_message_text_body)
    // Andre should not receive a notification
    Thread.sleep(1000L)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$ANDRE_ACCOUNT_ID",
         |      "filter": {"inMailbox": "${andreInboxId.serialize}"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`(
      baseRequestSpecBuilder(server)
        .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
        .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .setBody(request)
        .build, new ResponseSpecBuilder().build)
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .inPath("methodResponses[0][1].ids")
      .isArray
      .hasSize(0)
  }

  @Test
  def jmapVacationShouldNotSendNotificationTwice(server: GuiceJamesServer): Unit = {
    /* Test scenario :
        - Bob sets a Vacation on its account
        - Andre sends Bob a mail
        - Andre sends Bob a second mail
        - Bob should well receive this mail
        - Andre should well receive only one notification about user 1 vacation
    */
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val bobInboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB.asString, DefaultMailboxes.INBOX)
    val andreInboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ANDRE.asString, DefaultMailboxes.INBOX)

    // Bob sets a Vacation on its account
    setVacationResponse()

    // When
    // Andre sends Bob a mail
    andreSendMailToBob(server)
    andreSendMailToBob(server)
    // Then
    // Andre should well receive a notification about user 1 vacation
    isMessageReceived(server, ANDRE_ACCOUNT_ID, andreInboxId, ANDRE, ANDRE_PASSWORD, reason)
    // Andre should not receive another notification
    Thread.sleep(1000L)

    isMessageReceived(server, ANDRE_ACCOUNT_ID, andreInboxId, ANDRE, ANDRE_PASSWORD, reason)
  }

  @Test
  def jmapVacationShouldSendNotificationTwiceWhenVacationReset(server: GuiceJamesServer): Unit = {
    /* Test scenario :
        - Bob sets a Vacation on its account
        - Andre sends Bob a mail
        - Andre sends Bob a second mail
        - Bob should well receive this mail
        - Andre should well receive only one notification about user 1 vacation
    */
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val bobInboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB.asString, DefaultMailboxes.INBOX)
    val andreInboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ANDRE.asString, DefaultMailboxes.INBOX)

    // Bob sets a Vacation on its account
    setVacationResponse()
    // Andre sends Bob a mail
    andreSendMailToBob(server)
    // Wait Bob to receive the eMail before reset of vacation
    isMessageReceived(server, ANDRE_ACCOUNT_ID, andreInboxId, ANDRE, ANDRE_PASSWORD, reason)
    // When
    // Bob resets a Vacation on its account
    setVacationResponse()
    //Andre sends Bob a mail
    andreSendMailToBob(server)
    // Then
    // Andre should well receive two notification about user 1 vacation
    are2MessageReceived(server, ANDRE_ACCOUNT_ID, andreInboxId, ANDRE, ANDRE_PASSWORD)
  }

  private def setVacationResponse(): Unit = {
    val request =
      s"""
         |{
         |  "using": [ "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:vacationresponse" ],
         |  "methodCalls": [
         |    ["VacationResponse/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "update": {
         |        "singleton": {
         |          "isEnabled": true,
         |          "subject": "I am in vacation",
         |          "textBody": "$reason"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}
         |""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
  }

  private def setHtmlVacationResponse(): Unit = {
    val request =
      s"""
         |{
         |  "using": [ "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:vacationresponse" ],
         |  "methodCalls": [
         |    ["VacationResponse/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "update": {
         |        "singleton": {
         |          "isEnabled": true,
         |          "subject": "I am in vacation",
         |          "htmlBody": "$html_reason"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}
         |""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
  }

  private def andreSendMailToBob(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(ANDRE.asString)
      .setFrom("ANDRE <" + ANDRE.asString + ">")
      .setTo(BOB.asString)
      .setBody(original_message_text_body, StandardCharsets.UTF_8)
      .build

    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString(), andreDraftsPath, AppendCommand.builder().build(message))
      .getMessageId

    val requestAndre =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$ANDRE_ACCOUNT_ID",
         |       "create": {
         |         "k1490": {
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${ANDRE.asString}"},
         |             "rcptTo": [{"email": "${BOB.asString}"}]
         |           }
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    `given`(
      baseRequestSpecBuilder(server)
        .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
        .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .setBody(requestAndre)
        .build, new ResponseSpecBuilder().build)
      .post
    .`then`
      .statusCode(SC_OK)
  }

  private def isMessageReceived(server: GuiceJamesServer, accountId: String, mailboxId: MailboxId, username: Username, password: String, expectedBody: String): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$accountId",
         |      "filter": {"inMailbox": "${mailboxId.serialize}"}
         |    },
         |    "c1"], [
         |     "Email/get",
         |     {
         |       "accountId": "$accountId",
         |       "#ids": {
         |         "resultOf":"c1",
         |         "name":"Email/query",
         |         "path":"ids/*"
         |       }
         |     },
         |     "c2"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`(
        baseRequestSpecBuilder(server)
          .setAuth(authScheme(UserCredential(username, password)))
          .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
          .setBody(request)
          .build, new ResponseSpecBuilder().build)
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(response)
        .inPath("methodResponses[0][1].ids")
        .isArray
        .hasSize(1)

      assertThatJson(response)
        .inPath("methodResponses[1][1].list[0].preview")
        .isEqualTo(expectedBody)
    }
  }

  private def are2MessageReceived(server: GuiceJamesServer, accountId: String, mailboxId: MailboxId, username: Username, password: String): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$accountId",
         |      "filter": {"inMailbox": "${mailboxId.serialize}"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`(
        baseRequestSpecBuilder(server)
          .setAuth(authScheme(UserCredential(username, password)))
          .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
          .setBody(request)
          .build, new ResponseSpecBuilder().build)
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(response)
        .inPath("methodResponses[0][1].ids")
        .isArray
        .hasSize(2)
    }
  }
}
