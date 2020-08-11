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
package org.apache.james.jmap.rfc8621.contract

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured._
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.EchoMethodContract._
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Tag, Test}

object EchoMethodContract {
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
      |        "type": "unknownMethod"
      |      },
      |      "notsupport"
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
      |        "type": "unknownMethod"
      |      },
      |      "notsupport"
      |    ]
      |  ]
      |}""".stripMargin
}

trait EchoMethodContract {

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
  @Tag(CategoryTags.BASIC_FEATURE)
  def echoMethodShouldRespondOKWithRFC8621VersionAndSupportedMethod(): Unit = {

    val response: String = `given`()
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(ECHO_REQUEST_OBJECT)
      .when()
        .post()
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
      .extract()
        .body()
        .asString()

    assertThatJson(response).isEqualTo(ECHO_RESPONSE_OBJECT)
  }

  @Test
  def echoMethodShouldRespondWithRFC8621VersionAndUnsupportedMethod(): Unit = {
    val response: String = `given`()
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(REQUEST_OBJECT_WITH_UNSUPPORTED_METHOD)
      .when()
        .post()
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
      .extract()
        .body()
        .asString()

    assertThatJson(response).isEqualTo(RESPONSE_OBJECT_WITH_UNSUPPORTED_METHOD)
  }
}
