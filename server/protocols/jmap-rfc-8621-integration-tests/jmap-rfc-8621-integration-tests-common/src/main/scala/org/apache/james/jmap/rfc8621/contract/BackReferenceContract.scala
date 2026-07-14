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

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured._
import io.restassured.path.json.JsonPath
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.core.AccountId
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UuidState.INSTANCE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.{MailboxId, MailboxPath, MessageId}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}

object BackReferenceContract {
  case class TestContext(bob: Username)

  val currentContext: AtomicReference[TestContext] = new AtomicReference[TestContext]()
}

trait BackReferenceContract {
  import BackReferenceContract.currentContext

  def BOB: Username = currentContext.get().bob
  def BOB_ACCOUNT_ID: String = AccountId.from(BOB).toOption.get.id.value

  private def withBobAccountId(value: String): String = value.replace(ACCOUNT_ID, BOB_ACCOUNT_ID)

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    val bob = Username.fromLocalPartWithDomain(s"bob${UUID.randomUUID().toString.replace("-", "").take(8)}", DOMAIN)
    currentContext.set(BackReferenceContract.TestContext(bob))

    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(bob.asString, BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(bob, BOB_PASSWORD)))
      .build
  }

  @Test
  def backReferenceResolvingShouldWork(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(withBobAccountId(
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
           |}""".stripMargin))
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .extract()
      .body()
      .asString()

    val jsonPath = JsonPath.from(response)
    assertThat(jsonPath.getList[AnyRef]("methodResponses[1][1].list"))
      .isEqualTo(jsonPath.getList[AnyRef]("methodResponses[0][1].list"))

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].state", "methodResponses[0][1].list", "methodResponses[1][1].state", "methodResponses[1][1].list")
      .withOptions(Option.IGNORING_ARRAY_ORDER)
      .isEqualTo(withBobAccountId(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Mailbox/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "${INSTANCE.value}",
         |                "list": [
         |                    {"id": "1"},
         |                    {"id": "5"},
         |                    {"id": "2"},
         |                    {"id": "3"},
         |                    {"id": "4"},
         |                    {"id": "6"},
         |                    {"id": "7"}
         |                ],
         |                "notFound": []
         |            },
         |            "c1"
         |        ],
         |        [
         |            "Mailbox/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "list": [
         |                    {"id": "1"},
         |                    {"id": "5"},
         |                    {"id": "2"},
         |                    {"id": "3"},
         |                    {"id": "4"},
         |                    {"id": "6"},
         |                    {"id": "7"}
         |                ],
         |                "notFound": []
         |            },
         |            "c2"
         |        ]
         |    ]
         |}""".stripMargin))
  }

  @Test
  def pathShouldBeResolvable(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(withBobAccountId(
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
           |}""".stripMargin))
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].state", "methodResponses[0][1].list")
      .withOptions(Option.IGNORING_ARRAY_ORDER)
      .isEqualTo(withBobAccountId(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Mailbox/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "list": [
         |                    {"id": "1"},
         |                    {"id": "5"},
         |                    {"id": "2"},
         |                    {"id": "3"},
         |                    {"id": "4"},
         |                    {"id": "6"},
         |                    {"id": "7"}
         |                ],
         |                "notFound": []
         |            },
         |            "c1"
         |        ],
         |        [
         |            "error",
         |            {
         |                "type": "invalidResultReference",
         |                "description": "Failed resolving back-reference: List((,List(JsonValidationError(List(Expected path unknown was missing),List()))))"
         |            },
         |            "c2"
         |        ]
         |    ]
         |}""".stripMargin))
  }

  @Test
  def wildcardRequiresAnArray(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(withBobAccountId(
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
           |}""".stripMargin))
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].state", "methodResponses[0][1].list")
      .withOptions(Option.IGNORING_ARRAY_ORDER)
      .isEqualTo(withBobAccountId(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Mailbox/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "list": [
         |                    {"id": "1"},
         |                    {"id": "5"},
         |                    {"id": "2"},
         |                    {"id": "3"},
         |                    {"id": "4"},
         |                    {"id": "6"},
         |                    {"id": "7"}
         |                ],
         |                "notFound": []
         |            },
         |            "c1"
         |        ],
         |        [
         |            "error",
         |            {
         |                "type": "invalidResultReference",
         |                "description": "Failed resolving back-reference: List((,List(JsonValidationError(List(Expecting an array),List()))))"
         |            },
         |            "c2"
         |        ]
         |    ]
         |}""".stripMargin))
  }

  @Test
  def resolvedBackReferenceShouldHaveTheRightMethodName(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(withBobAccountId(
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
           |}""".stripMargin))
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].state", "methodResponses[0][1].list")
      .withOptions(Option.IGNORING_ARRAY_ORDER)
      .isEqualTo(withBobAccountId(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Mailbox/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "list": [
         |                    {"id": "1"},
         |                    {"id": "5"},
         |                    {"id": "2"},
         |                    {"id": "3"},
         |                    {"id": "4"},
         |                    {"id": "6"},
         |                    {"id": "7"}
         |                ],
         |                "notFound": []
         |            },
         |            "c1"
         |        ],
         |        [
         |            "error",
         |            {
         |                "type": "invalidResultReference",
         |                "description": "Failed resolving back-reference: List((,List(JsonValidationError(List(MethodCallId(c1) references a MethodName(Mailbox/get) method),List()))))"
         |            },
         |            "c2"
         |        ]
         |    ]
         |}""".stripMargin))
  }

  @Test
  def resolvingAnUnexistingMethodCallIdShouldFail(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(withBobAccountId(
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
           |}""".stripMargin))
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].state", "methodResponses[0][1].list")
      .withOptions(Option.IGNORING_ARRAY_ORDER)
      .isEqualTo(withBobAccountId(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Mailbox/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "list": [
         |                    {"id": "1"},
         |                    {"id": "5"},
         |                    {"id": "2"},
         |                    {"id": "3"},
         |                    {"id": "4"},
         |                    {"id": "6"},
         |                    {"id": "7"}
         |                ],
         |                "notFound": []
         |            },
         |            "c1"
         |        ],
         |        [
         |            "error",
         |            {
         |                "type": "invalidResultReference",
         |                "description": "Failed resolving back-reference: List((,List(JsonValidationError(List(Back reference could not be resolved),List()))))"
         |            },
         |            "c2"
         |        ]
         |    ]
         |}""".stripMargin))
  }

  @Test
  def loadingAccountWithBackReferences(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId: MailboxId = mailboxProbe
      .createMailbox(MailboxPath.inbox(BOB))
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(Message.Builder
        .of.setSubject("message 1")
        .setBody("testmail", StandardCharsets.UTF_8)
        .build))
      .getMessageId
    val messageId2: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(Message.Builder
        .of.setSubject("message 2")
        .setBody("testmail", StandardCharsets.UTF_8)
        .build))
      .getMessageId
    val messageId3: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(Message.Builder
        .of.setSubject("message 3")
        .setBody("testmail", StandardCharsets.UTF_8)
        .build))
      .getMessageId

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(withBobAccountId(
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
           |    "c1"],[
           |    "Email/query",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "filter": {
           |        "#inMailbox": {
           |         "resultOf":"c1",
           |         "name":"Mailbox/query",
           |         "path":"ids/0"
           |       }
           |      },
           |      "sort": [{
           |        "property":"receivedAt",
           |        "isAscending": false
           |      }]
           |    },
           |    "c2"], [
           |     "Email/get",
           |     {
           |       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |       "properties": ["id", "subject"],
           |       "#ids": {
           |         "resultOf":"c2",
           |         "name":"Email/query",
           |         "path":"ids/*"
           |       }
           |     },
           |     "c3"]]
           |}""".stripMargin))
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .withOptions(Option.IGNORING_ARRAY_ORDER)
      .whenIgnoringPaths("methodResponses[0][1].queryState", "methodResponses[1][1].queryState", "methodResponses[2][1].state")
      .isEqualTo(withBobAccountId(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Mailbox/query",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "queryState": "33cc79f8",
         |                "canCalculateChanges": false,
         |                "ids": ["${mailboxId.serialize}"],
         |                "position": 0,
         |                "limit": 256
         |            },
         |            "c1"
         |        ],
         |        [
         |            "Email/query",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "queryState": "a6904f19",
         |                "canCalculateChanges": false,
         |                "ids": ["${messageId1.serialize}", "${messageId2.serialize}", "${messageId3.serialize}"],
         |                "position": 0,
         |                "limit": 256
         |            },
         |            "c2"
         |        ],
         |        [
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "${INSTANCE.value}",
         |                "list": [
         |                    {"id": "${messageId3.serialize}", "subject": "message 3"},
         |                    {"id": "${messageId2.serialize}", "subject": "message 2"},
         |                    {"id": "${messageId1.serialize}", "subject": "message 1"}
         |                ],
         |                "notFound": []
         |            },
         |            "c3"
         |        ]
         |    ]
         |}""".stripMargin))
  }
}
