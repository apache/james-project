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

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.stream
import java.util.stream.Stream

import io.netty.handler.codec.http.HttpHeaderNames.{CONTENT_LENGTH, CONTENT_TYPE}
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus.OK
import jakarta.inject.{Inject, Named}
import org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE
import org.apache.james.jmap.JMAPUrls.JMAP
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.{ProblemDetails, RequestObject}
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.apache.james.jmap.http.rfc8621.InjectionKeys
import org.apache.james.jmap.http.{Authenticator, UserProvisioning}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.{Endpoint, JMAPRoute, JMAPRoutes}
import org.apache.james.mailbox.MailboxSession
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsError, JsSuccess, Json}
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono
import reactor.netty.http.server.{HttpServerRequest, HttpServerResponse}

object JMAPApiRoutes {
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[JMAPApiRoutes])
}

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

  private def requestAsJsonStream(httpServerRequest: HttpServerRequest): SMono[RequestObject] =
    SMono.fromPublisher(httpServerRequest
      .receive()
      .aggregate()
      .asInputStream())
      .handle[RequestObject] {
        case (input, sink) => parseRequestObject(input)
          .fold(sink.error, sink.next)
      }

  private def parseRequestObject(inputStream: InputStream): Either[IllegalArgumentException, RequestObject] =
    ResponseSerializer.deserializeRequestObject(inputStream) match {
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
