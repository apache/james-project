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
package org.apache.james.jmap.routes

import java.nio.charset.StandardCharsets
import java.util.stream
import java.util.stream.Stream

import com.fasterxml.jackson.core.exc.StreamConstraintsException
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Splitter
import io.netty.handler.codec.http.HttpHeaderNames.{CONTENT_LENGTH, CONTENT_TYPE}
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus.{OK, NOT_FOUND}
import jakarta.inject.{Inject, Named}
import org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE
import org.apache.james.jmap.JMAPUrls.JMAP
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.{MaxSizeRequest, ProblemDetails, RequestObject}
import org.apache.james.jmap.exceptions.{UnauthorizedException, UserNotFoundException}
import org.apache.james.jmap.http.rfc8621.InjectionKeys
import org.apache.james.jmap.http.{Authenticator, UserProvisioning}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.routes.JMAPApiRoutes.ORIGINAL_IP_HEADER
import org.apache.james.jmap.{Endpoint, JMAPRoute, JMAPRoutes}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.util.{MDCBuilder, ReactorUtils}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsError, JsSuccess, Json}
import reactor.core.publisher.{Mono, SynchronousSink}
import reactor.core.scala.publisher.SMono
import reactor.netty.http.server.{HttpServerRequest, HttpServerResponse}

import scala.jdk.OptionConverters._
import scala.util.Try

object JMAPApiRoutes {
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[JMAPApiRoutes])
  val ORIGINAL_IP_HEADER: String = System.getProperty("james.jmap.mdc.original.ip.header", "x-forwarded-for")

  @VisibleForTesting
  def extractOriginalClientIP(originalIpHeader: String): String =
    Option(originalIpHeader)
      .flatMap(value => Splitter.on(',')
        .trimResults
        .omitEmptyStrings
        .splitToStream(value)
        .findFirst()
        .toScala)
      .getOrElse("")
}

case class StreamConstraintsExceptionWithInput(cause: StreamConstraintsException, input: Array[Byte]) extends RuntimeException(cause)
case class RequestSizeExceeded(input: Array[Byte]) extends RuntimeException

class JMAPApiRoutes @Inject() (@Named(InjectionKeys.RFC_8621) val authenticator: Authenticator,
                     userProvisioner: UserProvisioning,
                     jmapApi: JMAPApi) extends JMAPRoutes {

  override def routes(): stream.Stream[JMAPRoute] = Stream.of(
    JMAPRoute.builder
      .endpoint(Endpoint.ofFixedPath(HttpMethod.POST, JMAP))
      .action(this.post)
      .corsHeaders,
    JMAPRoute.builder
      .endpoint(Endpoint.ofFixedPath(HttpMethod.OPTIONS, JMAP))
      .action(JMAPRoutes.CORS_CONTROL)
      .corsHeaders())

  private def post(httpServerRequest: HttpServerRequest, httpServerResponse: HttpServerResponse): Mono[Void] =
    SMono(authenticator.authenticate(httpServerRequest))
      .flatMap((mailboxSession: MailboxSession) => userProvisioner.provisionUser(mailboxSession)
        .`then`
        .`then`(this.requestAsJsonStream(httpServerRequest)
          .flatMap(requestObject => this.process(requestObject, httpServerResponse, mailboxSession))))
      .onErrorResume(throwable => handleError(throwable, httpServerResponse))
      .asJava()
      .`then`()
      .contextWrite(ReactorUtils.context("MDCBuilder.IP", MDCBuilder.create()
        .addToContext(MDCBuilder.IP, Option(httpServerRequest.hostAddress()).map(_.toString()).getOrElse(""))
        .addToContext(ORIGINAL_IP_HEADER, extractOriginalClientIP(httpServerRequest))
        .addToContext("User-Agent", Option(httpServerRequest.requestHeaders().get("User-Agent")).getOrElse(""))))

  private def extractOriginalClientIP(httpServerRequest: HttpServerRequest): String =
    JMAPApiRoutes.extractOriginalClientIP(httpServerRequest.requestHeaders().get(ORIGINAL_IP_HEADER))

  private def requestAsJsonStream(httpServerRequest: HttpServerRequest): SMono[RequestObject] =
    SMono.fromPublisher(httpServerRequest
      .receive()
      .aggregate()
      .asByteArray())
      .handle[Array[Byte]](validateRequestSize)
      .handle[RequestObject](parseRequestObject)

  private def validateRequestSize: (Array[Byte], SynchronousSink[Array[Byte]]) => Unit = {
    case (input, sink) => if (input.length > MaxSizeRequest.DEFAULT) {
      sink.error(RequestSizeExceeded(input))
    } else {
      sink.next(input)
    }
  }

  private def parseRequestObject: (Array[Byte], SynchronousSink[RequestObject]) => Unit = {
    case (input, sink) => Try(parseRequestObject(input)
      .fold(sink.error, sink.next))
      .fold({
        case ex: StreamConstraintsException => sink.error(StreamConstraintsExceptionWithInput(ex, input))
        case e => sink.error(e)
      }, nothing => nothing)
  }

  private def parseRequestObject(input: Array[Byte]): Either[IllegalArgumentException, RequestObject] =
    ResponseSerializer.deserializeRequestObject(input) match {
      case JsSuccess(requestObject, _) => Right(requestObject)
      case errors: JsError => Left(new IllegalArgumentException(ResponseSerializer.serialize(errors).toString()))
    }

  private def process(requestObject: RequestObject,
                      httpServerResponse: HttpServerResponse,
                      mailboxSession: MailboxSession): SMono[Void] =
    jmapApi.process(requestObject, mailboxSession)
      .map(ResponseSerializer.serialize)
      .map(Json.stringify)
      .map(_.getBytes(StandardCharsets.UTF_8))
      .flatMap(bytes =>
        SMono.fromPublisher(httpServerResponse.status(OK)
          .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
          .header(CONTENT_LENGTH, Integer.toString(bytes.length))
          .sendByteArray(SMono.just(bytes))
          .`then`()))

  private def handleError(throwable: Throwable, response: HttpServerResponse): SMono[Void] = throwable match {
    case e: UnauthorizedException => respondDetails(e.addHeaders(response), ProblemDetails.forThrowable(throwable))
    case e: UserNotFoundException => respondDetails(e.addHeaders(response), ProblemDetails(status = NOT_FOUND, detail = e.getMessage))
    case _ => respondDetails(response, ProblemDetails.forThrowable(throwable))
  }

  private def respondDetails(httpServerResponse: HttpServerResponse, details: ProblemDetails): SMono[Void] =
    SMono.fromCallable(() => ResponseSerializer.serialize(details).toString)
      .map(_.getBytes(StandardCharsets.UTF_8))
      .flatMap(bytes =>
        SMono.fromPublisher(httpServerResponse.status(details.status)
          .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
          .header(CONTENT_LENGTH, Integer.toString(bytes.length))
          .sendByteArray(SMono.just(bytes))
          .`then`))
}

case class UnsupportedCapabilitiesException(capabilities: Set[CapabilityIdentifier]) extends RuntimeException
case class TooManyCallsInRequest(requestObject: RequestObject) extends RuntimeException
