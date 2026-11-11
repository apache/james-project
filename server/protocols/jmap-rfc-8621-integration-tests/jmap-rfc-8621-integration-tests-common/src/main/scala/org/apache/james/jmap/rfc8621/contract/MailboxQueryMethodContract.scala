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
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UuidState.INSTANCE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE_PASSWORD, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.model.MailboxACL.Right
import org.apache.james.mailbox.model.{MailboxACL, MailboxId, MailboxPath}
import org.apache.james.modules.{ACLProbeImpl, MailboxProbeImpl}
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Test}
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.{CsvSource, ValueSource}

object MailboxQueryMethodContract {
  case class TestContext(bobUsername: Username, bobAccountId: String, andreUsername: Username)

  val currentContext: AtomicReference[TestContext] = new AtomicReference[TestContext]()
}

trait MailboxQueryMethodContract {
  import MailboxQueryMethodContract.{TestContext, currentContext}

  def bobUsername: Username = currentContext.get().bobUsername
  def bobAccountId: String = currentContext.get().bobAccountId
  def andreUsername: Username = currentContext.get().andreUsername

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    val uniqueSuffix = UUID.randomUUID().toString.replace("-", "").take(8)
    val bob = Username.fromLocalPartWithDomain(s"bob$uniqueSuffix", DOMAIN)
    val andre = Username.fromLocalPartWithDomain(s"andre$uniqueSuffix", DOMAIN)
    val bobAccountId = Hashing.sha256().hashString(bob.asString(), StandardCharsets.UTF_8).toString
    currentContext.set(TestContext(bob, bobAccountId, andre))

    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(bob.asString, BOB_PASSWORD)
      .addUser(andre.asString, ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(bob, BOB_PASSWORD)))
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
      .createMailbox(MailboxPath.forUser(bobUsername, "INBOX"))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/query",
         |    {
           |      "accountId": "$bobAccountId",
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
         |                "accountId": "$bobAccountId",
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
      .createMailbox(MailboxPath.forUser(bobUsername, "InBoX"))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/query",
         |    {
           |      "accountId": "$bobAccountId",
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
    val andreInbox = MailboxPath.inbox(andreUsername)
    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(andreInbox)
    val bobInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(bobUsername))

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreInbox, bobUsername.asString, MailboxACL.FULL_RIGHTS)

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [[
         |    "Mailbox/query",
         |    {
           |      "accountId": "$bobAccountId",
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
         |                "accountId": "$bobAccountId",
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
    val andreInbox = MailboxPath.inbox(andreUsername)
    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(andreInbox)
    val bobInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(bobUsername, "InBoX"))

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreInbox, bobUsername.asString, MailboxACL.FULL_RIGHTS)

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [[
         |    "Mailbox/query",
         |    {
           |      "accountId": "$bobAccountId",
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
         |                "accountId": "$bobAccountId",
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
           |      "accountId": "$bobAccountId",
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
           |      "accountId": "$bobAccountId",
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
  def emptyFilterShouldReturnAllMailboxes(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val inboxId: MailboxId = mailboxProbe.createMailbox(MailboxPath.inbox(bobUsername))
    val spamId: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(bobUsername, "Spam"))
    val projectsId: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(bobUsername, "Projects"))
    val inboxSentId: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(bobUsername, "INBOX.Sent"))

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [["Mailbox/query", {"accountId":"$bobAccountId", "filter":{}}, "q1"]]
               |}""".stripMargin)
      .when.post
      .`then`.statusCode(SC_OK).extract.asString

    assertThatJson(response).inPath("methodResponses[0]").isEqualTo(s"""["Mailbox/query",{
      |  "accountId":"$bobAccountId",
      |  "queryState":"${generateQueryState(inboxId, inboxSentId, projectsId, spamId)}",
      |  "canCalculateChanges":false,
      |  "ids":["${inboxId.serialize}","${inboxSentId.serialize}","${projectsId.serialize}","${spamId.serialize}"],
      |  "position":0,
      |  "limit":256
      |},"q1"]""".stripMargin)
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
           |      "accountId": "$bobAccountId",
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
  def missingFilterShouldReturnAllMailboxes(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val inboxId: MailboxId = mailboxProbe.createMailbox(MailboxPath.inbox(bobUsername))
    val spamId: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(bobUsername, "Spam"))
    val projectsId: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(bobUsername, "Projects"))
    val inboxSentId: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(bobUsername, "INBOX.Sent"))

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [["Mailbox/query", {"accountId":"$bobAccountId"}, "q1"]]
               |}""".stripMargin)
      .when.post
      .`then`.statusCode(SC_OK).extract.asString

    assertThatJson(response).inPath("methodResponses[0]").isEqualTo(s"""["Mailbox/query",{
      |  "accountId":"$bobAccountId",
      |  "queryState":"${generateQueryState(inboxId, inboxSentId, projectsId, spamId)}",
      |  "canCalculateChanges":false,
      |  "ids":["${inboxId.serialize}","${inboxSentId.serialize}","${projectsId.serialize}","${spamId.serialize}"],
      |  "position":0,
      |  "limit":256
      |},"q1"]""".stripMargin)
  }

  @ParameterizedTest
  @ValueSource(strings = Array("x-custom", "x-", "X-custom"))
  def xPrefixedRoleShouldBeRejected(role: String, server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(bobUsername, role))

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [["Mailbox/query", {"accountId":"$bobAccountId", "filter":{"role":"$role"}}, "q1"]]
               |}""".stripMargin)
      .when.post
      .`then`.statusCode(SC_OK).extract.asString

    assertThatJson(response).inPath("methodResponses[0]").isEqualTo(s"""["error",{
      |  "type":"invalidArguments",
      |  "description":"'/filter/role' property is not valid: $role is not a valid role"
      |},"q1"]""".stripMargin)
  }

  @ParameterizedTest
  @CsvSource(Array(
    "inbox, INBOX",
    "drafts, Drafts",
    "outbox, Outbox",
    "sent, Sent",
    "trash, Trash",
    "archive, Archive",
    "junk, Spam",
    "templates, Templates",
    "restored messages, Restored-Messages"))
  def roleNameShouldRetrieveTheMailbox(roleName: String, folderName: String, server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(bobUsername, folderName))

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [["Mailbox/query", {"accountId":"$bobAccountId", "filter":{"role":"$roleName"}}, "q1"]]
               |}""".stripMargin)
      .when.post
      .`then`.statusCode(SC_OK).extract.asString

    assertThatJson(response).inPath("methodResponses[0][1].ids").isEqualTo(s"""["${mailboxId.serialize}"]""")
  }

  @ParameterizedTest
  @ValueSource(strings = Array("Sent", "Spam", "Restored-Messages"))
  def folderNameShouldStillRetrieveTheMailbox(folderName: String, server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(bobUsername, folderName))

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [["Mailbox/query", {"accountId":"$bobAccountId", "filter":{"role":"$folderName"}}, "q1"]]
               |}""".stripMargin)
      .when.post
      .`then`.statusCode(SC_OK).extract.asString

    assertThatJson(response).inPath("methodResponses[0][1].ids").isEqualTo(s"""["${mailboxId.serialize}"]""")
  }

  @Test
  def spamShouldBeRejected(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(bobUsername, "Spam"))

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [["Mailbox/query", {"accountId":"$bobAccountId", "filter":{"role":"spam"}}, "q1"]]
               |}""".stripMargin)
      .when.post
      .`then`.statusCode(SC_OK).extract.asString

    assertThatJson(response).inPath("methodResponses[0]").isEqualTo("""["error",{
      |  "type":"invalidArguments",
      |  "description":"'/filter/role' property is not valid: spam is not a valid role"
      |},"q1"]""".stripMargin)
  }

  @Test
  def nullRoleShouldReturnMailboxesWithoutRole(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxPath.inbox(bobUsername))
    mailboxProbe.createMailbox(MailboxPath.forUser(bobUsername, "Sent"))
    val projectsId: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(bobUsername, "Projects"))
    val inboxSentId: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(bobUsername, "INBOX.Sent"))

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [["Mailbox/query", {"accountId":"$bobAccountId", "filter":{"role":null}}, "q1"]]
               |}""".stripMargin)
      .when.post
      .`then`.statusCode(SC_OK).extract.asString

    assertThatJson(response).inPath("methodResponses[0]").isEqualTo(s"""["Mailbox/query",{
      |  "accountId":"$bobAccountId",
      |  "queryState":"${generateQueryState(inboxSentId, projectsId)}",
      |  "canCalculateChanges":false,
      |  "ids":["${inboxSentId.serialize}","${projectsId.serialize}"],
      |  "position":0,
      |  "limit":256
      |},"q1"]""".stripMargin)
  }

  @Test
  def nullRoleShouldReturnEmptyWhenEveryMailboxHasARole(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(bobUsername))

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [["Mailbox/query", {"accountId":"$bobAccountId", "filter":{"role":null}}, "q1"]]
               |}""".stripMargin)
      .when.post
      .`then`.statusCode(SC_OK).extract.asString

    assertThatJson(response).inPath("methodResponses[0]").isEqualTo(s"""["Mailbox/query",{
      |  "accountId":"$bobAccountId",
      |  "queryState":"${generateQueryState()}",
      |  "canCalculateChanges":false,
      |  "ids":[],
      |  "position":0,
      |  "limit":256
      |},"q1"]""".stripMargin)
  }

  @Test
  def nullRoleShouldNotReturnSharedMailboxesWithoutSharesCapability(server: GuiceJamesServer): Unit = {
    val andreMailbox = MailboxPath.forUser(andreUsername, "Shared")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreMailbox)
    val projectsId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(bobUsername, "Projects"))
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailbox, bobUsername.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [["Mailbox/query", {"accountId":"$bobAccountId", "filter":{"role":null}}, "q1"]]
               |}""".stripMargin)
      .when.post
      .`then`.statusCode(SC_OK).extract.asString

    assertThatJson(response).inPath("methodResponses[0][1].ids").isEqualTo(s"""["${projectsId.serialize}"]""")
  }

  @Test
  def emptyFilterShouldNotReturnSharedMailboxesWithoutSharesCapability(server: GuiceJamesServer): Unit = {
    val andreMailbox = MailboxPath.forUser(andreUsername, "Shared")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreMailbox)
    val projectsId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(bobUsername, "Projects"))
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailbox, bobUsername.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [["Mailbox/query", {"accountId":"$bobAccountId", "filter":{}}, "q1"]]
               |}""".stripMargin)
      .when.post
      .`then`.statusCode(SC_OK).extract.asString

    assertThatJson(response).inPath("methodResponses[0][1].ids").isEqualTo(s"""["${projectsId.serialize}"]""")
  }

  @Test
  def nullRoleShouldReturnSharedMailboxesWithSharesCapability(server: GuiceJamesServer): Unit = {
    val andreMailbox = MailboxPath.forUser(andreUsername, "Shared")
    val andreMailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreMailbox)
    val projectsId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(bobUsername, "Projects"))
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailbox, bobUsername.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
               |  "methodCalls": [["Mailbox/query", {"accountId":"$bobAccountId", "filter":{"role":null}}, "q1"]]
               |}""".stripMargin)
      .when.post
      .`then`.statusCode(SC_OK).extract.asString

    assertThatJson(response).inPath("methodResponses[0][1].ids")
      .isEqualTo(s"""["${andreMailboxId.serialize}","${projectsId.serialize}"]""")
  }

  @Test
  def emptyFilterShouldReturnSharedMailboxesWithSharesCapability(server: GuiceJamesServer): Unit = {
    val andreMailbox = MailboxPath.forUser(andreUsername, "Shared")
    val andreMailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreMailbox)
    val projectsId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(bobUsername, "Projects"))
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailbox, bobUsername.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
               |  "methodCalls": [["Mailbox/query", {"accountId":"$bobAccountId", "filter":{}}, "q1"]]
               |}""".stripMargin)
      .when.post
      .`then`.statusCode(SC_OK).extract.asString

    assertThatJson(response).inPath("methodResponses[0][1].ids")
      .isEqualTo(s"""["${andreMailboxId.serialize}","${projectsId.serialize}"]""")
  }

  private def generateQueryState(ids: MailboxId*): String =
    Hashing.murmur3_32_fixed()
      .hashUnencodedChars(ids.toList.map(_.serialize()).mkString(" "))
      .toString
}
