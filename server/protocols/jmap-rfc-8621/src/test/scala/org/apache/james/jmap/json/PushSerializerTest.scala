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

package org.apache.james.jmap.json

import java.util.UUID

import org.apache.james.jmap.api.model.{PushSubscriptionId, VerificationCode}
import org.apache.james.jmap.method.PushVerification
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsValue, Json}

class PushSerializerTest extends AnyWordSpec with Matchers {

  "Serialize PushVerification" should {
    "PushVerification should be success" in {
      val pushVerification: PushVerification = PushVerification(
        PushSubscriptionId(UUID.fromString("44111166-affc-4187-b974-0672e312b72e")),
        VerificationCode("2b295d19-b37a-4865-b93e-bbb59f76ffc0"))

      val actualValue: JsValue = PushSerializer.serializePushVerification(pushVerification)

      val expectedValue: JsValue = Json.parse(
        """{
          |	"@type": "PushVerification",
          |	"pushSubscriptionId": "44111166-affc-4187-b974-0672e312b72e",
          |	"verificationCode": "2b295d19-b37a-4865-b93e-bbb59f76ffc0"
          |}""".stripMargin)

      actualValue should equal(expectedValue)
    }
  }
}
