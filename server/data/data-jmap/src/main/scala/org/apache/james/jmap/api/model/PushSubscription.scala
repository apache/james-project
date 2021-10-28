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

import java.net.URL
import java.time.{Clock, ZonedDateTime}
import java.util.UUID
import scala.util.Try

import scala.util.Try

object PushSubscriptionId {
  def generate(): PushSubscriptionId = PushSubscriptionId(UUID.randomUUID)
}

case class PushSubscriptionId(value: UUID)

case class DeviceClientId(value: String) extends AnyVal

object VerificationCode {
  def generate(): VerificationCode = VerificationCode(UUID.randomUUID().toString)
}

case class VerificationCode(value: String) extends AnyVal

object PushSubscriptionServerURL {
  def from(value: String): Try[PushSubscriptionServerURL] = Try(PushSubscriptionServerURL(new URL(value)))
}

case class PushSubscriptionServerURL(value: URL)

case class PushSubscriptionExpiredTime(value: ZonedDateTime) {
  def isAfter(date: ZonedDateTime): Boolean = value.isAfter(date)
  def isBefore(date: ZonedDateTime): Boolean = value.isBefore(date)
}

case class PushSubscriptionKeys(p256dh: String, auth: String)

case class PushSubscriptionCreationRequest(deviceClientId: DeviceClientId,
                                           url: PushSubscriptionServerURL,
                                           keys: Option[PushSubscriptionKeys] = None,
                                           expires: Option[PushSubscriptionExpiredTime] = None,
                                           types: Seq[TypeName])

object PushSubscription {
  val VALIDATED: Boolean = true
  val EXPIRES_TIME_MAX_DAY: Int = 7

  def from(creationRequest: PushSubscriptionCreationRequest,
           expireTime: PushSubscriptionExpiredTime): PushSubscription =
    PushSubscription(id = PushSubscriptionId.generate(),
      deviceClientId = creationRequest.deviceClientId,
      url = creationRequest.url,
      keys = creationRequest.keys,
      verificationCode = VerificationCode.generate(),
      validated = !VALIDATED,
      expires = expireTime,
      types = creationRequest.types)
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

case class ExpireTimeInvalidException(expires: ZonedDateTime, message: String) extends RuntimeException

case class DeviceClientIdInvalidException(deviceClientId: DeviceClientId, message: String) extends RuntimeException

case class InvalidPushSubscriptionKeys(keys: PushSubscriptionKeys) extends RuntimeException