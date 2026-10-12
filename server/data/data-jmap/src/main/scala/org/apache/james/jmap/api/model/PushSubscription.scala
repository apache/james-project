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

package org.apache.james.jmap.api.model

import java.net.{URI, URL}
import java.security.interfaces.ECPublicKey
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.{Base64, UUID}

import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.apps.webpush.WebPushHybridEncrypt
import com.google.crypto.tink.subtle.EllipticCurves
import com.google.crypto.tink.subtle.EllipticCurves.{CurveType, PointFormatType}
import org.apache.james.jmap.api.change.TypeStateFactory
import org.apache.james.jmap.api.model.ExpireTimeInvalidException.TIME_FORMATTER

import scala.util.Try

object PushSubscriptionId {
  def generate(): PushSubscriptionId = PushSubscriptionId(UUID.randomUUID)

  def parse(string: String): Either[IllegalArgumentException, PushSubscriptionId] = Try(UUID.fromString(string)).toEither
    .map(PushSubscriptionId(_))
    .left.map {
    case e: IllegalArgumentException => e
    case e => new IllegalArgumentException(e)
  }
}

case class PushSubscriptionId(value: UUID) {
  def serialise: String = value.toString
}

case class DeviceClientId(value: String) extends AnyVal

object VerificationCode {
  def generate(): VerificationCode = VerificationCode(UUID.randomUUID().toString)
}

case class VerificationCode(value: String) extends AnyVal

object PushSubscriptionServerURL {
  def from(value: String): Try[PushSubscriptionServerURL] = Try(PushSubscriptionServerURL(new URI(value).toURL))
}

case class PushSubscriptionServerURL(value: URL)

case class PushSubscriptionExpiredTime(value: ZonedDateTime) {
  def isAfter(date: ZonedDateTime): Boolean = value.isAfter(date)
  def isBefore(date: ZonedDateTime): Boolean = value.isBefore(date)
}

object PushSubscriptionKeys {
  private val UNCOMPRESSED_POINT_LENGTH: Int = 65
  private val UNCOMPRESSED_POINT_TAG: Byte = 0x04
}

case class PushSubscriptionKeys(p256dh: String, auth: String) {
  def validate: Either[IllegalArgumentException, PushSubscriptionKeys] =
    Try(asHybridEncrypt()).map(_ => this)
      .toEither
      .left.map {
      case e: IllegalArgumentException => e
      case e => new IllegalArgumentException(e)
    }

  // Follows https://datatracker.ietf.org/doc/html/rfc8291
  // Message Encryption for Web Push
  def encrypt(payload: Array[Byte]): Array[Byte] = asHybridEncrypt()
    .encrypt(payload, null)

  private def asHybridEncrypt(): HybridEncrypt =  new WebPushHybridEncrypt.Builder()
    .withAuthSecret(Base64.getUrlDecoder().decode(auth))
    .withRecipientPublicKey(asECPublicKey())
    .build()

  // RFC 8291 section 3.1: the key is an uncompressed point (65 bytes), which is what browsers hand out.
  // Its X.509 encoding, the only form that used to be read, is still accepted.
  private def asECPublicKey(): ECPublicKey = {
    val key: Array[Byte] = Base64.getUrlDecoder.decode(p256dh)
    if (key.length == PushSubscriptionKeys.UNCOMPRESSED_POINT_LENGTH && key(0) == PushSubscriptionKeys.UNCOMPRESSED_POINT_TAG) {
      EllipticCurves.getEcPublicKey(CurveType.NIST_P256, PointFormatType.UNCOMPRESSED, key)
    } else {
      EllipticCurves.getEcPublicKey(key)
    }
  }
}

object PushSubscriptionCreationRequest {
  val noTypes: Seq[TypeName] = Seq()
}

case class PushSubscriptionCreationRequest(deviceClientId: DeviceClientId,
                                           url: PushSubscriptionServerURL,
                                           keys: Option[PushSubscriptionKeys] = None,
                                           expires: Option[PushSubscriptionExpiredTime] = None,
                                           types: Option[Seq[TypeName]] = None) {

  def validate: Either[IllegalArgumentException, PushSubscriptionCreationRequest] =
    for {
      _ <- validateTypes
      _ <- validateKeys
    } yield {
      this
    }

  private def validateTypes: Either[IllegalArgumentException, PushSubscriptionCreationRequest] =
    types match {
      case Some(Seq()) => scala.Left(new IllegalArgumentException("types must not be empty"))
      case _ => Right(this)
    }

  private def validateKeys: Either[IllegalArgumentException, PushSubscriptionCreationRequest] =
    keys.map(_.validate.map(_ => this))
      .getOrElse(Right(this))
}

object PushSubscription {
  val VALIDATED: Boolean = true
  val EXPIRES_TIME_MAX_DAY: Int = 7

  def from(creationRequest: PushSubscriptionCreationRequest,
           expireTime: PushSubscriptionExpiredTime,
           typeStateFactory: TypeStateFactory): PushSubscription =
    PushSubscription(id = PushSubscriptionId.generate(),
      deviceClientId = creationRequest.deviceClientId,
      url = creationRequest.url,
      keys = creationRequest.keys,
      verificationCode = VerificationCode.generate(),
      validated = !VALIDATED,
      expires = expireTime,
      types = creationRequest.types.getOrElse(typeStateFactory.all.toSeq))
}

case class PushSubscription(id: PushSubscriptionId,
                            deviceClientId: DeviceClientId,
                            url: PushSubscriptionServerURL,
                            keys: Option[PushSubscriptionKeys],
                            verificationCode: VerificationCode,
                            validated: Boolean,
                            expires: PushSubscriptionExpiredTime,
                            types: Seq[TypeName]) {
  def withTypes(types: Seq[TypeName]): PushSubscription = copy(types = types)

  def verified(): PushSubscription = copy(validated = true)

  def withExpires(expires: PushSubscriptionExpiredTime): PushSubscription = copy(expires = expires)
}

case class PushSubscriptionNotFoundException(id: PushSubscriptionId) extends RuntimeException

object ExpireTimeInvalidException {
  val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")
}
case class ExpireTimeInvalidException(expires: ZonedDateTime, message: String) extends IllegalStateException(s"`${expires.format(TIME_FORMATTER)}` $message")

case class DeviceClientIdInvalidException(deviceClientId: DeviceClientId, message: String) extends IllegalArgumentException(s"`${deviceClientId.value}` $message")
case class InvalidPushSubscriptionKeys(keys: PushSubscriptionKeys) extends IllegalArgumentException