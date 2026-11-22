/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.pushsubscription

import java.nio.charset.StandardCharsets
import java.time.Duration

import io.netty.buffer.Unpooled
import org.apache.james.jmap.api.model.PushSubscriptionServerURL
import org.apache.james.jmap.pushsubscription.WebPushClientTestFixture.PUSH_REQUEST_SAMPLE
import org.assertj.core.api.Assertions.{assertThatCode, assertThatThrownBy}
import org.junit.jupiter.api.{AfterEach, BeforeEach, Test}
import reactor.core.publisher.{Flux, Mono, SynchronousSink}
import reactor.netty.DisposableServer
import reactor.netty.http.server.HttpServer

class DefaultWebPushClientBoundedResponseTest {
  private val CHUNK: Array[Byte] = "a".repeat(8192).getBytes(StandardCharsets.UTF_8)

  private var server: DisposableServer = _

  @BeforeEach
  def setUp(): Unit = {
    server = HttpServer.create()
      .host("127.0.0.1")
      .port(0)
      .route(routes => routes
        // Endless response bodies
        .post("/error", (request, response) => request.receive().`then`(Mono.from(response.status(500)
          .send(Flux.generate((sink: SynchronousSink[io.netty.buffer.ByteBuf]) => sink.next(Unpooled.wrappedBuffer(CHUNK)))))))
        .post("/success", (request, response) => request.receive().`then`(Mono.from(response.status(201)
          .send(Flux.generate((sink: SynchronousSink[io.netty.buffer.ByteBuf]) => sink.next(Unpooled.wrappedBuffer(CHUNK)))))))
        .post("/short-error", (request, response) => request.receive().`then`(Mono.from(response.status(500)
          .sendString(Mono.just("short error")))))
        // An endless response body, sent slowly enough not to trigger the response (inactivity) timeout
        .post("/slow", (request, response) => request.receive().`then`(Mono.from(response.status(500)
          .send(Flux.interval(Duration.ofMillis(100)).map(_ => Unpooled.wrappedBuffer(Array[Byte]('a'.toByte)))))))
      )
      .bindNow()
  }

  @AfterEach
  def tearDown(): Unit = server.disposeNow()

  private val testee: WebPushClient = new DefaultWebPushClient(PushClientConfiguration(
    maxTimeoutSeconds = Some(2),
    maxConnections = Some(10),
    preventServerSideRequestForgery = false))

  private def push(path: String): Unit =
    Mono.from(testee.push(PushSubscriptionServerURL.from(s"http://127.0.0.1:${server.port()}$path").get, PUSH_REQUEST_SAMPLE))
      .block(Duration.ofSeconds(30))

  @Test
  def pushShouldOnlyReadTheBeginningOfAnEndlessErrorResponse(): Unit = {
    assertThatThrownBy(() => push("/error"))
      .hasMessageContaining("a".repeat(DefaultWebPushClient.PUSH_SERVER_ERROR_RESPONSE_MAX_LENGTH))
      .hasMessageNotContaining("a".repeat(DefaultWebPushClient.PUSH_SERVER_ERROR_RESPONSE_MAX_LENGTH + 1))
      .isInstanceOf(classOf[WebPushTemporarilyUnavailableException])
  }

  @Test
  def pushShouldNotReadTheBodyOfASuccessfulResponse(): Unit = {
    assertThatCode(() => push("/success"))
      .doesNotThrowAnyException()
  }

  @Test
  def pushShouldTimeoutWhenThePushServerKeepsSendingSlowly(): Unit = {
    assertThatThrownBy(() => push("/slow"))
      .hasMessageContaining("was not received within")
      .isInstanceOf(classOf[WebPushTemporarilyUnavailableException])
  }

  @Test
  def pushShouldReadShortErrorResponses(): Unit = {
    assertThatThrownBy(() => push("/short-error"))
      .hasMessageContaining("short error")
      .isInstanceOf(classOf[WebPushTemporarilyUnavailableException])
  }
}
