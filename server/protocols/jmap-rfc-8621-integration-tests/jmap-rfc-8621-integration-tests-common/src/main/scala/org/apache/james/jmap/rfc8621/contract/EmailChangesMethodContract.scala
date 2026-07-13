/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
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
import java.time.Duration
import java.util.UUID
import java.util.concurrent.{TimeUnit, atomic}

import com.google.common.hash.Hashing
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, `with`, requestSpecification}
import io.restassured.builder.ResponseSpecBuilder
import io.restassured.http.ContentType.JSON
import jakarta.mail.Flags
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.JmapGuiceProbe
import org.apache.james.jmap.api.change.State
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE_PASSWORD, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.MailboxACL.Right
import org.apache.james.mailbox.model.{MailboxACL, MailboxPath, MessageId}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.{ACLProbeImpl, MailboxProbeImpl}
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.junit.jupiter.api.{BeforeEach, Nested, Test}
import play.api.libs.json.{JsString, Json}

object EmailChangesMethodContract {
  case class TestContext(bobUsername: Username, bobAccountId: String, andreUsername: Username, andreAccountId: String)

  val currentContext: atomic.AtomicReference[TestContext] = new atomic.AtomicReference[TestContext]()
}

trait EmailChangesMethodContract {
  import EmailChangesMethodContract.{TestContext, currentContext}

  def bobUsername: Username = currentContext.get().bobUsername
  def bobAccountId: String = currentContext.get().bobAccountId
  def andreUsername: Username = currentContext.get().andreUsername
  def andreAccountId: String = currentContext.get().andreAccountId

  private def accountId(username: Username): String =
    Hashing.sha256().hashString(username.asString(), StandardCharsets.UTF_8).toString

  private lazy val slowPacedPollInterval = Duration.ofMillis(100)
  private lazy val calmlyAwait = Awaitility.`with`
    .pollInterval(slowPacedPollInterval)
    .and.`with`.pollDelay(slowPacedPollInterval)
    .await
  private lazy val awaitAtMostTenSeconds = calmlyAwait.atMost(10, TimeUnit.SECONDS)

  def stateFactory: State.Factory

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    val uniqueSuffix = UUID.randomUUID().toString.replace("-", "").take(8)
    val bob = Username.fromLocalPartWithDomain(s"bob$uniqueSuffix", DOMAIN)
    val andre = Username.fromLocalPartWithDomain(s"andre$uniqueSuffix", DOMAIN)
    currentContext.set(TestContext(
      bobUsername = bob,
      bobAccountId = accountId(bob),
      andreUsername = andre,
      andreAccountId = accountId(andre)))

    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addDomain("domain-alias.tld")
      .addUser(bob.asString, BOB_PASSWORD)
      .addUser(andre.asString, ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(bob, BOB_PASSWORD)))
      .build
  }

  @Test
  def emailChangesShouldReturnCreatedChanges(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    val path: MailboxPath = MailboxPath.forUser(bobUsername, "mailbox1")

    mailboxProbe.createMailbox(path)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId: MessageId = mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message)).getMessageId

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/changes",
         |    {
         |      "accountId": "$bobAccountId",
         |      "sinceState": "${State.INITIAL.getValue}"
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
        .withOptions(IGNORING_ARRAY_ORDER)
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |      [ "Email/changes", {
             |        "accountId": "$bobAccountId",
             |        "oldState": "${State.INITIAL.getValue}",
             |        "hasMoreChanges": false,
             |        "created": ["${messageId.serialize}"],
             |        "updated": [],
             |        "destroyed": []
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
    }
  }

  @Test
  def emailChangesShouldReturnUpdatedChangesWhenAddFlags(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    val path: MailboxPath = MailboxPath.forUser(bobUsername, "mailbox1")

    mailboxProbe.createMailbox(path)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId: MessageId = mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message)).getMessageId

    val oldState: State = waitForNextState(server, AccountId.fromUsername(bobUsername), State.INITIAL)

    JmapRequests.markEmailAsSeen(messageId, bobUsername)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/changes",
         |    {
         |      "accountId": "$bobAccountId",
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
        .withOptions(IGNORING_ARRAY_ORDER)
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |      [ "Email/changes", {
             |        "accountId": "$bobAccountId",
             |        "oldState": "${oldState.getValue}",
             |        "hasMoreChanges": false,
             |        "created": [],
             |        "updated": ["${messageId.serialize()}"],
             |        "destroyed": []
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
    }
  }

  @Test
  def shouldFailWithCannotCalculateChangesWhenSingleChangeIsTooLarge(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    val path: MailboxPath = MailboxPath.forUser(bobUsername, "mailbox1")

    mailboxProbe.createMailbox(path)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId1: MessageId = mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message)).getMessageId
    waitForNextState(server, AccountId.fromUsername(bobUsername), State.INITIAL)
    val messageId2: MessageId = mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message)).getMessageId
    waitForNextState(server, AccountId.fromUsername(bobUsername), State.INITIAL)
    val messageId3: MessageId = mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message)).getMessageId
    waitForNextState(server, AccountId.fromUsername(bobUsername), State.INITIAL)
    val messageId4: MessageId = mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message)).getMessageId
    waitForNextState(server, AccountId.fromUsername(bobUsername), State.INITIAL)
    val messageId5: MessageId = mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message)).getMessageId
    waitForNextState(server, AccountId.fromUsername(bobUsername), State.INITIAL)
    val messageId6: MessageId = mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message)).getMessageId
    val state6: State = waitForNextState(server, AccountId.fromUsername(bobUsername), State.INITIAL)

    val updateEmail =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |  ["Email/set",
         |    {
         |      "accountId": "$bobAccountId",
         |      "update": {
         |        "${messageId1.serialize}":{
         |          "keywords/$$flagged": true
         |        },
         |        "${messageId2.serialize}":{
         |          "keywords/$$flagged": true
         |        },
         |        "${messageId3.serialize}":{
         |          "keywords/$$flagged": true
         |        },
         |        "${messageId4.serialize}":{
         |          "keywords/$$flagged": true
         |        },
         |        "${messageId5.serialize}":{
         |          "keywords/$$flagged": true
         |        },
         |        "${messageId6.serialize}":{
         |          "keywords/$$flagged": true
         |        }
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin

    `with`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(updateEmail)
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/changes",
         |    {
         |      "accountId": "$bobAccountId",
         |      "sinceState": "${state6.getValue}"
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
        .inPath("methodResponses[0]")
        .isEqualTo(
          s"""[
             |  "error",
             |  {
             |    "type": "cannotCalculateChanges",
             |    "description": "Current change collector limit 5 is exceeded by a single change, hence we cannot calculate changes."
             |  },
             |  "c1"
             |]""".stripMargin)
    }
  }



  @Test
  def shouldReturnUpdatedWhenMessageMove(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    val path: MailboxPath = MailboxPath.forUser(bobUsername, "mailbox1")

    mailboxProbe.createMailbox(path)
    val mailboxId2 = mailboxProbe.createMailbox(MailboxPath.forUser(bobUsername, "mailbox2"))

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId: MessageId = mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message)).getMessageId

    val oldState: State = waitForNextState(server, AccountId.fromUsername(bobUsername), State.INITIAL)

    val updateEmail =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |  ["Email/set",
         |    {
         |      "accountId": "$bobAccountId",
         |      "update": {
         |        "${messageId.serialize}":{
         |          "mailboxIds": {
         |            "${mailboxId2.serialize()}": true
         |          }
         |        }
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin

    `with`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(updateEmail)
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/changes",
         |    {
         |      "accountId": "$bobAccountId",
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
        .inPath("methodResponses[0][1]")
        .isEqualTo(
          s"""{
             |  "accountId": "$bobAccountId",
             |  "oldState": "${oldState.getValue}",
             |  "hasMoreChanges": false,
             |  "created": [],
             |  "updated": ["${messageId.serialize()}"],
             |  "destroyed": []
             |}""".stripMargin)
    }
  }

  @Test
  def emailChangesShouldReturnUpdatedChangesWhenRemoveFlags(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    val path: MailboxPath = MailboxPath.forUser(bobUsername, "mailbox1")

    mailboxProbe.createMailbox(path)

    val messageId: MessageId = mailboxProbe.appendMessage(bobUsername.asString(), path,
      AppendCommand.builder()
        .withFlags(new Flags(Flags.Flag.SEEN))
        .build("header: value\r\n\r\nbody"))
      .getMessageId

    val oldState: State = waitForNextState(server, AccountId.fromUsername(bobUsername), State.INITIAL)

    JmapRequests.markEmailAsNotSeen(messageId, bobUsername)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/changes",
         |    {
         |      "accountId": "$bobAccountId",
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
        .withOptions(IGNORING_ARRAY_ORDER)
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |      [ "Email/changes", {
             |        "accountId": "$bobAccountId",
             |        "oldState": "${oldState.getValue}",
             |        "hasMoreChanges": false,
             |        "created": [],
             |        "updated": ["${messageId.serialize()}"],
             |        "destroyed": []
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
    }
  }

  @Test
  def emailChangesShouldReturnDestroyedChanges(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    val path: MailboxPath = MailboxPath.forUser(bobUsername, "mailbox1")

    mailboxProbe.createMailbox(path)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId: MessageId = mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message)).getMessageId

    val oldState: State = waitForNextState(server, AccountId.fromUsername(bobUsername), State.INITIAL)

    JmapRequests.destroyEmail(messageId, bobUsername)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/changes",
         |    {
         |      "accountId": "$bobAccountId",
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
        .withOptions(IGNORING_ARRAY_ORDER)
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |      [ "Email/changes", {
             |        "accountId": "$bobAccountId",
             |        "oldState": "${oldState.getValue}",
             |        "hasMoreChanges": false,
             |        "created": [],
             |        "updated": [],
             |        "destroyed": ["${messageId.serialize()}"]
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
    }
  }

  @Test
  def emailChangesShouldReturnAllTypeOfChanges(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    val accountId: AccountId = AccountId.fromUsername(bobUsername)

    val path1: MailboxPath = MailboxPath.forUser(bobUsername, "mailbox1")
    mailboxProbe.createMailbox(path1)

    val path2: MailboxPath = MailboxPath.forUser(bobUsername, "mailbox2")
    mailboxProbe.createMailbox(path2)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val messageId1: MessageId = mailboxProbe.appendMessage(bobUsername.asString(), path2, AppendCommand.from(message)).getMessageId

    val state1: State = waitForNextState(server, accountId, State.INITIAL)

    val messageId2: MessageId = mailboxProbe.appendMessage(bobUsername.asString(), path2, AppendCommand.from(message)).getMessageId

    val oldState: State = waitForNextState(server, accountId, state1)

    val messageId3: MessageId = mailboxProbe.appendMessage(bobUsername.asString(), path1, AppendCommand.from(message)).getMessageId

    JmapRequests.markEmailAsSeen(messageId1, bobUsername)

    JmapRequests.destroyEmail(messageId2, bobUsername)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/changes",
         |    {
         |      "accountId": "$bobAccountId",
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
        .withOptions(IGNORING_ARRAY_ORDER)
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |      [ "Email/changes", {
             |        "accountId": "$bobAccountId",
             |        "oldState": "${oldState.getValue}",
             |        "hasMoreChanges": false,
             |        "created": ["${messageId3.serialize}"],
             |        "updated": ["${messageId1.serialize}"],
             |        "destroyed": ["${messageId2.serialize}"]
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
    }
  }

  @Test
  def emailChangesShouldNotReturnDuplicatedIdsAccrossCreatedUpdatedOrDestroyed(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    val path = MailboxPath.forUser(bobUsername, "mailbox1")
    mailboxProbe.createMailbox(path)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId1: MessageId = mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message)).getMessageId
    val messageId2: MessageId = mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message)).getMessageId
    val messageId3: MessageId = mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message)).getMessageId

    JmapRequests.markEmailAsSeen(messageId2, bobUsername)

    JmapRequests.destroyEmail(messageId3, bobUsername)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/changes",
         |    {
         |      "accountId": "$bobAccountId",
         |      "sinceState": "${State.INITIAL.getValue}"
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
        .withOptions(IGNORING_ARRAY_ORDER)
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |      [ "Email/changes", {
             |        "accountId": "$bobAccountId",
             |        "oldState": "${State.INITIAL.getValue}",
             |        "hasMoreChanges": false,
             |        "created": ["${messageId1.serialize}", "${messageId2.serialize}"],
             |        "updated": ["${messageId2.serialize}"],
             |        "destroyed": []
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
    }
  }

  @Nested
  class DelegationTest {
    @Test
    def emailChangesShouldReturnCreatedChanges(server: GuiceJamesServer): Unit = {
      val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
      val path: MailboxPath = MailboxPath.forUser(bobUsername, "mailbox1")

      mailboxProbe.createMailbox(path)

      server.getProbe(classOf[ACLProbeImpl])
        .replaceRights(path, andreUsername.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))

      val message: Message = Message.Builder
        .of
        .setSubject("test")
        .setBody("testmail", StandardCharsets.UTF_8)
        .build
      val messageId: MessageId = mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message)).getMessageId

      val request =
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
           |  "methodCalls": [[
           |    "Email/changes",
           |    {
           |      "accountId": "$andreAccountId",
           |      "sinceState": "${State.INITIAL.getValue}"
           |    },
           |    "c1"]]
           |}""".stripMargin

      awaitAtMostTenSeconds.untilAsserted { () =>
        val response = `given`(
          baseRequestSpecBuilder(server)
            .setAuth(authScheme(UserCredential(andreUsername, ANDRE_PASSWORD)))
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
          .withOptions(IGNORING_ARRAY_ORDER)
          .isEqualTo(
            s"""{
               |    "sessionState": "${SESSION_STATE.value}",
               |    "methodResponses": [
               |      [ "Email/changes", {
               |        "accountId": "$andreAccountId",
               |        "oldState": "${State.INITIAL.getValue}",
               |        "hasMoreChanges": false,
               |        "created": ["${messageId.serialize}"],
               |        "updated": [],
               |        "destroyed": []
               |      }, "c1"]
               |    ]
               |}""".stripMargin)
      }
    }

    @Test
    def emailChangesShouldReturnUpdatedChangesWhenAddFlags(server: GuiceJamesServer): Unit = {
      val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
      val path: MailboxPath = MailboxPath.forUser(bobUsername, "mailbox1")

      mailboxProbe.createMailbox(path)

      server.getProbe(classOf[ACLProbeImpl])
        .replaceRights(path, andreUsername.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))

      val message: Message = Message.Builder
        .of
        .setSubject("test")
        .setBody("testmail", StandardCharsets.UTF_8)
        .build
      val messageId: MessageId = mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message)).getMessageId

      val oldState: State = waitForNextStateWithDelegation(server, AccountId.fromUsername(andreUsername), State.INITIAL)

      JmapRequests.markEmailAsSeen(messageId, bobUsername)

      val request =
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
           |  "methodCalls": [[
           |    "Email/changes",
           |    {
           |      "accountId": "$andreAccountId",
           |      "sinceState": "${oldState.getValue}"
           |    },
           |    "c1"]]
           |}""".stripMargin

      awaitAtMostTenSeconds.untilAsserted { () =>
        val response = `given`(
          baseRequestSpecBuilder(server)
            .setAuth(authScheme(UserCredential(andreUsername, ANDRE_PASSWORD)))
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
          .withOptions(IGNORING_ARRAY_ORDER)
          .isEqualTo(
            s"""{
               |    "sessionState": "${SESSION_STATE.value}",
               |    "methodResponses": [
               |      [ "Email/changes", {
               |        "accountId": "$andreAccountId",
               |        "oldState": "${oldState.getValue}",
               |        "hasMoreChanges": false,
               |        "created": [],
               |        "updated": ["${messageId.serialize}"],
               |        "destroyed": []
               |      }, "c1"]
               |    ]
               |}""".stripMargin)
      }
    }

    @Test
    def emailChangesShouldReturnUpdatedChangesWhenRemoveFlags(server: GuiceJamesServer): Unit = {
      val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
      val path: MailboxPath = MailboxPath.forUser(bobUsername, "mailbox1")

      mailboxProbe.createMailbox(path)

      server.getProbe(classOf[ACLProbeImpl])
        .replaceRights(path, andreUsername.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))

      val messageId: MessageId = mailboxProbe.appendMessage(bobUsername.asString(), path,
        AppendCommand.builder()
          .withFlags(new Flags(Flags.Flag.SEEN))
          .build("header: value\r\n\r\nbody"))
        .getMessageId

      val oldState: State = waitForNextStateWithDelegation(server, AccountId.fromUsername(andreUsername), State.INITIAL)

      JmapRequests.markEmailAsNotSeen(messageId, bobUsername)

      val request =
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
           |  "methodCalls": [[
           |    "Email/changes",
           |    {
           |      "accountId": "$andreAccountId",
           |      "sinceState": "${oldState.getValue}"
           |    },
           |    "c1"]]
           |}""".stripMargin

      awaitAtMostTenSeconds.untilAsserted { () =>
        val response = `given`(
          baseRequestSpecBuilder(server)
            .setAuth(authScheme(UserCredential(andreUsername, ANDRE_PASSWORD)))
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
          .withOptions(IGNORING_ARRAY_ORDER)
          .isEqualTo(
            s"""{
               |    "sessionState": "${SESSION_STATE.value}",
               |    "methodResponses": [
               |      [ "Email/changes", {
               |        "accountId": "$andreAccountId",
               |        "oldState": "${oldState.getValue}",
               |        "hasMoreChanges": false,
               |        "created": [],
               |        "updated": ["${messageId.serialize}"],
               |        "destroyed": []
               |      }, "c1"]
               |    ]
               |}""".stripMargin)
      }
    }

    @Test
    def emailChangesShouldReturnDestroyedChanges(server: GuiceJamesServer): Unit = {
      val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
      val path: MailboxPath = MailboxPath.forUser(bobUsername, "mailbox1")

      mailboxProbe.createMailbox(path)

      server.getProbe(classOf[ACLProbeImpl])
        .replaceRights(path, andreUsername.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))

      val message: Message = Message.Builder
        .of
        .setSubject("test")
        .setBody("testmail", StandardCharsets.UTF_8)
        .build
      val messageId: MessageId = mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message)).getMessageId

      val oldState: State = waitForNextStateWithDelegation(server, AccountId.fromUsername(andreUsername), State.INITIAL)

      JmapRequests.destroyEmail(messageId, bobUsername)

      val request =
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
           |  "methodCalls": [[
           |    "Email/changes",
           |    {
           |      "accountId": "$andreAccountId",
           |      "sinceState": "${oldState.getValue}"
           |    },
           |    "c1"]]
           |}""".stripMargin

      awaitAtMostTenSeconds.untilAsserted { () =>
        val response = `given`(
          baseRequestSpecBuilder(server)
            .setAuth(authScheme(UserCredential(andreUsername, ANDRE_PASSWORD)))
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
          .withOptions(IGNORING_ARRAY_ORDER)
          .isEqualTo(
            s"""{
               |    "sessionState": "${SESSION_STATE.value}",
               |    "methodResponses": [
               |      [ "Email/changes", {
               |        "accountId": "$andreAccountId",
               |        "oldState": "${oldState.getValue}",
               |        "hasMoreChanges": false,
               |        "created": [],
               |        "updated": [],
               |        "destroyed": ["${messageId.serialize}"]
               |      }, "c1"]
               |    ]
               |}""".stripMargin)
      }
    }

    @Test
    def emailChangesShouldNotReturnUpdatedChangesWhenMissingSharesCapability(server: GuiceJamesServer): Unit = {
      val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

      val path = MailboxPath.forUser(bobUsername, "mailbox1")
      mailboxProbe.createMailbox(path)

      server.getProbe(classOf[ACLProbeImpl])
        .replaceRights(path, andreUsername.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))

      val message: Message = Message.Builder
        .of
        .setSubject("test")
        .setBody("testmail", StandardCharsets.UTF_8)
        .build
      mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message))

      val request =
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
           |  "methodCalls": [[
           |    "Email/changes",
           |    {
           |      "accountId": "$andreAccountId",
           |      "sinceState": "${State.INITIAL.getValue}"
           |    },
           |    "c1"]]
           |}""".stripMargin

      awaitAtMostTenSeconds.untilAsserted { () =>
        val response = `given`(
          baseRequestSpecBuilder(server)
            .setAuth(authScheme(UserCredential(andreUsername, ANDRE_PASSWORD)))
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
          .withOptions(IGNORING_ARRAY_ORDER)
          .isEqualTo(
            s"""{
               |    "sessionState": "${SESSION_STATE.value}",
               |    "methodResponses": [
               |      [ "Email/changes", {
               |        "accountId": "$andreAccountId",
               |        "oldState": "${State.INITIAL.getValue}",
               |        "hasMoreChanges": false,
               |        "created": [],
               |        "updated": [],
               |        "destroyed": []
               |      }, "c1"]
               |    ]
               |}""".stripMargin)
      }
    }
  }

  @Test
  def emailChangesShouldReturnHasMoreChangesWhenTrue(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    val path: MailboxPath = MailboxPath.forUser(bobUsername, "mailbox1")

    mailboxProbe.createMailbox(path)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val messageId1: String = mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message)).getMessageId.serialize
    val messageId2: String = mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message)).getMessageId.serialize
    val messageId3: String = mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message)).getMessageId.serialize
    val messageId4: String = mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message)).getMessageId.serialize
    val messageId5: String = mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message)).getMessageId.serialize
    mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message))

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/changes",
         |    {
         |      "accountId": "$bobAccountId",
         |      "sinceState": "${State.INITIAL.getValue}"
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
        .withOptions(IGNORING_ARRAY_ORDER)
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |      [ "Email/changes", {
             |        "accountId": "$bobAccountId",
             |        "oldState": "${State.INITIAL.getValue}",
             |        "hasMoreChanges": true,
             |        "created": ["$messageId1", "$messageId2", "$messageId3", "$messageId4", "$messageId5"],
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
    val path: MailboxPath = MailboxPath.forUser(bobUsername, "mailbox1")

    mailboxProbe.createMailbox(path)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val messageId1: String = mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message)).getMessageId.serialize
    val messageId2: String = mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message)).getMessageId.serialize
    val messageId3: String = mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message)).getMessageId.serialize
    val messageId4: String = mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message)).getMessageId.serialize
    val messageId5: String = mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message)).getMessageId.serialize
    val messageId6: String = mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message)).getMessageId.serialize

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/changes",
         |    {
         |      "accountId": "$bobAccountId",
         |      "sinceState": "${State.INITIAL.getValue}",
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
        .withOptions(IGNORING_ARRAY_ORDER)
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |      [ "Email/changes", {
             |        "accountId": "$bobAccountId",
             |        "oldState": "${State.INITIAL.getValue}",
             |        "hasMoreChanges": false,
             |        "created": ["$messageId1", "$messageId2", "$messageId3", "$messageId4", "$messageId5", "$messageId6"],
             |        "updated": [],
             |        "destroyed": []
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
    }
  }

  @Test
  def emailChangesShouldReturnNoChangesWhenNoNewerState(server: GuiceJamesServer): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/changes",
         |    {
         |      "accountId": "$bobAccountId",
         |      "sinceState": "${State.INITIAL.getValue}"
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
        .withOptions(IGNORING_ARRAY_ORDER)
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |      [ "Email/changes", {
             |        "accountId": "$bobAccountId",
             |        "oldState": "${State.INITIAL.getValue}",
             |        "newState": "${State.INITIAL.getValue}",
             |        "hasMoreChanges": false,
             |        "created": [],
             |        "updated": [],
             |        "destroyed": []
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
    }
  }

  @Test
  def emailChangesShouldReturnDifferentStateThanOldState(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    val path: MailboxPath = MailboxPath.forUser(bobUsername, "mailbox1")

    mailboxProbe.createMailbox(path)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message))

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/changes",
         |    {
         |      "accountId": "$bobAccountId",
         |      "sinceState": "${State.INITIAL.getValue}"
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

      assertThat(State.INITIAL.getValue.toString).isNotEqualTo(newState)
    }
  }

  @Test
  def emailChangesShouldEventuallyReturnNoChanges(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    val path = MailboxPath.forUser(bobUsername, "mailbox1")
    mailboxProbe.createMailbox(path)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    mailboxProbe.appendMessage(bobUsername.asString(), path, AppendCommand.from(message))

    waitForNextState(server, AccountId.fromUsername(bobUsername), State.INITIAL)

    val request1 =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/changes",
         |    {
         |      "accountId": "$bobAccountId",
         |      "sinceState": "${State.INITIAL.getValue}"
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
         |    "Email/changes",
         |    {
         |      "accountId": "$bobAccountId",
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
      .withOptions(IGNORING_ARRAY_ORDER)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      [ "Email/changes", {
           |        "accountId": "$bobAccountId",
           |        "oldState": "$newState",
           |        "newState": "$newState",
           |        "hasMoreChanges": false,
           |        "created": [],
           |        "updated": [],
           |        "destroyed": []
           |      }, "c1"]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def emailChangesShouldFailWhenAccountIdNotFound(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/changes",
         |    {
         |      "accountId": "bad",
         |      "sinceState": "${State.INITIAL.getValue}"
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
  def emailChangesShouldFailWhenStateNotFound(server: GuiceJamesServer): Unit = {
    val state: String = stateFactory.generate().getValue.toString

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/changes",
         |    {
         |      "accountId": "$bobAccountId",
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
      .withOptions(IGNORING_ARRAY_ORDER)
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

  private def waitForNextState(server: GuiceJamesServer, accountId: AccountId, initialState: State): State = {
    val jmapGuiceProbe: JmapGuiceProbe = server.getProbe(classOf[JmapGuiceProbe])
    awaitAtMostTenSeconds.untilAsserted {
      () => assertThat(jmapGuiceProbe.getLatestEmailState(accountId)).isNotEqualTo(initialState)
    }

    jmapGuiceProbe.getLatestEmailState(accountId)
  }

  private def waitForNextStateWithDelegation(server: GuiceJamesServer, accountId: AccountId, initialState: State): State = {
    val jmapGuiceProbe: JmapGuiceProbe = server.getProbe(classOf[JmapGuiceProbe])
    awaitAtMostTenSeconds.untilAsserted {
      () => assertThat(jmapGuiceProbe.getLatestEmailStateWithDelegation(accountId)).isNotEqualTo(initialState)
    }

    jmapGuiceProbe.getLatestEmailStateWithDelegation(accountId)
  }
}
