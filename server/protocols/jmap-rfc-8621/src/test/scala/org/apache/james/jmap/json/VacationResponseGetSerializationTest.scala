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

import eu.timepit.refined.auto._
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.james.jmap.json.Fixture.id
import org.apache.james.jmap.json.VacationResponseGetSerializationTest.{ACCOUNT_ID, PROPERTIES, SINGLETON_ID}
import org.apache.james.jmap.json.VacationResponseSerializationTest.VACATION_RESPONSE
import org.apache.james.jmap.mail.VacationResponse.UnparsedVacationResponseId
import org.apache.james.jmap.mail.{VacationResponse, VacationResponseGetRequest, VacationResponseGetResponse, VacationResponseIds, VacationResponseNotFound}
import org.apache.james.jmap.model.{AccountId, Properties}
import org.apache.james.mailbox.model.{MailboxId, TestId}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsSuccess, Json}

object VacationResponseGetSerializationTest {
  private val FACTORY: MailboxId.Factory = new TestId.Factory

  private val ACCOUNT_ID: AccountId = AccountId(id)

  private val SINGLETON_ID: UnparsedVacationResponseId = "singleton"
  private val PROPERTIES: Properties = Properties("isEnabled", "fromDate")
}

class VacationResponseGetSerializationTest extends AnyWordSpec with Matchers {
  "Deserialize VacationResponseGetRequest" should {
    "succeed on invalid VacationResponseId" in {
      val expectedRequestObject = VacationResponseGetRequest(
        accountId = ACCOUNT_ID,
        ids = Some(VacationResponseIds(List("invalid"))),
        properties = None)

      VacationSerializer.deserializeVacationResponseGetRequest(
        """
          |{
          |  "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |  "ids": ["invalid"]
          |}
          |""".stripMargin) should equal(JsSuccess(expectedRequestObject))
    }

    "succeed when properties are missing" in {
      val expectedRequestObject = VacationResponseGetRequest(
        accountId = ACCOUNT_ID,
        ids = Some(VacationResponseIds(List(SINGLETON_ID))),
        properties = None)

      VacationSerializer.deserializeVacationResponseGetRequest(
        """
          |{
          |  "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |  "ids": ["singleton"]
          |}
          |""".stripMargin) should equal(JsSuccess(expectedRequestObject))
    }

    "succeed when properties are null" in {
      val expectedRequestObject = VacationResponseGetRequest(
        accountId = ACCOUNT_ID,
        ids = Some(VacationResponseIds(List(SINGLETON_ID))),
        properties = None)

      VacationSerializer.deserializeVacationResponseGetRequest(
        """
          |{
          |  "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |  "ids": ["singleton"],
          |  "properties": null
          |}
          |""".stripMargin) should equal(JsSuccess(expectedRequestObject))
    }

    "succeed when properties are empty" in {
      val expectedRequestObject = VacationResponseGetRequest(
        accountId = ACCOUNT_ID,
        ids = Some(VacationResponseIds(List(SINGLETON_ID))),
        properties = Some(Properties.empty()))

      VacationSerializer.deserializeVacationResponseGetRequest(
        """
          |{
          |  "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |  "ids": ["singleton"],
          |  "properties": []
          |}
          |""".stripMargin) should equal(JsSuccess(expectedRequestObject))
    }

    "succeed when ids is empty" in {
      val expectedRequestObject = VacationResponseGetRequest(
        accountId = ACCOUNT_ID,
        ids = Some(VacationResponseIds(Nil)),
        properties = None)

      VacationSerializer.deserializeVacationResponseGetRequest(
        """
          |{
          |  "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |  "ids": []
          |}
          |""".stripMargin) should equal(JsSuccess(expectedRequestObject))
    }

    "succeed when ids is null" in {
      val expectedRequestObject = VacationResponseGetRequest(
        accountId = ACCOUNT_ID,
        ids = None,
        properties = None)

      VacationSerializer.deserializeVacationResponseGetRequest(
        """
          |{
          |  "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |  "ids": null
          |}
          |""".stripMargin) should equal(JsSuccess(expectedRequestObject))
    }

    "succeed when multiple ids" in {
      val expectedRequestObject = VacationResponseGetRequest(
        accountId = ACCOUNT_ID,
        ids = Some(VacationResponseIds(List(SINGLETON_ID, "randomId"))),
        properties = Some(PROPERTIES))

      VacationSerializer.deserializeVacationResponseGetRequest(
        """
          |{
          |  "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |  "ids": ["singleton", "randomId"],
          |  "properties": ["isEnabled", "fromDate"]
          |}
          |""".stripMargin) should equal(JsSuccess(expectedRequestObject))
    }
  }

  "Serialize VacationResponseGetResponse" should {
    "succeed" in {
      val actualValue: VacationResponseGetResponse = VacationResponseGetResponse(
        accountId = ACCOUNT_ID,
        state = "75128aab4b1b",
        list = List(VACATION_RESPONSE),
        notFound = VacationResponseNotFound(Set("randomId1", "randomId2")))

      val expectedJson: String =
        """
          |{
          |  "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |  "state": "75128aab4b1b",
          |  "list": [{
          |    "id":"singleton",
          |    "isEnabled":true,
          |    "fromDate":"2016-10-09T01:07:06Z",
          |    "toDate":"2017-10-09T01:07:06Z",
          |    "subject":"Hello world",
          |    "textBody":"text is required when enabled",
          |    "htmlBody":"<b>HTML body</b>"
          |  }],
          |  "notFound": ["randomId1", "randomId2"]
          |}
          |""".stripMargin

      assertThatJson(Json.stringify(VacationSerializer.serialize(actualValue)(VacationSerializer.vacationResponseWrites(VacationResponse.allProperties)))).isEqualTo(expectedJson)
    }
  }
}
