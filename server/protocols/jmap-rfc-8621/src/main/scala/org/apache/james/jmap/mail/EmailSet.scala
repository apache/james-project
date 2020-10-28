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

import java.nio.charset.StandardCharsets
import java.util.Date

import eu.timepit.refined
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import org.apache.james.jmap.mail.EmailSet.{EmailCreationId, UnparsedMessageId}
import org.apache.james.jmap.method.WithAccountId
import org.apache.james.jmap.model.Id.Id
import org.apache.james.jmap.model.State.State
import org.apache.james.jmap.model.{AccountId, Keywords, SetError, UTCDate}
import org.apache.james.mailbox.model.MessageId
import org.apache.james.mime4j.dom.Message
import org.apache.james.mime4j.dom.field.FieldName
import org.apache.james.mime4j.field.Fields
import org.apache.james.mime4j.stream.RawField
import play.api.libs.json.JsObject

import scala.jdk.CollectionConverters._
import scala.util.{Right, Try}

object EmailSet {
  type EmailCreationId = Id
  type UnparsedMessageIdConstraint = NonEmpty
  type UnparsedMessageId = String Refined UnparsedMessageIdConstraint

  def asUnparsed(messageId: MessageId): UnparsedMessageId = refined.refineV[UnparsedMessageIdConstraint](messageId.serialize()) match {
    case Left(e) => throw new IllegalArgumentException(e)
    case scala.Right(value) => value
  }

  def parse(messageIdFactory: MessageId.Factory)(unparsed: UnparsedMessageId): Try[MessageId] =
    Try(messageIdFactory.fromString(unparsed.value))
}

case class ClientPartId(id: Id)

case class ClientHtmlBody(partId: ClientPartId, `type`: Type)

case class ClientEmailBodyValue(value: String,
                                isEncodingProblem: Option[IsEncodingProblem],
                                isTruncated: Option[IsTruncated])

case class EmailCreationRequest(mailboxIds: MailboxIds,
                                messageId: Option[MessageIdsHeaderValue],
                                references: Option[MessageIdsHeaderValue],
                                inReplyTo: Option[MessageIdsHeaderValue],
                                from: Option[AddressesHeaderValue],
                                to: Option[AddressesHeaderValue],
                                cc: Option[AddressesHeaderValue],
                                bcc: Option[AddressesHeaderValue],
                                sender: Option[AddressesHeaderValue],
                                replyTo: Option[AddressesHeaderValue],
                                subject: Option[Subject],
                                sentAt: Option[UTCDate],
                                keywords: Option[Keywords],
                                receivedAt: Option[UTCDate],
                                htmlBody: Option[List[ClientHtmlBody]],
                                bodyValues: Option[Map[ClientPartId, ClientEmailBodyValue]]) {
  def toMime4JMessage: Either[IllegalArgumentException, Message] =
    validateHtmlBody.map(maybeHtmlBody => {
      val builder = Message.Builder.of
      references.flatMap(_.asString).map(new RawField("References", _)).foreach(builder.setField)
      inReplyTo.flatMap(_.asString).map(new RawField("In-Reply-To", _)).foreach(builder.setField)
      messageId.flatMap(_.asString).map(new RawField(FieldName.MESSAGE_ID, _)).foreach(builder.setField)
      subject.foreach(value => builder.setSubject(value.value))
      from.flatMap(_.asMime4JMailboxList).map(_.asJava).foreach(builder.setFrom)
      to.flatMap(_.asMime4JMailboxList).map(_.asJava).foreach(builder.setTo)
      cc.flatMap(_.asMime4JMailboxList).map(_.asJava).foreach(builder.setCc)
      bcc.flatMap(_.asMime4JMailboxList).map(_.asJava).foreach(builder.setBcc)
      sender.flatMap(_.asMime4JMailboxList).map(_.asJava).map(Fields.addressList(FieldName.SENDER, _)).foreach(builder.setField)
      replyTo.flatMap(_.asMime4JMailboxList).map(_.asJava).foreach(builder.setReplyTo)
      sentAt.map(_.asUTC).map(_.toInstant).map(Date.from).foreach(builder.setDate)
      builder.setBody(maybeHtmlBody.getOrElse(""), "html", StandardCharsets.UTF_8)
      builder.build()
    })

  def validateHtmlBody: Either[IllegalArgumentException, Option[String]] = htmlBody match {
    case None => Right(None)
    case Some(html :: Nil) => bodyValues.getOrElse(Map()).get(html.partId)
      .map(part => Right(Some(part.value)))
      .getOrElse(Left(new IllegalArgumentException("todo")))
  }
}

case class DestroyIds(value: Seq[UnparsedMessageId])

case class EmailSetRequest(accountId: AccountId,
                           create: Option[Map[EmailCreationId, JsObject]],
                           update: Option[Map[UnparsedMessageId, JsObject]],
                           destroy: Option[DestroyIds]) extends WithAccountId

case class EmailSetResponse(accountId: AccountId,
                            newState: State,
                            created: Option[Map[EmailCreationId, EmailCreationResponse]],
                            notCreated: Option[Map[EmailCreationId, SetError]],
                            updated: Option[Map[MessageId, Unit]],
                            notUpdated: Option[Map[UnparsedMessageId, SetError]],
                            destroyed: Option[DestroyIds],
                            notDestroyed: Option[Map[UnparsedMessageId, SetError]])

case class EmailSetUpdate(keywords: Option[Keywords],
                          keywordsToAdd: Option[Keywords],
                          keywordsToRemove: Option[Keywords],
                          mailboxIds: Option[MailboxIds],
                          mailboxIdsToAdd: Option[MailboxIds],
                          mailboxIdsToRemove: Option[MailboxIds]) {
  def validate: Either[IllegalArgumentException, ValidatedEmailSetUpdate] = {
    if (mailboxIds.isDefined && (mailboxIdsToAdd.isDefined || mailboxIdsToRemove.isDefined)) {
      Left(new IllegalArgumentException("Partial update and reset specified for mailboxIds"))
    } else if (keywords.isDefined && (keywordsToAdd.isDefined || keywordsToRemove.isDefined)) {
      Left(new IllegalArgumentException("Partial update and reset specified for keywords"))
    } else  {
      val mailboxIdsIdentity: Function[MailboxIds, MailboxIds] = ids => ids
      val mailboxIdsAddition: Function[MailboxIds, MailboxIds] = mailboxIdsToAdd
        .map(toBeAdded => (ids: MailboxIds) => ids ++ toBeAdded)
        .getOrElse(mailboxIdsIdentity)
      val mailboxIdsRemoval: Function[MailboxIds, MailboxIds] = mailboxIdsToRemove
        .map(toBeRemoved => (ids: MailboxIds) => ids -- toBeRemoved)
        .getOrElse(mailboxIdsIdentity)
      val mailboxIdsReset: Function[MailboxIds, MailboxIds] = mailboxIds
        .map(toReset => (_: MailboxIds) => toReset)
        .getOrElse(mailboxIdsIdentity)
      val mailboxIdsTransformation: Function[MailboxIds, MailboxIds] = mailboxIdsAddition
        .compose(mailboxIdsRemoval)
        .compose(mailboxIdsReset)

      val keywordsIdentity: Function[Keywords, Keywords] = keywords => keywords
      val keywordsAddition: Function[Keywords, Keywords] = keywordsToAdd
        .map(toBeAdded => (keywords: Keywords) => keywords ++ toBeAdded)
        .getOrElse(keywordsIdentity)
      val keywordsRemoval: Function[Keywords, Keywords] = keywordsToRemove
        .map(toBeRemoved => (keywords: Keywords) => keywords -- toBeRemoved)
        .getOrElse(keywordsIdentity)
      val keywordsReset: Function[Keywords, Keywords] = keywords
        .map(toReset => (_: Keywords) => toReset)
        .getOrElse(keywordsIdentity)
      val keywordsTransformation: Function[Keywords, Keywords] = keywordsAddition
        .compose(keywordsRemoval)
        .compose(keywordsReset)

      Right(ValidatedEmailSetUpdate(keywordsTransformation, mailboxIdsTransformation, this))
    }
  }

  def isOnlyMove: Boolean = mailboxIds.isDefined && mailboxIds.get.value.size == 1 &&
    keywords.isEmpty && keywordsToAdd.isEmpty && keywordsToRemove.isEmpty

  def isOnlyFlagAddition: Boolean = keywordsToAdd.isDefined && keywordsToRemove.isEmpty && mailboxIds.isEmpty &&
    mailboxIdsToAdd.isEmpty && mailboxIdsToRemove.isEmpty

  def isOnlyFlagRemoval: Boolean = keywordsToRemove.isDefined && keywordsToAdd.isEmpty && mailboxIds.isEmpty &&
    mailboxIdsToAdd.isEmpty && mailboxIdsToRemove.isEmpty
}

case class ValidatedEmailSetUpdate private (keywordsTransformation: Function[Keywords, Keywords],
                                            mailboxIdsTransformation: Function[MailboxIds, MailboxIds],
                                            update: EmailSetUpdate)

class EmailUpdateValidationException() extends IllegalArgumentException
case class InvalidEmailPropertyException(property: String, cause: String) extends EmailUpdateValidationException
case class InvalidEmailUpdateException(property: String, cause: String) extends EmailUpdateValidationException

case class EmailCreationResponse(id: MessageId)

