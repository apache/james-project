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

package org.apache.james.jmap.routes

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsError, JsSuccess, Json}

class JsonPathEdgeCasesTest extends AnyWordSpec with Matchers {
  "JsonPath wildcard evaluation" should {
    "return an empty array when no elements need child resolution" in {
      JsonPath.parse("list/*/id").evaluate(Json.obj("list" -> Json.arr())) shouldBe JsSuccess(Json.arr())
    }

    "propagate a missing child from one wildcard element" in {
      val json = Json.obj("list" -> Json.arr(Json.obj("id" -> "first"), Json.obj("name" -> "missing id")))

      JsonPath.parse("list/*/id").evaluate(json) shouldBe a[JsError]
    }

    "reject an object where a nested array index is requested" in {
      val json = Json.obj("list" -> Json.arr(Json.obj("children" -> Json.obj("id" -> "first"))))

      JsonPath.parse("list/*/children/0/id").evaluate(json) shouldBe a[JsError]
    }
  }
}
