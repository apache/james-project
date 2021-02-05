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
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.jmap.draft.JmapGuiceProbe
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.MailboxACL.Right
import org.apache.james.mailbox.model.{MailboxACL, MailboxPath}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.{ACLProbeImpl, MailboxProbeImpl}
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.junit.jupiter.api.{BeforeEach, Test}
import sttp.capabilities.WebSockets
import sttp.client3.monad.IdMonad
import sttp.client3.okhttp.OkHttpSyncBackend
import sttp.client3.{Identity, RequestT, SttpBackend, asWebSocket, basicRequest}
import sttp.model.Uri
import sttp.monad.MonadError
import sttp.monad.syntax.MonadErrorOps
import sttp.ws.WebSocketFrame
import sttp.ws.WebSocketFrame.Text

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
  def apiRequestsShouldBeProcessedWhenNoRequestId(server: GuiceJamesServer): Unit = {
    val response: Either[String, String] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, String] {
          ws =>
            ws.send(WebSocketFrame.text(
              """{
                |  "@type": "Request",
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
                   |  "requestId":null,
                   |  "sessionState":"2c9f1b12-b35a-43e6-9af2-0106fb53a943",
                   |  "methodResponses":[["Core/echo",{"arg1":"arg1data","arg2":"arg2data"},"c1"]]
                   |}""".stripMargin)
  }

  @Test
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
    val emailState: String = jmapGuiceProbe.getLatestEmailState(accountId).getValue.toString
    val mailboxState: String = jmapGuiceProbe.getLatestMailboxState(accountId).getValue.toString

    val mailboxStateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Mailbox":"$mailboxState"}}}"""
    val emailStateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Email":"$emailState"}}}"""

    assertThat(response.toOption.get.asJava)
      .hasSize(3) // email notification + mailbox notification + API response
      .contains(mailboxStateChange, emailStateChange)
  }

  @Test
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
    val emailState: String = jmapGuiceProbe.getLatestEmailState(accountId).getValue.toString
    val mailboxState: String = jmapGuiceProbe.getLatestMailboxState(accountId).getValue.toString

    val mailboxStateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Mailbox":"$mailboxState"}}}"""
    val emailStateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Email":"$emailState"}}}"""

    assertThat(response.toOption.get.asJava)
      .hasSize(3) // email notification + mailbox notification + API response
      .contains(mailboxStateChange, emailStateChange)
  }

  @Test
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
    val emailState: String = jmapGuiceProbe.getLatestEmailState(accountId).getValue.toString
    val mailboxState: String = jmapGuiceProbe.getLatestMailboxState(accountId).getValue.toString

    val mailboxStateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Mailbox":"$mailboxState"}}}"""
    val emailStateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Email":"$emailState"}}}"""

    assertThat(response.toOption.get.asJava)
      .hasSize(3) // email notification + mailbox notification + API response
      .contains(mailboxStateChange, emailStateChange)
  }

  @Test
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
    val emailState: String = jmapGuiceProbe.getLatestEmailState(accountId).getValue.toString
    val mailboxState: String = jmapGuiceProbe.getLatestMailboxState(accountId).getValue.toString

    val mailboxStateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Mailbox":"$mailboxState"}}}"""
    val emailStateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Email":"$emailState"}}}"""

    assertThat(response.toOption.get.asJava)
      .hasSize(2) // No Email notification
      .contains(mailboxStateChange)
      .doesNotContain(emailStateChange)
  }

  @Test
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
    val emailState: String = jmapGuiceProbe.getLatestEmailStateWithDelegation(accountId).getValue.toString
    val mailboxState: String = jmapGuiceProbe.getLatestMailboxStateWithDelegation(accountId).getValue.toString

    val mailboxStateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Mailbox":"$mailboxState"}}}"""
    val emailStateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Email":"$emailState"}}}"""

    assertThat(response.toOption.get.asJava)
      .hasSize(2) // email notification + mailbox notification
      .contains(mailboxStateChange, emailStateChange)
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
