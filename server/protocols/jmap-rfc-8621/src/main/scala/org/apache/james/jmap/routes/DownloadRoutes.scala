/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/
package org.apache.james.jmap.routes

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import java.util.stream
import java.util.stream.Stream

import com.google.common.base.CharMatcher
import eu.timepit.refined.numeric.NonNegative
import eu.timepit.refined.refineV
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE
import io.netty.handler.codec.http.HttpResponseStatus.OK
import io.netty.handler.codec.http.{HttpMethod, QueryStringDecoder}
import javax.inject.{Inject, Named}
import org.apache.http.HttpStatus.SC_NOT_FOUND
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.apache.james.jmap.http.Authenticator
import org.apache.james.jmap.http.rfc8621.InjectionKeys
import org.apache.james.jmap.json.Serializer
import org.apache.james.jmap.mail.Email.Size
import org.apache.james.jmap.mail.{BlobId, EmailBodyPart, PartId}
import org.apache.james.jmap.routes.DownloadRoutes.{BUFFER_SIZE, LOGGER}
import org.apache.james.jmap.{Endpoint, JMAPRoute, JMAPRoutes}
import org.apache.james.mailbox.model.{ContentType, FetchGroup, MessageId, MessageResult}
import org.apache.james.mailbox.{MailboxSession, MessageIdManager}
import org.apache.james.mime4j.codec.EncoderUtil
import org.apache.james.mime4j.codec.EncoderUtil.Usage
import org.apache.james.mime4j.message.DefaultMessageWriter
import org.apache.james.util.ReactorUtils
import org.slf4j.{Logger, LoggerFactory}
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

trait BlobResolver {
  def resolve(blobId: BlobId, mailboxSession: MailboxSession): Option[SMono[Blob]]
}

trait Blob {
  def blobId: BlobId
  def contentType: ContentType
  def size: Try[Size]
  def content: InputStream
}

case class BlobNotFoundException(blobId: BlobId) extends RuntimeException

case class MessageBlob(blobId: BlobId, message: MessageResult) extends Blob {
  override def contentType: ContentType = new ContentType("message/rfc822")

  override def size: Try[Size] = refineV[NonNegative](message.getSize) match {
    case Left(e) => Failure(new IllegalArgumentException(e))
    case Right(size) => Success(size)
  }

  override def content: InputStream = message.getFullContent.getInputStream
}

case class EmailBodyPartBlob(blobId: BlobId, part: EmailBodyPart) extends Blob {
  override def size: Try[Size] = Success(part.size)

  override def contentType: ContentType = new ContentType(part.`type`.value)

  override def content: InputStream = {
    val writer = new DefaultMessageWriter
    val outputStream = new ByteArrayOutputStream()
    writer.writeBody(part.entity.getBody, outputStream)
    new ByteArrayInputStream(outputStream.toByteArray)
  }
}

class MessageBlobResolver @Inject()(val messageIdFactory: MessageId.Factory,
                                    val messageIdManager: MessageIdManager) extends BlobResolver {
  override def resolve(blobId: BlobId, mailboxSession: MailboxSession): Option[SMono[Blob]] = {
    Try(messageIdFactory.fromString(blobId.value.value)) match {
      case Failure(_) => None
      case Success(messageId) => Some(SMono.fromPublisher(
        messageIdManager.getMessagesReactive(List(messageId).asJava, FetchGroup.FULL_CONTENT, mailboxSession))
        .map[Blob](MessageBlob(blobId, _))
        .switchIfEmpty(SMono.raiseError(BlobNotFoundException(blobId))))
    }
  }
}

class MessagePartBlobResolver @Inject()(val messageIdFactory: MessageId.Factory,
                                        val messageIdManager: MessageIdManager) extends BlobResolver {
  private def asMessageAndPartId(blobId: BlobId): Try[(MessageId, PartId)] = {
    blobId.value.value.split("_").toList match {
      case List(messageIdString, partIdString) => for {
        messageId <- Try(messageIdFactory.fromString(messageIdString))
        partId <- PartId.parse(partIdString)
      } yield {
        (messageId, partId)
      }
      case _ => Failure(BlobNotFoundException(blobId))
    }
  }

  override def resolve(blobId: BlobId, mailboxSession: MailboxSession): Option[SMono[Blob]] = {
    asMessageAndPartId(blobId) match {
      case Failure(_) => None
      case Success((messageId, partId)) =>
        Some(SMono.fromPublisher(
          messageIdManager.getMessagesReactive(List(messageId).asJava, FetchGroup.FULL_CONTENT, mailboxSession))
          .flatMap(message => SMono.fromTry(EmailBodyPart.of(messageId, message)))
          .flatMap(bodyStructure => SMono.fromTry(bodyStructure.flatten
              .filter(_.blobId.contains(blobId))
            .map(Success(_))
            .headOption
            .getOrElse(Failure(BlobNotFoundException(blobId)))))
          .map[Blob](EmailBodyPartBlob(blobId, _))
          .switchIfEmpty(SMono.raiseError(BlobNotFoundException(blobId))))
    }
  }
}

class BlobResolvers @Inject()(val messageBlobResolver: MessageBlobResolver,
                    val messagePartBlobResolver: MessagePartBlobResolver) {
  def resolve(blobId: BlobId, mailboxSession: MailboxSession): SMono[Blob] =
    messageBlobResolver.resolve(blobId, mailboxSession)
      .orElse(messagePartBlobResolver.resolve(blobId, mailboxSession))
      .getOrElse(SMono.raiseError(BlobNotFoundException(blobId)))
}

class DownloadRoutes @Inject()(@Named(InjectionKeys.RFC_8621) val authenticator: Authenticator,
                     val serializer: Serializer,
                     val blobResolvers: BlobResolvers) extends JMAPRoutes {

  val accountIdParam: String = "accountId"
  val blobIdParam: String = "blobId"
  val nameParam: String = "name"
  val contentTypeParam: String = "contentType"
  val downloadUri = s"/download/{$accountIdParam}/{$blobIdParam}"

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
      .flatMap((mailboxSession: MailboxSession) =>
        SMono.fromTry(BlobId.of(request.param(blobIdParam)))
          .flatMap(blobResolvers.resolve(_, mailboxSession))
          .flatMap(blob => downloadBlob(
            optionalName = queryParam(request, nameParam),
            response = response,
            blobContentType = queryParam(request, contentTypeParam)
              .map(ContentType.of)
              .getOrElse(blob.contentType),
            blob = blob))
          .`then`)
      .onErrorResume {
        case e: UnauthorizedException => SMono.fromPublisher(handleAuthenticationFailure(response, LOGGER, e)).`then`
        case _: BlobNotFoundException => SMono.fromPublisher(response.status(SC_NOT_FOUND).send).`then`
        case e =>
          LOGGER.error("Unexpected error", e)
          SMono.fromPublisher(handleInternalError(response, LOGGER, e)).`then`
      }
      .subscribeOn(Schedulers.elastic)
      .asJava()
      .`then`

  private def downloadBlob(optionalName: Option[String],
                           response: HttpServerResponse,
                           blobContentType: ContentType,
                           blob: Blob): SMono[Unit] =
    SMono.fromPublisher(Mono.using(
      () => blob.content,
      (stream: InputStream) => addContentDispositionHeader(optionalName)
        .compose(addContentLengthHeader(blob.size))
        .apply(response)
        .header(CONTENT_TYPE, blobContentType.asString)
        .status(OK)
        .send(ReactorUtils.toChunks(stream, BUFFER_SIZE)
          .map(Unpooled.wrappedBuffer(_))
          .subscribeOn(Schedulers.elastic))
        .`then`,
      asJavaConsumer[InputStream]((stream: InputStream) => stream.close())))
      .`then`

  private def addContentDispositionHeader(optionalName: Option[String]): HttpServerResponse => HttpServerResponse =
    resp => optionalName.map(addContentDispositionHeaderRegardingEncoding(_, resp))
      .getOrElse(resp)

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
}
