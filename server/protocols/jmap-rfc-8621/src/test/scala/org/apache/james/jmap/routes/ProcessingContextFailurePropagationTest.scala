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

class ProcessingContextFailurePropagationTest extends AnyWordSpec with Matchers {
  "Result reference failure propagation" should {
    "reject a reference to a method call that was not recorded" in {
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

      result.isLeft shouldBe true
      result.left.toOption.get.message should include("Back reference could not be resolved")
    }

    "reject a reference whose method name does not match the recorded invocation" in {
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

      result.isLeft shouldBe true
      result.left.toOption.get.message should include("references a")
    }

    "reject a reference to a missing property in the result" in {
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

      result.isLeft shouldBe true
      result.left.toOption.get.message should include("Expected path missing was missing")
    }

    "reject a reference to an array element outside the result" in {
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

      result.isLeft shouldBe true
      result.left.toOption.get.message should include("Supplied array have no")
    }

    "reject an object property path applied to an array" in {
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

      result.isLeft shouldBe true
      result.left.toOption.get.message should include("Expecting a JsObject")
    }

    "propagate a failed reference from a nested object" in {
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

      result.isLeft shouldBe true
      result.left.toOption.get.message should include("Back reference could not be resolved")
    }

    "propagate a failed reference from an object inside an array" in {
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

      result.isLeft shouldBe true
      result.left.toOption.get.message should include("Expected path missing was missing")
    }

    "propagate a failed reference through nested arrays" in {
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

      result.isLeft shouldBe true
      result.left.toOption.get.message should include("Back reference could not be resolved")
    }

    "reject the entire invocation when a valid sibling accompanies a failed nested reference" in {
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

      result.isLeft shouldBe true
      result.left.toOption.get.message should include("Supplied array have no")
    }

    "propagate a failure from one element evaluated by a wildcard path" in {
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

      result.isLeft shouldBe true
      result.left.toOption.get.message should include("Expected path id was missing")
    }
  }

  "Malformed result reference fallback" should {
    "preserve an unknown hash key when resultOf is missing" in {
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

      result shouldBe Right(invocation)
    }

    "preserve an unknown hash key when name is missing" in {
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

      result shouldBe Right(invocation)
    }

    "preserve an unknown hash key when path is missing" in {
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

      result shouldBe Right(invocation)
    }

    "preserve an unknown hash key when resultOf has the wrong JSON type" in {
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

      result shouldBe Right(invocation)
    }

    "preserve an unknown hash key when name has the wrong JSON type" in {
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

      result shouldBe Right(invocation)
    }

    "preserve an unknown hash key when path has the wrong JSON type" in {
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

      result shouldBe Right(invocation)
    }

    "continue traversing nested references inside a malformed result reference object" in {
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

      result.isRight shouldBe true
      result.toOption.get.arguments.value shouldBe Json.parse(
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
          |}""".stripMargin).as[JsObject]
    }

    "propagate a nested failure while falling back from a malformed result reference" in {
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

      result.isLeft shouldBe true
      result.left.toOption.get.message should include("Back reference could not be resolved")
    }
  }

  "Creation ID map keys" should {
    "resolve a known creation ID key when its value is not a result reference" in {
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

      result.isRight shouldBe true
      result.toOption.get.arguments.value shouldBe Json.parse(
        """{
          |  "update": {
          |    "server123": {
          |      "name": "child"
          |    }
          |  }
          |}""".stripMargin).as[JsObject]
    }

    "preserve an unknown creation ID key while resolving its nested result reference" in {
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

      result.isRight shouldBe true
      result.toOption.get.arguments.value shouldBe Json.parse(
        """{
          |  "update": {
          |    "#unknown": {
          |      "ids": [
          |        "message0",
          |        "message1"
          |      ]
          |    }
          |  }
          |}""".stripMargin).as[JsObject]
    }

    "fall back to a known creation ID key when result reference deserialization fails" in {
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

      result.isRight shouldBe true
      result.toOption.get.arguments.value shouldBe Json.parse(
        """{
          |  "update": {
          |    "server123": {
          |      "resultOf": 42,
          |      "name": "Email/get",
          |      "path": "/list/*/id"
          |    }
          |  }
          |}""".stripMargin).as[JsObject]
    }

    "prefer a well formed result reference over a matching creation ID map key" in {
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

      result.isRight shouldBe true
      result.toOption.get.arguments.value shouldBe Json.parse(
        """{
          |  "created": "message0"
          |}""".stripMargin).as[JsObject]
    }

    "return a result reference failure instead of falling back to a matching creation ID key" in {
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

      result.isLeft shouldBe true
      result.left.toOption.get.message should include("Back reference could not be resolved")
    }

    "propagate an array element failure before replacing a known creation ID key" in {
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

      result.isLeft shouldBe true
      result.left.toOption.get.message should include("Expected path missing was missing")
    }
  }
}
