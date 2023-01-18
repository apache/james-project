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
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.delegation.DelegationId
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.DelegatedAccountSetContract.BOB_ACCOUNT_ID
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE, ANDRE_ACCOUNT_ID, ANDRE_PASSWORD, BOB, BOB_PASSWORD, CEDRIC, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}

import scala.jdk.CollectionConverters._

object DelegatedAccountSetContract {
  val BOB_ACCOUNT_ID: String = Fixture.ACCOUNT_ID
}

trait DelegatedAccountSetContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)
      .addUser(CEDRIC.asString(), "secret")

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  @Test
  def delegatedAccountDestroyShouldSucceed(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(ANDRE, BOB)
    val andreToBobDelegationId = DelegationId.from(ANDRE, BOB).serialize

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:apache:james:params:jmap:delegation"],
         |	"methodCalls": [
         |		[
         |			"DelegatedAccount/set", {
         |				"accountId": "$BOB_ACCOUNT_ID",
         |				"destroy": ["$andreToBobDelegationId"]
         |			}, "0"
         |		]
         |	]
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
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "DelegatedAccount/set",
           |            {
           |                "accountId": "$BOB_ACCOUNT_ID",
           |                "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |                "destroyed": ["$andreToBobDelegationId"]
           |            },
           |            "0"
           |        ]
           |    ]
           |}""".stripMargin)

    assertThat(server.getProbe(classOf[DelegationProbe]).getDelegatedUsers(BOB).asJavaCollection)
      .isEmpty()
  }

  @Test
  def mixedCaseShouldDestroyOnlyRequestedEntry(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(ANDRE, BOB)
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(CEDRIC, BOB)
    val andreToBobDelegationId = DelegationId.from(ANDRE, BOB).serialize

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:apache:james:params:jmap:delegation"],
         |	"methodCalls": [
         |		[
         |			"DelegatedAccount/set", {
         |				"accountId": "$BOB_ACCOUNT_ID",
         |				"destroy": ["$andreToBobDelegationId"]
         |			}, "0"
         |		]
         |	]
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
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "DelegatedAccount/set",
           |            {
           |                "accountId": "$BOB_ACCOUNT_ID",
           |                "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |                "destroyed": ["$andreToBobDelegationId"]
           |            },
           |            "0"
           |        ]
           |    ]
           |}""".stripMargin)

    assertThat(server.getProbe(classOf[DelegationProbe]).getDelegatedUsers(BOB).asJavaCollection)
      .containsExactly(CEDRIC)
  }

  @Test
  def delegatedAccountDestroyShouldFailWhenMissingDelegationCapability(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(ANDRE, BOB)
    val delegationId = DelegationId.from(ANDRE, BOB).serialize

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core"],
         |	"methodCalls": [
         |		[
         |			"DelegatedAccount/set", {
         |				"accountId": "$BOB_ACCOUNT_ID",
         |				"destroy": ["$delegationId"]
         |			}, "0"
         |		]
         |	]
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
        s"""{
           |	"sessionState": "${SESSION_STATE.value}",
           |	"methodResponses": [
           |		[
           |			"error",
           |			{
           |				"type": "unknownMethod",
           |				"description": "Missing capability(ies): urn:apache:james:params:jmap:delegation"
           |			},
           |			"0"
           |		]
           |	]
           |}""".stripMargin)
  }

  @Test
  def delegatedAccountDestroyShouldBeIdempotent(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(ANDRE, BOB)
    val andreToBobDelegationId = DelegationId.from(ANDRE, BOB).serialize

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:apache:james:params:jmap:delegation"],
         |	"methodCalls": [
         |		[
         |			"DelegatedAccount/set", {
         |				"accountId": "$BOB_ACCOUNT_ID",
         |				"destroy": ["$andreToBobDelegationId", "$andreToBobDelegationId"]
         |			}, "0"
         |		]
         |	]
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
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "DelegatedAccount/set",
           |            {
           |                "accountId": "$BOB_ACCOUNT_ID",
           |                "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |                "destroyed": ["$andreToBobDelegationId", "$andreToBobDelegationId"]
           |            },
           |            "0"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def shouldReturnNotFoundWhenTryToAccessNonDelegatedAccount(): Unit = {
    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:apache:james:params:jmap:delegation"],
         |	"methodCalls": [
         |		[
         |			"DelegatedAccount/set", {
         |				"accountId": "$ANDRE_ACCOUNT_ID",
         |				"destroy": ["any"]
         |			}, "0"
         |		]
         |	]
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
        s"""{
           |	"type": "accountNotFound"
           |}""".stripMargin)
  }

  @Test
  def bobCanOnlyManageHisPrimaryAccountSetting(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe]).addAuthorizedUser(ANDRE, BOB)

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:apache:james:params:jmap:delegation"],
         |	"methodCalls": [
         |		[
         |			"DelegatedAccount/set", {
         |				"accountId": "$ANDRE_ACCOUNT_ID",
         |				"destroy": ["any"]
         |			}, "0"
         |		]
         |	]
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
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |	"error",
           |	{
           |		"type": "forbidden",
           |		"description": "Access to other accounts settings is forbidden"
           |	},
           |	"0"
           |]""".stripMargin)
  }


  @Test
  def destroyShouldFailWhenInvalidId(): Unit = {
    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:apache:james:params:jmap:delegation"],
         |	"methodCalls": [
         |		[
         |			"DelegatedAccount/set", {
         |				"accountId": "$BOB_ACCOUNT_ID",
         |				"destroy": ["invalid"]
         |			}, "0"
         |		]
         |	]
         |}""".stripMargin

    val response: String = `given`
      .body(request)
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |	"DelegatedAccount/set",
           |	{
           |		"accountId": "$BOB_ACCOUNT_ID",
           |		"newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |		"notDestroyed": {
           |			"invalid": {
           |				"type": "invalidArguments",
           |				"description": "invalid is not a DelegationId: Invalid UUID string: invalid"
           |			}
           |		}
           |	},
           |	"0"
           |]""".stripMargin)
  }
}