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

import java.net.{URI, URL}
import java.nio.charset.StandardCharsets
import java.util.stream.Stream

import io.netty.handler.codec.http.HttpHeaderNames.{CONTENT_LENGTH, CONTENT_TYPE}
import io.netty.handler.codec.http.HttpResponseStatus.{BAD_REQUEST, INTERNAL_SERVER_ERROR, OK, UNAUTHORIZED}
import io.netty.handler.codec.http.{HttpMethod, HttpResponseStatus}
import javax.inject.{Inject, Named}
import org.apache.james.jmap.HttpConstants.{JSON_CONTENT_TYPE, JSON_CONTENT_TYPE_UTF8}
import org.apache.james.jmap.JMAPRoutes.CORS_CONTROL
import org.apache.james.jmap.core.{ProblemDetails, Session}
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.apache.james.jmap.http.Authenticator
import org.apache.james.jmap.http.rfc8621.InjectionKeys
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.routes.SessionRoutes.{JMAP_PREFIX_HEADER, JMAP_SESSION, JMAP_WEBSOCKET_PREFIX_HEADER, LOGGER, WELL_KNOWN_JMAP}
import org.apache.james.jmap.{Endpoint, JMAPRoute, JMAPRoutes}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono
import reactor.core.scheduler.Schedulers
import reactor.netty.http.server.{HttpServerRequest, HttpServerResponse}

import scala.util.Try

object SessionRoutes {
  private val JMAP_SESSION: String = "/jmap/session"
  private val WELL_KNOWN_JMAP: String = "/.well-known/jmap"
  private val LOGGER: Logger = LoggerFactory.getLogger(classOf[SessionRoutes])
  private val JMAP_PREFIX_HEADER: String = "X-JMAP-PREFIX"
  private val JMAP_WEBSOCKET_PREFIX_HEADER: String = "X-JMAP-WEBSOCKET-PREFIX"
}

class SessionRoutes @Inject() (@Named(InjectionKeys.RFC_8621) val authenticator: Authenticator,
                               val sessionSupplier: SessionSupplier) extends JMAPRoutes {

  private val generateSession: JMAPRoute.Action =
    (request, response) => SMono.fromPublisher(authenticator.authenticate(request))
      .map(_.getUser)
      .handle[Session] {
        case (username, sink) => sessionSupplier.generate(username, extractJMAPPrefix(request), extractJMAPWebSocketPrefix(request))
          .fold(sink.error, session => sink.next(session))
      }
      .flatMap(session => sendRespond(session, response))
      .onErrorResume(throwable => SMono.fromPublisher(errorHandling(throwable, response)))
      .subscribeOn(Schedulers.elastic())
      .asJava()

  private def extractJMAPPrefix(request: HttpServerRequest): Option[URL] =
    Option(request.requestHeaders().get(JMAP_PREFIX_HEADER))
      .flatMap(value => Try(new URL(value)).toOption)

  private def extractJMAPWebSocketPrefix(request: HttpServerRequest): Option[URI] =
    Option(request.requestHeaders().get(JMAP_WEBSOCKET_PREFIX_HEADER))
      .flatMap(value => Try(new URI(value)).toOption)

  private val redirectToSession: JMAPRoute.Action = JMAPRoutes.redirectTo(JMAP_SESSION)

  override def routes: Stream[JMAPRoute] =
    Stream.of(
      JMAPRoute.builder()
        .endpoint(new Endpoint(HttpMethod.GET, JMAP_SESSION))
        .action(generateSession)
        .corsHeaders,
      JMAPRoute.builder()
        .endpoint(new Endpoint(HttpMethod.OPTIONS, JMAP_SESSION))
        .action(CORS_CONTROL)
        .noCorsHeaders,
      JMAPRoute.builder()
        .endpoint(new Endpoint(HttpMethod.GET, WELL_KNOWN_JMAP))
        .action(redirectToSession)
        .corsHeaders,
      JMAPRoute.builder()
        .endpoint(new Endpoint(HttpMethod.OPTIONS, WELL_KNOWN_JMAP))
        .action(CORS_CONTROL)
        .noCorsHeaders)

  private def sendRespond(session: Session, resp: HttpServerResponse): SMono[Void] =
    SMono.fromCallable(() => Json.stringify(ResponseSerializer.serialize(session)))
      .map(_.getBytes(StandardCharsets.UTF_8))
      .flatMap(bytes => SMono(resp.header(CONTENT_TYPE, JSON_CONTENT_TYPE_UTF8)
        .status(OK)
        .header(CONTENT_LENGTH, Integer.toString(bytes.length))
        .sendByteArray(SMono.just(bytes))
        .`then`()))

  def errorHandling(throwable: Throwable, response: HttpServerResponse): Mono[Void] =
    throwable match {
      case e: UnauthorizedException =>
        LOGGER.warn("Unauthorized", e)
        respondDetails(e.addHeaders(response),
          ProblemDetails(status = UNAUTHORIZED, detail = e.getMessage),
          UNAUTHORIZED)
      case e =>
        LOGGER.error("Unexpected error upon requesting session", e)
        respondDetails(response,
          ProblemDetails(status = INTERNAL_SERVER_ERROR, detail = e.getMessage),
          INTERNAL_SERVER_ERROR)
    }


  private def respondDetails(httpServerResponse: HttpServerResponse, details: ProblemDetails, statusCode: HttpResponseStatus = BAD_REQUEST): Mono[Void] =
    SMono.fromCallable(() => ResponseSerializer.serialize(details).toString)
      .map(_.getBytes(StandardCharsets.UTF_8))
      .flatMap(bytes =>
        SMono.fromPublisher(httpServerResponse.status(details.status)
          .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
          .header(CONTENT_LENGTH, Integer.toString(bytes.length))
          .sendByteArray(SMono.just(bytes))
          .`then`))
      .asJava()
}
