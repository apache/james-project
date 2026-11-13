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
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.MethodName
import org.apache.james.jmap.core.{ClientId, JmapRfc8621Configuration, ResponseObject, ServerId}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.{CoreEchoMethod, InvocationWithContext, Method}
import org.apache.james.mailbox.MailboxSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import play.api.libs.json.{JsObject, JsValue, Json}
import reactor.core.scala.publisher.SFlux

class JMAPApiCreatedIdsTest {
  private val configuration = JmapRfc8621Configuration("http://localhost", "ws://localhost")

  private val createMethod = new Method {
    override val methodName: MethodName = MethodName("Test/create")
    override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE)

    override def process(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, session: MailboxSession): SFlux[InvocationWithContext] = {
      val createdContext = invocation.processingContext.recordCreatedId(ClientId("new"), ServerId("new-server"))
      val createdResponse = invocation.copy(processingContext = createdContext)
      val shouldFailImplicitCall = (invocation.invocation.arguments.value \ "failAfterCreate").asOpt[Boolean].contains(true)
      if (shouldFailImplicitCall) {
        SFlux.concat(SFlux.just(createdResponse), SFlux.error[InvocationWithContext](new RuntimeException("Implicit call failed")))
      } else {
        val implicitResponse = invocation.invocation.copy(methodName = MethodName("Test/implicit"))
        SFlux.just(createdResponse, createdResponse.copy(invocation = implicitResponse))
      }
    }
  }

  private val failingMethod = new Method {
    override val methodName: MethodName = MethodName("Test/fail")
    override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE)

    override def process(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, session: MailboxSession): SFlux[InvocationWithContext] =
      SFlux.error(new RuntimeException("Failed call"))
  }

  private val readMethod = new Method {
    override val methodName: MethodName = MethodName("Test/read")
    override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE)

    override def process(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, session: MailboxSession): SFlux[InvocationWithContext] =
      SFlux.just(invocation)
  }

  private def process(requestJson: String): ResponseObject = {
    val request = ResponseSerializer.deserializeRequestObject(requestJson).get
    val api = new JMAPApi(Set(new CoreEchoMethod, createMethod, failingMethod, readMethod), Set(JMAP_CORE), configuration)
    api.process(request, mock(classOf[MailboxSession])).block()
  }

  private def argumentsOf(response: ResponseObject, callId: String, methodName: String): JsObject =
    response.methodResponses.find(invocation =>
      invocation.methodCallId.value.value == callId && invocation.methodName.value.value == methodName)
      .map(_.arguments.value)
      .getOrElse(throw new AssertionError(s"Missing response $methodName for call $callId"))

  private def createdIdsOf(response: ResponseObject): JsValue =
    (ResponseSerializer.serialize(response) \ "createdIds").get

  @Test
  def shouldReturnInitialIdsForEmptyRequest(): Unit = {
    val response = process(
      """{
        |  "using": ["urn:ietf:params:jmap:core"],
        |  "createdIds": {"old": "old-server"},
        |  "methodCalls": []
        |}""".stripMargin)

    assertThat(createdIdsOf(response)).isEqualTo(Json.obj("old" -> "old-server"))
    assertThat(response.methodResponses).isEqualTo(Seq.empty)
  }

  @Test
  def shouldReturnEmptyMapWhenRequested(): Unit = {
    val response = process(
      """{
        |  "using": ["urn:ietf:params:jmap:core"],
        |  "createdIds": {},
        |  "methodCalls": []
        |}""".stripMargin)

    assertThat(createdIdsOf(response)).isEqualTo(Json.obj())
  }

  @Test
  def shouldResolveInitialIdsAndKeepThemAfterAFailedCall(): Unit = {
    val response = process(
      """{
        |  "using": ["urn:ietf:params:jmap:core"],
        |  "createdIds": {"old": "old-server"},
        |  "methodCalls": [
        |    ["Test/read", {"id": "#old"}, "read"],
        |    ["Unknown/method", {}, "unknown"]
        |  ]
        |}""".stripMargin)

    assertThat(argumentsOf(response, "read", "Test/read")).isEqualTo(Json.obj("id" -> "old-server"))
    assertThat(argumentsOf(response, "unknown", "error")).isEqualTo(Json.obj("type" -> "unknownMethod"))
    assertThat(createdIdsOf(response)).isEqualTo(Json.obj("old" -> "old-server"))
  }

  @Test
  def shouldKeepLatestCreationAcrossMultipleResponsesAndAFinalError(): Unit = {
    val response = process(
      """{
        |  "using": ["urn:ietf:params:jmap:core"],
        |  "createdIds": {"old": "old-server", "new": "obsolete"},
        |  "methodCalls": [
        |    ["Test/create", {}, "create"],
        |    ["Test/read", {"id": "#new"}, "read"],
        |    ["Core/echo", {"#id": {"resultOf": "missing", "name": "Core/echo", "path": "/id"}}, "invalid"]
        |  ]
        |}""".stripMargin)

    assertThat(response.methodResponses.map(_.methodName.value.value))
      .isEqualTo(Seq("Test/create", "Test/implicit", "Test/read", "error"))
    assertThat(argumentsOf(response, "read", "Test/read")).isEqualTo(Json.obj("id" -> "new-server"))
    assertThat((argumentsOf(response, "invalid", "error") \ "type").as[String]).isEqualTo("invalidResultReference")
    assertThat(createdIdsOf(response)).isEqualTo(Json.obj("old" -> "old-server", "new" -> "new-server"))
  }

  @Test
  def shouldKeepCreationsWhenLastMethodFails(): Unit = {
    val response = process(
      """{
        |  "using": ["urn:ietf:params:jmap:core"],
        |  "createdIds": {},
        |  "methodCalls": [
        |    ["Test/create", {}, "create"],
        |    ["Test/fail", {}, "fail"]
        |  ]
        |}""".stripMargin)

    assertThat((argumentsOf(response, "fail", "error") \ "type").as[String]).isEqualTo("serverFail")
    assertThat(createdIdsOf(response)).isEqualTo(Json.obj("new" -> "new-server"))
  }

  @Test
  def shouldKeepCreationsWhenAnImplicitResponseFails(): Unit = {
    val response = process(
      """{
        |  "using": ["urn:ietf:params:jmap:core"],
        |  "createdIds": {"new": "obsolete"},
        |  "methodCalls": [
        |    ["Test/create", {"failAfterCreate": true}, "create"],
        |    ["Test/read", {"id": "#new"}, "read"]
        |  ]
        |}""".stripMargin)

    assertThat((argumentsOf(response, "create", "error") \ "type").as[String]).isEqualTo("serverFail")
    assertThat(argumentsOf(response, "read", "Test/read")).isEqualTo(Json.obj("id" -> "new-server"))
    assertThat(createdIdsOf(response)).isEqualTo(Json.obj("new" -> "new-server"))
  }

  @Test
  def shouldOmitMapWhenNotRequestedEvenAfterCreation(): Unit = {
    val response = process(
      """{
        |  "using": ["urn:ietf:params:jmap:core"],
        |  "methodCalls": [["Test/create", {}, "create"]]
        |}""".stripMargin)

    assertThat(ResponseSerializer.serialize(response).keys.contains("createdIds")).isFalse()
  }
}
