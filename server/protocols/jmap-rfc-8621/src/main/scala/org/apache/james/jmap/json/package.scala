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

package org.apache.james.jmap

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import eu.timepit.refined.api.{RefType, Validate}
import org.apache.james.core.MailAddress
import org.apache.james.jmap.api.change.Limit
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{AccountId, Properties, SetError, UTCDate, UuidState}
import org.apache.james.jmap.mail.HasMoreChanges
import play.api.libs.json._

import scala.annotation.nowarn
import scala.util.{Failure, Success, Try}

package object json {
  implicit val jsObjectReads: Reads[JsObject] = {
    case o: JsObject => JsSuccess(o)
    case _ => JsError("Expecting a JsObject as a creation entry")
  }

  val mapMarkerReads: Reads[Boolean] = {
    case JsBoolean(true) => JsSuccess(true)
    case JsBoolean(false) => JsError("map marker value can only be true")
    case _ => JsError("Expecting mailboxId value to be a boolean")
  }

  @nowarn
  def mapWrites[K, V](keyWriter: K => String, valueWriter: Writes[V]): OWrites[Map[K, V]] =
    (ids: Map[K, V]) => {
      ids.foldLeft(JsObject.empty)((jsObject, kv) => {
        val (key: K, value: V) = kv
        jsObject.+(keyWriter.apply(key), valueWriter.writes(value))
      })
    }

  // code copied from https://github.com/avdv/play-json-refined/blob/master/src/main/scala/de.cbley.refined.play.json/package.scala
  implicit def writeRefined[T, P, F[_, _]](
                                            implicit writesT: Writes[T],
                                            reftype: RefType[F]
                                          ): Writes[F[T, P]] = Writes(value => writesT.writes(reftype.unwrap(value)))

  // code copied from https://github.com/avdv/play-json-refined/blob/master/src/main/scala/de.cbley.refined.play.json/package.scala
  implicit def readRefined[T, P, F[_, _]](
                                           implicit readsT: Reads[T],
                                           reftype: RefType[F],
                                           validate: Validate[T, P]
                                         ): Reads[F[T, P]] =
    Reads(jsValue =>
      readsT.reads(jsValue).flatMap { valueT =>
        reftype.refine[P](valueT) match {
          case Right(valueP) => JsSuccess(valueP)
          case Left(error)   => JsError(error)
        }
      })

  private[json] implicit val UTCDateReads: Reads[UTCDate] = {
    case JsString(value) =>
      Try(UTCDate(ZonedDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME))) match {
        case Success(value) => JsSuccess(value)
        case Failure(e) => JsError(e.getMessage)
      }
    case _ => JsError("Expecting js string to represent UTC Date")
  }

  private[json] implicit val stateReads: Reads[UuidState] = Json.valueReads[UuidState]
  private[json] implicit val accountIdWrites: Format[AccountId] = Json.valueFormat[AccountId]
  private[json] implicit val propertiesFormat: Format[Properties] = Json.valueFormat[Properties]
  private[json] implicit val setErrorDescriptionWrites: Writes[SetErrorDescription] = Json.valueWrites[SetErrorDescription]
  private[json] implicit val setErrorWrites: Writes[SetError] = Json.writes[SetError]
  private[json] implicit val mailAddressWrites: Writes[MailAddress] = mailAddress => JsString(mailAddress.asString)
  private[json] implicit val mailAddressReads: Reads[MailAddress] = {
    case JsString(value) => Try(new MailAddress(value))
      .fold(e => JsError(e.getMessage),
        mailAddress => JsSuccess(mailAddress))
    case _ => JsError("mail address needs to be represented with a JsString")
  }
  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")
  private[json] implicit val utcDateWrites: Writes[UTCDate] = utcDate => JsString(utcDate.asUTC.format(dateTimeFormatter))
  private[json] implicit val hasMoreChangesWrites: Writes[HasMoreChanges] = Json.valueWrites[HasMoreChanges]
  private[json] implicit val limitReads: Reads[Limit] = {
    case JsNumber(underlying) if underlying > 0 => JsSuccess(Limit.of(underlying.intValue))
    case JsNumber(underlying) if underlying <= 0 => JsError("Expecting a positive integer as Limit")
    case _ => JsError("Expecting a number as Limit")
  }
}
