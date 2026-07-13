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

import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic

import com.google.common.hash.Hashing
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.delegation.DelegationId
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Test}

object DelegatedAccountGetMethodContractContext {
  case class TestContext(bobUsername: Username, bobAccountId: String, andreUsername: Username, andreAccountId: String, cedricUsername: Username)

  val currentContext: atomic.AtomicReference[TestContext] = new atomic.AtomicReference[TestContext]()
}

trait DelegatedAccountGetMethodContract {
  import DelegatedAccountGetMethodContractContext.{TestContext, currentContext}

  def bobUsername: Username = currentContext.get().bobUsername
  def bobAccountId: String = currentContext.get().bobAccountId
  def andreUsername: Username = currentContext.get().andreUsername
  def andreAccountId: String = currentContext.get().andreAccountId
  def cedricUsername: Username = currentContext.get().cedricUsername

  private def accountId(username: Username): String =
    Hashing.sha256().hashString(username.asString(), StandardCharsets.UTF_8).toString

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    val uniqueSuffix = UUID.randomUUID().toString.replace("-", "").take(8)
    val bob = Username.fromLocalPartWithDomain(s"bob$uniqueSuffix", DOMAIN)
    val andre = Username.fromLocalPartWithDomain(s"andre$uniqueSuffix", DOMAIN)
    val cedric = Username.fromLocalPartWithDomain(s"cedric$uniqueSuffix", DOMAIN)
    currentContext.set(TestContext(
      bobUsername = bob,
      bobAccountId = accountId(bob),
      andreUsername = andre,
      andreAccountId = accountId(andre),
      cedricUsername = cedric))

    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(bob.asString, BOB_PASSWORD)
      .addUser(andre.asString(), ANDRE_PASSWORD)
      .addUser(cedric.asString(), "cedric_pass")

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(bob, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build
  }

  @Test
  def listShouldEmptyWhenAccountDoesNotHaveAuthorized(): Unit = {
    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:apache:james:params:jmap:delegation"],
           |  "methodCalls": [[
           |    "DelegatedAccount/get",
           |    {
           |      "accountId": "$bobAccountId",
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
         |    "DelegatedAccount/get",
         |    {
         |      "accountId": "$bobAccountId",
         |      "list": [],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def delegatedAccountGetShouldReturnListWhenAuthorized(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(andreUsername, bobUsername)

    val response: String = `given`
      .body(s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:apache:james:params:jmap:delegation"],
           |  "methodCalls": [[
           |    "DelegatedAccount/get",
           |    {
           |      "accountId": "$bobAccountId",
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
      .withOptions(IGNORING_ARRAY_ORDER)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "DelegatedAccount/get",
           |            {
           |                "accountId": "$bobAccountId",
           |                "notFound": [],
           |                "list": [
           |                    {
           |                        "id": "${DelegationId.from(andreUsername, bobUsername).serialize}",
           |                        "username": "${andreUsername.asString()}"
           |                    }
           |                ]
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def delegatedAccountGetShouldReturnEmptyListWhenIdsAreEmpty(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(andreUsername, bobUsername)

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:apache:james:params:jmap:delegation"],
           |  "methodCalls": [[
           |    "DelegatedAccount/get",
           |    {
           |      "accountId": "$bobAccountId",
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
         |    "DelegatedAccount/get",
         |    {
         |      "accountId": "$bobAccountId",
         |      "list": [],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def delegatedAccountGetShouldReturnNotFoundWhenIdDoesNotExist(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(andreUsername, bobUsername)

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:apache:james:params:jmap:delegation"],
           |  "methodCalls": [[
           |    "DelegatedAccount/get",
           |    {
           |      "accountId": "$bobAccountId",
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
         |    "DelegatedAccount/get",
         |    {
         |      "accountId": "$bobAccountId",
         |      "list": [],
         |      "notFound": [ "notFound1" ]
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def delegatedAccountGetShouldReturnNotFoundAndListWhenMixCases(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(andreUsername, bobUsername)
      .serialize
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(cedricUsername, bobUsername)
      .serialize

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:apache:james:params:jmap:delegation"],
           |  "methodCalls": [[
           |    "DelegatedAccount/get",
           |    {
           |      "accountId": "$bobAccountId",
           |      "ids": [ "notFound1", "${DelegationId.from(andreUsername, bobUsername).serialize}", "${DelegationId.from(cedricUsername, bobUsername).serialize}" ]
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
      .withOptions(IGNORING_ARRAY_ORDER)
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "DelegatedAccount/get",
         |    {
         |      "accountId": "$bobAccountId",
         |      "list": [
         |        {
         |          "id": "${DelegationId.from(andreUsername, bobUsername).serialize}",
         |          "username": "${andreUsername.asString()}"
         |        },
         |        {
         |          "id": "${DelegationId.from(cedricUsername, bobUsername).serialize}",
         |          "username": "${cedricUsername.asString()}"
         |        }
         |      ],
         |      "notFound": [ "notFound1" ]
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def delegatedAccountGetShouldNotReturnDelegateOfOtherUser(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(cedricUsername, andreUsername)

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:apache:james:params:jmap:delegation"],
           |  "methodCalls": [[
           |    "DelegatedAccount/get",
           |    {
           |      "accountId": "$bobAccountId",
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
         |    "DelegatedAccount/get",
         |    {
         |      "accountId": "$bobAccountId",
         |      "list": [],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def delegatedAccountGetShouldNotReturnDelegateOfOtherUserWhenProvideIds(server: GuiceJamesServer): Unit = {
    val delegateId = server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(andreUsername, cedricUsername)
      .serialize

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:apache:james:params:jmap:delegation"],
           |  "methodCalls": [[
           |    "DelegatedAccount/get",
           |    {
           |      "accountId": "$bobAccountId",
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
         |    "DelegatedAccount/get",
         |    {
         |      "accountId": "$bobAccountId",
         |      "list": [],
         |      "notFound": [ "$delegateId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def bobShouldNotGetDelegatedAccountListOfAliceEvenAuthorized(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(andreUsername, bobUsername)
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(cedricUsername, andreUsername)

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:apache:james:params:jmap:delegation"],
           |  "methodCalls": [[
           |    "DelegatedAccount/get",
           |    {
           |      "accountId": "$andreAccountId",
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
      .withOptions(IGNORING_ARRAY_ORDER)
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
  def delegatedAccountGetReturnIdWhenNoPropertiesRequested(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(andreUsername, bobUsername)

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:apache:james:params:jmap:delegation"],
           |  "methodCalls": [[
           |    "DelegatedAccount/get",
           |    {
           |      "accountId": "$bobAccountId",
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
         |    "DelegatedAccount/get",
         |    {
         |      "accountId": "$bobAccountId",
         |      "list": [
         |        { "id" : "${DelegationId.from(andreUsername, bobUsername).serialize}" }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def delegatedAccountGetShouldFailWhenInvalidProperties(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(andreUsername, bobUsername)

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:apache:james:params:jmap:delegation"],
           |  "methodCalls": [[
           |    "DelegatedAccount/get",
           |    {
           |      "accountId": "$bobAccountId",
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
  def delegatedAccountGetShouldFailWhenWrongAccountId(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:apache:james:params:jmap:delegation"],
         |  "methodCalls": [[
         |    "DelegatedAccount/get",
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
  def delegatedAccountGetShouldFailWhenOmittingOneCapability(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core"],
         |  "methodCalls": [[
         |    "DelegatedAccount/get",
         |    {
         |      "accountId": "$bobAccountId",
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
  def delegatedAccountGetShouldFailWhenOmittingAllCapability(): Unit = {
    val request =
      s"""{
         |  "using": [],
         |  "methodCalls": [[
         |    "DelegatedAccount/get",
         |    {
         |      "accountId": "$bobAccountId",
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
