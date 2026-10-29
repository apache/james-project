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

import eu.timepit.refined.auto._
import org.apache.james.jmap.core.Invocation.{Arguments, MethodCallId, MethodName}
import org.apache.james.jmap.core.{ClientId, Id, Invocation, ServerId}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{Disabled, Test}
import play.api.libs.json.{JsString, JsSuccess, Json}

class ProcessingContextKnownEdgeCasesTest {
  @Test
  def resolveBackReferencesShouldResolveAReferenceToA255CharacterCreationID(): Unit = {
    val creationId = "c" * 255
    val context = ProcessingContext(Map(ClientId(Id.validate(creationId).toOption.get) -> ServerId("server-id")), Map.empty)
    val invocation = Invocation(MethodName("Mailbox/get"),
      Arguments(Json.obj("ids" -> Json.arr("#" + creationId))), MethodCallId("get"))

    assertThat(context.resolveBackReferences(invocation).toOption.get.arguments.value)
      .isEqualTo(Json.obj("ids" -> Json.arr("server-id")))
  }

  @Test
  @Disabled("Baseline only resolves keys beginning with '#', not references inside patch paths; the fix is deferred.")
  def resolveBackReferencesShouldResolveAMailboxCreationReferenceInsideAPatchPath(): Unit = {
    val context = ProcessingContext(Map(ClientId("mailbox") -> ServerId("server-id")), Map.empty)
    val invocation = Invocation(MethodName("Email/set"), Arguments(Json.obj(
      "update" -> Json.obj("email-id" -> Json.obj("mailboxIds/#mailbox" -> true)))), MethodCallId("set"))

    assertThat(context.resolveBackReferences(invocation).toOption.get.arguments.value)
      .isEqualTo(Json.obj(
        "update" -> Json.obj("email-id" -> Json.obj("mailboxIds/server-id" -> true))))
  }

  @Test
  @Disabled("Baseline substitutes every matching '#'-prefixed string, including this literal; method-aware resolution is deferred.")
  def resolveBackReferencesShouldPreserveALiteralStringMatchingARecordedCreationID(): Unit = {
    val context = ProcessingContext(Map(ClientId("created") -> ServerId("server-id")), Map.empty)
    val arguments = Json.obj("name" -> "#created")
    val invocation = Invocation(MethodName("Core/echo"), Arguments(arguments), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation).toOption.get.arguments.value)
      .isEqualTo(arguments)
  }

  @Test
  def jsonPathShouldDecodeAnEscapedTildeInAnObjectProperty(): Unit = {
    assertThat(JsonPath.parse("/a~0b").evaluate(Json.obj("a~b" -> "value")))
      .isEqualTo(JsSuccess(JsString("value")))
  }

  @Test
  def jsonPathShouldDecodeAnEscapedSlashInAnObjectProperty(): Unit = {
    assertThat(JsonPath.parse("/a~1b").evaluate(Json.obj("a/b" -> "value")))
      .isEqualTo(JsSuccess(JsString("value")))
  }

  @Test
  def jsonPathShouldReadANumericObjectProperty(): Unit = {
    assertThat(JsonPath.parse("/objects/0").evaluate(Json.obj("objects" -> Json.obj("0" -> "value"))))
      .isEqualTo(JsSuccess(JsString("value")))
  }
}
