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

package org.apache.james.jmap.rfc8621.contract

import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.concurrent.TimeUnit

import com.google.common.hash.Hashing
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.model.UTCDate
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.{MailboxPath, MessageId}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.awaitility.Awaitility
import org.awaitility.Duration.ONE_HUNDRED_MILLISECONDS
import org.junit.jupiter.api.{BeforeEach, Test}

trait EmailQueryMethodContract {

  private lazy val slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS
  private lazy val calmlyAwait = Awaitility.`with`
    .pollInterval(slowPacedPollInterval)
    .and.`with`.pollDelay(slowPacedPollInterval)
    .await
  private lazy val awaitAtMostTenSeconds = calmlyAwait.atMost(10, TimeUnit.SECONDS)

  private lazy val UTC_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)
      .addUser(ANDRE.asString, ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  @Test
  def shouldListMailsInAllUserMailboxes(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    val requestDate = Date.from(ZonedDateTime.now().minusDays(1).toInstant)
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().withInternalDate(requestDate).build(message))
      .getMessageId

    val messageId2: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "75128aab4b1b",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId2, messageId1)}",
           |                "canCalculateChanges": false,
           |                "position": 0,
           |                "limit": 256,
           |                "ids": ["${messageId2.serialize()}", "${messageId1.serialize()}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def shouldNotListMailsFromOtherUserMailboxes(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(ANDRE, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(message))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "75128aab4b1b",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId1)}",
           |                "canCalculateChanges": false,
           |                "position": 0,
           |                "limit": 256,
           |                "ids": ["${messageId1.serialize()}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def listMailsShouldBeSortedByDescendingOrderOfArrivalByDefault(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    val requestDateMessage1 = Date.from(ZonedDateTime.now().minusDays(1).toInstant)
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().withInternalDate(requestDateMessage1).build(message))
      .getMessageId
    val requestDateMessage2 = Date.from(ZonedDateTime.now().minusDays(1).plusHours(1).toInstant)
    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().withInternalDate(requestDateMessage2).build(message))
      .getMessageId
    val requestDateMessage3 = Date.from(ZonedDateTime.now().minusDays(1).plusHours(2).toInstant)
    val messageId3 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().withInternalDate(requestDateMessage3).build(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

    assertThatJson(response)
      .inPath("$.methodResponses[0][1].ids")
      .isEqualTo(s"""["${messageId3.serialize()}", "${messageId2.serialize()}", "${messageId1.serialize()}"]""")
    }
  }

  @Test
  def listMailsShouldBeIdempotent(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(message))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val responseFirstCall = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      val responseSecondCall = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(responseFirstCall).isEqualTo(responseSecondCall)
    }
  }

  @Test
  def shouldListMailsInASpecificUserMailboxes(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    val otherMailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(message))
      .getMessageId
    val messageId2: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "inMailbox": "${otherMailboxId.serialize()}"
         |        }
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(response)
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId2.serialize()}"]""")
    }
  }

  @Test
  def shouldReturnIllegalArgumentErrorForAnUnknownSpecificUserMailboxes(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    val otherMailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(message))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    server.getProbe(classOf[MailboxProbeImpl]).deleteMailbox(otherMailboxPath.getNamespace, BOB.asString(), otherMailboxPath.getName)

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "inMailbox": "${otherMailboxId.serialize()}"
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "75128aab4b1b",
           |    "methodResponses": [
           |        [
           |            "error",
           |            {
           |                "type": "invalidArguments",
           |                "description": "${otherMailboxId.serialize()} can not be found"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
    }
  }

  @Test
  def shouldListMailsNotInASpecificUserMailboxes(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    val otherMailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(message))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "inMailboxOtherThan": [ "${otherMailboxId.serialize()}" ]
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(response)
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId1.serialize()}"]""")
    }
  }

  @Test
  def shouldListMailsNotInZeroMailboxes(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    val requestDateMessage1 = Date.from(ZonedDateTime.now().minusDays(1).toInstant)
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().withInternalDate(requestDateMessage1).build(message))
      .getMessageId
    val messageId2: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "inMailboxOtherThan": [  ]
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(response)
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId2.serialize()}", "${messageId1.serialize()}"]""")
    }
  }

  @Test
  def listMailsInAFirstMailboxAndNotSomeOtherMailboxShouldReturnMailsInFirstMailbox(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val inbox = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    val otherMailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(message))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "inMailbox":  "${inbox.serialize()}",
         |        "inMailboxOtherThan": [ "${otherMailboxId.serialize()}" ]
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(response)
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId1.serialize()}"]""")
    }
  }

  @Test
  def listMailsInAFirstMailboxAndNotInTheSameMailboxShouldReturnEmptyResult(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val inbox = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(message))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "inMailbox":  "${inbox.serialize()}",
         |        "inMailboxOtherThan": [ "${inbox.serialize()}" ]
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(response)
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo("[]")
    }
  }

  @Test
  def shouldListMailsReceivedBeforeADate(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val requestDate = ZonedDateTime.now().minusDays(1)
    val messageId1 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withInternalDate(Date.from(requestDate.toInstant)).build(message))
      .getMessageId


    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "before": "${UTCDate(requestDate.plusHours(1)).asUTC.format(UTC_DATE_FORMAT)}"
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(response)
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId1.serialize()}"]""")
    }
  }
  @Test
  def shouldListMailsReceivedBeforeADateInclusively(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val requestDate = ZonedDateTime.now().minusDays(1)
    val messageId1 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withInternalDate(Date.from(requestDate.toInstant)).build(message))
      .getMessageId

    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "before": "${UTCDate(requestDate).asUTC.format(UTC_DATE_FORMAT)}"
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(response)
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId1.serialize()}"]""")
    }
  }

  private def generateQueryState(messages: MessageId*): String = {
    Hashing.murmur3_32()
      .hashUnencodedChars(messages.toList.map(_.serialize()).mkString(" "))
      .toString
  }
}
