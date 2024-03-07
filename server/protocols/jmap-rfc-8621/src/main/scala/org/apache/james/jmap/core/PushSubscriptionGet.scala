/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *  http://www.apache.org/licenses/LICENSE-2.0                  *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.core

import cats.implicits._
import eu.timepit.refined.auto._
import eu.timepit.refined.types.string.NonEmptyString
import org.apache.james.jmap.api.model.{DeviceClientId, PushSubscription, PushSubscriptionId, TypeName, VerificationCode}
import org.apache.james.jmap.method.{GetRequest, ValidableRequest, WithoutAccountId}

case class Ids(value: List[UnparsedPushSubscriptionId]) {
  def validate: Either[IllegalArgumentException, List[PushSubscriptionId]] =
    value.map(id => PushSubscriptionId.parse(id.serialise)).sequence
}

object PushSubscriptionGet {
  val allowedProperties: Properties = Properties("id", "deviceClientId", "expires", "types", "verificationCode")
  val idProperty: Properties = Properties("id")
}

case class PushSubscriptionGetRequest(ids: Option[Ids],
                                      properties: Option[Properties]) extends WithoutAccountId with GetRequest {
  override def idCount: Option[Long] = ids.map(_.value).map(_.size)

  def validateProperties: Either[IllegalArgumentException, Properties] =
    properties match {
      case None => Right(PushSubscriptionGet.allowedProperties)
      case Some(properties) =>
        val invalidProperties: Set[NonEmptyString] = properties.value -- PushSubscriptionGet.allowedProperties.value

        if (invalidProperties.nonEmpty) {
          Left(new IllegalArgumentException(s"The following properties [${invalidProperties.map(p => p.value).mkString(", ")}] do not exist."))
        } else {
          Right(properties ++ PushSubscriptionGet.idProperty)
        }
    }
}

case class PushSubscriptionGetResponse(list: List[PushSubscriptionDTO],
                                       notFound: Option[Ids])

object PushSubscriptionDTO {
  def from(pushSubscription: PushSubscription): PushSubscriptionDTO =
    PushSubscriptionDTO(
      id = pushSubscription.id,
      types = pushSubscription.types,
      deviceClientId = pushSubscription.deviceClientId,
      expires = UTCDate(pushSubscription.expires.value),
      verificationCode = Some(pushSubscription.verificationCode)
        .filter(_ => pushSubscription.validated))
}

case class PushSubscriptionDTO(id: PushSubscriptionId,
                               deviceClientId: DeviceClientId,
                               expires: UTCDate,
                               types: Seq[TypeName],
                               verificationCode: Option[VerificationCode])