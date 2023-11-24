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

import java.nio.charset.StandardCharsets
import java.util.Base64

import com.google.common.collect.ImmutableSet
import eu.timepit.refined.auto._
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured
import io.restassured.builder.RequestSpecBuilder
import io.restassured.config.EncoderConfig.encoderConfig
import io.restassured.config.RestAssuredConfig.newConfig
import io.restassured.http.{ContentType, Header, Headers}
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus
import org.apache.james.core.{Domain, Username}
import org.apache.james.domainlist.lib.DomainListConfiguration
import org.apache.james.domainlist.memory.MemoryDomainList
import org.apache.james.jmap.JMAPUrls.JMAP
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.MethodName
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.{DefaultCapabilities, JmapRfc8621Configuration, RequestLevelErrorType}
import org.apache.james.jmap.http.{Authenticator, BasicAuthenticationStrategy, UserProvisioning}
import org.apache.james.jmap.method.{CoreEchoMethod, Method}
import org.apache.james.jmap.routes.JMAPApiRoutesTest._
import org.apache.james.jmap.{JMAPConfiguration, JMAPRoutesHandler, JMAPServer, Version, VersionParser}
import org.apache.james.mailbox.extension.PreDeletionHook
import org.apache.james.mailbox.inmemory.{InMemoryMailboxManager, MemoryMailboxManagerProvider}
import org.apache.james.metrics.tests.RecordingMetricFactory
import org.apache.james.user.memory.MemoryUsersRepository
import org.hamcrest.Matchers.equalTo
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{doReturn, mock, when}
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import reactor.core.scala.publisher.SFlux

object JMAPApiRoutesTest {
  private val TEST_CONFIGURATION: JMAPConfiguration = JMAPConfiguration.builder().enable().randomPort().build()
  private val ACCEPT_JMAP_VERSION_HEADER = "application/json; jmapVersion="
  private val ACCEPT_DRAFT_VERSION_HEADER = ACCEPT_JMAP_VERSION_HEADER + Version.DRAFT.asString()
  private val ACCEPT_RFC8621_VERSION_HEADER = ACCEPT_JMAP_VERSION_HEADER + Version.RFC8621.asString()

  private val empty_set: ImmutableSet[PreDeletionHook] = ImmutableSet.of()
  private val domainList = new MemoryDomainList()
  domainList.configure(DomainListConfiguration.DEFAULT)
  domainList.addDomain(Domain.of("james.org"))

  private val usersRepository = MemoryUsersRepository.withoutVirtualHosting(domainList)
  usersRepository.addUser(Username.of("user1"), "password")

  private val mailboxManager: InMemoryMailboxManager = MemoryMailboxManagerProvider.provideMailboxManager(empty_set)
  private val authenticationStrategy: BasicAuthenticationStrategy = new BasicAuthenticationStrategy(usersRepository, mailboxManager)
  private val AUTHENTICATOR: Authenticator = Authenticator.of(new RecordingMetricFactory, authenticationStrategy)

  private val userProvisionner: UserProvisioning = new UserProvisioning(usersRepository, new RecordingMetricFactory)
  private val JMAP_METHODS: Set[Method] = Set(new CoreEchoMethod)

  private val configuration: JmapRfc8621Configuration = JmapRfc8621Configuration("http://127.0.0.1", "ws://127.0.0.1")
  private val JMAP_API_ROUTE: JMAPApiRoutes = new JMAPApiRoutes(AUTHENTICATOR, userProvisionner, new JMAPApi(JMAP_METHODS, DefaultCapabilities.supported(configuration).map(_.id()), configuration))
  private val ROUTES_HANDLER: ImmutableSet[JMAPRoutesHandler] = ImmutableSet.of(new JMAPRoutesHandler(Version.RFC8621, JMAP_API_ROUTE))

  private val userBase64String: String = Base64.getEncoder.encodeToString("user1:password".getBytes(StandardCharsets.UTF_8))
  
  private val REQUEST_OBJECT: String =
    """{
      |  "using": [
      |    "urn:ietf:params:jmap:core"
      |  ],
      |  "methodCalls": [
      |    [
      |      "Core/echo",
      |      {
      |        "arg1": "arg1data",
      |        "arg2": "arg2data"
      |      },
      |      "c1"
      |    ]
      |  ]
      |}""".stripMargin

  private val REQUEST_OBJECT_WITH_2_ECHO_METHOD_CALL: String =
    """{
      |  "using": [
      |    "urn:ietf:params:jmap:core"
      |  ],
      |  "methodCalls": [
      |    [
      |      "Core/echo",
      |      {
      |        "arg1": "arg1data",
      |        "arg2": "arg2data"
      |      },
      |      "c1"
      |    ],
      |    [
      |    "Core/echo",
      |      {
      |        "arg1": "arg1data",
      |        "arg2": "arg2data"
      |      },
      |      "c2"
      |     ]
      |  ]
      |}""".stripMargin

  private val REQUEST_OBJECT_WITH_UNSUPPORTED_METHOD: String =
    """{
      |  "using": [
      |    "urn:ietf:params:jmap:core"
      |  ],
      |  "methodCalls": [
      |    [
      |      "Core/echo",
      |      {
      |        "arg1": "arg1data",
      |        "arg2": "arg2data"
      |      },
      |      "c1"
      |    ],
      |    [
      |      "error",
      |      {
      |        "type": "Not implemented"
      |      },
      |      "notsupport"
      |    ]
      |  ]
      |}""".stripMargin

  private val RESPONSE_OBJECT: String =
    s"""{
      |  "sessionState": "${SESSION_STATE.value}",
      |  "methodResponses": [
      |    [
      |      "Core/echo",
      |      {
      |        "arg1": "arg1data",
      |        "arg2": "arg2data"
      |      },
      |      "c1"
      |    ]
      |  ]
      |}""".stripMargin

  private val SERVER_FAIL_RESPONSE_OBJECT: String =
    s"""{
      |  "sessionState": "${SESSION_STATE.value}",
      |  "methodResponses": [
      |    [
      |      "error",
      |      {
      |        "type": "serverFail",
      |        "description": "Unexpected Exception occur, the others method may proceed normally"
      |      },
      |      "c1"
      |    ],
      |    [
      |      "Core/echo",
      |      {
      |        "arg1": "arg1data",
      |        "arg2": "arg2data"
      |      },
      |      "c2"
      |    ]
      |  ]
      |}""".stripMargin

  private val RESPONSE_OBJECT_WITH_UNSUPPORTED_METHOD: String =
    s"""{
      |  "sessionState": "${SESSION_STATE.value}",
      |  "methodResponses": [
      |    [
      |      "Core/echo",
      |      {
      |        "arg1": "arg1data",
      |        "arg2": "arg2data"
      |      },
      |      "c1"
      |    ],
      |    [
      |      "error",
      |      {
      |        "type": "unknownMethod"
      |      },
      |      "notsupport"
      |    ]
      |  ]
      |}""".stripMargin

  private val SUPPORTED_VERSIONS = ImmutableSet.of(Version.DRAFT, Version.RFC8621)

  private val WRONG_OBJECT_REQUEST: String =
    """
      |{
      |  "using": [ "urn:ietf:params:jmap:core"],
      |  "methodCalls": {
      |      "arg1": "arg1data",
      |      "arg2": "arg2data"
      |    }
      |}
      |""".stripMargin

  private val NOT_JSON_REQUEST: String =
    """
      |{
      |  "using": [ "urn:ietf:params:jmap:core"],
      |  "methodCalls": {
      |      "arg1": "arg1data",
      |}
      |""".stripMargin

  private val UNKNOWN_CAPABILITY_REQUEST: String =
    """
      |{
      |  "using": [ "urn:ietf:params:jmap:core1"],
      |   "methodCalls": [
      |    [
      |      "Core/echo",
      |      {
      |        "arg1": "arg1data",
      |        "arg2": "arg2data"
      |      },
      |      "c1"
      |    ]
      |  ]
      |}
      |""".stripMargin
}

class JMAPApiRoutesTest extends AnyFlatSpec with BeforeAndAfter with Matchers {

  var jmapServer: JMAPServer = _

  before {
    val versionParser: VersionParser = new VersionParser(SUPPORTED_VERSIONS, JMAPConfiguration.DEFAULT)
    jmapServer = new JMAPServer(TEST_CONFIGURATION, ROUTES_HANDLER, versionParser)
    jmapServer.start()

    RestAssured.requestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .setAccept(ContentType.JSON)
      .setConfig(newConfig.encoderConfig(encoderConfig.defaultContentCharset(StandardCharsets.UTF_8)))
      .setPort(jmapServer.getPort.getValue)
      .setBasePath(JMAP)
      .build
  }

  after {
    jmapServer.stop()
  }

  "RFC-8621 version, GET" should "not supported and return 404 status" in {
    val headers: Headers = Headers.headers(
      new Header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER),
      new Header("Authorization", s"Basic ${userBase64String}")
    )

    RestAssured
      .`given`()
        .headers(headers)
      .when()
        .get
      .`then`
        .statusCode(HttpStatus.SC_NOT_FOUND)
  }

  "RFC-8621 version, POST, without body" should "return 200 status" in {
    val headers: Headers = Headers.headers(
      new Header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER),
      new Header("Authorization", s"Basic ${userBase64String}")
    )

    RestAssured
      .`given`()
        .headers(headers)
      .when()
        .post
      .`then`
        .statusCode(HttpStatus.SC_OK)
  }

  "RFC-8621 version, POST, methods include supported" should "return OK status" in {
    val headers: Headers = Headers.headers(
      new Header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER),
      new Header("Authorization", s"Basic ${userBase64String}")
    )

    val response = RestAssured
      .`given`()
        .headers(headers)
        .body(REQUEST_OBJECT)
      .when()
        .post()
      .`then`
        .statusCode(HttpStatus.SC_OK)
        .contentType(ContentType.JSON)
      .extract()
        .body()
        .asString()

    assertThatJson(response).isEqualTo(RESPONSE_OBJECT)
  }

  "RFC-8621 version, POST, with methods" should "return OK status, ResponseObject depend on method" in {

    val headers: Headers = Headers.headers(
      new Header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER),
      new Header("Authorization", s"Basic ${userBase64String}")
    )

    val response = RestAssured
      .`given`()
        .headers(headers)
        .body(REQUEST_OBJECT_WITH_UNSUPPORTED_METHOD)
      .when()
        .post()
      .`then`
        .statusCode(HttpStatus.SC_OK)
        .contentType(ContentType.JSON)
      .extract()
        .body()
        .asString()

    assertThatJson(response).isEqualTo(RESPONSE_OBJECT_WITH_UNSUPPORTED_METHOD)
  }

  "Draft version, GET" should "return 404 status" in {
    val headers: Headers = Headers.headers(
      new Header(ACCEPT.toString, ACCEPT_DRAFT_VERSION_HEADER),
      new Header("Authorization", s"Basic ${userBase64String}")
    )

    RestAssured
      .`given`()
        .headers(headers)
      .when()
        .get
      .`then`
        .statusCode(HttpStatus.SC_NOT_FOUND)
  }

  "Draft version, POST, without body" should "return 400 status" in {
    val headers: Headers = Headers.headers(
      new Header(ACCEPT.toString, ACCEPT_DRAFT_VERSION_HEADER),
      new Header("Authorization", s"Basic ${userBase64String}")
    )
    RestAssured
      .`given`()
        .headers(headers)
      .when()
        .post
      .`then`
        .statusCode(HttpStatus.SC_NOT_FOUND)
  }

  "RFC-8621 version, POST, with wrong requestObject body" should "return 400 status" in {
    val headers: Headers = Headers.headers(
      new Header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER),
      new Header("Authorization", s"Basic ${userBase64String}")
    )
    RestAssured
      .`given`()
        .headers(headers)
        .body(WRONG_OBJECT_REQUEST)
      .when()
        .post
      .`then`
        .statusCode(HttpStatus.SC_BAD_REQUEST)
        .body("status", equalTo(400))
        .body("type", equalTo(RequestLevelErrorType.NOT_REQUEST.value))
        .body("detail", equalTo("The request was successfully parsed as JSON but did not match the type signature of the Request object: {\"errors\":[{\"path\":\"obj.methodCalls\",\"messages\":[\"error.expected.jsarray\"]}]}"))
  }

  "RFC-8621 version, POST, with not json request body" should "return 400 status" in {
    val headers: Headers = Headers.headers(
      new Header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER),
      new Header("Authorization", s"Basic ${userBase64String}")
    )
    RestAssured
      .`given`()
        .headers(headers)
        .body(NOT_JSON_REQUEST)
      .when()
        .post
      .`then`
        .statusCode(HttpStatus.SC_BAD_REQUEST)
        .body("status", equalTo(400))
        .body("type", equalTo(RequestLevelErrorType.NOT_JSON.value))
        .body("detail", equalTo("The content type of the request was not application/json or the request did not parse as I-JSON: Unexpected character ('}' (code 125)): was expecting double-quote to start field name\n " +
          "at [Source: (reactor.netty.ByteBufMono$ReleasingInputStream); line: 6, column: 2]"))
  }

  "RFC-8621 version, POST, with unknown capability" should "return 400 status" in {
    val headers: Headers = Headers.headers(
      new Header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER),
      new Header("Authorization", s"Basic ${userBase64String}")
    )
    RestAssured
      .`given`()
        .headers(headers)
        .body(UNKNOWN_CAPABILITY_REQUEST)
      .when()
        .post
      .`then`
        .statusCode(HttpStatus.SC_BAD_REQUEST)
        .body("status", equalTo(400))
        .body("type", equalTo(RequestLevelErrorType.UNKNOWN_CAPABILITY.value))
        .body("detail", equalTo("The request used unsupported capabilities: Set(urn:ietf:params:jmap:core1)"))
  }

  "RFC-8621 with random error when processing request " should "return 200, with serverFail error, others method call proceed normally" in {
    val mockCoreEchoMethod = mock(classOf[CoreEchoMethod])

    doReturn(SFlux.error(new RuntimeException("Unexpected Exception occur, the others method may proceed normally")))
      .doCallRealMethod()
      .when(mockCoreEchoMethod)
      .process(any[Set[CapabilityIdentifier]], any(), any())

    when(mockCoreEchoMethod.methodName).thenReturn(MethodName("Core/echo"))
    when(mockCoreEchoMethod.requiredCapabilities).thenReturn(Set(JMAP_CORE))

    val methods: Set[Method] = Set(mockCoreEchoMethod)
    val jmapRfc8621Configuration = JmapRfc8621Configuration("http://127.0.0.1", "ws://127.0.0.1")
    val apiRoute: JMAPApiRoutes = new JMAPApiRoutes(AUTHENTICATOR, userProvisionner, new JMAPApi(methods, DefaultCapabilities.supported(jmapRfc8621Configuration).map(_.id()), jmapRfc8621Configuration))
    val routesHandler: ImmutableSet[JMAPRoutesHandler] = ImmutableSet.of(new JMAPRoutesHandler(Version.RFC8621, apiRoute))

    val versionParser: VersionParser = new VersionParser(SUPPORTED_VERSIONS, JMAPConfiguration.DEFAULT)
    jmapServer = new JMAPServer(TEST_CONFIGURATION, routesHandler, versionParser)
    jmapServer.start()

    RestAssured.requestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .setAccept(ContentType.JSON)
      .setConfig(newConfig.encoderConfig(encoderConfig.defaultContentCharset(StandardCharsets.UTF_8)))
      .setPort(jmapServer.getPort.getValue)
      .setBasePath(JMAP)
      .build

    val headers: Headers = Headers.headers(
      new Header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER),
      new Header("Authorization", s"Basic ${userBase64String}")
    )

    val response = RestAssured
      .`given`()
        .headers(headers)
        .body(REQUEST_OBJECT_WITH_2_ECHO_METHOD_CALL)
      .when()
        .post()
      .`then`
        .statusCode(HttpStatus.SC_OK)
        .contentType(ContentType.JSON)
      .extract()
        .body()
        .asString()

    assertThatJson(response).isEqualTo(SERVER_FAIL_RESPONSE_OBJECT)
  }
}
