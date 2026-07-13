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
import java.util.Base64
import java.util.UUID
import java.util.concurrent.{TimeUnit, atomic}

import com.google.common.hash.Hashing
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.netty.handler.codec.http.HttpResponseStatus
import io.restassured.RestAssured.{`given`, requestSpecification}
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.JmapGuiceProbe
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE_PASSWORD, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
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

object EventSourceContract {
  case class TestContext(bobUsername: Username, bobAccountId: String, andreUsername: Username, davidUsername: Username, davidAccountId: String)

  val currentContext: atomic.AtomicReference[TestContext] = new atomic.AtomicReference[TestContext]()
}

trait EventSourceContract {
  import EventSourceContract.{TestContext, currentContext}

  def bobUsername: Username = currentContext.get().bobUsername
  def bobAccountId: String = currentContext.get().bobAccountId
  def andreUsername: Username = currentContext.get().andreUsername
  def davidUsername: Username = currentContext.get().davidUsername
  def davidAccountId: String = currentContext.get().davidAccountId

  private def accountId(username: Username): String =
    Hashing.sha256().hashString(username.asString(), StandardCharsets.UTF_8).toString

  private def bobAuthorizationHeader: String = {
    val credentials = s"${bobUsername.asString}:$BOB_PASSWORD"
    s"Basic ${Base64.getEncoder.encodeToString(credentials.getBytes(StandardCharsets.UTF_8))}"
  }

  private lazy val awaitAtMostTenSeconds: ConditionFactory = Awaitility.`with`
    .pollInterval(ONE_HUNDRED_MILLISECONDS)
    .and.`with`.pollDelay(ONE_HUNDRED_MILLISECONDS)
    .await
    .atMost(10, TimeUnit.SECONDS)

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    val uniqueSuffix = UUID.randomUUID().toString.replace("-", "").take(8)
    val bob = Username.fromLocalPartWithDomain(s"bob$uniqueSuffix", DOMAIN)
    val andre = Username.fromLocalPartWithDomain(s"andre$uniqueSuffix", DOMAIN)
    val david = Username.fromLocalPartWithDomain(s"david$uniqueSuffix", DOMAIN)
    currentContext.set(TestContext(
      bobUsername = bob,
      bobAccountId = accountId(bob),
      andreUsername = andre,
      davidUsername = david,
      davidAccountId = accountId(david)))

    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(andre.asString(), ANDRE_PASSWORD)
      .addUser(bob.asString(), BOB_PASSWORD)
      .addUser(david.asString(), "secret")

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(bob, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()
  }

  @Test
  def typesQueryParamIsCompulsory(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val status = HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?ping=0&closeAfter=no")
      .headers(builder => {
        builder.add("Authorization", bobAuthorizationHeader)
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
        builder.add("Authorization", bobAuthorizationHeader)
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
        builder.add("Authorization", bobAuthorizationHeader)
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
        builder.add("Authorization", bobAuthorizationHeader)
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
        builder.add("Authorization", bobAuthorizationHeader)
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
        builder.add("Authorization", bobAuthorizationHeader)
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
        builder.add("Authorization", bobAuthorizationHeader)
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
        builder.add("Authorization", bobAuthorizationHeader)
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
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(bobUsername))
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
        builder.add("Authorization", bobAuthorizationHeader)
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
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(bobUsername))
    Thread.sleep(200)

    assertThat(seq.asJava)
      .hasSize(1)
    assertThat(seq.head)
      .startsWith(s"event: state\ndata: {\"@type\":\"StateChange\",\"changed\":{\"$bobAccountId\":{\"Mailbox\":")
    assertThat(seq.head).doesNotContain("pushState")
    assertThat(seq.head).endsWith("\n\n")
  }

  @Test
  def shouldPushEmailDeliveryChangeWhenUserReceivesEmail(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(bobUsername))

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=Mailbox,Email,VacationResponse,Thread,Identity,EmailSubmission,EmailDelivery&ping=0&closeAfter=no")
      .headers(builder => {
        builder.add("Authorization", bobAuthorizationHeader)
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
    sendEmailTo(server, bobUsername)

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
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(bobUsername))

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=Mailbox,Email,VacationResponse,Thread,Identity,EmailSubmission,EmailDelivery&ping=0&closeAfter=no")
      .headers(builder => {
        builder.add("Authorization", bobAuthorizationHeader)
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
         |      "accountId": "$bobAccountId",
         |      "create": {
         |        "aaaaaa":{
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "to": [{"email": "rcpt1@apache.org"}, {"email": "rcpt2@apache.org"}],
         |          "from": [{"email": "${bobUsername.asString}"}]
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
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(bobUsername))

    val messageId: MessageId = prepareDraftMessage(server)

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=Mailbox,Email,VacationResponse,Thread,Identity,EmailSubmission,EmailDelivery&ping=0&closeAfter=no")
      .headers(builder => {
        builder.add("Authorization", bobAuthorizationHeader)
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
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${andreUsername.asString}"}]
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
        builder.add("Authorization", bobAuthorizationHeader)
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
        builder.add("Authorization", bobAuthorizationHeader)
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
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(bobUsername))
    Thread.sleep(200)

    assertThat(seq.asJava)
      .hasSize(1)
    assertThat(seq.head)
      .startsWith(s"event: state\ndata: {\"@type\":\"StateChange\",\"changed\":{\"$bobAccountId\":{\"Mailbox\":")
    assertThat(seq.head).endsWith("\n\n")
  }

  @Test
  def sseShouldCloseAfterEventWhenCloseAfterState(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=*&ping=0&closeAfter=state")
      .headers(builder => {
        builder.add("Authorization", bobAuthorizationHeader)
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
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(bobUsername))
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(bobUsername, "other"))
    Thread.sleep(200)

    assertThat(seq.asJava)
      .hasSize(1)
    assertThat(seq.head)
      .startsWith(s"event: state\ndata: {\"@type\":\"StateChange\",\"changed\":{\"$bobAccountId\":{\"Mailbox\":")
    assertThat(seq.head).endsWith("\n\n")
  }

  @Test
  def sseShouldTransportEvents(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=*&ping=0&closeAfter=no")
      .headers(builder => {
        builder.add("Authorization", bobAuthorizationHeader)
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
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(bobUsername))
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(bobUsername, "other"))
    Thread.sleep(200)

    assertThat(seq.asJava)
      .hasSize(2)
  }

  @Test
  def shouldPushChangesToDelegatedUser(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue
    val davidPath = MailboxPath.inbox(davidUsername)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(davidPath)

    // davidUsername delegates bobUsername to access his account
    server.getProbe(classOf[DelegationProbe]).addAuthorizedUser(davidUsername, bobUsername)

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=EmailDelivery&ping=0&closeAfter=no")
      .headers(builder => {
        builder.add("Authorization", bobAuthorizationHeader)
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
    // davidUsername has a new mail therefore EmailDelivery change
    sendEmailTo(server, davidUsername)

    // Bob should receive davidUsername's EmailDelivery state change
    awaitAtMostTenSeconds.untilAsserted(() => {
      SoftAssertions.assertSoftly(softly => {
        softly.assertThat(seq.asJava)
          .hasSize(1)
        softly.assertThat(seq.head)
          .contains("EmailDelivery", davidAccountId)
      })
    })
  }

  @Test
  def ownerUserShouldStillReceiveHisChangesWhenHeDelegatesHisAccountToOtherUsers(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue
    val bobPath = MailboxPath.inbox(bobUsername)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    // bobUsername delegates davidUsername to access his account
    server.getProbe(classOf[DelegationProbe]).addAuthorizedUser(bobUsername, davidUsername)

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=EmailDelivery&ping=0&closeAfter=no")
      .headers(builder => {
        builder.add("Authorization", bobAuthorizationHeader)
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
    // bobUsername has a new mail therefore EmailDelivery change
    sendEmailTo(server, bobUsername)

    // Bob should receive his EmailDelivery state change
    awaitAtMostTenSeconds.untilAsserted(() => {
      SoftAssertions.assertSoftly(softly => {
        softly.assertThat(seq.asJava)
          .hasSize(1)
        softly.assertThat(seq.head)
          .contains("EmailDelivery", bobAccountId)
      })
    })
  }

  @Test
  def bobShouldReceiveHisChangesAndHisDelegatedAccountChanges(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue
    val davidPath = MailboxPath.inbox(davidUsername)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(davidPath)

    // davidUsername delegates bobUsername to access his account
    server.getProbe(classOf[DelegationProbe]).addAuthorizedUser(davidUsername, bobUsername)

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=EmailDelivery&ping=0&closeAfter=no")
      .headers(builder => {
        builder.add("Authorization", bobAuthorizationHeader)
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
    sendEmailTo(server, davidUsername)
    sendEmailTo(server, bobUsername)
    sendEmailTo(server, davidUsername)
    sendEmailTo(server, bobUsername)

    // Bob should receive David's change and his changes
    awaitAtMostTenSeconds.untilAsserted(() => {
      SoftAssertions.assertSoftly(softly => {
        softly.assertThat(seq.asJava)
          .hasSize(4)
        softly.assertThat(seq.apply(0))
          .contains("EmailDelivery", davidAccountId)
        softly.assertThat(seq.apply(1))
          .contains("EmailDelivery", bobAccountId)
        softly.assertThat(seq.apply(2))
          .contains("EmailDelivery", davidAccountId)
        softly.assertThat(seq.apply(3))
          .contains("EmailDelivery", bobAccountId)
      })
    })
  }

  private def sendEmailTo(server: GuiceJamesServer, recipient: Username): Unit = {
    val smtpMessageSender: SMTPMessageSender = new SMTPMessageSender(DOMAIN.asString())
    smtpMessageSender.connect("127.0.0.1", server.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(andreUsername.asString, ANDRE_PASSWORD)
      .sendMessage(andreUsername.asString, recipient.asString())
    smtpMessageSender.close()

    awaitAtMostTenSeconds.until(() => server.getProbe(classOf[SpoolerProbe]).processingFinished())
  }

  private def prepareDraftMessage(server: GuiceJamesServer) = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(bobUsername.asString)
      .setFrom(bobUsername.asString)
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId
    messageId
  }
}
