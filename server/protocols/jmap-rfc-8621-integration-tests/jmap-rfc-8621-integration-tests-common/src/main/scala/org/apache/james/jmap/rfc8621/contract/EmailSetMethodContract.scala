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

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.Date

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import javax.mail.Flags
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.draft.{JmapGuiceProbe, MessageIdProbe}
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.FlagsBuilder
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.MailboxACL.Right
import org.apache.james.mailbox.model.{ComposedMessageId, MailboxACL, MailboxConstants, MailboxId, MailboxPath, MessageId}
import org.apache.james.mailbox.probe.MailboxProbe
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.{ACLProbeImpl, MailboxProbeImpl}
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

import scala.jdk.CollectionConverters._

trait EmailSetMethodContract {
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

  def randomMessageId: MessageId

  @Test
  def shouldResetKeywords(server: GuiceJamesServer): Unit = {
    val message: Message = Fixture.createTestMessage

    val flags: Flags = new Flags(Flags.Flag.ANSWERED)

    val bobPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobPath, AppendCommand.builder()
      .withFlags(flags)
      .build(message))
      .getMessageId

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "update": {
         |        "${messageId.serialize}":{
         |          "keywords": {
         |             "music": true
         |          }
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |     {
         |       "accountId": "$ACCOUNT_ID",
         |       "ids": ["${messageId.serialize}"],
         |       "properties": ["keywords"]
         |     },
         |     "c2"]]
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
      .inPath("methodResponses[1][1].list[0]")
      .isEqualTo(String.format(
        """{
          |   "id":"%s",
          |   "keywords": {
          |     "music": true
          |   }
          |}
      """.stripMargin, messageId.serialize))
  }

  @Test
  def shouldNotResetKeywordWhenFalseValue(server: GuiceJamesServer): Unit = {
    val message: Message = Fixture.createTestMessage

    val flags: Flags = new Flags(Flags.Flag.ANSWERED)

    val bobPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobPath, AppendCommand.builder()
      .withFlags(flags)
      .build(message))
      .getMessageId

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "update": {
         |        "${messageId.serialize}":{
         |          "keywords": {
         |             "music": true,
         |             "movie": false
         |          }
         |        }
         |      }
         |    }, "c1"]]
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
      .inPath(s"methodResponses[0][1].notUpdated.${messageId.serialize}")
      .isEqualTo(
        """|{
          |   "type":"invalidPatch",
          |   "description": "Message 1 update is invalid: List((,List(JsonValidationError(List(Value associated with keywords is invalid: List((,List(JsonValidationError(List(keyword value can only be true),ArraySeq()))))),ArraySeq()))))"
          |}""".stripMargin)
  }

  @Test
  def shouldNotResetKeywordWhenInvalidKeyword(server: GuiceJamesServer): Unit = {
    val message: Message = Fixture.createTestMessage

    val flags: Flags = new Flags(Flags.Flag.ANSWERED)

    val bobPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobPath, AppendCommand.builder()
      .withFlags(flags)
      .build(message))
      .getMessageId

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "update": {
         |        "${messageId.serialize}":{
         |          "keywords": {
         |             "mus*c": true
         |          }
         |        }
         |      }
         |    }, "c1"]]
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
      .inPath(s"methodResponses[0][1].notUpdated.${messageId.serialize}")
      .isEqualTo(
        """|{
           |   "type":"invalidPatch",
           |   "description": "Message 1 update is invalid: List((,List(JsonValidationError(List(Value associated with keywords is invalid: List((,List(JsonValidationError(List(FlagName must not be null or empty, must have length form 1-255,must not contain characters with hex from '\\u0000' to '\\u00019' or {'(' ')' '{' ']' '%' '*' '\"' '\\'} ),ArraySeq()))))),ArraySeq()))))"
           |}""".stripMargin)
  }

  @ParameterizedTest
  @ValueSource(strings = Array(
    "$Recent",
    "$Deleted"
  ))
  def shouldNotResetNonExposedKeyword(unexposedKeyword: String, server: GuiceJamesServer): Unit = {
    val message: Message = Fixture.createTestMessage

    val flags: Flags = new Flags(Flags.Flag.ANSWERED)

    val bobPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobPath, AppendCommand.builder()
      .withFlags(flags)
      .build(message))
      .getMessageId

    val request = String.format(
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "update": {
         |        "${messageId.serialize}":{
         |          "keywords": {
         |             "music": true,
         |             "$unexposedKeyword": true
         |          }
         |        }
         |      }
         |    }, "c1"]]
         |}""".stripMargin)

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
      .inPath("methodResponses[0][1].notUpdated")
      .isEqualTo(
        s"""{
           |  "${messageId.serialize}":{
           |      "type":"invalidPatch",
           |      "description":"Message 1 update is invalid: List((,List(JsonValidationError(List(Value associated with keywords is invalid: List((,List(JsonValidationError(List(Does not allow to update 'Deleted' or 'Recent' flag),ArraySeq()))))),ArraySeq()))))"}
           |  }
           |}"""
          .stripMargin)
  }

  @Test
  def shouldKeepUnexposedKeywordWhenResetKeywords(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB.asString(), "mailbox");

    val bobPath = MailboxPath.forUser(BOB, "mailbox")
    val message: ComposedMessageId = mailboxProbe.appendMessage(BOB.asString, bobPath,
      new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)),
      new Date, false, new Flags(Flags.Flag.DELETED))

    val messageId: String = message.getMessageId.serialize

    val request = String.format(s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "update": {
         |        "$messageId":{
         |          "keywords": {
         |             "music": true
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

    val flags: List[Flags] = server.getProbe(classOf[MessageIdProbe]).getMessages(message.getMessageId, BOB).asScala.map(m => m.getFlags).toList
    val expectedFlags: Flags  = FlagsBuilder.builder.add("music").add(Flags.Flag.DELETED).build

    assertThat(flags.asJava)
      .containsExactly(expectedFlags)
  }

  @Test
  def shouldResetKeywordsWhenNotDefault(server: GuiceJamesServer): Unit = {
    val message: Message = Fixture.createTestMessage

    val flags: Flags = new Flags(Flags.Flag.ANSWERED)

    val bobPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobPath, AppendCommand.builder()
      .withFlags(flags)
      .build(message))
      .getMessageId

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "update": {
         |        "${messageId.serialize}":{
         |          "keywords": {
         |             "music": true
         |          }
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |     {
         |       "accountId": "$ACCOUNT_ID",
         |       "ids": ["${messageId.serialize}"],
         |       "properties": ["keywords"]
         |     },
         |     "c2"]]
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
      .inPath("methodResponses[1][1].list[0]")
      .isEqualTo(String.format(
        """{
          |   "id":"%s",
          |   "keywords": {
          |             "music": true
          |    }
          |}
      """.stripMargin, messageId.serialize))
  }

  @Test
  def shouldNotResetKeywordWhenInvalidMessageId(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "update": {
         |        "invalid":{
         |          "keywords": {
         |             "music": true
         |          }
         |        }
         |      }
         |    }, "c1"]]
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
     .inPath("methodResponses[0][1].notUpdated")
     .isEqualTo("""{
        | "invalid": {
        |     "type":"invalidPatch",
        |     "description":"Message invalid update is invalid: For input string: \"invalid\""
        | }
        |}""".stripMargin)
  }

  @Test
  def shouldNotResetKeywordWhenMessageIdNonExisted(server: GuiceJamesServer): Unit = {
    val invalidMessageId: MessageId = randomMessageId

    val bobPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    val request = s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "update": {
         |        "${invalidMessageId.serialize}":{
         |          "keywords": {
         |             "music": true
         |          }
         |        }
         |      }
         |    }, "c1"]]
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
      .inPath("methodResponses[0][1].notUpdated")
      .isEqualTo(s"""{
        | "${invalidMessageId.serialize}": {
        |     "type":"notFound",
        |     "description":"Cannot find message with messageId: ${invalidMessageId.serialize}"
        | }
        |}""".stripMargin)
  }

  @Test
  def shouldNotUpdateInDelegatedMailboxesWhenReadOnly(server: GuiceJamesServer): Unit = {
    val andreMailbox: String = "andrecustom"
    val andrePath = MailboxPath.forUser(ANDRE, andreMailbox)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andrePath)
    val message: Message = Message.Builder
      .of
      .setSender(BOB.asString())
      .setFrom(ANDRE.asString())
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString, andrePath, AppendCommand.from(message))
      .getMessageId
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andrePath, BOB.asString, MailboxACL.Rfc4314Rights.of(Set(Right.Read, Right.Lookup).asJava))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |  ["Email/set",
         |    {
         |      "accountId": "$ACCOUNT_ID",
         |      "update": {
         |        "${messageId.serialize}":{
         |          "keywords": {
         |             "music": true
         |          }
         |        }
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
      .inPath("methodResponses[0][1].notUpdated")
      .isEqualTo(
        s"""{
           |  "${messageId.serialize}":{
           |     "type": "notFound",
           |     "description": "Mailbox not found"
           |  }
           |}""".stripMargin)
  }

  @Test
  def shouldResetFlagsInDelegatedMailboxesWhenHadAtLeastWriteRight(server: GuiceJamesServer): Unit = {
    val andreMailbox: String = "andrecustom"
    val andrePath = MailboxPath.forUser(ANDRE, andreMailbox)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andrePath)
    val message: Message = Message.Builder
      .of
      .setSender(BOB.asString())
      .setFrom(ANDRE.asString())
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString, andrePath, AppendCommand.from(message))
      .getMessageId
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andrePath, BOB.asString, MailboxACL.Rfc4314Rights.of(Set(Right.Write, Right.Read).asJava))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |  ["Email/set",
         |    {
         |      "accountId": "$ACCOUNT_ID",
         |      "ids": ["${messageId.serialize}"],
         |      "update": {
         |        "${messageId.serialize}":{
         |          "keywords": {
         |             "music": true
         |          }
         |        }
         |      }
         |    },
         |    "c1"],
         |    ["Email/get",
         |     {
         |       "accountId": "$ACCOUNT_ID",
         |       "ids": ["${messageId.serialize}"],
         |       "properties": ["keywords"]
         |     },
         |     "c2"]]
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
      .inPath("methodResponses[1][1].list[0]")
      .isEqualTo(String.format(
        """{
          |   "id":"%s",
          |   "keywords": {
          |     "music":true
          |   }
          |}
      """.stripMargin, messageId.serialize))
  }

  @Test
  def emailSetShouldPartiallyUpdateKeywords(server: GuiceJamesServer): Unit = {
    val message: Message = Fixture.createTestMessage

    val flags: Flags = FlagsBuilder.builder()
      .add(Flags.Flag.ANSWERED)
      .add(Flags.Flag.SEEN)
      .build()

    val bobPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobPath, AppendCommand.builder()
      .withFlags(flags)
      .build(message))
      .getMessageId

    val request = String.format(
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "update": {
         |        "${messageId.serialize}":{
         |          "keywords/music": true,
         |          "keywords/%s": null
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |     {
         |       "accountId": "$ACCOUNT_ID",
         |       "ids": ["${messageId.serialize}"],
         |       "properties": ["keywords"]
         |     },
         |     "c2"]]
         |}""".stripMargin, "$Seen")

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
      .inPath("methodResponses[0][1].updated")
      .isEqualTo(s"""{
          |  "${messageId.serialize}": null
          |}
      """.stripMargin)
    assertThatJson(response)
      .inPath("methodResponses[1][1].list[0]")
      .isEqualTo(String.format(
        """{
          |   "id":"%s",
          |   "keywords": {
          |       "$answered": true,
          |       "music": true
          |    }
          |}
      """.stripMargin, messageId.serialize))
  }

  @Test
  def rangeFlagsAdditionShouldUpdateStoredFlags(server: GuiceJamesServer): Unit = {
    val message: Message = Fixture.createTestMessage

    val flags: Flags = FlagsBuilder.builder()
      .add(Flags.Flag.ANSWERED)
      .build()

    val bobPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobPath, AppendCommand.builder()
      .withFlags(flags).build(message)).getMessageId
    val messageId2: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobPath, AppendCommand.builder()
      .withFlags(flags).build(message)).getMessageId
    val messageId3: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobPath, AppendCommand.builder()
      .withFlags(flags).build(message)).getMessageId
    val messageId4: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobPath, AppendCommand.builder()
      .withFlags(flags).build(message)).getMessageId

    val request = String.format(
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "update": {
         |        "${messageId1.serialize}":{
         |          "keywords/music": true
         |        },
         |        "${messageId2.serialize}":{
         |          "keywords/music": true
         |        },
         |        "${messageId3.serialize}":{
         |          "keywords/music": true
         |        },
         |        "${messageId4.serialize}":{
         |          "keywords/music": true
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |     {
         |       "accountId": "$ACCOUNT_ID",
         |       "ids": ["${messageId1.serialize}", "${messageId2.serialize}", "${messageId3.serialize}", "${messageId4.serialize}"],
         |       "properties": ["keywords"]
         |     },
         |     "c2"]]
         |}""".stripMargin, "$Seen")

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
      .inPath("methodResponses[0][1].updated")
      .isEqualTo(s"""{
          |  "${messageId1.serialize}": null,
          |  "${messageId2.serialize}": null,
          |  "${messageId3.serialize}": null,
          |  "${messageId4.serialize}": null
          |}
      """.stripMargin)
    assertThatJson(response)
      .inPath("methodResponses[1][1].list")
      .isEqualTo(String.format(
        """[
          |{
          |   "id":"%s",
          |   "keywords": {
          |       "$answered": true,
          |       "music": true
          |    }
          |},
          |{
          |   "id":"%s",
          |   "keywords": {
          |       "$answered": true,
          |       "music": true
          |    }
          |},
          |{
          |   "id":"%s",
          |   "keywords": {
          |       "$answered": true,
          |       "music": true
          |    }
          |},
          |{
          |   "id":"%s",
          |   "keywords": {
          |       "$answered": true,
          |       "music": true
          |    }
          |}
          |]
      """.stripMargin, messageId1.serialize, messageId2.serialize, messageId3.serialize, messageId4.serialize))
  }

  @Test
  def rangeFlagsRemovalShouldUpdateStoredFlags(server: GuiceJamesServer): Unit = {
    val message: Message = Fixture.createTestMessage

    val flags: Flags = FlagsBuilder.builder()
      .add(Flags.Flag.ANSWERED)
      .add("music")
      .build()

    val bobPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobPath, AppendCommand.builder()
      .withFlags(flags).build(message)).getMessageId
    val messageId2: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobPath, AppendCommand.builder()
      .withFlags(flags).build(message)).getMessageId
    val messageId3: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobPath, AppendCommand.builder()
      .withFlags(flags).build(message)).getMessageId
    val messageId4: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobPath, AppendCommand.builder()
      .withFlags(flags).build(message)).getMessageId

    val request = String.format(
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "update": {
         |        "${messageId1.serialize}":{
         |          "keywords/music": null
         |        },
         |        "${messageId2.serialize}":{
         |          "keywords/music": null
         |        },
         |        "${messageId3.serialize}":{
         |          "keywords/music": null
         |        },
         |        "${messageId4.serialize}":{
         |          "keywords/music": null
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |     {
         |       "accountId": "$ACCOUNT_ID",
         |       "ids": ["${messageId1.serialize}", "${messageId2.serialize}", "${messageId3.serialize}", "${messageId4.serialize}"],
         |       "properties": ["keywords"]
         |     },
         |     "c2"]]
         |}""".stripMargin, "$Seen")

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
      .inPath("methodResponses[0][1].updated")
      .isEqualTo(s"""{
          |  "${messageId1.serialize}": null,
          |  "${messageId2.serialize}": null,
          |  "${messageId3.serialize}": null,
          |  "${messageId4.serialize}": null
          |}
      """.stripMargin)
    assertThatJson(response)
      .inPath("methodResponses[1][1].list")
      .isEqualTo(String.format(
        """[
          |{
          |   "id":"%s",
          |   "keywords": {
          |       "$answered": true
          |    }
          |},
          |{
          |   "id":"%s",
          |   "keywords": {
          |       "$answered": true
          |    }
          |},
          |{
          |   "id":"%s",
          |   "keywords": {
          |       "$answered": true
          |    }
          |},
          |{
          |   "id":"%s",
          |   "keywords": {
          |       "$answered": true
          |    }
          |}
          |]
      """.stripMargin, messageId1.serialize, messageId2.serialize, messageId3.serialize, messageId4.serialize))
  }

  @Test
  def rangeMoveShouldUpdateMailboxId(server: GuiceJamesServer): Unit = {
    val message: Message = Fixture.createTestMessage

    val flags: Flags = FlagsBuilder.builder()
      .add(Flags.Flag.ANSWERED)
      .add("music")
      .build()

    val bobPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val newId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "other"))
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobPath, AppendCommand.builder()
      .withFlags(flags).build(message)).getMessageId
    val messageId2: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobPath, AppendCommand.builder()
      .withFlags(flags).build(message)).getMessageId
    val messageId3: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobPath, AppendCommand.builder()
      .withFlags(flags).build(message)).getMessageId
    val messageId4: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobPath, AppendCommand.builder()
      .withFlags(flags).build(message)).getMessageId

    val request = String.format(
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "update": {
         |        "${messageId1.serialize}":{
         |          "mailboxIds": { "${newId.serialize()}" : true}
         |        },
         |        "${messageId2.serialize}":{
         |          "mailboxIds": { "${newId.serialize()}" : true}
         |        },
         |        "${messageId3.serialize}":{
         |          "mailboxIds": { "${newId.serialize()}" : true}
         |        },
         |        "${messageId4.serialize}":{
         |          "mailboxIds": { "${newId.serialize()}" : true}
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |     {
         |       "accountId": "$ACCOUNT_ID",
         |       "ids": ["${messageId1.serialize}", "${messageId2.serialize}", "${messageId3.serialize}", "${messageId4.serialize}"],
         |       "properties": ["mailboxIds"]
         |     },
         |     "c2"]]
         |}""".stripMargin, "$Seen")

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
      .inPath("methodResponses[0][1].updated")
      .isEqualTo(s"""{
          |  "${messageId1.serialize}": null,
          |  "${messageId2.serialize}": null,
          |  "${messageId3.serialize}": null,
          |  "${messageId4.serialize}": null
          |}
      """.stripMargin)
    assertThatJson(response)
      .inPath("methodResponses[1][1].list")
      .isEqualTo(s"""[
          |{
          |   "id":"${messageId1.serialize}",
          |   "mailboxIds": {
          |       "${newId.serialize}": true
          |    }
          |},
          |{
          |   "id":"${messageId2.serialize}",
          |   "mailboxIds": {
          |       "${newId.serialize}": true
          |    }
          |},
          |{
          |   "id":"${messageId3.serialize}",
          |   "mailboxIds": {
          |       "${newId.serialize}": true
          |    }
          |},
          |{
          |   "id":"${messageId4.serialize}",
          |   "mailboxIds": {
          |       "${newId.serialize}": true
          |    }
          |}
          |]
      """.stripMargin)
  }

  @Test
  def emailSetShouldRejectPartiallyUpdateAndResetKeywordsAtTheSameTime(server: GuiceJamesServer): Unit = {
    val message: Message = Fixture.createTestMessage

    val flags: Flags = FlagsBuilder.builder()
      .add(Flags.Flag.ANSWERED)
      .add(Flags.Flag.SEEN)
      .build()

    val bobPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobPath, AppendCommand.builder()
      .withFlags(flags)
      .build(message))
      .getMessageId

    val request = String.format(
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "update": {
         |        "${messageId.serialize}":{
         |          "keywords/music": true,
         |          "keywords/%s": null,
         |          "keywords": {
         |             "movie": true
         |          }
         |        }
         |      }
         |    }, "c1"]]
         |}""".stripMargin, "$Seen")

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
      .inPath("methodResponses[0][1].notUpdated")
      .isEqualTo(s"""{
          |  "${messageId.serialize}": {
          |     "type": "invalidPatch",
          |     "description": "Message 1 update is invalid: Partial update and reset specified for keywords"
          |   }
          |}
      """.stripMargin)
  }

  @Test
  def emailSetShouldRejectPartiallyUpdateWhenInvalidKeyword(server: GuiceJamesServer): Unit = {
    val message: Message = Fixture.createTestMessage

    val flags: Flags = new Flags(Flags.Flag.ANSWERED)

    val bobPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobPath, AppendCommand.builder()
      .withFlags(flags)
      .build(message))
      .getMessageId

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "update": {
         |        "${messageId.serialize}":{
         |          "keywords/mus*c": true
         |        }
         |      }
         |    }, "c1"]]
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
      .inPath(s"methodResponses[0][1].notUpdated.${messageId.serialize}")
      .isEqualTo(
        """|{
           |   "type":"invalidPatch",
           |   "description": "Message 1 update is invalid: List((,List(JsonValidationError(List(keywords/mus*c is an invalid entry in an Email/set update patch: FlagName must not be null or empty, must have length form 1-255,must not contain characters with hex from '\\u0000' to '\\u00019' or {'(' ')' '{' ']' '%' '*' '\"' '\\'} ),ArraySeq()))))"}"
           |}""".stripMargin)
  }

  @Test
  def emailSetShouldRejectPartiallyUpdateWhenFalseValue(server: GuiceJamesServer): Unit = {
    val message: Message = Fixture.createTestMessage

    val flags: Flags = new Flags(Flags.Flag.ANSWERED)

    val bobPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobPath, AppendCommand.builder()
      .withFlags(flags)
      .build(message))
      .getMessageId

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "update": {
         |        "${messageId.serialize}":{
         |           "keywords/music": true,
         |           "keywords/movie": false
         |        }
         |      }
         |    }, "c1"]]
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
      .inPath(s"methodResponses[0][1].notUpdated.${messageId.serialize}")
      .isEqualTo(
        """|{
          |   "type":"invalidPatch",
          |   "description": "Message 1 update is invalid: List((,List(JsonValidationError(List(Value associated with keywords/movie is invalid: Keywords partial updates requires a JsBoolean(true) (set) or a JsNull (unset)),ArraySeq()))))"
          |}""".stripMargin)
  }

  @ParameterizedTest
  @ValueSource(strings = Array(
    "$Recent",
    "$Deleted"
  ))
  def partialUpdateShouldRejectNonExposedKeyword(unexposedKeyword: String, server: GuiceJamesServer): Unit = {
    val message: Message = Fixture.createTestMessage

    val flags: Flags = new Flags(Flags.Flag.ANSWERED)

    val bobPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobPath, AppendCommand.builder()
      .withFlags(flags)
      .build(message))
      .getMessageId

    val request = String.format(
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "update": {
         |        "${messageId.serialize}":{
         |           "keywords/music": true,
         |           "keywords/$unexposedKeyword": true
         |        }
         |      }
         |    }, "c1"]]
         |}""".stripMargin)

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
      .inPath("methodResponses[0][1].notUpdated")
      .isEqualTo(
        s"""{
           |  "${messageId.serialize}":{
           |      "type":"invalidPatch",
           |      "description":"Message 1 update is invalid: List((,List(JsonValidationError(List(Does not allow to update 'Deleted' or 'Recent' flag),ArraySeq()))))"}
           |  }
           |}"""
          .stripMargin)
  }

  @Test
  def emailSetShouldDestroyEmail(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxPath.inbox(BOB))
    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.from(
          buildTestMessage))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "destroy": ["${messageId.serialize}"]
         |    }, "c1"],
         |    ["Email/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize}"]
         |    }, "c2"]
         |  ]
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
         |      ["Email/set", {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "newState": "000001",
         |        "destroyed": ["${messageId.serialize}"]
         |      }, "c1"],
         |      ["Email/get", {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "state": "000001",
         |        "list": [],
         |        "notFound": ["${messageId.serialize}"]
         |      }, "c2"]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def emailSetDestroyShouldFailWhenInvalidMessageId(server: GuiceJamesServer): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "destroy": ["invalid"]
         |    }, "c1"]]
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
         |      ["Email/set", {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "newState": "000001",
         |        "notDestroyed": {
         |          "invalid": {
         |            "type": "invalidArguments",
         |            "description": "invalid is not a messageId: For input string: \\"invalid\\""
         |          }
         |        }
         |      }, "c1"]]
         |}""".stripMargin)
  }

  @Test
  def emailSetDestroyShouldFailWhenMessageIdNotFound(server: GuiceJamesServer): Unit = {
    val messageId = randomMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "destroy": ["${messageId.serialize}"]
         |    }, "c1"]]
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
         |      ["Email/set", {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "newState": "000001",
         |        "notDestroyed": {
         |          "${messageId.serialize}": {
         |            "type": "notFound",
         |            "description": "Cannot find message with messageId: ${messageId.serialize}"
         |          }
         |        }
         |      }, "c1"]]
         |}""".stripMargin)
  }

  @Test
  def emailSetDestroyShouldFailWhenMailDoesNotBelongToUser(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxPath.inbox(ANDRE))

    val messageId: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString, MailboxPath.inbox(ANDRE),
        AppendCommand.from(
          buildTestMessage))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "destroy": ["${messageId.serialize}"]
         |    }, "c1"]]
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
         |      ["Email/set", {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "newState": "000001",
         |        "notDestroyed": {
         |          "${messageId.serialize}": {
         |            "type": "notFound",
         |            "description": "Cannot find message with messageId: ${messageId.serialize}"
         |          }
         |        }
         |      }, "c1"]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def emailSetDestroyShouldFailWhenForbidden(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])

    val andreMailbox: String = "andrecustom"
    val path = MailboxPath.forUser(ANDRE, andreMailbox)
    mailboxProbe.createMailbox(path)

    val messageId: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString, path,
        AppendCommand.from(
          buildTestMessage))
      .getMessageId

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Read))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "destroy": ["${messageId.serialize}"]
         |    }, "c1"]]
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
         |      ["Email/set", {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "newState": "000001",
         |        "notDestroyed": {
         |          "${messageId.serialize}": {
         |            "type": "notFound",
         |            "description": "Cannot find message with messageId: ${messageId.serialize}"
         |          }
         |        }
         |      }, "c1"]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def emailSetDestroyShouldDestroyEmailWhenShareeHasDeleteRight(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])

    val andreMailbox: String = "andrecustom"
    val path = MailboxPath.forUser(ANDRE, andreMailbox)
    mailboxProbe.createMailbox(path)

    val messageId: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString, path,
        AppendCommand.from(
          buildTestMessage))
      .getMessageId

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Read, Right.DeleteMessages))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "destroy": ["${messageId.serialize}"]
         |    }, "c1"],
         |    ["Email/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize}"]
         |    }, "c2"]
         |  ]
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
         |      ["Email/set", {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "newState": "000001",
         |        "destroyed": ["${messageId.serialize}"]
         |      }, "c1"],
         |      ["Email/get", {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "state": "000001",
         |        "list": [],
         |        "notFound": ["${messageId.serialize}"]
         |      }, "c2"]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def emailSetDestroyShouldDestroyEmailWhenMovedIntoAnotherMailbox(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])

    val andreMailbox: String = "andrecustom"
    val andrePath = MailboxPath.forUser(ANDRE, andreMailbox)
    val bobPath = MailboxPath.inbox(BOB)
    mailboxProbe.createMailbox(andrePath)
    val mailboxId: MailboxId = mailboxProbe.createMailbox(bobPath)

    val messageId: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString, andrePath,
        AppendCommand.from(
          buildTestMessage))
      .getMessageId

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andrePath, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Insert))

    server.getProbe(classOf[JmapGuiceProbe])
      .setInMailboxes(messageId, BOB, mailboxId)

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "destroy": ["${messageId.serialize}"]
         |    }, "c1"],
         |    ["Email/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize}"]
         |    }, "c2"]
         |  ]
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
         |      ["Email/set", {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "newState": "000001",
         |        "destroyed": ["${messageId.serialize}"]
         |      }, "c1"],
         |      ["Email/get", {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "state": "000001",
         |        "list": [],
         |        "notFound": ["${messageId.serialize}"]
         |      }, "c2"]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def mailboxIdsShouldSupportPartialUpdates(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId1: MailboxId = mailboxProbe.createMailbox(MailboxPath.inbox(BOB))
    val mailboxId2: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "other"))
    val mailboxId3: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "yet-another"))

    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.from(
          buildTestMessage))
      .getMessageId

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "${messageId.serialize}": {
         |          "mailboxIds": {
         |            "${mailboxId1.serialize}": true,
         |            "${mailboxId2.serialize}": true
         |          }
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "${messageId.serialize}": {
         |           "mailboxIds/${mailboxId1.serialize}": null,
         |           "mailboxIds/${mailboxId3.serialize}": true
         |         }
         |      }
         |    }, "c2"],
         |     ["Email/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize}"],
         |      "properties": ["mailboxIds"]
         |    }, "c3"]
         |  ]
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
      .inPath("methodResponses[2][1].list[0]")
      .isEqualTo(
      s"""{
         |  "id": "${messageId.serialize}",
         |  "mailboxIds": {"${mailboxId2.serialize}":true, "${mailboxId3.serialize}":true}
         |}""".stripMargin)
  }

  @Test
  def invalidPatchPropertyShouldFail(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId1: MailboxId = mailboxProbe.createMailbox(MailboxPath.inbox(BOB))

    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.from(
          buildTestMessage))
      .getMessageId

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "${messageId.serialize}": {
         |          "invalid": "value"
         |        }
         |      }
         |    }, "c1"]
         |  ]
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
      .inPath("methodResponses[0][1].notUpdated")
      .isEqualTo(
      s"""{
         |  "1": {
         |    "type": "invalidPatch",
         |    "description": "Message 1 update is invalid: List((,List(JsonValidationError(List(invalid is an invalid entry in an Email/set update patch),ArraySeq()))))"
         |  }
         |}""".stripMargin)
  }

  @Test
  def invalidMailboxPartialUpdatePropertyShouldFail(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId1: MailboxId = mailboxProbe.createMailbox(MailboxPath.inbox(BOB))

    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.from(
          buildTestMessage))
      .getMessageId

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "${messageId.serialize}": {
         |          "mailboxIds/invalid": "value"
         |        }
         |      }
         |    }, "c1"]
         |  ]
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
      .inPath("methodResponses[0][1].notUpdated")
      .isEqualTo(
      s"""{
         |  "1": {
         |    "type": "invalidPatch",
         |    "description": "Message 1 update is invalid: List((,List(JsonValidationError(List(mailboxIds/invalid is an invalid entry in an Email/set update patch: For input string: \\"invalid\\"),ArraySeq()))))"
         |  }
         |}""".stripMargin)
  }

  @Test
  def invalidMailboxPartialUpdateValueShouldFail(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId1: MailboxId = mailboxProbe.createMailbox(MailboxPath.inbox(BOB))

    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.from(
          buildTestMessage))
      .getMessageId

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "${messageId.serialize}": {
         |          "mailboxIds/${mailboxId1.serialize}": false
         |        }
         |      }
         |    }, "c1"]
         |  ]
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
      .inPath("methodResponses[0][1].notUpdated")
      .isEqualTo(
      s"""{
         |  "1": {
         |    "type": "invalidPatch",
         |    "description": "Message 1 update is invalid: List((,List(JsonValidationError(List(Value associated with mailboxIds/1 is invalid: MailboxId partial updates requires a JsBoolean(true) (set) or a JsNull (unset)),ArraySeq()))))"
         |  }
         |}""".stripMargin)
  }

  @Test
  def mixingResetAndPartialUpdatesShouldFail(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId1: MailboxId = mailboxProbe.createMailbox(MailboxPath.inbox(BOB))

    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.from(
          buildTestMessage))
      .getMessageId

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "${messageId.serialize}": {
         |          "mailboxIds/${mailboxId1.serialize}": true,
         |          "mailboxIds" : {
         |            "${mailboxId1.serialize}": true
         |          }
         |        }
         |      }
         |    }, "c1"]
         |  ]
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
      .inPath("methodResponses[0][1].notUpdated")
      .isEqualTo(
      s"""{
         |  "1": {
         |    "type": "invalidPatch",
         |    "description": "Message 1 update is invalid: Partial update and reset specified for mailboxIds"
         |  }
         |}""".stripMargin)
  }

  @Test
  def emailSetDestroySuccessAndFailureCanBeMixed(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxPath.inbox(BOB))
    val path = MailboxPath.inbox(BOB)

    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString, path,
        AppendCommand.from(
          buildTestMessage))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "destroy": [
         |        "${messageId.serialize}",
         |        "invalid"
         |      ]
         |    }, "c1"]]
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
         |      ["Email/set", {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "newState": "000001",
         |        "destroyed": ["${messageId.serialize}"],
         |        "notDestroyed": {
         |          "invalid": {
         |            "type": "invalidArguments",
         |            "description": "invalid is not a messageId: For input string: \\"invalid\\""
         |          }
         |        }
         |      }, "c1"]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def emailSetMailboxIdResetShouldSucceed(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxPath.inbox(BOB))
    val mailboxId2: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "other"))
    val mailboxId3: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "other2"))

    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.from(
          buildTestMessage))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "${messageId.serialize}": {
         |          "mailboxIds": {
         |            "${mailboxId2.serialize}": true,
         |            "${mailboxId3.serialize}": true
         |          }
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize}"],
         |      "properties":["mailboxIds"]
         |    }, "c2"]
         |  ]
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
         |      ["Email/set", {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "newState": "000001",
         |        "updated": {
         |          "${messageId.serialize}": null
         |        }
         |      }, "c1"],
         |      ["Email/get",{
         |        "accountId":"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "state":"000001",
         |        "list":[
         |          {
         |            "id":"${messageId.serialize}",
         |            "mailboxIds":{
         |              "${mailboxId2.serialize}":true,
         |              "${mailboxId3.serialize}":true
         |            }
         |          }
         |        ],
         |        "notFound":[]
         |        },"c2"]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def emailSetMailboxIdResetShouldSucceedForMultipleMessages(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxPath.inbox(BOB))
    val mailboxId2: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "other"))

    val messageId1: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.from(
          buildTestMessage))
      .getMessageId

    val messageId2: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.from(
          buildTestMessage))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "${messageId1.serialize}": {
         |          "mailboxIds": {
         |            "${mailboxId2.serialize}": true
         |          }
         |        },
         |        "${messageId2.serialize}": {
         |          "mailboxIds": {
         |            "${mailboxId2.serialize}": true
         |          }
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId1.serialize}", "${messageId2.serialize}"],
         |      "properties":["mailboxIds"]
         |    }, "c2"]
         |  ]
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
         |    "methodResponses": [
         |      ["Email/set", {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "newState": "000001",
         |        "updated": {
         |          "${messageId1.serialize}": null,
         |          "${messageId2.serialize}": null
         |        }
         |      }, "c1"],
         |      ["Email/get", {
         |        "accountId":"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "state":"000001",
         |        "list":[
         |          {
         |            "id":"${messageId1.serialize}",
         |            "mailboxIds": {
         |              "${mailboxId2.serialize}":true
         |            }
         |          },
         |          {
         |            "id":"${messageId2.serialize}",
         |            "mailboxIds":{
         |              "${mailboxId2.serialize}":true
         |            }
         |          }
         |        ],
         |        "notFound":[]
         |        },"c2"]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def emailSetMailboxIdsResetShouldFailWhenNoRightsOnSourceMailbox(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId1: MailboxId = mailboxProbe.createMailbox(MailboxPath.inbox(BOB))

    val path = MailboxPath.forUser(ANDRE, "other")
    mailboxProbe.createMailbox(path)

    val messageId: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString, path,
        AppendCommand.from(buildTestMessage))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "${messageId.serialize}": {
         |          "mailboxIds": {
         |            "${mailboxId1.serialize}": true
         |          }
         |        }
         |      }
         |    }, "c1"]
         |  ]
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
           |    "methodResponses": [
           |        ["Email/set", {
           |          "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |          "newState": "000001",
           |          "notUpdated": {
           |            "1": {
           |              "type": "notFound",
           |              "description": "Cannot find message with messageId: 1"
           |            }
           |          }
           |        }, "c1"]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def emailSetMailboxIdsResetShouldFailWhenForbidden(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId1: MailboxId = mailboxProbe.createMailbox(MailboxPath.inbox(BOB))

    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.from(
          buildTestMessage))
      .getMessageId

    val path = MailboxPath.forUser(ANDRE, "other")
    val mailboxId2: MailboxId = mailboxProbe.createMailbox(path)

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Read))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "${messageId.serialize}": {
         |          "mailboxIds": {
         |            "${mailboxId2.serialize}": true
         |          }
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize}"],
         |      "properties":["mailboxIds"]
         |    }, "c2"]
         |  ]
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
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "newState": "000001",
           |        "notUpdated": {
           |          "${messageId.serialize}": {
           |            "type": "notFound",
           |            "description": "Mailbox not found"
           |          }
           |        }
           |      }, "c1"],
           |      ["Email/get", {
           |        "accountId":"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "state":"000001",
           |        "list":[
           |          {
           |            "id":"${messageId.serialize}",
           |            "mailboxIds": {
           |              "${mailboxId1.serialize}": true
           |            }
           |          }
           |        ],
           |        "notFound":[]
           |        },"c2"]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def emailSetMailboxIdsResetShouldFailWhenRemovingMessageFromSourceMailbox(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId1: MailboxId = mailboxProbe.createMailbox(MailboxPath.inbox(BOB))

    val path = MailboxPath.forUser(ANDRE, "other")
    val mailboxId2: MailboxId = mailboxProbe.createMailbox(path)

    val messageId: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString, path,
        AppendCommand.from(
          buildTestMessage))
      .getMessageId

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Read))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "${messageId.serialize}": {
         |          "mailboxIds": {
         |            "${mailboxId1.serialize}": true
         |          }
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize}"],
         |      "properties":["mailboxIds"]
         |    }, "c2"]
         |  ]
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
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "newState": "000001",
           |        "notUpdated": {
           |          "1": {
           |            "type": "notFound",
           |            "description": "Mailbox not found"
           |          }
           |        }
           |      }, "c1"],
           |      ["Email/get", {
           |        "accountId":"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "state":"000001",
           |        "list":[
           |          {
           |            "id":"${messageId.serialize}",
           |            "mailboxIds": {
           |              "${mailboxId2.serialize}": true
           |            }
           |          }
           |        ],
           |        "notFound":[]
           |        },"c2"]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def emailSetMailboxIdsResetShouldSucceedWhenCopyMessage(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId1: MailboxId = mailboxProbe.createMailbox(MailboxPath.inbox(BOB))

    val path = MailboxPath.forUser(ANDRE, "other")
    val mailboxId2: MailboxId = mailboxProbe.createMailbox(path)

    val messageId: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString, path,
        AppendCommand.from(
          buildTestMessage))
      .getMessageId

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Read))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "${messageId.serialize}": {
         |          "mailboxIds": {
         |            "${mailboxId1.serialize}": true,
         |            "${mailboxId2.serialize}": true
         |          }
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize}"],
         |      "properties":["mailboxIds"]
         |    }, "c2"]
         |  ]
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
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "newState": "000001",
           |        "updated": {
           |          "${messageId.serialize}": null
           |        }
           |      }, "c1"],
           |      ["Email/get", {
           |        "accountId":"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "state":"000001",
           |        "list":[
           |          {
           |            "id":"${messageId.serialize}",
           |            "mailboxIds": {
           |              "${mailboxId1.serialize}": true,
           |              "${mailboxId2.serialize}": true
           |            }
           |          }
           |        ],
           |        "notFound":[]
           |        },"c2"]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def emailSetMailboxIdsResetShouldSucceedWhenShareeHasRightOnTargetMailbox(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxPath.inbox(BOB))

    val path = MailboxPath.forUser(ANDRE, "other")
    val mailboxId2: MailboxId = mailboxProbe.createMailbox(path)

    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.from(
          buildTestMessage))
      .getMessageId

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Read, Right.Insert))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "${messageId.serialize}": {
         |          "mailboxIds": {
         |            "${mailboxId2.serialize}": true
         |          }
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["${messageId.serialize}"],
         |      "properties":["mailboxIds"]
         |    }, "c2"]
         |  ]
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
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "newState": "000001",
           |        "updated": {
           |          "${messageId.serialize}": null
           |        }
           |      }, "c1"],
           |      ["Email/get", {
           |        "accountId":"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "state":"000001",
           |        "list":[
           |          {
           |            "id":"${messageId.serialize}",
           |            "mailboxIds": {
           |              "${mailboxId2.serialize}": true
           |            }
           |          }
           |        ],
           |        "notFound":[]
           |        },"c2"]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def emailSetMailboxIdsResetShouldNotAffectMailboxIdFilter(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])

    val mailboxId1: MailboxId = mailboxProbe.createMailbox(MailboxPath.inbox(BOB))
    val path = MailboxPath.forUser(ANDRE, "other")
    val mailboxId2: MailboxId = mailboxProbe.createMailbox(path)

    val messageId: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString, path,
        AppendCommand.from(buildTestMessage))
      .getMessageId

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Read))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "${messageId.serialize}": {
         |          "mailboxIds": {
         |            "${mailboxId1.serialize}": true,
         |            "${mailboxId2.serialize}": true
         |          }
         |        }
         |      }
         |    }, "c1"]
         |  ]
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
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "newState": "000001",
           |        "updated": {
           |          "${messageId.serialize}": null
           |        }
           |      }, "c1"]
           |    ]
           |}""".stripMargin)

    assertThat(server.getProbe(classOf[MessageIdProbe]).getMessages(messageId, ANDRE)
        .stream()
        .map(message => message.getMailboxId))
      .containsExactly(mailboxId2)
  }

  @Test
  def emailSetMailboxIdsResetShouldFailWhenInvalidKey(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxPath.inbox(BOB))

    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.from(
          buildTestMessage))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "${messageId.serialize}": {
         |          "mailboxIds": {
         |            "invalid": true
         |          }
         |        }
         |      }
         |    }, "c1"]
         |  ]
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
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "newState": "000001",
           |        "notUpdated": {
           |          "${messageId.serialize}": {
           |            "type": "invalidPatch",
           |            "description": "Message ${messageId.serialize} update is invalid: List((,List(JsonValidationError(List(Value associated with mailboxIds is invalid: List((,List(JsonValidationError(List(For input string: \\"invalid\\"),ArraySeq()))))),ArraySeq()))))"
           |          }
           |        }
           |      }, "c1"]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def emailSetMailboxIdsResetShouldFailWhenInvalidValue(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId: MailboxId = mailboxProbe.createMailbox(MailboxPath.inbox(BOB))

    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.from(
          buildTestMessage))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "${messageId.serialize}": {
         |          "mailboxIds": {
         |            "${mailboxId.serialize}": "invalid"
         |          }
         |        }
         |      }
         |    }, "c1"]
         |  ]
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
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "newState": "000001",
           |        "notUpdated": {
           |          "${messageId.serialize}": {
           |            "type": "invalidPatch",
           |            "description": "Message ${messageId.serialize} update is invalid: List((,List(JsonValidationError(List(Value associated with mailboxIds is invalid: List((,List(JsonValidationError(List(Expecting mailboxId value to be a boolean),ArraySeq()))))),ArraySeq()))))"
           |          }
           |        }
           |      }, "c1"]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def emailSetMailboxIdsResetShouldFailWhenValueIsFalse(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId: MailboxId = mailboxProbe.createMailbox(MailboxPath.inbox(BOB))

    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.from(
          buildTestMessage))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "${messageId.serialize}": {
         |          "mailboxIds": {
         |            "${mailboxId.serialize}": "false"
         |          }
         |        }
         |      }
         |    }, "c1"]
         |  ]
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
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "newState": "000001",
           |        "notUpdated": {
           |          "${messageId.serialize}": {
           |            "type": "invalidPatch",
           |            "description": "Message ${messageId.serialize} update is invalid: List((,List(JsonValidationError(List(Value associated with mailboxIds is invalid: List((,List(JsonValidationError(List(Expecting mailboxId value to be a boolean),ArraySeq()))))),ArraySeq()))))"
           |          }
           |        }
           |      }, "c1"]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def emailSetMailboxIdsResetSuccessAndFailureCanBeMixed(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxPath.inbox(BOB))

    val path = MailboxPath.forUser(ANDRE, "other")
    val mailboxId2: MailboxId = mailboxProbe.createMailbox(path)

    val messageId1: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.from(
          buildTestMessage))
      .getMessageId

    val messageId2: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.from(
          buildTestMessage))
      .getMessageId

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Read, Right.Insert))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "${messageId1.serialize}": {
         |          "mailboxIds": {
         |            "${mailboxId2.serialize}": true
         |          }
         |        },
         |        "${messageId2.serialize}": {
         |          "mailboxIds": {
         |            "invalid": true
         |          }
         |        }
         |      }
         |    }, "c1"]
         |  ]
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
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "newState": "000001",
           |        "updated": {
           |          "${messageId1.serialize}": null
           |        },
           |        "notUpdated": {
           |          "${messageId2.serialize}": {
           |            "type": "invalidPatch",
           |            "description": "Message ${messageId2.serialize} update is invalid: List((,List(JsonValidationError(List(Value associated with mailboxIds is invalid: List((,List(JsonValidationError(List(For input string: \\"invalid\\"),ArraySeq()))))),ArraySeq()))))"
           |          }
           |        }
           |      }, "c1"]
           |    ]
           |}""".stripMargin)
  }

  private def buildTestMessage = {
    Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
  }
}
