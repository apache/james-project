/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/

package org.apache.james.jmap.mail

import java.nio.charset.StandardCharsets.US_ASCII
import java.time.ZoneId
import java.util.Date

import org.apache.commons.lang3.StringUtils
import org.apache.james.jmap.api.model.EmailAddress
import org.apache.james.jmap.core.UTCDate
import org.apache.james.mime4j.codec.{DecodeMonitor, DecoderUtil}
import org.apache.james.mime4j.dom.address.{AddressList, Group, Address => Mime4jAddress, Mailbox => Mime4jMailbox}
import org.apache.james.mime4j.field.{AddressListFieldImpl, ContentLocationFieldImpl, DateTimeFieldImpl, Fields}
import org.apache.james.mime4j.stream.{Field, RawField}
import org.apache.james.mime4j.util.MimeUtil

import scala.jdk.CollectionConverters._

object EmailHeader {
  def apply(field: Field): EmailHeader = EmailHeader(EmailHeaderName(field.getName), RawHeaderValue.from(field))
}

object RawHeaderValue {
  def from(field: Field): RawHeaderValue = RawHeaderValue(new String(field.getRaw.toByteArray, US_ASCII).substring(field.getName.length + 1))
}

object TextHeaderValue {
  def from(field: Field): TextHeaderValue = TextHeaderValue(MimeUtil.unfold(DecoderUtil.decodeEncodedWords(field.getBody, DecodeMonitor.SILENT)).stripLeading())
}

object AddressesHeaderValue {
  def from(field: Field): AddressesHeaderValue = AddressesHeaderValue(EmailAddress.from(AddressListFieldImpl.PARSER.parse(field, DecodeMonitor.SILENT).getAddressList))
}

object GroupedAddressesHeaderValue {
  def from(field: Field): GroupedAddressesHeaderValue = {
    val addresses: List[Mime4jAddress] =
      Option(AddressListFieldImpl.PARSER.parse(field, DecodeMonitor.SILENT).getAddressList)
      .getOrElse(new AddressList())
      .asScala
      .toList

    if (addresses.isEmpty) {
      GroupedAddressesHeaderValue(List())
    } else {
      val groups: List[EmailAddressGroup] = addresses
        .flatMap({
          case group: Group => Some(group)
          case _ => None
        })
        .map(group => EmailAddressGroup(Some(GroupName(group.getName)), EmailAddress.from(group.getMailboxes)))

      val addressesWithoutGroup: List[EmailAddress] = addresses
        .flatMap({
          case mailbox: Mime4jMailbox => Some(mailbox)
          case _ => None
        })
        .flatMap(EmailAddress.from(_).toOption)

      GroupedAddressesHeaderValue(List(EmailAddressGroup(None, addressesWithoutGroup)) ++ groups)
    }
  }
}

object MessageIdsHeaderValue {
  def from(field: Field): MessageIdsHeaderValue = {
    val messageIds: List[HeaderMessageId] = MimeUtil.unfold(StringUtils.normalizeSpace(field.getBody))
      .split(' ')
      .flatMap(body => {
        if(body.startsWith("<") && body.endsWith(">") && body.contains("@")) {
          scala.Right(HeaderMessageId.from(body))
        } else {
          Left((): Unit)
        }
      }.toOption)
      .toList

      MessageIdsHeaderValue(Option(messageIds).filter(_.nonEmpty))
  }
}

object DateHeaderValue {
  def from(field: Field, zoneId: ZoneId): DateHeaderValue =
    Option(DateTimeFieldImpl.PARSER.parse(field, DecodeMonitor.SILENT).getDate)
      .map(date => DateHeaderValue(Some(UTCDate.from(date, zoneId))))
      .getOrElse(DateHeaderValue(None))
}

object URLsHeaderValue {
  def from(field: Field): URLsHeaderValue = {
    val url: Option[List[HeaderURL]] = Option(ContentLocationFieldImpl.PARSER.parse(field, DecodeMonitor.SILENT).getLocation)
      .map(urls => urls.split(',')
        .toList
        .flatMap(url => {
          if(url.startsWith("<") && url.endsWith(">")) {
            scala.Right(HeaderURL.from(url))
          } else {
            Left((): Unit)
          }
        }.toOption))

      URLsHeaderValue(url.filter(_.nonEmpty))
  }
}

object EmailHeaderName {
  val DATE: EmailHeaderName = EmailHeaderName("Date")
  val TO: EmailHeaderName = EmailHeaderName("To")
  val FROM: EmailHeaderName = EmailHeaderName("From")
  val CC: EmailHeaderName = EmailHeaderName("Cc")
  val BCC: EmailHeaderName = EmailHeaderName("Bcc")
  val SENDER: EmailHeaderName = EmailHeaderName("Sender")
  val REPLY_TO: EmailHeaderName = EmailHeaderName("Reply-To")
  val REFERENCES: EmailHeaderName = EmailHeaderName("References")
  val MESSAGE_ID: EmailHeaderName = EmailHeaderName("Message-Id")
  val IN_REPLY_TO: EmailHeaderName = EmailHeaderName("In-Reply-To")

  val MESSAGE_ID_NAMES = Set(REFERENCES, MESSAGE_ID, IN_REPLY_TO)
  val ADDRESSES_NAMES = Set(SENDER, FROM, TO, CC, BCC, REPLY_TO)
}

case class EmailHeaderName(value: String) extends AnyVal

sealed trait EmailHeaderValue {
  def asFields(name: EmailHeaderName): List[Field]
}
case class AllHeaderValues(values: List[EmailHeaderValue]) extends EmailHeaderValue {
  override def asFields(name: EmailHeaderName): List[Field] = values.flatMap(_.asFields(name))
}
case class RawHeaderValue(value: String) extends EmailHeaderValue {
  override def asFields(name: EmailHeaderName): List[Field] = List(new RawField(name.value, value.substring(1)))
}
case class TextHeaderValue(value: String) extends EmailHeaderValue {
  override def asFields(name: EmailHeaderName): List[Field] = List(new RawField(name.value, value))
}
case class AddressesHeaderValue(value: List[EmailAddress]) extends EmailHeaderValue {
  def asMime4JMailboxList: Option[List[Mime4jMailbox]] = Some(value.map(_.asMime4JMailbox)).filter(_.nonEmpty)

  override def asFields(name: EmailHeaderName): List[Field] = List(Fields.addressList(name.value, asMime4JMailboxList.getOrElse(List()).asJava))
}
case class GroupedAddressesHeaderValue(value: List[EmailAddressGroup]) extends EmailHeaderValue {
  override def asFields(name: EmailHeaderName): List[Field] = List(Fields.addressList(name.value, value.flatMap(_.asAddress).asJava))
}
case class MessageIdsHeaderValue(value: Option[List[HeaderMessageId]]) extends EmailHeaderValue {
  def asString: Option[String] = value.map(messageIds => messageIds
    .map(_.value)
    .map(messageId => s"<${messageId}>")
    .mkString(" "))

  override def asFields(name: EmailHeaderName): List[Field] = List(new RawField(name.value, asString.getOrElse("")))
}
case class DateHeaderValue(value: Option[UTCDate]) extends EmailHeaderValue {
  override def asFields(name: EmailHeaderName): List[Field] = List(Fields.date(name.value,
    value.map(_.asUTC.toInstant)
      .map(Date.from)
      .orNull))
}
case class URLsHeaderValue(value: Option[List[HeaderURL]]) extends EmailHeaderValue {
  override def asFields(name: EmailHeaderName): List[Field] =  List(new RawField(name.value,
   value
      .map(list => list.map("<" + _.value + ">").mkString(", "))
      .getOrElse("")))
}

case class EmailHeader(name: EmailHeaderName, value: EmailHeaderValue) {
  def asFields: List[Field] = value.asFields(name)
}
