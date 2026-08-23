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

package org.apache.james.jmap.pushsubscription

import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger

import org.apache.james.jmap.api.model.PushSubscriptionServerURL
import org.apache.james.jmap.pushsubscription.SSRFValidator.HostResolver
import org.apache.james.jmap.pushsubscription.WebPushClientTestFixture.PUSH_REQUEST_SAMPLE
import org.assertj.core.api.Assertions.{assertThatCode, assertThatThrownBy}
import org.junit.jupiter.api.{AfterEach, BeforeEach, Test}
import org.mockserver.configuration.ConfigurationProperties
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.verify.VerificationTimes
import reactor.core.scala.publisher.SMono

class DefaultWebPushClientSSRFTest {
  private val CONFIGURATION: PushClientConfiguration = PushClientConfiguration(
    maxTimeoutSeconds = Some(10),
    maxConnections = Some(10),
    preventServerSideRequestForgery = true)

  var mockServer: ClientAndServer = _

  @BeforeEach
  def setUp(): Unit = {
    mockServer = startClientAndServer(0)
    ConfigurationProperties.logLevel("WARN")
    MockPushServer.appendSpec(mockServer)
  }

  @AfterEach
  def tearDown(): Unit = mockServer.close()

  @Test
  def pushShouldNotReachAHostReboundToAForbiddenAddress(): Unit = {
    // Resolves to a public address the first time, to the push server the connection would land on afterwards
    val counter: AtomicInteger = new AtomicInteger(0)
    val rebinding: HostResolver = _ => if (counter.getAndIncrement() == 0) {
      Seq(InetAddress.getByName("93.184.216.34"))
    } else {
      Seq(InetAddress.getByName("127.0.0.1"))
    }
    val testee: DefaultWebPushClient = new DefaultWebPushClient(CONFIGURATION, new SSRFValidator(rebinding))

    assertThatThrownBy(() => SMono.fromPublisher(testee.push(
      PushSubscriptionServerURL.from(s"http://push.example.com:${mockServer.getLocalPort}/push").get,
      PUSH_REQUEST_SAMPLE)).block())
      .hasStackTraceContaining("server-side request forgery")

    mockServer.verify(request().withPath("/push"), VerificationTimes.exactly(0))
  }

  @Test
  def pushShouldSucceedThroughTheValidatingResolver(): Unit = {
    // Server side request forgery prevention is on: only the address policy is relaxed, so that the
    // loopback bound push server can be reached and the resolver the client connects with is exercised
    val testee: DefaultWebPushClient = new DefaultWebPushClient(CONFIGURATION,
      new SSRFValidator(policy = (_: InetAddress) => None))

    assertThatCode(() => SMono.fromPublisher(testee.push(
      PushSubscriptionServerURL.from(s"http://127.0.0.1:${mockServer.getLocalPort}/push").get,
      PUSH_REQUEST_SAMPLE)).block())
      .doesNotThrowAnyException()

    mockServer.verify(request().withPath("/push"), VerificationTimes.atLeast(1))
  }
}
