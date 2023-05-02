/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *  http://www.apache.org/licenses/LICENSE-2.0                  *
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
import io.restassured.RestAssured._
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.{MailboxPath, MessageId}
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.util.ClassLoaderUtils
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Test}

trait EmailParseMethodContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build()
  }

  def randomMessageId: MessageId

  @Test
  def parseShouldSuccess(guiceJamesServer: GuiceJamesServer): Unit = {
    val path: MailboxPath = MailboxPath.inbox(BOB)
    val mailboxProbe: MailboxProbeImpl = guiceJamesServer.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(path)

    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/html.eml")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "${messageId.serialize()}" ]
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
           |      [ "Email/parse", {
           |         "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |         "parsed": {
           |           "${messageId.serialize()}": {
           |             "size": 2725,
           |             "blobId": "1",
           |             "references": null,
           |             "subject": "MultiAttachment",
           |             "inReplyTo": null,
           |             "messageId": [ "13d4375e-a4a9-f613-06a1-7e8cb1e0ea93@linagora.com"],
           |             "from": [
           |               {
           |                 "name": "Lina",
           |                  "email": "from@linagora.com"
           |               }
           |             ],
           |             "sentAt": "2017-02-27T04:24:48Z",
           |             "to": [
           |               {
           |                  "email": "to@linagora.com"
           |               }
           |             ]
           |           }
           |         }
           |      }, "c1" ]]
           |}""".stripMargin)
  }

  @Test
  def parseShouldSupportAllProperties(guiceJamesServer: GuiceJamesServer): Unit = {
    val path: MailboxPath = MailboxPath.inbox(BOB)
    val mailboxProbe: MailboxProbeImpl = guiceJamesServer.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(path)

    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/html.eml")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "${messageId.serialize()}" ],
         |      "properties": ["blobId", "size", "headers", "references", "subject", "inReplyTo", "messageId", "from", "to", "sentAt"]
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

    val contentType = " multipart/mixed;\\r\\n boundary=\\\"------------64D8D789FC30153D6ED18258\\\""
    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      [ "Email/parse", {
           |         "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |         "parsed": {
           |           "${messageId.serialize()}": {
           |             "size": 2725,
           |             "blobId": "1",
           |             "headers": [
           |               {
           |                 "name": "Return-Path",
           |                 "value": " <from@linagora.com>"
           |               },
           |               {
           |                 "name": "To",
           |                 "value": " to@linagora.com"
           |               },
           |               {
           |                 "name": "From",
           |                 "value": " Lina <from@linagora.com>"
           |               },
           |               {
           |                 "name": "Subject",
           |                 "value": " MultiAttachment"
           |               },
           |               {
           |                 "name": "Message-ID",
           |                 "value": " <13d4375e-a4a9-f613-06a1-7e8cb1e0ea93@linagora.com>"
           |               },
           |               {
           |                 "name": "Date",
           |                 "value": " Mon, 27 Feb 2017 11:24:48 +0700"
           |               },
           |               {
           |                 "name": "User-Agent",
           |                 "value": " Mozilla/5.0 (X11; Linux x86_64; rv:45.0) Gecko/20100101\\r\\n Thunderbird/45.2.0"
           |               },
           |               {
           |                 "name": "MIME-Version",
           |                 "value": " 1.0"
           |               },
           |               {
           |                 "name": "Content-Type",
           |                 "value": "$contentType"
           |               }
           |             ],
           |             "references": null,
           |             "subject": "MultiAttachment",
           |             "inReplyTo": null,
           |             "messageId": [ "13d4375e-a4a9-f613-06a1-7e8cb1e0ea93@linagora.com"],
           |             "from": [
           |               {
           |                 "name": "Lina",
           |                  "email": "from@linagora.com"
           |               }
           |             ],
           |             "sentAt": "2017-02-27T04:24:48Z",
           |             "to": [
           |               {
           |                  "email": "to@linagora.com"
           |               }
           |             ]
           |           }
           |         }
           |      }, "c1" ]]
           |}""".stripMargin)
  }

  @Test
  def parseShouldFilterProperties(guiceJamesServer: GuiceJamesServer): Unit = {
    val path: MailboxPath = MailboxPath.inbox(BOB)
    val mailboxProbe: MailboxProbeImpl = guiceJamesServer.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(path)

    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/html.eml")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "${messageId.serialize()}" ],
         |      "properties": ["blobId"]
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
           |      [ "Email/parse", {
           |         "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |         "parsed": {
           |           "${messageId.serialize()}": {
           |             "blobId": "1"
           |           }
           |         }
           |      }, "c1" ]]
           |}""".stripMargin)
  }

  @Test
  def parseShouldFailWhenWrongAccountId(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/parse",
         |    {
         |      "accountId": "unknownAccountId",
         |      "blobIds": [ "0f9f65ab-dc7b-4146-850f-6e4881093965" ]
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [[
         |            "error",
         |            {
         |                "type": "accountNotFound"
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def parseShouldReturnNotFoundWhenBlobDoNotExist(): Unit = {
    val blobIdShouldNotFound = randomMessageId.serialize()
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "$blobIdShouldNotFound" ]
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [[
         |      "Email/parse",
         |      {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "parsed":{},
         |        "notFound": ["$blobIdShouldNotFound"]
         |      },
         |      "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def parseShouldReturnNotFoundWhenBadBlobId(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "invalid" ]
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [[
         |      "Email/parse",
         |      {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "parsed":{},
         |        "notFound": ["invalid"]
         |      },
         |      "c1"
         |    ]]
         |}""".stripMargin)
  }

  @Test
  def parseShouldReturnUnknownMethodWhenMissingOneCapability(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core"],
         |  "methodCalls": [[
         |    "Email/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "123" ]
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
         |  "sessionState": "${SESSION_STATE.value}",
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
  def parseShouldReturnUnknownMethodWhenMissingAllCapabilities(): Unit = {
    val request =
      s"""{
         |  "using": [],
         |  "methodCalls": [[
         |    "Email/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "123" ]
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
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "error",
         |    {
         |      "type": "unknownMethod",
         |      "description": "Missing capability(ies): urn:ietf:params:jmap:mail, urn:ietf:params:jmap:core"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }
}
