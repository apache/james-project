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

import eu.timepit.refined.auto._
import org.apache.james.jmap.json.Serializer
import org.apache.james.jmap.method.CoreEcho
import org.apache.james.jmap.model.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.model.{Invocation, RequestObject, ResponseObject}
import org.reactivestreams.Publisher
import play.api.libs.json.{JsError, JsSuccess, Json}
import reactor.core.scala.publisher.SMono
import reactor.netty.http.server.{HttpServerRequest, HttpServerResponse}

object JMAPApiRoutes {
  private val ECHO_METHOD = new CoreEcho()
}

class JMAPApiRoutes {
  private val echoMethod = JMAPApiRoutes.ECHO_METHOD

  def post(httpServerRequest: HttpServerRequest, httpServerResponse: HttpServerResponse): SMono[Void] = {
    SMono.fromPublisher(extractRequestObject(httpServerRequest))
      .flatMap(this.process)
      .doOnError(e => new RuntimeException(e.getMessage))
      .`then`()
  }

  private def process(requestObject: RequestObject): SMono[ResponseObject] = {
    SMono.just(
      requestObject.methodCalls.map((invocation: Invocation) =>
        invocation.methodName match {
          case echoMethod.methodName => echoMethod.process(invocation)
          case _ => SMono.just(new Invocation(
            MethodName("error"),
            Arguments(Json.obj("type" -> "Not implemented")),
            invocation.methodCallId))
        }
      )
    ).flatMap((invocations: Seq[Invocation]) => SMono.just(ResponseObject(ResponseObject.SESSION_STATE, invocations)))
  }

  private def extractRequestObject(httpServerRequest: HttpServerRequest): Publisher[RequestObject] = {
    httpServerRequest
      .receive()
      .asInputStream()
      .flatMap(inputStream => new Serializer().deserializeRequestObject(inputStream) match {
        case JsSuccess(requestObject, _) => SMono.just(new ResponseObject(ResponseObject.SESSION_STATE, requestObject.methodCalls))
        case JsError(errors) => SMono.raiseError(new RuntimeException(errors.toString()))
      })
  }
}

