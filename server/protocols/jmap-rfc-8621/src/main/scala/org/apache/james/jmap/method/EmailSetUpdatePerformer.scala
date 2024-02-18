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

package org.apache.james.jmap.method

import com.google.common.collect.ImmutableList
import jakarta.mail.Flags
import javax.inject.Inject
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.json.EmailSetSerializer
import org.apache.james.jmap.mail.KeywordsFactory.LENIENT_KEYWORDS_FACTORY
import org.apache.james.jmap.mail.{EmailSet, EmailSetRequest, MailboxIds, UnparsedMessageId, ValidatedEmailSetUpdate}
import org.apache.james.jmap.method.EmailSetUpdatePerformer.{EmailUpdateFailure, EmailUpdateResult, EmailUpdateResults, EmailUpdateSuccess}
import org.apache.james.mailbox.MessageManager.FlagsUpdateMode
import org.apache.james.mailbox.exception.{MailboxNotFoundException, OverQuotaException}
import org.apache.james.mailbox.model.{ComposedMessageIdWithMetaData, MailboxId, MessageId, MessageRange}
import org.apache.james.mailbox.{MailboxManager, MailboxSession, MessageIdManager, MessageManager}
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

object EmailSetUpdatePerformer {
  trait EmailUpdateResult
  case class EmailUpdateSuccess(messageId: MessageId) extends EmailUpdateResult
  case class EmailUpdateFailure(unparsedMessageId: UnparsedMessageId, e: Throwable) extends EmailUpdateResult {
    def asMessageSetError: SetError = e match {
      case e: IllegalArgumentException => SetError.invalidPatch(SetErrorDescription(s"Message update is invalid: ${e.getMessage}"))
      case _: MailboxNotFoundException => SetError.notFound(SetErrorDescription(s"Mailbox not found"))
      case e: MessageNotFoundException => SetError.notFound(SetErrorDescription(s"Cannot find message with messageId: ${e.messageId.serialize()}"))
      case e: OverQuotaException => SetError.overQuota(SetErrorDescription(e.getMessage))
      case _ => SetError.serverFail(SetErrorDescription(e.getMessage))
    }
  }
  case class EmailUpdateResults(results: Seq[EmailUpdateResult]) {
    def updated: Option[Map[MessageId, Unit]] =
      Option(results.flatMap{
        case result: EmailUpdateSuccess => Some(result.messageId, ())
        case _ => None
      }.toMap)
        .filter(_.nonEmpty)

    def notUpdated: Option[Map[UnparsedMessageId, SetError]] =
      Option(results.flatMap{
        case failure: EmailUpdateFailure => Some((failure.unparsedMessageId, failure.asMessageSetError))
        case _ => None
      }.toMap)
        .filter(_.nonEmpty)
  }
}

class EmailSetUpdatePerformer @Inject() (serializer: EmailSetSerializer,
                              messageIdManager: MessageIdManager,
                              mailboxManager: MailboxManager,
                              messageIdFactory: MessageId.Factory) {

  def update(emailSetRequest: EmailSetRequest, mailboxSession: MailboxSession): SMono[EmailUpdateResults] = {
    emailSetRequest.update
      .filter(_.nonEmpty)
      .map(update(_, mailboxSession))
      .getOrElse(SMono.just(EmailUpdateResults(Seq())))
  }

  private def update(updates: Map[UnparsedMessageId, JsObject], session: MailboxSession): SMono[EmailUpdateResults] = {
    val validatedUpdates: List[Either[EmailUpdateFailure, (MessageId, ValidatedEmailSetUpdate)]] = updates
      .map({
        case (unparsedMessageId, json) => EmailSet.parse(messageIdFactory)(unparsedMessageId)
          .toEither
          .left.map(e => EmailUpdateFailure(unparsedMessageId, e))
          .flatMap(id => serializer.deserializeEmailSetUpdate(json)
            .asEither.left.map(e => new IllegalArgumentException(e.toString))
            .flatMap(_.validate)
            .fold(e => Left(EmailUpdateFailure(unparsedMessageId, e)),
              emailSetUpdate => Right((id, emailSetUpdate))))
      })
      .toList
    val failures: List[EmailUpdateFailure] = validatedUpdates.flatMap({
      case Left(e) => Some(e)
      case _ => None
    })
    val validUpdates: List[(MessageId, ValidatedEmailSetUpdate)] = validatedUpdates.flatMap({
      case Right(pair) => Some(pair)
      case _ => None
    })

    for {
      updates <- SFlux.fromPublisher(messageIdManager.messagesMetadata(validUpdates.map(_._1).asJavaCollection, session))
        .collectMultimap(metaData => metaData.getComposedMessageId.getMessageId)
        .flatMap(doUpdate(validUpdates, _, session))
    } yield {
      EmailUpdateResults(updates ++ failures)
    }
  }

  private def doUpdate(validUpdates: List[(MessageId, ValidatedEmailSetUpdate)],
                       metaData: Map[MessageId, Iterable[ComposedMessageIdWithMetaData]],
                       session: MailboxSession): SMono[Seq[EmailUpdateResult]] = {
    val sameUpdate: Boolean = validUpdates.map(_._2).distinctBy(_.update).size == 1
    val singleMailbox: Boolean = metaData.values.flatten.map(_.getComposedMessageId.getMailboxId).toSet.size == 1

    if (sameUpdate && singleMailbox && validUpdates.size > 3) {
      val update: ValidatedEmailSetUpdate = validUpdates.map(_._2).headOption.get
      val ranges: List[MessageRange] = asRanges(metaData)
      val mailboxId: MailboxId = metaData.values.flatten.map(_.getComposedMessageId.getMailboxId).headOption.get

      if (update.update.isOnlyFlagAddition) {
        updateFlagsByRange(mailboxId, update.update.keywordsToAdd.get.asFlags, ranges, metaData, FlagsUpdateMode.ADD, session)
      } else if (update.update.isOnlyFlagRemoval) {
        updateFlagsByRange(mailboxId, update.update.keywordsToRemove.get.asFlags, ranges, metaData, FlagsUpdateMode.REMOVE, session)
      } else if (update.update.isOnlyMove) {
        moveByRange(mailboxId, update, ranges, metaData, session)
      } else {
        updateEachMessage(validUpdates, metaData, session)
      }
    } else {
      updateEachMessage(validUpdates, metaData, session)
    }
  }

  private def asRanges(metaData: Map[MessageId, Iterable[ComposedMessageIdWithMetaData]]) =
    MessageRange.toRanges(metaData.values
      .flatten.map(_.getComposedMessageId.getUid)
      .toList.distinct.asJava)
      .asScala.toList

  private def updateFlagsByRange(mailboxId: MailboxId,
                                 flags: Flags,
                                 ranges: List[MessageRange],
                                 metaData: Map[MessageId, Iterable[ComposedMessageIdWithMetaData]],
                                 updateMode: FlagsUpdateMode,
                                 session: MailboxSession): SMono[Seq[EmailUpdateResult]] = {
    val mailboxMono: SMono[MessageManager] = SMono(mailboxManager.getMailboxReactive(mailboxId, session))

    mailboxMono.flatMap(mailbox => updateByRange(ranges, metaData,

      range => SMono(mailbox.setFlagsReactive(flags, updateMode, range, session)).`then`()))
  }

  private def moveByRange(mailboxId: MailboxId,
                          update: ValidatedEmailSetUpdate,
                          ranges: List[MessageRange],
                          metaData: Map[MessageId, Iterable[ComposedMessageIdWithMetaData]],
                          session: MailboxSession): SMono[Seq[EmailUpdateResult]] = {
    val targetId: MailboxId = update.update.mailboxIds.get.value.headOption.get

    updateByRange(ranges, metaData,
      range => SMono(mailboxManager.moveMessagesReactive(range, mailboxId, targetId, session)).`then`())
  }

  private def updateByRange(ranges: List[MessageRange],
                            metaData: Map[MessageId, Iterable[ComposedMessageIdWithMetaData]],
                            operation: MessageRange => SMono[Unit]): SMono[Seq[EmailUpdateResult]] =
    SFlux.fromIterable(ranges)
      .concatMap(range => {
        val messageIds = metaData.filter(entry => entry._2.exists(composedId => range.includes(composedId.getComposedMessageId.getUid)))
          .keys
          .toSeq
        operation.apply(range)
          .`then`(SMono.just(messageIds.map(EmailUpdateSuccess)))
          .onErrorResume(e => SMono.just(messageIds.map(id => EmailUpdateFailure(EmailSet.asUnparsed(id), e))))
      })
      .reduce(Seq[EmailUpdateResult]())( _ ++ _)

  private def updateEachMessage(validUpdates: List[(MessageId, ValidatedEmailSetUpdate)],
                                metaData: Map[MessageId, Iterable[ComposedMessageIdWithMetaData]],
                                session: MailboxSession): SMono[Seq[EmailUpdateResult]] =
    SFlux.fromIterable(validUpdates)
      .concatMap[EmailUpdateResult]({
        case (messageId, updatePatch) =>
          updateSingleMessage(messageId, updatePatch, metaData.get(messageId).toList.flatten, session)
      })
      .collectSeq()

  private def updateSingleMessage(messageId: MessageId, update: ValidatedEmailSetUpdate, storedMetaData: List[ComposedMessageIdWithMetaData], session: MailboxSession): SMono[EmailUpdateResult] = {
    val mailboxIds: MailboxIds = MailboxIds(storedMetaData.map(metaData => metaData.getComposedMessageId.getMailboxId))
    val originFlags: Flags = storedMetaData
      .foldLeft[Flags](new Flags())((flags: Flags, m: ComposedMessageIdWithMetaData) => {
        flags.add(m.getFlags)
        flags
      })

    if (mailboxIds.value.isEmpty) {
      SMono.just[EmailUpdateResult](EmailUpdateFailure(EmailSet.asUnparsed(messageId), MessageNotFoundException(messageId)))
    } else {
      updateFlags(messageId, update, mailboxIds, originFlags, session)
        .flatMap {
          case failure: EmailUpdateFailure => SMono.just[EmailUpdateResult](failure)
          case _: EmailUpdateSuccess => updateMailboxIds(messageId, update, mailboxIds, session)
        }
        .onErrorResume(e => SMono.just[EmailUpdateResult](EmailUpdateFailure(EmailSet.asUnparsed(messageId), e)))
        .switchIfEmpty(SMono.just[EmailUpdateResult](EmailUpdateSuccess(messageId)))
    }
  }

  private def updateMailboxIds(messageId: MessageId, update: ValidatedEmailSetUpdate, mailboxIds: MailboxIds, session: MailboxSession): SMono[EmailUpdateResult] = {
    val targetIds = update.mailboxIdsTransformation.apply(mailboxIds)
    if (targetIds.equals(mailboxIds)) {
      SMono.just[EmailUpdateResult](EmailUpdateSuccess(messageId))
    } else {
      SMono(messageIdManager.setInMailboxesReactive(messageId, targetIds.value.asJava, session))
        .`then`(SMono.just[EmailUpdateResult](EmailUpdateSuccess(messageId)))
        .onErrorResume(e => SMono.just[EmailUpdateResult](EmailUpdateFailure(EmailSet.asUnparsed(messageId), e)))
        .switchIfEmpty(SMono.just[EmailUpdateResult](EmailUpdateSuccess(messageId)))
    }
  }

  private def updateFlags(messageId: MessageId, update: ValidatedEmailSetUpdate, mailboxIds: MailboxIds, originalFlags: Flags, session: MailboxSession): SMono[EmailUpdateResult] = {
    val newFlags = update.keywordsTransformation
      .apply(LENIENT_KEYWORDS_FACTORY.fromFlags(originalFlags).get)
      .asFlagsWithRecentAndDeletedFrom(originalFlags)

    if (newFlags.equals(originalFlags)) {
      SMono.just[EmailUpdateResult](EmailUpdateSuccess(messageId))
    } else {
      SMono(messageIdManager.setFlagsReactive(newFlags, FlagsUpdateMode.REPLACE, messageId, ImmutableList.copyOf(mailboxIds.value.asJavaCollection), session))
        .`then`(SMono.just[EmailUpdateResult](EmailUpdateSuccess(messageId)))
    }
  }
}
