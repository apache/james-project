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

import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.james.mailbox.model.TestId
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsError, JsPath, Json, JsonValidationError}

object JsErrorSerializationTest {
  private val SERIALIZER: Serializer = new Serializer(new TestId.Factory)
}

class JsErrorSerializationTest extends AnyWordSpec with Matchers {
  import JsErrorSerializationTest.SERIALIZER

  "Serialize JsError" should {
    "succeed " in {
      val errors: JsError = JsError(Seq(
        (
          JsPath.\("name"),
          Seq(JsonValidationError("validate.error.expected.jsstring"), JsonValidationError("error.maxLength"))
        ),
        (
          JsPath.\("id"),
          Seq(JsonValidationError("error.path.missing"))
        )))

      val expectedJson: String =
        """
          |{
          |  "errors": [
          |    {
          |      "path": "obj.name",
          |      "messages": ["validate.error.expected.jsstring", "error.maxLength"]
          |    },
          |    {
          |      "path": "obj.id",
          |      "messages": ["error.path.missing"]
          |    }
          |  ]
          |}
          |""".stripMargin

      assertThatJson(Json.stringify(SERIALIZER.serialize(errors))).isEqualTo(expectedJson)
    }
  }
}
