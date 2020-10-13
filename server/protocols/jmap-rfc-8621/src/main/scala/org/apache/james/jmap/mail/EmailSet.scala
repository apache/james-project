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

import eu.timepit.refined
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import org.apache.james.jmap.mail.EmailSet.UnparsedMessageId
import org.apache.james.jmap.method.WithAccountId
import org.apache.james.jmap.model.KeywordsFactory.STRICT_KEYWORDS_FACTORY
import org.apache.james.jmap.model.State.State
import org.apache.james.jmap.model.{AccountId, Keywords, SetError}
import org.apache.james.mailbox.model.MessageId
import play.api.libs.json.JsObject

import scala.util.{Failure, Right, Success, Try}

object EmailSet {
  type UnparsedMessageIdConstraint = NonEmpty
  type UnparsedMessageId = String Refined UnparsedMessageIdConstraint

  def asUnparsed(messageId: MessageId): UnparsedMessageId = refined.refineV[UnparsedMessageIdConstraint](messageId.serialize()) match {
    case Left(e) => throw new IllegalArgumentException(e)
    case scala.Right(value) => value
  }

  def parse(messageIdFactory: MessageId.Factory)(unparsed: UnparsedMessageId): Try[MessageId] =
    Try(messageIdFactory.fromString(unparsed.value))
}

case class DestroyIds(value: Seq[UnparsedMessageId])

case class EmailSetRequest(accountId: AccountId,
                           update: Option[Map[UnparsedMessageId, JsObject]],
                           destroy: Option[DestroyIds]) extends WithAccountId

case class EmailSetResponse(accountId: AccountId,
                            newState: State,
                            updated: Option[Map[MessageId, Unit]],
                            notUpdated: Option[Map[UnparsedMessageId, SetError]],
                            destroyed: Option[DestroyIds],
                            notDestroyed: Option[Map[UnparsedMessageId, SetError]])

case class EmailSetUpdate(keywords: Option[Keywords],
                          mailboxIds: Option[MailboxIds],
                          mailboxIdsToAdd: Option[MailboxIds],
                          mailboxIdsToRemove: Option[MailboxIds]) {
  def validate: Either[IllegalArgumentException, ValidatedEmailSetUpdate] = if (mailboxIds.isDefined && (mailboxIdsToAdd.isDefined || mailboxIdsToRemove.isDefined)) {
    Left(new IllegalArgumentException("Partial update and reset specified"))
  } else {
    val identity: Function[MailboxIds, MailboxIds] = ids => ids
    val mailboxIdsAddition: Function[MailboxIds, MailboxIds] = mailboxIdsToAdd
      .map(toBeAdded => (ids: MailboxIds) => ids ++ toBeAdded)
      .getOrElse(identity)
    val mailboxIdsRemoval: Function[MailboxIds, MailboxIds] = mailboxIdsToRemove
      .map(toBeRemoved => (ids: MailboxIds) => ids -- toBeRemoved)
      .getOrElse(identity)
    val mailboxIdsReset: Function[MailboxIds, MailboxIds] = mailboxIds
      .map(toReset => (_: MailboxIds) => toReset)
      .getOrElse(identity)
    val mailboxIdsTransformation: Function[MailboxIds, MailboxIds] = mailboxIdsAddition
      .compose(mailboxIdsRemoval)
      .compose(mailboxIdsReset)
    Right(mailboxIdsTransformation)
      .flatMap(mailboxIdsTransformation => validateKeywords
        .map(validatedKeywords => ValidatedEmailSetUpdate(validatedKeywords, mailboxIdsTransformation)))
  }

  private def validateKeywords: Either[IllegalArgumentException, Option[Keywords]] = {
    keywords.map(_.getKeywords)
      .map(STRICT_KEYWORDS_FACTORY.fromSet)
      .map {
        case Success(validatedKeywords: Keywords) => Right(Some(validatedKeywords))
        case Failure(throwable: IllegalArgumentException) => Left(throwable)
      }
      .getOrElse(Right(None))
  }
}

case class ValidatedEmailSetUpdate private (keywords: Option[Keywords],
                                            mailboxIdsTransformation: Function[MailboxIds, MailboxIds])

class EmailUpdateValidationException() extends IllegalArgumentException
case class InvalidEmailPropertyException(property: String, cause: String) extends EmailUpdateValidationException
case class InvalidEmailUpdateException(property: String, cause: String) extends EmailUpdateValidationException

