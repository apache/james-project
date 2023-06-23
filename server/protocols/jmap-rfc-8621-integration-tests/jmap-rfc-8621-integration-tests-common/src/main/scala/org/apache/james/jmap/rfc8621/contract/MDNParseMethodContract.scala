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

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured._
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.mail.MDNParseRequest
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.{MailboxPath, MessageId}
import org.apache.james.mime4j.dom.Message
import org.apache.james.mime4j.message.{BodyPartBuilder, MultipartBuilder, SingleBodyBuilder}
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.util.ClassLoaderUtils
import org.apache.james.utils.DataProbeImpl
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.junit.jupiter.api.{BeforeEach, Test}
import play.api.libs.json._

trait MDNParseMethodContract {
  private lazy val slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS
  private lazy val calmlyAwait = Awaitility.`with`
    .pollInterval(slowPacedPollInterval)
    .and.`with`.pollDelay(slowPacedPollInterval)
    .await

  private lazy val awaitConditionFactory = calmlyAwait.atMost(5, TimeUnit.SECONDS)

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
  def parseShouldSuccessWithMDNHasAllProperties(guiceJamesServer: GuiceJamesServer): Unit = {
    val path: MailboxPath = MailboxPath.inbox(BOB)
    val mailboxProbe: MailboxProbeImpl = guiceJamesServer.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(path)

    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/mdn_complex.eml")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mdn",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "MDN/parse",
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
           |      [ "MDN/parse", {
           |         "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |         "parsed": {
           |           "${messageId.serialize()}": {
           |             "subject": "Read: test",
           |             "textBody": "To: magiclan@linagora.com\\r\\nSubject: test\\r\\nMessage was displayed on Tue Mar 30 2021 10:31:50 GMT+0700 (Indochina Time)",
           |             "reportingUA": "OpenPaaS Unified Inbox; UA_Product",
           |             "disposition": {
           |               "actionMode": "manual-action",
           |               "sendingMode": "mdn-sent-manually",
           |               "type": "displayed"
           |             },
           |             "finalRecipient": "rfc822; tungexplorer@linagora.com",
           |             "originalMessageId": "<633c6811-f897-ec7c-642a-2360366e1b93@linagora.com>",
           |             "originalRecipient": "rfc822; tungexplorer@linagora.com",
           |             "includeOriginalMessage": true,
           |             "error": [
           |                "Message1",
           |                "Message2"
           |             ],
           |             "extensionFields": {
           |                "X-OPENPAAS-IP" : " 177.177.177.77",
           |                "X-OPENPAAS-PORT" : " 8000"
           |             }
           |           }
           |         }
           |      }, "c1" ]]
           |}""".stripMargin)
  }

  @Test
  def parseShouldAcceptSeveralIds(guiceJamesServer: GuiceJamesServer): Unit = {
    val path: MailboxPath = MailboxPath.inbox(BOB)
    val mailboxProbe: MailboxProbeImpl = guiceJamesServer.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(path)

    val messageId1: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/mdn_simple.eml")))
      .getMessageId
    val messageId2: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/mdn_simple.eml")))
      .getMessageId
    val messageId3: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/mdn_simple.eml")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mdn",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "MDN/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "${messageId1.serialize()}", "${messageId2.serialize()}", "${messageId3.serialize()}"]
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
      .inPath("methodResponses[0][1].parsed")
      .isEqualTo(
        s"""{
           |    "${messageId1.serialize()}": {
           |        "subject": "Read: test",
           |        "textBody": "This is simple body of human-readable part",
           |        "finalRecipient": "rfc822; tungexplorer@linagora.com",
           |        "includeOriginalMessage": false,
           |        "disposition": {
           |            "actionMode": "manual-action",
           |            "sendingMode": "mdn-sent-manually",
           |            "type": "displayed"
           |        }
           |    },
           |    "${messageId2.serialize()}": {
           |        "subject": "Read: test",
           |        "textBody": "This is simple body of human-readable part",
           |        "finalRecipient": "rfc822; tungexplorer@linagora.com",
           |        "includeOriginalMessage": false,
           |        "disposition": {
           |            "actionMode": "manual-action",
           |            "sendingMode": "mdn-sent-manually",
           |            "type": "displayed"
           |        }
           |    },
           |    "${messageId3.serialize()}": {
           |        "subject": "Read: test",
           |        "textBody": "This is simple body of human-readable part",
           |        "finalRecipient": "rfc822; tungexplorer@linagora.com",
           |        "includeOriginalMessage": false,
           |        "disposition": {
           |            "actionMode": "manual-action",
           |            "sendingMode": "mdn-sent-manually",
           |            "type": "displayed"
           |        }
           |    }
           |}""".stripMargin)
  }

  @Test
  def parseShouldSuccessWithMDNHasMinimalProperties(guiceJamesServer: GuiceJamesServer): Unit = {
    val path: MailboxPath = MailboxPath.inbox(BOB)
    val mailboxProbe: MailboxProbeImpl = guiceJamesServer.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(path)

    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/mdn_simple.eml")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mdn",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "MDN/parse",
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
           |      [ "MDN/parse", {
           |         "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |         "parsed": {
           |           "${messageId.serialize()}": {
           |             "subject": "Read: test",
           |             "textBody": "This is simple body of human-readable part",
           |             "disposition": {
           |               "actionMode": "manual-action",
           |               "sendingMode": "mdn-sent-manually",
           |               "type": "displayed"
           |             },
           |             "finalRecipient": "rfc822; tungexplorer@linagora.com",
           |             "includeOriginalMessage": false
           |           }
           |         }
           |      }, "c1" ]]
           |}""".stripMargin)
  }


  @Test
  def mdnParseShouldFailWhenWrongAccountId(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mdn",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "MDN/parse",
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
  def mdnParseShouldFailWhenNumberOfBlobIdsTooLarge(): Unit = {
    val blogIds = LazyList.continually(randomMessageId.serialize()).take(MDNParseRequest.MAXIMUM_NUMBER_OF_BLOB_IDS + 1).toArray;
    val blogIdsJson = Json.stringify(Json.arr(blogIds)).replace("[[", "[").replace("]]", "]");
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mdn",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "MDN/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds":  ${blogIdsJson}
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
      .whenIgnoringPaths("methodResponses[0][1].description")
      .isEqualTo(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [[
           |    "error",
           |    {
           |          "type": "requestTooLarge",
           |          "description": "The number of ids requested by the client exceeds the maximum number the server is willing to process in a single method call"
           |    },
           |    "c1"]]
           |}""".stripMargin)
  }

  @Test
  def parseShouldReturnNotParseableWhenNotAnMDN(guiceJamesServer: GuiceJamesServer): Unit = {
    val path: MailboxPath = MailboxPath.inbox(BOB)
    val mailboxProbe: MailboxProbeImpl = guiceJamesServer.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(path)

    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.builder()
        .build(Message.Builder
          .of
          .setSubject("Subject MDN")
          .setSender(ANDRE.asString())
          .setFrom(ANDRE.asString())
          .setBody(MultipartBuilder.create("report")
            .addTextPart("This is body of text part", StandardCharsets.UTF_8)
            .build)
          .build))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mdn",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "MDN/parse",
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

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [[
         |      "MDN/parse",
         |      {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "notParsable": ["${messageId.serialize()}"]
         |      },
         |      "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def parseShouldReturnNotFoundWhenBlobDoNotExist(): Unit = {
    val blobIdShouldNotFound = randomMessageId.serialize()
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mdn",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "MDN/parse",
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
         |      "MDN/parse",
         |      {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
         |    "urn:ietf:params:jmap:mdn",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "MDN/parse",
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
         |      "MDN/parse",
         |      {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "notFound": ["invalid"]
         |      },
         |      "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def parseAndNotFoundAndNotParsableCanBeMixed(guiceJamesServer: GuiceJamesServer): Unit = {
    val path: MailboxPath = MailboxPath.inbox(BOB)
    val mailboxProbe: MailboxProbeImpl = guiceJamesServer.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(path)

    val blobIdParsable: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/mdn_complex.eml")))
      .getMessageId

    val blobIdNotParsable: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.builder()
        .build(Message.Builder
          .of
          .setSubject("Subject MDN")
          .setSender(ANDRE.asString())
          .setFrom(ANDRE.asString())
          .setBody(MultipartBuilder.create("report")
            .addTextPart("This is body of text part", StandardCharsets.UTF_8)
            .build)
          .build))
      .getMessageId
    val blobIdNotFound = randomMessageId
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mdn",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "MDN/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "${blobIdParsable.serialize()}", "${blobIdNotParsable.serialize()}", "${blobIdNotFound.serialize()}" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitConditionFactory.untilAsserted{
      () => {
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
             |      "MDN/parse",
             |      {
             |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |        "notFound": ["${blobIdNotFound.serialize()}"],
             |        "notParsable": ["${blobIdNotParsable.serialize()}"],
             |        "parsed": {
             |           "${blobIdParsable.serialize()}": {
             |             "subject": "Read: test",
             |             "textBody": "To: magiclan@linagora.com\\r\\nSubject: test\\r\\nMessage was displayed on Tue Mar 30 2021 10:31:50 GMT+0700 (Indochina Time)",
             |             "reportingUA": "OpenPaaS Unified Inbox; UA_Product",
             |             "disposition": {
             |               "actionMode": "manual-action",
             |               "sendingMode": "mdn-sent-manually",
             |               "type": "displayed"
             |             },
             |             "finalRecipient": "rfc822; tungexplorer@linagora.com",
             |             "originalMessageId": "<633c6811-f897-ec7c-642a-2360366e1b93@linagora.com>",
             |             "originalRecipient": "rfc822; tungexplorer@linagora.com",
             |             "includeOriginalMessage": true,
             |             "error": [
             |                "Message1",
             |                "Message2"
             |             ],
             |             "extensionFields": {
             |                "X-OPENPAAS-IP" : " 177.177.177.77",
             |                "X-OPENPAAS-PORT" : " 8000"
             |             }
             |          }
             |        }
             |      },
             |      "c1"
             |        ]]
             |}""".stripMargin)
      }
    }
  }

  @Test
  def mdnParseShouldReturnUnknownMethodWhenMissingOneCapability(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "MDN/parse",
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
         |      "description": "Missing capability(ies): urn:ietf:params:jmap:mdn"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def mdnParseShouldReturnUnknownMethodWhenMissingAllCapabilities(): Unit = {
    val request =
      s"""{
         |  "using": [],
         |  "methodCalls": [[
         |    "MDN/parse",
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
         |      "description": "Missing capability(ies): urn:ietf:params:jmap:mdn, urn:ietf:params:jmap:mail, urn:ietf:params:jmap:core"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def forEmailIdShouldReturnWhenOriginalMessageIdIsRelated(guiceJamesServer: GuiceJamesServer): Unit = {
    val path: MailboxPath = MailboxPath.inbox(BOB)
    val mailboxProbe: MailboxProbeImpl = guiceJamesServer.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(path)

    val originalMessageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/mdn_relate_original_message.eml")))
      .getMessageId

    val mdnBodyPart = BodyPartBuilder
      .create
      .setBody(SingleBodyBuilder.create
        .setText(s"""Reporting-UA: UA_name; UA_product
                    |MDN-Gateway: smtp; apache.org
                    |Original-Recipient: rfc822; originalRecipient
                    |Final-Recipient: rfc822; ${BOB.asString()}
                    |Original-Message-ID: <messageId1@Atlassian.JIRA>
                    |Disposition: automatic-action/MDN-sent-automatically;processed/error,failed
                    |""".replace(System.lineSeparator(), "\r\n")
          .stripMargin)
        .buildText)
      .setContentType("message/disposition-notification")
      .build

    val mdnMessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.builder()
        .build(Message.Builder
          .of
          .setSubject("Subject MDN")
          .setSender(BOB.asString())
          .setFrom(BOB.asString())
          .setBody(MultipartBuilder.create("report")
            .addTextPart("This is body of text part", StandardCharsets.UTF_8)
            .addBodyPart(mdnBodyPart)
            .build)
          .build))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mdn",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "MDN/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "${mdnMessageId.serialize()}" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitConditionFactory.untilAsserted {
      () => {
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
          .inPath(s"methodResponses[0][1].parsed.${mdnMessageId.serialize()}.forEmailId")
          .isEqualTo(s""""${originalMessageId.serialize}"""")
      }
    }
  }

  @Test
  def forEmailIdShouldBeNullWhenOriginalMessageIdIsNotFound(guiceJamesServer: GuiceJamesServer): Unit = {
    val path: MailboxPath = MailboxPath.inbox(BOB)
    val mailboxProbe: MailboxProbeImpl = guiceJamesServer.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(path)
    val mdnBodyPart = BodyPartBuilder
      .create
      .setBody(SingleBodyBuilder.create
        .setText(s"""Reporting-UA: UA_name; UA_product
                    |MDN-Gateway: smtp; apache.org
                    |Original-Recipient: rfc822; originalRecipient
                    |Final-Recipient: rfc822; final_recipient
                    |Original-Message-ID: <notFound@Atlassian.JIRA>
                    |Disposition: automatic-action/MDN-sent-automatically;processed/error,failed
                    |""".replace(System.lineSeparator(), "\r\n")
          .stripMargin)
        .buildText)
      .setContentType("message/disposition-notification")
      .build

    val mdnMessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.builder()
        .build(Message.Builder
          .of
          .setSubject("Subject MDN")
          .setSender(BOB.asString())
          .setFrom(BOB.asString())
          .setBody(MultipartBuilder.create("report")
            .addTextPart("This is body of text part", StandardCharsets.UTF_8)
            .addBodyPart(mdnBodyPart)
            .build)
          .build))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mdn",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "MDN/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "${mdnMessageId.serialize()}" ]
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
      .inPath(s"methodResponses[0][1].parsed.${mdnMessageId.serialize()}.forEmailId").isAbsent()
  }
}
