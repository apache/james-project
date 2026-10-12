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

package org.apache.james.jmap.pushsubscription

import java.math.BigInteger
import java.net.URI
import java.security.interfaces.{ECPrivateKey, ECPublicKey}
import java.security.spec.ECGenParameterSpec
import java.security.{KeyPair, KeyPairGenerator}
import java.time.{Clock, Instant, ZoneOffset}
import java.util.{Base64, Date}

import io.jsonwebtoken.{Claims, Jws, Jwts}
import org.apache.james.jmap.pushsubscription.VapidTestFixture.{CONFIGURATION, KEY_PAIR, NOW, PRIVATE_KEY, PUBLIC_KEY, parse}
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.junit.jupiter.api.Test

object VapidTestFixture {
  val NOW: Instant = Instant.parse("2026-01-01T10:00:00Z")
  val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)

  val KEY_PAIR: KeyPair = generateKeyPair()
  // As push libraries and browsers handle them: the uncompressed point, and the private value, base64url encoded
  val PUBLIC_KEY: String = encodePublicKey(KEY_PAIR)
  val PRIVATE_KEY: String = encodePrivateKey(KEY_PAIR)
  val SUBJECT: String = "mailto:postmaster@example.com"
  val CONFIGURATION: VapidConfiguration = VapidConfiguration.from(Some(PUBLIC_KEY), Some(PRIVATE_KEY), Some(SUBJECT)).get

  def generateKeyPair(): KeyPair = {
    val generator: KeyPairGenerator = KeyPairGenerator.getInstance("EC")
    generator.initialize(new ECGenParameterSpec("secp256r1"))
    generator.generateKeyPair()
  }

  def encodePublicKey(keyPair: KeyPair): String = {
    val point = keyPair.getPublic.asInstanceOf[ECPublicKey].getW
    Base64.getUrlEncoder.withoutPadding()
      .encodeToString(Array[Byte](0x04) ++ coordinate(point.getAffineX) ++ coordinate(point.getAffineY))
  }

  def encodePrivateKey(keyPair: KeyPair): String =
    Base64.getUrlEncoder.withoutPadding()
      .encodeToString(coordinate(keyPair.getPrivate.asInstanceOf[ECPrivateKey].getS))

  private def coordinate(value: BigInteger): Array[Byte] = {
    val bytes: Array[Byte] = value.toByteArray.dropWhile(_ == 0)
    Array.fill[Byte](32 - bytes.length)(0) ++ bytes
  }

  def pushClient(vapid: Option[VapidConfiguration]): DefaultWebPushClient =
    new DefaultWebPushClient(PushClientConfiguration(
      maxTimeoutSeconds = Some(10),
      maxConnections = Some(10),
      preventServerSideRequestForgery = false,
      vapid = vapid), new SSRFValidator(), CLOCK)

  val SIGNING_PUSH_CLIENT: DefaultWebPushClient = pushClient(Some(CONFIGURATION))
  val UNSIGNED_PUSH_CLIENT: DefaultWebPushClient = pushClient(None)

  /**
   * The claims of the token as the push service reads them: its JSON payload.
   */
  def payload(authorization: String, at: Instant = NOW): String = {
    parse(authorization, at = at)
    val token: String = authorization.stripPrefix("vapid t=").takeWhile(_ != ',')
    new String(Base64.getUrlDecoder.decode(token.split('.')(1)), java.nio.charset.StandardCharsets.UTF_8)
  }

  def subject(authorization: String): String = parse(authorization).getPayload.getSubject

  /**
   * The token of a `vapid t=<token>, k=<key>` header, verified with the public key of the fixture at the time of the fixture.
   */
  def parse(authorization: String, keyPair: KeyPair = KEY_PAIR, at: Instant = NOW): Jws[Claims] = {
    val token: String = authorization.stripPrefix("vapid t=").takeWhile(_ != ',')
    Jwts.parser()
      .verifyWith(keyPair.getPublic)
      .clock(() => Date.from(at))
      .build()
      .parseSignedClaims(token)
  }
}

class VapidTest {
  private val signer: VapidSigner = new VapidSigner(CONFIGURATION, VapidTestFixture.CLOCK)

  @Test
  def authorizationShouldFollowTheVapidScheme(): Unit =
    assertThat(signer.authorization(new URI("https://push.example.com/send/abc").toURL))
      .matches("vapid t=[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+, k=" + PUBLIC_KEY)

  @Test
  def tokenShouldBeSignedByThePrivateKey(): Unit = {
    val token = parse(signer.authorization(new URI("https://push.example.com/send/abc").toURL))

    assertThat(token.getHeader.getAlgorithm).isEqualTo("ES256")
    assertThat(token.getHeader.getType).isEqualTo("JWT")
  }

  @Test
  def tokenShouldNotBeVerifiedByAnotherKey(): Unit =
    assertThatThrownBy(() => parse(signer.authorization(new URI("https://push.example.com/send/abc").toURL), VapidTestFixture.generateKeyPair()))
      .isInstanceOf(classOf[io.jsonwebtoken.security.SignatureException])

  @Test
  def audienceShouldBeTheOriginOfThePushResource(): Unit = {
    assertThat(VapidTestFixture.payload(signer.authorization(new URI("https://push.example.com/send/abc?d=1").toURL)))
      .contains("\"aud\":\"https://push.example.com\"")
  }

  @Test
  def audienceShouldKeepANonDefaultPort(): Unit = {
    assertThat(VapidTestFixture.payload(signer.authorization(new URI("https://push.example.com:8443/send/abc").toURL)))
      .contains("\"aud\":\"https://push.example.com:8443\"")
  }

  @Test
  def audienceShouldOmitTheDefaultPort(): Unit = {
    assertThat(VapidTestFixture.payload(signer.authorization(new URI("https://push.example.com:443/send/abc").toURL)))
      .contains("\"aud\":\"https://push.example.com\"")
  }

  @Test
  def audienceShouldOmitTheDefaultPortOfHttp(): Unit =
    assertThat(VapidTestFixture.payload(signer.authorization(new URI("http://push.example.com:80/send/abc").toURL)))
      .contains("\"aud\":\"http://push.example.com\"")

  @Test
  def audienceShouldBeInLowerCase(): Unit =
    assertThat(VapidTestFixture.payload(signer.authorization(new URI("https://Push.Example.COM/send/abc").toURL)))
      .contains("\"aud\":\"https://push.example.com\"")

  @Test
  def tokenShouldExpireWithinADay(): Unit = {
    val token = parse(signer.authorization(new URI("https://push.example.com/send/abc").toURL))

    val expiration: Instant = token.getPayload.getExpiration.toInstant

    assertThat(expiration.isAfter(NOW)).isTrue
    assertThat(expiration.isAfter(NOW.plusSeconds(24 * 60 * 60))).isFalse
  }

  @Test
  def tokenShouldCarryTheSubject(): Unit = {
    val token = parse(signer.authorization(new URI("https://push.example.com/send/abc").toURL))

    assertThat(token.getPayload.getSubject).isEqualTo(VapidTestFixture.SUBJECT)
  }

  @Test
  def subjectShouldBeOptional(): Unit = {
    val withoutSubject = VapidConfiguration.from(Some(PUBLIC_KEY), Some(PRIVATE_KEY), None).get
    val token = parse(new VapidSigner(withoutSubject, VapidTestFixture.CLOCK).authorization(new URI("https://push.example.com/send/abc").toURL))

    assertThat(token.getPayload.getSubject).isNull()
  }

  @Test
  def configurationShouldBeAbsentWithoutKeys(): Unit =
    assertThat(VapidConfiguration.from(None, None, Some(VapidTestFixture.SUBJECT)).isDefined).isFalse

  @Test
  def configurationShouldTreatBlankKeysAsAbsent(): Unit =
    assertThat(VapidConfiguration.from(Some(" "), Some(""), None).isDefined).isFalse

  @Test
  def applicationServerKeyShouldBeUnpadded(): Unit =
    assertThat(VapidConfiguration.from(Some(PUBLIC_KEY + "="), Some(PRIVATE_KEY), None).get.applicationServerKey)
      .isEqualTo(PUBLIC_KEY)

  @Test
  def configurationShouldRejectASingleKey(): Unit = {
    assertThatThrownBy(() => VapidConfiguration.from(Some(PUBLIC_KEY), None, None))
      .isInstanceOf(classOf[IllegalArgumentException])
    assertThatThrownBy(() => VapidConfiguration.from(None, Some(PRIVATE_KEY), None))
      .isInstanceOf(classOf[IllegalArgumentException])
  }

  @Test
  def configurationShouldRejectAPublicKeyOfAnotherPrivateKey(): Unit =
    assertThatThrownBy(() => VapidConfiguration.from(Some(VapidTestFixture.encodePublicKey(VapidTestFixture.generateKeyPair())), Some(PRIVATE_KEY), None))
      .isInstanceOf(classOf[IllegalArgumentException])
      .hasMessageContaining("does not match")

  @Test
  def configurationShouldRejectAPublicKeyThatIsNotAnUncompressedPoint(): Unit =
    assertThatThrownBy(() => VapidConfiguration.from(Some(PRIVATE_KEY), Some(PRIVATE_KEY), None))
      .isInstanceOf(classOf[IllegalArgumentException])
      .hasMessageContaining("uncompressed P-256 point")

  @Test
  def configurationShouldRejectAPublicKeyThatIsNotOnTheCurve(): Unit = {
    val notAPoint: String = Base64.getUrlEncoder.withoutPadding().encodeToString(Array[Byte](0x04) ++ Array.fill[Byte](64)(1))

    assertThatThrownBy(() => VapidConfiguration.from(Some(notAPoint), Some(PRIVATE_KEY), None))
      .isInstanceOf(classOf[IllegalArgumentException])
      .hasMessageContaining("not a point of the P-256 curve")
  }

  @Test
  def configurationShouldRejectAPrivateKeyOfTheWrongLength(): Unit =
    assertThatThrownBy(() => VapidConfiguration.from(Some(PUBLIC_KEY), Some(PUBLIC_KEY), None))
      .isInstanceOf(classOf[IllegalArgumentException])
      .hasMessageContaining("32 bytes")

  @Test
  def configurationShouldRejectKeysThatAreNotBase64Url(): Unit =
    assertThatThrownBy(() => VapidConfiguration.from(Some("not base64 !"), Some(PRIVATE_KEY), None))
      .isInstanceOf(classOf[IllegalArgumentException])
      .hasMessageContaining("base64url")

  @Test
  def configurationShouldRejectASubjectThatIsNotAContactUri(): Unit =
    assertThatThrownBy(() => VapidConfiguration.from(Some(PUBLIC_KEY), Some(PRIVATE_KEY), Some("postmaster@example.com")))
      .isInstanceOf(classOf[IllegalArgumentException])
      .hasMessageContaining("mailto:")

  @Test
  def toStringShouldNotLeakThePrivateKey(): Unit =
    assertThat(CONFIGURATION.toString)
      .doesNotContain(PRIVATE_KEY)
      .doesNotContain(KEY_PAIR.getPrivate.asInstanceOf[ECPrivateKey].getS.toString)
}
