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

import org.apache.james.jmap.json.Fixture._
import org.apache.james.jmap.model.RequestObject
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._

class RequestObjectSerializationTest extends AnyWordSpec with Matchers {
  "Deserialize RequestObject" should {
    "succeed when deserialize from JsString without CreatedIds" in {
      val expectedRequestObject = RequestObject(
        using = Seq(coreIdentifier),
        methodCalls = Seq(invocation1),
        createdIds = Option.empty)

      new Serializer()
        .deserializeRequestObject(
          """
            |{
            |  "using": [ "urn:ietf:params:jmap:core"],
            |  "methodCalls": [
            |    [ "Core/echo", {
            |      "arg1": "arg1data",
            |      "arg2": "arg2data"
            |    }, "c1" ]
            |  ]
            |}
            |""".stripMargin) should equal(JsSuccess(expectedRequestObject))
    }

    "succeed when deserialize from JsString with CreatedIds" in {
      val expectedRequestObject = RequestObject(
        using = Seq(coreIdentifier),
        methodCalls = Seq(invocation1),
        createdIds = Option(createdIds))

      new Serializer()
        .deserializeRequestObject(
          """
            |{
            |  "using": [ "urn:ietf:params:jmap:core"],
            |  "methodCalls": [
            |    [ "Core/echo", {
            |      "arg1": "arg1data",
            |      "arg2": "arg2data"
            |    }, "c1" ]
            |  ],
            |  "createdIds":{"aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8":"aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8"}
            |}
            |""".stripMargin) should equal(JsSuccess(expectedRequestObject))
    }

    "succeed with many Capability, methodCalls without CreatedIds" in {
      val expectedRequestObject = RequestObject(
        using = Seq(coreIdentifier, mailIdentifier),
        methodCalls = Seq(invocation1, invocation2),
        createdIds = Option.empty)

      new Serializer()
        .deserializeRequestObject(
          """
            |{
            |  "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
            |  "methodCalls": [
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
            |""".stripMargin) should equal(JsSuccess(expectedRequestObject))
    }
  }

  "Serialize RequestObject" should {
    "succeed when write to string without CreatedIds" in {
      val actualValue = new Serializer().serialize(
        RequestObject(
          using = Seq(coreIdentifier, mailIdentifier),
          methodCalls = Seq(invocation1),
          createdIds = Option.empty))

      val expectedValue = Json.prettyPrint(Json.parse(
        """
          |{
          |  "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
          |  "methodCalls": [
          |    [ "Core/echo", {
          |      "arg1": "arg1data",
          |      "arg2": "arg2data"
          |    }, "c1" ]
          |  ]
          |}
          |""".stripMargin))

      actualValue should equal(Json.parse(expectedValue))
    }

    "succeed when write to string with CreatedIds" in {
      val actualValue = new Serializer().serialize(
        RequestObject(
          using = Seq(coreIdentifier, mailIdentifier),
          methodCalls = Seq(invocation1),
          createdIds = Option(createdIds)))

      val expectedValue = Json.prettyPrint(Json.parse(
        """
          |{
          |  "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
          |  "methodCalls": [
          |    [ "Core/echo", {
          |      "arg1": "arg1data",
          |      "arg2": "arg2data"
          |    }, "c1" ]
          |  ],
          |  "createdIds":{"aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8":"aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8"}
          |}
          |""".stripMargin))

      actualValue should equal(Json.parse(expectedValue))
    }
  }
}