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

import com.google.common.collect.ImmutableMap
import javax.inject.Inject
import org.apache.commons.lang3.StringUtils
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.mail.{DestroyIds, EmailSet, EmailSetRequest, UnparsedMessageId}
import org.apache.james.jmap.method.EmailSetDeletePerformer.{DestroyFailure, DestroyResult, DestroyResults}
import org.apache.james.mailbox.model.{DeleteResult, MessageId}
import org.apache.james.mailbox.{MailboxSession, MessageIdManager}
import org.apache.james.util.AuditTrail
import reactor.core.scala.publisher.SMono

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

object EmailSetDeletePerformer {
  case class DestroyResults(results: Seq[DestroyResult]) {
    def destroyed: Option[DestroyIds] =
      Option(results.flatMap{
        case result: DestroySuccess => Some(result.messageId)
        case _ => None
      }.map(EmailSet.asUnparsed))
        .filter(_.nonEmpty)
        .map(DestroyIds)

    def notDestroyed: Option[Map[UnparsedMessageId, SetError]] =
      Option(results.flatMap{
        case failure: DestroyFailure => Some((failure.unparsedMessageId, failure.asMessageSetError))
        case _ => None
      }.toMap)
        .filter(_.nonEmpty)
  }
  object DestroyResult {
    def from(deleteResult: DeleteResult): Seq[DestroyResult] = {
      val success: Seq[DestroySuccess] = deleteResult.getDestroyed.asScala.toSeq
        .map(DestroySuccess)
      val notFound: Seq[DestroyResult] = deleteResult.getNotFound.asScala.toSeq
        .map(id => DestroyFailure(EmailSet.asUnparsed(id), MessageNotFoundException(id)))

      success ++ notFound
    }
  }
  trait DestroyResult
  case class DestroySuccess(messageId: MessageId) extends DestroyResult
  case class DestroyFailure(unparsedMessageId: UnparsedMessageId, e: Throwable) extends DestroyResult {
    def asMessageSetError: SetError = e match {
      case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(s"$unparsedMessageId is not a messageId: ${e.getMessage}"))
      case e: MessageNotFoundException => SetError.notFound(SetErrorDescription(s"Cannot find message with messageId: ${e.messageId.serialize()}"))
      case _ => SetError.serverFail(SetErrorDescription(e.getMessage))
    }
  }
}

class EmailSetDeletePerformer @Inject()(messageIdManager: MessageIdManager,
                                        messageIdFactory: MessageId.Factory) {
  def destroy(emailSetRequest: EmailSetRequest, mailboxSession: MailboxSession): SMono[DestroyResults] = {
    if (emailSetRequest.destroy.isDefined) {
      val messageIdsValidation: Seq[Either[DestroyFailure, MessageId]] = emailSetRequest.destroy.get.value
        .map(unparsedId => EmailSet.parse(messageIdFactory)(unparsedId).toEither
          .left.map(e => DestroyFailure(unparsedId, e)))
      val messageIds: Seq[MessageId] = messageIdsValidation.flatMap {
        case Right(messageId) => Some(messageId)
        case _ => None
      }
      val parsingErrors: Seq[DestroyFailure] = messageIdsValidation.flatMap {
        case Left(e) => Some(e)
        case _ => None
      }

      SMono(messageIdManager.delete(messageIds.toList.asJava, mailboxSession))
        .doOnSuccess(auditTrail(_, mailboxSession))
        .map(DestroyResult.from)
        .onErrorResume(e => SMono.just(messageIds.map(id => DestroyFailure(EmailSet.asUnparsed(id), e))))
        .map(_ ++ parsingErrors)
        .map(DestroyResults)
    } else {
      SMono.just(DestroyResults(Seq()))
    }
  }

  private def auditTrail(deleteResult: DeleteResult, mailboxSession: MailboxSession): Unit =
    if (!deleteResult.getDestroyed.isEmpty) {
      AuditTrail.entry
        .username(mailboxSession.getUser.asString())
        .protocol("JMAP")
        .action("Email/set destroy")
        .parameters(ImmutableMap.of("messageIds", StringUtils.join(deleteResult.getDestroyed),
          "loggedInUser", mailboxSession.getLoggedInUser.toScala
            .map(_.asString())
            .getOrElse("")))
        .log("Mails deleted.")
    }
}
