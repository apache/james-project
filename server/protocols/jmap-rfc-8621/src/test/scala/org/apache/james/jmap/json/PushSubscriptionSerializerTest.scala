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

import java.time.ZonedDateTime
import java.util.UUID

import com.google.common.collect.ImmutableSet
import eu.timepit.refined.auto._
import org.apache.james.jmap.api.change.TypeStateFactory
import org.apache.james.jmap.api.model.{PushSubscriptionCreationRequest, PushSubscriptionExpiredTime, PushSubscriptionId}
import org.apache.james.jmap.change.{EmailTypeName, MailboxTypeName}
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{PushSubscriptionCreationId, PushSubscriptionCreationResponse, PushSubscriptionSetRequest, PushSubscriptionSetResponse, SetError}
import org.apache.james.jmap.json.PushSubscriptionSerializerTest.{PUSH_SUBSCRIPTION_CREATED_ID_1, PUSH_SUBSCRIPTION_CREATED_ID_2, PUSH_SUBSCRIPTION_NOT_CREATED_ID_1, PUSH_SUBSCRIPTION_NOT_CREATED_ID_2}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, JsResult, Json}

object PushSubscriptionSerializerTest {
  lazy val PUSH_SUBSCRIPTION_CREATED_ID_1: PushSubscriptionCreationId = PushSubscriptionCreationId("created1")
  lazy val PUSH_SUBSCRIPTION_CREATED_ID_2: PushSubscriptionCreationId = PushSubscriptionCreationId("created2")
  lazy val PUSH_SUBSCRIPTION_NOT_CREATED_ID_1: PushSubscriptionCreationId = PushSubscriptionCreationId("notCreated1")
  lazy val PUSH_SUBSCRIPTION_NOT_CREATED_ID_2: PushSubscriptionCreationId = PushSubscriptionCreationId("notCreated2")
}

class PushSubscriptionSerializerTest extends AnyWordSpec with Matchers {
  val serializer = new PushSubscriptionSerializer(TypeStateFactory(ImmutableSet.of(MailboxTypeName, EmailTypeName)))

  "Deserialize PushSubscriptionSetRequest" should {
    "Request should be success" in {
      val setRequestActual: JsResult[PushSubscriptionSetRequest] = serializer.deserializePushSubscriptionSetRequest(
        Json.parse(
          """{
            |    "create": {
            |        "4f29": {
            |            "deviceClientId": "a889-ffea-910",
            |            "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
            |            "types": null
            |        }
            |    }
            |}""".stripMargin))

      assert(setRequestActual.isSuccess)
    }
  }

  "Deserialize PushSubscriptionCreationRequest" should {
    "Request should be success" in {
      val setRequestActual: JsResult[PushSubscriptionCreationRequest] = serializer.deserializePushSubscriptionCreationRequest(
        Json.parse(
          """{
            |    "deviceClientId": "a889-ffea-910",
            |    "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
            |    "types": []
            |}""".stripMargin))

      assert(setRequestActual.isSuccess)
    }

    "Request should accept expires property" in {
      val setRequestActual: JsResult[PushSubscriptionCreationRequest] = serializer.deserializePushSubscriptionCreationRequest(
        Json.parse(
          """{
            |    "deviceClientId": "a889-ffea-910",
            |    "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
            |    "types": [],
            |    "expires": "2017-07-19T14:30:00Z"
            |}""".stripMargin))

      assert(setRequestActual.isSuccess)
    }
  }

  "Serialize PushSubscriptionSetResponse" should {
    "PushSubscriptionSetResponse should be success" in {
      val createdMap: Map[PushSubscriptionCreationId, PushSubscriptionCreationResponse] = Map(
        PUSH_SUBSCRIPTION_CREATED_ID_1 -> PushSubscriptionCreationResponse(
          id = PushSubscriptionId(UUID.fromString("6e0dd59d-660e-4d9b-b22f-0354479f47b4")),
          expires = Some(PushSubscriptionExpiredTime(ZonedDateTime.parse("2016-07-19T14:30:00Z")))),

        PUSH_SUBSCRIPTION_CREATED_ID_2 -> PushSubscriptionCreationResponse(
          id = PushSubscriptionId(UUID.fromString("6e0dd59d-660e-4d9b-b22f-0354479f47b5")),
          expires = Some(PushSubscriptionExpiredTime(ZonedDateTime.parse("2017-07-19T14:30:00Z")))))

      val notCreatedMap: Map[PushSubscriptionCreationId, SetError] = Map(
        PUSH_SUBSCRIPTION_NOT_CREATED_ID_1 -> SetError.serverFail(SetErrorDescription("error1")),
        PUSH_SUBSCRIPTION_NOT_CREATED_ID_2 -> SetError.serverFail(SetErrorDescription("error2")))

      val response: PushSubscriptionSetResponse = PushSubscriptionSetResponse(created = Some(createdMap), notCreated = Some(notCreatedMap),
        updated = None, notUpdated = None)

      val actualValue: JsObject = serializer.serialize(response)

      val expectedValue: JsObject = Json.parse(
        """{
          |    "created": {
          |        "created1": {
          |            "id": "6e0dd59d-660e-4d9b-b22f-0354479f47b4",
          |            "expires": "2016-07-19T14:30:00Z"
          |        },
          |        "created2": {
          |            "id": "6e0dd59d-660e-4d9b-b22f-0354479f47b5",
          |            "expires": "2017-07-19T14:30:00Z"
          |        }
          |    },
          |    "notCreated": {
          |        "notCreated1": {
          |            "type": "serverFail",
          |            "description": "error1"
          |        },
          |        "notCreated2": {
          |            "type": "serverFail",
          |            "description": "error2"
          |        }
          |    }
          |}""".stripMargin).asInstanceOf[JsObject]

      actualValue should equal(expectedValue)
    }
  }

}
