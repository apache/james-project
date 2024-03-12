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
import org.apache.james.jmap.api.change.Limit
import org.apache.james.jmap.core.{AccountId, JmapRfc8621Configuration, Properties, UuidState}
import org.apache.james.jmap.mail.EmailGetRequest.MaxBodyValueBytes
import org.apache.james.jmap.mail.EmailHeaders.SPECIFIC_HEADER_PREFIX
import org.apache.james.jmap.method.{GetRequest, WithAccountId}
import org.apache.james.mailbox.model.MessageId
import org.apache.james.mime4j.stream.Field

import scala.jdk.CollectionConverters._

case class EmailIds(value: List[UnparsedEmailId])

case class FetchAllBodyValues(value: Boolean) extends AnyVal
case class FetchTextBodyValues(value: Boolean) extends AnyVal
case class FetchHTMLBodyValues(value: Boolean) extends AnyVal

object EmailGetRequest {
  type MaxBodyValueBytes = Int Refined NonNegative

  val ZERO: MaxBodyValueBytes = 0

  def readLevel(request: EmailGetRequest): ReadLevel = request.properties
    .getOrElse(Email.defaultProperties)
    .value
    .map(ReadLevel.of)
    .reduceOption(ReadLevel.combine)
    .getOrElse(MetadataReadLevel)
}

object SpecificHeaderRequest {
  def from(property: NonEmptyString): Either[NonEmptyString, SpecificHeaderRequest] = property match {
    case property if property.value.equals(SPECIFIC_HEADER_PREFIX) => Left(property)
    case property if property.startsWith(SPECIFIC_HEADER_PREFIX)  =>
      val headerName = property.substring(SPECIFIC_HEADER_PREFIX.length)
      if (headerName.endsWith(":all")) {
        extractSpecificHeader(property, headerName.substring(0, headerName.length - 4), isAll = true)
      } else {
        extractSpecificHeader(property, headerName, isAll = false)
      }
    case _ => Left(property)
  }

  private def extractSpecificHeader(property: NonEmptyString, headerName: String, isAll: Boolean) =
    if (headerName.contains(":")) {
      val indexOfFirstColon = headerName.indexOf(":")
      val parseOption = headerName.substring(indexOfFirstColon + 1)
      if (ParseOptions.validate(parseOption)) {
        scala.Right(SpecificHeaderRequest(property, headerName.substring(0, indexOfFirstColon), ParseOptions.from(parseOption), isAll))
      } else {
        Left(property)
      }
    } else {
      scala.Right(SpecificHeaderRequest(property, headerName, None, isAll))
    }
}

case class EmailGetRequest(accountId: AccountId,
                           ids: Option[EmailIds],
                           fetchAllBodyValues: Option[FetchAllBodyValues],
                           fetchTextBodyValues: Option[FetchTextBodyValues],
                           fetchHTMLBodyValues: Option[FetchHTMLBodyValues],
                           maxBodyValueBytes: Option[MaxBodyValueBytes],
                           properties: Option[Properties],
                           bodyProperties: Option[Properties]) extends WithAccountId with GetRequest {

  override def idCount: Option[Int] = ids.map(_.value).map(_.size)

  override def validate(configuration: JmapRfc8621Configuration): Either[Exception, EmailGetRequest] =
    if (EmailGetRequest.readLevel(this).equals(FullReadLevel) && ids.exists(_.value.size > configuration.jmapEmailGetFullMaxSize.asLong())) {
      Left(RequestTooLargeException(s"Too many items in an email read at level FULL. " +
        s"Got ${ids.get.value.size} items instead of maximum ${configuration.jmapEmailGetFullMaxSize.asLong()}."))
    } else if (ids.exists(_.value.size > configuration.maxObjectsInGet.value.value)) {
      Left(RequestTooLargeException(s"Too many items in an email read at level ${EmailGetRequest.readLevel(this)}. " +
        s"Got ${ids.get.value.size} items instead of maximum ${configuration.maxObjectsInGet.value}."))
    } else {
      scala.Right(this)
    }
}

case class EmailNotFound(value: Set[UnparsedEmailId]) {
  def merge(other: EmailNotFound): EmailNotFound = EmailNotFound(this.value ++ other.value)
}

case class EmailGetResponse(accountId: AccountId,
                            state: UuidState,
                            list: List[EmailView],
                            notFound: EmailNotFound)

case class SpecificHeaderRequest(property: NonEmptyString, headerName: String, parseOption: Option[ParseOption], isAll: Boolean = false) {
  def retrieveHeader(zoneId: ZoneId, header: org.apache.james.mime4j.dom.Header): (String, Option[EmailHeaderValue]) =
    if (isAll) {
      extractAllHeaders(zoneId, header)
    } else {
      extractLastHeader(zoneId, header)
    }

  private def extractAllHeaders(zoneId: ZoneId, header: org.apache.james.mime4j.dom.Header) = {
    val fields: List[Field] = Option(header.getFields(headerName))
      .map(_.asScala.toList)
      .getOrElse(List())

    val option = parseOption.getOrElse(AsRaw)
    (property.value, Some(AllHeaderValues(fields.map(toHeader(zoneId, option)))))
  }

  private def extractLastHeader(zoneId: ZoneId, header: org.apache.james.mime4j.dom.Header) = {
    val field: Option[Field] = Option(header.getFields(headerName))
      .map(_.asScala)
      .flatMap(fields => fields.reverse.headOption)

    val option = parseOption.getOrElse(AsRaw)
    (property.value, field.map(toHeader(zoneId, option)))
  }

  private def toHeader(zoneId: ZoneId, option: ParseOption): Field => EmailHeaderValue = {
    option match {
      case AsDate => AsDate.extractHeaderValue(_, zoneId)
      case _ => option.extractHeaderValue
    }
  }

  def validate: Either[IllegalArgumentException, SpecificHeaderRequest] = {
    val forbiddenNames = parseOption.map(_.forbiddenHeaderNames).getOrElse(Set())
    if (forbiddenNames.contains(EmailHeaderName(headerName))) {
      Left(new IllegalArgumentException(s"$property is forbidden with $parseOption"))
    } else {
      scala.Right(this)
    }
  }
}

case class EmailChangesRequest(accountId: AccountId,
                               sinceState: UuidState,
                               maxChanges: Option[Limit]) extends WithAccountId


case class EmailChangesResponse(accountId: AccountId,
                                oldState: UuidState,
                                newState: UuidState,
                                hasMoreChanges: HasMoreChanges,
                                created: Set[MessageId],
                                updated: Set[MessageId],
                                destroyed: Set[MessageId])