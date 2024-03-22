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
import java.util.stream.Stream

import io.netty.handler.codec.http.HttpHeaderNames.{CONTENT_LENGTH, CONTENT_TYPE}
import io.netty.handler.codec.http.HttpResponseStatus.{BAD_REQUEST, INTERNAL_SERVER_ERROR, OK, UNAUTHORIZED}
import io.netty.handler.codec.http.{HttpMethod, HttpResponseStatus}
import jakarta.inject.{Inject, Named}
import org.apache.commons.lang3.tuple.Pair
import org.apache.james.core.Username
import org.apache.james.jmap.HttpConstants.{JSON_CONTENT_TYPE, JSON_CONTENT_TYPE_UTF8}
import org.apache.james.jmap.JMAPRoutes.CORS_CONTROL
import org.apache.james.jmap.core.{JmapRfc8621Configuration, ProblemDetails, Session, UrlPrefixes}
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.apache.james.jmap.http.Authenticator
import org.apache.james.jmap.http.rfc8621.InjectionKeys
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.routes.SessionRoutes.{JMAP_SESSION, LOGGER, WELL_KNOWN_JMAP}
import org.apache.james.jmap.{Endpoint, JMAPRoute, JMAPRoutes}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.user.api.DelegationStore
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.netty.http.server.HttpServerResponse

import scala.jdk.OptionConverters._

object SessionRoutes {
  private val JMAP_SESSION: String = "/jmap/session"
  private val WELL_KNOWN_JMAP: String = "/.well-known/jmap"
  private val LOGGER: Logger = LoggerFactory.getLogger(classOf[SessionRoutes])
}

class SessionRoutes @Inject()(@Named(InjectionKeys.RFC_8621) val authenticator: Authenticator,
                              val sessionSupplier: SessionSupplier,
                              val delegationStore: DelegationStore,
                              val jmapRfc8621Configuration: JmapRfc8621Configuration) extends JMAPRoutes {

  private val generateSession: JMAPRoute.Action =
    (request, response) => SMono.fromPublisher(authenticator.authenticate(request))
      .flatMap(mailboxSession => getDelegatedUsers(mailboxSession)
        .collectSeq()
        .map(seq => Pair.of(mailboxSession.getUser, seq)))
      .handle[Session] {
        case (baseUserAndDelegatedUsers, sink) => sessionSupplier.generate(
          username = baseUserAndDelegatedUsers.getLeft,
          delegatedUsers = baseUserAndDelegatedUsers.getRight.toSet,
          urlPrefixes = UrlPrefixes.from(jmapRfc8621Configuration, request))
          .fold(sink.error, session => sink.next(session))
      }
      .flatMap(session => sendRespond(session, response))
      .onErrorResume(throwable => SMono.fromPublisher(errorHandling(throwable, response)))
      .asJava()

  private val redirectToSession: JMAPRoute.Action = JMAPRoutes.redirectTo(JMAP_SESSION)

  override def routes: Stream[JMAPRoute] =
    Stream.of(
      JMAPRoute.builder()
        .endpoint(Endpoint.ofFixedPath(HttpMethod.GET, JMAP_SESSION))
        .action(generateSession)
        .corsHeaders,
      JMAPRoute.builder()
        .endpoint(Endpoint.ofFixedPath(HttpMethod.OPTIONS, JMAP_SESSION))
        .action(CORS_CONTROL)
        .noCorsHeaders,
      JMAPRoute.builder()
        .endpoint(Endpoint.ofFixedPath(HttpMethod.GET, WELL_KNOWN_JMAP))
        .action(redirectToSession)
        .corsHeaders,
      JMAPRoute.builder()
        .endpoint(Endpoint.ofFixedPath(HttpMethod.OPTIONS, WELL_KNOWN_JMAP))
        .action(CORS_CONTROL)
        .noCorsHeaders)

  private def getDelegatedUsers(mailboxSession: MailboxSession): SFlux[Username] =
    SFlux(delegationStore.delegatedUsers(mailboxSession.getLoggedInUser.toScala
      .getOrElse(mailboxSession.getUser)))

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
