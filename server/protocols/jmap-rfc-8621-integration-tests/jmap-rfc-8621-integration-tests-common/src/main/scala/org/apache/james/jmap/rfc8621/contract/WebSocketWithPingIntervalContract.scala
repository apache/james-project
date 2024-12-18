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

import java.net.URI
import java.util.UUID

import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import okhttp3.OkHttpClient
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.JmapGuiceProbe
import org.apache.james.jmap.api.change.State
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.jmap.core.{PushState, UuidState}
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{AfterEach, Test, Timeout}
import sttp.capabilities.WebSockets
import sttp.client3.monad.IdMonad
import sttp.client3.okhttp.OkHttpSyncBackend
import sttp.client3.{Identity, RequestT, SttpBackend, asWebSocket, basicRequest}
import sttp.model.Uri
import sttp.monad.MonadError
import sttp.ws.WebSocketFrame.Text
import sttp.ws.{WebSocket, WebSocketFrame}

import scala.jdk.CollectionConverters._

trait WebSocketWithPingIntervalContract {
  private lazy val backend: SttpBackend[Identity, WebSockets] = OkHttpSyncBackend()
  private lazy implicit val monadError: MonadError[Identity] = IdMonad

  def startJmapServer(overrideJmapProperties: Map[String, Object]): GuiceJamesServer

  def stopJmapServer(): Unit

  @AfterEach
  def afterEach(): Unit = {
    stopJmapServer()
  }

  private def setUpJmapServer(overrideJmapProperties: Map[String, Object] = Map.empty): GuiceJamesServer = {
    val server = startJmapServer(overrideJmapProperties)
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)
      .addUser(BOB.asString(), BOB_PASSWORD)
      .addUser(DAVID.asString(), "secret")
    server
  }

  @Test
  @Timeout(180)
  def apiRequestsShouldBeProcessedWhenClientPingInterval(): Unit = {
    val server = setUpJmapServer()
    // Given client sends PING frame interval 2s
    val intervalDurationInMillis = 2000
    val backend: SttpBackend[Identity, WebSockets] = OkHttpSyncBackend.usingClient(new OkHttpClient.Builder()
      .pingInterval(intervalDurationInMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
      .build())

    // The websocket connection is keep alive during client ping interval
    val response: Either[String, WebSocketFrame] = authenticatedRequest(server)
      .response(asWebSocket[Identity, WebSocketFrame] { ws: WebSocket[Identity] =>
        sendEchoTextFrame(ws)
        Thread.sleep(intervalDurationInMillis * 3)
        ws.receive()
      })
      .send(backend)
      .body

    val responseAsFrame = response.toOption.get
    assertThat(responseAsFrame).isInstanceOf(classOf[WebSocketFrame.Text])
    assertThatJson(responseAsFrame.asPayload)
      .isEqualTo(
        """{
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
  def apiRequestsShouldBeProcessedWhenConfigurePingIntervalResponse(): Unit = {
    // Given a server with configured ping interval of 2s
    val server = setUpJmapServer(Map("websocket.ping.interval" -> "2s"))

    val requestId1 = UUID.randomUUID().toString
    val requestId2 = UUID.randomUUID().toString
    val response: Either[String, List[WebSocketFrame]] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, List[WebSocketFrame]] {
          ws =>
            sendEchoTextFrame(ws, requestId1)
            Thread.sleep(2000)
            val frame1 = ws.receive()
            sendEchoTextFrame(ws, requestId2)
            Thread.sleep(2000)
            val frame2 = ws.receive()
            List(frame1, frame2)
        })
        .send(backend)
        .body

    val listResponseFrame = response.toOption.get.map(_.asInstanceOf[Text]).map(_.payload)
    assertThat(listResponseFrame.asJava).hasSize(2)
    assertThat(listResponseFrame.filter(frame => frame.contains(requestId1)).asJava).hasSize(1)
    assertThat(listResponseFrame.filter(frame => frame.contains(requestId2)).asJava).hasSize(1)
  }

  @Test
  @Timeout(180)
  def pushEnableRequestsShouldBeProcessedWhenConfigurePingIntervalResponse(): Unit = {
    val server = setUpJmapServer(Map("websocket.ping.interval" -> "2s"))

    val bobPath = MailboxPath.inbox(BOB)
    val accountId: AccountId = AccountId.fromUsername(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

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
    val stateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Email":"${emailState.getValue}","Mailbox":"${mailboxState.getValue}"}},"pushState":"$globalState"}""".stripMargin

    assertThat(response.toOption.get.asJava)
      .hasSize(2)
      .contains(stateChange)
  }

  private def authenticatedRequest(server: GuiceJamesServer): RequestT[Identity, Either[String, String], Any] = {
    val port = server.getProbe(classOf[JmapGuiceProbe])
      .getJmapPort
      .getValue
    basicRequest.get(Uri.apply(new URI(s"ws://127.0.0.1:$port/jmap/ws")))
      .header("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
      .header("Accept", ACCEPT_RFC8621_VERSION_HEADER)
  }

  private def sendEchoTextFrame(ws: WebSocket[Identity], requestId: String = "req-36"): Identity[Unit] = {
    ws.send(WebSocketFrame.text(
      s"""{
        |  "@type": "Request",
        |  "id": "$requestId",
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
  }
}
