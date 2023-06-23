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

import com.google.common.hash.Hashing
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UuidState.INSTANCE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.model.{MailboxACL, MailboxId, MailboxPath}
import org.apache.james.modules.{ACLProbeImpl, MailboxProbeImpl}
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Test}

trait MailboxQueryMethodContract {
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
  def mailboxQueryShouldFailWhenWrongAccountId(server: GuiceJamesServer): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/query",
         |    {
         |      "accountId": "unknownAccountId",
         |      "filter": {"role":"Inbox"}
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
  def roleShouldAllowToRetrieveTheInbox(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "INBOX"))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {"role":"Inbox"}
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
           |    "sessionState": "${INSTANCE.value}",
           |    "methodResponses": [
           |        [
           |            "Mailbox/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(mailboxId)}",
           |                "canCalculateChanges": false,
           |                "ids": [
           |                    "${mailboxId.serialize}"
           |                ],
           |                "position": 0,
           |                "limit": 256
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def roleShouldAllowToRetrieveTheInboxUponCaseVariation(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "InBoX"))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {"role":"Inbox"}
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
      .inPath("methodResponses[0][1].ids")
        .isEqualTo(s"""[
           |  "${mailboxId.serialize}"
           |]""".stripMargin)
  }

  @Test
  def queryByRoleShouldNotReturnDelegatedMailboxes(server: GuiceJamesServer): Unit = {
    val andreInbox = MailboxPath.inbox(ANDRE)
    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(andreInbox)
    val bobInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(BOB))

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreInbox, BOB.asString, MailboxACL.FULL_RIGHTS)

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [[
         |    "Mailbox/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {"role":"Inbox"}
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
           |            "Mailbox/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(bobInboxId)}",
           |                "canCalculateChanges": false,
           |                "ids": [
           |                    "${bobInboxId.serialize}"
           |                ],
           |                "position": 0,
           |                "limit": 256
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def queryByRoleShouldNotReturnDelegatedMailboxesWhenCaseVariation(server: GuiceJamesServer): Unit = {
    val andreInbox = MailboxPath.inbox(ANDRE)
    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(andreInbox)
    val bobInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "InBoX"))

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreInbox, BOB.asString, MailboxACL.FULL_RIGHTS)

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [[
         |    "Mailbox/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {"role":"Inbox"}
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
           |            "Mailbox/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(bobInboxId)}",
           |                "canCalculateChanges": false,
           |                "ids": [
           |                    "${bobInboxId.serialize}"
           |                ],
           |                "position": 0,
           |                "limit": 256
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def invalidRoleShouldBeRejected(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {"role":"Invalid"}
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
           |                "type": "invalidArguments",
           |                "description": "'/filter/role' property is not valid: Invalid is not a valid role"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def roleShouldBeAString(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {"role":123}
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
           |                "type": "invalidArguments",
           |                "description": "'/filter/role' property is not valid: Expecting a JsString to be representing a role"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}
           |""".stripMargin)
  }

  @Test
  def roleShouldBeCompulsory(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {}
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
           |                "type": "invalidArguments",
           |                "description": "Missing '/filter/role' property"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def shouldReturnInvalidArgumentsWhenInvalidFilterCondition(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter":{
         |        "unsupported_option": "blahh_blahh",
         |        "role":"Inbox"
         |      }
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
           |                "type": "invalidArguments",
           |                "description": "'/filter' property is not valid: These '[unsupported_option]' was unsupported filter options"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def filterShouldBeCompulsory(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
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
           |                "type": "invalidArguments",
           |                "description": "Missing '/filter' property"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}
           |""".stripMargin)
  }

  private def generateQueryState(ids: MailboxId*): String =
    Hashing.murmur3_32_fixed()
      .hashUnencodedChars(ids.toList.map(_.serialize()).mkString(" "))
      .toString
}
