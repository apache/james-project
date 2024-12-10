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
import java.util.concurrent.{TimeUnit, TimeoutException}

import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.JmapGuiceProbe
import org.apache.james.jmap.api.change.State
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.jmap.core.{PushState, UuidState}
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.awaitility.core.ConditionFactory
import org.junit.jupiter.api.{AfterEach, Test, Timeout}
import reactor.core.scala.publisher.SMono
import reactor.core.scheduler.Schedulers
import sttp.capabilities.WebSockets
import sttp.client3.monad.IdMonad
import sttp.client3.okhttp.OkHttpSyncBackend
import sttp.client3.{Identity, RequestT, SttpBackend, asWebSocket, basicRequest}
import sttp.model.Uri
import sttp.monad.MonadError
import sttp.ws.WebSocketFrame

import scala.concurrent.duration.SECONDS
import scala.jdk.CollectionConverters._

trait WebSocketWithPingIntervalContract {
  private lazy val awaitAtMostTenSeconds: ConditionFactory = Awaitility.`with`
    .pollInterval(ONE_HUNDRED_MILLISECONDS)
    .and.`with`.pollDelay(ONE_HUNDRED_MILLISECONDS)
    .await
    .atMost(10, TimeUnit.SECONDS)
  private lazy val backend: SttpBackend[Identity, WebSockets] = OkHttpSyncBackend()
  private lazy implicit val monadError: MonadError[Identity] = IdMonad

  def startJmapServer(overrideJmapProperties: Map[String, Object]): GuiceJamesServer

  def stopJmapServer(): Unit

  @AfterEach
  def afterEach(): Unit = {
    stopJmapServer()
  }

  private def setUpJmapServer(overrideJmapProperties: Map[String, Object]): GuiceJamesServer = {
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
  def serverShouldIntervalPingResponseWhenConfigured(): Unit = {
    // Given a server with configured ping interval of 2s
    val server = setUpJmapServer(Map("websocket.ping.interval" -> "2s"))

    val response: Either[String, List[String]] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, List[String]] {
          ws =>
            Thread.sleep(100)
            val ping1 = ws.receive().asPayload
            Thread.sleep(2000)
            val ping2 = ws.receive().asPayload
            Thread.sleep(2000)
            val ping3 = ws.receive().asPayload
            List(ping1, ping2, ping3)
        })
        .send(backend)
        .body

    assertThat(response.toOption.get.asJava).containsExactly("ping", "ping", "ping")
  }

  @Test
  @Timeout(180)
  def serverShouldNotIntervalPingResponseWhenNotConfigured(): Unit = {
    // Given a server with configured ping interval = empty
    val server = setUpJmapServer(Map.empty)

    val getMessagesPublisher: SMono[Either[String, List[String]]] = SMono.fromCallable(()=> authenticatedRequest(server)
        .response(asWebSocket[Identity, List[String]] {
          ws =>
            Thread.sleep(100)
            List(ws.receive().asPayload)
        })
        .send(backend)
        .body)
      .subscribeOn(Schedulers.boundedElastic())

    // In 3 seconds, we should not have any ping response
    assertThatThrownBy(() => getMessagesPublisher
      .subscribeOn(Schedulers.boundedElastic())
      .block(timeout = scala.concurrent.duration.Duration(3, SECONDS)))
      .isInstanceOf[TimeoutException]
  }

  @Test
  @Timeout(180)
  def apiRequestsShouldBeProcessedWhenConfigurePingIntervalResponse(): Unit = {
    val server = setUpJmapServer(Map("websocket.ping.interval" -> "2s"))

    val response: Either[String, List[String]] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, List[String]] {
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
            Thread.sleep(100)
            List(ws.receive().asPayload, ws.receive().asPayload)
        })
        .send(backend)
        .body

    val responseAsList = response.toOption.get
    assertThat(responseAsList.asJava).hasSize(2).contains("ping")
    assertThatJson(responseAsList.filter(message => !message.contains("ping")).head)
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
  def pushEnableRequestsShouldBeProcessedWhenConfigurePingIntervalResponse(): Unit = {
    val server = setUpJmapServer(Map("websocket.ping.interval" -> "2s"))

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

            List(ws.receive().asPayload,
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
      .hasSize(3)
      .contains(stateChange)
      .contains("ping")
  }

  private def authenticatedRequest(server: GuiceJamesServer): RequestT[Identity, Either[String, String], Any] = {
    val port = server.getProbe(classOf[JmapGuiceProbe])
      .getJmapPort
      .getValue
    basicRequest.get(Uri.apply(new URI(s"ws://127.0.0.1:$port/jmap/ws")))
      .header("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
      .header("Accept", ACCEPT_RFC8621_VERSION_HEADER)
  }
}
