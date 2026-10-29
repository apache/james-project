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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsError, JsSuccess, Json}

class JsonPathEdgeCasesTest {
  @Test
  def wildcardEvaluationShouldReturnAnEmptyArrayWhenNoElementsNeedChildResolution(): Unit = {
    assertThat(JsonPath.parse("list/*/id").evaluate(Json.obj("list" -> Json.arr())))
      .isEqualTo(JsSuccess(Json.arr()))
  }

  @Test
  def wildcardEvaluationShouldPropagateAMissingChildFromOneWildcardElement(): Unit = {
    val json = Json.obj("list" -> Json.arr(Json.obj("id" -> "first"), Json.obj("name" -> "missing id")))

    assertThat(JsonPath.parse("list/*/id").evaluate(json)).isInstanceOf(classOf[JsError])
  }

  @Test
  def wildcardEvaluationShouldRejectAnObjectWhereANestedArrayIndexIsRequested(): Unit = {
    val json = Json.obj("list" -> Json.arr(Json.obj("children" -> Json.obj("id" -> "first"))))

    assertThat(JsonPath.parse("list/*/children/0/id").evaluate(json)).isInstanceOf(classOf[JsError])
  }

  @Test
  def jsonPathShouldDecodeAnEscapedTokenOnlyOnce(): Unit = {
    val json = Json.obj("a~1b" -> "once", "a/b" -> "twice", "a~01b" -> "encoded")

    assertThat(JsonPath.parse("/a~01b").evaluate(json)).isEqualTo(JsSuccess(Json.toJson("once")))
  }

  @Test
  def jsonPathShouldDecodeMultipleEscapesWithinOneToken(): Unit = {
    assertThat(JsonPath.parse("/a~1b~0c~1d~0e").evaluate(Json.obj("a/b~c/d~e" -> "value")))
      .isEqualTo(JsSuccess(Json.toJson("value")))
  }

  @Test
  def wildcardEvaluationShouldDecodeChildTokensAndFlattenTheirArrays(): Unit = {
    val json = Json.obj("list" -> Json.arr(Json.obj("a/b~c" -> Json.arr("first", "second")),
      Json.obj("a/b~c" -> Json.arr("third"))))

    assertThat(JsonPath.parse("/list/*/a~1b~0c").evaluate(json))
      .isEqualTo(JsSuccess(Json.arr("first", "second", "third")))
  }

  @Test
  def jsonPathShouldRejectAMissingDecodedPropertyEvenWhenTheEncodedPropertyExists(): Unit = {
    assertThat(JsonPath.parse("/a~1b").evaluate(Json.obj("a~1b" -> "encoded")))
      .isInstanceOf(classOf[JsError])
  }

  @Test
  def jsonPathShouldPreserveDistinctNumericObjectPropertyNames(): Unit = {
    val json = Json.obj("objects" -> Json.obj("01" -> "leading-zero", "1" -> "one"))

    assertThat(JsonPath.parse("/objects/01").evaluate(json)).isEqualTo(JsSuccess(Json.toJson("leading-zero")))
    assertThat(JsonPath.parse("/objects/1").evaluate(json)).isEqualTo(JsSuccess(Json.toJson("one")))
  }

  @Test
  def jsonPathShouldReadANumericObjectPropertyLargerThanAnInteger(): Unit = {
    assertThat(JsonPath.parse("/objects/2147483648").evaluate(Json.obj("objects" -> Json.obj("2147483648" -> "large"))))
      .isEqualTo(JsSuccess(Json.toJson("large")))
  }

  @Test
  def wildcardEvaluationShouldReadANumericPropertyWhenTheChildIsAnObject(): Unit = {
    val json = Json.obj("list" -> Json.arr(Json.obj("children" -> Json.obj("0" -> Json.obj("id" -> "first")))))

    assertThat(JsonPath.parse("/list/*/children/0/id").evaluate(json)).isEqualTo(JsSuccess(Json.arr("first")))
  }

  @Test
  def jsonPathShouldContinueAcceptingLeadingZeroArrayIndexes(): Unit = {
    assertThat(JsonPath.parse("/list/01").evaluate(Json.obj("list" -> Json.arr("zero", "one"))))
      .isEqualTo(JsSuccess(Json.toJson("one")))
  }

  @Test
  def jsonPathShouldRejectANumericTokenAppliedToAPrimitive(): Unit = {
    assertThat(JsonPath.parse("/0").evaluate(Json.toJson("value"))).isInstanceOf(classOf[JsError])
  }

  @Test
  def jsonPathShouldContinueAcceptingAnUnrecognizedEscapeLiterally(): Unit = {
    assertThat(JsonPath.parse("/a~2b").evaluate(Json.obj("a~2b" -> "value")))
      .isEqualTo(JsSuccess(Json.toJson("value")))
  }
}
