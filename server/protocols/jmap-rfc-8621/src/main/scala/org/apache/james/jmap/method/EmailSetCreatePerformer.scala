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

import javax.inject.Inject
import javax.mail.Flags
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{SetError, UTCDate}
import org.apache.james.jmap.json.EmailSetSerializer
import org.apache.james.jmap.mail.EmailSet.EmailCreationId
import org.apache.james.jmap.mail.{EmailCreationRequest, EmailCreationResponse, EmailSetRequest}
import org.apache.james.jmap.method.EmailSetCreatePerformer.{CreationFailure, CreationResult, CreationResults, CreationSuccess}
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.exception.{AttachmentNotFoundException, MailboxNotFoundException}
import org.apache.james.mailbox.model.MailboxId
import org.apache.james.mailbox.{AttachmentContentLoader, AttachmentManager, MailboxManager, MailboxSession}
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
      case e: AttachmentNotFoundException => SetError.invalidArguments(SetErrorDescription(s"${e.getMessage}"))
      case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(e.getMessage))
      case _ => SetError.serverFail(SetErrorDescription(e.getMessage))
    }
  }
}

class EmailSetCreatePerformer @Inject()(serializer: EmailSetSerializer,
                                        attachmentManager: AttachmentManager,
                                        attachmentContentLoader: AttachmentContentLoader,
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
      request.toMime4JMessage(attachmentManager, attachmentContentLoader, mailboxSession)
        .fold(e => SMono.just(CreationFailure(clientId, e)),
          message => SMono.fromCallable[CreationResult](() => {
            val appendResult = mailboxManager.getMailbox(mailboxIds.head, mailboxSession)
              .appendMessage(AppendCommand.builder()
                .recent()
                .withFlags(request.keywords.map(_.asFlags).getOrElse(new Flags()))
                .withInternalDate(Date.from(request.receivedAt.getOrElse(UTCDate(ZonedDateTime.now())).asUTC.toInstant))
                .build(message),
                mailboxSession)
            CreationSuccess(clientId, EmailCreationResponse(appendResult.getId.getMessageId))
          })
            .subscribeOn(Schedulers.elastic())
            .onErrorResume(e => SMono.just[CreationResult](CreationFailure(clientId, e))))
    }
  }
}
