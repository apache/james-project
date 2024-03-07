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

package org.apache.james.jmap.vacation

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.apache.james.jmap.core.SetError.{SetErrorDescription, SetErrorType, invalidArgumentValue, serverFailValue}
import org.apache.james.jmap.core.{AccountId, UuidState}
import org.apache.james.jmap.method.{SetRequest, WithAccountId}
import org.apache.james.util.ValuePatch
import org.apache.james.vacation.api.Vacation.ID
import org.apache.james.vacation.api.VacationPatch
import play.api.libs.json.{JsBoolean, JsNull, JsObject, JsString, JsValue}

import scala.util.{Failure, Success, Try}

case class VacationResponseSetRequest(accountId: AccountId,
                                      update: Option[Map[String, VacationResponsePatchObject]],
                                      create: Option[Map[String, JsObject]],
                                      destroy: Option[Seq[String]]) extends WithAccountId with SetRequest {

  override def idCount: Long = create.map(_.size).getOrElse(0) + update.map(_.size).getOrElse(0) + destroy.map(_.size).getOrElse(0)

  def parsePatch(): Map[String, Either[IllegalArgumentException, VacationResponsePatchObject]] =
    update.getOrElse(Map())
    .map({
      case (id, patch) if id.equals(ID) => (id, Right(patch))
      case (id, _) => (id, Left(new IllegalArgumentException(s"id $id must be singleton")))
    })
}

case class VacationResponsePatchObject(jsObject: JsObject) {
  def asVacationPatch: Either[IllegalArgumentException, VacationPatch] = {
    val parsedEntries: Seq[Either[IllegalArgumentException, VacationPatch.Builder]] = jsObject.fields
      .map(asVacationPatch)
      .toSeq

    val maybeError: Option[IllegalArgumentException] = parsedEntries.flatMap({
      case Left(e) => Some(e)
      case Right(_) => None
    }).headOption

    val patch: VacationPatch = parsedEntries.flatMap({
      case Right(builder) => Some(builder)
      case _ => None
    })
      .foldLeft(VacationPatch.builder()) { case (a, b) => a.addAll(b)}
      .build()

    maybeError.map(e => Left(e))
      .getOrElse(validatePatch(patch))
  }

  private def validatePatch(patch: VacationPatch): Either[IllegalArgumentException, VacationPatch] = {
    if(patch.getFromDate.isModified && patch.getToDate.isModified && patch.getFromDate.get().isAfter(patch.getToDate.get())) {
      Left(new IllegalArgumentException("fromDate must be older than toDate"))
    } else {
      Right(patch)
    }
  }

  private def asVacationPatch(entry: (String, JsValue)): Either[IllegalArgumentException, VacationPatch.Builder] = entry match {
    case ("isEnabled", JsBoolean(bool)) => Right(VacationPatch.builder().isEnabled(bool))
    case ("isEnabled", JsNull) => Right(VacationPatch.builder().isEnabled(ValuePatch.remove[java.lang.Boolean]()))
    case ("fromDate", JsString(value)) => parseDate(value).map(date => VacationPatch.builder().fromDate(date))
    case ("fromDate", JsNull) => Right(VacationPatch.builder().fromDate(ValuePatch.remove[ZonedDateTime]()))
    case ("toDate", JsString(value)) => parseDate(value).map(date => VacationPatch.builder().toDate(date))
    case ("toDate", JsNull) => Right(VacationPatch.builder().toDate(ValuePatch.remove[ZonedDateTime]()))
    case ("subject", JsString(value)) => Right(VacationPatch.builder().subject(value))
    case ("subject", JsNull) => Right(VacationPatch.builder().subject(ValuePatch.remove[String]()))
    case ("textBody", JsString(value)) => Right(VacationPatch.builder().textBody(value))
    case ("textBody", JsNull) => Right(VacationPatch.builder().textBody(ValuePatch.remove[String]()))
    case ("htmlBody", JsString(value)) => Right(VacationPatch.builder().htmlBody(value))
    case ("htmlBody", JsNull) => Right(VacationPatch.builder().htmlBody(ValuePatch.remove[String]()))
    case ("id", _) => Left(new IllegalArgumentException("id is server-set thus cannot be changed"))
    case (unknownProperty, _) => Left(new IllegalArgumentException(s"$unknownProperty is an unknown property"))
  }

  private def parseDate(string: String): Either[IllegalArgumentException, ZonedDateTime] =
    Try(ZonedDateTime.parse(string, DateTimeFormatter.ISO_DATE_TIME)) match {
      case Success(value) => Right(value)
      case Failure(e) => Left(new IllegalArgumentException(e))
    }
}

case class VacationResponseSetResponse(accountId: AccountId,
                                       newState: UuidState,
                                       updated: Option[Map[String, VacationResponseUpdateResponse]],
                                       notUpdated: Option[Map[String, VacationResponseSetError]],
                                       notCreated: Option[Map[String, VacationResponseSetError]],
                                       notDestroyed: Option[Map[String, VacationResponseSetError]])

object VacationResponseSetError {
  def invalidArgument(description: Option[SetErrorDescription]) = VacationResponseSetError(invalidArgumentValue, description)
  def serverFail(description: Option[SetErrorDescription]) = VacationResponseSetError(serverFailValue, description)
}

case class VacationResponseUpdateResponse(value: JsObject)

case class VacationResponseSetError(`type`: SetErrorType, description: Option[SetErrorDescription])
