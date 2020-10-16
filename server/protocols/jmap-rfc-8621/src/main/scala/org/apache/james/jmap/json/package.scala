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
import org.apache.james.jmap.model.Id.Id
import org.apache.james.jmap.model.SetError.SetErrorDescription
import org.apache.james.jmap.model.{AccountId, Properties, SetError, UTCDate}
import play.api.libs.json._

import scala.util.{Failure, Success, Try}

package object json {
  def mapWrites[K, V](keyWriter: K => String, valueWriter: Writes[V]): Writes[Map[K, V]] =
    (ids: Map[K, V]) => {
      ids.foldLeft(JsObject.empty)((jsObject, kv) => {
        val (key: K, value: V) = kv
        jsObject.+(keyWriter.apply(key), valueWriter.writes(value))
      })
    }

  def readMapEntry[K, V](keyValidator: String => Either[String, K], valueReads: Reads[V]): Reads[Map[K, V]] =
    _.validate[Map[String, JsValue]]
      .flatMap(mapWithStringKey =>{
        val firstAcc = scala.util.Right[JsError, Map[K, V]](Map.empty)
        mapWithStringKey
          .foldLeft[Either[JsError, Map[K, V]]](firstAcc)((acc: Either[JsError, Map[K, V]], keyValue) => {
            acc match {
              case error@Left(_) => error
              case scala.util.Right(validatedAcc) =>
                val refinedKey: Either[String, K] = keyValidator.apply(keyValue._1)
                refinedKey match {
                  case Left(error) => Left(JsError(error))
                  case scala.util.Right(unparsedK) =>
                    val transformValue: JsResult[V] = valueReads.reads(keyValue._2)
                    transformValue.fold(
                      error => Left(JsError(error)),
                      v => scala.util.Right(validatedAcc + (unparsedK -> v)))
                }
            }
          }) match {
          case Left(jsError) => jsError
          case scala.util.Right(value) => JsSuccess(value)
        }
      })

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

  implicit def idMapWrite[Any](implicit vr: Writes[Any]): Writes[Map[Id, Any]] =
    (m: Map[Id, Any]) => {
      JsObject(m.map { case (k, v) => (k.value, vr.writes(v)) }.toSeq)
    }

  private[json] implicit val UTCDateReads: Reads[UTCDate] = {
    case JsString(value) =>
      Try(UTCDate(ZonedDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME))) match {
        case Success(value) => JsSuccess(value)
        case Failure(e) => JsError(e.getMessage)
      }
    case _ => JsError("Expecting js string to represent UTC Date")
  }
  private[json] implicit val accountIdWrites: Format[AccountId] = Json.valueFormat[AccountId]
  private[json] implicit val propertiesFormat: Format[Properties] = Json.valueFormat[Properties]
  private[json] implicit val setErrorDescriptionWrites: Writes[SetErrorDescription] = Json.valueWrites[SetErrorDescription]
  private[json] implicit val setErrorWrites: Writes[SetError] = Json.writes[SetError]
  private[json] implicit val utcDateWrites: Writes[UTCDate] =
    utcDate => JsString(utcDate.asUTC.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")))
}
