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
import org.apache.james.jmap.core.{ClientId, JmapRfc8621Configuration, ServerId}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.{CoreEchoMethod, InvocationWithContext, Method}
import org.apache.james.mailbox.MailboxSession
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import play.api.libs.json.{JsObject, Json}
import reactor.core.scala.publisher.SFlux

class JMAPApiCreatedIdsTest {
  private val configuration = JmapRfc8621Configuration("http://localhost", "ws://localhost")

  private val createMethod = new Method {
    override val methodName: MethodName = MethodName("Test/create")
    override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE)

    override def process(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, session: MailboxSession): SFlux[InvocationWithContext] = {
      val updated = invocation.copy(processingContext = invocation.processingContext
        .recordCreatedId(ClientId("new"), ServerId("new-server")))
      if ((invocation.invocation.arguments.value \ "failAfterCreate").asOpt[Boolean].contains(true)) {
        SFlux.concat(SFlux.just(updated), SFlux.error[InvocationWithContext](new RuntimeException("Implicit call failed")))
      } else {
        SFlux.just(updated, updated.copy(invocation = invocation.invocation.copy(methodName = MethodName("Test/implicit"))))
      }
    }
  }

  private val failingMethod = new Method {
    override val methodName: MethodName = MethodName("Test/fail")
    override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE)

    override def process(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, session: MailboxSession): SFlux[InvocationWithContext] =
      SFlux.error(new RuntimeException("Failed call"))
  }

  private def process(json: JsObject): JsObject = {
    val request = ResponseSerializer.deserializeRequestObject(json).get
    val api = new JMAPApi(Set(new CoreEchoMethod, createMethod, failingMethod), Set(JMAP_CORE), configuration)
    ResponseSerializer.serialize(api.process(request, mock(classOf[MailboxSession])).block())
  }

  @Test
  def shouldReturnInitialIdsForEmptyRequest(): Unit = {
    val result = process(Json.obj("using" -> Json.arr(JMAP_CORE.value), "methodCalls" -> Json.arr(), "createdIds" -> Json.obj("old" -> "old-server")))
    assert((result \ "createdIds").as[JsObject] == Json.obj("old" -> "old-server"))
    assert((result \ "methodResponses").as[Seq[JsObject]].isEmpty)
  }

  @Test
  def shouldReturnEmptyMapWhenRequested(): Unit = {
    val result = process(Json.obj("using" -> Json.arr(JMAP_CORE.value), "methodCalls" -> Json.arr(), "createdIds" -> Json.obj()))
    assert((result \ "createdIds").as[JsObject] == Json.obj())
  }

  @Test
  def shouldResolveInitialIdsAndKeepThemAfterAFailedCall(): Unit = {
    val result = process(Json.obj("using" -> Json.arr(JMAP_CORE.value), "createdIds" -> Json.obj("old" -> "old-server"),
      "methodCalls" -> Json.arr(Json.arr("Core/echo", Json.obj("id" -> "#old"), "echo"), Json.arr("Unknown/method", Json.obj(), "error"))))
    assert((result \ "methodResponses")(0)(1)("id").as[String] == "old-server")
    assert((result \ "methodResponses")(1)(1)("type").as[String] == "unknownMethod")
    assert((result \ "createdIds").as[JsObject] == Json.obj("old" -> "old-server"))
  }

  @Test
  def shouldKeepLatestCreationAcrossMultipleResponsesAndAFinalError(): Unit = {
    val result = process(Json.obj("using" -> Json.arr(JMAP_CORE.value), "createdIds" -> Json.obj("old" -> "old-server", "new" -> "obsolete"),
      "methodCalls" -> Json.arr(Json.arr("Test/create", Json.obj(), "create"), Json.arr("Core/echo", Json.obj("id" -> "#new"), "echo"),
        Json.arr("Core/echo", Json.obj("#id" -> Json.obj("resultOf" -> "missing", "name" -> "Core/echo", "path" -> "/id")), "error"))))
    assert((result \ "methodResponses").as[Seq[play.api.libs.json.JsValue]].size == 4)
    assert((result \ "methodResponses")(2)(1)("id").as[String] == "new-server")
    assert((result \ "methodResponses")(3)(1)("type").as[String] == "invalidResultReference")
    assert((result \ "createdIds").as[JsObject] == Json.obj("old" -> "old-server", "new" -> "new-server"))
  }

  @Test
  def shouldKeepCreationsWhenLastMethodFails(): Unit = {
    val result = process(Json.obj("using" -> Json.arr(JMAP_CORE.value), "createdIds" -> Json.obj(),
      "methodCalls" -> Json.arr(Json.arr("Test/create", Json.obj(), "create"), Json.arr("Test/fail", Json.obj(), "error"))))
    assert((result \ "methodResponses")(2)(1)("type").as[String] == "serverFail")
    assert((result \ "createdIds").as[JsObject] == Json.obj("new" -> "new-server"))
  }

  @Test
  def shouldKeepCreationsWhenAnImplicitResponseFails(): Unit = {
    val result = process(Json.obj("using" -> Json.arr(JMAP_CORE.value), "createdIds" -> Json.obj("new" -> "obsolete"),
      "methodCalls" -> Json.arr(Json.arr("Test/create", Json.obj("failAfterCreate" -> true), "create"),
        Json.arr("Core/echo", Json.obj("id" -> "#new"), "echo"))))
    assert((result \ "methodResponses")(1)(1)("type").as[String] == "serverFail")
    assert((result \ "methodResponses")(2)(1)("id").as[String] == "new-server")
    assert((result \ "createdIds").as[JsObject] == Json.obj("new" -> "new-server"))
  }

  @Test
  def shouldOmitMapWhenNotRequestedEvenAfterCreation(): Unit = {
    val result = process(Json.obj("using" -> Json.arr(JMAP_CORE.value), "methodCalls" -> Json.arr(Json.arr("Test/create", Json.obj(), "create"))))
    assert(!result.keys.contains("createdIds"))
  }
}
