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
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.stream
import java.util.stream.Stream
import io.netty.handler.codec.http.HttpHeaderNames.{CONTENT_LENGTH, CONTENT_TYPE}
import io.netty.handler.codec.http.HttpResponseStatus.{BAD_REQUEST, CREATED, FORBIDDEN, INTERNAL_SERVER_ERROR, UNAUTHORIZED}
import io.netty.handler.codec.http.{HttpMethod, HttpResponseStatus}

import javax.inject.{Inject, Named}
import org.apache.commons.fileupload.util.LimitedInputStream
import org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE
import org.apache.james.jmap.api.model.Size.Size
import org.apache.james.jmap.api.model.{UploadId, UploadMetaData}
import org.apache.james.jmap.api.upload.UploadRepository
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.{AccountId, Id, JmapRfc8621Configuration, ProblemDetails, SessionTranslator}
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.apache.james.jmap.http.Authenticator
import org.apache.james.jmap.http.rfc8621.InjectionKeys
import org.apache.james.jmap.json.{ResponseSerializer, UploadSerializer}
import org.apache.james.jmap.mail.BlobId
import org.apache.james.jmap.method.AccountNotFoundException
import org.apache.james.jmap.routes.UploadRoutes.LOGGER
import org.apache.james.jmap.{Endpoint, JMAPRoute, JMAPRoutes}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.model.ContentType
import org.apache.james.util.ReactorUtils
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono
import reactor.netty.http.server.{HttpServerRequest, HttpServerResponse}

case class TooBigUploadException() extends RuntimeException

object UploadRoutes {
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[UploadRoutes])
}

case class UploadResponse(accountId: AccountId,
                          blobId: BlobId,
                          `type`: ContentType,
                          size: Size)

class UploadRoutes @Inject()(@Named(InjectionKeys.RFC_8621) val authenticator: Authenticator,
                             val configuration: JmapRfc8621Configuration,
                             val uploadRepository: UploadRepository,
                             val serializer: UploadSerializer,
                             val sessionTranslator: SessionTranslator) extends JMAPRoutes {

  private val accountIdParam: String = "accountId"
  private val uploadURI = s"/upload/{$accountIdParam}"

  override def routes(): stream.Stream[JMAPRoute] = Stream.of(
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.POST, uploadURI))
      .action(this.post)
      .corsHeaders,
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.OPTIONS, uploadURI))
      .action(JMAPRoutes.CORS_CONTROL)
      .noCorsHeaders)

  def post(request: HttpServerRequest, response: HttpServerResponse): Mono[Void] = {
    request.requestHeaders.get(CONTENT_TYPE) match {
      case contentType: String if contentType.nonEmpty => SMono.fromPublisher(
          authenticator.authenticate(request))
        .flatMap(session => post(request, response, ContentType.of(contentType), session))
        .onErrorResume {
          case e: UnauthorizedException =>
            LOGGER.warn("Unauthorized", e)
            respondDetails(e.addHeaders(response),
              ProblemDetails(status = UNAUTHORIZED, detail = e.getMessage),
              UNAUTHORIZED)
          case _: TooBigUploadException =>
            respondDetails(response,
              ProblemDetails(status = BAD_REQUEST, detail = "Attempt to upload exceed max size"),
              BAD_REQUEST)
          case _: ForbiddenException | _: AccountNotFoundException =>
            respondDetails(response,
              ProblemDetails(status = FORBIDDEN, detail = "Upload to other accounts is forbidden"),
              FORBIDDEN)
          case e =>
            LOGGER.error("Unexpected error upon uploads", e)
            respondDetails(response,
              ProblemDetails(status = INTERNAL_SERVER_ERROR, detail = e.getMessage),
              INTERNAL_SERVER_ERROR)
        }
        .asJava()
        .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
        .`then`()
      case _ => response.status(BAD_REQUEST).send
    }
  }

  def post(request: HttpServerRequest, response: HttpServerResponse, contentType: ContentType, mailboxSession: MailboxSession): SMono[Void] = {
    Id.validate(request.param(accountIdParam)) match {
      case Right(id: Id) =>
        val targetAccountId: AccountId = AccountId(id)
        sessionTranslator.delegateIfNeeded(mailboxSession, targetAccountId)
          .flatMap(session => handle(request, response, contentType, session, targetAccountId))

      case Left(throwable: Throwable) => SMono.error(throwable)
    }
  }

  private def handle(request: HttpServerRequest, response: HttpServerResponse, contentType: ContentType, session: MailboxSession, targetAccountId: AccountId): SMono[Void] =
    SMono.fromCallable(() => ReactorUtils.toInputStream(request.receive
      // Unwrapping to byte array needed to solve data races and buffer reordering when using .asByteBuffer()
      .asByteArray()
      .map(array => ByteBuffer.wrap(array))))
      .flatMap(content => handle(targetAccountId, contentType, content, session, response))

  def handle(accountId: AccountId, contentType: ContentType, content: InputStream, mailboxSession: MailboxSession, response: HttpServerResponse): SMono[Void] = {
    val maxSize: Long = configuration.maxUploadSize.value.value

    SMono.fromCallable(() => new LimitedInputStream(content, maxSize) {
      override def raiseError(max: Long, count: Long): Unit = if (count > max) {
        throw TooBigUploadException()
      }})
      .flatMap(uploadContent(accountId, contentType, _, mailboxSession))
      .flatMap(uploadResponse => {
        val jsonValue = serializer.serialize(uploadResponse)
        val json = Json.stringify(jsonValue)
        val bytes = json.getBytes(StandardCharsets.UTF_8)

        SMono.fromPublisher(response
          .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
          .header(CONTENT_LENGTH, Integer.toString(bytes.length))
          .status(CREATED)
          .sendByteArray(SMono.just(bytes)))
      })
  }

  def uploadContent(accountId: AccountId, contentType: ContentType, inputStream: InputStream, session: MailboxSession): SMono[UploadResponse] =
    SMono
      .fromPublisher(uploadRepository.upload(inputStream, contentType, session.getUser))
      .map(fromAttachment(_, accountId))

  private def fromAttachment(uploadMetaData: UploadMetaData, accountId: AccountId): UploadResponse =
    UploadResponse(
        blobId = asBlobId(uploadMetaData.uploadId),
        `type` = uploadMetaData.contentType,
        size = uploadMetaData.size,
        accountId = accountId)

  private def asBlobId(uploadId: UploadId): BlobId = BlobId.of(s"uploads-${uploadId.asString()}" ).get

  private def respondDetails(httpServerResponse: HttpServerResponse, details: ProblemDetails, statusCode: HttpResponseStatus = BAD_REQUEST): SMono[Void] =
    SMono.fromCallable(() => ResponseSerializer.serialize(details).toString)
      .map(_.getBytes(StandardCharsets.UTF_8))
      .flatMap(bytes =>
        SMono.fromPublisher(httpServerResponse.status(details.status)
          .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
          .header(CONTENT_LENGTH, Integer.toString(bytes.length))
          .sendByteArray(SMono.just(bytes))
          .`then`))
}
