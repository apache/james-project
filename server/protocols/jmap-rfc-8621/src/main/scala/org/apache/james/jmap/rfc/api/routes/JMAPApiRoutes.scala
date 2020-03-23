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
package org.apache.james.jmap.rfc.api.routes

import io.netty.handler.codec.http.HttpResponseStatus
import org.apache.james.jmap.rfc.api.method.EchoMethod
import reactor.core.publisher.Mono
import reactor.netty.http.server.{HttpServerRequest, HttpServerResponse}

object JMAPApiRoutes {
  private val ECHO_METHOD = new EchoMethod();
  private val METHOD_NAME_PARAMETER = "method-name"
}

class JMAPApiRoutes {
  def post(httpServerRequest: HttpServerRequest, httpServerResponse: HttpServerResponse): Mono[Void] = {
    Mono.just(httpServerRequest)
      .flatMap(httpRequest => this.process(httpRequest, httpServerResponse))
      .`then`()
  }

  def process(httpRequest: HttpServerRequest, httpServerResponse: HttpServerResponse) = {
    httpRequest.param(JMAPApiRoutes.METHOD_NAME_PARAMETER) match {
      case JMAPApiRoutes.ECHO_METHOD.methodName.value.value => Mono.just(httpServerResponse
        .status(HttpResponseStatus.OK)
        .sendObject(JMAPApiRoutes.ECHO_METHOD.process(httpRequest))).`then`()

      case _ => Mono.just(httpServerResponse
        .status(HttpResponseStatus.NOT_IMPLEMENTED)
        .sendObject("Api not implemented")).`then`()
    }
  }
}

