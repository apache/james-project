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

import org.apache.james.jmap.api.model.{PushSubscriptionId, PushSubscriptionServerURL, VerificationCode}
import org.apache.james.jmap.json.PushSerializer
import org.apache.james.jmap.method.{PushSubscriptionSetCreateProcessor, PushVerification}
import org.apache.james.jmap.pushsubscription.PushSubscriptionSetCreateProcessorTest.PUSH_VERIFICATION_SAMPLE
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.{BeforeEach, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.NottableString.string
import org.mockserver.verify.VerificationTimes
import play.api.libs.json.Json

import java.net.URL
import java.util.UUID

object PushSubscriptionSetCreateProcessorTest {
  val PUSH_VERIFICATION_SAMPLE: PushVerification = PushVerification(
    PushSubscriptionId(UUID.fromString("44111166-affc-4187-b974-0672e312b72e")),
    VerificationCode("2b295d19-b37a-4865-b93e-bbb59f76ffc0"))
}

@ExtendWith(Array(classOf[PushServerExtension]))
class PushSubscriptionSetCreateProcessorTest {

  var testee: PushSubscriptionSetCreateProcessor = _
  var pushServerUrl: PushSubscriptionServerURL = _

  @BeforeEach
  def setup(pushServer: ClientAndServer): Unit = {
    val webPushClient: WebPushClient = new DefaultWebPushClient(WebPushClientTestFixture.PUSH_CLIENT_CONFIGURATION)
    testee = new PushSubscriptionSetCreateProcessor(webPushClient)
    pushServerUrl = PushSubscriptionServerURL(new URL(s"http://127.0.0.1:${pushServer.getLocalPort}/subscribe"))

    pushServer
      .when(request
        .withPath("/subscribe")
        .withMethod("POST")
        .withHeader(string("Content-type"), string("application/json charset=utf-8")))
      .respond(response
        .withStatusCode(201))
  }

  @Test
  def pushVerificationShouldSuccess(pushServer: ClientAndServer): Unit = {
    testee.pushVerificationToPushServer(pushServerUrl, PUSH_VERIFICATION_SAMPLE).block()

    pushServer.verify(request()
      .withPath("/subscribe")
      .withBody(Json.stringify(PushSerializer.serializePushVerification(PUSH_VERIFICATION_SAMPLE))),
      VerificationTimes.atLeast(1))
  }
}
