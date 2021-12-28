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

import java.net.URL
import java.nio.charset.StandardCharsets

import java.net.URL
import java.nio.charset.StandardCharsets

import org.apache.james.jmap.api.model.PushSubscriptionServerURL
import org.apache.james.jmap.pushsubscription.WebPushClientTestFixture.PUSH_REQUEST_SAMPLE
import org.assertj.core.api.Assertions.{assertThatCode, assertThatThrownBy}
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.verify.VerificationTimes
import reactor.core.scala.publisher.SMono

object WebPushClientTestFixture {
  val PUSH_CLIENT_CONFIGURATION: PushClientConfiguration =
    PushClientConfiguration(
      maxTimeoutSeconds = Some(10),
      maxConnections = Some(10))

  val PUSH_REQUEST_SAMPLE: PushRequest = PushRequest(
    ttl = PushTTL.validate(15).toOption.get,
    topic = PushTopic.validate("topicabc").toOption,
    urgency = Some(High),
    payload = "Content123".getBytes(StandardCharsets.UTF_8))
}

trait WebPushClientContract {
  def testee: WebPushClient

  def pushServerBaseUrl: URL

  private def getPushSubscriptionServerURL: PushSubscriptionServerURL =
    PushSubscriptionServerURL.from(s"${pushServerBaseUrl.toString}/push").get

  @Test
  def pushValidRequestShouldNotThrowException(): Unit = {
    assertThatCode(() => SMono.fromPublisher(testee.push(getPushSubscriptionServerURL, PUSH_REQUEST_SAMPLE))
      .block())
      .doesNotThrowAnyException()
  }

  @Test
  def pushValidRequestShouldHTTPCallToPushServer(pushServer: ClientAndServer): Unit = {
    SMono.fromPublisher(testee.push(getPushSubscriptionServerURL, PUSH_REQUEST_SAMPLE))
      .block()
    pushServer.verify(request()
      .withPath("/push")
      .withHeader("TTL", "15")
      .withHeader("Topic", "topicabc")
      .withHeader("Urgency", "High")
      .withBody(new String(PUSH_REQUEST_SAMPLE.payload, StandardCharsets.UTF_8)),
      VerificationTimes.atLeast(1))
  }

  @Test
  def pushShouldSpecifyContentCoding(pushServer: ClientAndServer): Unit = {
    SMono.fromPublisher(testee.push(getPushSubscriptionServerURL, PushRequest(
      ttl = PushTTL.validate(15).toOption.get,
      contentCoding = Some(Aes128gcm),
      payload = "Content123".getBytes(StandardCharsets.UTF_8))))
      .block()
    pushServer.verify(request()
      .withPath("/push")
      .withHeader("TTL", "15")
      .withHeader("Content-Encoding", "aes128gcm")
      .withBody(new String(PUSH_REQUEST_SAMPLE.payload, StandardCharsets.UTF_8)),
      VerificationTimes.atLeast(1))
  }

  @ParameterizedTest
  @ValueSource(ints = Array(500, 501, 502, 503, 504))
  def pushRequestShouldThrowWhenPushServerReturnFailHTTPStatus(httpErrorCode: Int, pushServer: ClientAndServer): Unit = {
    pushServer
      .when(request
        .withPath("/invalid"))
      .respond(response.withStatusCode(httpErrorCode))

    assertThatThrownBy(() => SMono.fromPublisher(
      testee.push(PushSubscriptionServerURL.from(s"${pushServerBaseUrl.toString}/invalid").get,
        PUSH_REQUEST_SAMPLE))
      .block())
      .isInstanceOf(classOf[WebPushTemporarilyUnavailableException])

    pushServer.verify(request()
      .withPath("/invalid"),
      VerificationTimes.atLeast(1))
  }

  @Test
  def pushRequestShouldParserErrorResponseFromPushServerWhenFail(pushServer: ClientAndServer): Unit = {
    pushServer
      .when(request
        .withPath("/invalid"))
      .respond(response
        .withStatusCode(500)
        .withBody("Request did not validate 123"))

    assertThatThrownBy(() => SMono.fromPublisher(
      testee.push(PushSubscriptionServerURL.from(s"${pushServerBaseUrl.toString}/invalid").get,
        PUSH_REQUEST_SAMPLE))
      .block())
      .hasMessageContaining("Request did not validate 123")
  }
}


