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

package org.apache.james.jmap.json

import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.{ResponseObject, UuidState}
import org.apache.james.jmap.json.Fixture._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._

class ResponseObjectSerializationTest extends AnyWordSpec with Matchers {
  "Deserialize ResponseObject" should {
    "succeed " in {
      val expectedResponseObject = ResponseObject(
        sessionState = SESSION_STATE,
        methodResponses = Seq(invocation1))

      ResponseSerializer.deserializeResponseObject(
        s"""
          |{
          |  "methodResponses": [
          |    [ "Core/echo", {
          |      "arg1": "arg1data",
          |      "arg2": "arg2data"
          |    }, "c1" ]
          |  ],
          |  "sessionState": "${SESSION_STATE.value}"
          |}
          |""".stripMargin) should be(JsSuccess(expectedResponseObject))
    }

    "succeed with many Capability, methodCalls" in {
      val expectedResponseObject = ResponseObject(
        sessionState = UuidState.INSTANCE,
        methodResponses = Seq(invocation1, invocation2))

      ResponseSerializer.deserializeResponseObject(
        s"""
          |{
          |  "sessionState": "${SESSION_STATE.value}",
          |  "methodResponses": [
          |    [ "Core/echo", {
          |      "arg1": "arg1data",
          |      "arg2": "arg2data"
          |    }, "c1" ],
          |    [ "Core/echo", {
          |      "arg3": "arg3data",
          |      "arg4": "arg4data"
          |    }, "c2" ]
          |  ]
          |}
          |""".stripMargin) should be(JsSuccess(expectedResponseObject))
    }
  }

  "Serialize ResponseObject" should {
    "succeed " in {
      val responseObject: ResponseObject = ResponseObject(
        sessionState = UuidState.INSTANCE,
        methodResponses = Seq(invocation1))

      val expectedJson = Json.prettyPrint(Json.parse(
        s"""
          |{
          |  "sessionState": "${SESSION_STATE.value}",
          |  "methodResponses": [
          |    [ "Core/echo", {
          |      "arg1": "arg1data",
          |      "arg2": "arg2data"
          |    }, "c1" ]
          |  ]
          |}
          |""".stripMargin))

      ResponseSerializer.serialize(responseObject) should be(Json.parse(expectedJson))
    }
  }
}
