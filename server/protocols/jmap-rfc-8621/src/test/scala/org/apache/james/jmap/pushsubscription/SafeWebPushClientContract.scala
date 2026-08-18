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

import java.net.URI
import java.nio.charset.StandardCharsets

import org.apache.james.jmap.api.model.PushSubscriptionServerURL
import org.apache.james.jmap.pushsubscription.WebPushClientTestFixture.PUSH_REQUEST_SAMPLE
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import reactor.core.publisher.Mono

object SafeWebPushClientTestFixture {
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

trait SafeWebPushClientContract {
  def testee: WebPushClient

  @ParameterizedTest
  @ValueSource(strings = Array(
    "127.0.0.1", "127.0.0.9", "10.9.0.3", "192.168.102.35",
    // The wildcard address reaches local services and is covered by none of the JDK predicates
    "0.0.0.0", "[::]",
    "[::1]", "169.254.169.254", "224.0.0.1", "255.255.255.255", "100.64.0.1",
    // IPv6 unique local addresses, which hold the IPv6 cloud metadata endpoint
    "[fc00::1]", "[fd00::1]", "[fd00:ec2::254]",
    // IPv6 addresses embedding a forbidden IPv4 one
    "[::127.0.0.1]", "[64:ff9b::7f00:1]", "[2002:7f00:1::1]"))
  def serverSideRequestForgeryAttemptsShouldBeRejected(ip: String): Unit = {
    assertThatThrownBy(() => Mono.from(testee.push(PushSubscriptionServerURL(new URI(s"http://$ip").toURL), PUSH_REQUEST_SAMPLE)).block)
      .isInstanceOf(classOf[IllegalArgumentException])
  }

  @Test
  def pushShouldRejectNonHttpSchemes(): Unit = {
    assertThatThrownBy(() => Mono.from(testee.push(PushSubscriptionServerURL(new URI("file:///etc/passwd").toURL), PUSH_REQUEST_SAMPLE)).block)
      .isInstanceOf(classOf[IllegalArgumentException])
  }
}


