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
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZonedDateTime}
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

import com.google.common.hash.Hashing
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import javax.mail.Flags
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.{Arguments, MethodSource}
import org.threeten.extra.Seconds

object EmailQueryMethodContract {
  def jmapSystemKeywords : Stream[Arguments] = {
    Stream.of(
      Arguments.of(new Flags(Flags.Flag.SEEN), "$Seen"),
      Arguments.of(new Flags(Flags.Flag.ANSWERED), "$Answered"),
      Arguments.of(new Flags(Flags.Flag.FLAGGED), "$Flagged"),
      Arguments.of(new Flags(Flags.Flag.DRAFT), "$Draft"),
      Arguments.of(new Flags("$Forwarded"), "$Forwarded"))
  }
}


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
    val messageId1: MessageId = sendMessageToBobInbox(server, message, requestDate)

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
    val messageId1: MessageId = sendMessageToBobInbox(server, message, requestDateMessage1)
    val requestDateMessage2 = Date.from(ZonedDateTime.now().minusDays(1).plusHours(1).toInstant)
    val messageId2 = sendMessageToBobInbox(server, message, requestDateMessage2)
    val requestDateMessage3 = Date.from(ZonedDateTime.now().minusDays(1).plusHours(2).toInstant)
    val messageId3 = sendMessageToBobInbox(server, message, requestDateMessage3)

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
  def listMailsShouldBeSortedInAscendingOrderByDefault(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    val now: Instant = Instant.now()
    val requestDateMessage1: Date = Date.from(now)
    val messageId1 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withInternalDate(requestDateMessage1).build(message))
      .getMessageId
    val requestDateMessage2: Date = Date.from(now.plus(Seconds.of(3)))
    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withInternalDate(requestDateMessage2).build(message))
      .getMessageId
    val requestDateMessage3: Date = Date.from(now.plus(Seconds.of(6)))
    val messageId3 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withInternalDate(requestDateMessage3).build(message))
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
         |      "comparator": [{
         |        "property":"receivedAt"
         |      }]
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
      .isEqualTo(s"""["${messageId1.serialize()}", "${messageId2.serialize()}", "${messageId3.serialize()}"]""")
    }
  }

  @Test
  def listMailsShouldBeSortedInAscendingOrder(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val now: Instant = Instant.now()
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    val requestDateMessage1: Date = Date.from(now)
    val messageId1 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withInternalDate(requestDateMessage1).build(message))
      .getMessageId
    val requestDateMessage2: Date = Date.from(now.plus(Seconds.of(3)))
    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withInternalDate(requestDateMessage2).build(message))
      .getMessageId
    val requestDateMessage3: Date = Date.from(now.plus(Seconds.of(6)))
    val messageId3 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withInternalDate(requestDateMessage3).build(message))
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
         |      "comparator": [{
         |        "property":"receivedAt",
         |        "isAscending": true
         |      }]
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
      .isEqualTo(s"""["${messageId1.serialize()}", "${messageId2.serialize()}", "${messageId3.serialize()}"]""")
    }
  }

  @Test
  def listMailsShouldBeSortedInDescendingOrder(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val now: Instant = Instant.now()
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    val requestDateMessage1: Date = Date.from(now)
    val messageId1 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withInternalDate(requestDateMessage1).build(message))
      .getMessageId
    val requestDateMessage2: Date = Date.from(now.plus(Seconds.of(3)))
    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withInternalDate(requestDateMessage2).build(message))
      .getMessageId
    val requestDateMessage3: Date = Date.from(now.plus(Seconds.of(6)))
    val messageId3 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withInternalDate(requestDateMessage3).build(message))
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
         |      "comparator": [{
         |        "property":"receivedAt",
         |        "isAscending": false
         |      }]
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
  def listMailsShouldReturnErrorWhenPropertyFieldInComparatorIsOmitted(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    val otherMailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
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
         |        },
         |      "comparator": [{
         |        "isAscending":true
         |      }]
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
        .inPath("$.methodResponses[0][1]")
        .isEqualTo("""
         {
            "type": "invalidArguments",
            "description": "{\"errors\":[{\"path\":\"obj.comparator[0].property\",\"messages\":[\"error.path.missing\"]}]}"
          }
         """)
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
  def listMailsShouldReturnErrorWhenPropertyFieldInComparatorIsInvalid(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    val otherMailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
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
         |        },
         |      "comparator": [{
         |        "property":"unsupported",
         |        "isAscending":true
         |      }]
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
        .inPath("$.methodResponses[0][1]")
        .isEqualTo("""
         {
            "type": "invalidArguments",
            "description": "{\"errors\":[{\"path\":\"obj.comparator[0].property\",\"messages\":[\"'unsupported' is not a supported sort property\"]}]}"
          }
         """)
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
    val messageId1: MessageId = sendMessageToBobInbox(server, message, requestDateMessage1)
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
    val messageId1 = sendMessageToBobInbox(server, message, Date.from(requestDate.toInstant))


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
    val messageId1 = sendMessageToBobInbox(server, message, Date.from(requestDate.toInstant))

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

  @Test
  def shouldListMailsReceivedAfterADate(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val receivedDateMessage1 = ZonedDateTime.now().minusDays(1)
    sendMessageToBobInbox(server, message, Date.from(receivedDateMessage1.toInstant))

    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    val receivedDateMessage2 = receivedDateMessage1.plusHours(2)
    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.builder().withInternalDate(Date.from(receivedDateMessage2.toInstant)).build(message))
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
         |        "after": "${UTCDate(receivedDateMessage2.minusHours(1)).asUTC.format(UTC_DATE_FORMAT)}"
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
        .isEqualTo(s"""["${messageId2.serialize()}"]""")
    }
  }

  @Test
  def listMailsReceivedAfterADateShouldBeExclusive(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val receivedDateMessage1 = ZonedDateTime.now().minusDays(1)
    sendMessageToBobInbox(server, message, Date.from(receivedDateMessage1.toInstant))

    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
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
         |        "after": "${UTCDate(receivedDateMessage1).asUTC.format(UTC_DATE_FORMAT)}"
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
        .isEqualTo(s"""["${messageId2.serialize()}"]""")
    }
  }

  @Test
  def shouldLimitResultByTheLimitProvidedByTheClient(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    val requestDate = Date.from(ZonedDateTime.now().minusDays(1).toInstant)
    sendMessageToBobInbox(server, message, Date.from(requestDate.toInstant))

    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
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
         |      "limit": 1
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
           |                "queryState": "${generateQueryState(messageId2)}",
           |                "canCalculateChanges": false,
           |                "position": 0,
           |                "ids": ["${messageId2.serialize()}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def shouldReturnAnIllegalArgumentExceptionIfTheLimitIsNegative(server: GuiceJamesServer): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "limit": -1
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
           |                "description": "The limit can not be negative. -1 was provided."
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
    }
  }

  @Test
  def theLimitshouldBeEnforcedByTheServerIfNoLimitProvidedByTheClient(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))

    val allMessages = (0 to 300).toList.foldLeft(List[MessageId](), ZonedDateTime.now().minusYears(1))((acc, _) => {
      val (messageList, date) = acc
      val dateForNewMessage = date.plusDays(1)
      val messageId = sendMessageToBobInbox(server, message, Date.from(dateForNewMessage.toInstant))
      (messageId :: messageList, dateForNewMessage)
    })

    val expectedMessages = allMessages._1.take(256)

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
        .isEqualTo("[" + expectedMessages.map(message => s""""${message.serialize()}"""").mkString(", ") + "]")

      assertThatJson(response)
        .inPath("$.methodResponses[0][1].limit")
        .isEqualTo("256")
    }
  }

  @Test
  def theLimitshouldBeEnforcedByTheServerIfAGreaterLimitProvidedByTheClient(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))

    val allMessages = (0 to 300).toList.foldLeft(List[MessageId](), ZonedDateTime.now().minusYears(1))((acc, _) => {
      val (messageList, date) = acc
      val dateForNewMessage = date.plusDays(1)
      val messageId = sendMessageToBobInbox(server, message, Date.from(dateForNewMessage.toInstant))
      (messageId :: messageList, dateForNewMessage)
    })

    val expectedMessages = allMessages._1.take(256)

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "limit": 2000
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
        .isEqualTo("[" + expectedMessages.map(message => s""""${message.serialize()}"""").mkString(", ") + "]")

      assertThatJson(response)
        .inPath("$.methodResponses[0][1].limit")
        .isEqualTo("256")
    }
  }

  private def sendMessageToBobInbox(server: GuiceJamesServer, message: Message, requestDate: Date) = {
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().withInternalDate(requestDate).build(message))
      .getMessageId
  }

  @ParameterizedTest
  @MethodSource(value = Array("jmapSystemKeywords"))
  def listMailsBySystemKeywordShouldReturnOnlyMailsWithThisSystemKeyword(keywordFlag: Flags, keywordName: String, server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withFlags(keywordFlag).build(message))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(message))
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
         |        "hasKeyword": "$keywordName"
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
        .isEqualTo(s"""["${messageId.serialize()}"]""")
    }
  }

  @Test
  def hasKeywordShouldRejectEmpty(server: GuiceJamesServer): Unit = {
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
         |        "hasKeyword": ""
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin

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
      .whenIgnoringPaths("methodResponses[0][1].description")
      .isEqualTo("""{
                    |    "sessionState": "75128aab4b1b",
                    |    "methodResponses": [
                    |        [
                    |            "error",
                    |            {
                    |                "type": "invalidArguments"
                    |            },
                    |            "c1"
                    |        ]
                    |    ]
                    |}""".stripMargin)
  }

  @Test
  def hasKeywordShouldRejectTooLong(): Unit = {
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
         |        "hasKeyword": "${"a".repeat(257)}"
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin

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
      .whenIgnoringPaths("methodResponses[0][1].description")
      .isEqualTo("""{
                   |    "sessionState": "75128aab4b1b",
                   |    "methodResponses": [
                   |        [
                   |            "error",
                   |            {
                   |                "type": "invalidArguments"
                   |            },
                   |            "c1"
                   |        ]
                   |    ]
                   |}""".stripMargin)
  }

  @Test
  def hasKeywordShouldNotAcceptIMAPDeletedKeyword(): Unit = {
    val request =
      """{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "hasKeyword": "$Deleted"
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin

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
      .whenIgnoringPaths("methodResponses[0][1].description")
      .isEqualTo(
        s"""{
           |    "sessionState": "75128aab4b1b",
           |    "methodResponses": [
           |        [
           |            "error",
           |            {
           |                "type": "invalidArguments"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def hasKeywordShouldNotAcceptIMAPRecentKeyword(): Unit = {
    val request =
      """{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "hasKeyword": "$Recent"
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin

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
      .whenIgnoringPaths("methodResponses[0][1].description")
      .isEqualTo(
        s"""{
           |    "sessionState": "75128aab4b1b",
           |    "methodResponses": [
           |        [
           |            "error",
           |            {
           |                "type": "invalidArguments"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def hasKeywordShouldRejectInvalid(): Unit = {
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
         |        "hasKeyword": "custom&invalid"
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin

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
      .whenIgnoringPaths("methodResponses[0][1].description")
      .isEqualTo("""{
                   |    "sessionState": "75128aab4b1b",
                   |    "methodResponses": [
                   |        [
                   |            "error",
                   |            {
                   |                "type": "invalidArguments"
                   |            },
                   |            "c1"
                   |        ]
                   |    ]
                   |}""".stripMargin)
  }

  @Test
  def listMailsByCustomKeywordShouldReturnOnlyMailsWithThisCustomKeyword(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withFlags(new Flags("custom")).build(message))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(message))
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
         |        "hasKeyword": "custom"
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
        .isEqualTo(s"""["${messageId.serialize()}"]""")
    }
  }

  @ParameterizedTest
  @MethodSource(value = Array("jmapSystemKeywords"))
  def listMailsNotBySystemKeywordShouldReturnOnlyMailsWithoutThisSystemKeyword(keywordFlag: Flags, keywordName: String, server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withFlags(keywordFlag).build(message))
      .getMessageId
    val messageWithoudFlagId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(message))
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
         |        "notKeyword": "$keywordName"
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
        .isEqualTo(s"""["${messageWithoudFlagId.serialize()}"]""")
    }
  }

  @Test
  def listMailsNotByCustomKeywordShouldReturnOnlyMailsWithoutThisCustomKeyword(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withFlags(new Flags("custom")).build(message))
      .getMessageId
    val messageWithoudFlagId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(message))
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
         |        "notKeyword": "custom"
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
        .isEqualTo(s"""["${messageWithoudFlagId.serialize()}"]""")
    }
  }

  private def generateQueryState(messages: MessageId*): String = {
    Hashing.murmur3_32()
      .hashUnencodedChars(messages.toList.map(_.serialize()).mkString(" "))
      .toString
  }
}
