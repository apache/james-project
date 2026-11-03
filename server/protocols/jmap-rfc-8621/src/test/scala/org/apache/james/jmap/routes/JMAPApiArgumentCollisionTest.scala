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
import org.apache.james.jmap.core.CapabilityIdentifier.JMAP_CORE
import org.apache.james.jmap.core.Invocation.{Arguments, MethodCallId, MethodName}
import org.apache.james.jmap.core.{Invocation, JmapRfc8621Configuration}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.{CoreEchoMethod, Method}
import org.apache.james.mailbox.MailboxSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, spy, times, verify}
import play.api.libs.json.{JsArray, JsObject, Json}

class JMAPApiArgumentCollisionTest {
  @Test
  def resolverShouldRejectNormalAndResultReferenceArguments(): Unit = {
    val previous = Invocation(MethodName("Core/echo"), Arguments(Json.obj("ids" -> Json.arr("x"))), MethodCallId("c0"))
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.parse(
      """{"ids":["y"],"#ids":{"resultOf":"c0","name":"Core/echo","path":"/ids"}}""").as[JsObject]), MethodCallId("c1"))

    val result = ProcessingContext(Map.empty, Map(MethodCallId("c0") -> previous)).resolveBackReferences(invocation)

    assertThat(result).isEqualTo(Left(DuplicateArgumentException("ids")))
  }

  @ParameterizedTest
  @ValueSource(strings = Array(
    """{"ids":["y"],"#ids":{"resultOf":"c0","name":"Core/echo","path":"/ids"}}""",
    """{"#ids":{"resultOf":"c0","name":"Core/echo","path":"/ids"},"ids":["y"]}""",
    """{"ids":["x"],"#ids":{"resultOf":"c0","name":"Core/echo","path":"/ids"}}""",
    """{"ids":null,"#ids":{"resultOf":"c0","name":"Core/echo","path":"/ids"}}""",
    """{"ids":null,"#ids":null}""",
    """{"foo":1,"#foo":{"resultOf":"c0","name":"Core/echo","path":"/ids"}}""",
    """{"ids":["y"],"#ids":{"resultOf":"missing","name":"Core/echo","path":"/ids"}}""",
    """{"ids":["y"],"#ids":{"resultOf":"c0","name":"Core/echo","path":"/missing"}}""",
    """{"ids":["y"],"#ids":{"resultOf":"c0","path":"/ids"}}""",
    """{"#ids":1,"##ids":2}"""))
  def apiShouldRejectCollisionWithoutDispatchAndContinueUsingEarlierResults(arguments: String): Unit = {
    val echo = spy(new CoreEchoMethod)
    val configuration = JmapRfc8621Configuration("http://localhost", "ws://localhost")
    val api = new JMAPApi(Set[Method](echo), Set(JMAP_CORE), configuration)
    val request = ResponseSerializer.deserializeRequestObject(
      s"""{
        |  "using":["urn:ietf:params:jmap:core"],
        |  "methodCalls":[
        |    ["Core/echo",{"ids":["x"]},"c0"],
        |    ["Core/echo",$arguments,"c1"],
        |    ["Core/echo",{"#ids":{"resultOf":"c0","name":"Core/echo","path":"/ids"}},"c2"]
        |  ]
        |}""".stripMargin).get

    val response = ResponseSerializer.serialize(api.process(request, mock(classOf[MailboxSession])).block())
    val responses = (response \ "methodResponses").as[Seq[JsArray]]

    assertThat(responses.size).isEqualTo(3)
    assertThat(responses.head).isEqualTo(Json.arr("Core/echo", Json.obj("ids" -> Json.arr("x")), "c0"))
    assertThat(responses(1).value.head).isEqualTo(Json.toJson("error"))
    assertThat((responses(1).value(1) \ "type").as[String]).isEqualTo("invalidArguments")
    assertThat(responses(1).value(2)).isEqualTo(Json.toJson("c1"))
    assertThat(responses(2)).isEqualTo(Json.arr("Core/echo", Json.obj("ids" -> Json.arr("x")), "c2"))
    verify(echo, times(2)).process(any(), any(), any())
  }

  @ParameterizedTest
  @ValueSource(strings = Array(
    """{"ids":["x"],"label":"kept"}""",
    """{"#ids":null}""",
    """{"ids":1,"##ids":2}""",
    """{"payload":{"foo":1,"#foo":{"literal":true}}}"""))
  def resolverShouldPreserveNonCollidingArgumentsAndNestedLiteralObjects(arguments: String): Unit = {
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.parse(arguments).as[JsObject]), MethodCallId("c0"))

    assertThat(ProcessingContext(Map.empty, Map.empty).resolveBackReferences(invocation)).isEqualTo(Right(invocation))
  }

  @Test
  def resolverShouldResolveMultipleNonCollidingArgumentsAndPreserveReferencedObjects(): Unit = {
    val payload = Json.obj("foo" -> 1, "#foo" -> Json.obj("literal" -> true))
    val previous = Invocation(MethodName("Core/echo"), Arguments(Json.obj("payload" -> payload)), MethodCallId("c0"))
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.parse(
      """{"#value":{"resultOf":"c0","name":"Core/echo","path":"/payload"},"label":"kept"}""").as[JsObject]), MethodCallId("c1"))

    assertThat(ProcessingContext(Map.empty, Map(MethodCallId("c0") -> previous)).resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj("value" -> payload, "label" -> "kept")))))
  }

  @ParameterizedTest
  @ValueSource(strings = Array(
    """{"resultOf":"missing","name":"Core/echo","path":"/ids"}""",
    """{"resultOf":"c0","name":"Mailbox/get","path":"/ids"}""",
    """{"resultOf":"c0","name":"Core/echo","path":"/missing"}"""))
  def apiShouldKeepInvalidResultReferenceForNonCollidingReferenceErrors(reference: String): Unit = {
    val echo = spy(new CoreEchoMethod)
    val configuration = JmapRfc8621Configuration("http://localhost", "ws://localhost")
    val api = new JMAPApi(Set[Method](echo), Set(JMAP_CORE), configuration)
    val request = ResponseSerializer.deserializeRequestObject(
      s"""{
         |  "using":["urn:ietf:params:jmap:core"],
         |  "methodCalls":[
         |    ["Core/echo",{"ids":["x"]},"c0"],
         |    ["Core/echo",{"#ids":$reference},"c1"]
         |  ]
         |}""".stripMargin).get

    val response = ResponseSerializer.serialize(api.process(request, mock(classOf[MailboxSession])).block())
    val error = (response \ "methodResponses").as[Seq[JsArray]].apply(1)

    assertThat(error.value.head).isEqualTo(Json.toJson("error"))
    assertThat((error.value(1) \ "type").as[String]).isEqualTo("invalidResultReference")
    assertThat(error.value(2)).isEqualTo(Json.toJson("c1"))
    verify(echo, times(1)).process(any(), any(), any())
  }
}
