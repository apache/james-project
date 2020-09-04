/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *  http://www.apache.org/licenses/LICENSE-2.0                  *
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

trait VacationResponseSetMethodContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  @Test
  def updateShouldSucceed(server: GuiceJamesServer): Unit = {
    val request =
      s"""
         |{
         |  "using": [ "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:vacationresponse" ],
         |  "methodCalls": [
         |    ["VacationResponse/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "singleton": {
         |          "isEnabled": true,
         |          "fromDate": "2014-10-30T14:12:00Z",
         |          "toDate": "2014-11-30T14:12:00Z",
         |          "subject": "I am in vacation",
         |          "textBody": "I'm currently enjoying life. Please disturb me later",
         |          "htmlBody": "I'm currently enjoying <b>life</b>. <br/>Please disturb me later"
         |        }
         |      }
         |    }, "c1"],
         |    ["VacationResponse/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["singleton"]
         |    }, "c2"]
         |  ]
         |}
         |""".stripMargin

    val response = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(request)
      .when
        .post
      .`then`
        .log().ifValidationFails()
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |"sessionState": "75128aab4b1b",
         |  "methodResponses": [
         |    ["VacationResponse/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "000001",
         |      "updated": {
         |        "singleton": {}
         |      }
         |    }, "c1"],
         |    ["VacationResponse/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "state": "000001",
         |      "list": [
         |        {
         |          "id": "singleton",
         |          "isEnabled": true,
         |          "fromDate": "2014-10-30T14:12:00Z",
         |          "toDate": "2014-11-30T14:12:00Z",
         |          "subject": "I am in vacation",
         |          "textBody": "I'm currently enjoying life. Please disturb me later",
         |          "htmlBody": "I'm currently enjoying <b>life</b>. <br/>Please disturb me later"
         |        }
         |      ],
         |      "notFound": []
         |     }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldNoopWhenEmptyPatchObject(server: GuiceJamesServer): Unit = {
    val request1 =
      s"""
         |{
         |  "using": [ "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:vacationresponse" ],
         |  "methodCalls": [
         |    ["VacationResponse/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "singleton": {
         |          "isEnabled": true,
         |          "fromDate": "2014-10-30T14:12:00Z",
         |          "toDate": "2014-11-30T14:12:00Z",
         |          "subject": "I am in vacation",
         |          "textBody": "I'm currently enjoying life. Please disturb me later",
         |          "htmlBody": "I'm currently enjoying <b>life</b>. <br/>Please disturb me later"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}
         |""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request1)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)

    val request2 =
      s"""
         |{
         |  "using": [ "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:vacationresponse" ],
         |  "methodCalls": [
         |    ["VacationResponse/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "singleton": {}
         |      }
         |    }, "c1"],
         |    ["VacationResponse/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["singleton"]
         |    }, "c2"]
         |  ]
         |}
         |""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request2)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |"sessionState": "75128aab4b1b",
         |  "methodResponses": [
         |    ["VacationResponse/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "000001",
         |      "updated": {
         |        "singleton": {}
         |      }
         |    }, "c1"],
         |    ["VacationResponse/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "state": "000001",
         |      "list": [
         |        {
         |          "id": "singleton",
         |          "isEnabled": true,
         |          "fromDate": "2014-10-30T14:12:00Z",
         |          "toDate": "2014-11-30T14:12:00Z",
         |          "subject": "I am in vacation",
         |          "textBody": "I'm currently enjoying life. Please disturb me later",
         |          "htmlBody": "I'm currently enjoying <b>life</b>. <br/>Please disturb me later"
         |        }
         |      ],
         |      "notFound": []
         |     }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def partialUpdateShouldSucceed(server: GuiceJamesServer): Unit = {
    val request =
      s"""
         |{
         |  "using": [ "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:vacationresponse" ],
         |  "methodCalls": [
         |    ["VacationResponse/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "singleton": {
         |          "htmlBody": "I'm currently enjoying <b>life</b>. <br/>Please disturb me later"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}
         |""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |"sessionState": "75128aab4b1b",
         |  "methodResponses": [
         |    ["VacationResponse/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "000001",
         |      "updated": {
         |        "singleton": {}
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def partialUpdateShouldNotAffectOtherFields(server: GuiceJamesServer): Unit = {
    val request1 =
      s"""
         |{
         |  "using": [ "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:vacationresponse" ],
         |  "methodCalls": [
         |    ["VacationResponse/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "singleton": {
         |          "isEnabled": true,
         |           "fromDate": "2014-10-30T14:12:00Z",
         |           "toDate": "2014-11-30T14:12:00Z",
         |           "subject": "I am in vacation",
         |           "textBody": "I'm currently enjoying life. Please disturb me later",
         |           "htmlBody": ""
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}
         |""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request1)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)

    val request2 =
      s"""
         |{
         |  "using": [ "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:vacationresponse" ],
         |  "methodCalls": [
         |    ["VacationResponse/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "singleton": {
         |          "htmlBody": "I'm currently enjoying <b>life</b>. <br/>Please disturb me later"
         |        }
         |      }
         |    }, "c1"],
         |    ["VacationResponse/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["singleton"]
         |    }, "c2"]
         |  ]
         |}
         |""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request2)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |"sessionState": "75128aab4b1b",
         |  "methodResponses": [
         |    ["VacationResponse/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "000001",
         |      "updated": {
         |        "singleton": {}
         |      }
         |    }, "c1"],
         |    ["VacationResponse/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "state": "000001",
         |      "list": [
         |        {
         |          "id": "singleton",
         |          "isEnabled": true,
         |          "fromDate": "2014-10-30T14:12:00Z",
         |          "toDate": "2014-11-30T14:12:00Z",
         |          "subject": "I am in vacation",
         |          "textBody": "I'm currently enjoying life. Please disturb me later",
         |          "htmlBody": "I'm currently enjoying <b>life</b>. <br/>Please disturb me later"
         |        }
         |      ],
         |      "notFound": []
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenInvalidKey(server: GuiceJamesServer): Unit = {
    val request =
      s"""
         |{
         |  "using": [ "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:vacationresponse" ],
         |  "methodCalls": [
         |    ["VacationResponse/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "invalid": {
         |          "htmlBody": "I'm currently enjoying <b>life</b>. <br/>Please disturb me later"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}
         |""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "75128aab4b1b",
         |  "methodResponses": [
         |    ["VacationResponse/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "000001",
         |      "notUpdated": {
         |        "invalid": {
         |          "type": "invalidArguments",
         |          "description": "id invalid must be singleton"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenIdIsPresent(server: GuiceJamesServer): Unit = {
    val request =
      s"""
         |{
         |  "using": [ "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:vacationresponse" ],
         |  "methodCalls": [
         |    ["VacationResponse/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "singleton": {
         |          "id": "singleton",
         |          "htmlBody": "I'm currently enjoying <b>life</b>. <br/>Please disturb me later"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}
         |""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "75128aab4b1b",
         |  "methodResponses": [
         |    ["VacationResponse/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "000001",
         |      "notUpdated": {
         |        "singleton": {
         |          "type": "invalidArguments",
         |          "description": "id is server-set thus cannot be changed"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenInvalidDate(server: GuiceJamesServer): Unit = {
    val request =
      s"""
         |{
         |  "using": [ "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:vacationresponse" ],
         |  "methodCalls": [
         |    ["VacationResponse/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "singleton": {
         |          "fromDate": "2014/12/30"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}
         |""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "75128aab4b1b",
         |  "methodResponses": [
         |    ["VacationResponse/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "000001",
         |      "notUpdated": {
         |        "singleton": {
         |          "type": "invalidArguments",
         |          "description": "java.time.format.DateTimeParseException: Text '2014/12/30' could not be parsed at index 4"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenFromDateIsMoreRecentThanToDate(server: GuiceJamesServer): Unit = {
    val request =
      s"""
         |{
         |  "using": [ "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:vacationresponse" ],
         |  "methodCalls": [
         |    ["VacationResponse/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "singleton": {
         |          "fromDate": "2014-11-30T14:12:00Z",
         |          "toDate": "2014-10-30T14:12:00Z"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}
         |""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "75128aab4b1b",
         |  "methodResponses": [
         |    ["VacationResponse/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "000001",
         |      "notUpdated": {
         |        "singleton": {
         |          "type": "invalidArguments",
         |          "description": "fromDate must be older than toDate"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldNoopWhenEmptyMap(server: GuiceJamesServer): Unit = {
    val request =
      s"""
         |{
         |  "using": [ "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:vacationresponse" ],
         |  "methodCalls": [
         |    ["VacationResponse/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {}
         |    },
         |    "c1"]
         |  ]
         |}
         |""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "75128aab4b1b",
         |  "methodResponses": [
         |    ["VacationResponse/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "000001"
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenMultiplePatchObjects(server: GuiceJamesServer): Unit = {
    val request =
      s"""
         |{
         |  "using": [ "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:vacationresponse" ],
         |  "methodCalls": [
         |    ["VacationResponse/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "singleton": {
         |          "htmlBody": "I'm currently enjoying <b>life</b>. <br/>Please disturb me later"
         |        },
         |        "singleton2": {
         |          "htmlBody": "I'm currently enjoying <b>life</b>. <br/>Please disturb me later"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}
         |""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "75128aab4b1b",
         |  "methodResponses": [
         |    ["VacationResponse/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "000001",
         |      "updated": {
         |        "singleton": {}
         |      },
         |      "notUpdated": {
         |        "singleton2": {
         |          "type": "invalidArguments",
         |         "description": "id singleton2 must be singleton"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def createShouldFail(server: GuiceJamesServer): Unit = {
    val request =
      s"""
         |{
         |  "using": [ "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:vacationresponse" ],
         |  "methodCalls": [
         |    ["VacationResponse/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "create": {
         |        "singleton": {
         |          "htmlBody": "I'm currently enjoying <b>life</b>. <br/>Please disturb me later"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}
         |""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "75128aab4b1b",
         |  "methodResponses": [
         |    ["VacationResponse/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "000001",
         |      "notCreated": {
         |        "singleton": {
         |          "type": "invalidArguments",
         |          "description": "'create' is not supported on singleton objects"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def destroyShouldFail(server: GuiceJamesServer): Unit = {
    val request =
      s"""
         |{
         |  "using": [ "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:vacationresponse" ],
         |  "methodCalls": [
         |    ["VacationResponse/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "destroy": ["singleton"]
         |    }, "c1"]
         |  ]
         |}
         |""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "75128aab4b1b",
         |  "methodResponses": [
         |    ["VacationResponse/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "000001",
         |      "notDestroyed": {
         |        "singleton": {
         |          "type": "invalidArguments",
         |          "description": "'destroy' is not supported on singleton objects"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def vacationSetShouldReturnUnknownMethodWhenMissingOneCapability(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": ["urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [[
               |     "VacationResponse/set",
               |     {
               |       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |       "update": {
               |         "singleton": {}
               |       }
               |     },
               |     "c1"]]
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
         |  "sessionState": "75128aab4b1b",
         |  "methodResponses": [[
         |    "error",
         |    {
         |      "type": "unknownMethod",
         |      "description": "Missing capability(ies): urn:ietf:params:jmap:vacationresponse"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def vacationSetShouldReturnUnknownMethodWhenMissingAllCapabilities(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [],
               |  "methodCalls": [[
               |     "VacationResponse/set",
               |     {
               |       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |       "update": {
               |         "singleton": {}
               |       }
               |     },
               |     "c1"]]
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
         |  "sessionState": "75128aab4b1b",
         |  "methodResponses": [[
         |    "error",
         |    {
         |      "type": "unknownMethod",
         |      "description": "Missing capability(ies): urn:ietf:params:jmap:core, urn:ietf:params:jmap:mail, urn:ietf:params:jmap:vacationresponse"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }
}