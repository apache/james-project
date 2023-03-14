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
package org.apache.james.jmap.routes

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.stream
import java.util.stream.Stream

import com.google.common.base.CharMatcher
import eu.timepit.refined.numeric.NonNegative
import eu.timepit.refined.refineV
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.HttpHeaderNames.{CONTENT_LENGTH, CONTENT_TYPE}
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.{HttpHeaderValidationUtil, HttpMethod, HttpResponseStatus, QueryStringDecoder}
import javax.inject.{Inject, Named}
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream
import org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE
import org.apache.james.jmap.api.model.Size.{Size, sanitizeSize}
import org.apache.james.jmap.api.model.{Upload, UploadId, UploadNotFoundException}
import org.apache.james.jmap.api.upload.UploadRepository
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.{AccountId, Id, ProblemDetails, SessionTranslator}
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.apache.james.jmap.http.Authenticator
import org.apache.james.jmap.http.rfc8621.InjectionKeys
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.mail.{BlobId, EmailBodyPart, PartId}
import org.apache.james.jmap.method.{AccountNotFoundException, ZoneIdProvider}
import org.apache.james.jmap.routes.DownloadRoutes.{BUFFER_SIZE, LOGGER}
import org.apache.james.jmap.{Endpoint, JMAPRoute, JMAPRoutes}
import org.apache.james.mailbox.model.ContentType.{MediaType, MimeType, SubType}
import org.apache.james.mailbox.model._
import org.apache.james.mailbox.{AttachmentManager, MailboxSession, MessageIdManager}
import org.apache.james.mime4j.codec.EncoderUtil
import org.apache.james.mime4j.codec.EncoderUtil.Usage
import org.apache.james.mime4j.dom.SingleBody
import org.apache.james.mime4j.message.DefaultMessageWriter
import org.apache.james.util.ReactorUtils
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono
import reactor.core.scheduler.Schedulers
import reactor.netty.http.server.{HttpServerRequest, HttpServerResponse}

import scala.compat.java8.FunctionConverters._
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

object DownloadRoutes {
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[DownloadRoutes])

  val BUFFER_SIZE: Int = 16 * 1024
}

sealed trait BlobResolutionResult {
  def asOption: Option[SMono[Blob]]
}
case object NonApplicable extends BlobResolutionResult {
  override def asOption: Option[SMono[Blob]] = None
}
case class Applicable(blob: SMono[Blob]) extends BlobResolutionResult {
  override def asOption: Option[SMono[Blob]] = Some(blob)
}

trait BlobResolver {
  def resolve(blobId: BlobId, mailboxSession: MailboxSession): BlobResolutionResult
}

trait Blob {
  def blobId: BlobId
  def contentType: ContentType
  def size: Try[Size]
  def content: InputStream
}

case class BlobNotFoundException(blobId: BlobId) extends RuntimeException
case class ForbiddenException() extends RuntimeException

case class MessageBlob(blobId: BlobId, message: MessageResult) extends Blob {
  override def contentType: ContentType = ContentType.of(MimeType.of(MediaType.of("message"), SubType.of("rfc822")))

  override def size: Try[Size] = refineV[NonNegative](message.getSize) match {
    case Left(e) => Failure(new IllegalArgumentException(e))
    case Right(size) => Success(size)
  }

  override def content: InputStream = message.getFullContent.getInputStream
}

case class UploadedBlob(blobId: BlobId, upload: Upload) extends Blob {
  override def contentType: ContentType = upload.contentType

  override def size: Try[Size] = Success(upload.size)

  override def content: InputStream = upload.content()
}

case class AttachmentBlob(attachmentMetadata: AttachmentMetadata, fileContent: InputStream) extends Blob {
  override def size: Try[Size] = Success(sanitizeSize(attachmentMetadata.getSize))

  override def contentType: ContentType = attachmentMetadata.getType

  override def content: InputStream = fileContent

  override def blobId: BlobId = BlobId.of(attachmentMetadata.getAttachmentId.getId).get
}

case class EmailBodyPartBlob(blobId: BlobId, part: EmailBodyPart) extends Blob {
  override def size: Try[Size] = Success(part.size)

  override def contentType: ContentType = ContentType.of(part.`type`.value)

  override def content: InputStream = part.entity.getBody match {
    case body: SingleBody => body.getInputStream
    case body =>
      val writer = new DefaultMessageWriter
      val outputStream = new UnsynchronizedByteArrayOutputStream()
      writer.writeBody(body, outputStream)
      outputStream.toInputStream
  }
}

class MessageBlobResolver @Inject()(val messageIdFactory: MessageId.Factory,
                                    val messageIdManager: MessageIdManager) extends BlobResolver {
  override def resolve(blobId: BlobId, mailboxSession: MailboxSession): BlobResolutionResult = {
    Try(messageIdFactory.fromString(blobId.value.value)) match {
      case Failure(_) => NonApplicable
      case Success(messageId) => Applicable(SMono.fromPublisher(
        messageIdManager.getMessagesReactive(List(messageId).asJava, FetchGroup.FULL_CONTENT, mailboxSession))
        .map[Blob](MessageBlob(blobId, _))
        .switchIfEmpty(SMono.error(BlobNotFoundException(blobId))))
    }
  }
}

class UploadResolver @Inject()(val uploadRepository: UploadRepository) extends BlobResolver {
  private val prefix = "uploads-"

  override def resolve(blobId: BlobId, mailboxSession: MailboxSession): BlobResolutionResult = {
    if (!blobId.value.value.startsWith(prefix)) {
      NonApplicable
    } else {
      val uploadIdAsString = blobId.value.value.substring(prefix.length)
      Try(UploadId.from(uploadIdAsString)) match {
        case Failure(_) => NonApplicable
        case Success(uploadId) => Applicable(
          SMono(uploadRepository.retrieve(uploadId, mailboxSession.getUser))
            .map(upload => UploadedBlob(blobId, upload))
            .onErrorResume {
              case _: UploadNotFoundException => SMono.error(BlobNotFoundException(blobId))
            })
      }
    }
  }
}

class AttachmentBlobResolver @Inject()(val attachmentManager: AttachmentManager) extends BlobResolver {
  override def resolve(blobId: BlobId, mailboxSession: MailboxSession): BlobResolutionResult =
    AttachmentId.from(blobId.value.value) match {
      case attachmentId: AttachmentId =>
        Try(attachmentManager.getAttachment(attachmentId, mailboxSession)) match {
          case Success(attachmentMetadata) =>
            Applicable(SMono(attachmentManager.loadReactive(attachmentMetadata, mailboxSession))
              .map(content => AttachmentBlob(attachmentMetadata, content)))
          case Failure(_) => NonApplicable
        }
      case _ => NonApplicable
    }
}

class MessagePartBlobResolver @Inject()(val messageIdFactory: MessageId.Factory,
                                        val messageIdManager: MessageIdManager,
                                        val zoneIdSupplier: ZoneIdProvider) extends BlobResolver {
  private def asMessageAndPartId(blobId: BlobId): Try[(MessageId, PartId)] = {
    blobId.value.value.split('_').toList match {
      case List(messageIdString, partIdString) => for {
        messageId <- Try(messageIdFactory.fromString(messageIdString))
        partId <- PartId.parse(partIdString)
      } yield {
        (messageId, partId)
      }
      case _ => Failure(BlobNotFoundException(blobId))
    }
  }

  override def resolve(blobId: BlobId, mailboxSession: MailboxSession): BlobResolutionResult = {
    asMessageAndPartId(blobId) match {
      case Failure(_) => NonApplicable
      case Success((messageId, partId)) =>
        Applicable(SMono.fromPublisher(
          messageIdManager.getMessagesReactive(List(messageId).asJava, FetchGroup.FULL_CONTENT, mailboxSession))
          .handle[EmailBodyPart] {
            case (message, sink) => EmailBodyPart.of(None, zoneIdSupplier.get(), messageId, message)
              .fold(sink.error, sink.next)
          }
          .handle[EmailBodyPart] {
            case (bodyStructure, sink) =>
              bodyStructure.flatten
                .find(_.blobId.contains(blobId))
                .fold(sink.error(BlobNotFoundException(blobId)))(part => sink.next(part))
          }
          .map[Blob](EmailBodyPartBlob(blobId, _))
          .switchIfEmpty(SMono.error(BlobNotFoundException(blobId))))
    }
  }
}

class BlobResolvers(blobResolvers: Set[BlobResolver]) {

  @Inject
  def this(blobResolvers: java.util.Set[BlobResolver]) = {
    this(blobResolvers.asScala.toSet)
  }

  def resolve(blobId: BlobId, mailboxSession: MailboxSession): SMono[Blob] =
    blobResolvers.flatMap(resolver => resolver.resolve(blobId, mailboxSession).asOption)
      .headOption
      .getOrElse(SMono.error(BlobNotFoundException(blobId)))
}

class DownloadRoutes @Inject()(@Named(InjectionKeys.RFC_8621) val authenticator: Authenticator,
                               val blobResolvers: BlobResolvers,
                               val sessionTranslator: SessionTranslator) extends JMAPRoutes {

  private val accountIdParam: String = "accountId"
  private val blobIdParam: String = "blobId"
  private val nameParam: String = "name"
  private val contentTypeParam: String = "type"
  private val downloadUri = s"/download/{$accountIdParam}/{$blobIdParam}"

  override def routes(): stream.Stream[JMAPRoute] = Stream.of(
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.GET, downloadUri))
      .action(this.get)
      .corsHeaders,
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.OPTIONS, downloadUri))
      .action(JMAPRoutes.CORS_CONTROL)
      .noCorsHeaders)

  private def get(request: HttpServerRequest, response: HttpServerResponse): Mono[Void] =
    SMono(authenticator.authenticate(request))
      .flatMap(mailboxSession => getIfOwner(request, response, mailboxSession))
      .onErrorResume {
        case _: ForbiddenException | _: AccountNotFoundException =>
          respondDetails(response,
            ProblemDetails(status = FORBIDDEN, detail = "You cannot download in others accounts"),
            FORBIDDEN)
        case e: UnauthorizedException =>
          LOGGER.warn("Unauthorized", e)
          respondDetails(e.addHeaders(response),
            ProblemDetails(status = UNAUTHORIZED, detail = e.getMessage),
            UNAUTHORIZED)
        case _: BlobNotFoundException =>
          respondDetails(response,
            ProblemDetails(status = NOT_FOUND, detail = "The resource could not be found"),
            NOT_FOUND)
        case e =>
          LOGGER.error("Unexpected error upon downloads", e)
          respondDetails(response,
            ProblemDetails(status = INTERNAL_SERVER_ERROR, detail = e.getMessage),
            INTERNAL_SERVER_ERROR)
      }
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
      .asJava()
      .`then`

  private def get(request: HttpServerRequest, response: HttpServerResponse, mailboxSession: MailboxSession): SMono[Unit] =
    BlobId.of(request.param(blobIdParam))
      .fold(e => SMono.error(e),
        blobResolvers.resolve(_, mailboxSession))
      .flatMap(blob => downloadBlob(
        optionalName = queryParam(request, nameParam),
        response = response,
        blobContentType = queryParam(request, contentTypeParam)
          .map(ContentType.of)
          .getOrElse(blob.contentType),
        blob = blob)
        .`then`())

  private def getIfOwner(request: HttpServerRequest, response: HttpServerResponse, mailboxSession: MailboxSession): SMono[Unit] =
    Id.validate(request.param(accountIdParam)) match {
      case Right(id: Id) => sessionTranslator.delegateIfNeeded(mailboxSession, AccountId(id))
          .flatMap(session => get(request, response, session))
      case Left(throwable: Throwable) => SMono.error(throwable)
    }

  private def downloadBlob(optionalName: Option[String],
                           response: HttpServerResponse,
                           blobContentType: ContentType,
                           blob: Blob): SMono[Unit] =
    SMono.fromPublisher(Mono.using(
      () => blob.content,
      (stream: InputStream) => addContentDispositionHeader(optionalName)
        .compose(addContentLengthHeader(blob.size))
        .apply(response)
        .header(CONTENT_TYPE, sanitizeHeaderValue(blobContentType.asString))
        .status(OK)
        .send(ReactorUtils.toChunks(stream, BUFFER_SIZE)
          .map(Unpooled.wrappedBuffer(_))
          .subscribeOn(Schedulers.boundedElastic()))
        .`then`,
      asJavaConsumer[InputStream]((stream: InputStream) => stream.close())))
      .`then`

  private def addContentDispositionHeader(optionalName: Option[String]): HttpServerResponse => HttpServerResponse =
    resp => optionalName.map(addContentDispositionHeaderRegardingEncoding(_, resp))
      .getOrElse(resp)

  private def sanitizeHeaderValue(s: String): String =
    if (HttpHeaderValidationUtil.validateValidHeaderValue(s) == -1) {
      s
    } else {
      "application/octet-stream"
    }

  private def addContentLengthHeader(sizeTry: Try[Size]): HttpServerResponse => HttpServerResponse =
    resp => sizeTry
      .map(size => resp.header("Content-Length", size.value.toString))
      .getOrElse(resp)

  private def addContentDispositionHeaderRegardingEncoding(name: String, resp: HttpServerResponse): HttpServerResponse =
    if (CharMatcher.ascii.matchesAllOf(name)) {
      resp.header("Content-Disposition", "attachment; filename=\"" + name + "\"")
    } else {
      resp.header("Content-Disposition", "attachment; filename*=\"" + EncoderUtil.encodeEncodedWord(name, Usage.TEXT_TOKEN) + "\"")
    }

  private def queryParam(httpRequest: HttpServerRequest, parameterName: String): Option[String] =
    queryParam(parameterName, httpRequest.uri)

  private def queryParam(parameterName: String, uri: String): Option[String] =
    Option(new QueryStringDecoder(uri).parameters.get(parameterName))
      .toList
      .flatMap(_.asScala)
      .headOption

  private def respondDetails(httpServerResponse: HttpServerResponse, details: ProblemDetails, statusCode: HttpResponseStatus = BAD_REQUEST): SMono[Unit] =
    SMono.fromCallable(() => ResponseSerializer.serialize(details))
      .map(Json.stringify)
      .map(_.getBytes(StandardCharsets.UTF_8))
      .flatMap(bytes =>
        SMono.fromPublisher(httpServerResponse.status(statusCode)
          .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
          .header(CONTENT_LENGTH, Integer.toString(bytes.length))
          .sendByteArray(SMono.just(bytes))
          .`then`).`then`)
}
