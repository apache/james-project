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
import org.apache.james.jmap.model.Invocation.{MethodCallId, MethodName}
import org.apache.james.jmap.routes.{BackReference, JsonPath}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsError, JsSuccess, Json}


class BackReferenceTest extends AnyWordSpec with Matchers {
  "Deserialize backReference" should {
    "succeed" in {
      val jsonPath = JsonPath.parse("list/*/id")
      val expectedBackReference = BackReference(
        name = MethodName("Mailbox/get"),
        resultOf = MethodCallId("c1"),
        path = jsonPath)

      BackReferenceDeserializer.deserializeBackReference(
        Json.parse("""{
          |  "resultOf":"c1",
          |  "name":"Mailbox/get",
          |  "path":"list/*/id"
          |}""".stripMargin)) should equal(JsSuccess(expectedBackReference))
    }
  }

  "JsonPath evaluation" should {
    "noop when empty" in {
      val jsonPath = JsonPath.parse("")
      val json = Json.parse("""["array"]""")
      val expected = Json.parse("""["array"]""")

      jsonPath.evaluate(json) should equal(JsSuccess(expected))
    }
    "succeed when single path part is present" in {
      val jsonPath = JsonPath.parse("path")
      val json = Json.parse("""{"path" : ["array"]}""")
      val expected = Json.parse("""["array"]""")

      jsonPath.evaluate(json) should equal(JsSuccess(expected))
    }
    "succeed when array part is present" in {
      val jsonPath = JsonPath.parse("path/*")
      val json = Json.parse("""{"path" : ["1", "2"]}""")
      val expected = Json.parse("""["1", "2"]""")

      jsonPath.evaluate(json) should equal(JsSuccess(expected))
    }
    "fail when not an array" in {
      val jsonPath = JsonPath.parse("path/*")
      val json = Json.parse("""{"path" : {"key": "value"}}""")

      jsonPath.evaluate(json) shouldBe a[JsError]
    }
    "allow simple resolution within an array" in {
      val jsonPath = JsonPath.parse("path/*/key")
      val json = Json.parse(
        """{"path" : [
          |  {"key": "1"},
          |  {"key": "2"}
          |]}""".stripMargin)
      val expected = Json.parse("""["1", "2"]""")

      jsonPath.evaluate(json) should equal(JsSuccess(expected))
    }
    "allow recursive array resolution" in {
      val jsonPath = JsonPath.parse("path/*/key/*")
      val json = Json.parse(
        """{"path" : [
          |  {"key": ["1", "2"]},
          |  {"key": ["3"]}
          |]}""".stripMargin)
      val expected = Json.parse("""["1", "2", "3"]""")

      jsonPath.evaluate(json) should equal(JsSuccess(expected))
    }
    "succeed when double path part is present" in {
      val jsonPath = JsonPath.parse("path/second")
      val json = Json.parse(
        """{
          |  "path" : {
          |    "second" : {"key": "value"}
          |  }
          |}""".stripMargin)
      val expected = Json.parse("""{"key": "value"}""")

      jsonPath.evaluate(json) should equal(JsSuccess(expected))
    }
    "succeed double delimiter" in {
      val jsonPath = JsonPath.parse("path//second")
      val json = Json.parse(
        """{
          |  "path" : {
          |    "second" : {"key": "value"}
          |  }
          |}""".stripMargin)
      val expected = Json.parse("""{"key": "value"}""")

      jsonPath.evaluate(json) should equal(JsSuccess(expected))
    }
    "accept starting delimiter" in {
      val jsonPath = JsonPath.parse("/path")
      val json = Json.parse("""{"path" : ["array"]}""")
      val expected = Json.parse("""["array"]""")

      jsonPath.evaluate(json) should equal(JsSuccess(expected))
    }
    "accept ending delimiter" in {
      val jsonPath = JsonPath.parse("path/")
      val json = Json.parse("""{"path" : ["array"]}""")
      val expected = Json.parse("""["array"]""")

      jsonPath.evaluate(json) should equal(JsSuccess(expected))
    }
    "fail when single path part is missing" in {
      val jsonPath = JsonPath.parse("other")
      val json = Json.parse("""{"path" : ["array"]}""")

      jsonPath.evaluate(json) shouldBe a[JsError]
    }
    "fail when single path part is present in an array" in {
      val jsonPath = JsonPath.parse("other")
      val json = Json.parse("""[{"path" : "array"}]""")

      jsonPath.evaluate(json) shouldBe a[JsError]
    }
    "fail when single path part requested on a value" in {
      val jsonPath = JsonPath.parse("other")
      val json = Json.parse(""""value"""")

      jsonPath.evaluate(json) shouldBe a[JsError]
    }
  }
}
