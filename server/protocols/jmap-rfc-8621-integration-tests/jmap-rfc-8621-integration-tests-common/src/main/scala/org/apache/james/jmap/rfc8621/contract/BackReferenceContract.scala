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
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Tag, Test}

trait BackReferenceContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)
      .addUser(ANDRE.asString, ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def backReferenceResolvingShouldWork(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:mail"],
           |  "methodCalls": [[
           |     "Mailbox/get",
           |     {
           |       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |       "ids": null,
           |       "properties": ["id"]
           |     },
           |     "c1"],[
           |     "Mailbox/get",
           |     {
           |       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |       "properties": ["id"],
           |       "#ids": {
           |         "resultOf":"c1",
           |         "name":"Mailbox/get",
           |         "path":"list/*/id"
           |       }
           |     },
           |     "c2"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [
         |        [
         |            "Mailbox/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |                    {"id": "1"},
         |                    {"id": "5"},
         |                    {"id": "2"},
         |                    {"id": "3"},
         |                    {"id": "4"},
         |                    {"id": "6"}
         |                ],
         |                "notFound": []
         |            },
         |            "c1"
         |        ],
         |        [
         |            "Mailbox/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |                    {"id": "1"},
         |                    {"id": "5"},
         |                    {"id": "2"},
         |                    {"id": "3"},
         |                    {"id": "4"},
         |                    {"id": "6"}
         |                ],
         |                "notFound": []
         |            },
         |            "c2"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def pathShouldBeResolvable(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:mail"],
           |  "methodCalls": [[
           |     "Mailbox/get",
           |     {
           |       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |       "ids": null,
           |       "properties": ["id"]
           |     },
           |     "c1"],[
           |     "Mailbox/get",
           |     {
           |       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |       "properties": ["id"],
           |       "#ids": {
           |         "resultOf":"c1",
           |         "name":"Mailbox/get",
           |         "path":"unknown/*/id"
           |       }
           |     },
           |     "c2"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [
         |        [
         |            "Mailbox/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |                    {"id": "1"},
         |                    {"id": "5"},
         |                    {"id": "2"},
         |                    {"id": "3"},
         |                    {"id": "4"},
         |                    {"id": "6"}
         |                ],
         |                "notFound": []
         |            },
         |            "c1"
         |        ],
         |        [
         |            "error",
         |            {
         |                "type": "invalidResultReference",
         |                "description": "Failed resolving back-reference: List((,List(JsonValidationError(List(Expected path unknown was missing),ArraySeq()))))"
         |            },
         |            "c2"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def wildcardRequiresAnArray(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:mail"],
           |  "methodCalls": [[
           |     "Mailbox/get",
           |     {
           |       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |       "ids": null,
           |       "properties": ["id"]
           |     },
           |     "c1"],[
           |     "Mailbox/get",
           |     {
           |       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |       "properties": ["id"],
           |       "#ids": {
           |         "resultOf":"c1",
           |         "name":"Mailbox/get",
           |         "path":"*/*/id"
           |       }
           |     },
           |     "c2"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [
         |        [
         |            "Mailbox/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |                    {"id": "1"},
         |                    {"id": "5"},
         |                    {"id": "2"},
         |                    {"id": "3"},
         |                    {"id": "4"},
         |                    {"id": "6"}
         |                ],
         |                "notFound": []
         |            },
         |            "c1"
         |        ],
         |        [
         |            "error",
         |            {
         |                "type": "invalidResultReference",
         |                "description": "Failed resolving back-reference: List((,List(JsonValidationError(List(Expecting an array),ArraySeq()))))"
         |            },
         |            "c2"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def resolvedBackReferenceShouldHaveTheRightMethodName(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:mail"],
           |  "methodCalls": [[
           |     "Mailbox/get",
           |     {
           |       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |       "ids": null,
           |       "properties": ["id"]
           |     },
           |     "c1"],[
           |     "Mailbox/get",
           |     {
           |       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |       "properties": ["id"],
           |       "#ids": {
           |         "resultOf":"c1",
           |         "name":"Mailbox/set",
           |         "path":"list/*/id"
           |       }
           |     },
           |     "c2"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [
         |        [
         |            "Mailbox/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |                    {"id": "1"},
         |                    {"id": "5"},
         |                    {"id": "2"},
         |                    {"id": "3"},
         |                    {"id": "4"},
         |                    {"id": "6"}
         |                ],
         |                "notFound": []
         |            },
         |            "c1"
         |        ],
         |        [
         |            "error",
         |            {
         |                "type": "invalidResultReference",
         |                "description": "Failed resolving back-reference: List((,List(JsonValidationError(List(MethodCallId(c1) references a MethodName(Mailbox/get) method),ArraySeq()))))"
         |            },
         |            "c2"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def resolvingAnUnexistingMethodCallIdShouldFail(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:mail"],
           |  "methodCalls": [[
           |     "Mailbox/get",
           |     {
           |       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |       "ids": null,
           |       "properties": ["id"]
           |     },
           |     "c1"],[
           |     "Mailbox/get",
           |     {
           |       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |       "properties": ["id"],
           |       "#ids": {
           |         "resultOf":"c42",
           |         "name":"Mailbox/get",
           |         "path":"list/*/id"
           |       }
           |     },
           |     "c2"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [
         |        [
         |            "Mailbox/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |                    {"id": "1"},
         |                    {"id": "5"},
         |                    {"id": "2"},
         |                    {"id": "3"},
         |                    {"id": "4"},
         |                    {"id": "6"}
         |                ],
         |                "notFound": []
         |            },
         |            "c1"
         |        ],
         |        [
         |            "error",
         |            {
         |                "type": "invalidResultReference",
         |                "description": "Failed resolving back-reference: List((,List(JsonValidationError(List(Back reference could not be resolved),ArraySeq()))))"
         |            },
         |            "c2"
         |        ]
         |    ]
         |}""".stripMargin)
  }
}
