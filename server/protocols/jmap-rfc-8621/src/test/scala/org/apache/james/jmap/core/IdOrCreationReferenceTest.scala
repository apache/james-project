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

package org.apache.james.jmap.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import play.api.libs.json.{JsError, JsString, JsSuccess, Json}

class IdOrCreationReferenceTest {
  @ParameterizedTest
  @ValueSource(strings = Array("id", "012_Az-9", "a#b", "#created", "##created", "##"))
  def shouldRoundTripValidatedTokens(value: String): Unit = {
    val token = IdOrCreationReference.validate(value).toOption.get

    assertThat(token.value).isEqualTo(value)
    assertThat(token.toString).isEqualTo(value)
    assertThat(Json.toJson(token)).isEqualTo(JsString(value))
    assertThat(Json.fromJson[IdOrCreationReference](JsString(value))).isEqualTo(JsSuccess(token))
  }

  @ParameterizedTest
  @ValueSource(strings = Array("", "#", "invalid id", "#invalid id", "#a/b", "#é", "a/b"))
  def shouldRejectInvalidTokens(value: String): Unit = {
    assertThat(IdOrCreationReference.validate(value).isLeft).isTrue()
    assertThat(Json.fromJson[IdOrCreationReference](JsString(value))).isInstanceOf(classOf[JsError])
  }

  @Test
  def shouldAcceptA255CharacterId(): Unit = {
    assertThat(IdOrCreationReference.validate("i" * 255).isRight).isTrue()
  }

  @Test
  def shouldAcceptAReferenceWithA255CharacterSuffix(): Unit = {
    val reference = "#" + "c" * 255

    assertThat(IdOrCreationReference.validate(reference).toOption.get.value).isEqualTo(reference)
    assertThat(Json.fromJson[IdOrCreationReference](JsString(reference)).isSuccess).isTrue()
  }

  @Test
  def shouldRejectA256CharacterId(): Unit = {
    assertThat(IdOrCreationReference.validate("i" * 256).isLeft).isTrue()
  }

  @Test
  def shouldRejectAReferenceWithA256CharacterSuffix(): Unit = {
    assertThat(IdOrCreationReference.validate("#" + "c" * 256).isLeft).isTrue()
  }

  @Test
  def shouldRejectNonStringJson(): Unit = {
    assertThat(Json.fromJson[IdOrCreationReference](Json.obj("value" -> "id"))).isInstanceOf(classOf[JsError])
  }

  @Test
  def shouldPreserveLegacyIdValidation(): Unit = {
    assertThat(Id.validate("#").isRight).isTrue()
    assertThat(Id.validate("##created").isRight).isTrue()
    assertThat(Id.validate("a#b").isRight).isTrue()
    assertThat(Id.validate("#" + "c" * 255).isLeft).isTrue()
  }
}
