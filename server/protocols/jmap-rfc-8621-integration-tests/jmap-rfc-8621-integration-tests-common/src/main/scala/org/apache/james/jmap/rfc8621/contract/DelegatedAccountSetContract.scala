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

import com.google.common.hash.Hashing
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.delegation.DelegationId
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE_PASSWORD, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}

import scala.jdk.CollectionConverters._

object DelegatedAccountSetContract {
  case class TestContext(bobUsername: Username, bobAccountId: String, andreUsername: Username, andreAccountId: String, cedricUsername: Username)
  val currentContext: java.util.concurrent.atomic.AtomicReference[TestContext] = new java.util.concurrent.atomic.AtomicReference[TestContext]()
}

trait DelegatedAccountSetContract {
  import DelegatedAccountSetContract.currentContext

  def bobUsername: Username = currentContext.get().bobUsername
  def bobAccountId: String = currentContext.get().bobAccountId
  def andreUsername: Username = currentContext.get().andreUsername
  def andreAccountId: String = currentContext.get().andreAccountId
  def cedricUsername: Username = currentContext.get().cedricUsername

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    val uniqueSuffix = UUID.randomUUID().toString.replace("-", "").take(8)
    val bob = Username.fromLocalPartWithDomain(s"bob$uniqueSuffix", DOMAIN)
    val andre = Username.fromLocalPartWithDomain(s"andre$uniqueSuffix", DOMAIN)
    val cedric = Username.fromLocalPartWithDomain(s"cedric$uniqueSuffix", DOMAIN)
    currentContext.set(DelegatedAccountSetContract.TestContext(
      bob, Hashing.sha256().hashString(bob.asString(), StandardCharsets.UTF_8).toString,
      andre, Hashing.sha256().hashString(andre.asString(), StandardCharsets.UTF_8).toString, cedric))
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(bob.asString, BOB_PASSWORD)
      .addUser(andre.asString, ANDRE_PASSWORD)
      .addUser(cedric.asString, "secret")

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(bobUsername, BOB_PASSWORD)))
      .build
  }

  @Test
  def delegatedAccountDestroyShouldSucceed(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(andreUsername, bobUsername)
    val andreToBobDelegationId = DelegationId.from(andreUsername, bobUsername).serialize

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:apache:james:params:jmap:delegation"],
         |	"methodCalls": [
         |		[
         |			"DelegatedAccount/set", {
         |				"accountId": "$bobAccountId",
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
           |                "accountId": "$bobAccountId",
           |                "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |                "destroyed": ["$andreToBobDelegationId"]
           |            },
           |            "0"
           |        ]
           |    ]
           |}""".stripMargin)

    assertThat(server.getProbe(classOf[DelegationProbe]).getDelegatedUsers(bobUsername).asJavaCollection)
      .isEmpty()
  }

  @Test
  def mixedCaseShouldDestroyOnlyRequestedEntry(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(andreUsername, bobUsername)
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(cedricUsername, bobUsername)
    val andreToBobDelegationId = DelegationId.from(andreUsername, bobUsername).serialize

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:apache:james:params:jmap:delegation"],
         |	"methodCalls": [
         |		[
         |			"DelegatedAccount/set", {
         |				"accountId": "$bobAccountId",
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
           |                "accountId": "$bobAccountId",
           |                "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |                "destroyed": ["$andreToBobDelegationId"]
           |            },
           |            "0"
           |        ]
           |    ]
           |}""".stripMargin)

    assertThat(server.getProbe(classOf[DelegationProbe]).getDelegatedUsers(bobUsername).asJavaCollection)
      .containsExactly(cedricUsername)
  }

  @Test
  def delegatedAccountDestroyShouldFailWhenMissingDelegationCapability(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(andreUsername, bobUsername)
    val delegationId = DelegationId.from(andreUsername, bobUsername).serialize

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core"],
         |	"methodCalls": [
         |		[
         |			"DelegatedAccount/set", {
         |				"accountId": "$bobAccountId",
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
      .addAuthorizedUser(andreUsername, bobUsername)
    val andreToBobDelegationId = DelegationId.from(andreUsername, bobUsername).serialize

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:apache:james:params:jmap:delegation"],
         |	"methodCalls": [
         |		[
         |			"DelegatedAccount/set", {
         |				"accountId": "$bobAccountId",
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
           |                "accountId": "$bobAccountId",
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
         |				"accountId": "$andreAccountId",
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
    server.getProbe(classOf[DelegationProbe]).addAuthorizedUser(andreUsername, bobUsername)

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:apache:james:params:jmap:delegation"],
         |	"methodCalls": [
         |		[
         |			"DelegatedAccount/set", {
         |				"accountId": "$andreAccountId",
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
         |				"accountId": "$bobAccountId",
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
           |		"accountId": "$bobAccountId",
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
