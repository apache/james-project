/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/
package org.apache.james.jmap.json

import eu.timepit.refined.auto._
import org.apache.james.jmap.json.CreatedIds._
import org.apache.james.jmap.model.CreatedIds
import org.apache.james.jmap.model.CreatedIds.{ClientId, ServerId}
import org.apache.james.jmap.model.Id.Id
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsError, JsString, JsSuccess, Json}

class CreatedIdsTest extends PlaySpec {
  private val id: Id = "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8"

  "Deserialize ClientId" must {
    "succeed with JsString" in {
      val expectedClientId: ClientId = ClientId(id)

      Json.fromJson[ClientId](Json.toJson[Id](id)) === expectedClientId
    }
  }

  "Serialize ClientId" must {
    "succeed" in {
      val clientId: ClientId = ClientId(id)
      val expectedValue = "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8"

      Json.toJson[ClientId](clientId) === expectedValue
    }
  }

  "Deserialize ServerId" must {
    "succeed with JsString" in {
      val expectedServerId: ServerId = ServerId(id)

      Json.fromJson[ServerId](Json.toJson[Id](id)) must be (JsSuccess(expectedServerId))
    }
  }

  "Serialize ServerId" must {
    "succeed" in {
      val serverId: ServerId = ServerId(id)
      val expectedValue = "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8"

      Json.toJson[ServerId](serverId) === expectedValue
    }
  }

  "Deserialize CreatedIds" must {
    "succeed with a Map with value" in {
      val jsonMapValue: String = """{"aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8":"aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8"}"""
      val mapValue: Map[ClientId, ServerId] = Map(ClientId(id) -> ServerId(id))
      val expectedValue: CreatedIds = CreatedIds(mapValue)
      Json.fromJson[CreatedIds](Json.parse(jsonMapValue)) must be (JsSuccess(expectedValue))
    }

    "fail for null or empty value" in {
      Json.fromJson[CreatedIds](JsString("")) mustBe a[JsError]
      Json.fromJson[CreatedIds](JsString(null)) mustBe a[JsError]
    }

    "succeed with an empty Map" in {
      val jsonMapValue: String = """{}"""
      val mapValue: Map[ClientId, ServerId] = Map()
      val expectedValue: CreatedIds = CreatedIds(mapValue)
      Json.fromJson[CreatedIds](Json.parse(jsonMapValue)) must be (JsSuccess(expectedValue))
    }
  }

  "Serialize CreatedIds" must {
    "succeed with non empty map" in {
      val expectedValue: String = Json.prettyPrint(Json.parse(
      """{"aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8":"aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8"}"""))
      val mapValue: Map[ClientId, ServerId] = Map(
        ClientId(id) -> ServerId(id))

      val createdIds: CreatedIds = CreatedIds(mapValue)
      Json.prettyPrint(Json.toJson(createdIds)) must be(expectedValue)
    }
  }
}
