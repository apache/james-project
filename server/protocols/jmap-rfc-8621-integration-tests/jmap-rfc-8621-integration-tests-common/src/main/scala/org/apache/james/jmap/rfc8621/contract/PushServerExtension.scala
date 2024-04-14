/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ************************************************************** */

package org.apache.james.jmap.rfc8621.contract

import java.net.{URI, URL}
import java.time.Clock
import java.util.UUID

import org.junit.jupiter.api.`extension`.{AfterEachCallback, BeforeEachCallback, ExtensionContext, ParameterContext, ParameterResolver}
import org.mockserver.configuration.ConfigurationProperties
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.NottableString.{not, string}

class PushServerExtension extends BeforeEachCallback with AfterEachCallback with ParameterResolver {
  var mockServer: ClientAndServer = _

  override def afterEach(extensionContext: ExtensionContext): Unit = mockServer.close()

  override def supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
    parameterContext.getParameter.getType eq classOf[ClientAndServer]

  override def resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): AnyRef =
    mockServer

  override def beforeEach(extensionContext: ExtensionContext): Unit = {
    mockServer = startClientAndServer(0)
    ConfigurationProperties.logLevel("WARN")
    MockPushServer.appendSpec(mockServer)
  }

  def getBaseUrl: URL = new URI(s"http://127.0.0.1:${mockServer.getLocalPort}").toURL
}

object MockPushServer {
  def appendSpec(mockServer: ClientAndServer): Unit = {
    mockServer
      .when(request.withHeader(not("TTL")))
      .respond(response.withStatusCode(400).withBody("missing TTL header"))

    mockServer
      .when(request.withHeader(not("Content-type")))
      .respond(response.withStatusCode(400).withBody("Content-type is missing or invalid"))

    mockServer
      .when(request.withHeader(string("Content-type"), not("application/json charset=utf-8")))
      .respond(response.withStatusCode(400).withBody("Content-type is missing or invalid"))

    mockServer
      .when(request
        .withPath("/push")
        .withMethod("POST")
        .withHeader(string("Content-type"), string("application/json charset=utf-8"))
        .withHeader(string("Urgency"))
        .withHeader(string("Topic"))
        .withHeader(string("TTL")))
      .respond(response
        .withStatusCode(201)
        .withHeader("Location", String.format("https://push.example.net/message/%s", UUID.randomUUID))
        .withHeader("Date", Clock.systemUTC.toString)
        .withBody(UUID.randomUUID.toString))

    mockServer
      .when(request
        .withPath("/push")
        .withMethod("POST")
        .withHeader(string("Content-type"), string("application/json charset=utf-8"))
        .withHeader(string("Content-Encoding"))
        .withHeader(string("TTL")))
      .respond(response
        .withStatusCode(201)
        .withHeader("Location", String.format("https://push.example.net/message/%s", UUID.randomUUID))
        .withHeader("Date", Clock.systemUTC.toString)
        .withBody(UUID.randomUUID.toString))
  }
}
