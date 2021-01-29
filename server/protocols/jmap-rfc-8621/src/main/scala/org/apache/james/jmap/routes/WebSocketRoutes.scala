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

import java.nio.charset.StandardCharsets
import java.util.stream

import io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import javax.inject.{Inject, Named}
import org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE
import org.apache.james.jmap.JMAPUrls.JMAP_WS
import org.apache.james.jmap.core.{ProblemDetails, RequestId, WebSocketError, WebSocketOutboundMessage, WebSocketRequest, WebSocketResponse}
import org.apache.james.jmap.http.rfc8621.InjectionKeys
import org.apache.james.jmap.http.{Authenticator, UserProvisioning}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.{Endpoint, JMAPRoute, JMAPRoutes}
import org.apache.james.mailbox.MailboxSession
import org.slf4j.{Logger, LoggerFactory}
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers
import reactor.netty.http.server.{HttpServerRequest, HttpServerResponse}
import reactor.netty.http.websocket.{WebsocketInbound, WebsocketOutbound}

object WebSocketRoutes {
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[WebSocketRoutes])
}

class WebSocketRoutes @Inject() (@Named(InjectionKeys.RFC_8621) val authenticator: Authenticator,
                                 userProvisioner: UserProvisioning,
                                 jmapApi: JMAPApi) extends JMAPRoutes {

  override def routes(): stream.Stream[JMAPRoute] = stream.Stream.of(
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.GET, JMAP_WS))
      .action(this.handleWebSockets)
      .corsHeaders,
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.OPTIONS, JMAP_WS))
      .action(JMAPRoutes.CORS_CONTROL)
      .corsHeaders())

  private def handleWebSockets(httpServerRequest: HttpServerRequest, httpServerResponse: HttpServerResponse): Mono[Void] = {
    SMono(authenticator.authenticate(httpServerRequest))
      .flatMap((mailboxSession: MailboxSession) => userProvisioner.provisionUser(mailboxSession)
        .`then`
        .`then`(SMono(httpServerResponse.sendWebsocket((in, out) => handleWebSocketConnection(mailboxSession)(in, out)))))
      .onErrorResume(throwable => handleHttpHandshakeError(throwable, httpServerResponse))
      .subscribeOn(Schedulers.elastic)
      .asJava()
      .`then`()
  }

  private def handleWebSocketConnection(session: MailboxSession)(in: WebsocketInbound, out: WebsocketOutbound): Mono[Void] =
    SFlux[WebSocketFrame](in.aggregateFrames()
      .receiveFrames())
      .map(frame => {
        val bytes = new Array[Byte](frame.content().readableBytes)
        frame.content().readBytes(bytes)
        new String(bytes, StandardCharsets.UTF_8)
      })
      .flatMap(handleClientMessages(session))
      .onErrorResume(e => SMono.just(asError(None)(e)))
      .map(ResponseSerializer.serialize)
      .map(_.toString)
      .flatMap(response => out.sendString(SMono.just(response), StandardCharsets.UTF_8))
      .onErrorResume(e => {
        e.printStackTrace()
        SMono.empty
      })
      .`then`()
      .asJava()
      .`then`()

  private def handleClientMessages(session: MailboxSession)(message: String): SMono[WebSocketOutboundMessage] =
    ResponseSerializer.deserializeWebSocketInboundMessage(message)
      .fold(invalid => {
        val error = asError(None)(new IllegalArgumentException(invalid.toString()))
        SMono.just[WebSocketOutboundMessage](error)
      }, {
          case request: WebSocketRequest =>
            jmapApi.process(request.requestObject, session)
              .map[WebSocketOutboundMessage](WebSocketResponse(request.requestId, _))
              .onErrorResume(e => SMono.just(asError(request.requestId)(e)))
              .subscribeOn(Schedulers.elastic)
        })

  private def handleHttpHandshakeError(throwable: Throwable, response: HttpServerResponse): SMono[Void] =
    respondDetails(response, ProblemDetails.forThrowable(throwable))

  private def asError(requestId: Option[RequestId])(throwable: Throwable): WebSocketError =
    WebSocketError(requestId, ProblemDetails.forThrowable(throwable))

  private def respondDetails(httpServerResponse: HttpServerResponse, details: ProblemDetails): SMono[Void] =
    SMono.fromPublisher(httpServerResponse.status(details.status)
      .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
      .sendString(SMono.fromCallable(() => ResponseSerializer.serialize(details).toString),
        StandardCharsets.UTF_8)
      .`then`)
}
