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
import java.util.concurrent.atomic.AtomicReference

import com.google.common.hash.Hashing
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.{SC_BAD_REQUEST, SC_OK}
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE_PASSWORD, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Test}

object MailboxQueryChangesContractContext {
  case class TestContext(bobUsername: Username, bobAccountId: String, andreAccountId: String)
  val currentContext: AtomicReference[TestContext] = new AtomicReference[TestContext]()
}

trait MailboxQueryChangesContract {
  import MailboxQueryChangesContractContext.currentContext

  def bobUsername: Username = currentContext.get().bobUsername
  def bobAccountId: String = currentContext.get().bobAccountId
  def andreAccountId: String = currentContext.get().andreAccountId

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    val bob = Username.of(s"bob${UUID.randomUUID().toString.replace("-", "").take(8)}@${DOMAIN.asString}")
    val andre = Username.of(s"andre${UUID.randomUUID().toString.replace("-", "").take(8)}@${DOMAIN.asString}")
    currentContext.set(MailboxQueryChangesContractContext.TestContext(
      bob,
      Hashing.sha256().hashString(bob.asString, StandardCharsets.UTF_8).toString,
      Hashing.sha256().hashString(andre.asString, StandardCharsets.UTF_8).toString))
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addDomain("domain-alias.tld")
      .addUser(bobUsername.asString, BOB_PASSWORD)
      .addUser(s"andre${bobUsername.asString.drop(3)}", ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(bobUsername, BOB_PASSWORD)))
      .build
  }

  @Test
  def shouldReturnCannotCalculateChanges(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/queryChanges",
         |    {
         |      "accountId": "$bobAccountId",
         |      "sinceQueryState": "2c9f1b12-b35a-43e6-9af2-0106fb53a941",
         |      "calculateTotal": false
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
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
           |            "error",
           |            {
           |                "type": "cannotCalculateChanges",
           |                "description": "Naive implementation for Mailbox/queryChanges"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def wrongAccountIdShouldBeRejected(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/queryChanges",
         |    {
         |      "accountId": "$andreAccountId",
         |      "sinceQueryState": "2c9f1b12-b35a-43e6-9af2-0106fb53a941",
         |      "calculateTotal": false
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
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
           |            "error",
           |            {
           |                "type": "accountNotFound"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def missCapabilities(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core"],
         |  "methodCalls": [[
         |    "Mailbox/queryChanges",
         |    {
         |      "accountId": "$bobAccountId",
         |      "sinceQueryState": "2c9f1b12-b35a-43e6-9af2-0106fb53a941",
         |      "calculateTotal": false
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
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
           |            "error",
           |            {
           |                "type": "unknownMethod",
           |                "description": "Missing capability(ies): urn:ietf:params:jmap:mail"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }
}
