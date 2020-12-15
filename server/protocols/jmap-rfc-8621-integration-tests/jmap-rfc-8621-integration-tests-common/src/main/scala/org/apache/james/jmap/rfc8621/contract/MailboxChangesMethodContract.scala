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
import java.time.ZonedDateTime
import java.util.UUID

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.builder.ResponseSpecBuilder
import io.restassured.http.ContentType.JSON
import javax.mail.Flags
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import net.javacrumbs.jsonunit.core.internal.Options
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.api.change.MailboxChange
import org.apache.james.jmap.api.change.MailboxChange.State
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
import org.junit.jupiter.api.{BeforeEach, Disabled, Nested, Test}
import play.api.libs.json.{JsString, Json}

import scala.jdk.CollectionConverters._

object TestId {
  def of(value: Long): MailboxId = TestId(value)
}

case class TestId(value: Long) extends MailboxId {
  override def serialize(): String = String.valueOf(value)
}

trait MailboxChangesMethodContract {

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
    provisionSystemMailboxes(server)

    val oldState: State = storeReferenceState(server, BOB)

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
            |        "updatedProperties": [],
            |        "created": ["$mailboxId1", "$mailboxId2", "$mailboxId3"],
            |        "updated": [],
            |        "destroyed": []
            |      }, "c1"]
            |    ]
            |}""".stripMargin)
  }

  @Test
  def mailboxChangesShouldReturnUpdatedChangesWhenRenameMailbox(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    provisionSystemMailboxes(server)
    val path = MailboxPath.forUser(BOB, "mailbox1")
    val mailboxId: String = mailboxProbe
      .createMailbox(path)
      .serialize

    val oldState: State = storeReferenceState(server, BOB)

    renameMailbox(mailboxId, "mailbox11")

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
           |        "updatedProperties": [],
           |        "created": [],
           |        "updated": ["$mailboxId"],
           |        "destroyed": []
           |      }, "c1"]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def mailboxChangesShouldReturnUpdatedChangesWhenAppendMessageToMailbox(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    provisionSystemMailboxes(server)

    val path = MailboxPath.forUser(BOB, "mailbox1")
    val mailboxId: String = mailboxProbe
      .createMailbox(path)
      .serialize

    val oldState: State = storeReferenceState(server, BOB)

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
           |        "updatedProperties": [],
           |        "created": [],
           |        "updated": ["$mailboxId"],
           |        "destroyed": []
           |      }, "c1"]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def mailboxChangesShouldReturnUpdatedChangesWhenAddSeenFlag(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    provisionSystemMailboxes(server)

    val path = MailboxPath.forUser(BOB, "mailbox1")
    val mailboxId: String = mailboxProbe
      .createMailbox(path)
      .serialize

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId: MessageId = mailboxProbe.appendMessage(BOB.asString(), path, AppendCommand.from(message)).getMessageId

    val oldState: State = storeReferenceState(server, BOB)

    markEmailAsSeen(messageId)

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
           |        "updatedProperties": [],
           |        "created": [],
           |        "updated": ["$mailboxId"],
           |        "destroyed": []
           |      }, "c1"]
           |    ]
           |}""".stripMargin)
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

    val messageId: MessageId = mailboxProbe.appendMessage(BOB.asString(), path,
      AppendCommand.builder()
        .withFlags(new Flags(Flags.Flag.SEEN))
        .build("header: value\r\n\r\nbody"))
      .getMessageId

    val oldState: State = storeReferenceState(server, BOB)

    markEmailAsNotSeen(messageId)

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
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [
           |    ["Mailbox/changes", {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "oldState": "${oldState.getValue}",
           |      "hasMoreChanges": false,
           |      "updatedProperties": [],
           |      "created": [],
           |      "updated": ["$mailboxId"],
           |      "destroyed": []
           |    }, "c1"]
           |  ]
           |}""".stripMargin)
  }

  @Test
  def mailboxChangesShouldReturnUpdatedChangesWhenDestroyEmail(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    provisionSystemMailboxes(server)

    val path = MailboxPath.forUser(BOB, "mailbox1")
    val mailboxId: String = mailboxProbe
      .createMailbox(path)
      .serialize

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId: MessageId = mailboxProbe.appendMessage(BOB.asString(), path, AppendCommand.from(message)).getMessageId

    val oldState: State = storeReferenceState(server, BOB)

    destroyEmail(messageId)

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
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [
           |    ["Mailbox/changes", {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "oldState": "${oldState.getValue}",
           |      "hasMoreChanges": false,
           |      "updatedProperties": [],
           |      "created": [],
           |      "updated": ["$mailboxId"],
           |      "destroyed": []
           |    }, "c1"]
           |  ]
           |}""".stripMargin)
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

      val oldState: State = storeReferenceState(server, ANDRE)

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
           |      "accountId": "$ANDRE_ACCOUNT_ID",
           |      "sinceState": "${oldState.getValue}"
           |    },
           |    "c1"]]
           |}""".stripMargin

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
             |        "updatedProperties": [],
             |        "created": [],
             |        "updated": ["$mailboxId"],
             |        "destroyed": []
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
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

      val oldState: State = storeReferenceState(server, ANDRE)

      renameMailbox(mailboxId, "mailbox11")

      val request =
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
           |  "methodCalls": [[
           |    "Mailbox/changes",
           |    {
           |      "accountId": "$ANDRE_ACCOUNT_ID",
           |      "sinceState": "${oldState.getValue}"
           |    },
           |    "c1"]]
           |}""".stripMargin

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
             |        "updatedProperties": [],
             |        "created": [],
             |        "updated": ["$mailboxId"],
             |        "destroyed": []
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
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

      val message: Message = Message.Builder
        .of
        .setSubject("test")
        .setBody("testmail", StandardCharsets.UTF_8)
        .build
      val messageId: MessageId = mailboxProbe.appendMessage(BOB.asString(), path, AppendCommand.from(message)).getMessageId

      val oldState: State = storeReferenceState(server, ANDRE)

      markEmailAsSeen(messageId)

      val request =
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
           |  "methodCalls": [[
           |    "Mailbox/changes",
           |    {
           |      "accountId": "$ANDRE_ACCOUNT_ID",
           |      "sinceState": "${oldState.getValue}"
           |    },
           |    "c1"]]
           |}""".stripMargin

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
             |        "updatedProperties": [],
             |        "created": [],
             |        "updated": ["$mailboxId"],
             |        "destroyed": []
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
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

      val messageId: MessageId = mailboxProbe.appendMessage(BOB.asString(), path,
        AppendCommand.builder()
          .withFlags(new Flags(Flags.Flag.SEEN))
          .build("header: value\r\n\r\nbody"))
        .getMessageId

      val oldState: State = storeReferenceState(server, ANDRE)

      markEmailAsNotSeen(messageId)

      val request =
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
           |  "methodCalls": [[
           |    "Mailbox/changes",
           |    {
           |      "accountId": "$ANDRE_ACCOUNT_ID",
           |      "sinceState": "${oldState.getValue}"
           |    },
           |    "c1"]]
           |}""".stripMargin

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
             |        "updatedProperties": [],
             |        "created": [],
             |        "updated": ["$mailboxId"],
             |        "destroyed": []
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
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

      val message: Message = Message.Builder
        .of
        .setSubject("test")
        .setBody("testmail", StandardCharsets.UTF_8)
        .build
      val messageId: MessageId = mailboxProbe.appendMessage(BOB.asString(), path, AppendCommand.from(message)).getMessageId

      val oldState: State = storeReferenceState(server, ANDRE)

      destroyEmail(messageId)

      val request =
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
           |  "methodCalls": [[
           |    "Mailbox/changes",
           |    {
           |      "accountId": "$ANDRE_ACCOUNT_ID",
           |      "sinceState": "${oldState.getValue}"
           |    },
           |    "c1"]]
           |}""".stripMargin

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
             |        "updatedProperties": [],
             |        "created": [],
             |        "updated": ["$mailboxId"],
             |        "destroyed": []
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
    }

    @Test
    @Disabled("Not implemented yet")
    def mailboxChangesShouldReturnUpdatedChangesWhenDestroyDelegatedMailbox(server: GuiceJamesServer): Unit = {
      val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

      provisionSystemMailboxes(server)

      val path = MailboxPath.forUser(BOB, "mailbox1")
      val mailboxId: String = mailboxProbe
        .createMailbox(path)
        .serialize

      server.getProbe(classOf[ACLProbeImpl])
        .replaceRights(path, ANDRE.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))

      val oldState: State = storeReferenceState(server, ANDRE)

      destroyMailbox(mailboxId)

      val request =
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
           |  "methodCalls": [[
           |    "Mailbox/changes",
           |    {
           |      "accountId": "$ANDRE_ACCOUNT_ID",
           |      "sinceState": "${oldState.getValue}"
           |    },
           |    "c1"]]
           |}""".stripMargin

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
             |        "updatedProperties": [],
             |        "created": [],
             |        "updated": [],
             |        "destroyed": ["$mailboxId"]
             |      }, "c1"]
             |    ]
             |}""".stripMargin)
    }
  }

  @Test
  def mailboxChangesShouldReturnDestroyedChanges(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    provisionSystemMailboxes(server)

    val path = MailboxPath.forUser(BOB, "mailbox1")
    val mailboxId: String = mailboxProbe
      .createMailbox(path)
      .serialize

    val oldState: State = storeReferenceState(server, BOB)

    mailboxProbe
      .deleteMailbox(path.getNamespace, BOB.asString(), path.getName)

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
           |        "updatedProperties": [],
           |        "created": [],
           |        "updated": [],
           |        "destroyed": ["$mailboxId"]
           |      }, "c1"]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def mailboxChangesShouldReturnAllTypeOfChanges(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    provisionSystemMailboxes(server)

    val oldState: State = storeReferenceState(server, BOB)

    val path1 = MailboxPath.forUser(BOB, "mailbox1")
    val mailboxId1: String = mailboxProbe
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
    renameMailbox(mailboxId2, "mailbox22")

    server.getProbe(classOf[MailboxProbeImpl])
      .deleteMailbox(path1.getNamespace, BOB.asString(), path1.getName)

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
           |        "updatedProperties": [],
           |        "created": ["$mailboxId1", "$mailboxId2"],
           |        "updated": ["$mailboxId1", "$mailboxId2"],
           |        "destroyed": ["$mailboxId1"]
           |      }, "c1"]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def mailboxChangesShouldReturnHasMoreChangesWhenTrue(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    provisionSystemMailboxes(server)

    val oldState: State = storeReferenceState(server, BOB)

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
           |        "hasMoreChanges": true,
           |        "updatedProperties": [],
           |        "created": ["$mailboxId1", "$mailboxId2", "$mailboxId3", "$mailboxId4", "$mailboxId5"],
           |        "updated": [],
           |        "destroyed": []
           |      }, "c1"]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def mailboxChangesShouldFailWhenAccountIdNotFound(server: GuiceJamesServer): Unit = {
    val oldState: State = storeReferenceState(server, BOB)

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

    val state: String = UUID.randomUUID().toString

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
    provisionSystemMailboxes(server)

    val oldState: State = storeReferenceState(server, BOB)

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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      [ "Mailbox/changes", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "oldState": "${oldState.getValue}",
           |        "newState": "${oldState.getValue}",
           |        "hasMoreChanges": false,
           |        "updatedProperties": [],
           |        "created": [],
           |        "updated": [],
           |        "destroyed": []
           |      }, "c1"]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def mailboxChangesShouldReturnDifferentStateThanOldState(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    provisionSystemMailboxes(server)

    val oldState: State = storeReferenceState(server, BOB)
    mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "mailbox1"))
    mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "mailbox2"))

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

    val newState = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("newState")
      .get.asInstanceOf[JsString].value

    assertThat(oldState.getValue.toString).isNotEqualTo(newState)
  }

  @Test
  def mailboxChangesShouldEventuallyReturnNoChanges(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    provisionSystemMailboxes(server)

    val oldState: State = storeReferenceState(server, BOB)
    mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "mailbox1"))
    mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "mailbox2"))

    val request1 =
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
           |        "updatedProperties": [],
           |        "created": [],
           |        "updated": [],
           |        "destroyed": []
           |      }, "c1"]
           |    ]
           |}""".stripMargin)
  }

  private def renameMailbox(mailboxId: String, name: String): Unit = {
    val request =
      s"""
         |{
         |  "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |  "methodCalls": [[
         |    "Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "$mailboxId": {
         |          "name": "$name"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}
         |""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
  }

  private def destroyMailbox(mailboxId: String): Unit = {
    val request =
      s"""
         |{
         |  "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |  "methodCalls": [[
         |    "Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "destroy": ["$mailboxId"]
         |    }, "c1"]
         |  ]
         |}
         |""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
  }

  private def markEmailAsSeen(messageId: MessageId): Unit = {
    val request = String.format(
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "${messageId.serialize}": {
         |          "keywords": {
         |             "$$seen": true
         |          }
         |        }
         |      }
         |    }, "c1"]]
         |}""".stripMargin)

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
  }

  private def markEmailAsNotSeen(messageId: MessageId): Unit = {
    val request = String.format(
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "${messageId.serialize}": {
         |          "keywords/$$seen": null
         |        }
         |      }
         |    }, "c1"]]
         |}""".stripMargin)

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
  }

  private def destroyEmail(messageId: MessageId): Unit = {
    val request = String.format(
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "destroy": ["${messageId.serialize}"]
         |    }, "c1"]]
         |}""".stripMargin)

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
  }

  private def storeReferenceState(server: GuiceJamesServer, username: Username): State = {
    val state: State = State.of(UUID.randomUUID())
    val jmapGuiceProbe: JmapGuiceProbe = server.getProbe(classOf[JmapGuiceProbe])
    jmapGuiceProbe.saveMailboxChange(MailboxChange.updated(AccountId.fromUsername(username), state, ZonedDateTime.now(), List(TestId.of(0)).asJava).build)

    state
  }

  private def provisionSystemMailboxes(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Mailbox/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["$mailboxId"]
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
  }
}
