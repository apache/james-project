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
import io.restassured.RestAssured
import io.restassured.builder.RequestSpecBuilder
import io.restassured.http.ContentType
import io.restassured.specification.RequestSpecification
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus
import org.apache.james.jmap.JMAPServer
import org.apache.james.jmap.JMAPUrls.JMAP
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.{ClientId, ServerId}
import org.apache.james.jmap.method.{CoreEchoMethod, DeferredCreationReferences, InvocationWithContext}
import org.apache.james.mailbox.MailboxSession
import org.junit.jupiter.api.{AfterEach, BeforeEach, Test}
import play.api.libs.json.JsPath
import reactor.core.scala.publisher.SFlux

class JMAPApiDeferredCreationReferencesTest {
  private val seedCreationMapping = new CoreEchoMethod {
    override val methodName: MethodName = MethodName("Test/seedCreationMapping")

    override def process(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession): SFlux[InvocationWithContext] =
      SFlux.just(invocation.copy(processingContext = invocation.processingContext.recordCreatedId(ClientId("parent"), ServerId("old-parent"))))
  }
  private val deferredEcho = new CoreEchoMethod with DeferredCreationReferences {
    override val methodName: MethodName = MethodName("Test/deferredEcho")

    override def deferredCreationReferencePaths(arguments: Arguments): Set[JsPath] =
      Set(JsPath \ "create" \ "child" \ "parentId", JsPath \ "create" \ "fromResult" \ "parentId")
  }

  private var jmapServer: JMAPServer = _
  private var requestSpecification: RequestSpecification = _

  @BeforeEach
  def setUp(): Unit = {
    jmapServer = JMAPApiRoutesTest.newServer(Set(new CoreEchoMethod, seedCreationMapping, deferredEcho))
    jmapServer.start()
    requestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .setAccept(ContentType.JSON)
      .setPort(jmapServer.getPort.getValue)
      .setBasePath(JMAP)
      .build()
  }

  @AfterEach
  def tearDown(): Unit = jmapServer.stop()

  @Test
  def shouldDeferOptInCreationValuesWhileResolvingOtherPathsAndResultReferences(): Unit = {
    val response = RestAssured
      .`given`()
        .spec(requestSpecification)
        .auth().preemptive().basic("user1", "password")
        .body("""{
          |  "using": ["urn:ietf:params:jmap:core"],
          |  "methodCalls": [
          |    ["Test/seedCreationMapping", {"id": "result-parent"}, "seed"],
          |    ["Test/deferredEcho", {
          |      "create": {
          |        "child": {"parentId": "#parent", "name": "#parent"},
          |        "fromResult": {"#parentId": {"resultOf": "seed", "name": "Test/seedCreationMapping", "path": "/id"}}
          |      },
          |      "update": {"#parent": {"parentId": "#parent"}}
          |    }, "defer"]
          |  ]
          |}""".stripMargin)
      .when().post()
      .`then`().statusCode(HttpStatus.SC_OK)
      .extract().asString()

    assertThatJson(response).isEqualTo(s"""{
      |  "sessionState": "${SESSION_STATE.value}",
      |  "methodResponses": [
      |    ["Test/seedCreationMapping", {"id": "result-parent"}, "seed"],
      |    ["Test/deferredEcho", {
      |      "create": {
      |        "child": {"parentId": "#parent", "name": "old-parent"},
      |        "fromResult": {"parentId": "result-parent"}
      |      },
      |      "update": {"old-parent": {"parentId": "old-parent"}}
      |    }, "defer"]
      |  ]
      |}""".stripMargin)
  }

  @Test
  def shouldResolveCreationReferencesForMethodsWithoutTheOptInCapability(): Unit = {
    val response = RestAssured
      .`given`()
        .spec(requestSpecification)
        .auth().preemptive().basic("user1", "password")
        .body("""{
          |  "using": ["urn:ietf:params:jmap:core"],
          |  "methodCalls": [
          |    ["Test/seedCreationMapping", {}, "seed"],
          |    ["Core/echo", {"create": {"child": {"parentId": "#parent"}}}, "echo"]
          |  ]
          |}""".stripMargin)
      .when().post()
      .`then`().statusCode(HttpStatus.SC_OK)
      .extract().asString()

    assertThatJson(response).isEqualTo(s"""{
      |  "sessionState": "${SESSION_STATE.value}",
      |  "methodResponses": [
      |    ["Test/seedCreationMapping", {}, "seed"],
      |    ["Core/echo", {"create": {"child": {"parentId": "old-parent"}}}, "echo"]
      |  ]
      |}""".stripMargin)
  }
}
