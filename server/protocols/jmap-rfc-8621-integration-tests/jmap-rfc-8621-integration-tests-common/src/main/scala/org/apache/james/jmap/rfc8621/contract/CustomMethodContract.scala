/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.rfc8621.contract

import com.google.inject.AbstractModule
import com.google.inject.multibindings.{Multibinder, ProvidesIntoSet}
import eu.timepit.refined.auto._
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured._
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.MethodName
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.{Capability, CapabilityProperties}
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.method.{InvocationWithContext, Method}
import org.apache.james.jmap.rfc8621.contract.CustomMethodContract.CUSTOM
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Test}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

object CustomMethodContract {
  val CUSTOM: CapabilityIdentifier = "urn:apache:james:params:jmap:custom"
}

case class CustomCapabilityProperties() extends CapabilityProperties

case class CustomCapability(properties: CustomCapabilityProperties = CustomCapabilityProperties(), identifier: CapabilityIdentifier = CUSTOM) extends Capability

class CustomCapabilitiesModule extends AbstractModule {
  @ProvidesIntoSet
  private def capability(): Capability = CustomCapability()
}

class CustomMethodModule extends AbstractModule {
  override protected def configure(): Unit = {
    install(new CustomCapabilitiesModule)
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[CustomMethod])
  }
}

class CustomMethod extends Method {

  override val methodName = MethodName("Custom/echo")

  override def process(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession): Publisher[InvocationWithContext] = SMono.just(invocation)

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, CUSTOM)
}

trait CustomMethodContract {

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  @Test
  def customMethodShouldRespondOKWithRFC8621VersionAndSupportedMethod(): Unit = {
    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        """{
          |  "using": [
          |    "urn:ietf:params:jmap:core", "urn:apache:james:params:jmap:custom"
          |  ],
          |  "methodCalls": [
          |    [
          |      "Custom/echo",
          |      {
          |        "arg1": "arg1data",
          |        "arg2": "arg2data"
          |      },
          |      "c1"
          |    ]
          |  ]
          |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .isEqualTo(
        s"""{
          |  "sessionState": "${SESSION_STATE.value}",
          |  "methodResponses": [
          |    [
          |      "Custom/echo",
          |      {
          |        "arg1": "arg1data",
          |        "arg2": "arg2data"
          |      },
          |      "c1"
          |    ]
          |  ]
          |}""".stripMargin)
  }

  @Test
  def customMethodShouldReturnUnknownMethodWhenMissingCoreCapability(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        """{
          |  "using": [
          |    "urn:apache:james:params:jmap:custom"
          |  ],
          |  "methodCalls": [
          |    [
          |      "Custom/echo",
          |      {
          |        "arg1": "arg1data",
          |        "arg2": "arg2data"
          |      },
          |      "c1"
          |    ]
          |  ]
          |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "error",
         |    {
         |      "type": "unknownMethod",
         |      "description": "Missing capability(ies): urn:ietf:params:jmap:core"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def customMethodShouldReturnUnknownMethodWhenMissingCustomCapability(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        """{
          |  "using": [
          |    "urn:ietf:params:jmap:core"
          |  ],
          |  "methodCalls": [
          |    [
          |      "Custom/echo",
          |      {
          |        "arg1": "arg1data",
          |        "arg2": "arg2data"
          |      },
          |      "c1"
          |    ]
          |  ]
          |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "error",
         |    {
         |      "type": "unknownMethod",
         |      "description": "Missing capability(ies): urn:apache:james:params:jmap:custom"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }
}
