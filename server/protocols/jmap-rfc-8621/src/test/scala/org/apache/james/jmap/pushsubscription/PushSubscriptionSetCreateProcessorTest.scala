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
import java.security.KeyPair
import java.security.interfaces.ECPublicKey
import java.util.{Base64, UUID}

import com.google.crypto.tink.subtle.EllipticCurves
import com.google.crypto.tink.subtle.EllipticCurves.CurveType
import org.apache.james.jmap.api.model.{PushSubscriptionId, PushSubscriptionKeys, PushSubscriptionServerURL, VerificationCode}
import org.apache.james.jmap.method.{PushSubscriptionSetCreateProcessor, PushVerification}
import org.apache.james.jmap.pushsubscription.PushSubscriptionSetCreateProcessorTest.PUSH_VERIFICATION_SAMPLE
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.{BeforeEach, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.JsonBody.json
import org.mockserver.model.Not.not
import org.mockserver.model.NottableString.string
import org.mockserver.verify.VerificationTimes

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
    pushServerUrl = PushSubscriptionServerURL(new URI(s"http://127.0.0.1:${pushServer.getLocalPort}/subscribe").toURL)

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
    testee.pushVerificationToPushServer(pushServerUrl, PUSH_VERIFICATION_SAMPLE, None).block()
    pushServer.verify(request()
      .withPath("/subscribe")
      .withBody(json("""{
                       |    "@type": "PushVerification",
                       |    "pushSubscriptionId": "44111166-affc-4187-b974-0672e312b72e",
                       |    "verificationCode": "2b295d19-b37a-4865-b93e-bbb59f76ffc0"
                       |}""".stripMargin)),
      VerificationTimes.atLeast(1))
  }

  @Test
  def pushVerificationShouldEncryptedPayloadWhenAssignKeys(pushServer: ClientAndServer): Unit = {
    val uaKeyPair: KeyPair = EllipticCurves.generateKeyPair(CurveType.NIST_P256)
    val uaPublicKey: ECPublicKey = uaKeyPair.getPublic.asInstanceOf[ECPublicKey]
    val authSecret: Array[Byte] = "secret123secret1".getBytes

    val p256dh: String = Base64.getUrlEncoder.encodeToString(uaPublicKey.getEncoded)
    val auth: String = Base64.getUrlEncoder.encodeToString(authSecret)

    testee.pushVerificationToPushServer(pushServerUrl, PUSH_VERIFICATION_SAMPLE, Some(PushSubscriptionKeys(p256dh, auth))).block()
    pushServer.verify(request()
      .withPath("/subscribe")
      .withBody(not(json("""{
                       |    "@type": "PushVerification",
                       |    "pushSubscriptionId": "44111166-affc-4187-b974-0672e312b72e",
                       |    "verificationCode": "2b295d19-b37a-4865-b93e-bbb59f76ffc0"
                       |}""".stripMargin))),
      VerificationTimes.atLeast(1))
  }
}
