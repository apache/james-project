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
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured
import io.restassured.builder.RequestSpecBuilder
import io.restassured.config.EncoderConfig.encoderConfig
import io.restassured.config.RestAssuredConfig.newConfig
import io.restassured.http.{ContentType, Header, Headers}
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus
import org.apache.james.core.{Domain, Username}
import org.apache.james.dnsservice.api.DNSService
import org.apache.james.domainlist.memory.MemoryDomainList
import org.apache.james.jmap.JMAPUrls.JMAP
import org.apache.james.jmap._
import org.apache.james.jmap.http.{Authenticator, BasicAuthenticationStrategy}
import org.apache.james.jmap.json.Serializer
import org.apache.james.jmap.method.{CoreEchoMethod, Method}
import org.apache.james.jmap.routes.JMAPApiRoutesTest._
import org.apache.james.mailbox.MailboxManager
import org.apache.james.mailbox.extension.PreDeletionHook
import org.apache.james.mailbox.inmemory.MemoryMailboxManagerProvider
import org.apache.james.mailbox.model.TestId
import org.apache.james.metrics.tests.RecordingMetricFactory
import org.apache.james.user.memory.MemoryUsersRepository
import org.mockito.Mockito.mock
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

object JMAPApiRoutesTest {
  private val SERIALIZER: Serializer = new Serializer(new TestId.Factory)
  private val TEST_CONFIGURATION: JMAPConfiguration = JMAPConfiguration.builder().enable().randomPort().build()
  private val ACCEPT_JMAP_VERSION_HEADER = "application/json; jmapVersion="
  private val ACCEPT_DRAFT_VERSION_HEADER = ACCEPT_JMAP_VERSION_HEADER + Version.DRAFT.asString()
  private val ACCEPT_RFC8621_VERSION_HEADER = ACCEPT_JMAP_VERSION_HEADER + Version.RFC8621.asString()

  private val empty_set: ImmutableSet[PreDeletionHook] = ImmutableSet.of()
  private val dnsService = mock(classOf[DNSService])
  private val domainList = new MemoryDomainList(dnsService)
  domainList.addDomain(Domain.of("james.org"))

  private val usersRepository = MemoryUsersRepository.withoutVirtualHosting(domainList)
  usersRepository.addUser(Username.of("user1"), "password")

  private val mailboxManager: MailboxManager = MemoryMailboxManagerProvider.provideMailboxManager(empty_set)
  private val authenticationStrategy: BasicAuthenticationStrategy = new BasicAuthenticationStrategy(usersRepository, mailboxManager)
  private val AUTHENTICATOR: Authenticator = Authenticator.of(new RecordingMetricFactory, authenticationStrategy)

  private val JMAP_METHODS: Set[Method] = Set(new CoreEchoMethod)

  private val JMAP_API_ROUTE: JMAPApiRoutes = new JMAPApiRoutes(AUTHENTICATOR, SERIALIZER, JMAP_METHODS)
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
    """{
      |  "sessionState": "75128aab4b1b",
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
  private val RESPONSE_OBJECT_WITH_UNSUPPORTED_METHOD: String =
    """{
      |  "sessionState": "75128aab4b1b",
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
      |        "type": "Not implemented"
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
}

class JMAPApiRoutesTest extends AnyFlatSpec with BeforeAndAfter with Matchers {

  var jmapServer: JMAPServer = _

  before {
    val versionParser: VersionParser = new VersionParser(SUPPORTED_VERSIONS)
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
      .then
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
      .then
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
      .then
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
      .then
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
      .then
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
      .then
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
      .then
        .statusCode(HttpStatus.SC_BAD_REQUEST)
  }
}
