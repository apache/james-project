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
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Test}

trait IdentityGetContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addDomain("domain-alias.tld")
      .addUser(BOB.asString, BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  @Test
  def getIdentityShouldReturnDefaultIdentity(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Identity/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": null
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .inPath("methodResponses[0][1]")
      .isEqualTo(
      """{
        |  "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |  "state": "000001",
        |  "list": [
        |      {
        |          "id": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |          "name": "bob@domain.tld",
        |          "email": "bob@domain.tld",
        |          "mayDelete": false
        |      }
        |  ]
        |}""".stripMargin)
  }

  @Test
  def getIdentityShouldReturnAliases(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl]).addUserAliasMapping("bob-alias", "domain.tld", "bob@domain.tld")
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Identity/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": null
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .inPath("methodResponses[0][1]")
      .isEqualTo(
      """{
        |    "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |    "state": "000001",
        |    "list": [
        |        {
        |            "id": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |            "name": "bob@domain.tld",
        |            "email": "bob@domain.tld",
        |            "mayDelete": false
        |        },
        |        {
        |            "id": "6310e0a86aedaad878f634a5ff5c2cb8bb3c2401319305ef3272591ebcdc6cb4",
        |            "name": "bob-alias@domain.tld",
        |            "email": "bob-alias@domain.tld",
        |            "mayDelete": false
        |        }
        |    ]
        |}""".stripMargin)
  }

  @Test
  def getIdentityShouldReturnDomainAliases(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl]).addUserAliasMapping("bob-alias", "domain.tld", "bob@domain.tld")
    server.getProbe(classOf[DataProbeImpl]).addDomainAliasMapping("domain-alias.tld", "domain.tld")
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Identity/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": null
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .inPath("methodResponses[0][1]")
      .isEqualTo(
      """{
        |    "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |    "state": "000001",
        |    "list": [
        |        {
        |            "id": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |            "name": "bob@domain.tld",
        |            "email": "bob@domain.tld",
        |            "mayDelete": false
        |        },
        |        {
        |            "id": "725cfddc2c1905fefa6b8c3a6ab5dd9f8ba611c4d7772cf066f69cfd2ec23832",
        |            "name": "bob@domain-alias.tld",
        |            "email": "bob@domain-alias.tld",
        |            "mayDelete": false
        |        },
        |        {
        |            "id": "6310e0a86aedaad878f634a5ff5c2cb8bb3c2401319305ef3272591ebcdc6cb4",
        |            "name": "bob-alias@domain.tld",
        |            "email": "bob-alias@domain.tld",
        |            "mayDelete": false
        |        },
        |        {
        |            "id": "62844b5cd203bcb86cb590355fc509773ef1972ce8457b13a7d55d99a308c8f6",
        |            "name": "bob-alias@domain-alias.tld",
        |            "email": "bob-alias@domain-alias.tld",
        |            "mayDelete": false
        |        }
        |    ]
        |}""".stripMargin)
  }

  @Test
  def propertiesShouldBeSupported(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Identity/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": null,
         |      "properties": ["id", "name", "email", "replyTo", "bcc", "textSignature", "htmlSignature", "mayDelete"]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString


    assertThatJson(response)
      .inPath("methodResponses[0][1]")
      .isEqualTo(
        """{
          |  "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
          |  "state": "000001",
          |  "list": [
          |      {
          |          "id": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
          |          "name": "bob@domain.tld",
          |          "email": "bob@domain.tld",
          |          "mayDelete": false
          |      }
          |  ]
          |}""".stripMargin)
  }

  @Test
  def propertiesShouldBeFiltered(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Identity/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": null,
         |      "properties": ["id", "email"]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString


    assertThatJson(response)
      .inPath("methodResponses[0][1]")
      .isEqualTo(
        """{
          |  "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
          |  "state": "000001",
          |  "list": [
          |      {
          |          "id": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
          |          "email": "bob@domain.tld"
          |      }
          |  ]
          |}""".stripMargin)
  }

  @Test
  def badPropertiesShouldBeRejected(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Identity/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": null,
         |      "properties": ["id", "bad"]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString


    assertThatJson(response)
      .isEqualTo(
        """{
          |    "sessionState": "75128aab4b1b",
          |    "methodResponses": [
          |        [
          |            "error",
          |            {
          |                "type": "invalidArguments",
          |                "description": "The following properties [bad] do not exist."
          |            },
          |            "c1"
          |        ]
          |    ]
          |}""".stripMargin)
  }

  @Test
  def badAccountIdShouldBeRejected(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Identity/get",
         |    {
         |      "accountId": "bad",
         |      "ids": null
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        """{
          |    "sessionState": "75128aab4b1b",
          |    "methodResponses": [
          |        [
          |            "error",
          |            {
          |                "type": "accountNotFound"
          |            },
          |            "c1"
          |        ]
          |    ]
          |}""".stripMargin)
  }
}
