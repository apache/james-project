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

import cats.implicits._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.refineV
import io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE
import io.netty.handler.codec.http.{HttpMethod, QueryStringDecoder}
import jakarta.inject.{Inject, Named}
import org.apache.james.events.{EventBus, Registration, RegistrationKey}
import org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE
import org.apache.james.jmap.JMAPUrls.EVENT_SOURCE
import org.apache.james.jmap.api.change.TypeStateFactory
import org.apache.james.jmap.api.model.TypeName
import org.apache.james.jmap.change.{AccountIdRegistrationKey, StateChangeListener}
import org.apache.james.jmap.core.{OutboundMessage, PingMessage, ProblemDetails, StateChange}
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.apache.james.jmap.http.rfc8621.InjectionKeys
import org.apache.james.jmap.http.{Authenticator, UserProvisioning}
import org.apache.james.jmap.json.{PushSerializer, ResponseSerializer}
import org.apache.james.jmap.routes.PingPolicy.Interval
import org.apache.james.jmap.{Endpoint, JMAPRoute, JMAPRoutes, InjectionKeys => JMAPInjectionKeys}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.user.api.DelegationStore
import play.api.libs.json.Json
import reactor.core.publisher.Sinks.EmitFailureHandler.FAIL_FAST
import reactor.core.publisher.{Mono, Sinks}
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers
import reactor.netty.http.server.{HttpServerRequest, HttpServerResponse}

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

case class EventSourceOptionsFactory @Inject() (typeStateFactory: TypeStateFactory){
  def forRequest(request: HttpServerRequest): Either[IllegalArgumentException, EventSourceOptions] =
    for {
      pingPolicy <- retrievePing(request)
      closeAfter <- retrieveCloseAfter(request)
      types <- retrieveTypes(request)
    } yield {
      EventSourceOptions(pingPolicy = pingPolicy,
        closeAfter = closeAfter,
        types = types)
    }

  private def retrieveTypes(request: HttpServerRequest): Either[IllegalArgumentException, Set[TypeName]] =
    queryParam(request, "types") match {
      case None => Left(new IllegalArgumentException("types parameter is compulsory"))
      case Some(List("*")) => Right(typeStateFactory.all.toSet)
      case Some(list) => list.flatMap(_.split(','))
        .map(string => typeStateFactory.parse(string))
        .sequence.map(_.toSet)
    }

  private def retrievePing(request: HttpServerRequest): Either[IllegalArgumentException, PingPolicy] =
    queryParam(request, "ping") match {
      case None => Left(new IllegalArgumentException("ping parameter is compulsory"))
      case Some(List(value)) => PingPolicy.parse(value)
      case _ => Left(new IllegalArgumentException("ping query parameter must be constituted of a single string value"))
    }

  private def retrieveCloseAfter(request: HttpServerRequest): Either[IllegalArgumentException, CloseAfter] =
    queryParam(request, "closeAfter") match {
      case None => Left(new IllegalArgumentException("closeAfter parameter is compulsory"))
      case Some(List(value)) => CloseAfter.parse(value)
      case _ => Left(new IllegalArgumentException("closeAfter query parameter must be constituted of a single string value"))
    }

  private def queryParam(httpRequest: HttpServerRequest, parameterName: String): Option[List[String]] = queryParam(parameterName, httpRequest.uri)

  private def queryParam(parameterName: String, uri: String): Option[List[String]] =
    Option(new QueryStringDecoder(removeTrailingSlash(uri))
      .parameters
      .get(parameterName))
      .map(_.asScala.toList)

  def removeTrailingSlash(uri: String): String = uri match {
    case u if u.endsWith("/") => u.substring(0, u.length -1)
    case _ => uri
  }
}

case class EventSourceOptions(types: Set[TypeName],
                             pingPolicy: PingPolicy = NoPingPolicy,
                             closeAfter: CloseAfter = NoCloseAfter)

object PingPolicy {
  type Interval = Int Refined Positive

  def parse(string: String): Either[IllegalArgumentException, PingPolicy] =
    Try(string.toInt) match {
      case Failure(exception) => Left(new IllegalArgumentException(exception))
      case Success(0) => Right(NoPingPolicy)
      case Success(intervalInSeconds) =>
        refineV[Positive](intervalInSeconds)
          .fold(errorMessage => Left(new IllegalArgumentException(errorMessage)),
          interval => Right(PingEnabled(interval)))
    }
}
sealed trait PingPolicy {
  def asFlux(): SFlux[PingMessage]
}
case object NoPingPolicy extends PingPolicy {
  override def asFlux(): SFlux[PingMessage] = SFlux.never[PingMessage]()
}
case class PingEnabled(interval: Interval) extends PingPolicy {
  override def asFlux(): SFlux[PingMessage] = SFlux.interval(interval.value seconds, Schedulers.parallel())
    .map(_ => PingMessage(interval))
}

object CloseAfter {
  def parse(string: String): Either[IllegalArgumentException, CloseAfter] = string match {
    case "no" => Right(NoCloseAfter)
    case "state" => Right(CloseAfterState)
    case unsupported: String => Left(new IllegalArgumentException(s"$unsupported is not a supported value for eventSource closeAfter parameter"))
  }
}
sealed trait CloseAfter {
  def applyOn(flux: SFlux[OutboundMessage]): SFlux[OutboundMessage]
}
case object CloseAfterState extends CloseAfter {
  override def applyOn(flux: SFlux[OutboundMessage]): SFlux[OutboundMessage] = flux.takeUntil {
    case _: StateChange => true
    case _ => false
  }
}
case object NoCloseAfter extends CloseAfter {
  override def applyOn(flux: SFlux[OutboundMessage]): SFlux[OutboundMessage] = flux
}

class EventSourceRoutes@Inject() (@Named(InjectionKeys.RFC_8621) val authenticator: Authenticator,
                                  userProvisioner: UserProvisioning,
                                  @Named(JMAPInjectionKeys.JMAP) eventBus: EventBus,
                                  pushSerializer: PushSerializer,
                                  typeStateFactory: TypeStateFactory,
                                  delegationStore: DelegationStore) extends JMAPRoutes {

  override def routes(): stream.Stream[JMAPRoute] = stream.Stream.of(
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.GET, EVENT_SOURCE))
      .action(this.handleSSE)
      .corsHeaders,
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.OPTIONS, EVENT_SOURCE))
      .action(JMAPRoutes.CORS_CONTROL)
      .corsHeaders())

  private def handleSSE(request: HttpServerRequest, response: HttpServerResponse): Mono[Void] =
    EventSourceOptionsFactory(typeStateFactory).forRequest(request)
      .fold(e => SMono.error[Void](e),
        options => SMono(authenticator.authenticate(request))
          .flatMap((mailboxSession: MailboxSession) => userProvisioner.provisionUser(mailboxSession)
            .`then`
            .`then`(registerSSE(response, mailboxSession, options))))
      .onErrorResume(throwable => handleConnectionEstablishmentError(throwable, response))
      .asJava()
      .`then`()

  private def registerSSE(response: HttpServerResponse, session: MailboxSession, options: EventSourceOptions): SMono[Unit] = {
    val sink: Sinks.Many[OutboundMessage] = Sinks.many().unicast().onBackpressureBuffer()
    val context = ClientContext(sink, new AtomicReference[Registration](), session)

    val pingDisposable = options.pingPolicy
      .asFlux()
      .subscribe(ping => context.outbound.emitNext(ping, FAIL_FAST))

    SMono.just(session.getUser)
      .concatWith(SFlux.fromPublisher(delegationStore.delegatedUsers(session.getUser)))
      .map(username => AccountIdRegistrationKey.of(username).asInstanceOf[RegistrationKey])
      .collectSeq()
      .flatMap(keys => SMono(eventBus.register(StateChangeListener(options.types, context.outbound), keys.asJavaCollection)))
      .doOnNext(newRegistration => context.withRegistration(newRegistration))
      .subscribeOn(Schedulers.boundedElastic())
      .subscribe()

    SMono(response
      .addHeader("Connection", "keep-alive")
      .sse()
      .sendString(
        options.closeAfter.applyOn(SFlux(sink.asFlux()))
          .map(asSSEEvent),
        StandardCharsets.UTF_8).`then`
      .doFinally(_ => context.clean())
      .doFinally(_ => pingDisposable.dispose())
      .`then`())
      .`then`()
  }

  private def asSSEEvent(outboundMessage: OutboundMessage): String = {
    val event: String = outboundMessage match {
      case _: PingMessage => "ping"
      case _: StateChange => "state"
      case _ => throw new NotImplementedError()
    }
    s"event: $event\ndata: ${Json.stringify(pushSerializer.serializeSSE(outboundMessage))}\n\n"
  }

  private def handleConnectionEstablishmentError(throwable: Throwable, response: HttpServerResponse): SMono[Void] = throwable match {
    case e: UnauthorizedException => respondDetails(e.addHeaders(response), ProblemDetails.forThrowable(throwable))
    case _ => respondDetails(response, ProblemDetails.forThrowable(throwable))
  }

  private def respondDetails(response: HttpServerResponse, details: ProblemDetails): SMono[Void] =
    SMono.fromPublisher(response.status(details.status)
      .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
      .sendString(SMono.fromCallable(() => ResponseSerializer.serialize(details).toString),
        StandardCharsets.UTF_8)
      .`then`)
}
