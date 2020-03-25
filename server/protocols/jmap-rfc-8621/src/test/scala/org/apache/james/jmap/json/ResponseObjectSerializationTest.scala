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
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._

class ResponseObjectSerializationTest extends PlaySpec {
  "Deserialize ResponseObject" must {
    "succeed " in {
      val expectedResponseObject = ResponseObject(
        sessionState = "75128aab4b1b",
        methodResponses = Seq(invocation1))

      new Serializer().deserializeResponseObject(
        """
          |{
          |  "methodResponses": [
          |    [ "Core/echo1", {
          |      "arg1": "arg1data",
          |      "arg2": "arg2data"
          |    }, "c1" ]
          |  ],
          |  "sessionState": "75128aab4b1b"
          |}
          |""".stripMargin) must be(JsSuccess(expectedResponseObject))
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
          |    [ "Core/echo1", {
          |      "arg1": "arg1data",
          |      "arg2": "arg2data"
          |    }, "c1" ],
          |    [ "Core/echo2", {
          |      "arg3": "arg3data",
          |      "arg4": "arg4data"
          |    }, "c2" ]
          |  ]
          |}
          |""".stripMargin) must be(JsSuccess(expectedResponseObject))
    }
  }

  "Serialize ResponseObject" must {
    "succeed " in {
      val responseObject: ResponseObject = ResponseObject(
        sessionState = "75128aab4b1b",
        methodResponses = Seq(invocation1))

      val expectedJson = Json.prettyPrint(Json.parse(
        """
          |{
          |  "sessionState": "75128aab4b1b",
          |  "methodResponses": [
          |    [ "Core/echo1", {
          |      "arg1": "arg1data",
          |      "arg2": "arg2data"
          |    }, "c1" ]
          |  ]
          |}
          |""".stripMargin))

      Json.parse(new Serializer().serialize(responseObject)) must be(Json.parse(expectedJson))
    }
  }
}
