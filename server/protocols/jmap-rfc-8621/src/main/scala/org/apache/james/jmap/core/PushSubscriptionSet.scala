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

import java.time.ZonedDateTime
import java.util.UUID

import cats.implicits._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.refineV
import eu.timepit.refined.types.string.NonEmptyString
import org.apache.james.jmap.api.change.TypeStateFactory
import org.apache.james.jmap.api.model.{PushSubscriptionExpiredTime, PushSubscriptionId, TypeName, VerificationCode}
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.Properties.toProperties
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.mail.{InvalidPropertyException, InvalidUpdateException, PatchUpdateValidationException, UnsupportedPropertyUpdatedException}
import org.apache.james.jmap.method.{SetRequest, WithoutAccountId}
import play.api.libs.json.{JsArray, JsObject, JsString, JsValue}

import scala.util.{Failure, Success, Try}

case class PushSubscriptionSetRequest(create: Option[Map[PushSubscriptionCreationId, JsObject]],
                                      update: Option[Map[UnparsedPushSubscriptionId, PushSubscriptionPatchObject]],
                                      destroy: Option[Seq[UnparsedPushSubscriptionId]]) extends WithoutAccountId with SetRequest {
  override def idCount: Int = create.map(_.size).getOrElse(0) + update.map(_.size).getOrElse(0) + destroy.map(_.size).getOrElse(0)
}

case class PushSubscriptionCreationId(id: Id) {
  def serialise: String = id.value
}
object UnparsedPushSubscriptionId {
  def of(id: PushSubscriptionId): UnparsedPushSubscriptionId = UnparsedPushSubscriptionId(Id.validate(id.serialise).toOption.get)
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

case class PushSubscriptionUpdateResponse(expires: Option[UTCDate])

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
  def computeUpdates(typeStateFactory: TypeStateFactory): Iterable[Either[PatchUpdateValidationException, Update]] = value.map({
    case (property, newValue) => property match {
      case "verificationCode" => VerificationCodeUpdate.parse(newValue)
      case "types" => TypesUpdate.parse(newValue, typeStateFactory)
      case "expires" => ExpiresUpdate.parse(newValue)
      case property => PushSubscriptionPatchObject.notFound(property)
    }
  })

  def validate(typeStateFactory: TypeStateFactory): Either[PatchUpdateValidationException, ValidatedPushSubscriptionPatchObject] = {
    val updates = computeUpdates(typeStateFactory)
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

    val expiresUpdate: Option[ExpiresUpdate] = updates
      .flatMap(x => x match {
        case Right(ExpiresUpdate(newExpires)) => Some(ExpiresUpdate(newExpires))
        case _ => None
      }).headOption

    val typesUpdate: Option[TypesUpdate] = updates
      .flatMap(x => x match {
        case Right(TypesUpdate(newTypes)) => Some(TypesUpdate(newTypes))
        case _ => None
      }).headOption

    maybeParseException
      .map(e => Left(e))
      .getOrElse(scala.Right(ValidatedPushSubscriptionPatchObject(
        verificationCodeUpdate = verificationCodeUpdate.map(_.newVerificationCode),
        typesUpdate = typesUpdate.map(_.types),
        expiresUpdate = expiresUpdate.map(expiresUpdate => PushSubscriptionExpiredTime(expiresUpdate.newExpires.asUTC)))))
  }
}

object VerificationCodeUpdate {
  def parse(jsValue: JsValue): Either[PatchUpdateValidationException, Update] = jsValue match {
    case JsString(aString) => Right(VerificationCodeUpdate(VerificationCode(aString)))
    case _ => Left(InvalidUpdateException("verificationCode", "Expecting a JSON string as an argument"))
  }
}

object TypesUpdate {
  def parse(jsValue: JsValue, typeStateFactory: TypeStateFactory): Either[PatchUpdateValidationException, Update] = jsValue match {
    case JsArray(aArray) => aArray.toList
      .map(js => parseType(js, typeStateFactory))
      .sequence
      .map(_.toSet)
      .map(TypesUpdate(_))
    case _ => Left(InvalidUpdateException("types", "Expecting an array of JSON strings as an argument"))
  }
  def parseType(jsValue: JsValue, typeStateFactory: TypeStateFactory): Either[PatchUpdateValidationException, TypeName] = jsValue match {
    case JsString(aString) => typeStateFactory.parse(aString).left.map(e => InvalidUpdateException("types", e.getMessage))
    case _ => Left(InvalidUpdateException("types", "Expecting an array of JSON strings as an argument"))
  }
}

object ExpiresUpdate {
  def parse(jsValue: JsValue): Either[PatchUpdateValidationException, Update] = jsValue match {
    case JsString(aString) => toZonedDateTime(aString) match {
      case Success(value) => Right(ExpiresUpdate(UTCDate(value)))
      case Failure(e) => Left(InvalidUpdateException("expires", "This string can not be parsed to UTCDate"))
    }
    case _ => Left(InvalidUpdateException("expires", "Expecting a JSON string as an argument"))
  }

  private def toZonedDateTime(string: String): Try[ZonedDateTime] = Try(ZonedDateTime.parse(string))
}

sealed trait Update
case class VerificationCodeUpdate(newVerificationCode: VerificationCode) extends Update
case class TypesUpdate(types: Set[TypeName]) extends Update
case class ExpiresUpdate(newExpires: UTCDate) extends Update

object ValidatedPushSubscriptionPatchObject {
  val verificationCodeProperty: NonEmptyString = "verificationCode"
  val typesProperty: NonEmptyString = "types"
  val expiresUpdate: NonEmptyString = "expires"
}

case class ValidatedPushSubscriptionPatchObject(verificationCodeUpdate: Option[VerificationCode],
                                                typesUpdate: Option[Set[TypeName]],
                                                expiresUpdate: Option[PushSubscriptionExpiredTime]) {
  val shouldUpdate: Boolean = verificationCodeUpdate.isDefined || typesUpdate.isDefined || expiresUpdate.isDefined

  val updatedProperties: Properties = Properties(Set(
    verificationCodeUpdate.map(_ => ValidatedPushSubscriptionPatchObject.verificationCodeProperty),
    typesUpdate.map(_ => ValidatedPushSubscriptionPatchObject.typesProperty),
    expiresUpdate.map(_ => ValidatedPushSubscriptionPatchObject.expiresUpdate))
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
