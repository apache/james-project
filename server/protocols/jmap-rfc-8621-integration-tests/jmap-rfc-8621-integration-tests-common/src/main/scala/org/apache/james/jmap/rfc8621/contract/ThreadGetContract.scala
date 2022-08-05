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
import java.util

import com.google.common.collect.ImmutableList
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.MessageManager
import org.apache.james.mailbox.model.{MailboxId, MailboxPath, SearchQuery}
import org.apache.james.mime4j.dom.Message
import org.apache.james.mime4j.stream.RawField
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Test}

trait ThreadGetContract {

  protected def awaitMessageCount(mailboxIds: util.List[MailboxId], query: SearchQuery, messageCount: Long): Unit

  protected def initOpenSearchClient(): Unit

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    initOpenSearchClient()

    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addDomain("domain-alias.tld")
      .addUser(BOB.asString, BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  @Test
  def givenNonMessageThenGetThreadsShouldReturnNotFound(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Thread/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["123456"]
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
           |	"sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"methodResponses": [
           |		[
           |			"Thread/get",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"state": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |				"list": [
           |
           |				],
           |				"notFound": [
           |					"123456"
           |				]
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
  }

  @Test
  def badAccountIdShouldBeRejected(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Thread/get",
         |    {
         |      "accountId": "bad",
         |      "ids": ["123456"]
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
  def addRelatedMailsInAThreadThenGetThatThreadShouldReturnExactThreadObjectWithEmailIdsSortedByArrivalDate(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    // given 3 mails with related Subject and related Mime Message-ID fields
    val message1: MessageManager.AppendResult = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessageAndGetAppendResult(BOB.asString(), bobPath,
        MessageManager.AppendCommand.from(Message.Builder.of.setSubject("Test")
        .setMessageId("Message-ID-1")
          .setBody("testmail", StandardCharsets.UTF_8)))
    awaitMessageCount(ImmutableList.of, SearchQuery.matchAll, 1)

    // message2 reply to message1
    val message2: MessageManager.AppendResult = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessageAndGetAppendResult(BOB.asString(), bobPath,
        MessageManager.AppendCommand.from(Message.Builder.of.setSubject("Re: Test")
          .setMessageId("Message-ID-2")
          .setField(new RawField("In-Reply-To", "Message-ID-1"))
          .setBody("testmail", StandardCharsets.UTF_8)))
    awaitMessageCount(ImmutableList.of, SearchQuery.matchAll, 2)

    // message3 related to message1 through Subject and References message1's Message-ID
    val message3: MessageManager.AppendResult = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessageAndGetAppendResult(BOB.asString(), bobPath,
        MessageManager.AppendCommand.from(Message.Builder.of.setSubject("Fwd: Re: Test")
          .setMessageId("Message-ID-3")
          .setField(new RawField("In-Reply-To", "Random-InReplyTo"))
          .addField(new RawField("References", "Message-ID-1"))
          .setBody("testmail", StandardCharsets.UTF_8)))

    awaitMessageCount(ImmutableList.of, SearchQuery.matchAll, 3)

    val threadId = message1.getThreadId.serialize()
    val message1Id = message1.getId.getMessageId.serialize()
    val message2Id = message2.getId.getMessageId.serialize()
    val message3Id = message3.getId.getMessageId.serialize()

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Thread/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["$threadId"]
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
           |	"sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"methodResponses": [
           |		[
           |			"Thread/get",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"state": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |				"list": [{
           |					"id": "$threadId",
           |					"emailIds": ["$message1Id", "$message2Id", "$message3Id"]
           |				}],
           |				"notFound": [
           |
           |				]
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
  }

  @Test
  def givenTwoThreadGetThatTwoThreadShouldReturnExactTwoThreadObjectWithEmailIdsSortedByArrivalDate(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    // given 2 mails with related Subject and related Mime Message-ID fields in threadA
    val message1: MessageManager.AppendResult = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessageAndGetAppendResult(BOB.asString(), bobPath,
        MessageManager.AppendCommand.from(Message.Builder.of.setSubject("Test")
          .setMessageId("Message-ID-1")
          .setBody("testmail", StandardCharsets.UTF_8)))
    awaitMessageCount(ImmutableList.of, SearchQuery.matchAll, 1)

    // message2 reply to message1
    val message2: MessageManager.AppendResult = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessageAndGetAppendResult(BOB.asString(), bobPath,
        MessageManager.AppendCommand.from(Message.Builder.of.setSubject("Re: Test")
          .setMessageId("Message-ID-2")
          .setField(new RawField("In-Reply-To", "Message-ID-1"))
          .setBody("testmail", StandardCharsets.UTF_8)))
    val threadA = message1.getThreadId.serialize()

    // message3 in threadB
    val message3: MessageManager.AppendResult = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessageAndGetAppendResult(BOB.asString(), bobPath,
        MessageManager.AppendCommand.from(Message.Builder.of.setSubject("Message3-SubjectLine")
          .setMessageId("Message-ID-3")
          .setBody("testmail", StandardCharsets.UTF_8)))
    val threadB = message3.getThreadId.serialize()

    awaitMessageCount(ImmutableList.of, SearchQuery.matchAll, 3)

    val message1Id = message1.getId.getMessageId.serialize()
    val message2Id = message2.getId.getMessageId.serialize()
    val message3Id = message3.getId.getMessageId.serialize()

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Thread/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["$threadA", "$threadB"]
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
      .inPath("methodResponses[0][1].list")
      .isArray
      .contains(
        s"""{"id":"$threadA","emailIds":["$message1Id","$message2Id"]}""",
        s"""{"id":"$threadB","emailIds":["$message3Id"]}""")
  }

  @Test
  def givenOneThreadGetTwoThreadShouldReturnOnlyOneThreadObjectAndNotFound(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    // given 2 mails with related Subject and related Mime Message-ID fields in threadA
    val message1: MessageManager.AppendResult = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessageAndGetAppendResult(BOB.asString(), bobPath,
        MessageManager.AppendCommand.from(Message.Builder.of.setSubject("Test")
          .setMessageId("Message-ID-1")
          .setBody("testmail", StandardCharsets.UTF_8)))
    // message2 reply to message1
    val message2: MessageManager.AppendResult = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessageAndGetAppendResult(BOB.asString(), bobPath,
        MessageManager.AppendCommand.from(Message.Builder.of.setSubject("Re: Test")
          .setMessageId("Message-ID-2")
          .setField(new RawField("In-Reply-To", "Message-ID-1"))
          .setBody("testmail", StandardCharsets.UTF_8)))
    val threadA = message1.getThreadId.serialize()

    awaitMessageCount(ImmutableList.of, SearchQuery.matchAll, 2)

    val message1Id = message1.getId.getMessageId.serialize()
    val message2Id = message2.getId.getMessageId.serialize()

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Thread/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["$threadA", "nonExistThread"]
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
           |	"sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"methodResponses": [
           |		[
           |			"Thread/get",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"state": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |				"list": [{
           |					"id": "$threadA",
           |					"emailIds": [
           |						"$message1Id",
           |						"$message2Id"
           |					]
           |				}],
           |				"notFound": [
           |					"nonExistThread"
           |				]
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
  }

  @Test
  def addThreeMailsWithRelatedSubjectButNonIdenticalMimeMessageIDThenGetThatThreadShouldNotReturnUnrelatedMails(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    val message1: MessageManager.AppendResult = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessageAndGetAppendResult(BOB.asString(), bobPath,
        MessageManager.AppendCommand.from(Message.Builder.of.setSubject("Test")
          .setMessageId("Message-ID-1")
          .setBody("testmail", StandardCharsets.UTF_8)))

    // message2 have related subject with message1 but non identical Mime Message-ID
    val message2: MessageManager.AppendResult = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessageAndGetAppendResult(BOB.asString(), bobPath,
        MessageManager.AppendCommand.from(Message.Builder.of.setSubject("Re: Test")
          .setMessageId("Message-ID-2")
          .setField(new RawField("In-Reply-To", "Random-InReplyTo"))
          .setBody("testmail", StandardCharsets.UTF_8)))

    // message3 have related subject with message1 but non identical Mime Message-ID
    val message3: MessageManager.AppendResult = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessageAndGetAppendResult(BOB.asString(), bobPath,
        MessageManager.AppendCommand.from(Message.Builder.of.setSubject("Fwd: Re: Test")
          .setMessageId("Message-ID-3")
          .setField(new RawField("In-Reply-To", "Another-Random-InReplyTo"))
          .addField(new RawField("References", "Random-References"))
          .setBody("testmail", StandardCharsets.UTF_8)))

    awaitMessageCount(ImmutableList.of, SearchQuery.matchAll, 3)

    val threadId1 = message1.getThreadId.serialize()
    val message1Id = message1.getId.getMessageId.serialize()

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Thread/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["$threadId1"]
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
           |	"sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"methodResponses": [
           |		[
           |			"Thread/get",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"state": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |				"list": [{
           |					"id": "$threadId1",
           |					"emailIds": ["$message1Id"]
           |				}],
           |				"notFound": [
           |
           |				]
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
  }

  @Test
  def addThreeMailsWithIdenticalMimeMessageIDButNonRelatedSubjectThenGetThatThreadShouldNotReturnUnrelatedMails(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    val message1: MessageManager.AppendResult = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessageAndGetAppendResult(BOB.asString(), bobPath,
        MessageManager.AppendCommand.from(Message.Builder.of.setSubject("Test1")
          .setMessageId("Message-ID-1")
          .setBody("testmail", StandardCharsets.UTF_8)))

    // message2 have identical Mime Message-ID with message1 through In-Reply-To field but have non related subject
    val message2: MessageManager.AppendResult = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessageAndGetAppendResult(BOB.asString(), bobPath,
        MessageManager.AppendCommand.from(Message.Builder.of.setSubject("Test2")
          .setMessageId("Message-ID-2")
          .setField(new RawField("In-Reply-To", "Message-ID-1"))
          .setBody("testmail", StandardCharsets.UTF_8)))

    // message2 have identical Mime Message-ID with message1 through References field but have non related subject
    val message3: MessageManager.AppendResult = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessageAndGetAppendResult(BOB.asString(), bobPath,
        MessageManager.AppendCommand.from(Message.Builder.of.setSubject("Test3")
          .setMessageId("Message-ID-3")
          .setField(new RawField("In-Reply-To", "Random-InReplyTo"))
          .addField(new RawField("References", "Message-ID-1"))
          .setBody("testmail", StandardCharsets.UTF_8)))

    awaitMessageCount(ImmutableList.of, SearchQuery.matchAll, 3)

    val threadId1 = message1.getThreadId.serialize()
    val message1Id = message1.getId.getMessageId.serialize()

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Thread/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["$threadId1"]
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
           |	"sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"methodResponses": [
           |		[
           |			"Thread/get",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"state": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |				"list": [{
           |					"id": "$threadId1",
           |					"emailIds": ["$message1Id"]
           |				}],
           |				"notFound": [
           |
           |				]
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
  }

}
