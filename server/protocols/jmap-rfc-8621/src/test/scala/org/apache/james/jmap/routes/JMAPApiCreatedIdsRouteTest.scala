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

import java.util.stream.Stream

import io.restassured.RestAssured.`given`
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.{SC_BAD_REQUEST, SC_OK}
import org.apache.james.jmap.JMAPServer
import org.apache.james.jmap.core.RequestLevelErrorType
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.routes.JMAPApiRoutesTest.{requestSpec, startServer, userBase64String}
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.{AfterEach, BeforeEach, Test}
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.{Arguments, MethodSource}

object JMAPApiCreatedIdsRouteTest {
  def malformedMappings(): Stream[Arguments] = Stream.of(
    Arguments.of("array", "[]"),
    Arguments.of("number", "42"),
    Arguments.of("empty client ID", """{"": "server"}"""),
    Arguments.of("empty server ID", """{"client": ""}"""),
    Arguments.of("numeric server ID", """{"client": 42}"""),
    Arguments.of("invalid client ID", """{"bad id": "server"}"""),
    Arguments.of("invalid server ID", """{"client": "bad id"}"""),
    Arguments.of("client ID too long", s"""{"${"c" * 256}": "server"}"""),
    Arguments.of("server ID too long", s"""{"client": "${"s" * 256}"}"""))
}

class JMAPApiCreatedIdsRouteTest {
  private var server: JMAPServer = _

  @BeforeEach
  def setUp(): Unit = {
    server = startServer()
  }

  @AfterEach
  def tearDown(): Unit = {
    server.stop()
  }

  @Test
  def shouldKeepInitialMappingsAfterAnUnknownMethod(): Unit = {
    val response = `given`()
      .spec(requestSpec(server))
      .header("Authorization", s"Basic $userBase64String")
      .body(
        """{
          |  "using": ["urn:ietf:params:jmap:core"],
          |  "createdIds": {"old": "server-id"},
          |  "methodCalls": [["Unknown/method", {}, "unknown"]]
          |}""".stripMargin)
      .post().`then`()
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract().asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "createdIds": {"old": "server-id"},
         |  "methodResponses": [["error", {"type": "unknownMethod"}, "unknown"]]
         |}""".stripMargin)
  }

  @Test
  def shouldReturnAnExplicitlyRequestedEmptyMap(): Unit = {
    val response = `given`()
      .spec(requestSpec(server))
      .header("Authorization", s"Basic $userBase64String")
      .body(
        """{
          |  "using": ["urn:ietf:params:jmap:core"],
          |  "createdIds": {},
          |  "methodCalls": []
          |}""".stripMargin)
      .post().`then`()
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract().asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "createdIds": {},
         |  "methodResponses": []
         |}""".stripMargin)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource(Array("malformedMappings"))
  def shouldRejectMalformedMappingsAtTheRequestBoundary(scenario: String, malformedIds: String): Unit = {
    `given`()
      .spec(requestSpec(server))
      .header("Authorization", s"Basic $userBase64String")
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core"],
           |  "createdIds": $malformedIds,
           |  "methodCalls": []
           |}""".stripMargin)
      .post().`then`()
      .log().ifValidationFails()
      .statusCode(SC_BAD_REQUEST)
      .contentType(JSON)
      .body("type", equalTo(RequestLevelErrorType.NOT_REQUEST.value))
  }
}
