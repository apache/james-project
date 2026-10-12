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
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.interfaces.{ECPrivateKey, ECPublicKey}
import java.security.spec.ECPrivateKeySpec
import java.security.{KeyFactory, Signature}
import java.time.{Clock, Duration}
import java.util.{Base64, Date, Locale}

import com.google.common.base.Preconditions
import com.google.crypto.tink.subtle.EllipticCurves
import com.google.crypto.tink.subtle.EllipticCurves.{CurveType, PointFormatType}
import io.jsonwebtoken.Jwts
import org.slf4j.{Logger, LoggerFactory}

import scala.util.Try

/**
 * Voluntary Application Server Identification (VAPID, RFC 8292) of the Web Push requests,
 * as JMAP servers advertise it (RFC 9749).
 */
object VapidConfiguration {
  private val LOGGER: Logger = LoggerFactory.getLogger(classOf[VapidConfiguration])
  private val UNCOMPRESSED_POINT_LENGTH: Int = 65
  private val UNCOMPRESSED_POINT_TAG: Byte = 0x04
  private val COORDINATE_LENGTH: Int = 32

  /**
   * @param publicKey  P-256 public key, uncompressed (65 bytes), base64url encoded: the `applicationServerKey` of browsers
   * @param privateKey P-256 private key (32 bytes), base64url encoded
   * @param subject    contact of the operator, a `mailto:` or `https:` URI
   */
  def from(publicKey: Option[String], privateKey: Option[String], subject: Option[String]): Option[VapidConfiguration] =
    (publicKey.map(_.trim).filter(_.nonEmpty), privateKey.map(_.trim).filter(_.nonEmpty)) match {
      case (None, None) => None
      case (Some(public), Some(secret)) => Some(parse(public, secret, subject.map(_.trim).filter(_.nonEmpty)))
      case _ => throw new IllegalArgumentException("VAPID needs both its public and its private key")
    }

  private def parse(publicKey: String, privateKey: String, subject: Option[String]): VapidConfiguration = {
    val point: Array[Byte] = decode(publicKey, "public")
    Preconditions.checkArgument(point.length == UNCOMPRESSED_POINT_LENGTH && point(0) == UNCOMPRESSED_POINT_TAG,
      "The VAPID public key must be an uncompressed P-256 point (65 bytes), base64url encoded".asInstanceOf[Object])
    val scalar: Array[Byte] = decode(privateKey, "private")
    Preconditions.checkArgument(scalar.length == COORDINATE_LENGTH,
      "The VAPID private key must be a P-256 private key (32 bytes), base64url encoded".asInstanceOf[Object])
    subject.foreach(value => Preconditions.checkArgument(value.startsWith("mailto:") || value.startsWith("https:"),
      "The VAPID subject must be a `mailto:` or an `https:` URI".asInstanceOf[Object]))
    if (subject.isEmpty) {
      LOGGER.warn("VAPID is configured without a subject (`webpush.vapid.subject`): some push services, as the one of Apple, refuse such requests")
    }

    // Checks that the point is on the curve, which the JDK key factory does not
    val ecPublicKey: ECPublicKey = Try(EllipticCurves.getEcPublicKey(CurveType.NIST_P256, PointFormatType.UNCOMPRESSED, point))
      .getOrElse(throw new IllegalArgumentException("The VAPID public key is not a point of the P-256 curve"))
    val ecPrivateKey: ECPrivateKey = KeyFactory.getInstance("EC")
      .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalar), ecPublicKey.getParams)).asInstanceOf[ECPrivateKey]
    Preconditions.checkArgument(isKeyPair(ecPublicKey, ecPrivateKey),
      "The VAPID public key does not match the VAPID private key".asInstanceOf[Object])

    VapidConfiguration(
      applicationServerKey = Base64.getUrlEncoder.withoutPadding().encodeToString(point),
      privateKey = ecPrivateKey,
      subject = subject)
  }

  private def decode(value: String, name: String): Array[Byte] =
    Try(Base64.getUrlDecoder.decode(value))
      .getOrElse(throw new IllegalArgumentException(s"The VAPID $name key is not base64url encoded"))

  private def isKeyPair(publicKey: ECPublicKey, privateKey: ECPrivateKey): Boolean = {
    val challenge: Array[Byte] = "vapid".getBytes(StandardCharsets.UTF_8)
    val signer: Signature = Signature.getInstance("SHA256withECDSA")
    signer.initSign(privateKey)
    signer.update(challenge)
    val verifier: Signature = Signature.getInstance("SHA256withECDSA")
    verifier.initVerify(publicKey)
    verifier.update(challenge)
    verifier.verify(signer.sign())
  }
}

/**
 * @param applicationServerKey the public key as push services and browsers expect it: uncompressed, base64url without padding
 */
case class VapidConfiguration(applicationServerKey: String,
                              privateKey: ECPrivateKey,
                              subject: Option[String]) {
  // The private key never reaches the logs
  override def toString: String = s"VapidConfiguration($applicationServerKey, $subject)"
}

object VapidSigner {
  // RFC 8292 section 2: a token is valid for 24 hours at most
  val TOKEN_VALIDITY: Duration = Duration.ofHours(12)
}

class VapidSigner(configuration: VapidConfiguration, clock: Clock) {
  /**
   * The `Authorization` header of a request to a push resource: a token signed for the origin of the push service.
   */
  def authorization(pushResource: URL): String = {
    val token: String = Jwts.builder()
      .header().`type`("JWT").and()
      // A single string, not an array: what push services expect
      .audience().single(origin(pushResource))
      .expiration(Date.from(clock.instant().plus(VapidSigner.TOKEN_VALIDITY)))
      .subject(configuration.subject.orNull)
      .signWith(configuration.privateKey, Jwts.SIG.ES256)
      .compact()
    s"vapid t=$token, k=${configuration.applicationServerKey}"
  }

  // RFC 6454: scheme and host in lower case, the port unless it is the default one
  private def origin(url: URL): String = {
    val host: String = url.getHost.toLowerCase(Locale.ROOT)
    if (url.getPort == -1 || url.getPort == url.getDefaultPort) {
      s"${url.getProtocol}://$host"
    } else {
      s"${url.getProtocol}://$host:${url.getPort}"
    }
  }
}
