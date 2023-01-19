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
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import net.javacrumbs.jsonunit.core.internal.Options
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.delegation.DelegationId
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.DelegateGetMethodContract.BOB_ACCOUNT_ID
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE, ANDRE_ACCOUNT_ID, ANDRE_PASSWORD, BOB, BOB_PASSWORD, CEDRIC, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Test}

object DelegateGetMethodContract {
  val BOB_ACCOUNT_ID: String = Fixture.ACCOUNT_ID
}
trait DelegateGetMethodContract {

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)
      .addUser(CEDRIC.asString(), "cedric_pass")

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build
  }

  @Test
  def listShouldEmptyWhenAccountDoesNotHaveDelegates(): Unit = {
    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:apache:james:params:jmap:delegation"],
           |  "methodCalls": [[
           |    "Delegate/get",
           |    {
           |      "accountId": "$BOB_ACCOUNT_ID",
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
         |    "Delegate/get",
         |    {
         |      "accountId": "$BOB_ACCOUNT_ID",
         |      "list": [],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def delegateGetShouldReturnListWhenDelegated(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(BOB, ANDRE)

    val response: String = `given`
      .body(s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:apache:james:params:jmap:delegation"],
           |  "methodCalls": [[
           |    "Delegate/get",
           |    {
           |      "accountId": "$BOB_ACCOUNT_ID",
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

    assertThatJson(response)
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "Delegate/get",
           |            {
           |                "accountId": "$BOB_ACCOUNT_ID",
           |                "notFound": [],
           |                "list": [
           |                    {
           |                        "id": "${DelegationId.from(BOB, ANDRE).serialize}",
           |                        "username": "${ANDRE.asString()}"
           |                    }
           |                ]
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def delegateGetShouldReturnEmptyListWhenIdsAreEmpty(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(BOB, ANDRE)

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:apache:james:params:jmap:delegation"],
           |  "methodCalls": [[
           |    "Delegate/get",
           |    {
           |      "accountId": "$BOB_ACCOUNT_ID",
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
         |    "Delegate/get",
         |    {
         |      "accountId": "$BOB_ACCOUNT_ID",
         |      "list": [],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def delegateGetShouldReturnNotFoundWhenIdDoesNotExist(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(BOB, ANDRE)

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:apache:james:params:jmap:delegation"],
           |  "methodCalls": [[
           |    "Delegate/get",
           |    {
           |      "accountId": "$BOB_ACCOUNT_ID",
           |      "ids": ["notFound1"]
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
         |    "Delegate/get",
         |    {
         |      "accountId": "$BOB_ACCOUNT_ID",
         |      "list": [],
         |      "notFound": [ "notFound1" ]
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def delegateGetShouldReturnNotFoundAndListWhenMixCases(server: GuiceJamesServer): Unit = {
    val delegatedId = server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(BOB, ANDRE)
      .serialize
    val delegatedId2 = server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(BOB, CEDRIC)
      .serialize

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:apache:james:params:jmap:delegation"],
           |  "methodCalls": [[
           |    "Delegate/get",
           |    {
           |      "accountId": "$BOB_ACCOUNT_ID",
           |      "ids": [ "notFound1", "$delegatedId", "$delegatedId2" ]
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
         |    "Delegate/get",
         |    {
         |      "accountId": "$BOB_ACCOUNT_ID",
         |      "list": [
         |        {
         |          "id": "$delegatedId",
         |          "username": "${ANDRE.asString()}"
         |        },
         |        {
         |          "id": "$delegatedId2",
         |          "username": "${CEDRIC.asString()}"
         |        }
         |      ],
         |      "notFound": [ "notFound1" ]
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def delegateGetShouldNotReturnDelegateOfOtherUser(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(ANDRE, BOB)

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:apache:james:params:jmap:delegation"],
           |  "methodCalls": [[
           |    "Delegate/get",
           |    {
           |      "accountId": "$BOB_ACCOUNT_ID",
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
         |    "Delegate/get",
         |    {
         |      "accountId": "$BOB_ACCOUNT_ID",
         |      "list": [],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def delegateGetShouldNotReturnDelegateOfOtherUserWhenProvideIds(server: GuiceJamesServer): Unit = {
    val delegateId = server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(ANDRE, BOB)
      .serialize

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:apache:james:params:jmap:delegation"],
           |  "methodCalls": [[
           |    "Delegate/get",
           |    {
           |      "accountId": "$BOB_ACCOUNT_ID",
           |      "ids": ["$delegateId"]
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
         |    "Delegate/get",
         |    {
         |      "accountId": "$BOB_ACCOUNT_ID",
         |      "list": [],
         |      "notFound": [ "$delegateId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def bobShouldNotGetDelegateListOfAliceEvenDelegated(server: GuiceJamesServer): Unit = {
    val delegateId1 = server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(ANDRE, BOB)
      .serialize
    val delegateId2 =  server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(ANDRE, CEDRIC)
      .serialize

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:apache:james:params:jmap:delegation"],
           |  "methodCalls": [[
           |    "Delegate/get",
           |    {
           |      "accountId": "$ANDRE_ACCOUNT_ID",
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

    assertThatJson(response)
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |        [
         |            "error",
         |            {
         |                "type": "forbidden",
         |                "description": "Access to other accounts settings is forbidden"
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def delegateGetReturnIdWhenNoPropertiesRequested(server: GuiceJamesServer): Unit = {
    val delegateId = server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(BOB, ANDRE)
      .serialize

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:apache:james:params:jmap:delegation"],
           |  "methodCalls": [[
           |    "Delegate/get",
           |    {
           |      "accountId": "$BOB_ACCOUNT_ID",
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
         |    "Delegate/get",
         |    {
         |      "accountId": "$BOB_ACCOUNT_ID",
         |      "list": [
         |        { "id" : "$delegateId" }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def delegateGetShouldFailWhenInvalidProperties(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(BOB, ANDRE)

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:apache:james:params:jmap:delegation"],
           |  "methodCalls": [[
           |    "Delegate/get",
           |    {
           |      "accountId": "$BOB_ACCOUNT_ID",
           |      "ids": null,
           |      "properties": ["invalid"]
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "error",
         |            {
         |                "type": "invalidArguments",
         |                "description": "The following properties [invalid] do not exist."
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def delegateGetShouldFailWhenWrongAccountId(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:apache:james:params:jmap:delegation"],
         |  "methodCalls": [[
         |    "Delegate/get",
         |    {
         |      "accountId": "unknownAccountId",
         |      "ids": null
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
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
  def delegateGetShouldFailWhenOmittingOneCapability(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core"],
         |  "methodCalls": [[
         |    "Delegate/get",
         |    {
         |      "accountId": "$BOB_ACCOUNT_ID",
         |      "ids": null
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
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
         |  "methodResponses": [[
         |    "error",
         |    {
         |      "type": "unknownMethod",
         |      "description":"Missing capability(ies): urn:apache:james:params:jmap:delegation"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def delegateGetShouldFailWhenOmittingAllCapability(): Unit = {
    val request =
      s"""{
         |  "using": [],
         |  "methodCalls": [[
         |    "Delegate/get",
         |    {
         |      "accountId": "$BOB_ACCOUNT_ID",
         |      "ids": null
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
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
         |  "methodResponses": [[
         |    "error",
         |    {
         |      "type": "unknownMethod",
         |      "description":"Missing capability(ies): urn:ietf:params:jmap:core, urn:apache:james:params:jmap:delegation"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

}
