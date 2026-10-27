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
import org.apache.james.jmap.core.{ClientId, Invocation, ServerId}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, Json}
import reactor.core.scala.publisher.SMono

class ProcessingContextTest extends AnyWordSpec with Matchers {
  "Creation processing" should {
    "resolve multiple dependencies before creating records and preserve literal strings" in {
      val context = ProcessingContext(Map.empty, Map.empty)
      val creations = Map(
        ClientId("child") -> Json.obj("name" -> "#parent", "parentId" -> "#parent", "otherId" -> "#other"),
        ClientId("parent") -> Json.obj("name" -> "parent"),
        ClientId("other") -> Json.obj("name" -> "other"))

      val result = context.processCreations[ClientId, JsObject](creations, Set("parentId", "otherId"))(
        _.value, json => (json \ "id").as[String]) { (id, json) =>
        SMono.just(Right(json + ("id" -> Json.toJson("server-" + id.value.value))))
      }.block()

      result.notCreated shouldBe empty
      result.created(ClientId("child")) shouldBe Json.obj(
        "id" -> "server-child", "name" -> "#parent", "parentId" -> "server-parent", "otherId" -> "server-other")
      result.processingContext.retrieveServerId(ClientId("parent")) shouldBe Some(ServerId("server-parent"))
      context.retrieveServerId(ClientId("parent")) shouldBe None
    }

    "reject cycles and unknown references without preventing independent creates" in {
      val result = ProcessingContext(Map.empty, Map.empty).processCreations[ClientId, JsObject](Map(
        ClientId("a") -> Json.obj("parentId" -> "#b"),
        ClientId("b") -> Json.obj("parentId" -> "#a"),
        ClientId("descendant") -> Json.obj("parentId" -> "#a"),
        ClientId("missing") -> Json.obj("parentId" -> "#unknown"),
        ClientId("independent") -> Json.obj()), Set("parentId"))(
        _.value, json => (json \ "id").as[String]) { (id, json) =>
        SMono.just(Right(json + ("id" -> Json.toJson("server-" + id.value.value))))
      }.block()

      result.created.keySet shouldBe Set(ClientId("independent"))
      result.notCreated.keySet shouldBe Set(ClientId("a"), ClientId("b"), ClientId("descendant"), ClientId("missing"))
      result.notCreated.values.foreach(error => error.properties shouldBe Some(org.apache.james.jmap.core.Properties("parentId")))
    }

    "resolve update IDs after creation without substituting literal patch values" in {
      val context = ProcessingContext(Map(ClientId("custom") -> ServerId("server-id")), Map.empty)
      val request = Json.obj("update" -> Json.obj("#custom" -> Json.obj("name" -> "#custom")), "destroy" -> Json.arr("#custom"))

      context.resolveSetReferences(request) shouldBe Json.obj(
        "update" -> Json.obj("server-id" -> Json.obj("name" -> "#custom")), "destroy" -> Json.arr("server-id"))
    }
  }

  "Back reference resolution" should {
    "resolve creation references in ID properties" in {
      val context = ProcessingContext(Map(ClientId("parent") -> ServerId("server123")), Map.empty)
      val invocation = Invocation(MethodName("Mailbox/get"), Arguments(Json.obj(
        "accountId" -> "account",
        "ids" -> Json.arr("#parent"))), MethodCallId("get"))

      context.resolveBackReferences(invocation).toOption.get.arguments.value shouldBe Json.obj(
        "accountId" -> "account",
        "ids" -> Json.arr("server123"))
    }

    "preserve literal strings outside ID properties" in {
      val context = ProcessingContext(Map(ClientId("parent") -> ServerId("server123")), Map.empty)
      val arguments = Json.obj(
        "name" -> "#parent",
        "nested" -> Json.obj("text" -> "#parent"),
        "values" -> Json.arr("#parent"))
      val invocation = Invocation(MethodName("Core/echo"), Arguments(arguments), MethodCallId("echo"))

      context.resolveBackReferences(invocation).toOption.get.arguments.value shouldBe arguments
    }

    "resolve update map keys while preserving literal patch values" in {
      val context = ProcessingContext(Map(ClientId("parent") -> ServerId("server123")), Map.empty)
      val invocation = Invocation(MethodName("Mailbox/set"), Arguments(Json.obj(
        "update" -> Json.obj("#parent" -> Json.obj("name" -> "#parent")))), MethodCallId("set"))

      context.resolveBackReferences(invocation).toOption.get.arguments.value shouldBe Json.obj(
        "update" -> Json.obj("server123" -> Json.obj("name" -> "#parent")))
    }
  }

  "Creation ID lookup" should {
    "return no mapping for an unknown creation ID" in {
      ProcessingContext(Map.empty, Map.empty).retrieveServerId(ClientId("missing")) should equal(None)
    }

    "retain initial and newly recorded mappings without changing the previous context" in {
      val initial = ProcessingContext(Map(ClientId("initial") -> ServerId("server0")), Map.empty)
      val updated = initial.recordCreatedId(ClientId("parent"), ServerId("server1"))
        .recordCreatedId(ClientId("child"), ServerId("server2"))

      updated.retrieveServerId(ClientId("initial")) should equal(Some(ServerId("server0")))
      updated.retrieveServerId(ClientId("parent")) should equal(Some(ServerId("server1")))
      updated.retrieveServerId(ClientId("child")) should equal(Some(ServerId("server2")))
      initial.retrieveServerId(ClientId("parent")) should equal(None)
    }
  }
}
