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
import play.api.libs.json.{JsObject, Json}

class ProcessingContextTest extends AnyWordSpec with Matchers {
  "Creation reference resolution" should {
    "preserve an empty argument object" in {
      val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj()), MethodCallId("echo"))
      val context = ProcessingContext(Map.empty, Map.empty)

      context.resolveBackReferences(invocation) shouldBe Right(invocation)
    }

    "preserve ordinary values and nested empty containers" in {
      val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.parse(
        """{
          |  "string": "server-id",
          |  "number": 42,
          |  "boolean": true,
          |  "null": null,
          |  "array": [],
          |  "object": {}
          |}""".stripMargin).as[JsObject]), MethodCallId("echo"))
      val context = ProcessingContext(Map.empty, Map.empty)

      context.resolveBackReferences(invocation) shouldBe Right(invocation)
    }

    "resolve SearchSnippet emailIds after recording an email creation" in {
      val context = ProcessingContext(Map.empty, Map.empty)
        .recordCreatedId(ClientId("email"), ServerId("server-email"))
      val invocation = Invocation(MethodName("SearchSnippet/get"), Arguments(Json.obj(
        "accountId" -> "account",
        "emailIds" -> Json.arr("#email", "existing-email"))), MethodCallId("snippets"))

      context.resolveBackReferences(invocation) shouldBe Right(invocation.copy(arguments = Arguments(Json.obj(
        "accountId" -> "account",
        "emailIds" -> Json.arr("server-email", "existing-email")))))
    }

    "resolve strings regardless of method and property names" in {
      val context = ProcessingContext(Map(ClientId("asset") -> ServerId("server-asset")), Map.empty)
      val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
        "arbitrary" -> "#asset")), MethodCallId("echo"))

      context.resolveBackReferences(invocation) shouldBe Right(invocation.copy(arguments = Arguments(Json.obj(
        "arbitrary" -> "server-asset"))))
    }

    "resolve creation references in nested arrays and objects" in {
      val context = ProcessingContext(Map(ClientId("email") -> ServerId("server-email")), Map.empty)
      val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
        "nested" -> Json.arr(Json.arr("#email"), Json.obj("value" -> "#email")))), MethodCallId("echo"))

      context.resolveBackReferences(invocation) shouldBe Right(invocation.copy(arguments = Arguments(Json.obj(
        "nested" -> Json.arr(Json.arr("server-email"), Json.obj("value" -> "server-email"))))))
    }

    "resolve both object keys and their nested values" in {
      val context = ProcessingContext(Map(ClientId("mailbox") -> ServerId("server-mailbox")), Map.empty)
      val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
        "#mailbox" -> Json.obj("value" -> "#mailbox"))), MethodCallId("echo"))

      context.resolveBackReferences(invocation) shouldBe Right(invocation.copy(arguments = Arguments(Json.obj(
        "server-mailbox" -> Json.obj("value" -> "server-mailbox")))))
    }

    "preserve an unknown object key while resolving its value" in {
      val context = ProcessingContext(Map(ClientId("email") -> ServerId("server-email")), Map.empty)
      val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
        "#unknown" -> "#email")), MethodCallId("echo"))

      context.resolveBackReferences(invocation) shouldBe Right(invocation.copy(arguments = Arguments(Json.obj(
        "#unknown" -> "server-email"))))
    }

    "preserve unknown creation references" in {
      val invocation = Invocation(MethodName("Mailbox/get"), Arguments(Json.obj(
        "ids" -> Json.arr("#unknown"))), MethodCallId("get"))
      val context = ProcessingContext(Map.empty, Map.empty)

      context.resolveBackReferences(invocation) shouldBe Right(invocation)
    }

    "preserve a bare hash without failing the invocation" in {
      val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
        "value" -> "#", "#" -> true)), MethodCallId("echo"))
      val context = ProcessingContext(Map.empty, Map.empty)

      context.resolveBackReferences(invocation) shouldBe Right(invocation)
    }

    "preserve malformed creation references" in {
      val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
        "value" -> "#with space", "#with space" -> true)), MethodCallId("echo"))
      val context = ProcessingContext(Map.empty, Map.empty)

      context.resolveBackReferences(invocation) shouldBe Right(invocation)
    }

    "preserve references exceeding the existing ID length limit" in {
      val reference = "#" + "x" * 256
      val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
        "value" -> reference, reference -> true)), MethodCallId("echo"))
      val context = ProcessingContext(Map.empty, Map.empty)

      context.resolveBackReferences(invocation) shouldBe Right(invocation)
    }

    "resolve a reference whose total length is 255 characters" in {
      val creationId = "x" * 254
      val context = ProcessingContext(Map(ClientId(Id.validate(creationId).toOption.get) -> ServerId("server-id")), Map.empty)
      val invocation = Invocation(MethodName("Mailbox/get"), Arguments(Json.obj(
        "ids" -> Json.arr("#" + creationId))), MethodCallId("get"))

      context.resolveBackReferences(invocation) shouldBe Right(invocation.copy(arguments = Arguments(Json.obj(
        "ids" -> Json.arr("server-id")))))
    }

    "preserve an embedded hash that does not start the value" in {
      val context = ProcessingContext(Map(ClientId("email") -> ServerId("server-email")), Map.empty)
      val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
        "value" -> "prefix#email")), MethodCallId("echo"))

      context.resolveBackReferences(invocation) shouldBe Right(invocation)
    }

    "substitute a mapped value once even if the server ID begins with a hash" in {
      val context = ProcessingContext(Map(
        ClientId("first") -> ServerId("#second"),
        ClientId("second") -> ServerId("server-id")), Map.empty)
      val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
        "value" -> "#first")), MethodCallId("echo"))

      context.resolveBackReferences(invocation) shouldBe Right(invocation.copy(arguments = Arguments(Json.obj(
        "value" -> "#second"))))
    }
  }

  "Recording creation IDs" should {
    "keep the previous context unchanged" in {
      val original = ProcessingContext(Map.empty, Map.empty)
      val updated = original.recordCreatedId(ClientId("email"), ServerId("server-email"))
      val invocation = Invocation(MethodName("Email/get"), Arguments(Json.obj(
        "ids" -> Json.arr("#email"))), MethodCallId("get"))

      original.resolveBackReferences(invocation) shouldBe Right(invocation)
      updated.resolveBackReferences(invocation) shouldBe Right(invocation.copy(arguments = Arguments(Json.obj(
        "ids" -> Json.arr("server-email")))))
    }

    "retain existing mappings when adding another creation" in {
      val context = ProcessingContext(Map(ClientId("first") -> ServerId("server-first")), Map.empty)
        .recordCreatedId(ClientId("second"), ServerId("server-second"))
      val invocation = Invocation(MethodName("Email/get"), Arguments(Json.obj(
        "ids" -> Json.arr("#first", "#second"))), MethodCallId("get"))

      context.resolveBackReferences(invocation) shouldBe Right(invocation.copy(arguments = Arguments(Json.obj(
        "ids" -> Json.arr("server-first", "server-second")))))
    }

    "replace a mapping when a creation ID is recorded again" in {
      val context = ProcessingContext(Map(ClientId("email") -> ServerId("old-email")), Map.empty)
        .recordCreatedId(ClientId("email"), ServerId("new-email"))
      val invocation = Invocation(MethodName("Email/get"), Arguments(Json.obj(
        "ids" -> Json.arr("#email"))), MethodCallId("get"))

      context.resolveBackReferences(invocation) shouldBe Right(invocation.copy(arguments = Arguments(Json.obj(
        "ids" -> Json.arr("new-email")))))
    }
  }

  "Recording invocations" should {
    "make a recorded response available without modifying the previous context" in {
      val response = Invocation(MethodName("Email/get"), Arguments(Json.obj(
        "list" -> Json.arr(Json.obj("id" -> "server-email")))), MethodCallId("get"))
      val invocation = Invocation(MethodName("Email/set"), Arguments(Json.obj(
        "#destroy" -> Json.obj("resultOf" -> "get", "name" -> "Email/get", "path" -> "/list/*/id"))), MethodCallId("set"))
      val original = ProcessingContext(Map.empty, Map.empty)
      val updated = original.recordInvocation(response)

      original.resolveBackReferences(invocation).isLeft shouldBe true
      updated.resolveBackReferences(invocation) shouldBe Right(invocation.copy(arguments = Arguments(Json.obj(
        "destroy" -> Json.arr("server-email")))))
    }

    "retain creation mappings while recording an invocation" in {
      val response = Invocation(MethodName("Email/get"), Arguments(Json.obj(
        "list" -> Json.arr())), MethodCallId("get"))
      val context = ProcessingContext(Map(ClientId("email") -> ServerId("server-email")), Map.empty)
        .recordInvocation(response)
      val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
        "value" -> "#email",
        "#list" -> Json.obj("resultOf" -> "get", "name" -> "Email/get", "path" -> "/list"))), MethodCallId("echo"))

      context.resolveBackReferences(invocation) shouldBe Right(invocation.copy(arguments = Arguments(Json.obj(
        "value" -> "server-email", "list" -> Json.arr()))))
    }

    "retain earlier invocations when recording another response" in {
      val first = Invocation(MethodName("Email/get"), Arguments(Json.obj("state" -> "first-state")), MethodCallId("first"))
      val second = Invocation(MethodName("Mailbox/get"), Arguments(Json.obj("state" -> "second-state")), MethodCallId("second"))
      val context = ProcessingContext(Map.empty, Map.empty).recordInvocation(first).recordInvocation(second)
      val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
        "#first" -> Json.obj("resultOf" -> "first", "name" -> "Email/get", "path" -> "/state"),
        "#second" -> Json.obj("resultOf" -> "second", "name" -> "Mailbox/get", "path" -> "/state"))), MethodCallId("echo"))

      context.resolveBackReferences(invocation) shouldBe Right(invocation.copy(arguments = Arguments(Json.obj(
        "first" -> "first-state", "second" -> "second-state"))))
    }

    "use the last response recorded for a method call ID" in {
      val first = Invocation(MethodName("Email/get"), Arguments(Json.obj("state" -> "old-state")), MethodCallId("get"))
      val second = Invocation(MethodName("Email/get"), Arguments(Json.obj("state" -> "new-state")), MethodCallId("get"))
      val context = ProcessingContext(Map.empty, Map.empty).recordInvocation(first).recordInvocation(second)
      val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
        "#state" -> Json.obj("resultOf" -> "get", "name" -> "Email/get", "path" -> "/state"))), MethodCallId("echo"))

      context.resolveBackReferences(invocation) shouldBe Right(invocation.copy(arguments = Arguments(Json.obj(
        "state" -> "new-state"))))
    }

    "leave strings in a referenced response untouched" in {
      val response = Invocation(MethodName("Email/get"), Arguments(Json.obj("state" -> "#email")), MethodCallId("get"))
      val context = ProcessingContext(Map(ClientId("email") -> ServerId("server-email")), Map.empty).recordInvocation(response)
      val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
        "#state" -> Json.obj("resultOf" -> "get", "name" -> "Email/get", "path" -> "/state"))), MethodCallId("echo"))

      context.resolveBackReferences(invocation) shouldBe Right(invocation.copy(arguments = Arguments(Json.obj(
        "state" -> "#email"))))
    }
  }
}
