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

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ALICE, ANDRE, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.MailboxACL.Right
import org.apache.james.mailbox.model.{MailboxACL, MailboxPath, MessageId}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.{ACLProbeImpl, MailboxProbeImpl}
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Test}

trait EmailGetMethodContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  def randomMessageId: MessageId

  @Test
  def idsShouldBeMandatory(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [[
               |    "Email/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "error",
         |            {
         |                "type": "invalidArguments",
         |                "description": "ids can not be ommited for email/get"
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def noIdsShouldBeAccepted(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [[
               |    "Email/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [],
         |                "notFound": []
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def invalidIdsShouldBeNotFound(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [[
               |    "Email/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "ids": ["invalid"]
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
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [],
         |                "notFound": ["invalid"]
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def notExistingValidIdsShouldBeNotFound(): Unit = {
    val messageId: MessageId = randomMessageId
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}"]
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [],
         |                "notFound": ["${messageId.serialize()}"]
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def existingEmailsShouldBeFound(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}"]
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [{
         |                        "id": "${messageId.serialize()}",
         |                        "size": 85
         |                    }],
         |                "notFound": []
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def foundAndNotFoundCanBeMixed(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(message))
      .getMessageId
    val nonExistingMessageId: MessageId = randomMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}", "invalid", "${nonExistingMessageId.serialize()}"]
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
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [ {
         |                        "id": "${messageId.serialize()}",
         |                        "size": 85
         |                    }],
         |                "notFound": ["${nonExistingMessageId.serialize()}", "invalid"]
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def severalEmailCanBeRetrievedAtOnce(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(message))
      .getMessageId
    val messageId2: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId1.serialize()}", "${messageId2.serialize()}"]
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |                    {
         |                        "id": "${messageId1.serialize()}",
         |                        "size": 85
         |                    },
         |                    {
         |                        "id": "${messageId2.serialize()}",
         |                        "size": 85
         |                    }
         |                ],
         |                "notFound": []
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def requestingTheSameIdTwiceReturnsItOnce(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}", "${messageId.serialize()}"]
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [{
         |                        "id": "${messageId.serialize()}",
         |                        "size": 85
         |                    }],
         |                "notFound": []
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def propertiesShouldBeFiltered(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}", "${messageId.serialize()}"],
         |      "properties": ["id"]
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [{
         |                        "id": "${messageId.serialize()}"
         |                    }],
         |                "notFound": []
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def emptyPropertiesDefaultsToId(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}", "${messageId.serialize()}"],
         |      "properties": []
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [{
         |                        "id": "${messageId.serialize()}"
         |                    }],
         |                "notFound": []
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def idPropertyShouldAlwaysBeReturned(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}", "${messageId.serialize()}"],
         |      "properties": ["size"]
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [{
         |                        "id": "${messageId.serialize()}",
         |                        "size": 85
         |                    }],
         |                "notFound": []
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def invalidPropertiesShouldFail(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}", "${messageId.serialize()}"],
         |      "properties": ["invalid"]
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "error",
         |            {
         |                "type": "invalidArguments",
         |                "description": "The following properties [invalid] do not exist."
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def requestingTheSameNotFoundIdTwiceReturnsItOnce(): Unit = {
    val messageId: MessageId = randomMessageId
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}", "${messageId.serialize()}"]
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [],
         |                "notFound": ["${messageId.serialize()}"]
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def getShouldReturnNotFoundWhenNoRights(server: GuiceJamesServer): Unit = {
    val andreMailbox: String = "andrecustom"
    val path = MailboxPath.forUser(ANDRE, andreMailbox)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString, path, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}"]
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [],
         |                "notFound": ["${messageId.serialize()}"]
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def getShouldReturnMessagesInDelegatedMailboxes(server: GuiceJamesServer): Unit = {
    val andreMailbox: String = "andrecustom"
    val path = MailboxPath.forUser(ANDRE, andreMailbox)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString, path, AppendCommand.from(message))
      .getMessageId
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Read))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}"]
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [{
         |                        "id": "${messageId.serialize()}",
         |                        "size": 85
         |                    }],
         |                "notFound": []
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def getShouldReturnNotFoundWhenDeletedUserMissesReadRight(server: GuiceJamesServer): Unit = {
    val andreMailbox: String = "andrecustom"
    val path = MailboxPath.forUser(ANDRE, andreMailbox)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString, path, AppendCommand.from(message))
      .getMessageId
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, BOB.asString, MailboxACL.Rfc4314Rights.allExcept(Right.Read))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}"]
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [],
         |                "notFound": ["${messageId.serialize()}"]
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def emailGetShouldReturnUnknownMethodWhenMissingOneCapability(): Unit = {
    val messageId: MessageId = randomMessageId
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": ["urn:ietf:params:jmap:core"],
               |  "methodCalls": [[
               |     "Email/get",
               |     {
               |       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |       "ids": ["${messageId.serialize()}"]
               |     },
               |     "c1"]]
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
         |  "sessionState": "75128aab4b1b",
         |  "methodResponses": [[
         |    "error",
         |    {
         |      "type": "unknownMethod",
         |      "description": "Missing capability(ies): urn:ietf:params:jmap:mail"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def emailGetShouldReturnUnknownMethodWhenMissingAllCapabilities(): Unit = {
    val messageId: MessageId = randomMessageId
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [],
               |  "methodCalls": [[
               |     "Email/get",
               |     {
               |       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |       "ids": ["${messageId.serialize()}"]
               |     },
               |     "c1"]]
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
         |  "sessionState": "75128aab4b1b",
         |  "methodResponses": [[
         |    "error",
         |    {
         |      "type": "unknownMethod",
         |      "description": "Missing capability(ies): urn:ietf:params:jmap:core, urn:ietf:params:jmap:mail"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def bodyPropertiesFilteringShouldBeApplied(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}"],
         |      "properties":["bodyStructure"],
         |      "bodyProperties":["partId", "blobId"]
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |                    {
         |                        "id": "${messageId.serialize()}",
         |                        "bodyStructure": {
         |                            "partId": "1",
         |                            "blobId": "${messageId.serialize()}_1"
         |                        }
         |                    }
         |                ],
         |                "notFound": []
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def bodyPropertiesShouldMatchSpecifiedDefaults(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}"],
         |      "properties":["bodyStructure"]
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [
         |        [
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |                    {
         |                        "id": "${messageId.serialize()}",
         |                        "bodyStructure": {
         |                            "partId": "1",
         |                            "blobId": "${messageId.serialize()}_1",
         |                            "size": 85,
         |                            "type": "text/plain",
         |                            "charset": "UTF-8"
         |                        }
         |                    }
         |                ],
         |                "notFound": [
         |
         |                ]
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def emptyBodyPropertiesShouldReturnEmptyObjects(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}"],
         |      "properties":["bodyStructure"],
         |      "bodyProperties":[]
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |                    {
         |                        "id": "${messageId.serialize()}",
         |                        "bodyStructure": {}
         |                    }
         |                ],
         |                "notFound": [
         |
         |                ]
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def invalidBodyPropertiesShouldBeRejected(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}"],
         |      "properties":["bodyStructure"],
         |      "bodyProperties":["invalid"]
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [
         |        [
         |            "error",
         |            {
         |                "type": "invalidArguments",
         |                "description": "The following bodyProperties [invalid] do not exist."
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def bodyStructureForSimpleMessage(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}"],
         |      "properties":["bodyStructure"],
         |      "bodyProperties":["partId", "blobId", "size", "name", "type", "charset", "disposition", "cid", "language", "location", "subParts", "headers"]
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [
         |        [
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |                    {
         |                        "id": "${messageId.serialize()}",
         |                        "bodyStructure": {
         |                            "partId": "1",
         |                            "blobId": "${messageId.serialize()}_1",
         |                            "headers": [
         |                                {
         |                                    "name": "MIME-Version",
         |                                    "value": "1.0"
         |                                },
         |                                {
         |                                    "name": "Subject",
         |                                    "value": "test"
         |                                },
         |                                {
         |                                    "name": "Content-Type",
         |                                    "value": "text/plain; charset=UTF-8"
         |                                }
         |                            ],
         |                            "size": 85,
         |                            "type": "text/plain",
         |                            "charset": "UTF-8"
         |                        }
         |                    }
         |                ],
         |                "notFound": [
         |
         |                ]
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def bodyStructureForSimpleMultipart(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoader.getSystemResourceAsStream("eml/multipart_simple.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}"],
         |      "properties":["bodyStructure"],
         |      "bodyProperties":["partId", "blobId", "size", "name", "type", "charset", "disposition", "cid", "language", "location", "subParts", "headers"]
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

    val contentType = "multipart/mixed; boundary=\\\"------------64D8D789FC30153D6ED18258\\\""
    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [
         |        [
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |                    {
         |                        "id": "${messageId.serialize()}",
         |                        "bodyStructure": {
         |                            "partId": "1",
         |                            "headers": [
         |                                {
         |                                    "name": "Return-Path",
         |                                    "value": "<from@linagora.com>"
         |                                },
         |                                {
         |                                    "name": "To",
         |                                    "value": "to@linagora.com"
         |                                },
         |                                {
         |                                    "name": "From",
         |                                    "value": "Lina <from@linagora.com>"
         |                                },
         |                                {
         |                                    "name": "Subject",
         |                                    "value": "MultiAttachment"
         |                                },
         |                                {
         |                                    "name": "Message-ID",
         |                                    "value": "<13d4375e-a4a9-f613-06a1-7e8cb1e0ea93@linagora.com>"
         |                                },
         |                                {
         |                                    "name": "Date",
         |                                    "value": "Mon, 27 Feb 2017 11:24:48 +0700"
         |                                },
         |                                {
         |                                    "name": "User-Agent",
         |                                    "value": "Mozilla/5.0 (X11; Linux x86_64; rv:45.0) Gecko/20100101 Thunderbird/45.2.0"
         |                                },
         |                                {
         |                                    "name": "MIME-Version",
         |                                    "value": "1.0"
         |                                },
         |                                {
         |                                    "name": "Content-Type",
         |                                    "value": "$contentType"
         |                                }
         |                            ],
         |                            "size": 2688,
         |                            "type": "multipart/mixed",
         |                            "charset": "us-ascii",
         |                            "subParts": [
         |                                {
         |                                    "partId": "2",
         |                                    "blobId": "${messageId.serialize()}_2",
         |                                    "headers": [
         |                                        {
         |                                            "name": "Content-Type",
         |                                            "value": "text/plain; charset=utf-8; format=flowed"
         |                                        },
         |                                        {
         |                                            "name": "Content-Transfer-Encoding",
         |                                            "value": "7bit"
         |                                        }
         |                                    ],
         |                                    "size": 97,
         |                                    "type": "text/plain",
         |                                    "charset": "utf-8"
         |                                },
         |                                {
         |                                    "partId": "3",
         |                                    "blobId": "${messageId.serialize()}_3",
         |                                    "headers": [
         |                                        {
         |                                            "name": "Content-Type",
         |                                            "value": "text/plain; charset=UTF-8; name=\\\"text1\\\""
         |                                        },
         |                                        {
         |                                            "name": "Content-Transfer-Encoding",
         |                                            "value": "base64"
         |                                        },
         |                                        {
         |                                            "name": "Content-Disposition",
         |                                            "value": "attachment; filename=\\\"text1\\\""
         |                                        }
         |                                    ],
         |                                    "size": 519,
         |                                    "name": "text1",
         |                                    "type": "text/plain",
         |                                    "charset": "UTF-8",
         |                                    "disposition": "attachment"
         |                                },
         |                                {
         |                                    "partId": "4",
         |                                    "blobId": "${messageId.serialize()}_4",
         |                                    "headers": [
         |                                        {
         |                                            "name": "Content-Type",
         |                                            "value": "application/vnd.ms-publisher; name=\\\"text2\\\""
         |                                        },
         |                                        {
         |                                            "name": "Content-Transfer-Encoding",
         |                                            "value": "base64"
         |                                        },
         |                                        {
         |                                            "name": "Content-Disposition",
         |                                            "value": "attachment; filename=\\\"text2\\\""
         |                                        }
         |                                    ],
         |                                    "size": 694,
         |                                    "name": "text2",
         |                                    "type": "application/vnd.ms-publisher",
         |                                    "charset": "us-ascii",
         |                                    "disposition": "attachment"
         |                                },
         |                                {
         |                                    "partId": "5",
         |                                    "blobId": "${messageId.serialize()}_5",
         |                                    "headers": [
         |                                        {
         |                                            "name": "Content-Type",
         |                                            "value": "text/plain; charset=UTF-8; name=\\\"text3\\\""
         |                                        },
         |                                        {
         |                                            "name": "Content-Transfer-Encoding",
         |                                            "value": "base64"
         |                                        },
         |                                        {
         |                                            "name": "Content-Disposition",
         |                                            "value": "attachment; filename=\\\"text3\\\""
         |                                        }
         |                                    ],
         |                                    "size": 713,
         |                                    "name": "text3",
         |                                    "type": "text/plain",
         |                                    "charset": "UTF-8",
         |                                    "disposition": "attachment"
         |                                }
         |                            ]
         |                        }
         |                    }
         |                ],
         |                "notFound": []
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def bodyStructureForComplexMultipart(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoader.getSystemResourceAsStream("eml/multipart_complex.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}"],
         |      "properties":["bodyStructure"],
         |      "bodyProperties":["partId", "blobId", "size", "name", "type", "charset", "disposition", "cid", "language", "location", "subParts", "headers"]
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [
         |        [
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |                    {
         |                        "id": "${messageId.serialize()}",
         |                        "bodyStructure": {
         |                            "partId": "1",
         |                            "headers": [
         |                                {
         |                                    "name": "Date",
         |                                    "value": "Tue, 03 Jan 2017 16:05:01 +0100"
         |                                },
         |                                {
         |                                    "name": "From",
         |                                    "value": "sender <sender@james.org>"
         |                                },
         |                                {
         |                                    "name": "MIME-Version",
         |                                    "value": "1.0"
         |                                },
         |                                {
         |                                    "name": "To",
         |                                    "value": "David DOLCIMASCOLO <david.ddo@linagora.com>"
         |                                },
         |                                {
         |                                    "name": "Subject",
         |                                    "value": "Re: [Internet] Rendez-vous"
         |                                },
         |                                {
         |                                    "name": "References",
         |                                    "value": "<9b6a4271-69fb-217a-5c14-c68c68375d96@linagora.com>"
         |                                },
         |                                {
         |                                    "name": "In-Reply-To",
         |                                    "value": "<d5c6f1d6-96e7-8172-9fe6-41fa6c9bd6ec@linagora.com>"
         |                                },
         |                                {
         |                                    "name": "X-Gie-Attachments",
         |                                    "value": "none"
         |                                },
         |                                {
         |                                    "name": "Cc",
         |                                    "value": ""
         |                                },
         |                                {
         |                                    "name": "Content-type",
         |                                    "value": "multipart/mixed; boundary=\\"----------=_1483455916-7086-3\\""
         |                                }
         |                            ],
         |                            "size": 1300,
         |                            "type": "multipart/mixed",
         |                            "charset": "us-ascii",
         |                            "subParts": [
         |                                {
         |                                    "partId": "2",
         |                                    "headers": [
         |                                        {
         |                                            "name": "Content-Type",
         |                                            "value": "multipart/alternative; boundary=\\\"------------060506070600060108040700\\\""
         |                                        }
         |                                    ],
         |                                    "size": 483,
         |                                    "type": "multipart/alternative",
         |                                    "charset": "us-ascii",
         |                                    "subParts": [
         |                                        {
         |                                            "partId": "3",
         |                                            "blobId": "${messageId.serialize()}_3",
         |                                            "headers": [
         |                                                {
         |                                                    "name": "Content-Type",
         |                                                    "value": "text/plain; charset=ISO-8859-1; format=flowed"
         |                                                },
         |                                                {
         |                                                    "name": "Content-Transfer-Encoding",
         |                                                    "value": "8bit"
         |                                                }
         |                                            ],
         |                                            "size": 114,
         |                                            "type": "text/plain",
         |                                            "charset": "ISO-8859-1"
         |                                        },
         |                                        {
         |                                            "partId": "4",
         |                                            "blobId": "${messageId.serialize()}_4",
         |                                            "headers": [
         |                                                {
         |                                                    "name": "Content-Type",
         |                                                    "value": "text/html; charset=ISO-8859-1"
         |                                                },
         |                                                {
         |                                                    "name": "Content-Transfer-Encoding",
         |                                                    "value": "7bit"
         |                                                }
         |                                            ],
         |                                            "size": 108,
         |                                            "type": "text/html",
         |                                            "charset": "ISO-8859-1"
         |                                        }
         |                                    ]
         |                                },
         |                                {
         |                                    "partId": "5",
         |                                    "blobId": "${messageId.serialize()}_5",
         |                                    "headers": [
         |                                        {
         |                                            "name": "Content-ID",
         |                                            "value": "<14672787885774e5c4d4cee471352039@linagora.com>"
         |                                        },
         |                                        {
         |                                            "name": "Content-Type",
         |                                            "value": "text/plain; charset=\\\"iso-8859-1\\\"; name=\\\"avertissement.txt\\\""
         |                                        },
         |                                        {
         |                                            "name": "Content-Disposition",
         |                                            "value": "inline; filename=\\\"avertissement.txt\\\""
         |                                        },
         |                                        {
         |                                            "name": "Content-Transfer-Encoding",
         |                                            "value": "binary"
         |                                        }
         |                                    ],
         |                                    "size": 249,
         |                                    "name": "avertissement.txt",
         |                                    "type": "text/plain",
         |                                    "charset": "iso-8859-1",
         |                                    "disposition": "inline",
         |                                    "cid": "14672787885774e5c4d4cee471352039@linagora.com"
         |                                }
         |                            ]
         |                        }
         |                    }
         |                ],
         |                "notFound": []
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def textBodyForSimpleMultipart(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoader.getSystemResourceAsStream("eml/multipart_simple.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}"],
         |      "properties":["textBody"]
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |                    {
         |                        "id": "${messageId.serialize()}",
         |                        "textBody": [
         |                            {
         |                                "partId": "2",
         |                                "blobId": "${messageId.serialize()}_2",
         |                                "size": 97,
         |                                "type": "text/plain",
         |                                "charset": "utf-8"
         |                            }
         |                        ]
         |                    }
         |                ],
         |                "notFound": []
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def textBodyForComplexMultipart(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoader.getSystemResourceAsStream("eml/multipart_complex.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}"],
         |      "properties":["textBody"]
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |                    {
         |                        "id": "${messageId.serialize()}",
         |                        "textBody": [
         |                            {
         |                                "partId": "3",
         |                                "blobId": "${messageId.serialize()}_3",
         |                                "size": 114,
         |                                "type": "text/plain",
         |                                "charset": "ISO-8859-1"
         |                            }
         |                        ]
         |                    }
         |                ],
         |                "notFound": []
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def htmlBodyForComplexMultipart(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoader.getSystemResourceAsStream("eml/multipart_complex.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}"],
         |      "properties":["htmlBody"]
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |                    {
         |                        "id": "${messageId.serialize()}",
         |                        "htmlBody": [
         |                            {
         |                                "partId": "4",
         |                                "blobId": "${messageId.serialize()}_4",
         |                                "size": 108,
         |                                "type": "text/html",
         |                                "charset": "ISO-8859-1"
         |                            }
         |                        ]
         |                    }
         |                ],
         |                "notFound": []
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def textBodyValuesForSimpleMultipart(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoader.getSystemResourceAsStream("eml/multipart_simple.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}"],
         |      "properties":["bodyValues"],
         |      "fetchTextBodyValues": true
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |                    {
         |                        "id": "${messageId.serialize()}",
         |                        "bodyValues": {
         |                            "2": {
         |                                "value": "Send\\n\\n",
         |                                "isEncodingProblem": false,
         |                                "isTruncated": false
         |                            }
         |                        }
         |                    }
         |                ],
         |                "notFound": []
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def textBodyValuesForComplexMultipart(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoader.getSystemResourceAsStream("eml/multipart_complex.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}"],
         |      "properties":["bodyValues"],
         |      "fetchTextBodyValues": true
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |                    {
         |                        "id": "${messageId.serialize()}",
         |                        "bodyValues": {
         |                            "3": {
         |                                "value": "/blabla/\\n*bloblo*\\n",
         |                                "isEncodingProblem": false,
         |                                "isTruncated": false
         |                            }
         |                        }
         |                    }
         |                ],
         |                "notFound": []
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def htmlBodyValuesForComplexMultipart(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoader.getSystemResourceAsStream("eml/multipart_complex.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}"],
         |      "properties":["bodyValues"],
         |      "fetchHTMLBodyValues": true
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |                    {
         |                        "id": "${messageId.serialize()}",
         |                        "bodyValues": {
         |                            "4": {
         |                                "value": "<i>blabla</i>\\n<b>bloblo</b>\\n",
         |                                "isEncodingProblem": false,
         |                                "isTruncated": false
         |                            }
         |                        }
         |                    }
         |                ],
         |                "notFound": []
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def textAndHtmlBodyValuesForComplexMultipart(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoader.getSystemResourceAsStream("eml/multipart_complex.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}"],
         |      "properties":["bodyValues"],
         |      "fetchTextBodyValues": true,
         |      "fetchHTMLBodyValues": true
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [
         |        [
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |                    {
         |                        "id": "1",
         |                        "bodyValues": {
         |                            "3": {
         |                                "value": "/blabla/\\n*bloblo*\\n",
         |                                "isEncodingProblem": false,
         |                                "isTruncated": false
         |                            },
         |                            "4": {
         |                                "value": "<i>blabla</i>\\n<b>bloblo</b>\\n",
         |                                "isEncodingProblem": false,
         |                                "isTruncated": false
         |                            }
         |                        }
         |                    }
         |                ],
         |                "notFound": []
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def textAndHtmlBodyValuesForSimpleMultipart(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoader.getSystemResourceAsStream("eml/multipart_simple.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}"],
         |      "properties":["bodyValues"],
         |      "fetchTextBodyValues": true,
         |      "fetchHTMLBodyValues": true
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |                    {
         |                        "id": "${messageId.serialize()}",
         |                        "bodyValues": {
         |                            "2": {
         |                                "value": "Send\\n\\n",
         |                                "isEncodingProblem": false,
         |                                "isTruncated": false
         |                            }
         |                        }
         |                    }
         |                ],
         |                "notFound": []
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def allBodyValuesForSimpleMultipart(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoader.getSystemResourceAsStream("eml/multipart_simple.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}"],
         |      "properties":["bodyValues"],
         |      "fetchAllBodyValues": true
         |    },
         |    "c1"]]
         |}""".stripMargin
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post.prettyPeek()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [
         |        [
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |                    {
         |                        "id": "1",
         |                        "bodyValues": {
         |                            "2": {
         |                                "value": "Send\\n\\n",
         |                                "isEncodingProblem": false,
         |                                "isTruncated": false
         |                            },
         |                            "3": {
         |                                "value": "-----BEGIN RSA PRIVATE KEY-----\\nMIIEogIBAAKCAQEAx7PG0+E//EMpm7IgI5Q9TMDSFya/1hE+vvTJrk0iGFllPeHL\\nA5/VlTM0YWgG6X50qiMfE3VLazf2c19iXrT0mq/21PZ1wFnogv4zxUNaih+Bng62\\nF0SyruE/O/Njqxh/Ccq6K/e05TV4T643USxAeG0KppmYW9x8HA/GvV832apZuxkV\\ni6NVkDBrfzaUCwu4zH+HwOv/pI87E7KccHYC++Biaj3\\n",
         |                                "isEncodingProblem": false,
         |                                "isTruncated": false
         |                            },
         |                            "5": {
         |                                "value": "|1|oS75OgL3vF2Gdl99CJDbEpaJ3yE=|INGqljCW1XMf4ggOQm26/BNnKGc= ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAQEAq2A7hRGmdnm9tUDbO9IDSwBK6TbQa+PXYPCPy6rbTrTtw7PHkccKrpp0yVhp5HdEIcKr6pLlVDBfOLX9QUsyCOV0wzfjIJNlGEYsdlLJizHhbn2mUjvSAHQqZETYP81eFzLQNnPHt4EVVUh7VfDESU84KezmD5QlWpXLmvU31/yMf+Se8xhHTvKSCZIFImWwoG6mbUoWf9nzpIoaSjB+weqqUUmpaaasXVal72J+UX2B+2RPW3RcT0eOzQgqlJL3RKrTJvdsjE3JEAvGq3lGHSZXyN6m5U4hpph9uOv54aHc4Xr8jhAa/SX5MJ\\n",
         |                                "isEncodingProblem": false,
         |                                "isTruncated": false
         |                            }
         |                        }
         |                    }
         |                ],
         |                "notFound": []
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def allBodyValuesForComplexMultipart(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoader.getSystemResourceAsStream("eml/multipart_complex.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}"],
         |      "properties":["bodyValues"],
         |      "fetchAllBodyValues": true
         |    },
         |    "c1"]]
         |}""".stripMargin
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post.prettyPeek()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |                    {
         |                        "id": "1",
         |                        "bodyValues": {
         |                            "3": {
         |                                "value": "/blabla/\\n*bloblo*\\n",
         |                                "isEncodingProblem": false,
         |                                "isTruncated": false
         |                            },
         |                            "4": {
         |                                "value": "<i>blabla</i>\\n<b>bloblo</b>\\n",
         |                                "isEncodingProblem": false,
         |                                "isTruncated": false
         |                            },
         |                            "5": {
         |                                "value": "inline attachment\\n",
         |                                "isEncodingProblem": false,
         |                                "isTruncated": false
         |                            }
         |                        }
         |                    }
         |                ],
         |                "notFound": []
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def bodyValuesShouldBeEmptyWithoutFetch(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoader.getSystemResourceAsStream("eml/multipart_complex.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}"],
         |      "properties":["bodyValues"]
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |                    {
         |                        "id": "${messageId.serialize()}",
         |                        "bodyValues": {}
         |                    }
         |                ],
         |                "notFound": []
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }


  @Test
  def attachmentsForSimpleMultipart(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoader.getSystemResourceAsStream("eml/multipart_simple.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}"],
         |      "properties":["attachments"]
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |                    {
         |                        "id": "${messageId.serialize()}",
         |                        "attachments": [
         |                            {
         |                                "partId": "3",
         |                                "blobId": "${messageId.serialize()}_3",
         |                                "size": 519,
         |                                "name": "text1",
         |                                "type": "text/plain",
         |                                "charset": "UTF-8",
         |                                "disposition": "attachment"
         |                            },
         |                            {
         |                                "partId": "4",
         |                                "blobId": "${messageId.serialize()}_4",
         |                                "size": 694,
         |                                "name": "text2",
         |                                "type": "application/vnd.ms-publisher",
         |                                "charset": "us-ascii",
         |                                "disposition": "attachment"
         |                            },
         |                            {
         |                                "partId": "5",
         |                                "blobId": "${messageId.serialize()}_5",
         |                                "size": 713,
         |                                "name": "text3",
         |                                "type": "text/plain",
         |                                "charset": "UTF-8",
         |                                "disposition": "attachment"
         |                            }
         |                        ]
         |                    }
         |                ],
         |                "notFound": []
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def attachmentsForComplexMultipart(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoader.getSystemResourceAsStream("eml/multipart_complex.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize()}"],
         |      "properties":["attachments"]
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |                    {
         |                        "id": "${messageId.serialize()}",
         |                        "attachments": [
         |                            {
         |                                "partId": "5",
         |                                "blobId": "${messageId.serialize()}_5",
         |                                "size": 249,
         |                                "name": "avertissement.txt",
         |                                "type": "text/plain",
         |                                "charset": "iso-8859-1",
         |                                "disposition": "inline",
         |                                "cid": "14672787885774e5c4d4cee471352039@linagora.com"
         |                            }
         |                        ]
         |                    }
         |                ],
         |                "notFound": []
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def emailGetShouldReturnUnparsedHeaders(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB);
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val alicePath = MailboxPath.inbox(ALICE);
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(alicePath)
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(ANDRE.asString())
      .setFrom(ANDRE.asString())
      .setSubject("World domination \r\n" +
        " and this is also part of the header")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, bobPath, AppendCommand.from(message))
      .getMessageId

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [[
               |     "Email/get",
               |     {
               |       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |       "ids": ["${messageId.serialize()}"],
               |       "properties": ["headers"]
               |     },
               |     "c1"]]
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
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [[
         |            "Email/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [{
         |                    "id": "${messageId.serialize()}",
         |                    "headers": [
         |                      {"name":"MIME-Version","value":" 1.0"},
         |                      {"name":"Subject","value":" =?US-ASCII?Q?World_domination_=0D=0A_and_thi?=\\r\\n =?US-ASCII?Q?s_is_also_part_of_the_header?="},
         |                      {"name":"Sender","value":" andre@domain.tld"},
         |                      {"name":"From","value":" andre@domain.tld"},
         |                      {"name":"Content-Type","value":" text/plain; charset=UTF-8"}
         |                    ]
         |                }],
         |                "notFound": []
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }
}
