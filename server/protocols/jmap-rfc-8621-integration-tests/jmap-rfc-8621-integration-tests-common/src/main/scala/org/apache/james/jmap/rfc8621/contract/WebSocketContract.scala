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

import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.api.change.State
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.jmap.core.{PushState, UuidState}
import org.apache.james.jmap.draft.JmapGuiceProbe
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.MailboxACL.Right
import org.apache.james.mailbox.model.{MailboxACL, MailboxPath}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.{ACLProbeImpl, MailboxProbeImpl}
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
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
import sttp.monad.syntax.MonadErrorOps
import sttp.ws.WebSocketFrame.Text
import sttp.ws.{WebSocket, WebSocketFrame}

import scala.jdk.CollectionConverters._

trait WebSocketContract {
  private lazy val backend: SttpBackend[Identity, WebSockets] = OkHttpSyncBackend()
  private lazy implicit val monadError: MonadError[Identity] = IdMonad

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)
      .addUser(BOB.asString(), BOB_PASSWORD)
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
                |  "requestId": "req-36",
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

            ws.receive()
              .map { case t: Text => t.payload }
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
                  |  "requestId": "req-36",
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
              ws.receive()
                .map {
                  case t: Text => t.payload
                }
            }, {
              Thread.sleep(200)

              ws.send(WebSocketFrame.text(
                """{
                  |  "@type": "Request",
                  |  "requestId": "req-36",
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

              ws.receive()
                .map {
                  case t: Text => t.payload
                }
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

            ws.receive()
              .map { case t: Text => t.payload }
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

          ws.receive()
            .map { case t: Text => t.toString }
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
                |  "requestId": "req-36",
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

            ws.receive()
              .map { case t: Text => t.payload }
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
                |  "requestId": "req-36",
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

            ws.receive()
              .map { case t: Text => t.payload }
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
                |  "requestId": "req-36",
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

            ws.receive()
              .map { case t: Text => t.payload }
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
                |  "requestId": "req-36",
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

            ws.receive()
              .map { case t: Text => t.payload }
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

            ws.receive()
              .map { case t: Text => t.payload }
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
                 |  "requestId": "req-36",
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
              ws.receive()
                .map { case t: Text =>
                  t.payload
                },
              ws.receive()
                .map { case t: Text =>
                  t.payload
                },
              ws.receive()
                .map { case t: Text =>
                  t.payload
                })
        })
        .send(backend)
        .body

    Thread.sleep(100)

    val jmapGuiceProbe: JmapGuiceProbe = server.getProbe(classOf[JmapGuiceProbe])
    val emailState: State = jmapGuiceProbe.getLatestEmailState(accountId)
    val mailboxState: State = jmapGuiceProbe.getLatestMailboxState(accountId)

    val globalState1: String = PushState.fromOption(Some(UuidState.fromJava(mailboxState)), None).get.value
    val globalState2: String = PushState.fromOption(None, Some(UuidState.fromJava(emailState))).get.value
    val mailboxStateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Mailbox":"${mailboxState.getValue}"}},"pushState":"$globalState1"}"""
    val emailStateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Email":"${emailState.getValue}"}},"pushState":"$globalState2"}"""

    assertThat(response.toOption.get.asJava)
      .hasSize(3) // email notification + mailbox notification + API response
      .contains(mailboxStateChange, emailStateChange)
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
           |  "requestId": "req-36",
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

            List.range(0, 15)
              .map(i => ws.receive()
                .map { case t: Text =>
                  t.payload
                })
        })
        .send(backend)
        .body

    // 5 changes, each one generate one response, one email state change, one mailbox state change
    assertThat(response.toOption.get.asJava).hasSize(15)
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
                 |  "requestId": "req-36",
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
              ws.receive()
                .map { case t: Text =>
                  t.payload
                },
              ws.receive()
                .map { case t: Text =>
                  t.payload
                })
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
                 |  "requestId": "req-36",
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
              ws.receive()
                .map { case t: Text =>
                  t.payload
                },
              ws.receive()
                .map { case t: Text =>
                  t.payload
                },
              ws.receive()
                .map { case t: Text =>
                  t.payload
                })
        })
        .send(backend)
        .body

    Thread.sleep(100)

    val jmapGuiceProbe: JmapGuiceProbe = server.getProbe(classOf[JmapGuiceProbe])
    val mailboxState: State = jmapGuiceProbe.getLatestMailboxState(accountId)
    val emailState: State = jmapGuiceProbe.getLatestEmailState(accountId)

    val globalState1: String = PushState.fromOption(Some(UuidState.fromJava(mailboxState)), None).get.value
    val globalState2: String = PushState.fromOption(None, Some(UuidState.fromJava(emailState))).get.value
    val mailboxStateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Mailbox":"${mailboxState.getValue}"}},"pushState":"$globalState1"}"""
    val emailStateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Email":"${emailState.getValue}","EmailDelivery":"${emailState.getValue}"}},"pushState":"$globalState2"}"""

    assertThat(response.toOption.get.asJava)
      .hasSize(3) // email notification + mailbox notification + API response
      .contains(mailboxStateChange, emailStateChange)
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
                 |  "requestId": "req-36",
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

            val responseAsJson = Json.parse(ws.receive()
              .map { case t: Text =>
                t.payload
              })
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

            val stateChange1 = ws.receive()
              .map { case t: Text =>
                t.payload
              }
            val response1 =
              ws.receive()
                .map { case t: Text =>
                  t.payload
                }

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

            val stateChange2 = ws.receive()
              .map { case t: Text =>
                t.payload
              }
            val stateChange3 =
              ws.receive()
                .map { case t: Text =>
                  t.payload
                }
            val response2 =
              ws.receive()
                .map { case t: Text =>
                  t.payload
                }

            List(response1, response2, stateChange1, stateChange2, stateChange3)
        })
        .send(backend)
        .body

    assertThat(response.toOption.get.asJava)
      .hasSize(5) // update flags response + email state change notif + destroy response + email state change notif + mailbox state change notif (count)
    assertThat(response.toOption.get.filter(s => s.startsWith("{\"@type\":\"StateChange\"")).asJava)
      .hasSize(3)
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
                 |  "requestId": "req-36",
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
              ws.receive()
                .map { case t: Text =>
                  t.payload
                },
              ws.receive()
                .map { case t: Text =>
                  t.payload
                },
              ws.receive()
                .map { case t: Text =>
                  t.payload
                })
        })
        .send(backend)
        .body

    Thread.sleep(100)

    val jmapGuiceProbe: JmapGuiceProbe = server.getProbe(classOf[JmapGuiceProbe])
    val emailState: State = jmapGuiceProbe.getLatestEmailState(accountId)
    val mailboxState: State = jmapGuiceProbe.getLatestMailboxState(accountId)

    val globalState1: String = PushState.fromOption(Some(UuidState.fromJava(mailboxState)), None).get.value
    val globalState2: String = PushState.fromOption(None, Some(UuidState.fromJava(emailState))).get.value
    val mailboxStateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Mailbox":"${mailboxState.getValue}"}},"pushState":"$globalState1"}"""
    val emailStateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Email":"${emailState.getValue}","EmailDelivery":"${emailState.getValue}"}},"pushState":"$globalState2"}"""

    assertThat(response.toOption.get.asJava)
      .hasSize(3) // email notification + mailbox notification + API response
      .contains(mailboxStateChange, emailStateChange)
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
                 |  "requestId": "req-36",
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

            List(ws.receive()
              .map { case t: Text =>
                t.payload
            },
            ws.receive()
              .map { case t: Text =>
                t.payload
              })
        })
        .send(backend)
        .body

    Thread.sleep(100)
    val jmapGuiceProbe: JmapGuiceProbe = server.getProbe(classOf[JmapGuiceProbe])
    val emailState: State = jmapGuiceProbe.getLatestEmailState(accountId)
    val mailboxState: State = jmapGuiceProbe.getLatestMailboxState(accountId)

    val globalState1: String = PushState.fromOption(Some(UuidState.fromJava(mailboxState)), None).get.value
    val globalState2: String = PushState.fromOption(None, Some(UuidState.fromJava(emailState))).get.value
    val mailboxStateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Mailbox":"${mailboxState.getValue}"}},"pushState":"$globalState1"}"""
    val emailStateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Email":"${emailState.getValue}"}},"pushState":"$globalState2"}"""

    assertThat(response.toOption.get.asJava)
      .hasSize(2) // No Email notification
      .contains(mailboxStateChange)
      .doesNotContain(emailStateChange)
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

            List(
              ws.receive()
                .map { case t: Text =>
                  t.payload
                },
              ws.receive()
                .map { case t: Text =>
                  t.payload
                })
        })
        .send(backend)
        .body

    Thread.sleep(100)

    val jmapGuiceProbe: JmapGuiceProbe = server.getProbe(classOf[JmapGuiceProbe])
    val accountId: AccountId = AccountId.fromUsername(BOB)
    val emailState: State = jmapGuiceProbe.getLatestEmailStateWithDelegation(accountId)
    val mailboxState: State = jmapGuiceProbe.getLatestMailboxStateWithDelegation(accountId)

    val globalState1: String = PushState.fromOption(Some(UuidState.fromJava(mailboxState)), None).get.value
    val globalState2: String = PushState.fromOption(None, Some(UuidState.fromJava(emailState))).get.value
    val mailboxStateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Mailbox":"${mailboxState.getValue}"}},"pushState":"$globalState1"}"""
    val emailStateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Email":"${emailState.getValue}"}},"pushState":"$globalState2"}"""

    assertThat(response.toOption.get.asJava)
      .hasSize(2) // email notification + mailbox notification
      .contains(mailboxStateChange, emailStateChange)
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
                 |  "requestId": "req-36",
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

            val response = ws.receive()
              .map { case t: Text =>
                t.payload
              }

            val maybeNotification: String = SMono.fromCallable(() =>
            ws.receive()
              .map { case t: Text =>
                t.payload
              })
              .timeout(scala.concurrent.duration.Duration.fromNanos(100000000), Some(SMono.just("No notification received")))
              .subscribeOn(Schedulers.elastic())
              .block()

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
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

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

            ws.receive()
              .map { case t: Text =>
                t.payload
              }
        })
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
}
