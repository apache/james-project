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
import java.util.concurrent.TimeUnit

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.netty.handler.codec.http.HttpResponseStatus
import io.restassured.RestAssured.{`given`, requestSpecification}
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.JmapGuiceProbe
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DAVID, DAVID_ACCOUNT_ID, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.mailbox.DefaultMailboxes
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.{MailboxPath, MessageId}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.modules.protocols.SmtpGuiceProbe
import org.apache.james.utils.{DataProbeImpl, SMTPMessageSender, SpoolerProbe}
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.awaitility.core.ConditionFactory
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scheduler.Schedulers
import reactor.netty.http.client.HttpClient

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._

trait EventSourceContract {
  private lazy val awaitAtMostTenSeconds: ConditionFactory = Awaitility.`with`
    .pollInterval(ONE_HUNDRED_MILLISECONDS)
    .and.`with`.pollDelay(ONE_HUNDRED_MILLISECONDS)
    .await
    .atMost(10, TimeUnit.SECONDS)

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)
      .addUser(BOB.asString(), BOB_PASSWORD)
      .addUser(DAVID.asString(), "secret")

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()
  }

  @Test
  def typesQueryParamIsCompulsory(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val status = HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?ping=0&closeAfter=no")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .response().block().status()

    assertThat(status.code()).isEqualTo(HttpResponseStatus.BAD_REQUEST.code())
  }

  @Test
  def pingQueryParamIsCompulsory(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val status = HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=*&closeAfter=no")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .response()
      .block()
      .status()

    assertThat(status.code()).isEqualTo(HttpResponseStatus.BAD_REQUEST.code())
  }

  @Test
  def closeAfterQueryParamIsCompulsory(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val status = HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=*&ping=0")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .response()
      .block()
      .status()

    assertThat(status.code()).isEqualTo(HttpResponseStatus.BAD_REQUEST.code())
  }

  @Test
  def shouldRejectInvalidCloseAfter(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val status = HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=*&ping=0&closeAfter=bad")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .response()
      .block()
      .status()

    assertThat(status.code()).isEqualTo(HttpResponseStatus.BAD_REQUEST.code())
  }

  @Test
  def shouldRejectInvalidPing(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val status = HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=*&ping=bad&closeAfter=no")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .response()
      .block()
      .status()

    assertThat(status.code()).isEqualTo(HttpResponseStatus.BAD_REQUEST.code())
  }

  @Test
  def shouldRejectInvalidTypes(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val status = HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=bad&ping=0&closeAfter=no")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .response()
      .block()
      .status()

    assertThat(status.code()).isEqualTo(HttpResponseStatus.BAD_REQUEST.code())
  }

  @Test
  def shouldRejectUnauthenticated(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val status = HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=Email&ping=0&closeAfter=no")
      .headers(builder => {
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .response()
      .block()
      .status()

    assertThat(status.code()).isEqualTo(HttpResponseStatus.UNAUTHORIZED.code())
  }

  @Test
  def noSSEEventShouldBeSentByDefault(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=*&ping=0&closeAfter=no")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .responseContent()
      .map(bb => {
        val bytes = new Array[Byte](bb.readableBytes)
        bb.readBytes(bytes)
        new String(bytes, StandardCharsets.UTF_8)
      })
      .doOnNext(seq.addOne)
      .subscribeOn(Schedulers.boundedElastic())
      .subscribe()

    Thread.sleep(500)

    assertThat(seq.asJava).isEmpty()
  }

  @Test
  def sseEventsShouldBeFilteredByTypes(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=Email&ping=0&closeAfter=no")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .responseContent()
      .map(bb => {
        val bytes = new Array[Byte](bb.readableBytes)
        bb.readBytes(bytes)
        new String(bytes, StandardCharsets.UTF_8)
      })
      .doOnNext(seq.addOne)
      .subscribeOn(Schedulers.boundedElastic())
      .subscribe()

    Thread.sleep(500)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    Thread.sleep(200)

    assertThat(seq.asJava).isEmpty()
  }

  @Test
  def allTypesShouldBeSupported(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=Mailbox,Email,VacationResponse,Thread,Identity,EmailSubmission,EmailDelivery&ping=0&closeAfter=no")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .responseContent()
      .map(bb => {
        val bytes = new Array[Byte](bb.readableBytes)
        bb.readBytes(bytes)
        new String(bytes, StandardCharsets.UTF_8)
      })
      .doOnNext(seq.addOne)
      .subscribeOn(Schedulers.boundedElastic())
      .subscribe()

    Thread.sleep(500)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    Thread.sleep(200)

    assertThat(seq.asJava)
      .hasSize(1)
    assertThat(seq.head)
      .startsWith("event: state\ndata: {\"@type\":\"StateChange\",\"changed\":{\"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6\":{\"Mailbox\":")
    assertThat(seq.head).doesNotContain("pushState")
    assertThat(seq.head).endsWith("\n\n")
  }

  @Test
  def shouldPushEmailDeliveryChangeWhenUserReceivesEmail(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=Mailbox,Email,VacationResponse,Thread,Identity,EmailSubmission,EmailDelivery&ping=0&closeAfter=no")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .responseContent()
      .map(bb => {
        val bytes = new Array[Byte](bb.readableBytes)
        bb.readBytes(bytes)
        new String(bytes, StandardCharsets.UTF_8)
      })
      .doOnNext(seq.addOne)
      .subscribeOn(Schedulers.boundedElastic())
      .subscribe()

    // Bob receives a mail
    Thread.sleep(500)
    sendEmailTo(server, BOB)

    awaitAtMostTenSeconds.untilAsserted(() => {
      assertThat(seq.asJava)
        .hasSize(1)
      assertThat(seq.head)
        .contains("EmailDelivery")
    })
  }

  @Test
  def shouldNotPushEmailDeliveryChangeWhenUserCreatesDraftEmail(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=Mailbox,Email,VacationResponse,Thread,Identity,EmailSubmission,EmailDelivery&ping=0&closeAfter=no")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .responseContent()
      .map(bb => {
        val bytes = new Array[Byte](bb.readableBytes)
        bb.readBytes(bytes)
        new String(bytes, StandardCharsets.UTF_8)
      })
      .doOnNext(seq.addOne)
      .subscribeOn(Schedulers.boundedElastic())
      .subscribe()

    // Bob creates a draft mail
    Thread.sleep(500)
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa":{
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "to": [{"email": "rcpt1@apache.org"}, {"email": "rcpt2@apache.org"}],
         |          "from": [{"email": "${BOB.asString}"}]
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

    awaitAtMostTenSeconds.untilAsserted(() => {
      assertThat(seq.asJava)
        .hasSize(1)
      assertThat(seq.head)
        .doesNotContain("EmailDelivery")
    })
  }

  @Test
  def shouldNotPushEmailDeliveryChangeWhenUserSendsEmail(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))

    val messageId: MessageId = prepareDraftMessage(server)

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=Mailbox,Email,VacationResponse,Thread,Identity,EmailSubmission,EmailDelivery&ping=0&closeAfter=no")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .responseContent()
      .map(bb => {
        val bytes = new Array[Byte](bb.readableBytes)
        bb.readBytes(bytes)
        new String(bytes, StandardCharsets.UTF_8)
      })
      .doOnNext(seq.addOne)
      .subscribeOn(Schedulers.boundedElastic())
      .subscribe()

    // WHEN Bob sends an email to Andre
    Thread.sleep(500)
    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$ACCOUNT_ID",
         |       "create": {
         |         "k1490": {
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${BOB.asString}"},
         |             "rcptTo": [{"email": "${ANDRE.asString}"}]
         |           }
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
    .when
      .post
    .`then`
      .statusCode(SC_OK)

    awaitAtMostTenSeconds.untilAsserted(() => assertThat(seq.asJava).hasSize(0))
  }

  @Test
  def pingShouldBeSupported(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=*&ping=1&closeAfter=no")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .responseContent()
      .map(bb => {
        val bytes = new Array[Byte](bb.readableBytes)
        bb.readBytes(bytes)
        new String(bytes, StandardCharsets.UTF_8)
      })
      .doOnNext(seq.addOne)
      .subscribeOn(Schedulers.boundedElastic())
      .subscribe()

    Thread.sleep(2000)

    assertThat(seq.size).isGreaterThanOrEqualTo(1)
    assertThat(seq.head).isEqualTo("event: ping\ndata: {\"interval\":1}\n\n")
  }

  @Test
  def sseShouldTransportEvent(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=*&ping=0&closeAfter=no")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .responseContent()
      .map(buffer => {
        val bytes = new Array[Byte](buffer.readableBytes)
        buffer.readBytes(bytes)
        new String(bytes, StandardCharsets.UTF_8)
      })
      .doOnNext(seq.addOne)
      .subscribeOn(Schedulers.boundedElastic())
      .subscribe()

    Thread.sleep(500)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    Thread.sleep(200)

    assertThat(seq.asJava)
      .hasSize(1)
    assertThat(seq.head)
      .startsWith("event: state\ndata: {\"@type\":\"StateChange\",\"changed\":{\"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6\":{\"Mailbox\":")
    assertThat(seq.head).endsWith("\n\n")
  }

  @Test
  def sseShouldCloseAfterEventWhenCloseAfterState(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=*&ping=0&closeAfter=state")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .responseContent()
      .map(buffer => {
        val bytes = new Array[Byte](buffer.readableBytes)
        buffer.readBytes(bytes)
        new String(bytes, StandardCharsets.UTF_8)
      })
      .doOnNext(seq.addOne)
      .subscribeOn(Schedulers.boundedElastic())
      .subscribe()

    Thread.sleep(500)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "other"))
    Thread.sleep(200)

    assertThat(seq.asJava)
      .hasSize(1)
    assertThat(seq.head)
      .startsWith("event: state\ndata: {\"@type\":\"StateChange\",\"changed\":{\"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6\":{\"Mailbox\":")
    assertThat(seq.head).endsWith("\n\n")
  }

  @Test
  def sseShouldTransportEvents(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=*&ping=0&closeAfter=no")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .responseContent()
      .map(buffer => {
        val bytes = new Array[Byte](buffer.readableBytes)
        buffer.readBytes(bytes)
        new String(bytes, StandardCharsets.UTF_8)
      })
      .doOnNext(seq.addOne)
      .subscribeOn(Schedulers.boundedElastic())
      .subscribe()

    Thread.sleep(500)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "other"))
    Thread.sleep(200)

    assertThat(seq.asJava)
      .hasSize(2)
  }

  @Test
  def shouldPushChangesToDelegatedUser(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue
    val davidPath = MailboxPath.inbox(DAVID)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(davidPath)

    // DAVID delegates BOB to access his account
    server.getProbe(classOf[DelegationProbe]).addAuthorizedUser(DAVID, BOB)

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=EmailDelivery&ping=0&closeAfter=no")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .responseContent()
      .map(bb => {
        val bytes = new Array[Byte](bb.readableBytes)
        bb.readBytes(bytes)
        new String(bytes, StandardCharsets.UTF_8)
      })
      .doOnNext(seq.addOne)
      .subscribeOn(Schedulers.boundedElastic())
      .subscribe()

    Thread.sleep(500)
    // DAVID has a new mail therefore EmailDelivery change
    sendEmailTo(server, DAVID)

    // Bob should receive DAVID's EmailDelivery state change
    awaitAtMostTenSeconds.untilAsserted(() => {
      SoftAssertions.assertSoftly(softly => {
        softly.assertThat(seq.asJava)
          .hasSize(1)
        softly.assertThat(seq.head)
          .contains("EmailDelivery", DAVID_ACCOUNT_ID)
      })
    })
  }

  @Test
  def ownerUserShouldStillReceiveHisChangesWhenHeDelegatesHisAccountToOtherUsers(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue
    val bobPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    // BOB delegates DAVID to access his account
    server.getProbe(classOf[DelegationProbe]).addAuthorizedUser(BOB, DAVID)

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=EmailDelivery&ping=0&closeAfter=no")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .responseContent()
      .map(bb => {
        val bytes = new Array[Byte](bb.readableBytes)
        bb.readBytes(bytes)
        new String(bytes, StandardCharsets.UTF_8)
      })
      .doOnNext(seq.addOne)
      .subscribeOn(Schedulers.boundedElastic())
      .subscribe()

    Thread.sleep(500)
    // BOB has a new mail therefore EmailDelivery change
    sendEmailTo(server, BOB)

    // Bob should receive his EmailDelivery state change
    awaitAtMostTenSeconds.untilAsserted(() => {
      SoftAssertions.assertSoftly(softly => {
        softly.assertThat(seq.asJava)
          .hasSize(1)
        softly.assertThat(seq.head)
          .contains("EmailDelivery", ACCOUNT_ID)
      })
    })
  }

  @Test
  def bobShouldReceiveHisChangesAndHisDelegatedAccountChanges(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue
    val davidPath = MailboxPath.inbox(DAVID)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(davidPath)

    // DAVID delegates BOB to access his account
    server.getProbe(classOf[DelegationProbe]).addAuthorizedUser(DAVID, BOB)

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=EmailDelivery&ping=0&closeAfter=no")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .responseContent()
      .map(bb => {
        val bytes = new Array[Byte](bb.readableBytes)
        bb.readBytes(bytes)
        new String(bytes, StandardCharsets.UTF_8)
      })
      .doOnNext(seq.addOne)
      .subscribeOn(Schedulers.boundedElastic())
      .subscribe()

    Thread.sleep(500)
    sendEmailTo(server, DAVID)
    sendEmailTo(server, BOB)
    sendEmailTo(server, DAVID)
    sendEmailTo(server, BOB)

    // Bob should receive David's change and his changes
    awaitAtMostTenSeconds.untilAsserted(() => {
      SoftAssertions.assertSoftly(softly => {
        softly.assertThat(seq.asJava)
          .hasSize(4)
        softly.assertThat(seq.apply(0))
          .contains("EmailDelivery", DAVID_ACCOUNT_ID)
        softly.assertThat(seq.apply(1))
          .contains("EmailDelivery", ACCOUNT_ID)
        softly.assertThat(seq.apply(2))
          .contains("EmailDelivery", DAVID_ACCOUNT_ID)
        softly.assertThat(seq.apply(3))
          .contains("EmailDelivery", ACCOUNT_ID)
      })
    })
  }

  private def sendEmailTo(server: GuiceJamesServer, recipient: Username): Unit = {
    val smtpMessageSender: SMTPMessageSender = new SMTPMessageSender(DOMAIN.asString())
    smtpMessageSender.connect("127.0.0.1", server.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(ANDRE.asString, ANDRE_PASSWORD)
      .sendMessage(ANDRE.asString, recipient.asString())
    smtpMessageSender.close()

    awaitAtMostTenSeconds.until(() => server.getProbe(classOf[SpoolerProbe]).processingFinished())
  }

  private def prepareDraftMessage(server: GuiceJamesServer) = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(BOB.asString)
      .setFrom(BOB.asString)
      .setTo(ANDRE.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val bobDraftsPath = MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId
    messageId
  }
}
