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

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.stream
import java.util.stream.Stream

import eu.timepit.refined.auto._
import io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus.OK
import javax.inject.Inject
import org.apache.http.HttpStatus.SC_BAD_REQUEST
import org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE
import org.apache.james.jmap.JMAPUrls.JMAP
import org.apache.james.jmap.json.Serializer
import org.apache.james.jmap.method.CoreEcho
import org.apache.james.jmap.model.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.model.{Invocation, RequestObject, ResponseObject}
import org.apache.james.jmap.{Endpoint, JMAPRoute, JMAPRoutes}
import play.api.libs.json.{JsError, JsSuccess, Json}
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers
import reactor.netty.http.server.{HttpServerRequest, HttpServerResponse}

class JMAPApiRoutes @Inject() (serializer: Serializer) extends JMAPRoutes {
  private val coreEcho = new CoreEcho

  override def routes(): stream.Stream[JMAPRoute] = Stream.of(
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.POST, JMAP))
      .action(this.post)
      .corsHeaders,
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.OPTIONS, JMAP))
      .action(JMAPRoutes.CORS_CONTROL)
      .corsHeaders())

  private def post(httpServerRequest: HttpServerRequest, httpServerResponse: HttpServerResponse): Mono[Void] =
    this.requestAsJsonStream(httpServerRequest)
      .flatMap(requestObject => this.process(requestObject, httpServerResponse))
      .onErrorResume(throwable => handleError(throwable, httpServerResponse))
      .subscribeOn(Schedulers.elastic)
      .asJava()
      .`then`()

  private def requestAsJsonStream(httpServerRequest: HttpServerRequest): SMono[RequestObject] = {
    SMono.fromPublisher(httpServerRequest
      .receive()
      .aggregate()
      .asInputStream())
      .flatMap(this.parseRequestObject)
  }

  private def parseRequestObject(inputStream: InputStream): SMono[RequestObject] =
    serializer.deserializeRequestObject(inputStream) match {
      case JsSuccess(requestObject, _) => SMono.just(requestObject)
      case JsError(_) => SMono.raiseError(new IllegalArgumentException("Invalid RequestObject"))
    }

  private def process(requestObject: RequestObject, httpServerResponse: HttpServerResponse): SMono[Void] =
    requestObject
      .methodCalls
      .map(this.processMethodWithMatchName)
      .foldLeft(SFlux.empty[Invocation]) { (flux: SFlux[Invocation], mono: SMono[Invocation]) => flux.mergeWith(mono) }
      .collectSeq()
      .flatMap((invocations: Seq[Invocation]) =>
        SMono.fromPublisher(httpServerResponse.status(OK)
          .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
          .sendString(
            SMono.fromCallable(() =>
              serializer.serialize(ResponseObject(ResponseObject.SESSION_STATE, invocations)).toString),
            StandardCharsets.UTF_8
          ).`then`())
      )

  private def processMethodWithMatchName(invocation: Invocation): SMono[Invocation] = invocation.methodName match {
    case coreEcho.methodName => SMono.fromPublisher(coreEcho.process(invocation))
    case _ => SMono.just(new Invocation(
      MethodName("error"),
      Arguments(Json.obj("type" -> "Not implemented")),
      invocation.methodCallId))
  }

  private def handleError(throwable: Throwable, httpServerResponse: HttpServerResponse): SMono[Void] = {
    if (throwable.isInstanceOf[IllegalArgumentException]) {
      return SMono.fromPublisher(httpServerResponse.status(SC_BAD_REQUEST)
        .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
        .sendString(SMono.fromCallable(() => throwable.getMessage), StandardCharsets.UTF_8)
        .`then`())
    }

    SMono.fromPublisher(handleInternalError(httpServerResponse, throwable))
  }
}
