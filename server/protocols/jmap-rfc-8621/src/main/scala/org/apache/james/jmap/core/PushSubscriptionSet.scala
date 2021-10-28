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

import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.refineV
import eu.timepit.refined.types.string.NonEmptyString
import org.apache.james.jmap.api.model.{PushSubscriptionExpiredTime, PushSubscriptionId, TypeName}
import org.apache.james.jmap.change.{EmailTypeName, MailboxTypeName}
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.method.WithoutAccountId
import play.api.libs.json.JsObject

case class PushSubscriptionSetRequest(create: Option[Map[PushSubscriptionCreationId, JsObject]]) extends WithoutAccountId

case class PushSubscriptionCreationId(id: Id)

object PushSubscriptionCreation {
  private val serverSetProperty: Set[String] = Set("id", "verificationCode")
  private val assignableProperties: Set[String] = Set("deviceClientId", "url", "keys", "expires", "types")
  private val knownProperties: Set[String] = assignableProperties ++ serverSetProperty

  def validateProperties(jsObject: JsObject): Either[PushSubscriptionCreationParseException, JsObject] =
    (jsObject.keys.intersect(serverSetProperty), jsObject.keys.diff(knownProperties)) match {
      case (_, unknownProperties) if unknownProperties.nonEmpty =>
        Left(PushSubscriptionCreationParseException(SetError.invalidArguments(
          SetErrorDescription("Some unknown properties were specified"),
          Some(toProperties(unknownProperties.toSet)))))
      case (specifiedServerSetProperties, _) if specifiedServerSetProperties.nonEmpty =>
        Left(PushSubscriptionCreationParseException(SetError.invalidArguments(
          SetErrorDescription("Some server-set properties were specified"),
          Some(toProperties(specifiedServerSetProperties.toSet)))))
      case _ => scala.Right(jsObject)
    }

  private def toProperties(strings: Set[String]): Properties = Properties(strings
    .flatMap(string => {
      val refinedValue: Either[String, NonEmptyString] = refineV[NonEmpty](string)
      refinedValue.fold(_ => None,  Some(_))
    }))
}

case class PushSubscriptionCreationParseException(setError: SetError) extends Exception

case class PushSubscriptionCreationResponse(id: PushSubscriptionId,
                                            expires: Option[PushSubscriptionExpiredTime])

case class PushSubscriptionSetResponse(created: Option[Map[PushSubscriptionCreationId, PushSubscriptionCreationResponse]],
                                       notCreated: Option[Map[PushSubscriptionCreationId, SetError]])

object TypeNameHelper {
  def parse(value: String): Option[TypeName] =
    MailboxTypeName.parse(value)
      .orElse(EmailTypeName.parse(value))
}
