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

import java.util.UUID

import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.refineV
import eu.timepit.refined.types.string.NonEmptyString
import org.apache.james.jmap.api.model.{PushSubscriptionExpiredTime, PushSubscriptionId, VerificationCode}
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.mail.{InvalidPropertyException, InvalidUpdateException, PatchUpdateValidationException, UnsupportedPropertyUpdatedException}
import org.apache.james.jmap.method.WithoutAccountId
import play.api.libs.json.{JsObject, JsString, JsValue}

import scala.util.Try

case class PushSubscriptionSetRequest(create: Option[Map[PushSubscriptionCreationId, JsObject]],
                                      update: Option[Map[UnparsedPushSubscriptionId, PushSubscriptionPatchObject]],
                                      destroy: Option[Seq[UnparsedPushSubscriptionId]]) extends WithoutAccountId

case class PushSubscriptionCreationId(id: Id) {
  def serialise: String = id.value
}
case class UnparsedPushSubscriptionId(id: Id) {
  def serialise: String = id.value
  def parse: Either[IllegalArgumentException, PushSubscriptionId] = Try(UUID.fromString(id.value))
    .toEither
    .left.map({
      case e: IllegalArgumentException => e
      case e => new IllegalArgumentException(e)
    }).map(uuid => PushSubscriptionId(uuid))
}

object PushSubscriptionUpdateResponse {
  def empty: PushSubscriptionUpdateResponse = PushSubscriptionUpdateResponse(JsObject(Map[String, JsValue]()))
}

case class PushSubscriptionUpdateResponse(value: JsObject)

object PushSubscriptionPatchObject {
  type KeyConstraint = NonEmpty
  type PushSubscriptionPatchObjectKey = String Refined KeyConstraint

  def notFound(property: String): Either[PatchUpdateValidationException, Update] = {
    val refinedKey: Either[String, PushSubscriptionPatchObjectKey] = refineV(property)
    refinedKey.fold[Either[PatchUpdateValidationException, Update]](
      cause => Left(InvalidPropertyException(property = property, cause = s"Invalid property specified in a patch object: $cause")),
      value => Left(UnsupportedPropertyUpdatedException(value)))
  }
}

case class PushSubscriptionPatchObject(value: Map[String, JsValue]) {
  val updates: Iterable[Either[PatchUpdateValidationException, Update]] = value.map({
    case (property, newValue) => property match {
      case "verificationCode" => VerificationCodeUpdate.parse(newValue)
      case property => PushSubscriptionPatchObject.notFound(property)
    }
  })

  def validate(): Either[PatchUpdateValidationException, ValidatedPushSubscriptionPatchObject] = {
    val maybeParseException: Option[PatchUpdateValidationException] = updates
      .flatMap(x => x match {
        case Left(e) => Some(e)
        case _ => None
      }).headOption

    val verificationCodeUpdate: Option[VerificationCodeUpdate] = updates
      .flatMap(x => x match {
        case Right(VerificationCodeUpdate(newName)) => Some(VerificationCodeUpdate(newName))
        case _ => None
      }).headOption

    maybeParseException
      .map(e => Left(e))
      .getOrElse(scala.Right(ValidatedPushSubscriptionPatchObject(
        verificationCodeUpdate = verificationCodeUpdate.map(_.newVerificationCode))))
  }
}

object VerificationCodeUpdate {
  def parse(jsValue: JsValue): Either[PatchUpdateValidationException, Update] = jsValue match {
    case JsString(aString) => Right(VerificationCodeUpdate(VerificationCode(aString)))
    case _ => Left(InvalidUpdateException("verificationCode", "Expecting a JSON string as an argument"))
  }
}

sealed trait Update
case class VerificationCodeUpdate(newVerificationCode: VerificationCode) extends Update

object ValidatedPushSubscriptionPatchObject {
  val verificationCodeProperty: NonEmptyString = "verificationCode"
}

case class ValidatedPushSubscriptionPatchObject(verificationCodeUpdate: Option[VerificationCode]) {
  val shouldUpdate: Boolean = verificationCodeUpdate.isDefined

  val updatedProperties: Properties = Properties(Set(
    verificationCodeUpdate.map(_ => ValidatedPushSubscriptionPatchObject.verificationCodeProperty))
    .flatMap(_.toList))
}

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
                                       notCreated: Option[Map[PushSubscriptionCreationId, SetError]],
                                       updated: Option[Map[PushSubscriptionId, PushSubscriptionUpdateResponse]],
                                       notUpdated: Option[Map[UnparsedPushSubscriptionId, SetError]],
                                       destroyed: Option[Seq[PushSubscriptionId]],
                                       notDestroyed: Option[Map[UnparsedPushSubscriptionId, SetError]])
