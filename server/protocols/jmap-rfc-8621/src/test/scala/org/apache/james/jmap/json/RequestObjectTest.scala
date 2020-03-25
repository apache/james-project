/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/

package org.apache.james.jmap.json

import eu.timepit.refined.auto._
//import org.apache.james.jmap.json.Invocation._
//import org.apache.james.jmap.json.RequestObject._
import org.apache.james.jmap.model.CreatedIds.{ClientId, ServerId}
import org.apache.james.jmap.model.Id.Id
import org.apache.james.jmap.model.Invocation.{Arguments, MethodCallId, MethodName}
import org.apache.james.jmap.model.RequestObject.Capability
import org.apache.james.jmap.model.{CreatedIds, Invocation, RequestObject}
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._

class RequestObjectTest extends PlaySpec {
  private val id: Id = "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8"
//
//  "Deserialize Capability" must {
//    "succeed when deserialize from JsString" in {
//      val capabilityJsValue: JsValue = JsString("org.com.test.1")
//      val actualCapability = Json.fromJson[Capability](capabilityJsValue)
//
//      actualCapability must be(JsSuccess(Capability("org.com.test.1")))
//      actualCapability.get mustBe a[Capability]
//    }
//
//    "be instanceOf JsError when deserialize with wrong value type" in {
//      val capabilityJsValue: JsValue = JsBoolean(true)
//      Json.fromJson[Capability](capabilityJsValue) mustBe a[JsError]
//    }
//
//    "succeed with MethodName value class type but not same instanceOf Capability" in {
//      val capabilityJsValue: JsValue = JsString("org.com.test.1")
//      val actualCapability = Json.fromJson[MethodName](capabilityJsValue).get
//
//      actualCapability must not be a[Capability]
//      actualCapability mustBe a[MethodName]
//    }
//  }
//
//  "Serialize Capability" must {
//    "succeed when write to string" in {
//      val capability: Capability = Capability("org.com.test.1")
//      val expectedCapability: JsValue = JsString("org.com.test.1")
//
//      Json.toJson[Capability](capability) must be(expectedCapability)
//    }
//  }
//
//  "Deserialize RequestObject" must {
//    "succeed when deserialize from JsString without CreatedIds" in {
//      val methodName: MethodName = MethodName("Core/echo")
//      val argument: Arguments = Arguments(Json.obj("arg1" -> "arg1data", "arg2" -> "arg2data"))
//      val methodCallId: MethodCallId = MethodCallId("c1")
//      val expectedInvocation: Invocation = Invocation(methodName, argument, methodCallId)
//
//      deserialize(
//        """
//          |{
//          |  "using": [ "urn:ietf:params:jmap:core"],
//          |  "methodCalls": [
//          |    [ "Core/echo", {
//          |      "arg1": "arg1data",
//          |      "arg2": "arg2data"
//          |    }, "c1" ]
//          |  ]
//          |}
//          |""".stripMargin) must be(
//        JsSuccess(RequestObject(
//          using = Seq(Capability("urn:ietf:params:jmap:core")),
//          methodCalls = Seq(expectedInvocation))))
//    }
//
//    "succeed when deserialize from JsString withCreatedIds" in {
//      val methodName: MethodName = MethodName("Core/echo")
//      val argument: Arguments = Arguments(Json.obj("arg1" -> "arg1data", "arg2" -> "arg2data"))
//      val methodCallId: MethodCallId = MethodCallId("c1")
//      val expectedInvocation: Invocation = Invocation(methodName, argument, methodCallId)
//      val expectedCreatedIds: CreatedIds = CreatedIds(Map(
//        ClientId(id) -> ServerId(id)
//      ))
//
//      deserialize(
//        """
//          |{
//          |  "using": [ "urn:ietf:params:jmap:core"],
//          |  "methodCalls": [
//          |    [ "Core/echo", {
//          |      "arg1": "arg1data",
//          |      "arg2": "arg2data"
//          |    }, "c1" ]
//          |  ],
//          |  "createdIds":{"aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8":"aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8"}
//          |}
//          |""".stripMargin) must be(
//        JsSuccess(RequestObject(
//          using = Seq(Capability("urn:ietf:params:jmap:core")),
//          methodCalls = Seq(expectedInvocation),
//          Option(expectedCreatedIds))))
//    }
//
//    "succeed with many Capability, methodCalls without CreatedIds" in {
//      val expectedInvocation1: Invocation = Invocation(MethodName("Core/echo"),
//        Arguments(Json.obj("arg1" -> "arg1data", "arg2" -> "arg2data")), MethodCallId("c1"))
//      val expectedInvocation2: Invocation = Invocation(MethodName("Core/echo2"),
//        Arguments(Json.obj("arg3" -> "arg3data", "arg4" -> "arg4data")), MethodCallId("c2"))
//
//      deserialize(
//        """
//          |{
//          |  "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:core2"],
//          |  "methodCalls": [
//          |    [ "Core/echo", {
//          |      "arg1": "arg1data",
//          |      "arg2": "arg2data"
//          |    }, "c1" ],
//          |    [ "Core/echo2", {
//          |      "arg3": "arg3data",
//          |      "arg4": "arg4data"
//          |    }, "c2" ]
//          |  ]
//          |}
//          |""".stripMargin) must be(
//        JsSuccess(RequestObject(
//          using = Seq(Capability("urn:ietf:params:jmap:core"), Capability("urn:ietf:params:jmap:core2")),
//          methodCalls = Seq(expectedInvocation1, expectedInvocation2))))
//    }
//  }

  "Serialize RequestObject" must {
//    "succeed when write to string without CreatedIds" in {
//      val methodName: MethodName = MethodName("Core/echo")
//      val argument: Arguments = Arguments(Json.obj("arg1" -> "arg1data", "arg2" -> "arg2data"))
//      val methodCallId: MethodCallId = MethodCallId("c1")
//      val invocation: Invocation = Invocation(methodName, argument, methodCallId)
//
//      val requestObject: RequestObject = RequestObject(
//        using = Seq(Capability("urn:ietf:params:jmap:core"), Capability("urn:ietf:params:jmap:core2")),
//        methodCalls = Seq(invocation))
//      val expectedValue = Json.prettyPrint(Json.parse(
//        """
//          |{
//          |  "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:core2"],
//          |  "methodCalls": [
//          |    [ "Core/echo", {
//          |      "arg1": "arg1data",
//          |      "arg2": "arg2data"
//          |    }, "c1" ]
//          |  ]
//          |}
//          |""".stripMargin))
//
//      Json.toJson(requestObject) must be(Json.parse(expectedValue))
//    }
//
//    "succeed when write to string with CreatedIds" in {
//      val methodName: MethodName = MethodName("Core/echo")
//      val argument: Arguments = Arguments(Json.obj("arg1" -> "arg1data", "arg2" -> "arg2data"))
//      val methodCallId: MethodCallId = MethodCallId("c1")
//      val invocation: Invocation = Invocation(methodName, argument, methodCallId)
//      val createdIds: CreatedIds = CreatedIds(Map(
//        ClientId(id) -> ServerId(id)
//      ))
//      val requestObject: RequestObject = RequestObject(
//        using = Seq(Capability("urn:ietf:params:jmap:core"), Capability("urn:ietf:params:jmap:core2")),
//        methodCalls = Seq(invocation),
//        Option(createdIds))
//
//      val expectedValue = Json.prettyPrint(Json.parse(
//        """
//          |{
//          |  "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:core2"],
//          |  "methodCalls": [
//          |    [ "Core/echo", {
//          |      "arg1": "arg1data",
//          |      "arg2": "arg2data"
//          |    }, "c1" ]
//          |  ],
//          |  "createdIds":{"aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8":"aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8"}
//          |}
//          |""".stripMargin))
//
//      Json.toJson(requestObject) must be(Json.parse(expectedValue))
//    }

     "succeed when write to string with CreatedIds" in {
      val methodName: MethodName = MethodName("Core/echo")
      val argument: Arguments = Arguments(Json.obj("arg1" -> "arg1data", "arg2" -> "arg2data"))
      val methodCallId: MethodCallId = MethodCallId("c1")
      val invocation: Invocation = Invocation(methodName, argument, methodCallId)
      val createdIds: CreatedIds = CreatedIds(Map(
        ClientId(id) -> ServerId(id)
      ))
      val requestObject: RequestObject = RequestObject(
        using = Seq(Capability("urn:ietf:params:jmap:core"), Capability("urn:ietf:params:jmap:core2")),
        methodCalls = Seq(invocation),
        Option(createdIds))

      val expectedValue = Json.prettyPrint(Json.parse(
        """
          |{
          |  "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:core2"],
          |  "methodCalls": [
          |    [ "Core/echo", {
          |      "arg1": "arg1data",
          |      "arg2": "arg2data"
          |    }, "c1" ]
          |  ],
          |  "createdIds":{"aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8":"aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8"}
          |}
          |""".stripMargin))

       Json.parse(new Serializer().serialize(requestObject)) must equal(Json.parse(expectedValue))
    }
  }

}