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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsObject, Json}

class ProcessingContextFailurePropagationTest {
  @Test
  def resolveBackReferencesShouldRejectAReferenceToAMethodCallThatWasNotRecorded(): Unit = {
    val context = ProcessingContext(
      creationIds = Map.empty,
      invocations = Map.empty)
    val invocation = Invocation(
      methodName = MethodName("Mailbox/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "#ids": {
            |    "resultOf": "missing",
            |    "name": "Email/get",
            |    "path": "/list/*/id"
            |  }
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("current"))

    val result = context.resolveBackReferences(invocation)

    assertThat(result.isLeft).isTrue()
    assertThat(result.left.toOption.get.message).contains("Back reference could not be resolved")
  }

  @Test
  def resolveBackReferencesShouldRejectAReferenceWhoseMethodNameDoesNotMatchTheRecordedInvocation(): Unit = {
    val previous = Invocation(
      methodName = MethodName("Email/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "list": [
            |    {
            |      "id": "message0"
            |    },
            |    {
            |      "id": "message1"
            |    }
            |  ]
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("get"))
    val context = ProcessingContext(
      creationIds = Map.empty,
      invocations = Map(MethodCallId("get") -> previous))
    val invocation = Invocation(
      methodName = MethodName("Mailbox/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "#ids": {
            |    "resultOf": "get",
            |    "name": "Mailbox/get",
            |    "path": "/list/*/id"
            |  }
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("current"))

    val result = context.resolveBackReferences(invocation)

    assertThat(result.isLeft).isTrue()
    assertThat(result.left.toOption.get.message).contains("references a")
  }

  @Test
  def resolveBackReferencesShouldRejectAReferenceToAMissingPropertyInTheResult(): Unit = {
    val previous = Invocation(
      methodName = MethodName("Email/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "list": [
            |    {
            |      "id": "message0"
            |    },
            |    {
            |      "id": "message1"
            |    }
            |  ]
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("get"))
    val context = ProcessingContext(
      creationIds = Map.empty,
      invocations = Map(MethodCallId("get") -> previous))
    val invocation = Invocation(
      methodName = MethodName("Mailbox/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "#ids": {
            |    "resultOf": "get",
            |    "name": "Email/get",
            |    "path": "/missing"
            |  }
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("current"))

    val result = context.resolveBackReferences(invocation)

    assertThat(result.isLeft).isTrue()
    assertThat(result.left.toOption.get.message).contains("Expected path missing was missing")
  }

  @Test
  def resolveBackReferencesShouldRejectAReferenceToAnArrayElementOutsideTheResult(): Unit = {
    val previous = Invocation(
      methodName = MethodName("Email/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "list": [
            |    {
            |      "id": "message0"
            |    },
            |    {
            |      "id": "message1"
            |    }
            |  ]
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("get"))
    val context = ProcessingContext(
      creationIds = Map.empty,
      invocations = Map(MethodCallId("get") -> previous))
    val invocation = Invocation(
      methodName = MethodName("Mailbox/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "#id": {
            |    "resultOf": "get",
            |    "name": "Email/get",
            |    "path": "/list/9/id"
            |  }
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("current"))

    val result = context.resolveBackReferences(invocation)

    assertThat(result.isLeft).isTrue()
    assertThat(result.left.toOption.get.message).contains("Supplied array have no")
  }

  @Test
  def resolveBackReferencesShouldRejectAnObjectPropertyPathAppliedToAnArray(): Unit = {
    val previous = Invocation(
      methodName = MethodName("Email/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "list": [
            |    {
            |      "id": "message0"
            |    },
            |    {
            |      "id": "message1"
            |    }
            |  ]
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("get"))
    val context = ProcessingContext(
      creationIds = Map.empty,
      invocations = Map(MethodCallId("get") -> previous))
    val invocation = Invocation(
      methodName = MethodName("Mailbox/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "#id": {
            |    "resultOf": "get",
            |    "name": "Email/get",
            |    "path": "/list/id"
            |  }
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("current"))

    val result = context.resolveBackReferences(invocation)

    assertThat(result.isLeft).isTrue()
    assertThat(result.left.toOption.get.message).contains("Expecting a JsObject")
  }

  @Test
  def resolveBackReferencesShouldPropagateAFailedReferenceFromANestedObject(): Unit = {
    val context = ProcessingContext(
      creationIds = Map.empty,
      invocations = Map.empty)
    val invocation = Invocation(
      methodName = MethodName("Mailbox/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "outer": {
            |    "inner": {
            |      "#ids": {
            |        "resultOf": "missing",
            |        "name": "Email/get",
            |        "path": "/list/*/id"
            |      }
            |    }
            |  }
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("current"))

    val result = context.resolveBackReferences(invocation)

    assertThat(result.isLeft).isTrue()
    assertThat(result.left.toOption.get.message).contains("Back reference could not be resolved")
  }

  @Test
  def resolveBackReferencesShouldPropagateAFailedReferenceFromAnObjectInsideAnArray(): Unit = {
    val previous = Invocation(
      methodName = MethodName("Email/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "list": [
            |    {
            |      "id": "message0"
            |    },
            |    {
            |      "id": "message1"
            |    }
            |  ]
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("get"))
    val context = ProcessingContext(
      creationIds = Map.empty,
      invocations = Map(MethodCallId("get") -> previous))
    val invocation = Invocation(
      methodName = MethodName("Mailbox/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "values": [
            |    "literal",
            |    {
            |      "#ids": {
            |        "resultOf": "get",
            |        "name": "Email/get",
            |        "path": "/missing"
            |      }
            |    }
            |  ]
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("current"))

    val result = context.resolveBackReferences(invocation)

    assertThat(result.isLeft).isTrue()
    assertThat(result.left.toOption.get.message).contains("Expected path missing was missing")
  }

  @Test
  def resolveBackReferencesShouldPropagateAFailedReferenceThroughNestedArrays(): Unit = {
    val context = ProcessingContext(
      creationIds = Map.empty,
      invocations = Map.empty)
    val invocation = Invocation(
      methodName = MethodName("Mailbox/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "values": [
            |    [
            |      {
            |        "#ids": {
            |          "resultOf": "missing",
            |          "name": "Email/get",
            |          "path": "/list/*/id"
            |        }
            |      }
            |    ]
            |  ]
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("current"))

    val result = context.resolveBackReferences(invocation)

    assertThat(result.isLeft).isTrue()
    assertThat(result.left.toOption.get.message).contains("Back reference could not be resolved")
  }

  @Test
  def resolveBackReferencesShouldRejectTheEntireInvocationWhenAValidSiblingAccompaniesAFailedNestedReference(): Unit = {
    val previous = Invocation(
      methodName = MethodName("Email/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "list": [
            |    {
            |      "id": "message0"
            |    },
            |    {
            |      "id": "message1"
            |    }
            |  ]
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("get"))
    val context = ProcessingContext(
      creationIds = Map.empty,
      invocations = Map(MethodCallId("get") -> previous))
    val invocation = Invocation(
      methodName = MethodName("Mailbox/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "valid": {
            |    "#ids": {
            |      "resultOf": "get",
            |      "name": "Email/get",
            |      "path": "/list/*/id"
            |    }
            |  },
            |  "invalid": {
            |    "#id": {
            |      "resultOf": "get",
            |      "name": "Email/get",
            |      "path": "/list/8/id"
            |    }
            |  }
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("current"))

    val result = context.resolveBackReferences(invocation)

    assertThat(result.isLeft).isTrue()
    assertThat(result.left.toOption.get.message).contains("Supplied array have no")
  }

  @Test
  def resolveBackReferencesShouldPropagateAFailureFromOneElementEvaluatedByAWildcardPath(): Unit = {
    val previous = Invocation(
      methodName = MethodName("Email/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "list": [
            |    {
            |      "id": "message0"
            |    },
            |    {
            |      "subject": "no id"
            |    }
            |  ]
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("get"))
    val context = ProcessingContext(
      creationIds = Map.empty,
      invocations = Map(MethodCallId("get") -> previous))
    val invocation = Invocation(
      methodName = MethodName("Mailbox/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "#ids": {
            |    "resultOf": "get",
            |    "name": "Email/get",
            |    "path": "/list/*/id"
            |  }
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("current"))

    val result = context.resolveBackReferences(invocation)

    assertThat(result.isLeft).isTrue()
    assertThat(result.left.toOption.get.message).contains("Expected path id was missing")
  }

  @Test
  def resolveBackReferencesShouldPreserveAnUnknownHashKeyWhenResultOfIsMissing(): Unit = {
    val context = ProcessingContext(
      creationIds = Map.empty,
      invocations = Map.empty)
    val invocation = Invocation(
      methodName = MethodName("Mailbox/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "#ids": {
            |    "name": "Email/get",
            |    "path": "/list/*/id"
            |  }
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("current"))

    val result = context.resolveBackReferences(invocation)

    assertThat(result)
      .isEqualTo(Right(invocation))
  }

  @Test
  def resolveBackReferencesShouldPreserveAnUnknownHashKeyWhenNameIsMissing(): Unit = {
    val context = ProcessingContext(
      creationIds = Map.empty,
      invocations = Map.empty)
    val invocation = Invocation(
      methodName = MethodName("Mailbox/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "#ids": {
            |    "resultOf": "get",
            |    "path": "/list/*/id"
            |  }
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("current"))

    val result = context.resolveBackReferences(invocation)

    assertThat(result)
      .isEqualTo(Right(invocation))
  }

  @Test
  def resolveBackReferencesShouldPreserveAnUnknownHashKeyWhenPathIsMissing(): Unit = {
    val context = ProcessingContext(
      creationIds = Map.empty,
      invocations = Map.empty)
    val invocation = Invocation(
      methodName = MethodName("Mailbox/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "#ids": {
            |    "resultOf": "get",
            |    "name": "Email/get"
            |  }
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("current"))

    val result = context.resolveBackReferences(invocation)

    assertThat(result)
      .isEqualTo(Right(invocation))
  }

  @Test
  def resolveBackReferencesShouldPreserveAnUnknownHashKeyWhenResultOfHasTheWrongJSONType(): Unit = {
    val context = ProcessingContext(
      creationIds = Map.empty,
      invocations = Map.empty)
    val invocation = Invocation(
      methodName = MethodName("Mailbox/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "#ids": {
            |    "resultOf": 42,
            |    "name": "Email/get",
            |    "path": "/list"
            |  }
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("current"))

    val result = context.resolveBackReferences(invocation)

    assertThat(result)
      .isEqualTo(Right(invocation))
  }

  @Test
  def resolveBackReferencesShouldPreserveAnUnknownHashKeyWhenNameHasTheWrongJSONType(): Unit = {
    val context = ProcessingContext(
      creationIds = Map.empty,
      invocations = Map.empty)
    val invocation = Invocation(
      methodName = MethodName("Mailbox/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "#ids": {
            |    "resultOf": "get",
            |    "name": 42,
            |    "path": "/list"
            |  }
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("current"))

    val result = context.resolveBackReferences(invocation)

    assertThat(result)
      .isEqualTo(Right(invocation))
  }

  @Test
  def resolveBackReferencesShouldPreserveAnUnknownHashKeyWhenPathHasTheWrongJSONType(): Unit = {
    val context = ProcessingContext(
      creationIds = Map.empty,
      invocations = Map.empty)
    val invocation = Invocation(
      methodName = MethodName("Mailbox/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "#ids": {
            |    "resultOf": "get",
            |    "name": "Email/get",
            |    "path": [
            |      "list"
            |    ]
            |  }
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("current"))

    val result = context.resolveBackReferences(invocation)

    assertThat(result)
      .isEqualTo(Right(invocation))
  }

  @Test
  def resolveBackReferencesShouldContinueTraversingNestedReferencesInsideAMalformedResultReferenceObject(): Unit = {
    val previous = Invocation(
      methodName = MethodName("Email/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "list": [
            |    {
            |      "id": "message0"
            |    },
            |    {
            |      "id": "message1"
            |    }
            |  ]
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("get"))
    val context = ProcessingContext(
      creationIds = Map.empty,
      invocations = Map(MethodCallId("get") -> previous))
    val invocation = Invocation(
      methodName = MethodName("Mailbox/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "#unknown": {
            |    "resultOf": 42,
            |    "nested": {
            |      "#ids": {
            |        "resultOf": "get",
            |        "name": "Email/get",
            |        "path": "/list/*/id"
            |      }
            |    }
            |  }
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("current"))

    val result = context.resolveBackReferences(invocation)

    assertThat(result.isRight).isTrue()
    assertThat(result.toOption.get.arguments.value)
      .isEqualTo(Json.parse(
        """{
          |  "#unknown": {
          |    "resultOf": 42,
          |    "nested": {
          |      "ids": [
          |        "message0",
          |        "message1"
          |      ]
          |    }
          |  }
          |}""".stripMargin).as[JsObject])
  }

  @Test
  def resolveBackReferencesShouldPropagateANestedFailureWhileFallingBackFromAMalformedResultReference(): Unit = {
    val context = ProcessingContext(
      creationIds = Map.empty,
      invocations = Map.empty)
    val invocation = Invocation(
      methodName = MethodName("Mailbox/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "#unknown": {
            |    "resultOf": 42,
            |    "nested": {
            |      "#ids": {
            |        "resultOf": "missing",
            |        "name": "Email/get",
            |        "path": "/list/*/id"
            |      }
            |    }
            |  }
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("current"))

    val result = context.resolveBackReferences(invocation)

    assertThat(result.isLeft).isTrue()
    assertThat(result.left.toOption.get.message).contains("Back reference could not be resolved")
  }

  @Test
  def resolveBackReferencesShouldResolveAKnownCreationIDKeyWhenItsValueIsNotAResultReference(): Unit = {
    val context = ProcessingContext(
      creationIds = Map(ClientId("created") -> ServerId("server123")),
      invocations = Map.empty)
    val invocation = Invocation(
      methodName = MethodName("Mailbox/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "update": {
            |    "#created": {
            |      "name": "child"
            |    }
            |  }
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("current"))

    val result = context.resolveBackReferences(invocation)

    assertThat(result.isRight).isTrue()
    assertThat(result.toOption.get.arguments.value)
      .isEqualTo(Json.parse(
        """{
          |  "update": {
          |    "server123": {
          |      "name": "child"
          |    }
          |  }
          |}""".stripMargin).as[JsObject])
  }

  @Test
  def resolveBackReferencesShouldPreserveAnUnknownCreationIDKeyWhileResolvingItsNestedResultReference(): Unit = {
    val previous = Invocation(
      methodName = MethodName("Email/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "list": [
            |    {
            |      "id": "message0"
            |    },
            |    {
            |      "id": "message1"
            |    }
            |  ]
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("get"))
    val context = ProcessingContext(
      creationIds = Map.empty,
      invocations = Map(MethodCallId("get") -> previous))
    val invocation = Invocation(
      methodName = MethodName("Mailbox/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "update": {
            |    "#unknown": {
            |      "#ids": {
            |        "resultOf": "get",
            |        "name": "Email/get",
            |        "path": "/list/*/id"
            |      }
            |    }
            |  }
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("current"))

    val result = context.resolveBackReferences(invocation)

    assertThat(result.isRight).isTrue()
    assertThat(result.toOption.get.arguments.value)
      .isEqualTo(Json.parse(
        """{
          |  "update": {
          |    "#unknown": {
          |      "ids": [
          |        "message0",
          |        "message1"
          |      ]
          |    }
          |  }
          |}""".stripMargin).as[JsObject])
  }

  @Test
  def resolveBackReferencesShouldFallBackToAKnownCreationIDKeyWhenResultReferenceDeserializationFails(): Unit = {
    val context = ProcessingContext(
      creationIds = Map(ClientId("created") -> ServerId("server123")),
      invocations = Map.empty)
    val invocation = Invocation(
      methodName = MethodName("Mailbox/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "update": {
            |    "#created": {
            |      "resultOf": 42,
            |      "name": "Email/get",
            |      "path": "/list/*/id"
            |    }
            |  }
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("current"))

    val result = context.resolveBackReferences(invocation)

    assertThat(result.isRight).isTrue()
    assertThat(result.toOption.get.arguments.value)
      .isEqualTo(Json.parse(
        """{
          |  "update": {
          |    "server123": {
          |      "resultOf": 42,
          |      "name": "Email/get",
          |      "path": "/list/*/id"
          |    }
          |  }
          |}""".stripMargin).as[JsObject])
  }

  @Test
  def resolveBackReferencesShouldPreferAWellFormedResultReferenceOverAMatchingCreationIDMapKey(): Unit = {
    val previous = Invocation(
      methodName = MethodName("Email/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "list": [
            |    {
            |      "id": "message0"
            |    },
            |    {
            |      "id": "message1"
            |    }
            |  ]
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("get"))
    val context = ProcessingContext(
      creationIds = Map(ClientId("created") -> ServerId("server123")),
      invocations = Map(MethodCallId("get") -> previous))
    val invocation = Invocation(
      methodName = MethodName("Mailbox/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "#created": {
            |    "resultOf": "get",
            |    "name": "Email/get",
            |    "path": "/list/0/id"
            |  }
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("current"))

    val result = context.resolveBackReferences(invocation)

    assertThat(result.isRight).isTrue()
    assertThat(result.toOption.get.arguments.value)
      .isEqualTo(Json.parse(
        """{
          |  "created": "message0"
          |}""".stripMargin).as[JsObject])
  }

  @Test
  def resolveBackReferencesShouldReturnAResultReferenceFailureInsteadOfFallingBackToAMatchingCreationIDKey(): Unit = {
    val context = ProcessingContext(
      creationIds = Map(ClientId("created") -> ServerId("server123")),
      invocations = Map.empty)
    val invocation = Invocation(
      methodName = MethodName("Mailbox/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "#created": {
            |    "resultOf": "missing",
            |    "name": "Email/get",
            |    "path": "/list/*/id"
            |  }
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("current"))

    val result = context.resolveBackReferences(invocation)

    assertThat(result.isLeft).isTrue()
    assertThat(result.left.toOption.get.message).contains("Back reference could not be resolved")
  }

  @Test
  def resolveBackReferencesShouldPropagateAnArrayElementFailureBeforeReplacingAKnownCreationIDKey(): Unit = {
    val previous = Invocation(
      methodName = MethodName("Email/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "list": [
            |    {
            |      "id": "message0"
            |    },
            |    {
            |      "id": "message1"
            |    }
            |  ]
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("get"))
    val context = ProcessingContext(
      creationIds = Map(ClientId("created") -> ServerId("server123")),
      invocations = Map(MethodCallId("get") -> previous))
    val invocation = Invocation(
      methodName = MethodName("Mailbox/get"),
      arguments = Arguments(Json.parse(
          """{
            |  "#created": [
            |    {
            |      "#ids": {
            |        "resultOf": "get",
            |        "name": "Email/get",
            |        "path": "/missing"
            |      }
            |    }
            |  ]
            |}""".stripMargin).as[JsObject]),
      methodCallId = MethodCallId("current"))

    val result = context.resolveBackReferences(invocation)

    assertThat(result.isLeft).isTrue()
    assertThat(result.left.toOption.get.message).contains("Expected path missing was missing")
  }
}
