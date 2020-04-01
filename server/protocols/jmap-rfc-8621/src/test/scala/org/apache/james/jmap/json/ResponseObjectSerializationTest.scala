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

import eu.timepit.refined.auto._
import org.apache.james.jmap.json.Fixture._
import org.apache.james.jmap.model.ResponseObject
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._

class ResponseObjectSerializationTest extends AnyWordSpec with Matchers {
  "Deserialize ResponseObject" should {
    "succeed " in {
      val expectedResponseObject = ResponseObject(
        sessionState = "75128aab4b1b",
        methodResponses = Seq(invocation1))

      new Serializer().deserializeResponseObject(
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
          |""".stripMargin) should be(JsSuccess(expectedResponseObject))
    }

    "succeed with many Capability, methodCalls" in {
      val expectedResponseObject = ResponseObject(
        sessionState = "75128aab4b1b",
        methodResponses = Seq(invocation1, invocation2))

      new Serializer().deserializeResponseObject(
        """
          |{
          |  "sessionState": "75128aab4b1b",
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
        sessionState = "75128aab4b1b",
        methodResponses = Seq(invocation1))

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

      new Serializer().serialize(responseObject) should be(Json.parse(expectedJson))
    }
  }
}
