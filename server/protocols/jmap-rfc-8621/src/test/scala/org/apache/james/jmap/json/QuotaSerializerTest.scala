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
import org.apache.james.jmap.core.{AccountId, CanCalculateChanges, Id, Limit, Position, Properties, QueryState, UnsignedInt, UuidState}
import org.apache.james.jmap.json.Fixture.id
import org.apache.james.jmap.json.QuotaSerializerTest.ACCOUNT_ID
import org.apache.james.jmap.mail.{AccountScope, CountResourceType, JmapQuota, MailDataType, OctetsResourceType, QuotaDescription, QuotaGetRequest, QuotaGetResponse, QuotaIds, QuotaName, QuotaNotFound, QuotaQueryResponse, UnparsedQuotaId}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsSuccess, Json}

object QuotaSerializerTest {
  private val ACCOUNT_ID: AccountId = AccountId(id)
}

class QuotaSerializerTest extends AnyWordSpec with Matchers {

  "Deserialize QuotaGetRequest" should {
    "succeed when properties are missing" in {
      val expectedRequestObject = QuotaGetRequest(
        accountId = ACCOUNT_ID,
        ids = Some(QuotaIds(List(UnparsedQuotaId("singleton")))),
        properties = None)

      QuotaSerializer.deserializeQuotaGetRequest(
        """
          |{
          |  "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |  "ids": ["singleton"]
          |}
          |""".stripMargin) should equal(JsSuccess(expectedRequestObject))
    }

    "succeed when properties are null" in {
      val expectedRequestObject = QuotaGetRequest(
        accountId = ACCOUNT_ID,
        ids = Some(QuotaIds(List(UnparsedQuotaId("singleton")))),
        properties = None)

      QuotaSerializer.deserializeQuotaGetRequest(
        """
          |{
          |  "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |  "ids": ["singleton"],
          |  "properties": null
          |}
          |""".stripMargin) should equal(JsSuccess(expectedRequestObject))
    }

    "succeed when properties are empty" in {
      val expectedRequestObject = QuotaGetRequest(
        accountId = ACCOUNT_ID,
        ids = Some(QuotaIds(List(UnparsedQuotaId("singleton")))),
        properties = Some(Properties.empty()))

      QuotaSerializer.deserializeQuotaGetRequest(
        """
          |{
          |  "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |  "ids": ["singleton"],
          |  "properties": []
          |}
          |""".stripMargin) should equal(JsSuccess(expectedRequestObject))
    }

    "succeed when ids is null" in {
      val expectedRequestObject = QuotaGetRequest(
        accountId = ACCOUNT_ID,
        ids = None,
        properties = None)

      QuotaSerializer.deserializeQuotaGetRequest(
        """
          |{
          |  "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |  "ids": null
          |}
          |""".stripMargin) should equal(JsSuccess(expectedRequestObject))
    }

    "succeed when multiple ids" in {
      val expectedRequestObject = QuotaGetRequest(
        accountId = ACCOUNT_ID,
        ids = Some(QuotaIds(List(UnparsedQuotaId("singleton"), UnparsedQuotaId("randomId")))),
        properties = Some(Properties.empty()))

      QuotaSerializer.deserializeQuotaGetRequest(
        """
          |{
          |  "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |  "ids": ["singleton", "randomId"],
          |  "properties": []
          |}
          |""".stripMargin) should equal(JsSuccess(expectedRequestObject))
    }
  }

  "Serialize QuotaGetResponse" should {
    "succeed" in {
      val jmapQuota: JmapQuota = JmapQuota(
        id = Id.validate("aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8").toOption.get,
        resourceType = CountResourceType,
        used = UnsignedInt.liftOrThrow(1),
        hardLimit = UnsignedInt.liftOrThrow(2),
        scope = AccountScope,
        name = QuotaName("name1"),
        types = List(MailDataType),
        warnLimit = Some(UnsignedInt.liftOrThrow(123)),
        softLimit = Some(UnsignedInt.liftOrThrow(456)),
        description = Some(QuotaDescription("Description 1")))

      val actualValue: QuotaGetResponse = QuotaGetResponse(
        accountId = ACCOUNT_ID,
        state = UuidState.INSTANCE,
        list = List(jmapQuota),
        notFound = QuotaNotFound(Set(UnparsedQuotaId("notfound2"))))

      val expectedJson: String =
        """{
          |    "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |    "state": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
          |    "list": [
          |        {
          |            "id": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |            "resourceType": "count",
          |            "used": 1,
          |            "hardLimit": 2,
          |            "scope": "account",
          |            "name": "name1",
          |            "types": [
          |                "Mail"
          |            ],
          |            "warnLimit": 123,
          |            "softLimit": 456,
          |            "description": "Description 1"
          |        }
          |    ],
          |    "notFound": [
          |        "notfound2"
          |    ]
          |}""".stripMargin

      assertThatJson(Json.stringify(QuotaSerializer.serialize(actualValue))).isEqualTo(expectedJson)
    }

    "succeed when draft compatibility" in {
      val jmapQuota: JmapQuota = JmapQuota(
        id = Id.validate("aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8").toOption.get,
        resourceType = CountResourceType,
        used = UnsignedInt.liftOrThrow(1),
        hardLimit = UnsignedInt.liftOrThrow(2),
        limit = Some(UnsignedInt.liftOrThrow(2)),
        scope = AccountScope,
        name = QuotaName("name1"),
        types = List(MailDataType),
        dataTypes = Some(List(MailDataType)),
        warnLimit = Some(UnsignedInt.liftOrThrow(123)),
        softLimit = Some(UnsignedInt.liftOrThrow(456)),
        description = Some(QuotaDescription("Description 1")))

      val actualValue: QuotaGetResponse = QuotaGetResponse(
        accountId = ACCOUNT_ID,
        state = UuidState.INSTANCE,
        list = List(jmapQuota),
        notFound = QuotaNotFound(Set(UnparsedQuotaId("notfound2"))))

      val expectedJson: String =
        """{
          |    "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |    "state": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
          |    "list": [
          |        {
          |            "id": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |            "resourceType": "count",
          |            "used": 1,
          |            "hardLimit": 2,
          |            "limit": 2,
          |            "scope": "account",
          |            "name": "name1",
          |            "types": [ "Mail" ],
          |            "dataTypes": [ "Mail" ],
          |            "warnLimit": 123,
          |            "softLimit": 456,
          |            "description": "Description 1"
          |        }
          |    ],
          |    "notFound": [
          |        "notfound2"
          |    ]
          |}""".stripMargin

      assertThatJson(Json.stringify(QuotaSerializer.serialize(actualValue))).isEqualTo(expectedJson)
    }

    "succeed when list has multiple quota" in {
      val jmapQuota: JmapQuota = JmapQuota(
        id = Id.validate("aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8").toOption.get,
        resourceType = CountResourceType,
        used = UnsignedInt.liftOrThrow(1),
        hardLimit = UnsignedInt.liftOrThrow(2),
        scope = AccountScope,
        name = QuotaName("name1"),
        types = List(MailDataType),
        description = None)

      val jmapQuota2 = jmapQuota.copy(id = Id.validate("aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy9").toOption.get,
        resourceType = OctetsResourceType,
        scope = AccountScope,
        name = QuotaName("name2"))

      val actualValue: QuotaGetResponse = QuotaGetResponse(
        accountId = ACCOUNT_ID,
        state = UuidState.INSTANCE,
        list = List(jmapQuota, jmapQuota2),
        notFound = QuotaNotFound(Set()))

      val expectedJson: String =
        """{
          |    "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |    "state": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
          |    "list": [
          |        {
          |            "id": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |            "resourceType": "count",
          |            "used": 1,
          |            "hardLimit": 2,
          |            "scope": "account",
          |            "name": "name1",
          |            "types": [
          |                "Mail"
          |            ]
          |        },
          |        {
          |            "id": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy9",
          |            "resourceType": "octets",
          |            "used": 1,
          |            "hardLimit": 2,
          |            "scope": "account",
          |            "name": "name2",
          |            "types": [
          |                "Mail"
          |            ]
          |        }
          |    ],
          |    "notFound": []
          |}""".stripMargin

      assertThatJson(Json.stringify(QuotaSerializer.serialize(actualValue))).isEqualTo(expectedJson)
    }
  }

  "Serialize QuotaQueryResponse" should {
    "succeed" in {
      val quotaQueryResponse: QuotaQueryResponse = QuotaQueryResponse(accountId = ACCOUNT_ID,
        queryState = QueryState.forStrings(Seq()),
        canCalculateChanges = CanCalculateChanges(false),
        ids = Id.validate("id1").toSeq,
        position = Position.zero,
        limit = Some(Limit.default))

      val expectedJson: String =
        """{
          |    "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |    "queryState": "00000000",
          |    "canCalculateChanges": false,
          |    "ids": [
          |        "id1"
          |    ],
          |    "position": 0,
          |    "limit": 256
          |}""".stripMargin

      assertThatJson(Json.stringify(QuotaSerializer.serializeQuery(quotaQueryResponse))).isEqualTo(expectedJson)
    }
  }
}
