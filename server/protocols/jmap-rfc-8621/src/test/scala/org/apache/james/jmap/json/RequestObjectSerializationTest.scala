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
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.CreatedIds.{ClientId, ServerId}
import org.apache.james.jmap.model.Id.Id
import org.apache.james.jmap.model.Invocation.{Arguments, MethodCallId, MethodName}
import org.apache.james.jmap.model.{CreatedIds, Invocation, RequestObject}
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._

class RequestObjectSerializationTest extends PlaySpec {
  private val id: Id = "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8"
  private val createdIds: CreatedIds = CreatedIds(Map(ClientId(id) -> ServerId(id)))
  private val coreIdentifier: CapabilityIdentifier = "urn:ietf:params:jmap:core"
  private val mailIdentifier: CapabilityIdentifier = "urn:ietf:params:jmap:mail"
  private val invocation1: Invocation = Invocation(
    methodName = MethodName("Core/echo1"),
    arguments = Arguments(Json.obj("arg1" -> "arg1data", "arg2" -> "arg2data")),
    methodCallId = MethodCallId("c1"))
  private val invocation2: Invocation = Invocation(
    methodName = MethodName("Core/echo2"),
    arguments = Arguments(Json.obj("arg3" -> "arg3data", "arg4" -> "arg4data")),
    methodCallId = MethodCallId("c2")
  )

  "Deserialize RequestObject" must {
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
            |    [ "Core/echo1", {
            |      "arg1": "arg1data",
            |      "arg2": "arg2data"
            |    }, "c1" ]
            |  ]
            |}
            |""".stripMargin) must equal(JsSuccess(expectedRequestObject))
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
            |    [ "Core/echo1", {
            |      "arg1": "arg1data",
            |      "arg2": "arg2data"
            |    }, "c1" ]
            |  ],
            |  "createdIds":{"aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8":"aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8"}
            |}
            |""".stripMargin) must equal(JsSuccess(expectedRequestObject))
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
            |""".stripMargin) must equal(JsSuccess(expectedRequestObject))
    }
  }

  "Serialize RequestObject" must {
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
          |    [ "Core/echo1", {
          |      "arg1": "arg1data",
          |      "arg2": "arg2data"
          |    }, "c1" ]
          |  ]
          |}
          |""".stripMargin))

      Json.parse(actualValue) must equal(Json.parse(expectedValue))
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
          |    [ "Core/echo1", {
          |      "arg1": "arg1data",
          |      "arg2": "arg2data"
          |    }, "c1" ]
          |  ],
          |  "createdIds":{"aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8":"aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8"}
          |}
          |""".stripMargin))

      Json.parse(actualValue) must equal(Json.parse(expectedValue))
    }
  }
}