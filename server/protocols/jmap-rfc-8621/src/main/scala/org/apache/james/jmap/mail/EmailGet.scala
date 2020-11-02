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

package org.apache.james.jmap.mail

import java.time.ZoneId

import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.NonNegative
import eu.timepit.refined.types.string.NonEmptyString
import org.apache.james.jmap.core.State.State
import org.apache.james.jmap.core.{AccountId, Properties}
import org.apache.james.jmap.mail.Email.UnparsedEmailId
import org.apache.james.jmap.mail.EmailGetRequest.MaxBodyValueBytes
import org.apache.james.jmap.mail.EmailHeaders.SPECIFIC_HEADER_PREFIX
import org.apache.james.jmap.method.WithAccountId
import org.apache.james.mime4j.dom.Message
import org.apache.james.mime4j.stream.Field

import scala.jdk.CollectionConverters._

case class EmailIds(value: List[UnparsedEmailId])

case class FetchAllBodyValues(value: Boolean) extends AnyVal
case class FetchTextBodyValues(value: Boolean) extends AnyVal
case class FetchHTMLBodyValues(value: Boolean) extends AnyVal

object EmailGetRequest {
  type MaxBodyValueBytes = Int Refined NonNegative

  val ZERO: MaxBodyValueBytes = 0
}

object SpecificHeaderRequest {
  def from(property: NonEmptyString): Either[NonEmptyString, SpecificHeaderRequest] = property match {
    case property if property.value.equals(SPECIFIC_HEADER_PREFIX) => Left(property)
    case property if property.startsWith(SPECIFIC_HEADER_PREFIX)  =>
      val headerName = property.substring(SPECIFIC_HEADER_PREFIX.length)
      if (headerName.contains(":")) {
        val indexOfFirstColon = headerName.indexOf(":")
        val parseOption = headerName.substring(indexOfFirstColon + 1)
        if (ParseOptions.validate(parseOption)) {
          scala.Right(SpecificHeaderRequest(property, headerName.substring(0, indexOfFirstColon), ParseOptions.from(parseOption)))
        } else {
          Left(property)
        }
      } else {
        scala.Right(SpecificHeaderRequest(property, headerName, None))
      }
    case _ => Left(property)
  }
}

case class EmailGetRequest(accountId: AccountId,
                           ids: Option[EmailIds],
                           fetchAllBodyValues: Option[FetchAllBodyValues],
                           fetchTextBodyValues: Option[FetchTextBodyValues],
                           fetchHTMLBodyValues: Option[FetchHTMLBodyValues],
                           maxBodyValueBytes: Option[MaxBodyValueBytes],
                           properties: Option[Properties],
                           bodyProperties: Option[Properties]) extends WithAccountId

case class EmailNotFound(value: Set[UnparsedEmailId]) {
  def merge(other: EmailNotFound): EmailNotFound = EmailNotFound(this.value ++ other.value)
}

case class EmailGetResponse(accountId: AccountId,
                            state: State,
                            list: List[EmailView],
                            notFound: EmailNotFound)

case class SpecificHeaderRequest(headerName: NonEmptyString, property: String, parseOption: Option[ParseOption]) {
  def retrieveHeader(zoneId: ZoneId, message: Message): (String, Option[EmailHeaderValue]) = {
    val field: Option[Field] = Option(message.getHeader.getFields(property))
      .map(_.asScala)
      .flatMap(fields => fields.reverse.headOption)

    (headerName, field.map({
      val option = parseOption.getOrElse(AsRaw)
        option match {
          case AsDate => AsDate.extractHeaderValue(_, zoneId)
          case _ => option.extractHeaderValue
        }
    }))
  }
}
