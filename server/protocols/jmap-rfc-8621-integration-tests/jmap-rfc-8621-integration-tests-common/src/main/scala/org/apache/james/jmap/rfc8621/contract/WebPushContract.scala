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
import java.security.KeyPair
import java.security.interfaces.{ECPrivateKey, ECPublicKey}
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

import com.google.crypto.tink.apps.webpush.WebPushHybridDecrypt
import com.google.crypto.tink.subtle.EllipticCurves
import com.google.crypto.tink.subtle.EllipticCurves.CurveType
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.mailbox.DefaultMailboxes
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.{MailboxConstants, MailboxPath, MessageId}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.modules.protocols.SmtpGuiceProbe
import org.apache.james.utils.{DataProbeImpl, SMTPMessageSender, SpoolerProbe, UpdatableTickingClock}
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.awaitility.core.ConditionFactory
import org.junit.jupiter.api.{BeforeEach, Tag, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.mock.action.ExpectationResponseCallback
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.JsonBody.json
import org.mockserver.model.Not.not
import org.mockserver.model.{HttpRequest, HttpResponse}
import org.mockserver.verify.VerificationTimes
import play.api.libs.json.{JsObject, JsString, Json}

import scala.jdk.CollectionConverters._

trait WebPushContract {
  private lazy val awaitAtMostTenSeconds: ConditionFactory = Awaitility.`with`
    .pollInterval(ONE_HUNDRED_MILLISECONDS)
    .and.`with`.pollDelay(ONE_HUNDRED_MILLISECONDS)
    .await
    .atMost(10, TimeUnit.SECONDS)
  private lazy val PUSH_URL_PATH: String = "/push2"

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)

    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(BOB))

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()
  }

  private def getPushServerUrl(pushServer: ClientAndServer): String =
    s"http://127.0.0.1:${pushServer.getLocalPort}$PUSH_URL_PATH"

  // return pushSubscriptionId
  private def createPushSubscription(pushServer: ClientAndServer): String =
    `given`
      .body(
        s"""{
           |    "using": ["urn:ietf:params:jmap:core"],
           |    "methodCalls": [
           |      [
           |        "PushSubscription/set",
           |        {
           |            "create": {
           |                "4f29": {
           |                  "deviceClientId": "a889-ffea-910",
           |                  "url": "${getPushServerUrl(pushServer)}",
           |                  "types": ["Mailbox"]
           |                }
           |              }
           |        },
           |        "c1"
           |      ]
           |    ]
           |  }""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .extract()
      .jsonPath()
      .get("methodResponses[0][1].created.4f29.id")

  private def updateValidateVerificationCode(pushSubscriptionId: String, verificationCode: String): String =
    `given`()
      .body(
        s"""{
           |    "using": ["urn:ietf:params:jmap:core"],
           |    "methodCalls": [
           |      [
           |        "PushSubscription/set",
           |        {
           |            "update": {
           |                "$pushSubscriptionId": {
           |                  "verificationCode": "$verificationCode"
           |                }
           |              }
           |        },
           |        "c1"
           |      ]
           |    ]
           |  }""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

  private def sendEmailToBob(server: GuiceJamesServer): Unit = {
    val smtpMessageSender: SMTPMessageSender = new SMTPMessageSender(DOMAIN.asString())
    smtpMessageSender.connect("127.0.0.1", server.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(ANDRE.asString, ANDRE_PASSWORD)
      .sendMessage(ANDRE.asString, BOB.asString())
    smtpMessageSender.close()

    awaitAtMostTenSeconds.until(() => server.getProbe(classOf[SpoolerProbe]).processingFinished())
  }

  private def setupPushServerCallback(pushServer: ClientAndServer): AtomicReference[String] = {
    val bodyRequestOnPushServer: AtomicReference[String] = new AtomicReference("")
    pushServer
      .when(request
        .withPath(PUSH_URL_PATH)
        .withMethod("POST"))
      .respond(new ExpectationResponseCallback() {
        override def handle(httpRequest: HttpRequest): HttpResponse = {
          bodyRequestOnPushServer.set(httpRequest.getBodyAsString)
          response()
            .withStatusCode(HttpStatus.SC_CREATED)
        }
      })
    bodyRequestOnPushServer
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def correctBehaviourShouldSuccess(server: GuiceJamesServer, pushServer: ClientAndServer): Unit = {
    // Setup mock-server for callback
    val bodyRequestOnPushServer: AtomicReference[String] = setupPushServerCallback(pushServer)

    // WHEN bob creates a push subscription
    val pushSubscriptionId: String = createPushSubscription(pushServer)
    // THEN a validation code is sent
    awaitAtMostTenSeconds.untilAsserted { () =>
      val bodyAssert: Consumer[HttpRequest] = request => assertThatJson(request.getBodyAsString)
        .isEqualTo(
          s"""{
             |    "@type": "PushVerification",
             |    "pushSubscriptionId": "$pushSubscriptionId",
             |    "verificationCode": "$${json-unit.any-string}"
             |}""".stripMargin)

      assertThat(pushServer.retrieveRecordedRequests(HttpRequest.request().withPath(PUSH_URL_PATH)).toSeq.asJava)
        .hasSizeGreaterThanOrEqualTo(1)
        .anySatisfy(bodyAssert)
    }

    // GIVEN bob retrieves the validation code from the mock server
    val verificationCode: String = Json.parse(bodyRequestOnPushServer.get()).asInstanceOf[JsObject]
      .value("verificationCode")
      .asInstanceOf[JsString]
      .value

    // WHEN bob updates the validation code via JMAP
    val updateVerificationCodeResponse: String = updateValidateVerificationCode(pushSubscriptionId, verificationCode)

    // THEN  it succeed
    assertThatJson(updateVerificationCodeResponse)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "PushSubscription/set",
           |            {
           |                "updated": {
           |                    "$pushSubscriptionId": {}
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)

    // WHEN bob receives a mail
    sendEmailToBob(server)

    // THEN bob has a stateChange on the push gateway
    awaitAtMostTenSeconds.untilAsserted { () =>
      val bodyAssert: Consumer[HttpRequest] = request => assertThatJson(request.getBodyAsString)
        .isEqualTo(
          s"""{
             |    "@type": "StateChange",
             |    "changed": {
             |        "$ACCOUNT_ID": {
             |          "Mailbox": "$${json-unit.any-string}"
             |        }
             |    }
             |}""".stripMargin)

      assertThat(pushServer.retrieveRecordedRequests(HttpRequest.request().withPath(PUSH_URL_PATH)).toSeq.asJava)
        .hasSizeGreaterThanOrEqualTo(1)
        .anySatisfy(bodyAssert)
    }
  }

  @Test
  def shouldPushEmailDeliveryChangeWhenUserReceivesEmail(server: GuiceJamesServer, pushServer: ClientAndServer): Unit = {
    setupPushSubscriptionForBob(pushServer)

    // WHEN bob receives a mail
    sendEmailToBob(server)

    // THEN bob has a EmailDelivery stateChange on the push gateway
    awaitAtMostTenSeconds.untilAsserted { () =>
      val bodyAssert: Consumer[HttpRequest] = request => assertThatJson(request.getBodyAsString)
        .isEqualTo(
          s"""{
             |    "@type": "StateChange",
             |    "changed": {
             |        "$ACCOUNT_ID": {
             |          "EmailDelivery": "$${json-unit.any-string}"
             |        }
             |    }
             |}""".stripMargin)

      assertThat(pushServer.retrieveRecordedRequests(HttpRequest.request().withPath(PUSH_URL_PATH)).toSeq.asJava)
        .hasSizeGreaterThanOrEqualTo(1)
        .anySatisfy(bodyAssert)
    }
  }

  @Test
  def shouldNotPushEmailDeliveryChangeWhenUserCreatesDraftEmail(server: GuiceJamesServer, pushServer: ClientAndServer): Unit = {
    setupPushSubscriptionForBob(pushServer)

    // WHEN bob create a draft mail
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).getMailboxId("#private", BOB.asString(), MailboxConstants.INBOX)
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

    // THEN bob should not have a EmailDelivery stateChange on the push gateway
    awaitAtMostTenSeconds.untilAsserted { () =>
      val bodyAssert: Consumer[HttpRequest] = request => assertThatJson(request.getBodyAsString)
        .isNotEqualTo(
          s"""{
             |    "@type": "StateChange",
             |    "changed": {
             |        "$ACCOUNT_ID": {
             |          "EmailDelivery": "$${json-unit.any-string}"
             |        }
             |    }
             |}""".stripMargin)

      assertThat(pushServer.retrieveRecordedRequests(HttpRequest.request().withPath(PUSH_URL_PATH)).toSeq.asJava)
        .allSatisfy(bodyAssert)
    }
  }

  @Test
  def shouldNotPushEmailDeliveryChangeWhenUserSendsEmail(server: GuiceJamesServer, pushServer: ClientAndServer): Unit = {
    val messageId: MessageId = prepareDraftMessage(server)
    setupPushSubscriptionForBob(pushServer)

    // WHEN Bob sends an email to Andre
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

    // THEN bob should not have a EmailDelivery stateChange on the push gateway
    awaitAtMostTenSeconds.untilAsserted { () =>
      val bodyAssert: Consumer[HttpRequest] = request => assertThatJson(request.getBodyAsString)
        .isNotEqualTo(
          s"""{
             |    "@type": "StateChange",
             |    "changed": {
             |        "$ACCOUNT_ID": {
             |          "EmailDelivery": "$${json-unit.any-string}"
             |        }
             |    }
             |}""".stripMargin)

      assertThat(pushServer.retrieveRecordedRequests(HttpRequest.request().withPath(PUSH_URL_PATH)).toSeq.asJava)
        .allSatisfy(bodyAssert)
    }
  }

  @Test
  def webPushShouldNotPushToPushServerWhenExpiredSubscription(server: GuiceJamesServer, pushServer: ClientAndServer, clock: UpdatableTickingClock): Unit = {
    // Setup mock-server for callback
    val bodyRequestOnPushServer: AtomicReference[String] = setupPushServerCallback(pushServer)

    // WHEN bob creates a push subscription
    val pushSubscriptionId: String = createPushSubscription(pushServer)
    // THEN a validation code is sent
    awaitAtMostTenSeconds.untilAsserted { () =>
      val bodyAssert: Consumer[HttpRequest] = request => assertThatJson(request.getBodyAsString)
        .isEqualTo(
          s"""{
             |    "@type": "PushVerification",
             |    "pushSubscriptionId": "$pushSubscriptionId",
             |    "verificationCode": "$${json-unit.any-string}"
             |}""".stripMargin)

      assertThat(pushServer.retrieveRecordedRequests(HttpRequest.request().withPath(PUSH_URL_PATH)).toSeq.asJava)
        .anySatisfy(bodyAssert)
    }

    // GIVEN bob retrieves the validation code from the mock server
    val verificationCode: String = Json.parse(bodyRequestOnPushServer.get()).asInstanceOf[JsObject]
      .value("verificationCode")
      .asInstanceOf[JsString]
      .value

    // WHEN bob updates the validation code via JMAP
    val updateVerificationCodeResponse: String = updateValidateVerificationCode(pushSubscriptionId, verificationCode)

    // THEN  it succeed
    assertThatJson(updateVerificationCodeResponse)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "PushSubscription/set",
           |            {
           |                "updated": {
           |                    "$pushSubscriptionId": {}
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)

    // GIVEN 8 days passes
    clock.setInstant(clock.instant().plus(8, ChronoUnit.DAYS))

    // WHEN bob receives a mail
    sendEmailToBob(server)

    // THEN bob has a stateChange on the push gateway
    TimeUnit.MILLISECONDS.sleep(200)

    val bodyAssert: Consumer[HttpRequest] = request => assertThatJson(request.getBodyAsString)
      .isNotEqualTo(
        s"""{
           |    "@type": "StateChange",
           |    "changed": {
           |        "$ACCOUNT_ID": {
           |          "Mailbox": "$${json-unit.any-string}"
           |        }
           |    }
           |}""".stripMargin)

    assertThat(pushServer.retrieveRecordedRequests(HttpRequest.request().withPath(PUSH_URL_PATH)).toSeq.asJava)
      .allSatisfy(bodyAssert)
  }

  @Test
  def webPushShouldNotPushToPushServerWhenDeletedSubscription(server: GuiceJamesServer, pushServer: ClientAndServer): Unit = {
    // Setup mock-server for callback
    val bodyRequestOnPushServer: AtomicReference[String] = setupPushServerCallback(pushServer)

    // WHEN bob creates a push subscription
    val pushSubscriptionId: String = createPushSubscription(pushServer)
    // THEN a validation code is sent
    awaitAtMostTenSeconds.untilAsserted { () =>
      val bodyAssert: Consumer[HttpRequest] = request => assertThatJson(request.getBodyAsString)
        .isEqualTo(
          s"""{
             |    "@type": "PushVerification",
             |    "pushSubscriptionId": "$pushSubscriptionId",
             |    "verificationCode": "$${json-unit.any-string}"
             |}""".stripMargin)

      assertThat(pushServer.retrieveRecordedRequests(HttpRequest.request().withPath(PUSH_URL_PATH)).toSeq.asJava)
        .anySatisfy(bodyAssert)
    }

    // GIVEN bob retrieves the validation code from the mock server
    val verificationCode: String = Json.parse(bodyRequestOnPushServer.get()).asInstanceOf[JsObject]
      .value("verificationCode")
      .asInstanceOf[JsString]
      .value

    // WHEN bob updates the validation code via JMAP
    val updateVerificationCodeResponse: String = updateValidateVerificationCode(pushSubscriptionId, verificationCode)

    // THEN  it succeed
    assertThatJson(updateVerificationCodeResponse)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "PushSubscription/set",
           |            {
           |                "updated": {
           |                    "$pushSubscriptionId": {}
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)

    // GIVEN bob deletes the push subscription
    val pushSubscriptionProbe: PushSubscriptionProbe = server.getProbe(classOf[PushSubscriptionProbe])

    `given`
      .body(
        s"""{
           |    "using": ["urn:ietf:params:jmap:core"],
           |    "methodCalls": [
           |      [
           |        "PushSubscription/set",
           |        {
           |            "destroy": ["$pushSubscriptionId"]
           |        },
           |        "c1"
           |      ]
           |    ]
           |  }""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)

    // WHEN bob receives a mail
    sendEmailToBob(server)

    // THEN bob has no stateChange on the push gateway
    TimeUnit.MILLISECONDS.sleep(200)

    val bodyAssert: Consumer[HttpRequest] = request => assertThatJson(request.getBodyAsString)
      .isNotEqualTo(
        s"""{
           |    "@type": "StateChange",
           |    "changed": {
           |        "$ACCOUNT_ID": {
           |          "Mailbox": "$${json-unit.any-string}"
           |        }
           |    }
           |}""".stripMargin)

    assertThat(pushServer.retrieveRecordedRequests(HttpRequest.request().withPath(PUSH_URL_PATH)).toSeq.asJava)
      .allSatisfy(bodyAssert)
  }

  @Test
  def webPushShouldNotPushToPushServerWhenNotValidatedCode(server: GuiceJamesServer, pushServer: ClientAndServer): Unit = {
    // Setup mock-server for callback
    setupPushServerCallback(pushServer)

    // WHEN bob creates a push subscription [no code validation]
    createPushSubscription(pushServer)

    // GIVEN bob receives a mail
    sendEmailToBob(server)

    // THEN bob has no stateChange on the push gateway
    TimeUnit.MILLISECONDS.sleep(200)

    val bodyAssert: Consumer[HttpRequest] = request => assertThatJson(request.getBodyAsString)
      .isNotEqualTo(
        s"""{
           |    "@type": "StateChange",
           |    "changed": {
           |        "$ACCOUNT_ID": {
           |          "Mailbox": "$${json-unit.any-string}"
           |        }
           |    }
           |}""".stripMargin)

    assertThat(pushServer.retrieveRecordedRequests(HttpRequest.request().withPath(PUSH_URL_PATH)).toSeq.asJava)
      .allSatisfy(bodyAssert)
  }

  @Test
  def correctBehaviourShouldSuccessWhenEncryptionKeys(server: GuiceJamesServer, pushServer: ClientAndServer): Unit = {
    // Setup mock-server for callback
    val bodyRequestOnPushServer: AtomicReference[Array[Byte]] = new AtomicReference()

    pushServer
      .when(request
        .withPath(PUSH_URL_PATH)
        .withMethod("POST"))
      .respond(new ExpectationResponseCallback() {
        override def handle(httpRequest: HttpRequest): HttpResponse = {
          bodyRequestOnPushServer.set(httpRequest.getBodyAsRawBytes)
          response()
            .withStatusCode(HttpStatus.SC_CREATED)
        }
      })

    val uaKeyPair: KeyPair = EllipticCurves.generateKeyPair(CurveType.NIST_P256)
    val uaPublicKey: ECPublicKey = uaKeyPair.getPublic.asInstanceOf[ECPublicKey]
    val uaPrivateKey: ECPrivateKey = uaKeyPair.getPrivate.asInstanceOf[ECPrivateKey]
    val authSecret: Array[Byte] = "secret123secret1".getBytes

    val p256dh: String = Base64.getUrlEncoder.encodeToString(uaPublicKey.getEncoded)
    val auth: String = Base64.getUrlEncoder.encodeToString(authSecret)

    val pushSubscriptionId: String = `given`
      .body(
        s"""{
           |    "using": ["urn:ietf:params:jmap:core"],
           |    "methodCalls": [
           |      [
           |        "PushSubscription/set",
           |        {
           |            "create": {
           |                "4f29": {
           |                  "deviceClientId": "a889-ffea-910",
           |                  "url": "${getPushServerUrl(pushServer)}",
           |                  "types": ["Mailbox"],
           |                  "keys": {
           |                    "p256dh": "$p256dh",
           |                    "auth": "$auth"
           |                  }
           |                }
           |              }
           |        },
           |        "c1"
           |      ]
           |    ]
           |  }""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .extract()
      .jsonPath()
      .get("methodResponses[0][1].created.4f29.id")

    // THEN a validation code is sent
    awaitAtMostTenSeconds.untilAsserted { () =>
      val bodyAssert: Consumer[HttpRequest] = request => assertThatJson(request.getBodyAsString)
        .isNotEqualTo(
          s"""{
             |    "@type": "PushVerification",
             |    "pushSubscriptionId": "$pushSubscriptionId",
             |    "verificationCode": "$${json-unit.any-string}"
             |}""".stripMargin)

      assertThat(pushServer.retrieveRecordedRequests(HttpRequest.request().withPath(PUSH_URL_PATH)).toSeq.asJava)
        .hasSizeGreaterThanOrEqualTo(1)
        .allSatisfy(bodyAssert)
    }

    val hybridDecrypt: WebPushHybridDecrypt = new WebPushHybridDecrypt.Builder()
      .withAuthSecret(authSecret)
      .withRecipientPublicKey(uaPublicKey)
      .withRecipientPrivateKey(uaPrivateKey)
      .build

    val decryptBodyRequestOnPushServer: String = new String(hybridDecrypt.decrypt(bodyRequestOnPushServer.get(), null), StandardCharsets.UTF_8)

    // GIVEN bob retrieves the validation code from the mock server
    val verificationCode: String = Json.parse(decryptBodyRequestOnPushServer).asInstanceOf[JsObject]
      .value("verificationCode")
      .asInstanceOf[JsString]
      .value

    // WHEN bob updates the validation code via JMAP
    updateValidateVerificationCode(pushSubscriptionId, verificationCode)

    // WHEN bob receives a mail
    sendEmailToBob(server)

    // THEN bob has a stateChange on the push gateway
    awaitAtMostTenSeconds.untilAsserted { () =>
      pushServer.verify(HttpRequest.request()
        .withPath(PUSH_URL_PATH),
        VerificationTimes.atLeast(1))
    }

    assertThatJson(new String(hybridDecrypt.decrypt(bodyRequestOnPushServer.get(), null), StandardCharsets.UTF_8))
      .isEqualTo(
        s"""{
           |    "@type": "StateChange",
           |    "changed": {
           |        "$ACCOUNT_ID": {
           |          "Mailbox": "$${json-unit.ignore}"
           |        }
           |    }
           |}""".stripMargin)
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
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId
    messageId
  }

  private def setupPushSubscriptionForBob(pushServer: ClientAndServer) = {
    // Setup mock-server for callback
    val bodyRequestOnPushServer: AtomicReference[String] = setupPushServerCallback(pushServer)

    // WHEN bob creates a push subscription
    val pushSubscriptionId: String = `given`
      .body(
        s"""{
           |    "using": ["urn:ietf:params:jmap:core"],
           |    "methodCalls": [
           |      [
           |        "PushSubscription/set",
           |        {
           |            "create": {
           |                "4f29": {
           |                  "deviceClientId": "a889-ffea-910",
           |                  "url": "${getPushServerUrl(pushServer)}",
           |                  "types": ["EmailDelivery"]
           |                }
           |              }
           |        },
           |        "c1"
           |      ]
           |    ]
           |  }""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .extract()
      .jsonPath()
      .get("methodResponses[0][1].created.4f29.id")

    // THEN a validation code is sent
    awaitAtMostTenSeconds.untilAsserted { () =>

      val bodyAssert: Consumer[HttpRequest] = request => assertThatJson(request.getBodyAsString)
        .isEqualTo(
          s"""{
             |    "@type": "PushVerification",
             |    "pushSubscriptionId": "$pushSubscriptionId",
             |    "verificationCode": "$${json-unit.any-string}"
             |}""".stripMargin)

      assertThat(pushServer.retrieveRecordedRequests(HttpRequest.request().withPath(PUSH_URL_PATH)).toSeq.asJava)
        .anySatisfy(bodyAssert)
    }

    // GIVEN bob retrieves the validation code from the mock server
    val verificationCode: String = Json.parse(bodyRequestOnPushServer.get()).asInstanceOf[JsObject]
      .value("verificationCode")
      .asInstanceOf[JsString]
      .value

    // WHEN bob updates the validation code via JMAP
    val updateVerificationCodeResponse: String = updateValidateVerificationCode(pushSubscriptionId, verificationCode)

    // THEN it succeed
    assertThatJson(updateVerificationCodeResponse)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "PushSubscription/set",
           |            {
           |                "updated": {
           |                    "$pushSubscriptionId": {}
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }
}
