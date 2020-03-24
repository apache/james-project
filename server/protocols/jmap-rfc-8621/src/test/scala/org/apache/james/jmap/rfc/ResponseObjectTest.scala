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
 * **************************************************************/

package org.apache.james.jmap.rfc

import org.apache.james.jmap.model
import org.apache.james.jmap.model.{Invocation, ResponseObject}
import org.apache.james.jmap.model.Invocation.{Arguments, MethodCallId, MethodName}
import org.apache.james.jmap.model.RequestObject.Capability
import org.apache.james.jmap.model.ResponseObject.SessionState
import org.apache.james.jmap.model.ResponseObject
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import eu.timepit.refined.auto._

class ResponseObjectTest extends PlaySpec {

  "Deserialize SessionState" must {
    "succeed with 1 value" in {
      val sessionStateJsValue: JsValue = JsString("75128aab4b1b")
      Json.fromJson[SessionState](sessionStateJsValue) must be(JsSuccess(SessionState("75128aab4b1b")))
    }

    "failed with wrong value type" in {
      val sessionStateJsValue: JsValue = JsBoolean(true)
      Json.fromJson[Capability](sessionStateJsValue) must not be (JsSuccess(SessionState("75128aab4b1b")))
    }

    "failed with wrong class type" in {
      val sessionStateJsValue: JsValue = JsBoolean(true)
      Json.fromJson[SessionState](sessionStateJsValue) must not be (JsSuccess(SessionState("75128aab4b1b")))
    }
  }

  "Serialize SessionState" must {
    "succeed " in {
      val sessionState: SessionState = SessionState("75128aab4b1b")
      val expectedSessionState: JsValue = JsString("75128aab4b1b")
      Json.toJson[SessionState](sessionState) must be(expectedSessionState)
    }
  }

  "Deserialize ResponseObject" must {
    "succeed " in {
      val methodName: MethodName = MethodName("Core/echo")
      val argument: Arguments = Arguments(Json.obj("arg1" -> "arg1data", "arg2" -> "arg2data"))
      val methodCallId: MethodCallId = MethodCallId("c1")
      val expectedInvocation: Invocation = Invocation(methodName, argument, methodCallId)

      ResponseObject.deserialize(
        """
          |{
          |  "methodResponses": [
          |    [ "Core/echo", {
          |      "arg1": "arg1data",
          |      "arg2": "arg2data"
          |    }, "c1" ]
          |  ],
          |  "sessionState": "75128aab4b1b"
          |}
          |""".stripMargin) must be(
        JsSuccess(ResponseObject(
          sessionState = ResponseObject.SessionState("75128aab4b1b"),
          methodResponses = Seq(expectedInvocation))))
    }

    "succeed with many Capability, methodCalls" in {
      val expectedInvocation1: Invocation = Invocation(MethodName("Core/echo"),
        Arguments(Json.obj("arg1" -> "arg1data", "arg2" -> "arg2data")), MethodCallId("c1"))
      val expectedInvocation2: Invocation = Invocation(MethodName("Core/echo2"),
        Arguments(Json.obj("arg3" -> "arg3data", "arg4" -> "arg4data")), MethodCallId("c2"))

      ResponseObject.deserialize(
        """
          |{
          |  "sessionState": "75128aab4b1b",
          |  "methodResponses": [
          |    [ "Core/echo", {
          |      "arg1": "arg1data",
          |      "arg2": "arg2data"
          |    }, "c1" ],
          |    [ "Core/echo2", {
          |      "arg3": "arg3data",
          |      "arg4": "arg4data"
          |    }, "c2" ]
          |  ]
          |}
          |""".stripMargin) must be(
        JsSuccess(model.ResponseObject(
          sessionState = ResponseObject.SessionState("75128aab4b1b"),
          methodResponses = Seq(expectedInvocation1, expectedInvocation2))))
    }
  }

  "Serialize ResponseObject" must {
    "succeed " in {
      val methodName: MethodName = MethodName("Core/echo")
      val argument: Arguments = Arguments(Json.obj("arg1" -> "arg1data", "arg2" -> "arg2data"))
      val methodCallId: MethodCallId = MethodCallId("c1")
      val invocation: Invocation = Invocation(methodName, argument, methodCallId)

      val requestObject: ResponseObject = model.ResponseObject(
        sessionState = ResponseObject.SessionState("75128aab4b1b"),
        methodResponses = Seq(invocation))

      val expectedJson = Json.prettyPrint(Json.parse(
        """
          |{
          |  "sessionState": "75128aab4b1b",
          |  "methodResponses": [
          |    [ "Core/echo", {
          |      "arg1": "arg1data",
          |      "arg2": "arg2data"
          |    }, "c1" ]
          |  ]
          |}
          |""".stripMargin))

      Json.toJson(requestObject) must be(Json.parse(expectedJson))
    }
  }
}
