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

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.interfaces.{ECPrivateKey, ECPublicKey}
import java.util.concurrent.TimeUnit
import java.util.{Base64, UUID}

import com.google.crypto.tink.apps.webpush.WebPushHybridDecrypt
import com.google.crypto.tink.subtle.EllipticCurves.{CurveType, EcdsaEncoding, PointFormatType}
import com.google.crypto.tink.subtle.Enums.HashType
import com.google.crypto.tink.subtle.{EcdsaVerifyJce, EllipticCurves}
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.{SC_CREATED, SC_OK}
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.JmapRfc8621Configuration
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.pushsubscription.VapidConfiguration
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.jmap.rfc8621.contract.WebPushVapidContract.{APPLICATION_SERVER_KEY, KEY_PAIR}
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.modules.protocols.SmtpGuiceProbe
import org.apache.james.utils.{DataProbeImpl, SMTPMessageSender, SpoolerProbe}
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.awaitility.core.ConditionFactory
import org.junit.jupiter.api.{BeforeEach, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import play.api.libs.json.{JsObject, JsString, Json}

object WebPushVapidContract {
  private val KEY_PAIR: KeyPair = EllipticCurves.generateKeyPair(CurveType.NIST_P256)
  // As Web Push tools generate them: the uncompressed point and the private value, base64url encoded
  private val APPLICATION_SERVER_KEY: String = Base64.getUrlEncoder.withoutPadding()
    .encodeToString(EllipticCurves.pointEncode(CurveType.NIST_P256, PointFormatType.UNCOMPRESSED, KEY_PAIR.getPublic.asInstanceOf[ECPublicKey].getW))
  private val PRIVATE_KEY: String = Base64.getUrlEncoder.withoutPadding()
    .encodeToString(leftPad(KEY_PAIR.getPrivate.asInstanceOf[ECPrivateKey].getS))

  val configuration: JmapRfc8621Configuration = JmapRfc8621Configuration(
    urlPrefixString = "http://127.0.0.1",
    websocketPrefixString = "ws://127.0.0.1",
    preventServerSideRequestForgery = false,
    vapid = VapidConfiguration.from(Some(APPLICATION_SERVER_KEY), Some(PRIVATE_KEY), Some("mailto:postmaster@example.com")))

  private def leftPad(value: BigInteger): Array[Byte] = {
    val bytes: Array[Byte] = value.toByteArray.dropWhile(_ == 0)
    Array.fill[Byte](32 - bytes.length)(0) ++ bytes
  }
}

trait WebPushVapidContract {
  private lazy val awaitAtMostTenSeconds: ConditionFactory = Awaitility.`with`
    .pollInterval(ONE_HUNDRED_MILLISECONDS)
    .and.`with`.pollDelay(ONE_HUNDRED_MILLISECONDS)
    .await
    .atMost(10, TimeUnit.SECONDS)
  private lazy val PUSH_URL_PATH: String = "/push-vapid"

  @BeforeEach
  def setUp(server: GuiceJamesServer, pushServer: ClientAndServer): Unit = {
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

    pushServer
      .when(request.withPath(PUSH_URL_PATH).withMethod("POST"))
      .respond(response.withStatusCode(SC_CREATED))
  }

  private def pushed(pushServer: ClientAndServer): Seq[HttpRequest] =
    pushServer.retrieveRecordedRequests(request().withPath(PUSH_URL_PATH)).toSeq

  private def createPushSubscription(pushServer: ClientAndServer, keys: String = ""): String =
    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core"],
           |  "methodCalls": [[
           |    "PushSubscription/set",
           |    {
           |      "create": {
           |        "4f29": {
           |          "deviceClientId": "a889-ffea-910",
           |          "url": "http://127.0.0.1:${pushServer.getLocalPort}$PUSH_URL_PATH",
           |          $keys
           |          "types": ["Mailbox"]
           |        }
           |      }
           |    },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .extract()
      .jsonPath()
      .get("methodResponses[0][1].created.4f29.id")

  // The claims of a `vapid t=<token>, k=<key>` header, once its signature is verified with the configured public key
  private def verifiedClaims(pushRequest: HttpRequest): JsObject = {
    val authorization: String = pushRequest.getFirstHeader("Authorization")
    assertThat(authorization).startsWith("vapid t=").endsWith(s", k=$APPLICATION_SERVER_KEY")
    val Array(header, payload, signature) = authorization.stripPrefix("vapid t=").takeWhile(_ != ',').split('.')

    new EcdsaVerifyJce(KEY_PAIR.getPublic.asInstanceOf[ECPublicKey], HashType.SHA256, EcdsaEncoding.IEEE_P1363)
      .verify(Base64.getUrlDecoder.decode(signature), s"$header.$payload".getBytes(StandardCharsets.US_ASCII))
    Json.parse(Base64.getUrlDecoder.decode(payload)).as[JsObject]
  }

  @Test
  def sessionShouldAdvertiseTheApplicationServerKey(): Unit = {
    val applicationServerKey: String = `given`()
    .when()
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .extract()
      .jsonPath()
      .get("capabilities.'urn:ietf:params:jmap:webpush-vapid'.applicationServerKey")

    assertThat(applicationServerKey).isEqualTo(APPLICATION_SERVER_KEY)
  }

  @Test
  def sessionStateShouldDependOnTheApplicationServerKey(): Unit = {
    val sessionState: String = `given`()
    .when()
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .extract()
      .jsonPath()
      .get("state")

    val responseSessionState: String = `given`
      .body(
        """{
          |  "using": ["urn:ietf:params:jmap:core"],
          |  "methodCalls": [["Core/echo", {}, "c1"]]
          |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .extract()
      .jsonPath()
      .get("sessionState")

    assertThat(sessionState)
      .isEqualTo(UUID.nameUUIDFromBytes(APPLICATION_SERVER_KEY.getBytes(StandardCharsets.UTF_8)).toString)
    assertThat(responseSessionState).isEqualTo(sessionState)
  }

  @Test
  def pushVerificationShouldBeAuthenticated(pushServer: ClientAndServer): Unit = {
    createPushSubscription(pushServer)

    awaitAtMostTenSeconds.untilAsserted(() => assertThat(pushed(pushServer).size).isEqualTo(1))
    val claims: JsObject = verifiedClaims(pushed(pushServer).head)
    assertThat(claims.value("aud").as[String]).isEqualTo(s"http://127.0.0.1:${pushServer.getLocalPort}")
    assertThat(claims.value("sub").as[String]).isEqualTo("mailto:postmaster@example.com")
    assertThat(claims.value("exp").as[Long]).isGreaterThan(System.currentTimeMillis() / 1000)
  }

  @Test
  def stateChangeShouldBeAuthenticated(server: GuiceJamesServer, pushServer: ClientAndServer): Unit = {
    val pushSubscriptionId: String = createPushSubscription(pushServer)
    awaitAtMostTenSeconds.untilAsserted(() => assertThat(pushed(pushServer).size).isEqualTo(1))
    val verificationCode: String = Json.parse(pushed(pushServer).head.getBodyAsString).as[JsObject]
      .value("verificationCode").as[JsString].value
    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core"],
           |  "methodCalls": [[
           |    "PushSubscription/set",
           |    { "update": { "$pushSubscriptionId": { "verificationCode": "$verificationCode" } } },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)

    val smtpMessageSender: SMTPMessageSender = new SMTPMessageSender(DOMAIN.asString())
    smtpMessageSender.connect("127.0.0.1", server.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(ANDRE.asString, ANDRE_PASSWORD)
      .sendMessage(ANDRE.asString, BOB.asString())
    smtpMessageSender.close()
    awaitAtMostTenSeconds.until(() => server.getProbe(classOf[SpoolerProbe]).processingFinished())

    awaitAtMostTenSeconds.untilAsserted(() => assertThat(pushed(pushServer).size).isGreaterThanOrEqualTo(2))
    val stateChange: HttpRequest = pushed(pushServer).last
    assertThatJson(stateChange.getBodyAsString).node("@type").isEqualTo("StateChange")
    assertThat(verifiedClaims(stateChange).value("aud").as[String]).isEqualTo(s"http://127.0.0.1:${pushServer.getLocalPort}")
  }

  @Test
  def pushVerificationShouldBeReadableWithTheKeysOfABrowser(pushServer: ClientAndServer): Unit = {
    val uaKeyPair: KeyPair = EllipticCurves.generateKeyPair(CurveType.NIST_P256)
    val uaPublicKey: ECPublicKey = uaKeyPair.getPublic.asInstanceOf[ECPublicKey]
    val authSecret: Array[Byte] = "secret123secret1".getBytes(StandardCharsets.UTF_8)
    // RFC 8291: the public key as an uncompressed point, as `PushSubscription.getKey("p256dh")` gives it
    val p256dh: String = Base64.getUrlEncoder.withoutPadding()
      .encodeToString(EllipticCurves.pointEncode(CurveType.NIST_P256, PointFormatType.UNCOMPRESSED, uaPublicKey.getW))
    val auth: String = Base64.getUrlEncoder.withoutPadding().encodeToString(authSecret)

    val pushSubscriptionId: String = createPushSubscription(pushServer,
      keys = s""""keys": { "p256dh": "$p256dh", "auth": "$auth" },""")

    awaitAtMostTenSeconds.untilAsserted(() => assertThat(pushed(pushServer).size).isEqualTo(1))
    val pushVerification: HttpRequest = pushed(pushServer).head
    assertThat(pushVerification.getFirstHeader("Content-Encoding")).isEqualTo("aes128gcm")
    verifiedClaims(pushVerification)
    val clear: String = new String(new WebPushHybridDecrypt.Builder()
      .withAuthSecret(authSecret)
      .withRecipientPublicKey(uaPublicKey)
      .withRecipientPrivateKey(uaKeyPair.getPrivate.asInstanceOf[ECPrivateKey])
      .build
      .decrypt(pushVerification.getBodyAsRawBytes, null), StandardCharsets.UTF_8)
    assertThatJson(clear).node("pushSubscriptionId").isEqualTo(pushSubscriptionId)
  }
}
