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
import javax.inject.Inject
import javax.mail.Flags
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Properties, SetError, UTCDate}
import org.apache.james.jmap.json.EmailSetSerializer
import org.apache.james.jmap.mail.EmailSet.EmailCreationId
import org.apache.james.jmap.mail.{BlobId, Email, EmailCreationRequest, EmailCreationResponse, EmailSetRequest}
import org.apache.james.jmap.method.EmailSetCreatePerformer.{CreationFailure, CreationResult, CreationResults, CreationSuccess}
import org.apache.james.jmap.routes.{BlobNotFoundException, BlobResolvers}
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.exception.MailboxNotFoundException
import org.apache.james.mailbox.model.MailboxId
import org.apache.james.mailbox.{MailboxManager, MailboxSession}
import org.apache.james.mime4j.dom.Message
import org.apache.james.util.html.HtmlTextExtractor
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

object EmailSetCreatePerformer {
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
      case e: MailboxNotFoundException => SetError.notFound(SetErrorDescription("Mailbox " + e.getMessage))
      case e: BlobNotFoundException => SetError.invalidArguments(SetErrorDescription(s"Attachment not found: ${e.blobId.value}"), Some(Properties("attachments")))
      case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(e.getMessage))
      case _ => SetError.serverFail(SetErrorDescription(e.getMessage))
    }
  }
}

class EmailSetCreatePerformer @Inject()(serializer: EmailSetSerializer,
                                        blobResolvers: BlobResolvers,
                                        htmlTextExtractor: HtmlTextExtractor,
                                        mailboxManager: MailboxManager) {

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
          message => SMono.fromCallable[CreationResult](() => append(clientId, asAppendCommand(request, message), mailboxSession, mailboxIds))))
        .onErrorResume(e => SMono.just[CreationResult](CreationFailure(clientId, e)))
        .subscribeOn(Schedulers.elastic())
    }
  }

  private def append(clientId: EmailCreationId, appendCommand: AppendCommand, mailboxSession: MailboxSession, mailboxIds: List[MailboxId]): CreationSuccess = {
    val appendResult = mailboxManager.getMailbox(mailboxIds.head, mailboxSession)
      .appendMessage(appendCommand, mailboxSession)

    val blobId: Option[BlobId] = BlobId.of(appendResult.getId.getMessageId).toOption
    CreationSuccess(clientId, EmailCreationResponse(appendResult.getId.getMessageId, blobId, blobId, Email.sanitizeSize(appendResult.getSize)))
  }

  private def asAppendCommand(request: EmailCreationRequest, message: Message): AppendCommand =
    AppendCommand.builder()
      .recent()
      .withFlags(request.keywords.map(_.asFlags).getOrElse(new Flags()))
      .withInternalDate(Date.from(request.receivedAt.getOrElse(UTCDate(ZonedDateTime.now())).asUTC.toInstant))
      .build(message)
}
