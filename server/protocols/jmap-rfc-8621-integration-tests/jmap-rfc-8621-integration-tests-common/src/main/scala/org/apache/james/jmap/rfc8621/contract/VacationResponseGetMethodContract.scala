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

import java.time.ZonedDateTime

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.JmapGuiceProbe
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UuidState.INSTANCE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.VacationResponseGetMethodContract.VACATION_RESPONSE
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.utils.DataProbeImpl
import org.apache.james.vacation.api.VacationPatch
import org.junit.jupiter.api.{BeforeEach, Tag, Test}

object VacationResponseGetMethodContract {
  private val VACATION_RESPONSE: VacationPatch =
    VacationPatch.builder
      .isEnabled(true)
      .fromDate(ZonedDateTime.parse("2014-09-30T14:10:00+02:00"))
      .toDate(ZonedDateTime.parse("2016-04-15T11:56:32.224+07:00[Asia/Vientiane]"))
      .subject("On vacation...")
      .textBody("Test explaining my vacations")
      .htmlBody("<b>Test explaining my vacations</b>")
      .build
}

trait VacationResponseGetMethodContract {
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
  def vacationResponseGetShouldFailWhenWrongAccountId(server: GuiceJamesServer): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:vacationresponse"],
         |  "methodCalls": [[
         |    "VacationResponse/get",
         |    {
         |      "accountId": "unknownAccountId",
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
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
        |  "sessionState": "${SESSION_STATE.value}",
        |  "methodResponses": [
        |    ["error", {
        |      "type": "accountNotFound"
        |    }, "c1"]
        |  ]
        |}""".stripMargin)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def vacationResponseShouldBeDisabledByDefault(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:ietf:params:jmap:vacationresponse"],
               |  "methodCalls": [[
               |    "VacationResponse/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "ids": null
               |    },
               |    "c1"]]
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
         |    "VacationResponse/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "state": "${INSTANCE.value}",
         |      "list": [
         |        {
         |          "id":"singleton",
         |          "isEnabled": false
         |        }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def vacationResponseShouldReturnStoredValue(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[JmapGuiceProbe])
      .modifyVacation(AccountId.fromUsername(BOB), VACATION_RESPONSE)

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:ietf:params:jmap:vacationresponse"],
               |  "methodCalls": [[
               |    "VacationResponse/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "ids": null
               |    },
               |    "c1"]]
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
         |    "VacationResponse/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "state": "${INSTANCE.value}",
         |      "list": [
         |        {
         |          "id":"singleton",
         |          "isEnabled": true,
         |          "fromDate": "2014-09-30T12:10:00Z",
         |          "toDate": "2016-04-15T04:56:32Z",
         |          "subject": "On vacation...",
         |          "textBody": "Test explaining my vacations",
         |          "htmlBody": "<b>Test explaining my vacations</b>"
         |        }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def vacationResponseShouldReturnUnknownMethodWhenOmittingOneCapability(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [[
               |    "VacationResponse/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "ids": null
               |    },
               |    "c1"]]
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
         |      "description":"Missing capability(ies): urn:ietf:params:jmap:vacationresponse"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def vacationResponseShouldReturnUnknownMethodWhenMissingAllCapability(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [],
               |  "methodCalls": [[
               |    "VacationResponse/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "ids": null
               |    },
               |    "c1"]]
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
         |      "description":"Missing capability(ies): urn:ietf:params:jmap:core, urn:ietf:params:jmap:vacationresponse"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def vacationResponseShouldReturnValidResponseWhenSingletonId(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:ietf:params:jmap:vacationresponse"],
               |  "methodCalls": [[
               |    "VacationResponse/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "ids": ["singleton"]
               |    },
               |    "c1"]]
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
         |    "VacationResponse/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "state": "${INSTANCE.value}",
         |      "list": [
         |        {
         |          "id":"singleton",
         |          "isEnabled": false
         |        }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def vacationResponseShouldReturnNotFoundWhenIdNotSingleton(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:ietf:params:jmap:vacationresponse"],
               |  "methodCalls": [[
               |    "VacationResponse/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "ids": ["random"]
               |    },
               |    "c1"]]
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
         |    "VacationResponse/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "state": "${INSTANCE.value}",
         |      "list": [],
         |      "notFound": ["random"]
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def vacationResponseShouldReturnSingletonAndNotFoundIds(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:ietf:params:jmap:vacationresponse"],
               |  "methodCalls": [[
               |    "VacationResponse/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "ids": ["random1", "singleton", "random2"]
               |    },
               |    "c1"]]
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
         |    "VacationResponse/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "state": "${INSTANCE.value}",
         |      "list": [
         |        {
         |          "id":"singleton",
         |          "isEnabled": false
         |        }
         |      ],
         |      "notFound": ["random1", "random2"]
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def vacationResponseShouldReturnEmptyListWhenEmptyIdsArray(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:ietf:params:jmap:vacationresponse"],
               |  "methodCalls": [[
               |    "VacationResponse/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "ids": []
               |    },
               |    "c1"]]
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
         |    "VacationResponse/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "state": "${INSTANCE.value}",
         |      "list": [],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def vacationResponseShouldFailWhenEmptyId(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:ietf:params:jmap:vacationresponse"],
               |  "methodCalls": [[
               |    "VacationResponse/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "ids": [""]
               |    },
               |    "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString
      .stripMargin

    assertThatJson(response)
      //  I gave up trying to understand the escape sequence after 1 hour +.  (a Json in a JSON in a scala string)
      // If somebody ants to give a shot to assert the description...
      .whenIgnoringPaths("methodResponses[0][1].description")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "error",
         |      {
         |        "type": "invalidArguments"
         |      },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def vacationResponseShouldReturnAllPropertiesWhenNull(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[JmapGuiceProbe])
      .modifyVacation(AccountId.fromUsername(BOB), VACATION_RESPONSE)

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:ietf:params:jmap:vacationresponse"],
               |  "methodCalls": [[
               |    "VacationResponse/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "ids": null,
               |      "properties": null
               |    },
               |    "c1"]]
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
         |    "VacationResponse/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "state": "${INSTANCE.value}",
         |      "list": [
         |        {
         |          "id":"singleton",
         |          "isEnabled": true,
         |          "fromDate": "2014-09-30T12:10:00Z",
         |          "toDate": "2016-04-15T04:56:32Z",
         |          "subject": "On vacation...",
         |          "textBody": "Test explaining my vacations",
         |          "htmlBody": "<b>Test explaining my vacations</b>"
         |        }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def vacationResponseShouldReturnIdWhenNoPropertiesRequested(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[JmapGuiceProbe])
      .modifyVacation(AccountId.fromUsername(BOB), VACATION_RESPONSE)

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:ietf:params:jmap:vacationresponse"],
               |  "methodCalls": [[
               |    "VacationResponse/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "ids": null,
               |      "properties": []
               |    },
               |    "c1"]]
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
         |    "VacationResponse/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "state": "${INSTANCE.value}",
         |      "list": [
         |        {
         |          "id":"singleton"
         |        }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def vacationResponseShouldReturnOnlyRequestedProperties(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[JmapGuiceProbe])
      .modifyVacation(AccountId.fromUsername(BOB), VACATION_RESPONSE)

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:ietf:params:jmap:vacationresponse"],
               |  "methodCalls": [[
               |    "VacationResponse/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "ids": null,
               |      "properties": ["id", "subject"]
               |    },
               |    "c1"]]
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
         |    "VacationResponse/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "state": "${INSTANCE.value}",
         |      "list": [
         |        {
         |          "id":"singleton",
         |          "subject": "On vacation..."
         |        }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def vacationResponseShouldAlwaysReturnIdEvenIfNotRequestedInProperties(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[JmapGuiceProbe])
      .modifyVacation(AccountId.fromUsername(BOB), VACATION_RESPONSE)

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:ietf:params:jmap:vacationresponse"],
               |  "methodCalls": [[
               |    "VacationResponse/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "ids": null,
               |      "properties": ["subject"]
               |    },
               |    "c1"]]
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
         |    "VacationResponse/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "state": "${INSTANCE.value}",
         |      "list": [
         |        {
         |          "id":"singleton",
         |          "subject": "On vacation..."
         |        }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def vacationResponseShouldReturnInvalidArgumentsErrorWhenInvalidProperty(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:ietf:params:jmap:vacationresponse"],
               |  "methodCalls": [[
               |    "VacationResponse/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "ids": null,
               |      "properties": ["invalidProperty"]
               |    },
               |    "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString
      .stripMargin

    assertThatJson(response).isEqualTo(
      s"""{
        |  "sessionState": "${SESSION_STATE.value}",
        |  "methodResponses": [[
        |    "error",
        |    {
        |      "type": "invalidArguments",
        |      "description": "The following properties [invalidProperty] do not exist."
        |    },
        |    "c1"]]
        |}""".stripMargin)
  }
}
