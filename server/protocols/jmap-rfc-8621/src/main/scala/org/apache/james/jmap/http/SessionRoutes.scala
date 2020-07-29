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

package org.apache.james.jmap.http

import java.util.stream.Stream

import io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus.OK
import javax.inject.{Inject, Named}
import org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE_UTF8
import org.apache.james.jmap.JMAPRoutes.CORS_CONTROL
import org.apache.james.jmap.JMAPUrls.AUTHENTICATION
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.apache.james.jmap.http.SessionRoutes.{JMAP_SESSION, LOGGER}
import org.apache.james.jmap.http.rfc8621.InjectionKeys
import org.apache.james.jmap.json.Serializer
import org.apache.james.jmap.model.Session
import org.apache.james.jmap.{Endpoint, JMAPRoute, JMAPRoutes}
import org.slf4j.LoggerFactory
import play.api.libs.json.Json
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono
import reactor.core.scheduler.Schedulers
import reactor.netty.http.server.HttpServerResponse

object SessionRoutes {
  private val JMAP_SESSION: String = "/jmap/session"
  private val LOGGER = LoggerFactory.getLogger(classOf[SessionRoutes])
}

class SessionRoutes @Inject() (@Named(InjectionKeys.RFC_8621) val authenticator: Authenticator,
                    val serializer: Serializer,
                    val sessionSupplier: SessionSupplier) extends JMAPRoutes {

  private val generateSession: JMAPRoute.Action =
    (request, response) => SMono.fromPublisher(authenticator.authenticate(request))
      .map(_.getUser)
      .flatMap(sessionSupplier.generate)
      .flatMap(session => sendRespond(session, response))
      .onErrorResume(throwable => SMono.fromPublisher(errorHandling(throwable, response)))
      .subscribeOn(Schedulers.elastic())
      .asJava()

  override def routes: Stream[JMAPRoute] =
    Stream.of(
      JMAPRoute.builder()
        .endpoint(new Endpoint(HttpMethod.GET, JMAP_SESSION))
        .action(generateSession)
        .corsHeaders,
      JMAPRoute.builder()
        .endpoint(new Endpoint(HttpMethod.OPTIONS, AUTHENTICATION))
        .action(CORS_CONTROL)
        .noCorsHeaders)

  private def sendRespond(session: Session, resp: HttpServerResponse) =
    SMono.fromPublisher(resp.header(CONTENT_TYPE, JSON_CONTENT_TYPE_UTF8)
      .status(OK)
      .sendString(SMono.fromCallable(() => Json.stringify(serializer.serialize(session))))
      .`then`())

  def errorHandling(throwable: Throwable, response: HttpServerResponse): Mono[Void] =
    throwable match {
      case _: UnauthorizedException => handleAuthenticationFailure(response, LOGGER, throwable)
      case _ => handleInternalError(response, LOGGER, throwable)
    }
}
