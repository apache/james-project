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
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsString, JsSuccess, Json}

class ProcessingContextKnownEdgeCasesTest extends AnyWordSpec with Matchers {
  "Known creation-reference limitations" should {
    // Baseline validates the prefix too, leaving the 256-character reference unresolved; the fix is deferred.
    "resolve a reference to a 255-character creation ID" ignore {
      val creationId = "c" * 255
      val context = ProcessingContext(Map(ClientId(Id.validate(creationId).toOption.get) -> ServerId("server-id")), Map.empty)
      val invocation = Invocation(MethodName("Mailbox/get"),
        Arguments(Json.obj("ids" -> Json.arr("#" + creationId))), MethodCallId("get"))

      context.resolveBackReferences(invocation).toOption.get.arguments.value shouldBe
        Json.obj("ids" -> Json.arr("server-id"))
    }

    // Baseline only resolves keys beginning with '#', not references inside patch paths; the fix is deferred.
    "resolve a mailbox creation reference inside a patch path" ignore {
      val context = ProcessingContext(Map(ClientId("mailbox") -> ServerId("server-id")), Map.empty)
      val invocation = Invocation(MethodName("Email/set"), Arguments(Json.obj(
        "update" -> Json.obj("email-id" -> Json.obj("mailboxIds/#mailbox" -> true)))), MethodCallId("set"))

      context.resolveBackReferences(invocation).toOption.get.arguments.value shouldBe Json.obj(
        "update" -> Json.obj("email-id" -> Json.obj("mailboxIds/server-id" -> true)))
    }

    // Baseline substitutes every matching '#'-prefixed string, including this literal; method-aware resolution is deferred.
    "preserve a literal string matching a recorded creation ID" ignore {
      val context = ProcessingContext(Map(ClientId("created") -> ServerId("server-id")), Map.empty)
      val arguments = Json.obj("name" -> "#created")
      val invocation = Invocation(MethodName("Core/echo"), Arguments(arguments), MethodCallId("echo"))

      context.resolveBackReferences(invocation).toOption.get.arguments.value shouldBe arguments
    }
  }

  "Known JSON Pointer limitations" should {
    // Baseline looks up 'a~0b' literally instead of decoding '~0'; JSON Pointer support is deferred.
    "decode an escaped tilde in an object property" ignore {
      JsonPath.parse("/a~0b").evaluate(Json.obj("a~b" -> "value")) shouldBe JsSuccess(JsString("value"))
    }

    // Baseline looks up 'a~1b' literally instead of decoding '~1'; JSON Pointer support is deferred.
    "decode an escaped slash in an object property" ignore {
      JsonPath.parse("/a~1b").evaluate(Json.obj("a/b" -> "value")) shouldBe JsSuccess(JsString("value"))
    }

    // Baseline treats every numeric path part as an array index, even for an object; the fix is deferred.
    "read a numeric object property" ignore {
      JsonPath.parse("/objects/0").evaluate(Json.obj("objects" -> Json.obj("0" -> "value"))) shouldBe
        JsSuccess(JsString("value"))
    }
  }
}
