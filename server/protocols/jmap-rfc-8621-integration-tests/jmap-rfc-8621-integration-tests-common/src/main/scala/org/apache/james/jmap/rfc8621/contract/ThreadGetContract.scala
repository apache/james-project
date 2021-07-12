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
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.MessageManager
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.mime4j.dom.Message
import org.apache.james.mime4j.stream.RawField
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Test}

object ThreadGetContract {
  private def createTestMessage: Message = Message.Builder
    .of
    .setSubject("test")
    .setSender(ANDRE.asString())
    .setFrom(ANDRE.asString())
    .setSubject("World domination \r\n" +
      " and this is also part of the header")
    .setBody("testmail", StandardCharsets.UTF_8)
    .build
}

trait ThreadGetContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
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
  def givenMailsBelongToAThreadThenGetThatThreadShouldReturnExactThreadObject(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    // given 2 mail with same thread
    val message1: MessageManager.AppendResult = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessageAndGetAppendResult(BOB.asString(), bobPath,
        MessageManager.AppendCommand.from(Message.Builder.of.setSubject("Test")
        .setMessageId("Message-ID")
          .setField(new RawField("In-Reply-To", "someInReplyTo"))
          .addField(new RawField("References", "references1"))
          .addField(new RawField("References", "references2"))
          .setBody("testmail", StandardCharsets.UTF_8)))

    val message2: MessageManager.AppendResult = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessageAndGetAppendResult(BOB.asString(), bobPath,
        MessageManager.AppendCommand.from(Message.Builder.of.setSubject("Re: Test")
          .setMessageId("Another-Message-ID")
          .setField(new RawField("In-Reply-To", "someInReplyTo"))
          .addField(new RawField("References", "references1"))
          .addField(new RawField("References", "references2"))
          .setBody("testmail", StandardCharsets.UTF_8)))

    val threadId = message1.getThreadId.serialize()
    val message1Id = message1.getId.getMessageId.serialize()
    val message2Id = message2.getId.getMessageId.serialize()

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
           |					"emailIds": ["$message1Id", "$message2Id"]
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
