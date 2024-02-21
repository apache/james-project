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
 ****************************************************************/

package org.apache.james.jmap.method

import java.util.Date

import eu.timepit.refined.auto._
import javax.inject.Inject
import org.apache.james.jmap.api.change.EmailChangeRepository
import org.apache.james.jmap.api.model.Size.sanitizeSize
import org.apache.james.jmap.api.model.{AccountId => JavaAccountId}
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JAMES_SHARES, JMAP_CORE, JMAP_MAIL}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{ClientId, Id, Invocation, ServerId, SessionTranslator, SetError, UuidState}
import org.apache.james.jmap.json.EmailSetSerializer
import org.apache.james.jmap.mail.{BlobId, EmailCreationId, EmailCreationResponse, EmailImport, EmailImportRequest, EmailImportResponse, ThreadId, ValidatedEmailImport}
import org.apache.james.jmap.method.EmailImportMethod.{ImportFailure, ImportResult, ImportResults, ImportSuccess, ImportWithBlob}
import org.apache.james.jmap.routes.{Blob, BlobNotFoundException, BlobResolvers, ProcessingContext, SessionSupplier}
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.exception.{MailboxNotFoundException, OverQuotaException}
import org.apache.james.mailbox.{MailboxManager, MailboxSession, MessageManager}
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.mime4j.codec.DecodeMonitor
import org.apache.james.mime4j.dom.Message
import org.apache.james.mime4j.message.DefaultMessageBuilder
import org.apache.james.mime4j.stream.MimeConfig
import org.apache.james.util.ReactorUtils
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.util.{Try, Using}

object EmailImportMethod {
  private val LOGGER = LoggerFactory.getLogger(classOf[EmailImportMethod])

  case class ImportWithBlob(id: EmailCreationId, request: EmailImport, blob: Blob)
  case class ImportResults(results: Seq[ImportResult]) {
    def created: Option[Map[EmailCreationId, EmailCreationResponse]] =
      Option(results.flatMap{
        case result: ImportSuccess => Some((result.clientId, result.response))
        case _ => None
      }.toMap)
        .filter(_.nonEmpty)

    def notCreated: Option[Map[EmailCreationId, SetError]] = {
      Option(results.flatMap{
        case failure: ImportFailure => Some((failure.clientId, failure.asMessageSetError))
        case _ => None
      }
        .toMap)
        .filter(_.nonEmpty)
    }
  }
  trait ImportResult
  case class ImportSuccess(clientId: EmailCreationId, response: EmailCreationResponse) extends ImportResult
  case class ImportFailure(clientId: EmailCreationId, e: Throwable) extends ImportResult {
    def asMessageSetError: SetError = e match {
      case e: BlobNotFoundException =>
        LOGGER.info(s"Could not import email as ${e.blobId} is not found")
        SetError.notFound(SetErrorDescription(s"Blob ${e.blobId} could not be found"))
      case e: MailboxNotFoundException =>
        LOGGER.info(s"Could not import email as Mailbox ${e.getMessage} is not found")
        SetError.notFound(SetErrorDescription("Mailbox " + e.getMessage))
      case e: IllegalArgumentException =>
        LOGGER.info("Illegal arguments while importing email", e)
        SetError.invalidArguments(SetErrorDescription(e.getMessage))
      case e: OverQuotaException =>
        LOGGER.info(s"Could not import email as the user is overquota", e)
        SetError.overQuota(SetErrorDescription(e.getMessage))
      case _ =>
        LOGGER.error("Failed to import email", e)
        SetError.serverFail(SetErrorDescription(e.getMessage))
    }
  }
}

class EmailImportMethod @Inject() (val metricFactory: MetricFactory,
                                   val sessionSupplier: SessionSupplier,
                                   val sessionTranslator: SessionTranslator,
                                   val blobResolvers: BlobResolvers,
                                   val serializer: EmailSetSerializer,
                                   val mailboxManager: MailboxManager,
                                   val emailChangeRepository: EmailChangeRepository) extends MethodRequiringAccountId[EmailImportRequest] {
  override val methodName: MethodName = MethodName("Email/import")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, JMAP_MAIL)

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, EmailImportRequest] =
    serializer.deserializeEmailImportRequest(invocation.arguments.value).asEitherRequest

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: EmailImportRequest): Publisher[InvocationWithContext] =
    for {
      oldState <- retrieveState(capabilities, mailboxSession)
      importResults <- importEmails(request, mailboxSession)
        .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
      newState <- retrieveState(capabilities, mailboxSession)
    } yield {
      val updatedContext = updateProcessingContext(importResults, invocation.processingContext)
      val importResponse = EmailImportResponse(
        accountId = request.accountId,
        oldState = oldState,
        newState = newState,
        created = importResults.created,
        notCreated = importResults.notCreated)
      InvocationWithContext(
        Invocation(
          methodName = methodName,
          arguments = Arguments(serializer.serializeEmailImportResponse(importResponse)),
          methodCallId = invocation.invocation.methodCallId),
        updatedContext)
    }

  def updateProcessingContext(importResults: ImportResults, processingContext: ProcessingContext): ProcessingContext =
    importResults.created.getOrElse(Map())
      .foldLeft(processingContext) {
        case (context, entry) =>
          Id.validate(entry._2.id.serialize)
            .fold(_ => context,
              serverId => context.recordCreatedId(ClientId(entry._1.id), ServerId(serverId)))
      }

  private def importEmails(request: EmailImportRequest, mailboxSession: MailboxSession): SMono[ImportResults] =
    SFlux.fromIterable(request.emails.toList)
      .flatMap {
        case creationId -> emailImport => resolveBlob(mailboxSession, creationId, emailImport)
      }
      .map {
        case Right(emailImport) => importEmail(mailboxSession, emailImport)
        case Left(e) => e
      }.collectSeq()
      .map(ImportResults)

  private def importEmail(mailboxSession: MailboxSession, emailImport: ImportWithBlob): ImportResult = {
    val either = for {
      validatedRequest <- emailImport.request.validate
      message <- asMessage(emailImport.blob)
      response <- append(validatedRequest, message, mailboxSession)
    } yield response

    either.fold(e => ImportFailure(emailImport.id, e),
      response => ImportSuccess(emailImport.id, response))
  }

  private def resolveBlob(mailboxSession: MailboxSession, creationId: EmailCreationId, emailImport: EmailImport): SMono[Either[ImportFailure, ImportWithBlob]] =
    blobResolvers.resolve(emailImport.blobId, mailboxSession)
      .map(blob => Right[ImportFailure, ImportWithBlob](ImportWithBlob(creationId, emailImport, blob)))
      .onErrorResume(e => SMono.just(Left[ImportFailure, ImportWithBlob](ImportFailure(creationId, e))))

  private def asMessage(blob: Blob): Either[Throwable, Message] = {
    val defaultMessageBuilder = new DefaultMessageBuilder
    defaultMessageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE)
    defaultMessageBuilder.setDecodeMonitor(DecodeMonitor.SILENT)

    Using(blob.content) {content => defaultMessageBuilder.parseMessage(content)}
      .toEither
  }

  private def append(emailImport: ValidatedEmailImport, message: Message, mailboxSession: MailboxSession): Either[Throwable, EmailCreationResponse] =
    Try(mailboxManager.getMailbox(emailImport.mailboxId, mailboxSession)
      .appendMessage(AppendCommand.builder()
        .recent()
        .withFlags(emailImport.keywords.asFlags)
        .withInternalDate(Date.from(emailImport.receivedAt.asUTC.toInstant))
        .build(message),
        mailboxSession))
      .map(asEmailCreationResponse)
      .toEither

  private def asEmailCreationResponse(appendResult: MessageManager.AppendResult): EmailCreationResponse = {
    val blobId: Option[BlobId] = BlobId.of(appendResult.getId.getMessageId).toOption
    val threadId: ThreadId = ThreadId.fromJava(appendResult.getThreadId)
    EmailCreationResponse(appendResult.getId.getMessageId, blobId, threadId, sanitizeSize(appendResult.getSize))
  }

  private def retrieveState(capabilities: Set[CapabilityIdentifier], mailboxSession: MailboxSession): SMono[UuidState] =
    if (capabilities.contains(JAMES_SHARES)) {
      SMono(emailChangeRepository.getLatestStateWithDelegation(JavaAccountId.fromUsername(mailboxSession.getUser)))
        .map(UuidState.fromJava)
    } else {
      SMono(emailChangeRepository.getLatestState(JavaAccountId.fromUsername(mailboxSession.getUser)))
        .map(UuidState.fromJava)
    }
}