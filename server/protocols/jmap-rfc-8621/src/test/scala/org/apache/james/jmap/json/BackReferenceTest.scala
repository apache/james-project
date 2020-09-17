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
import org.apache.james.jmap.routes.{ArrayElementPart, BackReference, JsonPath, PlainPart}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsError, JsString, JsSuccess, Json}


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

  "Array element parsing" should {
    "succeed when poositive" in {
      ArrayElementPart.parse("[1]") should equal(Some(ArrayElementPart(1)))
    }
    "succeed when zero" in {
      ArrayElementPart.parse("[0]") should equal(Some(ArrayElementPart(0)))
    }
    "fail when negative" in {
      ArrayElementPart.parse("[-1]") should equal(None)
    }
    "fail when not an int" in {
      ArrayElementPart.parse("[invalid]") should equal(None)
    }
    "fail when not closed" in {
      ArrayElementPart.parse("[0") should equal(None)
    }
    "fail when not open" in {
      ArrayElementPart.parse("0]") should equal(None)
    }
    "fail when no bracket" in {
      ArrayElementPart.parse("0") should equal(None)
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
    "succeed when array element is present and root" in {
      val jsonPath = JsonPath.parse("[1]")
      val json = Json.parse("""["1", "2", "3"]""")
      val expected = JsString("2")

      jsonPath.evaluate(json) should equal(JsSuccess(expected))
    }
    "succeed when first array element is present" in {
      val jsonPath = JsonPath.parse("path[0]")
      val json = Json.parse("""{"path" : ["1", "2", "3"]}""")
      val expected = JsString("1")

      jsonPath.evaluate(json) should equal(JsSuccess(expected))
    }
    "fail when overflow" in {
      val jsonPath = JsonPath.parse("path[3]")
      val json = Json.parse("""{"path" : ["1", "2", "3"]}""")

      jsonPath.evaluate(json) shouldBe a[JsError]
    }
    "parse should default to plain part when negative" in {
      JsonPath.parse("path[-1]") should equal(JsonPath(List(PlainPart("path[-1]"))))
    }
    "parse should default to plain part when not an int" in {
      JsonPath.parse("path[invalid]") should equal(JsonPath(List(PlainPart("path[invalid]"))))
    }
    "parse should default to plain part when empty" in {
      JsonPath.parse("path[]") should equal(JsonPath(List(PlainPart("path[]"))))
    }
    "parse should default to plain part when not closed" in {
      JsonPath.parse("path[36") should equal(JsonPath(List(PlainPart("path[36"))))
    }
    "parse should default to plain part when not closed and root" in {
      JsonPath.parse("[36") should equal(JsonPath(List(PlainPart("[36"))))
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
