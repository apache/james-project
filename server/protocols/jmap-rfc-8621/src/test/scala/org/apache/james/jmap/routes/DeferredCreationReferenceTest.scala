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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsObject, JsPath, Json}

class DeferredCreationReferenceTest {
  private val context = ProcessingContext(Map(ClientId("parent") -> ServerId("old-parent")), Map.empty)

  @Test
  def shouldDeferOnlyDeclaredValuesAndKeepLegacySubstitutionElsewhere(): Unit = {
    val arguments = Json.parse("""{
      |  "create": {"child": {"parentId": "#parent", "name": "#parent"}},
      |  "update": {"#parent": {"parentId": "#parent"}}
      |}""".stripMargin).as[JsObject]
    val expectedArguments = Json.parse("""{
      |  "create": {"child": {"parentId": "#parent", "name": "old-parent"}},
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

    val resolved = contextWithPreviousResult.resolveBackReferences(invocation, deferredPaths).toOption.get
    assertThat((resolved.arguments.value \ "create" \ "child" \ "parentId").as[String]).isEqualTo("real-parent")
  }

  @Test
  def shouldDistinguishDeferredArrayElements(): Unit = {
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj("ids" -> Json.arr("#parent", "#parent"))), MethodCallId("echo"))
    val expectedArguments = Arguments(Json.obj("ids" -> Json.arr("#parent", "old-parent")))

    val resolved = context.resolveBackReferences(invocation, Set((JsPath \ "ids")(0)))

    assertThat(resolved).isEqualTo(Right(invocation.copy(arguments = expectedArguments)))
  }
}
