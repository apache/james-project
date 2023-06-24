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

import java.net.{ProtocolException, URI}
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.api.change.State
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.jmap.core.{PushState, UuidState}
import org.apache.james.jmap.draft.JmapGuiceProbe
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.MailboxACL.Right
import org.apache.james.mailbox.model.{MailboxACL, MailboxPath}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.protocols.SmtpGuiceProbe
import org.apache.james.modules.{ACLProbeImpl, MailboxProbeImpl}
import org.apache.james.utils.{DataProbeImpl, SMTPMessageSender, SpoolerProbe}
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.awaitility.core.ConditionFactory
import org.junit.jupiter.api.{BeforeEach, Test, Timeout}
import play.api.libs.json.{JsString, Json}
import reactor.core.scala.publisher.SMono
import reactor.core.scheduler.Schedulers
import sttp.capabilities.WebSockets
import sttp.client3.monad.IdMonad
import sttp.client3.okhttp.OkHttpSyncBackend
import sttp.client3.{Identity, RequestT, SttpBackend, asWebSocket, basicRequest}
import sttp.model.Uri
import sttp.monad.MonadError
import sttp.ws.{WebSocket, WebSocketFrame}

import scala.jdk.CollectionConverters._
import scala.util.Try

trait WebSocketContract {
  private lazy val awaitAtMostTenSeconds: ConditionFactory = Awaitility.`with`
    .pollInterval(ONE_HUNDRED_MILLISECONDS)
    .and.`with`.pollDelay(ONE_HUNDRED_MILLISECONDS)
    .await
    .atMost(10, TimeUnit.SECONDS)
  private lazy val backend: SttpBackend[Identity, WebSockets] = OkHttpSyncBackend()
  private lazy implicit val monadError: MonadError[Identity] = IdMonad

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)
      .addUser(BOB.asString(), BOB_PASSWORD)
      .addUser(DAVID.asString(), "secret")
  }

  @Test
  @Timeout(180)
  def apiRequestsShouldBeProcessed(server: GuiceJamesServer): Unit = {
    val response: Either[String, String] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, String] {
          ws =>
            ws.send(WebSocketFrame.text(
              """{
                |  "@type": "Request",
                |  "id": "req-36",
                |  "using": [ "urn:ietf:params:jmap:core"],
                |  "methodCalls": [
                |    [
                |      "Core/echo",
                |      {
                |        "arg1": "arg1data",
                |        "arg2": "arg2data"
                |      },
                |      "c1"
                |    ]
                |  ]
                |}""".stripMargin))

            ws.receive().asPayload
        })
        .send(backend)
        .body

    assertThatJson(response.toOption.get)
      .isEqualTo("""{
                   |  "@type":"Response",
                   |  "requestId":"req-36",
                   |  "sessionState":"2c9f1b12-b35a-43e6-9af2-0106fb53a943",
                   |  "methodResponses":[
                   |    ["Core/echo",
                   |      {
                   |        "arg1":"arg1data",
                   |        "arg2":"arg2data"
                   |      },"c1"]
                   |  ]
                   |}
                   |""".stripMargin)
  }

  @Test
  @Timeout(180)
  def executingSeveralAPICallsShouldBePossible(server: GuiceJamesServer): Unit = {
    val response: Either[String, List[String]] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, List[String]] {
          ws =>
            List({
              ws.send(WebSocketFrame.text(
                """{
                  |  "@type": "Request",
                  |  "id": "req-36",
                  |  "using": [ "urn:ietf:params:jmap:core"],
                  |  "methodCalls": [
                  |    [
                  |      "Core/echo",
                  |      {
                  |        "arg1": "1",
                  |        "arg2": "arg2data"
                  |      },
                  |      "c1"
                  |    ]
                  |  ]
                  |}""".stripMargin))
              ws.receive().asPayload
            }, {
              Thread.sleep(200)

              ws.send(WebSocketFrame.text(
                """{
                  |  "@type": "Request",
                  |  "id": "req-36",
                  |  "using": [ "urn:ietf:params:jmap:core"],
                  |  "methodCalls": [
                  |    [
                  |      "Core/echo",
                  |      {
                  |        "arg1": "2",
                  |        "arg2": "arg2data"
                  |      },
                  |      "c1"
                  |    ]
                  |  ]
                  |}""".stripMargin))

              ws.receive().asPayload
            })
        })
        .send(backend)
        .body

    assertThat(response.toOption.get.asJava)
      .hasSize(2)
  }

  @Test
  @Timeout(180)
  def nonJsonPayloadShouldTriggerError(server: GuiceJamesServer): Unit = {
    val response: Either[String, String] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, String] {
          ws =>
            ws.send(WebSocketFrame.text("The quick brown fox"))

            ws.receive().asPayload
        })
        .send(backend)
        .body

    assertThatJson(response.toOption.get)
      .isEqualTo("""{
                   |  "status":400,
                   |  "detail":"The request was successfully parsed as JSON but did not match the type signature of the Request object: List((,List(JsonValidationError(List(Unrecognized token 'The': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')\n at [Source: (String)\"The quick brown fox\"; line: 1, column: 4]),List()))))",
                   |  "type":"urn:ietf:params:jmap:error:notRequest",
                   |  "requestId":null,
                   |  "@type":"RequestError"
                   |}""".stripMargin)
  }

  @Test
  @Timeout(180)
  def handshakeShouldBeAuthenticated(server: GuiceJamesServer): Unit = {
    assertThatThrownBy(() =>
      unauthenticatedRequest(server)
        .response(asWebSocket[Identity, String] {
          ws =>
            ws.send(WebSocketFrame.text("The quick brown fox"))

          ws.receive().asPayload
      })
      .send(backend)
      .body)
      .hasRootCause(new ProtocolException("Expected HTTP 101 response but was '401 Unauthorized'"))
  }

  @Test
  @Timeout(180)
  def noTypeFieldShouldTriggerError(server: GuiceJamesServer): Unit = {
    val response: Either[String, String] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, String] {
          ws =>
            ws.send(WebSocketFrame.text(
              """{
                |  "id": "req-36",
                |  "using": [ "urn:ietf:params:jmap:core"],
                |  "methodCalls": [
                |    [
                |      "Core/echo",
                |      {
                |        "arg1": "arg1data",
                |        "arg2": "arg2data"
                |      },
                |      "c1"
                |    ]
                |  ]
                |}""".stripMargin))

            ws.receive().asPayload
        })
        .send(backend)
        .body

    assertThatJson(response.toOption.get)
      .isEqualTo("""{
                   |  "status":400,
                   |  "detail":"The request was successfully parsed as JSON but did not match the type signature of the Request object: List((,List(JsonValidationError(List(Missing @type field on a webSocket inbound message),List()))))",
                   |  "type":"urn:ietf:params:jmap:error:notRequest",
                   |  "requestId":null,
                   |  "@type":"RequestError"
                   |}""".stripMargin)
  }

  @Test
  @Timeout(180)
  def badTypeFieldShouldTriggerError(server: GuiceJamesServer): Unit = {
    val response: Either[String, String] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, String] {
          ws =>
            ws.send(WebSocketFrame.text(
              """{
                |  "@type": 42,
                |  "id": "req-36",
                |  "using": [ "urn:ietf:params:jmap:core"],
                |  "methodCalls": [
                |    [
                |      "Core/echo",
                |      {
                |        "arg1": "arg1data",
                |        "arg2": "arg2data"
                |      },
                |      "c1"
                |    ]
                |  ]
                |}""".stripMargin))

            ws.receive().asPayload
        })
        .send(backend)
        .body

    assertThatJson(response.toOption.get)
      .isEqualTo("""{
                   |  "status":400,
                   |  "detail":"The request was successfully parsed as JSON but did not match the type signature of the Request object: List((,List(JsonValidationError(List(Invalid @type field on a webSocket inbound message: expecting a JsString, got 42),List()))))",
                   |  "type":"urn:ietf:params:jmap:error:notRequest",
                   |  "requestId":null,
                   |  "@type":"RequestError"
                   |}""".stripMargin)
  }

  @Test
  @Timeout(180)
  def unknownTypeFieldShouldTriggerError(server: GuiceJamesServer): Unit = {
    val response: Either[String, String] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, String] {
          ws =>
            ws.send(WebSocketFrame.text(
              """{
                |  "@type": "unknown",
                |  "id": "req-36",
                |  "using": [ "urn:ietf:params:jmap:core"],
                |  "methodCalls": [
                |    [
                |      "Core/echo",
                |      {
                |        "arg1": "arg1data",
                |        "arg2": "arg2data"
                |      },
                |      "c1"
                |    ]
                |  ]
                |}""".stripMargin))

            ws.receive().asPayload
        })
        .send(backend)
        .body

    assertThatJson(response.toOption.get)
      .isEqualTo("""{
                   |  "status":400,
                   |  "detail":"The request was successfully parsed as JSON but did not match the type signature of the Request object: List((,List(JsonValidationError(List(Unknown @type field on a webSocket inbound message: unknown),List()))))",
                   |  "type":"urn:ietf:params:jmap:error:notRequest",
                   |  "requestId":null,
                   |  "@type":"RequestError"
                   |}""".stripMargin)
  }

  @Test
  @Timeout(180)
  def clientSendingARespondTypeFieldShouldTriggerError(server: GuiceJamesServer): Unit = {
    val response: Either[String, String] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, String] {
          ws =>
            ws.send(WebSocketFrame.text(
              """{
                |  "@type": "Response",
                |  "id": "req-36",
                |  "using": [ "urn:ietf:params:jmap:core"],
                |  "methodCalls": [
                |    [
                |      "Core/echo",
                |      {
                |        "arg1": "arg1data",
                |        "arg2": "arg2data"
                |      },
                |      "c1"
                |    ]
                |  ]
                |}""".stripMargin))

            ws.receive().asPayload
        })
        .send(backend)
        .body

    assertThatJson(response.toOption.get)
      .isEqualTo("""{
                   |  "status":400,
                   |  "detail":"The request was successfully parsed as JSON but did not match the type signature of the Request object: List((,List(JsonValidationError(List(Unknown @type field on a webSocket inbound message: Response),List()))))",
                   |  "type":"urn:ietf:params:jmap:error:notRequest",
                   |  "requestId":null,
                   |  "@type":"RequestError"
                   |}""".stripMargin)
  }

  @Test
  @Timeout(180)
  def requestLevelErrorShouldReturnAPIError(server: GuiceJamesServer): Unit = {
    val response: Either[String, String] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, String] {
          ws =>
            ws.send(WebSocketFrame.text(
              """{
                |  "@type": "Request",
                |  "using": [
                |    "urn:ietf:params:jmap:core",
                |    "urn:ietf:params:jmap:mail"],
                |  "methodCalls": [[
                |      "Mailbox/get",
                |      {
                |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                |        "properties": ["invalidProperty"]
                |      },
                |      "c1"]]
                |}""".stripMargin))

            ws.receive().asPayload
        })
        .send(backend)
        .body

    assertThatJson(response.toOption.get)
      .isEqualTo("""{
                   |  "@type": "Response",
                   |  "requestId": null,
                   |  "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
                   |  "methodResponses": [["error",{"type":"invalidArguments","description":"The following properties [invalidProperty] do not exist."},"c1"]]
                   |}""".stripMargin)
  }

  @Test
  @Timeout(180)
  def pushEnableRequestsShouldBeProcessed(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val accountId: AccountId = AccountId.fromUsername(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    Thread.sleep(100)

    val response: Either[String, List[String]] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, List[String]] {
          ws =>
            ws.send(WebSocketFrame.text(
              """{
                |  "@type": "WebSocketPushEnable",
                |  "dataTypes": ["Mailbox", "Email"]
                |}""".stripMargin))

            Thread.sleep(100)

            ws.send(WebSocketFrame.text(
              s"""{
                 |  "@type": "Request",
                 |  "id": "req-36",
                 |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
                 |  "methodCalls": [
                 |    ["Email/set", {
                 |      "accountId": "$ACCOUNT_ID",
                 |      "create": {
                 |        "aaaaaa":{
                 |          "mailboxIds": {
                 |             "${mailboxId.serialize}": true
                 |          }
                 |        }
                 |      }
                 |    }, "c1"]]
                 |}""".stripMargin))

            List(
              ws.receive().asPayload,
              ws.receive().asPayload)
        })
        .send(backend)
        .body

    Thread.sleep(100)

    val jmapGuiceProbe: JmapGuiceProbe = server.getProbe(classOf[JmapGuiceProbe])
    val emailState: State = jmapGuiceProbe.getLatestEmailState(accountId)
    val mailboxState: State = jmapGuiceProbe.getLatestMailboxState(accountId)

    val globalState: String = PushState.fromOption(Some(UuidState.fromJava(mailboxState)), Some(UuidState.fromJava(emailState))).get.value
    val stateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Email":"${emailState.getValue}","Mailbox":"${mailboxState.getValue}"}},"pushState":"$globalState"}""".stripMargin

    assertThat(response.toOption.get.asJava)
      .hasSize(2) // state change notification + API response
      .contains(stateChange)
  }

  @Test
  @Timeout(180)
  def shouldPushChangesToDelegatedUser(server: GuiceJamesServer): Unit = {
    val davidPath = MailboxPath.inbox(DAVID)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(davidPath)

    // DAVID delegates BOB to access his account
    server.getProbe(classOf[DelegationProbe]).addAuthorizedUser(DAVID, BOB)

    Thread.sleep(100)

    val response: Either[String, List[String]] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, List[String]] {
          ws =>
            ws.send(WebSocketFrame.text(
              """{
                |  "@type": "WebSocketPushEnable",
                |  "dataTypes": ["EmailDelivery"]
                |}""".stripMargin))

            Thread.sleep(100)

            // DAVID has a new mail therefore EmailDelivery change
            sendEmailTo(server, DAVID)

            List(
              ws.receive().asPayload)
        })
        .send(backend)
        .body

    Thread.sleep(100)

    // Bob should receive DAVID's EmailDelivery state change
    assertThat(response.toOption.get.asJava)
      .hasSize(1)

    assertThatJson(response.toOption.get.asJava.get(0))
      .isEqualTo(s"""{"@type":"StateChange","changed":{"$DAVID_ACCOUNT_ID":{"EmailDelivery":"$${json-unit.ignore}"}},"pushState":"$${json-unit.ignore}"}""".stripMargin)
  }

  @Test
  @Timeout(180)
  def ownerUserShouldStillReceiveHisChangesWhenHeDelegatesHisAccountToOtherUsers(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    // BOB delegates DAVID to access his account
    server.getProbe(classOf[DelegationProbe]).addAuthorizedUser(BOB, DAVID)

    Thread.sleep(100)

    val response: Either[String, List[String]] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, List[String]] {
          ws =>
            ws.send(WebSocketFrame.text(
              """{
                |  "@type": "WebSocketPushEnable",
                |  "dataTypes": ["EmailDelivery"]
                |}""".stripMargin))

            Thread.sleep(100)

            // BOB has a new mail therefore EmailDelivery change
            sendEmailTo(server, BOB)

            List(
              ws.receive().asPayload)
        })
        .send(backend)
        .body

    Thread.sleep(100)

    // Bob should receive his EmailDelivery state change
    assertThat(response.toOption.get.asJava)
      .hasSize(1)

    assertThatJson(response.toOption.get.asJava.get(0))
      .isEqualTo(s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"EmailDelivery":"$${json-unit.ignore}"}},"pushState":"$${json-unit.ignore}"}""".stripMargin)
  }

  @Test
  @Timeout(180)
  def bobShouldReceiveHisChangesAndHisDelegatedAccountChanges(server: GuiceJamesServer): Unit = {
    val davidPath = MailboxPath.inbox(DAVID)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(davidPath)

    // DAVID delegates BOB to access his account
    server.getProbe(classOf[DelegationProbe]).addAuthorizedUser(DAVID, BOB)

    Thread.sleep(100)

    val response: Either[String, List[String]] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, List[String]] {
          ws =>
            ws.send(WebSocketFrame.text(
              """{
                |  "@type": "WebSocketPushEnable",
                |  "dataTypes": ["EmailDelivery"]
                |}""".stripMargin))

            Thread.sleep(100)

            sendEmailTo(server, DAVID)
            sendEmailTo(server, BOB)
            sendEmailTo(server, DAVID)
            sendEmailTo(server, BOB)

            List(
              ws.receive().asPayload,
              ws.receive().asPayload,
              ws.receive().asPayload,
              ws.receive().asPayload)
        })
        .send(backend)
        .body

    Thread.sleep(100)

    // Bob should receive DAVID's change and his changes
    assertThat(response.toOption.get.asJava)
      .hasSize(4)
    assertThatJson(response.toOption.get.asJava.get(0))
      .isEqualTo(s"""{"@type":"StateChange","changed":{"$DAVID_ACCOUNT_ID":{"EmailDelivery":"$${json-unit.ignore}"}},"pushState":"$${json-unit.ignore}"}""".stripMargin)
    assertThatJson(response.toOption.get.asJava.get(1))
      .isEqualTo(s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"EmailDelivery":"$${json-unit.ignore}"}},"pushState":"$${json-unit.ignore}"}""".stripMargin)
    assertThatJson(response.toOption.get.asJava.get(2))
      .isEqualTo(s"""{"@type":"StateChange","changed":{"$DAVID_ACCOUNT_ID":{"EmailDelivery":"$${json-unit.ignore}"}},"pushState":"$${json-unit.ignore}"}""".stripMargin)
    assertThatJson(response.toOption.get.asJava.get(1))
      .isEqualTo(s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"EmailDelivery":"$${json-unit.ignore}"}},"pushState":"$${json-unit.ignore}"}""".stripMargin)
  }

  @Test
  @Timeout(180)
  def mixingPushAndResponsesShouldBeSupported(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    Thread.sleep(100)

    def createEmail(ws: WebSocket[Identity]): Identity[Unit] = ws.send(WebSocketFrame.text(
        s"""{
           |  "@type": "Request",
           |  "id": "req-36",
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
           |  "methodCalls": [
           |    ["Email/set", {
           |      "accountId": "$ACCOUNT_ID",
           |      "create": {
           |        "aaaaaa":{
           |          "mailboxIds": {
           |             "${mailboxId.serialize}": true
           |          }
           |        }
           |      }
           |    }, "c1"]]
           |}""".stripMargin))

    val response: Either[String, List[String]] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, List[String]] {
          ws =>
            ws.send(WebSocketFrame.text(
              """{
                |  "@type": "WebSocketPushEnable",
                |  "dataTypes": ["Mailbox", "Email"]
                |}""".stripMargin))
            Thread.sleep(100)
            createEmail(ws)
            createEmail(ws)
            createEmail(ws)
            createEmail(ws)
            createEmail(ws)

            List.range(0, 10)
              .map(i => ws.receive().asPayload)
        })
        .send(backend)
        .body

    // 5 changes, each one generate one response, one state change
    assertThat(response.toOption.get.asJava).hasSize(10)
  }

  @Test
  @Timeout(180)
  def pushShouldHandleVacationResponses(server: GuiceJamesServer): Unit = {
    val response: Either[String, List[String]] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, List[String]] {
          ws =>
            ws.send(WebSocketFrame.text(
              """{
                |  "@type": "WebSocketPushEnable",
                |  "dataTypes": ["VacationResponse"]
                |}""".stripMargin))

            Thread.sleep(100)

            ws.send(WebSocketFrame.text(
              s"""{
                 |  "@type": "Request",
                 |  "id": "req-36",
                 |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:vacationresponse"],
                 |  "methodCalls": [
                 |    ["VacationResponse/set", {
                 |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                 |      "update": {
                 |        "singleton": {
                 |          "isEnabled": true,
                 |          "fromDate": "2014-10-30T14:12:00Z",
                 |          "toDate": "2014-11-30T14:12:00Z",
                 |          "subject": "I am in vacation",
                 |          "textBody": "I'm currently enjoying life. Please disturb me later",
                 |          "htmlBody": "I'm currently enjoying <b>life</b>. <br/>Please disturb me later"
                 |        }
                 |      }
                 |    }, "c1"]]
                 |}""".stripMargin))

            List(
              ws.receive().asPayload,
              ws.receive().asPayload)
        })
        .send(backend)
        .body

    assertThat(response.toOption.get.asJava).hasSize(2) // vacation notification + API response
    assertThat(response.toOption.get.filter(s => s.contains(""""@type":"StateChange"""")).asJava)
      .hasSize(1)
      .allMatch(s => s.contains("VacationResponse"))
  }

  @Test
  @Timeout(180)
  // For client compatibility purposes
  def specifiedUnHandledDataTypesShouldNotBeRejected(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val accountId: AccountId = AccountId.fromUsername(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    Thread.sleep(100)

    val response: Either[String, List[String]] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, List[String]] {
          ws =>
            ws.send(WebSocketFrame.text(
              """{
                |  "@type": "WebSocketPushEnable",
                |  "dataTypes": ["Mailbox", "Email", "VacationResponse", "Thread", "Identity", "EmailSubmission", "EmailDelivery"]
                |}""".stripMargin))

            Thread.sleep(100)

            ws.send(WebSocketFrame.text(
              s"""{
                 |  "@type": "Request",
                 |  "id": "req-36",
                 |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
                 |  "methodCalls": [
                 |    ["Email/set", {
                 |      "accountId": "$ACCOUNT_ID",
                 |      "create": {
                 |        "aaaaaa":{
                 |          "mailboxIds": {
                 |             "${mailboxId.serialize}": true
                 |          }
                 |        }
                 |      }
                 |    }, "c1"]]
                 |}""".stripMargin))

            List(
              ws.receive().asPayload,
              ws.receive().asPayload)
        })
        .send(backend)
        .body

    Thread.sleep(100)

    val jmapGuiceProbe: JmapGuiceProbe = server.getProbe(classOf[JmapGuiceProbe])
    val mailboxState: State = jmapGuiceProbe.getLatestMailboxState(accountId)
    val emailState: State = jmapGuiceProbe.getLatestEmailState(accountId)

    val globalState: String = PushState.fromOption(Some(UuidState.fromJava(mailboxState)), Some(UuidState.fromJava(emailState))).get.value
    val stateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Email":"${emailState.getValue}","Mailbox":"${mailboxState.getValue}"}},"pushState":"$globalState"}""".stripMargin

    assertThat(response.toOption.get.asJava)
      .hasSize(2) // state change notification + API response
      .contains(stateChange)
  }

  @Test
  @Timeout(180)
  // For client compatibility purposes
  def emailDeliveryShouldNotIncludeFlagUpdatesAndDeletes(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    Thread.sleep(100)

    val response: Either[String, List[String]] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, List[String]] {
          ws =>
            ws.send(WebSocketFrame.text(
              s"""{
                 |  "@type": "Request",
                 |  "id": "req-36",
                 |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
                 |  "methodCalls": [
                 |    ["Email/set", {
                 |      "accountId": "$ACCOUNT_ID",
                 |      "create": {
                 |        "aaaaaa":{
                 |          "mailboxIds": {
                 |             "${mailboxId.serialize}": true
                 |          }
                 |        }
                 |      }
                 |    }, "c1"]]
                 |}""".stripMargin))

            val responseAsJson = Json.parse(ws.receive().asPayload)
              .\("methodResponses")
              .\(0).\(1)
              .\("created")
              .\("aaaaaa")

            val messageId = responseAsJson
              .\("id")
              .get.asInstanceOf[JsString].value

            Thread.sleep(100)

            ws.send(WebSocketFrame.text(
              """{
                |  "@type": "WebSocketPushEnable",
                |  "dataTypes": ["Mailbox", "Email", "VacationResponse", "Thread", "Identity", "EmailSubmission", "EmailDelivery"]
                |}""".stripMargin))

            Thread.sleep(100)

            ws.send(WebSocketFrame.text(
              s"""{
                 |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
                 |  "@type": "Request",
                 |  "methodCalls": [
                 |    ["Email/set", {
                 |      "accountId": "$ACCOUNT_ID",
                 |      "update": {
                 |        "$messageId":{
                 |          "keywords": {
                 |             "music": true
                 |          }
                 |        }
                 |      }
                 |    }, "c1"]]
                 |}""".stripMargin))

            val stateChange1 = ws.receive().asPayload
            val response1 = ws.receive().asPayload

            Thread.sleep(100)

            ws.send(WebSocketFrame.text(
              s"""{
                 |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
                 |  "@type": "Request",
                 |  "methodCalls": [
                 |    ["Email/set", {
                 |      "accountId": "$ACCOUNT_ID",
                 |      "destroy": ["$messageId"]
                 |    }, "c1"]]
                 |}""".stripMargin))

            Thread.sleep(100)

            val stateChange2 = ws.receive().asPayload
            val response2 = ws.receive().asPayload

            List(response1, response2, stateChange1, stateChange2)
        })
        .send(backend)
        .body

    assertThat(response.toOption.get.asJava)
      .hasSize(4) // update flags response + email state change notif + destroy response + email state change notif + mailbox state change notif (count)
    assertThat(response.toOption.get.filter(s => s.startsWith("{\"@type\":\"StateChange\"")).asJava)
      .hasSize(2)
      .noneMatch(s => s.contains("EmailDelivery"))
  }

  @Test
  @Timeout(180)
  def dataTypesShouldDefaultToAll(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val accountId: AccountId = AccountId.fromUsername(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    Thread.sleep(100)

    val response: Either[String, List[String]] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, List[String]] {
          ws =>
            ws.send(WebSocketFrame.text(
              """{
                |  "@type": "WebSocketPushEnable",
                |  "dataTypes": null
                |}""".stripMargin))

            Thread.sleep(100)

            ws.send(WebSocketFrame.text(
              s"""{
                 |  "@type": "Request",
                 |  "id": "req-36",
                 |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
                 |  "methodCalls": [
                 |    ["Email/set", {
                 |      "accountId": "$ACCOUNT_ID",
                 |      "create": {
                 |        "aaaaaa":{
                 |          "mailboxIds": {
                 |             "${mailboxId.serialize}": true
                 |          }
                 |        }
                 |      }
                 |    }, "c1"]]
                 |}""".stripMargin))

            List(
              ws.receive().asPayload,
              ws.receive().asPayload)
        })
        .send(backend)
        .body

    Thread.sleep(100)

    val jmapGuiceProbe: JmapGuiceProbe = server.getProbe(classOf[JmapGuiceProbe])
    val emailState: State = jmapGuiceProbe.getLatestEmailState(accountId)
    val mailboxState: State = jmapGuiceProbe.getLatestMailboxState(accountId)

    val globalState: String = PushState.fromOption(Some(UuidState.fromJava(mailboxState)), Some(UuidState.fromJava(emailState))).get.value
    val stateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Email":"${emailState.getValue}","Mailbox":"${mailboxState.getValue}"}},"pushState":"$globalState"}""".stripMargin

    assertThat(response.toOption.get.asJava)
      .hasSize(2) // state change notification + API response
      .contains(stateChange)
  }

  @Test
  @Timeout(180)
  def shouldPushEmailDeliveryChangeWhenUserReceivesEmail(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))

    val response: Either[String, List[String]] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, List[String]] {
          ws =>
            ws.send(WebSocketFrame.text(
              """{
                |  "@type": "WebSocketPushEnable",
                |  "dataTypes": null
                |}""".stripMargin))

            Thread.sleep(100)

            // Andre send mail to Bob
            sendEmailTo(server, BOB)

            List(
              ws.receive().asPayload)
        })
        .send(backend)
        .body

    // Bob should receive EmailDelivery state change
    assertThat(response.toOption.get.asJava)
      .hasSize(1) // state change notification
      .allMatch(s => s.contains("EmailDelivery"))
  }

  @Test
  @Timeout(180)
  def shouldNotPushEmailDeliveryChangeWhenCreateDraftMail(server: GuiceJamesServer): Unit = {
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))

    val response: Either[String, List[String]] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, List[String]] {
          ws =>
            ws.send(WebSocketFrame.text(
              """{
                |  "@type": "WebSocketPushEnable",
                |  "dataTypes": null
                |}""".stripMargin))

            Thread.sleep(100)

            ws.send(WebSocketFrame.text(
              s"""{
                 |  "@type": "Request",
                 |  "id": "req-36",
                 |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
                 |  "methodCalls": [
                 |    ["Email/set", {
                 |      "accountId": "$ACCOUNT_ID",
                 |      "create": {
                 |        "aaaaaa":{
                 |          "mailboxIds": {
                 |             "${mailboxId.serialize}": true
                 |          }
                 |        }
                 |      }
                 |    }, "c1"]]
                 |}""".stripMargin))

            List(
              ws.receive().asPayload,
              ws.receive().asPayload)
        })
        .send(backend)
        .body

    assertThat(response.toOption.get.asJava)
      .hasSize(2) // state change notification + API response
      .noneMatch(s => s.contains("EmailDelivery"))
  }

  @Test
  @Timeout(180)
  def pushEnableShouldUpdatePreviousSubscriptions(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val accountId: AccountId = AccountId.fromUsername(BOB)
    Thread.sleep(100)

    val response: Either[String, List[String]] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, List[String]] {
          ws =>
            ws.send(WebSocketFrame.text(
              """{
                |  "@type": "WebSocketPushEnable",
                |  "dataTypes": ["Mailbox", "Email"]
                |}""".stripMargin))

            Thread.sleep(100)

            ws.send(WebSocketFrame.text(
              """{
                |  "@type": "WebSocketPushEnable",
                |  "dataTypes": ["Mailbox"]
                |}""".stripMargin))

            Thread.sleep(100)

            ws.send(WebSocketFrame.text(
              s"""{
                 |  "@type": "Request",
                 |  "id": "req-36",
                 |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
                 |  "methodCalls": [
                 |    ["Email/set", {
                 |      "accountId": "$ACCOUNT_ID",
                 |      "create": {
                 |        "aaaaaa":{
                 |          "mailboxIds": {
                 |             "${mailboxId.serialize}": true
                 |          }
                 |        }
                 |      }
                 |    }, "c1"]]
                 |}""".stripMargin))

            List(ws.receive().asPayload,
            ws.receive().asPayload)
        })
        .send(backend)
        .body

    Thread.sleep(100)
    val jmapGuiceProbe: JmapGuiceProbe = server.getProbe(classOf[JmapGuiceProbe])
    val emailState: State = jmapGuiceProbe.getLatestEmailState(accountId)
    val mailboxState: State = jmapGuiceProbe.getLatestMailboxState(accountId)

    val globalState: String = PushState.fromOption(Some(UuidState.fromJava(mailboxState)), Some(UuidState.fromJava(emailState))).get.value
    val mailboxStateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Mailbox":"${mailboxState.getValue}"}},"pushState":"$globalState"}"""

    assertThat(response.toOption.get.asJava)
      .hasSize(2) // Method response + Mailbox state change, no Email notification
      .contains(mailboxStateChange)
  }

  @Test
  @Timeout(180)
  def pushShouldSupportDelegation(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    val andrePath = MailboxPath.inbox(ANDRE)
    mailboxProbe.createMailbox(andrePath)

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andrePath, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))

    Thread.sleep(100)

    val response: Either[String, List[String]] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, List[String]] {
          ws =>
            ws.send(WebSocketFrame.text(
              """{
                |  "@type": "WebSocketPushEnable",
                |  "dataTypes": ["Mailbox", "Email"]
                |}""".stripMargin))

            Thread.sleep(100)

            val message: Message = Message.Builder
              .of
              .setSubject("test")
              .setBody("testmail", StandardCharsets.UTF_8)
              .build
            mailboxProbe.appendMessage(ANDRE.asString(), andrePath, AppendCommand.from(message))

            Thread.sleep(100)

            List(ws.receive().asPayload)
        })
        .send(backend)
        .body

    Thread.sleep(100)

    val jmapGuiceProbe: JmapGuiceProbe = server.getProbe(classOf[JmapGuiceProbe])
    val accountId: AccountId = AccountId.fromUsername(BOB)
    val emailState: State = jmapGuiceProbe.getLatestEmailStateWithDelegation(accountId)
    val mailboxState: State = jmapGuiceProbe.getLatestMailboxStateWithDelegation(accountId)

    val globalState: String = PushState.fromOption(Some(UuidState.fromJava(mailboxState)), Some(UuidState.fromJava(emailState))).get.value
    val stateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Email":"${emailState.getValue}","Mailbox":"${mailboxState.getValue}"}},"pushState":"$globalState"}""".stripMargin

    assertThat(response.toOption.get.asJava)
      .hasSize(1)
      .contains(stateChange)
  }

  @Test
  @Timeout(180)
  def pushCancelRequestsShouldDisableNotification(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val accountId: AccountId = AccountId.fromUsername(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    Thread.sleep(100)

    val response: Either[String, List[String]] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, List[String]] {
          ws =>
            ws.send(WebSocketFrame.text(
              """{
                |  "@type": "WebSocketPushEnable",
                |  "dataTypes": ["Mailbox", "Email"]
                |}""".stripMargin))

            Thread.sleep(100)

                ws.send(WebSocketFrame.text(
              """{
                |  "@type": "WebSocketPushDisable"
                |}""".stripMargin))

            Thread.sleep(100)

            ws.send(WebSocketFrame.text(
              s"""{
                 |  "@type": "Request",
                 |  "id": "req-36",
                 |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
                 |  "methodCalls": [
                 |    ["Email/set", {
                 |      "accountId": "$ACCOUNT_ID",
                 |      "create": {
                 |        "aaaaaa":{
                 |          "mailboxIds": {
                 |             "${mailboxId.serialize}": true
                 |          }
                 |        }
                 |      }
                 |    }, "c1"]]
                 |}""".stripMargin))

            val response = ws.receive().asPayload

            val maybeNotification: String = Try(SMono.fromCallable(() =>
            ws.receive().asPayload)
              .subscribeOn(Schedulers.newSingle("test"))
              .block(scala.concurrent.duration.Duration.fromNanos(100000000)))
              .fold(e => "No notification received", s => s)

          List(response, maybeNotification)
        })
        .send(backend)
        .body

      Thread.sleep(100)
      val jmapGuiceProbe: JmapGuiceProbe = server.getProbe(classOf[JmapGuiceProbe])
      val emailState: String = jmapGuiceProbe.getLatestEmailState(accountId).getValue.toString
      val mailboxState: String = jmapGuiceProbe.getLatestMailboxState(accountId).getValue.toString

      val mailboxStateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Mailbox":"$mailboxState"}}}"""
      val emailStateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Email":"$emailState"}}}"""

      assertThat(response.toOption.get.asJava)
        .hasSize(2) // Email create response + no notification message
        .contains("No notification received")
        .doesNotContain(mailboxStateChange, emailStateChange)
  }

  @Test
  @Timeout(180)
  def pushCancelRequestAsFirstMessageShouldBeProcessedNormally(server: GuiceJamesServer): Unit = {
    Thread.sleep(100)

    authenticatedRequest(server)
      .response(asWebSocket[Identity, Unit] {
        ws =>
          ws.send(WebSocketFrame.text(
            """{
              |  "@type": "WebSocketPushDisable"
              |}""".stripMargin))

          Thread.sleep(100)

          assertThat(ws.isOpen()).isTrue
      })
      .send(backend)
      .body
  }

  @Test
  @Timeout(180)
  def pushEnableRequestWithPushStateShouldReturnServerState(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val accountId: AccountId = AccountId.fromUsername(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    Thread.sleep(100)

    val response: Either[String, String] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, String] {
          ws =>
            ws.send(WebSocketFrame.text(
              """{
                |  "@type": "WebSocketPushEnable",
                |  "dataTypes": ["Mailbox", "Email"],
                |  "pushState": "aaa"
                |}""".stripMargin))

            Thread.sleep(100)

            ws.receive().asPayload})
        .send(backend)
        .body

    Thread.sleep(100)

    val jmapGuiceProbe: JmapGuiceProbe = server.getProbe(classOf[JmapGuiceProbe])
    val emailState: State = jmapGuiceProbe.getLatestEmailState(accountId)
    val mailboxState: State = jmapGuiceProbe.getLatestMailboxState(accountId)
    val globalState: PushState = PushState.from(UuidState(mailboxState.getValue), UuidState(emailState.getValue))
    val pushEnableResponse: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Mailbox":"${mailboxState.getValue}","Email":"${emailState.getValue}"}},"pushState":"${globalState.value}"}"""

    assertThat(response.toOption.get)
      .isEqualTo(pushEnableResponse)
  }

  private def authenticatedRequest(server: GuiceJamesServer): RequestT[Identity, Either[String, String], Any] = {
    val port = server.getProbe(classOf[JmapGuiceProbe])
      .getJmapPort
      .getValue

    basicRequest.get(Uri.apply(new URI(s"ws://127.0.0.1:$port/jmap/ws")))
      .header("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
      .header("Accept", ACCEPT_RFC8621_VERSION_HEADER)
  }

  private def unauthenticatedRequest(server: GuiceJamesServer): RequestT[Identity, Either[String, String], Any] = {
    val port = server.getProbe(classOf[JmapGuiceProbe])
      .getJmapPort
      .getValue

    basicRequest.get(Uri.apply(new URI(s"ws://127.0.0.1:$port/jmap/ws")))
      .header("Accept", ACCEPT_RFC8621_VERSION_HEADER)
  }

  private def sendEmailTo(server: GuiceJamesServer, recipient: Username): Unit = {
    val smtpMessageSender: SMTPMessageSender = new SMTPMessageSender(DOMAIN.asString())
    smtpMessageSender.connect("127.0.0.1", server.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(ANDRE.asString, ANDRE_PASSWORD)
      .sendMessage(ANDRE.asString, recipient.asString())
    smtpMessageSender.close()

    awaitAtMostTenSeconds.until(() => server.getProbe(classOf[SpoolerProbe]).processingFinished())
  }
}
