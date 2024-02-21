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

import java.time.ZonedDateTime
import java.util.Date

import eu.timepit.refined.auto._
import jakarta.mail.Flags
import javax.inject.Inject
import org.apache.james.jmap.JMAPConfiguration
import org.apache.james.jmap.api.model.Size.sanitizeSize
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Properties, SetError, UTCDate}
import org.apache.james.jmap.json.EmailSetSerializer
import org.apache.james.jmap.mail.{BlobId, EmailCreationId, EmailCreationRequest, EmailCreationResponse, EmailSetRequest, ThreadId}
import org.apache.james.jmap.method.EmailSetCreatePerformer.{CreationFailure, CreationResult, CreationResults, CreationSuccess}
import org.apache.james.jmap.routes.{BlobNotFoundException, BlobResolvers}
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.exception.{MailboxNotFoundException, OverQuotaException}
import org.apache.james.mailbox.model.MailboxId
import org.apache.james.mailbox.{MailboxManager, MailboxSession}
import org.apache.james.mime4j.dom.Message
import org.apache.james.util.ReactorUtils
import org.apache.james.util.html.HtmlTextExtractor
import org.slf4j.LoggerFactory
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.OptionConverters._

object EmailSetCreatePerformer {
  private val LOGGER = LoggerFactory.getLogger(classOf[EmailSetCreatePerformer])

  case class CreationResults(results: Seq[CreationResult]) {
    def created: Option[Map[EmailCreationId, EmailCreationResponse]] =
      Option(results.flatMap{
        case result: CreationSuccess => Some((result.clientId, result.response))
        case _ => None
      }.toMap)
        .filter(_.nonEmpty)

    def notCreated: Option[Map[EmailCreationId, SetError]] = {
      Option(results.flatMap{
        case failure: CreationFailure => Some((failure.clientId, failure.asMessageSetError))
        case _ => None
      }
        .toMap)
        .filter(_.nonEmpty)
    }
  }
  trait CreationResult
  case class CreationSuccess(clientId: EmailCreationId, response: EmailCreationResponse) extends CreationResult
  case class CreationFailure(clientId: EmailCreationId, e: Throwable) extends CreationResult {
    def asMessageSetError: SetError = e match {
      case e: MailboxNotFoundException =>
        LOGGER.info(s"Mailbox ${e.getMessage}")
        SetError.notFound(SetErrorDescription("Mailbox " + e.getMessage))
      case e: BlobNotFoundException =>
        LOGGER.info(s"Attachment not found: ${e.blobId.value}")
        SetError.invalidArguments(SetErrorDescription(s"Attachment not found: ${e.blobId.value}"), Some(Properties("attachments")))
      case e: SizeExceededException =>
        LOGGER.info("Attempt to create too big of a message")
        SetError.tooLarge(SetErrorDescription(e.getMessage))
      case e: IllegalArgumentException =>
        LOGGER.info("Illegal argument in Email/set create", e)
        SetError.invalidArguments(SetErrorDescription(e.getMessage))
      case e: OverQuotaException =>
        LOGGER.info("Email/set failed because overquota")
        SetError.overQuota(SetErrorDescription(e.getMessage))
      case _ =>
        LOGGER.error("Email/set failed to create a message", e)
        SetError.serverFail(SetErrorDescription(e.getMessage))
    }
  }
}

case class SizeExceededException(actualSize: Long, maximumSize: Long) extends IllegalArgumentException(s"Attempt to create a message of $actualSize bytes while the maximum allowed is $maximumSize")

class EmailSetCreatePerformer @Inject()(serializer: EmailSetSerializer,
                                        blobResolvers: BlobResolvers,
                                        htmlTextExtractor: HtmlTextExtractor,
                                        mailboxManager: MailboxManager,
                                        configuration: JMAPConfiguration) {

  def create(request: EmailSetRequest, mailboxSession: MailboxSession): SMono[CreationResults] =
    SFlux.fromIterable(request.create.getOrElse(Map()))
      .concatMap {
        case (clientId, json) => serializer.deserializeCreationRequest(json)
          .fold(e => SMono.just[CreationResult](CreationFailure(clientId, new IllegalArgumentException(e.toString))),
            creationRequest => create(clientId, creationRequest, mailboxSession))
      }.collectSeq()
      .map(CreationResults)

  private def create(clientId: EmailCreationId, request: EmailCreationRequest, mailboxSession: MailboxSession): SMono[CreationResult] = {
    val mailboxIds: List[MailboxId] = request.mailboxIds.value
    if (mailboxIds.size != 1) {
      SMono.just(CreationFailure(clientId, new IllegalArgumentException("mailboxIds need to have size 1")))
    } else {
      SMono.fromCallable(() => request.toMime4JMessage(blobResolvers, htmlTextExtractor, mailboxSession))
        .flatMap(either => either.fold(e => SMono.just(CreationFailure(clientId, e)),
          message =>
            asAppendCommand(request, message)
              .fold(e => SMono.error(e),
              appendCommand => append(clientId, appendCommand, mailboxSession, mailboxIds))))
        .onErrorResume(e => SMono.just[CreationResult](CreationFailure(clientId, e)))
        .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
    }
  }

  private def append(clientId: EmailCreationId, appendCommand: AppendCommand, mailboxSession: MailboxSession, mailboxIds: List[MailboxId]): SMono[CreationSuccess] = {
    for {
      mailbox <- SMono(mailboxManager.getMailboxReactive(mailboxIds.head, mailboxSession))
      appendResult <- SMono(mailbox.appendMessageReactive(appendCommand, mailboxSession))
    } yield {
      val blobId: Option[BlobId] = BlobId.of(appendResult.getId.getMessageId).toOption
      val threadId: ThreadId = ThreadId.fromJava(appendResult.getThreadId)
      CreationSuccess(clientId, EmailCreationResponse(appendResult.getId.getMessageId, blobId, threadId, sanitizeSize(appendResult.getSize)))
    }
  }

  private def asAppendCommand(request: EmailCreationRequest, message: Message): Either[IllegalArgumentException, AppendCommand]  =
    Right(
      AppendCommand.builder()
        .recent()
        .withFlags(request.keywords.map(_.asFlags).getOrElse(new Flags()))
        .withInternalDate(Date.from(request.receivedAt.getOrElse(UTCDate(ZonedDateTime.now())).asUTC.toInstant))
        .build(message))
      .flatMap(appendCommand =>
        configuration.getMaximumSendSize.toScala
          .filter(limit => appendCommand.getMsgIn.size() > limit)
          .map(limit => Left(SizeExceededException(appendCommand.getMsgIn.size(), limit)))
          .getOrElse(Right(appendCommand)))
}
