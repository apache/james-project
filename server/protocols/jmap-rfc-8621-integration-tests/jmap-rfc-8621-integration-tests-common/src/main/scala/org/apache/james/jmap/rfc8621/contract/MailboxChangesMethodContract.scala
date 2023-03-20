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
import io.restassured.builder.ResponseSpecBuilder
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import net.javacrumbs.jsonunit.core.internal.Options
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.api.change.State
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.draft.JmapGuiceProbe
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE, ANDRE_ACCOUNT_ID, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.MailboxACL.Right
import org.apache.james.mailbox.model.{MailboxACL, MailboxId, MailboxPath, MessageId}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.{ACLProbeImpl, MailboxProbeImpl}
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.junit.jupiter.api.{BeforeEach, Nested, Test}
import play.api.libs.json.{JsArray, JsString, Json}

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.mail.Flags

trait MailboxChangesMethodContract {

  private lazy val slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS
  private lazy val calmlyAwait = Awaitility.`with`
    .pollInterval(slowPacedPollInterval)
    .and.`with`.pollDelay(slowPacedPollInterval)
    .await
  private lazy val awaitAtMostTenSeconds = calmlyAwait.atMost(10, TimeUnit.SECONDS)

  def stateFactory: State.Factory
  def generateMailboxId: MailboxId

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addDomain("domain-alias.tld")
      .addUser(BOB.asString, BOB_PASSWORD)
      .addUser(ANDRE.asString, ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  @Test
  def mailboxChangesShouldReturnCreatedChanges(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    val provisioningState: State = provisionSystemMailboxes(server)

    val mailboxId1: String = mailboxProbe
      .createMailbox(MailboxPath.forUser(BOB, "mailbox1"))
      .serialize

    val mailboxId2: String = mailboxProbe
      .createMailbox(MailboxPath.forUser(BOB, "mailbox2"))
      .serialize

    val mailboxId3: String = mailboxProbe
      .createMailbox(MailboxPath.forUser(BOB, "mailbox3"))
      .serialize

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/changes",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sinceState": "${provisioningState.getValue}"
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
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
        .whenIgnoringPaths("methodResponses[0][1].newState")
        .withOptions(new Options(IGNORING_ARRAY_ORDER))
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |      [ "Mailbox/changes", {
             |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |        "oldState": "${provisioningState.getValue}",
             |        "hasMoreChanges": false,
             |        "updatedProperties": null,
             |        "created": ["$mailboxId1", "$mailboxId2", "$mailboxId3"],
             |        "updated": [],
             |        "destroyed": []
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
    }
  }

  @Test
  def mailboxChangesShouldReturnUpdatedChangesWhenRenameMailbox(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    val accountId: AccountId = AccountId.fromUsername(BOB)

    val provisioningState: State = provisionSystemMailboxes(server)

    val path = MailboxPath.forUser(BOB, "mailbox1")
    val mailboxId: String = mailboxProbe
      .createMailbox(path)
      .serialize

    val oldState: State = waitForNextState(server, accountId, provisioningState)

    JmapRequests.renameMailbox(mailboxId, "mailbox11")

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/changes",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sinceState": "${oldState.getValue}"
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
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
        .whenIgnoringPaths("methodResponses[0][1].newState")
        .withOptions(new Options(IGNORING_ARRAY_ORDER))
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |      [ "Mailbox/changes", {
             |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |        "oldState": "${oldState.getValue}",
             |        "hasMoreChanges": false,
             |        "updatedProperties": null,
             |        "created": [],
             |        "updated": ["$mailboxId"],
             |        "destroyed": []
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
    }
  }

  @Test
  def mailboxChangesShouldReturnUpdatedChangesWhenSubscribed(server: GuiceJamesServer): Unit = {
    val accountId: AccountId = AccountId.fromUsername(BOB)
    val provisioningState: State = provisionSystemMailboxes(server)

    // create mailbox with isSubscribed = false
    val mailboxId: String = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .body(
      """
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "create": {
        |                    "C42": {
        |                      "name": "myMailbox",
        |                      "isSubscribed": false
        |                    }
        |                }
        |           },
        |    "c1"
        |       ]
        |   ]
        |}
        |""".stripMargin)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .jsonPath()
      .get("methodResponses[0][1].created.C42.id");

    val oldState: State = waitForNextState(server, accountId, provisioningState)

    // change subscription
    JmapRequests.subscribe(mailboxId)

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(
          s"""{
             |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
             |  "methodCalls": [[
             |    "Mailbox/changes",
             |    {
             |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |      "sinceState": "${oldState.getValue}"
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
        .whenIgnoringPaths("methodResponses[0][1].newState")
        .withOptions(new Options(IGNORING_ARRAY_ORDER))
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |      [ "Mailbox/changes", {
             |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |        "oldState": "${oldState.getValue}",
             |        "hasMoreChanges": false,
             |        "updatedProperties": null,
             |        "created": [],
             |        "updated": ["$mailboxId"],
             |        "destroyed": []
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
    }
  }

  @Test
  def mailboxChangesShouldReturnUpdatedChangesWhenUnSubscribed(server: GuiceJamesServer): Unit = {
    val accountId: AccountId = AccountId.fromUsername(BOB)
    val provisioningState: State = provisionSystemMailboxes(server)

    // create mailbox with isSubscribed = true
    val mailboxId: String = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        """
          |{
          |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
          |   "methodCalls": [
          |       [
          |           "Mailbox/set",
          |           {
          |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
          |                "create": {
          |                    "C42": {
          |                      "name": "myMailbox",
          |                      "isSubscribed": true
          |                    }
          |                }
          |           },
          |    "c1"
          |       ]
          |   ]
          |}
          |""".stripMargin)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .jsonPath()
      .get("methodResponses[0][1].created.C42.id");

    val oldState: State = waitForNextState(server, accountId, provisioningState)

    // change subscription
    JmapRequests.unSubscribe(mailboxId)

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(
          s"""{
             |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
             |  "methodCalls": [[
             |    "Mailbox/changes",
             |    {
             |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |      "sinceState": "${oldState.getValue}"
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
        .whenIgnoringPaths("methodResponses[0][1].newState")
        .withOptions(new Options(IGNORING_ARRAY_ORDER))
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |      [ "Mailbox/changes", {
             |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |        "oldState": "${oldState.getValue}",
             |        "hasMoreChanges": false,
             |        "updatedProperties": null,
             |        "created": [],
             |        "updated": ["$mailboxId"],
             |        "destroyed": []
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
    }
  }

  @Test
  def mailboxChangesShouldReturnUpdatedChangesWhenAppendMessageToMailbox(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    val provisioningState: State = provisionSystemMailboxes(server)

    val path = MailboxPath.forUser(BOB, "mailbox1")
    val mailboxId: String = mailboxProbe
      .createMailbox(path)
      .serialize

    val oldState: State = waitForNextState(server, AccountId.fromUsername(BOB), provisioningState)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    mailboxProbe.appendMessage(BOB.asString(), path, AppendCommand.from(message))

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/changes",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sinceState": "${oldState.getValue}"
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
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
        .whenIgnoringPaths("methodResponses[0][1].newState")
        .withOptions(new Options(IGNORING_ARRAY_ORDER))
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |      [ "Mailbox/changes", {
             |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |        "oldState": "${oldState.getValue}",
             |        "hasMoreChanges": false,
             |        "updatedProperties": ["totalEmails", "unreadEmails", "totalThreads", "unreadThreads"],
             |        "created": [],
             |        "updated": ["$mailboxId"],
             |        "destroyed": []
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
    }
  }

  @Test
  def mailboxChangesShouldReturnUpdatedChangesWhenAddSeenFlag(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    val accountId: AccountId = AccountId.fromUsername(BOB)

    val provisioningState: State = provisionSystemMailboxes(server)

    val path = MailboxPath.forUser(BOB, "mailbox1")
    val mailboxId: String = mailboxProbe
      .createMailbox(path)
      .serialize

    val state1: State = waitForNextState(server, accountId, provisioningState)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId: MessageId = mailboxProbe.appendMessage(BOB.asString(), path, AppendCommand.from(message)).getMessageId

    val oldState: State = waitForNextState(server, accountId, state1)

    JmapRequests.markEmailAsSeen(messageId)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/changes",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sinceState": "${oldState.getValue}"
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
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
        .whenIgnoringPaths("methodResponses[0][1].newState")
        .withOptions(new Options(IGNORING_ARRAY_ORDER))
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |      [ "Mailbox/changes", {
             |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |        "oldState": "${oldState.getValue}",
             |        "hasMoreChanges": false,
             |        "updatedProperties": ["totalEmails", "unreadEmails", "totalThreads", "unreadThreads"],
             |        "created": [],
             |        "updated": ["$mailboxId"],
             |        "destroyed": []
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
    }
  }

  @Test
  def mailboxChangesShouldReturnUpdatedChangesWhenRemoveSeenFlag(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    val accountId: AccountId = AccountId.fromUsername(BOB)

    val provisioningState: State = provisionSystemMailboxes(server)

    val path = MailboxPath.forUser(BOB, "mailbox1")
    val mailboxId: String = mailboxProbe
      .createMailbox(path)
      .serialize

    val state1: State = waitForNextState(server, accountId, provisioningState)

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, ANDRE.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))

    val state2: State = waitForNextState(server, accountId, state1)

    val messageId: MessageId = mailboxProbe.appendMessage(BOB.asString(), path,
      AppendCommand.builder()
        .withFlags(new Flags(Flags.Flag.SEEN))
        .build("header: value\r\n\r\nbody"))
      .getMessageId

    val oldState: State = waitForNextState(server, accountId, state2)

    JmapRequests.markEmailAsNotSeen(messageId)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/changes",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sinceState": "${oldState.getValue}"
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
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
        .whenIgnoringPaths("methodResponses[0][1].newState")
        .withOptions(new Options(IGNORING_ARRAY_ORDER))
        .isEqualTo(
          s"""{
             |  "sessionState": "${SESSION_STATE.value}",
             |  "methodResponses": [
             |    ["Mailbox/changes", {
             |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |      "oldState": "${oldState.getValue}",
             |      "hasMoreChanges": false,
             |      "updatedProperties": ["totalEmails", "unreadEmails", "totalThreads", "unreadThreads"],
             |      "created": [],
             |      "updated": ["$mailboxId"],
             |      "destroyed": []
             |    }, "c1"]
             |  ]
             |}""".stripMargin)
    }
  }

  @Test
  def mailboxChangesShouldReturnUpdatedChangesWhenDestroyEmail(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    val accountId: AccountId = AccountId.fromUsername(BOB)

    val provisioningState: State = provisionSystemMailboxes(server)

    val path = MailboxPath.forUser(BOB, "mailbox1")
    val mailboxId: String = mailboxProbe
      .createMailbox(path)
      .serialize

    val state1: State = waitForNextState(server, accountId, provisioningState)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId: MessageId = mailboxProbe.appendMessage(BOB.asString(), path, AppendCommand.from(message)).getMessageId

    val oldState: State = waitForNextState(server, accountId, state1)

    JmapRequests.destroyEmail(messageId)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/changes",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sinceState": "${oldState.getValue}"
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
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
        .whenIgnoringPaths("methodResponses[0][1].newState")
        .withOptions(new Options(IGNORING_ARRAY_ORDER))
        .isEqualTo(
          s"""{
             |  "sessionState": "${SESSION_STATE.value}",
             |  "methodResponses": [
             |    ["Mailbox/changes", {
             |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |      "oldState": "${oldState.getValue}",
             |      "hasMoreChanges": false,
             |      "updatedProperties": ["totalEmails", "unreadEmails", "totalThreads", "unreadThreads"],
             |      "created": [],
             |      "updated": ["$mailboxId"],
             |      "destroyed": []
             |    }, "c1"]
             |  ]
             |}""".stripMargin)
    }
  }

  @Nested
  class MailboxDelegationTest {
    @Test
    def mailboxChangesShouldReturnUpdatedChangesWhenAppendMessageToMailbox(server: GuiceJamesServer): Unit = {
      val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

      provisionSystemMailboxes(server)

      val path = MailboxPath.forUser(BOB, "mailbox1")
      val mailboxId: String = mailboxProbe
        .createMailbox(path)
        .serialize

      server.getProbe(classOf[ACLProbeImpl])
        .replaceRights(path, ANDRE.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))

      val oldState: State = waitForNextStateWithDelegation(server, AccountId.fromUsername(ANDRE), State.INITIAL)

      val message: Message = Message.Builder
        .of
        .setSubject("test")
        .setBody("testmail", StandardCharsets.UTF_8)
        .build
      mailboxProbe.appendMessage(BOB.asString(), path, AppendCommand.from(message))

      val request =
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
           |  "methodCalls": [[
           |    "Mailbox/changes",
           |    {
           |      "accountId": "$ANDRE_ACCOUNT_ID",
           |      "sinceState": "${oldState.getValue}"
           |    },
           |    "c1"]]
           |}""".stripMargin

      awaitAtMostTenSeconds.untilAsserted { () =>
        val response = `given`(
          baseRequestSpecBuilder(server)
            .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
            .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
            .setBody(request)
            .build, new ResponseSpecBuilder().build)
          .post
          .`then`
            .statusCode(SC_OK)
            .contentType(JSON)
            .extract
            .body
            .asString

        assertThatJson(response)
          .whenIgnoringPaths("methodResponses[0][1].newState")
          .withOptions(new Options(IGNORING_ARRAY_ORDER))
          .isEqualTo(
            s"""{
               |    "sessionState": "${SESSION_STATE.value}",
               |    "methodResponses": [
               |      [ "Mailbox/changes", {
               |        "accountId": "$ANDRE_ACCOUNT_ID",
               |        "oldState": "${oldState.getValue}",
               |        "hasMoreChanges": false,
               |        "updatedProperties": ["totalEmails", "unreadEmails", "totalThreads", "unreadThreads"],
               |        "created": [],
               |        "updated": ["$mailboxId"],
               |        "destroyed": []
               |      }, "c1"]
               |    ]
               |}""".stripMargin)
      }
    }

    @Test
    def mailboxChangesShouldReturnUpdatedChangesWhenRenameMailbox(server: GuiceJamesServer): Unit = {
      val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

      provisionSystemMailboxes(server)

      val path = MailboxPath.forUser(BOB, "mailbox1")
      val mailboxId: String = mailboxProbe
        .createMailbox(path)
        .serialize

      server.getProbe(classOf[ACLProbeImpl])
        .replaceRights(path, ANDRE.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))

      val oldState: State = waitForNextStateWithDelegation(server, AccountId.fromUsername(ANDRE), State.INITIAL)

      JmapRequests.renameMailbox(mailboxId, "mailbox11")

      val request =
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
           |  "methodCalls": [[
           |    "Mailbox/changes",
           |    {
           |      "accountId": "$ANDRE_ACCOUNT_ID",
           |      "sinceState": "${oldState.getValue}"
           |    },
           |    "c1"]]
           |}""".stripMargin

      awaitAtMostTenSeconds.untilAsserted { () =>
        val response = `given`(
          baseRequestSpecBuilder(server)
            .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
            .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
            .setBody(request)
            .build, new ResponseSpecBuilder().build)
          .post
          .`then`
            .statusCode(SC_OK)
            .contentType(JSON)
            .extract
            .body
            .asString

        assertThatJson(response)
          .whenIgnoringPaths("methodResponses[0][1].newState")
          .withOptions(new Options(IGNORING_ARRAY_ORDER))
          .isEqualTo(
            s"""{
               |    "sessionState": "${SESSION_STATE.value}",
               |    "methodResponses": [
               |      [ "Mailbox/changes", {
               |        "accountId": "$ANDRE_ACCOUNT_ID",
               |        "oldState": "${oldState.getValue}",
               |        "hasMoreChanges": false,
               |        "updatedProperties":null,
               |        "created": [],
               |        "updated": ["$mailboxId"],
               |        "destroyed": []
               |      }, "c1"]
               |    ]
               |}""".stripMargin)
      }
    }

    @Test
    def mailboxChangesShouldReturnUpdatedChangesWhenAddSeenFlag(server: GuiceJamesServer): Unit = {
      val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

      provisionSystemMailboxes(server)

      val path = MailboxPath.forUser(BOB, "mailbox1")
      val mailboxId: String = mailboxProbe
        .createMailbox(path)
        .serialize

      server.getProbe(classOf[ACLProbeImpl])
        .replaceRights(path, ANDRE.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))

      val state1: State = waitForNextStateWithDelegation(server, AccountId.fromUsername(ANDRE), State.INITIAL)

      val message: Message = Message.Builder
        .of
        .setSubject("test")
        .setBody("testmail", StandardCharsets.UTF_8)
        .build
      val messageId: MessageId = mailboxProbe.appendMessage(BOB.asString(), path, AppendCommand.from(message)).getMessageId

      val oldState: State = waitForNextStateWithDelegation(server, AccountId.fromUsername(ANDRE), state1)

      JmapRequests.markEmailAsSeen(messageId)

      val request =
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
           |  "methodCalls": [[
           |    "Mailbox/changes",
           |    {
           |      "accountId": "$ANDRE_ACCOUNT_ID",
           |      "sinceState": "${oldState.getValue}"
           |    },
           |    "c1"]]
           |}""".stripMargin

      awaitAtMostTenSeconds.untilAsserted { () =>
        val response = `given`(
          baseRequestSpecBuilder(server)
            .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
            .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
            .setBody(request)
            .build, new ResponseSpecBuilder().build)
          .post
          .`then`
            .statusCode(SC_OK)
            .contentType(JSON)
            .extract
            .body
            .asString

          assertThatJson(response)
            .whenIgnoringPaths("methodResponses[0][1].newState")
            .withOptions(new Options(IGNORING_ARRAY_ORDER))
            .isEqualTo(
              s"""{
                 |    "sessionState": "${SESSION_STATE.value}",
                 |    "methodResponses": [
                 |      [ "Mailbox/changes", {
                 |        "accountId": "$ANDRE_ACCOUNT_ID",
                 |        "oldState": "${oldState.getValue}",
                 |        "hasMoreChanges": false,
                 |        "updatedProperties": ["totalEmails", "unreadEmails", "totalThreads", "unreadThreads"],
                 |        "created": [],
                 |        "updated": ["$mailboxId"],
                 |        "destroyed": []
                 |      }, "c1"]
                 |    ]
                 |}""".stripMargin)
      }
    }

    @Test
    def mailboxChangesShouldReturnUpdatedChangesWhenRemoveSeenFlag(server: GuiceJamesServer): Unit = {
      val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

      provisionSystemMailboxes(server)

      val path = MailboxPath.forUser(BOB, "mailbox1")
      val mailboxId: String = mailboxProbe
        .createMailbox(path)
        .serialize

      server.getProbe(classOf[ACLProbeImpl])
        .replaceRights(path, ANDRE.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))

      val state1: State = waitForNextStateWithDelegation(server, AccountId.fromUsername(ANDRE), State.INITIAL)

      val messageId: MessageId = mailboxProbe.appendMessage(BOB.asString(), path,
        AppendCommand.builder()
          .withFlags(new Flags(Flags.Flag.SEEN))
          .build("header: value\r\n\r\nbody"))
        .getMessageId

      val oldState: State = waitForNextStateWithDelegation(server, AccountId.fromUsername(ANDRE), state1)

      JmapRequests.markEmailAsNotSeen(messageId)

      val request =
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
           |  "methodCalls": [[
           |    "Mailbox/changes",
           |    {
           |      "accountId": "$ANDRE_ACCOUNT_ID",
           |      "sinceState": "${oldState.getValue}"
           |    },
           |    "c1"]]
           |}""".stripMargin

      awaitAtMostTenSeconds.untilAsserted { () =>
        val response = `given`(
          baseRequestSpecBuilder(server)
            .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
            .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
            .setBody(request)
            .build, new ResponseSpecBuilder().build)
          .post
          .`then`
            .statusCode(SC_OK)
            .contentType(JSON)
            .extract
            .body
            .asString

        assertThatJson(response)
          .whenIgnoringPaths("methodResponses[0][1].newState")
          .withOptions(new Options(IGNORING_ARRAY_ORDER))
          .isEqualTo(
            s"""{
               |    "sessionState": "${SESSION_STATE.value}",
               |    "methodResponses": [
               |      [ "Mailbox/changes", {
               |        "accountId": "$ANDRE_ACCOUNT_ID",
               |        "oldState": "${oldState.getValue}",
               |        "hasMoreChanges": false,
               |        "updatedProperties": ["totalEmails", "unreadEmails", "totalThreads", "unreadThreads"],
               |        "created": [],
               |        "updated": ["$mailboxId"],
               |        "destroyed": []
               |      }, "c1"]
               |    ]
               |}""".stripMargin)
      }
    }

    @Test
    def mailboxChangesShouldReturnUpdatedChangesWhenDestroyEmail(server: GuiceJamesServer): Unit = {
      val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

      provisionSystemMailboxes(server)

      val path = MailboxPath.forUser(BOB, "mailbox1")
      val mailboxId: String = mailboxProbe
        .createMailbox(path)
        .serialize

      server.getProbe(classOf[ACLProbeImpl])
        .replaceRights(path, ANDRE.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))

      val state1: State = waitForNextStateWithDelegation(server, AccountId.fromUsername(ANDRE), State.INITIAL)

      val message: Message = Message.Builder
        .of
        .setSubject("test")
        .setBody("testmail", StandardCharsets.UTF_8)
        .build
      val messageId: MessageId = mailboxProbe.appendMessage(BOB.asString(), path, AppendCommand.from(message)).getMessageId

      val oldState: State = waitForNextStateWithDelegation(server, AccountId.fromUsername(ANDRE), state1)

      JmapRequests.destroyEmail(messageId)

      val request =
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
           |  "methodCalls": [[
           |    "Mailbox/changes",
           |    {
           |      "accountId": "$ANDRE_ACCOUNT_ID",
           |      "sinceState": "${oldState.getValue}"
           |    },
           |    "c1"]]
           |}""".stripMargin

      awaitAtMostTenSeconds.untilAsserted { () =>
        val response = `given`(
          baseRequestSpecBuilder(server)
            .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
            .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
            .setBody(request)
            .build, new ResponseSpecBuilder().build)
          .post
          .`then`
            .statusCode(SC_OK)
            .contentType(JSON)
            .extract
            .body
            .asString

        assertThatJson(response)
          .whenIgnoringPaths("methodResponses[0][1].newState")
          .withOptions(new Options(IGNORING_ARRAY_ORDER))
          .isEqualTo(
            s"""{
               |    "sessionState": "${SESSION_STATE.value}",
               |    "methodResponses": [
               |      [ "Mailbox/changes", {
               |        "accountId": "$ANDRE_ACCOUNT_ID",
               |        "oldState": "${oldState.getValue}",
               |        "hasMoreChanges": false,
               |        "updatedProperties": ["totalEmails", "unreadEmails", "totalThreads", "unreadThreads"],
               |        "created": [],
               |        "updated": ["$mailboxId"],
               |        "destroyed": []
               |      }, "c1"]
               |    ]
               |}""".stripMargin)
      }
    }

    @Test
    def mailboxChangesShouldReturnUpdatedChangesWhenSubscribedOnlyPerUser(server: GuiceJamesServer): Unit = {
      val accountId: AccountId = AccountId.fromUsername(BOB)
      val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
      val provisioningState: State = provisionSystemMailboxes(server)

      val path = MailboxPath.forUser(BOB, "mailbox1")
      val mailboxId: String = mailboxProbe
        .createMailbox(path)
        .serialize

      server.getProbe(classOf[ACLProbeImpl])
        .replaceRights(path, ANDRE.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))

      val oldStateBob: State = waitForNextState(server, accountId, provisioningState)
      val oldStateAndre: State = waitForNextStateWithDelegation(server, AccountId.fromUsername(ANDRE), State.INITIAL)

      // change subscription
      JmapRequests.subscribe(mailboxId)

      awaitAtMostTenSeconds.untilAsserted { () =>
        val response = `given`
          .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
          .body(
            s"""{
               |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
               |  "methodCalls": [[
               |    "Mailbox/changes",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "sinceState": "${oldStateBob.getValue}"
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
          .whenIgnoringPaths("methodResponses[0][1].newState")
          .withOptions(new Options(IGNORING_ARRAY_ORDER))
          .isEqualTo(
            s"""{
               |    "sessionState": "${SESSION_STATE.value}",
               |    "methodResponses": [
               |      [ "Mailbox/changes", {
               |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |        "oldState": "${oldStateBob.getValue}",
               |        "hasMoreChanges": false,
               |        "updatedProperties": null,
               |        "created": [],
               |        "updated": ["$mailboxId"],
               |        "destroyed": []
               |      }, "c1"]
               |    ]
               |}""".stripMargin)
      }

      val request =
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
           |  "methodCalls": [[
           |    "Mailbox/changes",
           |    {
           |      "accountId": "$ANDRE_ACCOUNT_ID",
           |      "sinceState": "${oldStateAndre.getValue}"
           |    },
           |    "c1"]]
           |}""".stripMargin

      Thread.sleep(1000)

      val responseAndre = `given`(
        baseRequestSpecBuilder(server)
          .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
          .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
          .setBody(request)
          .build, new ResponseSpecBuilder().build)
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(responseAndre)
        .whenIgnoringPaths("methodResponses[0][1].newState")
        .withOptions(new Options(IGNORING_ARRAY_ORDER))
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |      [ "Mailbox/changes", {
             |        "accountId": "$ANDRE_ACCOUNT_ID",
             |        "oldState": "${oldStateAndre.getValue}",
             |        "hasMoreChanges": false,
             |        "updatedProperties": null,
             |        "created": [],
             |        "updated": [],
             |        "destroyed": []
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
    }

    @Test
    def mailboxChangesShouldReturnUpdatedChangesWhenUnsubscribedOnlyPerUser(server: GuiceJamesServer): Unit = {
      val accountId: AccountId = AccountId.fromUsername(BOB)
      val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
      val provisioningState: State = provisionSystemMailboxes(server)

      val path = MailboxPath.forUser(BOB, "mailbox1")
      val mailboxId: String = mailboxProbe
        .createMailbox(path)
        .serialize

      server.getProbe(classOf[ACLProbeImpl])
        .replaceRights(path, ANDRE.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))

      val oldStateBob: State = waitForNextState(server, accountId, provisioningState)
      val oldStateAndre: State = waitForNextStateWithDelegation(server, AccountId.fromUsername(ANDRE), State.INITIAL)

      // change subscription
      JmapRequests.unSubscribe(mailboxId)

      awaitAtMostTenSeconds.untilAsserted { () =>
        val response = `given`
          .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
          .body(
            s"""{
               |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
               |  "methodCalls": [[
               |    "Mailbox/changes",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "sinceState": "${oldStateBob.getValue}"
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
          .whenIgnoringPaths("methodResponses[0][1].newState")
          .withOptions(new Options(IGNORING_ARRAY_ORDER))
          .isEqualTo(
            s"""{
               |    "sessionState": "${SESSION_STATE.value}",
               |    "methodResponses": [
               |      [ "Mailbox/changes", {
               |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |        "oldState": "${oldStateBob.getValue}",
               |        "hasMoreChanges": false,
               |        "updatedProperties": null,
               |        "created": [],
               |        "updated": ["$mailboxId"],
               |        "destroyed": []
               |      }, "c1"]
               |    ]
               |}""".stripMargin)
      }

      val request =
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
           |  "methodCalls": [[
           |    "Mailbox/changes",
           |    {
           |      "accountId": "$ANDRE_ACCOUNT_ID",
           |      "sinceState": "${oldStateAndre.getValue}"
           |    },
           |    "c1"]]
           |}""".stripMargin

      Thread.sleep(1000)

      val responseAndre = `given`(
        baseRequestSpecBuilder(server)
          .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
          .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
          .setBody(request)
          .build, new ResponseSpecBuilder().build)
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(responseAndre)
        .whenIgnoringPaths("methodResponses[0][1].newState")
        .withOptions(new Options(IGNORING_ARRAY_ORDER))
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |      [ "Mailbox/changes", {
             |        "accountId": "$ANDRE_ACCOUNT_ID",
             |        "oldState": "${oldStateAndre.getValue}",
             |        "hasMoreChanges": false,
             |        "updatedProperties": null,
             |        "created": [],
             |        "updated": [],
             |        "destroyed": []
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
    }

    @Test
    def mailboxChangesShouldNotReturnUpdatedChangesWhenMissingSharesCapability(server: GuiceJamesServer): Unit = {
      val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

      provisionSystemMailboxes(server)

      val path = MailboxPath.forUser(BOB, "mailbox1")
      mailboxProbe.createMailbox(path)

      server.getProbe(classOf[ACLProbeImpl])
        .replaceRights(path, ANDRE.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))

      val message: Message = Message.Builder
        .of
        .setSubject("test")
        .setBody("testmail", StandardCharsets.UTF_8)
        .build
      val messageId: MessageId = mailboxProbe.appendMessage(BOB.asString(), path, AppendCommand.from(message)).getMessageId

      waitForNextStateWithDelegation(server, AccountId.fromUsername(ANDRE), State.INITIAL)

      JmapRequests.destroyEmail(messageId)

      val request =
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
           |  "methodCalls": [[
           |    "Mailbox/changes",
           |    {
           |      "accountId": "$ANDRE_ACCOUNT_ID",
           |      "sinceState": "${State.INITIAL.getValue}"
           |    },
           |    "c1"]]
           |}""".stripMargin

      awaitAtMostTenSeconds.untilAsserted { () =>
        val response = `given`(
          baseRequestSpecBuilder(server)
            .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
            .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
            .setBody(request)
            .build, new ResponseSpecBuilder().build)
          .post
          .`then`
            .statusCode(SC_OK)
            .contentType(JSON)
            .extract
            .body
            .asString

        assertThatJson(response)
          .whenIgnoringPaths("methodResponses[0][1].newState")
          .withOptions(new Options(IGNORING_ARRAY_ORDER))
          .isEqualTo(
            s"""{
               |    "sessionState": "${SESSION_STATE.value}",
               |    "methodResponses": [
               |      [ "Mailbox/changes", {
               |        "accountId": "$ANDRE_ACCOUNT_ID",
               |        "oldState": "${State.INITIAL.getValue}",
               |        "hasMoreChanges": false,
               |        "updatedProperties": null,
               |        "created": [],
               |        "updated": [],
               |        "destroyed": []
               |      }, "c1"]
               |    ]
               |}""".stripMargin)
      }
    }

    @Test
    def mailboxChangesShouldReturnDestroyedChangesWhenDestroyDelegatedMailbox(server: GuiceJamesServer): Unit = {
      val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

      provisionSystemMailboxes(server)

      val path = MailboxPath.forUser(BOB, "mailbox1")
      val mailboxId: String = mailboxProbe
        .createMailbox(path)
        .serialize

      server.getProbe(classOf[ACLProbeImpl])
        .replaceRights(path, ANDRE.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))

      val oldState: State = waitForNextStateWithDelegation(server, AccountId.fromUsername(ANDRE), State.INITIAL)

      JmapRequests.destroyMailbox(mailboxId)

      val request =
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
           |  "methodCalls": [[
           |    "Mailbox/changes",
           |    {
           |      "accountId": "$ANDRE_ACCOUNT_ID",
           |      "sinceState": "${oldState.getValue}"
           |    },
           |    "c1"]]
           |}""".stripMargin

      awaitAtMostTenSeconds.untilAsserted { () =>
        val response = `given`(
          baseRequestSpecBuilder(server)
            .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
            .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
            .setBody(request)
            .build, new ResponseSpecBuilder().build)
          .post
          .`then`
            .statusCode(SC_OK)
            .contentType(JSON)
            .extract
            .body
            .asString

        assertThatJson(response)
          .whenIgnoringPaths("methodResponses[0][1].newState")
          .withOptions(new Options(IGNORING_ARRAY_ORDER))
          .isEqualTo(
            s"""{
               |    "sessionState": "${SESSION_STATE.value}",
               |    "methodResponses": [
               |      [ "Mailbox/changes", {
               |        "accountId": "$ANDRE_ACCOUNT_ID",
               |        "oldState": "${oldState.getValue}",
               |        "hasMoreChanges": false,
               |        "updatedProperties": null,
               |        "created": [],
               |        "updated": [],
               |        "destroyed": ["$mailboxId"]
               |      }, "c1"]
               |    ]
               |}""".stripMargin)
      }
    }
  }

  @Test
  def mailboxChangesShouldReturnDestroyedChanges(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    val accountId: AccountId = AccountId.fromUsername(BOB)

    val provisioningState: State = provisionSystemMailboxes(server)

    val path = MailboxPath.forUser(BOB, "mailbox1")
    val mailboxId: String = mailboxProbe
      .createMailbox(path)
      .serialize

    val oldState: State = waitForNextState(server, accountId, provisioningState)

    mailboxProbe.deleteMailbox(path.getNamespace, BOB.asString(), path.getName)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/changes",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sinceState": "${oldState.getValue}"
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
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
        .whenIgnoringPaths("methodResponses[0][1].newState")
        .withOptions(new Options(IGNORING_ARRAY_ORDER))
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |      [ "Mailbox/changes", {
             |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |        "oldState": "${oldState.getValue}",
             |        "hasMoreChanges": false,
             |        "updatedProperties":null,
             |        "created": [],
             |        "updated": [],
             |        "destroyed": ["$mailboxId"]
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
    }
  }

  @Test
  def mailboxChangesShouldReturnDestroyedChangeWhenOwnerRevokingMailboxShareeRights(server: GuiceJamesServer): Unit = {
    val accountId: AccountId = AccountId.fromUsername(BOB)
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    val provisioningState: State = provisionSystemMailboxes(server)

    val path = MailboxPath.forUser(BOB, "mailbox1")
    val mailboxId: String = mailboxProbe
      .createMailbox(path)
      .serialize

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, ANDRE.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))

    waitForNextState(server, accountId, provisioningState)
    val oldStateAndre: State = waitForNextStateWithDelegation(server, AccountId.fromUsername(ANDRE), State.INITIAL)

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, ANDRE.asString, new MailboxACL.Rfc4314Rights())

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [[
         |    "Mailbox/changes",
         |    {
         |      "accountId": "$ANDRE_ACCOUNT_ID",
         |      "sinceState": "${oldStateAndre.getValue}"
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`(
        baseRequestSpecBuilder(server)
          .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
          .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
          .setBody(request)
          .build, new ResponseSpecBuilder().build)
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(response)
        .whenIgnoringPaths("methodResponses[0][1].newState")
        .withOptions(new Options(IGNORING_ARRAY_ORDER))
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |      [ "Mailbox/changes", {
             |        "accountId": "$ANDRE_ACCOUNT_ID",
             |        "oldState": "${oldStateAndre.getValue}",
             |        "hasMoreChanges": false,
             |        "updatedProperties": null,
             |        "created": [],
             |        "updated": [],
             |        "destroyed": ["$mailboxId"]
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
    }
  }

  @Test
  def returnedIdsShouldNotReturnDuplicatesAccrossCreatedUpdatedOrDestroyed(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    val provisioningState: State = provisionSystemMailboxes(server)

    val path1 = MailboxPath.forUser(BOB, "mailbox1")
    mailboxProbe
      .createMailbox(path1)
      .serialize

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    mailboxProbe.appendMessage(BOB.asString(), path1, AppendCommand.from(message))

    val path2 = MailboxPath.forUser(BOB, "mailbox2")
    val mailboxId2: String = mailboxProbe
      .createMailbox(path2)
      .serialize
    JmapRequests.renameMailbox(mailboxId2, "mailbox22")

    mailboxProbe.deleteMailbox(path1.getNamespace, BOB.asString(), path1.getName)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/changes",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sinceState": "${provisioningState.getValue}"
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
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
        .whenIgnoringPaths("methodResponses[0][1].newState")
        .withOptions(new Options(IGNORING_ARRAY_ORDER))
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |      [ "Mailbox/changes", {
             |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |        "oldState": "${provisioningState.getValue}",
             |        "hasMoreChanges": false,
             |        "updatedProperties":null,
             |        "created": ["$mailboxId2"],
             |        "updated": [],
             |        "destroyed": []
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
    }
  }

  @Test
  def mailboxChangesShouldReturnAllTypeOfChanges(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    val accountId: AccountId = AccountId.fromUsername(BOB)

    val provisioningState: State = provisionSystemMailboxes(server)

    val path1 = MailboxPath.forUser(BOB, "mailbox1")
    val mailboxId1: String = mailboxProbe
      .createMailbox(path1)
      .serialize

    val state1: State = waitForNextState(server, accountId, provisioningState)

    val path2 = MailboxPath.forUser(BOB, "mailbox2")
    val mailboxId2: String = mailboxProbe
      .createMailbox(path2)
      .serialize

    val oldState: State = waitForNextState(server, accountId, state1)

    val path3 = MailboxPath.forUser(BOB, "mailbox3")
    val mailboxId3: String = mailboxProbe
      .createMailbox(path3)
      .serialize

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    mailboxProbe.appendMessage(BOB.asString(), path1, AppendCommand.from(message))

    mailboxProbe.deleteMailbox(path2.getNamespace, BOB.asString(), path2.getName)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/changes",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sinceState": "${oldState.getValue}"
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
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
        .whenIgnoringPaths("methodResponses[0][1].newState")
        .withOptions(new Options(IGNORING_ARRAY_ORDER))
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |      [ "Mailbox/changes", {
             |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |        "oldState": "${oldState.getValue}",
             |        "hasMoreChanges": false,
             |        "updatedProperties": null,
             |        "created": ["$mailboxId3"],
             |        "updated": ["$mailboxId1"],
             |        "destroyed": ["$mailboxId2"]
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
    }
  }

  @Test
  def mailboxChangesShouldReturnHasMoreChangesWhenTrue(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    val provisioningState: State = provisionSystemMailboxes(server)

    val mailboxId1: String = mailboxProbe
      .createMailbox(MailboxPath.forUser(BOB, "mailbox1"))
      .serialize

    val mailboxId2: String = mailboxProbe
      .createMailbox(MailboxPath.forUser(BOB, "mailbox2"))
      .serialize

    val mailboxId3: String = mailboxProbe
      .createMailbox(MailboxPath.forUser(BOB, "mailbox3"))
      .serialize

    val mailboxId4: String = mailboxProbe
      .createMailbox(MailboxPath.forUser(BOB, "mailbox4"))
      .serialize

    val mailboxId5: String = mailboxProbe
      .createMailbox(MailboxPath.forUser(BOB, "mailbox5"))
      .serialize

    mailboxProbe
      .createMailbox(MailboxPath.forUser(BOB, "mailbox6"))
      .serialize

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/changes",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sinceState": "${provisioningState.getValue}"
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
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
        .whenIgnoringPaths("methodResponses[0][1].newState")
        .withOptions(new Options(IGNORING_ARRAY_ORDER))
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |      [ "Mailbox/changes", {
             |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |        "oldState": "${provisioningState.getValue}",
             |        "hasMoreChanges": true,
             |        "updatedProperties": null,
             |        "created": ["$mailboxId1", "$mailboxId2", "$mailboxId3", "$mailboxId4", "$mailboxId5"],
             |        "updated": [],
             |        "destroyed": []
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
    }
  }

  @Test
  def maxChangesShouldBeTakenIntoAccount(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    val provisioningState: State = provisionSystemMailboxes(server)

    val mailboxId1: String = mailboxProbe
      .createMailbox(MailboxPath.forUser(BOB, "mailbox1"))
      .serialize

    val mailboxId2: String = mailboxProbe
      .createMailbox(MailboxPath.forUser(BOB, "mailbox2"))
      .serialize

    val mailboxId3: String = mailboxProbe
      .createMailbox(MailboxPath.forUser(BOB, "mailbox3"))
      .serialize

    val mailboxId4: String = mailboxProbe
      .createMailbox(MailboxPath.forUser(BOB, "mailbox4"))
      .serialize

    val mailboxId5: String = mailboxProbe
      .createMailbox(MailboxPath.forUser(BOB, "mailbox5"))
      .serialize

    val mailboxId6: String = mailboxProbe
      .createMailbox(MailboxPath.forUser(BOB, "mailbox6"))
      .serialize

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/changes",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sinceState": "${provisioningState.getValue}",
         |      "maxChanges": 38
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
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
        .whenIgnoringPaths("methodResponses[0][1].newState")
        .withOptions(new Options(IGNORING_ARRAY_ORDER))
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |      [ "Mailbox/changes", {
             |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |        "oldState": "${provisioningState.getValue}",
             |        "hasMoreChanges": false,
             |        "updatedProperties": null,
             |        "created": ["$mailboxId1", "$mailboxId2", "$mailboxId3", "$mailboxId4", "$mailboxId5", "$mailboxId6"],
             |        "updated": [],
             |        "destroyed": []
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
    }
  }

  @Test
  def mailboxChangesShouldFailWhenAccountIdNotFound(server: GuiceJamesServer): Unit = {
    val jmapGuiceProbe:JmapGuiceProbe = server.getProbe(classOf[JmapGuiceProbe])
    val oldState: State = jmapGuiceProbe.getLatestMailboxState(AccountId.fromUsername(BOB))

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/changes",
         |    {
         |      "accountId": "bad",
         |      "sinceState": "${oldState.getValue}"
         |    },
         |    "c1"]]
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
          |   "sessionState": "${SESSION_STATE.value}",
          |   "methodResponses": [[
          |     "error", {
          |       "type": "accountNotFound"
          |     }, "c1"]
          |   ]
          |}""".stripMargin)
  }

  @Test
  def mailboxChangesShouldFailWhenStateNotFound(server: GuiceJamesServer): Unit = {
    provisionSystemMailboxes(server)

    val state: String = stateFactory.generate().getValue.toString

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/changes",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sinceState": "$state"
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
      .whenIgnoringPaths("methodResponses[0][1].newState")
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .isEqualTo(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [[
           |    "error", {
           |      "type": "cannotCalculateChanges",
           |      "description": "State '$state' could not be found"
           |    }, "c1"]
           |  ]
           |}""".stripMargin)
  }

  @Test
  def mailboxChangesShouldReturnNoChangesWhenNoNewerState(server: GuiceJamesServer): Unit = {
    val provisioningState: State = provisionSystemMailboxes(server)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/changes",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sinceState": "${provisioningState.getValue}"
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
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
        .withOptions(new Options(IGNORING_ARRAY_ORDER))
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |      [ "Mailbox/changes", {
             |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |        "oldState": "${provisioningState.getValue}",
             |        "newState": "${provisioningState.getValue}",
             |        "hasMoreChanges": false,
             |        "updatedProperties": null,
             |        "created": [],
             |        "updated": [],
             |        "destroyed": []
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
    }
  }

  @Test
  def mailboxChangesShouldReturnDifferentStateThanOldState(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    val provisioningState: State = provisionSystemMailboxes(server)

    mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "mailbox1"))
    mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "mailbox2"))

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/changes",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sinceState": "${provisioningState.getValue}"
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
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

      val newState = Json.parse(response)
        .\("methodResponses")
        .\(0).\(1)
        .\("newState")
        .get.asInstanceOf[JsString].value

      assertThat(provisioningState.getValue.toString).isNotEqualTo(newState)
    }
  }

  @Test
  def mailboxChangesShouldEventuallyReturnNoChanges(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    val provisioningState: State = provisionSystemMailboxes(server)

    mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "mailbox1"))

    val request1 =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/changes",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sinceState": "${provisioningState.getValue}"
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response1 = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request1)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    val newState = Json.parse(response1)
      .\("methodResponses")
      .\(0).\(1)
      .\("newState")
      .get.asInstanceOf[JsString].value

    val request2 =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/changes",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sinceState": "$newState"
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response2 = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request2)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response2)
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      [ "Mailbox/changes", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "oldState": "$newState",
           |        "newState": "$newState",
           |        "hasMoreChanges": false,
           |        "updatedProperties":null,
           |        "created": [],
           |        "updated": [],
           |        "destroyed": []
           |      }, "c1"]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def mailboxChangesShouldReturnUpdatedPropertiesWhenOnlyCountChanges(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    val accountId: AccountId = AccountId.fromUsername(BOB)

    val provisioningState: State = provisionSystemMailboxes(server)

    val path = MailboxPath.forUser(BOB, "mailbox1")
    val mailboxId: String = mailboxProbe
      .createMailbox(path)
      .serialize

    val oldState: State = waitForNextState(server, accountId, provisioningState)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    mailboxProbe.appendMessage(BOB.asString(), path, AppendCommand.from(message)).getMessageId
    val messageId2: MessageId = mailboxProbe.appendMessage(BOB.asString(), path, AppendCommand.from(message)).getMessageId
    val messageId3: MessageId = mailboxProbe.appendMessage(BOB.asString(), path, AppendCommand.from(message)).getMessageId

    JmapRequests.destroyEmail(messageId2)
    JmapRequests.markEmailAsSeen(messageId3)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/changes",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sinceState": "${oldState.getValue}"
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
      .whenIgnoringPaths("methodResponses[0][1].newState")
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      [ "Mailbox/changes", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "oldState": "${oldState.getValue}",
           |        "hasMoreChanges": false,
           |        "updatedProperties": ["totalEmails", "unreadEmails", "totalThreads", "unreadThreads"],
           |        "created": [],
           |        "updated": ["$mailboxId"],
           |        "destroyed": []
           |      }, "c1"]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def mailboxChangesShouldNotReturnUpdatedPropertiesWhenMixedChanges(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    val accountId: AccountId = AccountId.fromUsername(BOB)

    val provisioningState: State = provisionSystemMailboxes(server)

    val path = MailboxPath.forUser(BOB, "mailbox1")
    val mailboxId1: String = mailboxProbe
      .createMailbox(path)
      .serialize

    val oldState: State = waitForNextState(server, accountId, provisioningState)

    val path2 = MailboxPath.forUser(BOB, "mailbox2")
    val mailboxId2: String = mailboxProbe
      .createMailbox(path2)
      .serialize

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    mailboxProbe.appendMessage(BOB.asString(), path, AppendCommand.from(message))

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/changes",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sinceState": "${oldState.getValue}"
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
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
        .whenIgnoringPaths("methodResponses[0][1].newState")
        .withOptions(new Options(IGNORING_ARRAY_ORDER))
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |      [ "Mailbox/changes", {
             |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |        "oldState": "${oldState.getValue}",
             |        "hasMoreChanges": false,
             |        "updatedProperties":null,
             |        "created": ["$mailboxId2"],
             |        "updated": ["$mailboxId1"],
             |        "destroyed": []
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
    }
  }

  @Test
  def mailboxChangesShouldSupportBackReferenceWithUpdatedProperties(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    val accountId: AccountId = AccountId.fromUsername(BOB)

    val provisioningState: State = provisionSystemMailboxes(server)

    val path = MailboxPath.forUser(BOB, "mailbox1")
    val mailboxId: String = mailboxProbe
      .createMailbox(path)
      .serialize

    val oldState: State = waitForNextState(server, accountId, provisioningState)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    mailboxProbe.appendMessage(BOB.asString(), path, AppendCommand.from(message)).getMessageId

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Mailbox/changes", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sinceState": "${oldState.getValue}"
         |    }, "c1"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "#properties": {
         |        "resultOf": "c1",
         |        "name": "Mailbox/changes",
         |        "path": "updatedProperties"
         |      },
         |      "#ids": {
         |        "resultOf": "c1",
         |        "name": "Mailbox/changes",
         |        "path": "/updated"
         |      }
         |    }, "c2"]
         |  ]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
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
        .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[1][1].state")
        .withOptions(new Options(IGNORING_ARRAY_ORDER))
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |      [ "Mailbox/changes", {
             |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |        "oldState": "${oldState.getValue}",
             |        "hasMoreChanges": false,
             |        "updatedProperties": ["totalEmails", "unreadEmails", "totalThreads", "unreadThreads"],
             |        "created": [],
             |        "updated": ["$mailboxId"],
             |        "destroyed": []
             |      }, "c1"],
             |      ["Mailbox/get", {
             |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |        "notFound": [],
             |        "list": [
             |          {
             |            "id": "$mailboxId",
             |            "totalEmails": 1,
             |            "unreadEmails": 1,
             |            "totalThreads": 1,
             |            "unreadThreads": 1
             |          }
             |        ]
             |      }, "c2"]
             |    ]
             |}""".stripMargin)
    }
  }

  @Test
  def mailboxChangesShouldSupportBackReferenceWithNullUpdatedProperties(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    val accountId: AccountId = AccountId.fromUsername(BOB)

    val provisioningState: State = provisionSystemMailboxes(server)

    val path1 = MailboxPath.forUser(BOB, "mailbox1")
    val mailboxId1: String = mailboxProbe
      .createMailbox(path1)
      .serialize

    val oldState: State = waitForNextState(server, accountId, provisioningState)

    val path2 = MailboxPath.forUser(BOB, "mailbox2")
    val mailboxId2: String = mailboxProbe
      .createMailbox(path2)
      .serialize

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    mailboxProbe.appendMessage(BOB.asString(), path1, AppendCommand.from(message)).getMessageId

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Mailbox/changes", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sinceState": "${oldState.getValue}"
         |    }, "c1"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "#properties": {
         |        "resultOf": "c1",
         |        "name": "Mailbox/changes",
         |        "path": "updatedProperties"
         |      },
         |      "#ids": {
         |        "resultOf": "c1",
         |        "name": "Mailbox/changes",
         |        "path": "/updated"
         |      }
         |    }, "c2"]
         |  ]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
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
        .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[1][1].state")
        .withOptions(new Options(IGNORING_ARRAY_ORDER))
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |      [ "Mailbox/changes", {
             |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |        "oldState": "${oldState.getValue}",
             |        "hasMoreChanges": false,
             |        "updatedProperties": null,
             |        "created": ["$mailboxId2"],
             |        "updated": ["$mailboxId1"],
             |        "destroyed": []
             |      }, "c1"],
             |      ["Mailbox/get", {
             |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |        "notFound": [],
             |        "list": [
             |          {
             |            "id": "$mailboxId1",
             |            "name": "mailbox1",
             |            "sortOrder": 1000,
             |            "totalEmails": 1,
             |            "unreadEmails": 1,
             |            "totalThreads": 1,
             |            "unreadThreads": 1,
             |            "myRights": {
             |              "mayReadItems": true,
             |              "mayAddItems": true,
             |              "mayRemoveItems": true,
             |              "maySetSeen": true,
             |              "maySetKeywords": true,
             |              "mayCreateChild": true,
             |              "mayRename": true,
             |              "mayDelete": true,
             |              "maySubmit": true
             |            },
             |            "isSubscribed": false
             |          }
             |        ]
             |      }, "c2"]
             |    ]
             |}""".stripMargin)
    }
  }

  private def waitForNextState(server: GuiceJamesServer, accountId: AccountId, initialState: State): State = {
    val jmapGuiceProbe: JmapGuiceProbe = server.getProbe(classOf[JmapGuiceProbe])
    awaitAtMostTenSeconds.untilAsserted {
      () => assertThat(jmapGuiceProbe.getLatestMailboxState(accountId)).isNotEqualTo(initialState)
    }

    jmapGuiceProbe.getLatestMailboxState(accountId)
  }

  private def waitForNextStateWithDelegation(server: GuiceJamesServer, accountId: AccountId, initialState: State): State = {
    val jmapGuiceProbe: JmapGuiceProbe = server.getProbe(classOf[JmapGuiceProbe])
    awaitAtMostTenSeconds.untilAsserted{ () => assertThat(jmapGuiceProbe.getLatestMailboxStateWithDelegation(accountId)).isNotEqualTo(initialState) }

    jmapGuiceProbe.getLatestMailboxStateWithDelegation(accountId)
  }

  private def provisionSystemMailboxes(server: GuiceJamesServer): State = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val jmapGuiceProbe: JmapGuiceProbe = server.getProbe(classOf[JmapGuiceProbe])

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
         |    },
         |    "c1"]]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)

    //Wait until all the system mailboxes are created
    val request2 =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/changes",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sinceState": "${State.INITIAL.getValue.toString}"
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response1 = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(request2)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      val createdSize = Json.parse(response1)
        .\("methodResponses")
        .\(0).\(1)
        .\("created")
        .get.asInstanceOf[JsArray].value.size

      assertThat(createdSize).isEqualTo(5)
    }

    jmapGuiceProbe.getLatestMailboxState(AccountId.fromUsername(BOB))
  }
}
