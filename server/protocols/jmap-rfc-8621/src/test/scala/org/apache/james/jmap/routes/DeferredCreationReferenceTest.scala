/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
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
import org.apache.james.jmap.method.DeferredCreationReferences
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsObject, JsPath, Json}

class DeferredCreationReferenceTest {
  private val context = ProcessingContext(Map(ClientId("parent") -> ServerId("old-parent")), Map.empty)

  @Test
  def shouldDeferOnlyDeclaredValuesAndResolveOtherIdLocations(): Unit = {
    val arguments = Json.parse("""{
      |  "create": {"child": {"parentId": "#parent", "name": "#parent"}},
      |  "update": {"#parent": {"parentId": "#parent"}}
      |}""".stripMargin).as[JsObject]
    val expectedArguments = Json.parse("""{
      |  "create": {"child": {"parentId": "#parent", "name": "#parent"}},
      |  "update": {"old-parent": {"parentId": "old-parent"}}
      |}""".stripMargin).as[JsObject]
    val invocation = Invocation(MethodName("Mailbox/set"), Arguments(arguments), MethodCallId("set"))
    val deferredPaths = Set(JsPath \ "create" \ "child" \ "parentId")

    val resolved = context.resolveBackReferences(invocation, deferredPaths)

    assertThat(resolved).isEqualTo(Right(invocation.copy(arguments = Arguments(expectedArguments))))
  }

  @Test
  def shouldKeepResolvingResultReferencesAtDeferredPaths(): Unit = {
    val previous = Invocation(MethodName("Core/echo"), Arguments(Json.obj("id" -> "real-parent")), MethodCallId("previous"))
    val arguments = Json.parse("""{
      |  "create": {
      |    "child": {
      |      "#parentId": {"resultOf": "previous", "name": "Core/echo", "path": "/id"}
      |    }
      |  }
      |}""".stripMargin).as[JsObject]
    val invocation = Invocation(MethodName("Mailbox/set"), Arguments(arguments), MethodCallId("set"))
    val deferredPaths = Set(JsPath \ "create" \ "child" \ "parentId")
    val contextWithPreviousResult = context.recordInvocation(previous)

    val resolved = contextWithPreviousResult.resolveBackReferences(invocation, deferredPaths)

    val resolvedParent = resolved.map(result => (result.arguments.value \ "create" \ "child" \ "parentId").as[String])
    assertThat(resolvedParent).isEqualTo(Right("real-parent"))
  }

  @Test
  def shouldDistinguishDeferredArrayElements(): Unit = {
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj("ids" -> Json.arr("#parent", "#parent"))), MethodCallId("echo"))
    val expectedArguments = Arguments(Json.obj("ids" -> Json.arr("#parent", "old-parent")))

    val resolved = context.resolveBackReferences(invocation, Set((JsPath \ "ids")(0)))

    assertThat(resolved).isEqualTo(Right(invocation.copy(arguments = expectedArguments)))
  }

  @Test
  def shouldDeferUpdateKeysAndValuesWithoutLosingAliases(): Unit = {
    val arguments = Json.obj("update" -> Json.obj("#parent" -> Json.obj("parentId" -> "#parent"),
      "old-parent" -> Json.obj("name" -> "other")), "destroy" -> Json.arr("#parent"))
    val invocation = Invocation(MethodName("Mailbox/set"), Arguments(arguments), MethodCallId("set"))
    val deferredPaths = Set(
      JsPath \ "update" \ "#parent",
      JsPath \ "update" \ "#parent" \ "parentId",
      (JsPath \ "destroy")(0))

    val resolved = context.resolveBackReferences(invocation, deferredPaths)

    assertThat(resolved).isEqualTo(Right(invocation))
  }

  @Test
  def shouldDeferCreationReferencesInAResolvedUpdateResult(): Unit = {
    val patches = Json.obj("#parent" -> Json.obj("parentId" -> "#parent", "name" -> "after"))
    val previous = Invocation(MethodName("Core/echo"), Arguments(Json.obj("patches" -> patches)), MethodCallId("echoPatches"))
    val invocation = Invocation(MethodName("Mailbox/set"), Arguments(Json.obj(
      "#update" -> Json.obj("resultOf" -> "echoPatches", "name" -> "Core/echo", "path" -> "/patches"))), MethodCallId("updateFromResult"))
    val contextWithPreviousResult = context.recordInvocation(previous)
    val expectedArguments = Arguments(Json.obj("update" -> patches))
    // Deferred paths are those of the expanded arguments, as computed by Mailbox/set.
    val deferredPaths = Set(JsPath \ "update" \ "#parent", JsPath \ "update" \ "#parent" \ "parentId")

    val resolved = contextWithPreviousResult.resolveResultReferences(invocation)
      .flatMap(expanded => contextWithPreviousResult.resolveCreationReferences(expanded, deferredPaths, IdPaths.CONVENTIONAL))

    assertThat(resolved).isEqualTo(Right(invocation.copy(arguments = expectedArguments)))
  }

  @Test
  def shouldDeferCreationReferencesInAResolvedDestroyResult(): Unit = {
    val targets = Json.arr("#parent")
    val previous = Invocation(MethodName("Core/echo"), Arguments(Json.obj("targets" -> targets)), MethodCallId("echoTargets"))
    val invocation = Invocation(MethodName("Mailbox/set"), Arguments(Json.obj(
      "#destroy" -> Json.obj("resultOf" -> "echoTargets", "name" -> "Core/echo", "path" -> "/targets"))), MethodCallId("destroyFromResult"))
    val contextWithPreviousResult = context.recordInvocation(previous)
    val expectedArguments = Arguments(Json.obj("destroy" -> targets))
    val deferredPaths = Set((JsPath \ "destroy")(0))

    val resolved = contextWithPreviousResult.resolveResultReferences(invocation)
      .flatMap(expanded => contextWithPreviousResult.resolveCreationReferences(expanded, deferredPaths, IdPaths.CONVENTIONAL))

    assertThat(resolved).isEqualTo(Right(invocation.copy(arguments = expectedArguments)))
  }

  private def sameCallTargetPaths(json: String): Set[JsPath] =
    DeferredCreationReferences.sameCallTargetPaths(Arguments(Json.parse(json).as[JsObject]), ProcessingContext(Map.empty, Map.empty))

  @Test
  def sameCallTargetPathsShouldSelectTargetsCreatedByTheSameCall(): Unit =
    assertThat(sameCallTargetPaths(
      """{
        |  "create": {"x": {"parentId": "#x"}},
        |  "update": {"#x": {}, "#y": {}, "x": {}, "other": {"parentId": "#x"}},
        |  "destroy": ["#y", "#x", "x"]
        |}""".stripMargin))
      .isEqualTo(Set(JsPath \ "update" \ "#x", (JsPath \ "destroy")(1)))
}
