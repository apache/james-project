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

import javax.mail.Flags
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.api.change.State
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.jmap.core.PushState
import org.apache.james.jmap.draft.JmapGuiceProbe
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.{MailboxPath, MessageId}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test, Timeout}
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

trait WebSocketAggregateContract {
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
  def pushEnableRequestsShouldBeProcessed(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val accountId: AccountId = AccountId.fromUsername(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val message: Message = Fixture.createTestMessage
    val flags: Flags = new Flags(Flags.Flag.ANSWERED)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobPath, AppendCommand.builder()
      .withFlags(flags)
      .build(message))
      .getMessageId



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
                 |      "update": {
                 |        "${messageId.serialize()}":{
                 |          "keywords": {
                 |             "$$flagged": true
                 |          }
                 |        }
                 |      }
                 |    }, "c1"],
                 |    ["Mailbox/set", {
                 |      "accountId": "$ACCOUNT_ID",
                 |      "create": {
                 |        "C42": {
                 |          "name": "myMailbox"
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

    Thread.sleep(100)

    val jmapGuiceProbe: JmapGuiceProbe = server.getProbe(classOf[JmapGuiceProbe])
    val emailState: State = jmapGuiceProbe.getLatestEmailState(accountId)
    val mailboxState: State = jmapGuiceProbe.getLatestMailboxState(accountId)

    val globalState1: String = PushState.fromOption(Some(mailboxState), None).get.value
    val stateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Mailbox":"${mailboxState.getValue}","Email":"${emailState.getValue}"}},"pushState":"$globalState1"}"""

    assertThat(response.toOption.get.asJava)
      .hasSize(2) // aggregated notification + API response
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
}
