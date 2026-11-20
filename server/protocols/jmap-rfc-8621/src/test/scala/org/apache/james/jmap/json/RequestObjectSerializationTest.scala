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

import eu.timepit.refined.refineV
import eu.timepit.refined.string.Uri
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.RequestObject
import org.apache.james.jmap.json.Fixture._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._


class RequestObjectSerializationTest extends AnyWordSpec with Matchers {
  "Deserialize RequestObject" should {
    "succeed when deserialize from JsString without CreatedIds" in {
      val expectedRequestObject = RequestObject(
        using = Set(coreIdentifier),
        methodCalls = Seq(invocation1),
        createdIds = Option.empty)

      ResponseSerializer
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
        using = Set(coreIdentifier),
        methodCalls = Seq(invocation1),
        createdIds = Option(createdIds))

      ResponseSerializer
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
        using = Set(coreIdentifier, mailIdentifier),
        methodCalls = Seq(invocation1, invocation2),
        createdIds = Option.empty)

      ResponseSerializer
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
    "succeed with a capability that is not a well-known one" in {
      val customIdentifier: CapabilityIdentifier = refineV[Uri]("com:example:custom").toOption.get
      ResponseSerializer
        .deserializeRequestObject(
          """
            |{
            |  "using": [ "urn:ietf:params:jmap:core", "com:example:custom"],
            |  "methodCalls": []
            |}
            |""".stripMargin).map(_.`using`) should equal(JsSuccess(Set(coreIdentifier, customIdentifier)))
    }

    "fail with the URI validation error when a capability is not a valid URI" in {
      val invalid = "urn:ietf:params:jmap:core invalid"
      val expectedMessage: String = refineV[Uri](invalid).swap.toOption.get

      val result = ResponseSerializer
        .deserializeRequestObject(
          s"""
            |{
            |  "using": [ "$invalid"],
            |  "methodCalls": []
            |}
            |""".stripMargin)

      result.isError should be(true)
      result.asInstanceOf[JsError].errors.flatMap(_._2).flatMap(_.messages) should contain(expectedMessage)
    }

    "fail when a capability is not a string" in {
      ResponseSerializer
        .deserializeRequestObject(
          """
            |{
            |  "using": [ 42 ],
            |  "methodCalls": []
            |}
            |""".stripMargin).isError should be(true)
    }
  }

  "Serialize RequestObject" should {
    "succeed when write to string without CreatedIds" in {
      val actualValue = ResponseSerializer.serialize(
        RequestObject(
          using = Set(coreIdentifier, mailIdentifier),
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
      val actualValue = ResponseSerializer.serialize(
        RequestObject(
          using = Set(coreIdentifier, mailIdentifier),
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