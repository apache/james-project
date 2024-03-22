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
import java.util.concurrent.atomic.AtomicReference
import java.util.stream

import io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import jakarta.inject.{Inject, Named}
import org.apache.james.core.Username
import org.apache.james.events.{EventBus, Registration, RegistrationKey}
import org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE
import org.apache.james.jmap.JMAPUrls.JMAP_WS
import org.apache.james.jmap.api.change.{EmailChangeRepository, MailboxChangeRepository, TypeStateFactory}
import org.apache.james.jmap.api.model.{AccountId => JavaAccountId}
import org.apache.james.jmap.change._
import org.apache.james.jmap.core._
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.apache.james.jmap.http.rfc8621.InjectionKeys
import org.apache.james.jmap.http.{Authenticator, UserProvisioning}
import org.apache.james.jmap.json.{PushSerializer, ResponseSerializer}
import org.apache.james.jmap.{Endpoint, JMAPRoute, JMAPRoutes, InjectionKeys => JMAPInjectionKeys}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.user.api.DelegationStore
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json
import reactor.core.publisher.Sinks.EmitFailureHandler.FAIL_FAST
import reactor.core.publisher.{Mono, Sinks}
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers
import reactor.netty.http.server.{HttpServerRequest, HttpServerResponse}
import reactor.netty.http.websocket.{WebsocketInbound, WebsocketOutbound}

import scala.jdk.CollectionConverters._

object WebSocketRoutes {
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[WebSocketRoutes])
}

case class ClientContext(outbound: Sinks.Many[OutboundMessage], pushRegistration: AtomicReference[Registration], session: MailboxSession) {
  def withRegistration(registration: Registration): Unit = withRegistration(Some(registration))

  def clean(): Unit ={
    withRegistration(None)
    outbound.emitComplete(FAIL_FAST)
  }

  def withRegistration(registration: Option[Registration]): Unit = Option(pushRegistration.getAndSet(registration.orNull))
    .foreach(oldRegistration => SMono(oldRegistration.unregister())
      .subscribeOn(Schedulers.boundedElastic())
      .subscribe())
}

class WebSocketRoutes @Inject() (@Named(InjectionKeys.RFC_8621) val authenticator: Authenticator,
                                 userProvisioner: UserProvisioning,
                                 @Named(JMAPInjectionKeys.JMAP) eventBus: EventBus,
                                 jmapApi: JMAPApi,
                                 mailboxChangeRepository: MailboxChangeRepository,
                                 emailChangeRepository: EmailChangeRepository,
                                 pushSerializer: PushSerializer,
                                 typeStateFactory: TypeStateFactory,
                                 delegationStore: DelegationStore) extends JMAPRoutes {

  override def routes(): stream.Stream[JMAPRoute] = stream.Stream.of(
    JMAPRoute.builder
      .endpoint(Endpoint.ofFixedPath(HttpMethod.GET, JMAP_WS))
      .action(this.handleWebSockets)
      .corsHeaders,
    JMAPRoute.builder
      .endpoint(Endpoint.ofFixedPath(HttpMethod.OPTIONS, JMAP_WS))
      .action(JMAPRoutes.CORS_CONTROL)
      .corsHeaders())

  private def handleWebSockets(httpServerRequest: HttpServerRequest, httpServerResponse: HttpServerResponse): Mono[Void] = {
    SMono(authenticator.authenticate(httpServerRequest))
      .flatMap((mailboxSession: MailboxSession) => userProvisioner.provisionUser(mailboxSession)
        .`then`
        .`then`(SMono(httpServerResponse.sendWebsocket((in, out) => handleWebSocketConnection(mailboxSession)(in, out)))))
      .onErrorResume(throwable => handleHttpHandshakeError(throwable, httpServerResponse))
      .asJava()
      .`then`()
  }

  private def handleWebSocketConnection(session: MailboxSession)(in: WebsocketInbound, out: WebsocketOutbound): Mono[Void] = {
    val sink: Sinks.Many[OutboundMessage] = Sinks.many().unicast().onBackpressureBuffer()

    val context = ClientContext(sink, new AtomicReference[Registration](), session)
    val responseFlux: SFlux[OutboundMessage] = SFlux[WebSocketFrame](in.aggregateFrames()
      .receiveFrames())
      .map(frame => {
        val bytes = new Array[Byte](frame.content().readableBytes)
        frame.content().readBytes(bytes)
        new String(bytes, StandardCharsets.UTF_8)
      })
      .flatMap(message => handleClientMessages(context)(message))
      .doOnTerminate(context.clean)
      .doOnCancel(context.clean)

    out.sendString(
      SFlux.merge(Seq(responseFlux, sink.asFlux()))
        .map(pushSerializer.serialize)
        .map(Json.stringify))
      .`then`()
  }

  private def handleClientMessages(clientContext: ClientContext)(message: String): SMono[OutboundMessage] =
    pushSerializer.deserializeWebSocketInboundMessage(message)
      .fold(invalid => {
        val error = asError(None)(new IllegalArgumentException(invalid.toString()))
        SMono.just(error)
      }, {
          case request: WebSocketRequest =>
            jmapApi.process(request.requestObject, clientContext.session)
              .map[OutboundMessage](WebSocketResponse(request.id, _))
              .onErrorResume(e => SMono.just(asError(request.id)(e)))
          case pushEnable: WebSocketPushEnable =>
            SMono.just(clientContext.session.getUser)
              .concatWith(SFlux.fromPublisher(delegationStore.delegatedUsers(clientContext.session.getUser)))
              .map(username => AccountIdRegistrationKey.of(username).asInstanceOf[RegistrationKey])
              .collectSeq()
              .flatMap(keys => SMono(eventBus.register(
                StateChangeListener(pushEnable.dataTypes.getOrElse(typeStateFactory.all.toSet), clientContext.outbound),
                keys.asJavaCollection)))
              .doOnNext(newRegistration => clientContext.withRegistration(newRegistration))
              .`then`(sendPushStateIfRequested(pushEnable, clientContext))
          case WebSocketPushDisable => SMono.fromCallable(() => clientContext.clean())
          .`then`(SMono.empty)
      })

  private def sendPushStateIfRequested(pushEnable: WebSocketPushEnable, clientContext: ClientContext): SMono[OutboundMessage] =
    pushEnable.pushState
      .map(_ => sendPushState(clientContext))
      .getOrElse(SMono.empty)

  private def sendPushState(clientContext: ClientContext): SMono[OutboundMessage] = {
    val username: Username = clientContext.session.getUser
    val accountId: AccountId = AccountId.from(username).fold(
      failure => throw new IllegalArgumentException(failure),
      success => success)
    SMono(
      for {
        mailboxState <- mailboxChangeRepository.getLatestStateWithDelegation(JavaAccountId.fromUsername(username))
        emailState <- emailChangeRepository.getLatestStateWithDelegation(JavaAccountId.fromUsername(username))
      } yield {
        StateChange(Map(accountId -> TypeState(
          MailboxTypeName.asMap(Some(UuidState.fromJava(mailboxState))) ++
            EmailTypeName.asMap(Some(UuidState.fromJava(emailState))))),
          Some(PushState.from(UuidState(mailboxState.getValue), UuidState(emailState.getValue))))
      })
  }

  private def handleHttpHandshakeError(throwable: Throwable, response: HttpServerResponse): SMono[Void] = throwable match {
    case e: UnauthorizedException => respondDetails(e.addHeaders(response), ProblemDetails.forThrowable(throwable))
    case _ => respondDetails(response, ProblemDetails.forThrowable(throwable))
  }

  private def asError(requestId: Option[RequestId])(throwable: Throwable): WebSocketError =
    WebSocketError(requestId, ProblemDetails.forThrowable(throwable))

  private def respondDetails(httpServerResponse: HttpServerResponse, details: ProblemDetails): SMono[Void] =
    SMono.fromPublisher(httpServerResponse.status(details.status)
      .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
      .sendString(SMono.fromCallable(() => ResponseSerializer.serialize(details).toString),
        StandardCharsets.UTF_8)
      .`then`)
}
