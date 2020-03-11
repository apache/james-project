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
package org.apache.james.jmap.rfc

import org.apache.james.jmap.rfc.model.Invocation
import org.apache.james.jmap.rfc.model.Invocation.{Arguments, MethodCallId, MethodName}
import org.apache.james.jmap.rfc.model.RequestObject.Capability
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import eu.timepit.refined.auto._

class InvocationTest extends PlaySpec {
  "Deserialize MethodName" must {
    "succeed when deserialize from JsString" in {
      val jsValue: JsValue = JsString("Core/echo")
      val actualValue = Json.fromJson[MethodName](jsValue)

      actualValue must be(JsSuccess(MethodName("Core/echo")))
      actualValue.get mustBe a[MethodName]
    }

    "be instanceOf JsError when deserialize with wrong value type" in {
      val jsValue: JsValue = JsBoolean(true)
      Json.fromJson[Capability](jsValue) mustBe a[JsError]
    }

    "succeed with Capability value class type but not same instanceOf MethodName" in {
      val jsValue: JsValue = JsString("Core/echo")
      val actualValue = Json.fromJson[Capability](jsValue).get

      actualValue must not be a[MethodName]
      actualValue mustBe a[Capability]
    }
  }

  "Serialize MethodName" must {
    "succeed when write to string" in {
      val methodName: MethodName = MethodName("Core/echo")
      val expectedMethodName: JsValue = JsString("Core/echo")

      Json.toJson[MethodName](methodName) must be(expectedMethodName)
    }
  }

  "Deserialize MethodCallId" must {
    "succeed when deserialize from JsString" in {
      val jsValue: JsValue = JsString("c1")
      val actualValue = Json.fromJson[MethodCallId](jsValue)

      actualValue must be(JsSuccess(MethodCallId("c1")))
      actualValue.get mustBe a[MethodCallId]
    }

    "be instanceOf JsError when deserialize with wrong value type" in {
      val jsValue: JsValue = JsBoolean(true)
      Json.fromJson[MethodCallId](jsValue) mustBe a[JsError]
    }

    "succeed with Capability value class type but not same instanceOf MethodCallId" in {
      val jsValue: JsValue = JsString("c1")
      val actualValue = Json.fromJson[Capability](jsValue).get

      actualValue must not be a[MethodCallId]
      actualValue mustBe a[Capability]
    }
  }

  "Serialize MethodCallId" must {
    "succeed when write to string" in {
      val methodCallId: MethodCallId = MethodCallId("c1")
      val expectedMethodCallId: JsValue = JsString("c1")

      Json.toJson[MethodCallId](methodCallId) must be(expectedMethodCallId)
    }
  }

  "Deserialize Arguments" must {
    "succeed when deserialize from JsString" in {
      val jsValue: JsValue = Json.parse("""{"arg1":"arg1data","arg2":"arg2data"}""")
      val argumentObject: JsObject = Json.obj("arg1" -> "arg1data", "arg2" -> "arg2data")
      val expectedArgument: Arguments = Arguments(argumentObject)
      val actualArgument = Json.fromJson[Arguments](jsValue)

      actualArgument must be(JsSuccess(expectedArgument))
      actualArgument.get mustBe a[Arguments]
    }

    "be instanceOf JsError when deserialize with wrong value type" in {
      val jsValue: JsValue = JsBoolean(true)
      Json.fromJson[Arguments](jsValue) mustBe a[JsError]
    }

    "succeed with JsObject value class type but not same instanceOf Arguments" in {
      val jsObjectValue: JsObject = Json.obj("arg1" -> "arg1data", "arg2" -> "arg2data")
      val actualArgument = Json.fromJson[JsObject](jsObjectValue)

      actualArgument must be(JsSuccess(jsObjectValue))
      actualArgument.get must not be a[Arguments]
    }
  }

  "Serialize Arguments" must {
    "succeed when write to string" in {
      val argument: Arguments = Arguments(Json.obj("arg1" -> "arg1data", "arg2" -> "arg2data"))
      val expectedValue: JsValue = Json.parse("""{"arg1":"arg1data","arg2":"arg2data"}""")

      Json.toJson[Arguments](argument) must be(expectedValue)
    }
  }

  "Deserialize Invocation" must {
    "succeed when deserialize" in {
      val methodName: MethodName = MethodName("Core/echo")
      val argument: Arguments = Arguments(Json.obj("arg1" -> "arg1data", "arg2" -> "arg2data"))
      val methodCallId: MethodCallId = MethodCallId("c1")
      val expectedInvocation: Invocation = Invocation(methodName, argument, methodCallId)

      val invocationJsValue: JsValue = Json.parse(
        """["Core/echo",{"arg1": "arg1data","arg2":"arg2data"},"c1"]""").as[JsArray]
      Json.fromJson[Invocation](invocationJsValue) must be(JsSuccess(expectedInvocation))
    }

    "be instanceOf JsError when deserialize with wrong value type" in {
      val invocationJsValue: JsValue = JsBoolean(true)
      Json.fromJson[Invocation](invocationJsValue) mustBe a[JsError]
    }
  }

  "Serialize Invocation" must {
    "succeed when write to string" in {
      val methodName: MethodName = MethodName("Core/echo")
      val argument: Arguments = Arguments(Json.obj("arg1" -> "arg1data", "arg2" -> "arg2data"))
      val methodCallId: MethodCallId = MethodCallId("c1")
      val invocation: Invocation = Invocation(methodName, argument, methodCallId)

      val expectedInvocation: JsValue = Json.parse(
        """["Core/echo",{"arg1": "arg1data","arg2":"arg2data"},"c1"]""")
      Json.toJson[Invocation](invocation) must be(expectedInvocation)
    }
  }
}
