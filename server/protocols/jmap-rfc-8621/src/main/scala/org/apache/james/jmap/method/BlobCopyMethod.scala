/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *  http://www.apache.org/licenses/LICENSE-2.0                  *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.method

import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.api.model.UploadId
import org.apache.james.jmap.api.upload.UploadService
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{BlobCopyRequest, BlobCopyResponse, ErrorCode, Invocation, JmapRfc8621Configuration, SessionTranslator, SetError}
import org.apache.james.jmap.json.BlobCopySerializer
import org.apache.james.jmap.mail.BlobId
import org.apache.james.jmap.mail.MDNParse.UnparsedBlobId
import org.apache.james.jmap.routes.{Blob, BlobNotFoundException, BlobResolvers, SessionSupplier}
import org.apache.james.mailbox.{MailboxSession, SessionProvider}
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.util.ReactorUtils
import org.reactivestreams.Publisher
import org.slf4j.{Logger, LoggerFactory}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.OptionConverters._

sealed trait CopyResult {
  def sourceBlobId: BlobId
}
case class Copied(sourceBlobId: BlobId, copied: BlobId) extends CopyResult
case class NotCopied(sourceBlobId: BlobId, error: SetError) extends CopyResult
case class CopyResults(results: Seq[CopyResult]) {
  def copied: Option[Map[BlobId, BlobId]] =
    Option(results.collect { case Copied(source, copied) => source -> copied }.toMap).filter(_.nonEmpty)

  def notCopied: Option[Map[BlobId, SetError]] =
    Option(results.collect { case NotCopied(source, error) => source -> error }.toMap).filter(_.nonEmpty)
}

case class FromAccountNotFoundException() extends RuntimeException

class BlobCopyMethod @Inject()(val metricFactory: MetricFactory,
                               val sessionSupplier: SessionSupplier,
                               val sessionTranslator: SessionTranslator,
                               val sessionProvider: SessionProvider,
                               val blobResolvers: BlobResolvers,
                               val uploadService: UploadService,
                               val serializer: BlobCopySerializer,
                               val configuration: JmapRfc8621Configuration) extends MethodRequiringAccountId[BlobCopyRequest] {
  private val LOGGER: Logger = LoggerFactory.getLogger(classOf[BlobCopyMethod])

  override val methodName: MethodName = MethodName("Blob/copy")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: BlobCopyRequest): Publisher[InvocationWithContext] =
    resolveSourceSession(request, mailboxSession)
      .flatMap(sourceSession => copyBlobs(request, sourceSession, targetSession = mailboxSession))
      .map(response => asInvocation(response, invocation))
      .onErrorResume {
        case _: FromAccountNotFoundException =>
          SMono.just(asErrorInvocation(ErrorCode.FromAccountNotFound, invocation))
        case e =>
          LOGGER.error("Failed to copy blob", e)
          SMono.just(asErrorInvocation(ErrorCode.ServerFail, e.getMessage, invocation))
      }

  private def resolveSourceSession(request: BlobCopyRequest, mailboxSession: MailboxSession): SMono[MailboxSession] =
    if (request.fromAccountId.equals(request.accountId)) {
      SMono.just(mailboxSession)
    } else {
      mailboxSession.getLoggedInUser.toScala
        .filter(loggedInUser => sessionTranslator.hasAccountId(request.fromAccountId)(loggedInUser))
        .map(createSourceSession)
        .getOrElse(SMono.error(FromAccountNotFoundException()))
    }

  private def createSourceSession(loggedInUser: Username): SMono[MailboxSession] =
    SMono.fromCallable(() => sessionProvider.authenticate(loggedInUser).withoutDelegation())
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)

  private def asInvocation(response: BlobCopyResponse, invocation: InvocationWithContext): InvocationWithContext =
    InvocationWithContext(
      Invocation(
        methodName = methodName,
        arguments = Arguments(serializer.serializeBlobCopyResponse(response)),
        methodCallId = invocation.invocation.methodCallId),
      invocation.processingContext)

  private def asErrorInvocation(errorCode: ErrorCode, invocation: InvocationWithContext): InvocationWithContext =
    InvocationWithContext(
      Invocation.error(errorCode, invocation.invocation.methodCallId),
      invocation.processingContext)

  private def asErrorInvocation(errorCode: ErrorCode, description: String = "", invocation: InvocationWithContext): InvocationWithContext =
    InvocationWithContext(
      Invocation.error(errorCode, description, invocation.invocation.methodCallId),
      invocation.processingContext)

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, BlobCopyRequest] =
    serializer.deserializeBlobCopyRequest(invocation.arguments.value).asEitherRequest
      .flatMap(request => request.validate(configuration).map(_ => request))

  private def copyBlobs(request: BlobCopyRequest, sourceSession: MailboxSession, targetSession: MailboxSession): SMono[BlobCopyResponse] =
    SFlux.fromIterable(request.blobIds.value)
      .flatMap(blobId => copyBlob(blobId, sourceSession, targetSession), ReactorUtils.DEFAULT_CONCURRENCY)
      .collectSeq()
      .map(CopyResults)
      .map(results => BlobCopyResponse(request.fromAccountId, request.accountId, results.copied, results.notCopied))

  private def copyBlob(unparsedBlobId: UnparsedBlobId, sourceSession: MailboxSession, targetSession: MailboxSession): SMono[CopyResult] =
    SMono.fromTry(BlobId.of(unparsedBlobId))
      .flatMap { sourceBlobId =>
        blobResolvers.resolve(sourceBlobId, sourceSession)
          .flatMap(blob => uploadBlob(sourceBlobId, blob, targetSession))
          .onErrorResume(e => SMono.just(NotCopied(sourceBlobId, asSetError(sourceBlobId, sourceSession, targetSession, e))))
      }

  private def uploadBlob(sourceBlobId: BlobId, blob: Blob, targetSession: MailboxSession): SMono[CopyResult] =
    SMono.fromPublisher(uploadService.upload(blob.content, blob.contentType, targetSession.getUser))
      .map(upload => Copied(sourceBlobId, asBlobId(upload.uploadId)))

  private def asBlobId(uploadId: UploadId): BlobId =
    BlobId.of(s"uploads-${uploadId.asString()}").get

  private def asSetError(sourceId: BlobId, sourceSession: MailboxSession, targetSession: MailboxSession, throwable: Throwable): SetError =
    throwable match {
      case e: BlobNotFoundException =>
        LOGGER.info(s"Could not copy blob as ${e.blobId} is not found")
        SetError.notFound(SetErrorDescription(s"Blob ${e.blobId} could not be found"))
      case _ =>
        LOGGER.error(s"Failed to copy blob $sourceId from account ${sourceSession.getUser.asString()} to account ${targetSession.getUser.asString()}", throwable)
        SetError.serverFail(SetErrorDescription(throwable.getMessage))
    }
}
