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
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{ClientId, Id, SetError}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, Json}
import reactor.core.scala.publisher.SMono

import scala.collection.immutable.ListMap

class CreationProcessorTest extends AnyWordSpec with Matchers {
  "Creation processing" should {
    "accept an empty create" in {
      val result = ProcessingContext(Map.empty, Map.empty).processCreations[ClientId, JsObject](Map.empty)(
        _.value, json => (json \ "id").as[String])((_, json) => SMono.just(Right(json))).block()

      result.created shouldBe empty
      result.notCreated shouldBe empty
    }

    "create parents before children for every permutation of a forest" in {
      val entries = Seq(
        ClientId("parent") -> Json.obj("name" -> "parent"),
        ClientId("child") -> Json.obj("parentId" -> "#parent"),
        ClientId("grandchild") -> Json.obj("parentId" -> "#child"),
        ClientId("sibling") -> Json.obj("parentId" -> "#parent"),
        ClientId("independent") -> Json.obj())
      entries.permutations.foreach { permutation =>
        val result = ProcessingContext(Map.empty, Map.empty).processCreations[ClientId, JsObject](
          ListMap.from(permutation), Set("parentId"))(_.value, json => (json \ "id").as[String]) { (id, json) =>
          SMono.just(Right(json + ("id" -> Json.toJson("server-" + id.value.value))))
        }.block()

        result.notCreated shouldBe empty
        result.created.keySet shouldBe entries.map(_._1).toSet
        (result.created(ClientId("grandchild")) \ "parentId").as[String] shouldBe "server-child"
      }
    }

    "reject descendants of a failed creation while continuing independent creates" in {
      val result = ProcessingContext(Map.empty, Map.empty).processCreations[ClientId, JsObject](Map(
        ClientId("parent") -> Json.obj(),
        ClientId("child") -> Json.obj("parentId" -> "#parent"),
        ClientId("independent") -> Json.obj()), Set("parentId"))(_.value, json => (json \ "id").as[String]) { (id, json) =>
        if (id == ClientId("parent")) {
          SMono.just(Left(SetError.forbidden(SetErrorDescription("Denied"))))
        } else {
          SMono.just(Right(json + ("id" -> Json.toJson("server-" + id.value.value))))
        }
      }.block()

      result.created.keySet shouldBe Set(ClientId("independent"))
      result.notCreated.keySet shouldBe Set(ClientId("parent"), ClientId("child"))
    }

    "handle deep trees without recursive traversal" in {
      val ids = (0 until 10000).map(index => ClientId(Id.validate(s"item$index").toOption.get))
      val entries = ids.zipWithIndex.map { case (id, index) =>
        id -> (if (index == 0) Json.obj() else Json.obj("parentId" -> ("#" + ids(index - 1).value.value)))
      }
      val result = ProcessingContext(Map.empty, Map.empty).processCreations[ClientId, JsObject](
        ListMap.from(entries.reverse), Set("parentId"))(_.value, json => (json \ "id").as[String]) { (id, json) =>
        SMono.just(Right(json + ("id" -> Json.toJson("server-" + id.value.value))))
      }.block()

      result.notCreated shouldBe empty
      result.created.size shouldBe ids.size
    }
  }
}
