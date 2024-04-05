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
 * under the License.                   *
 ****************************************************************/
package org.apache.james.jmap.rfc8621.contract

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.concurrent.TimeUnit

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, `with`, requestSpecification}
import io.restassured.builder.ResponseSpecBuilder
import io.restassured.http.ContentType.JSON
import jakarta.mail.Flags
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option
import net.javacrumbs.jsonunit.core.internal.Options
import org.apache.http.HttpStatus.{SC_CREATED, SC_OK}
import org.apache.james.GuiceJamesServer
import org.apache.james.core.quota.QuotaCountLimit
import org.apache.james.jmap.api.change.State
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UTCDate
import org.apache.james.jmap.core.UuidState.INSTANCE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.DownloadContract.accountId
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ANDRE, ANDRE_ACCOUNT_ID, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.jmap.{JmapGuiceProbe, MessageIdProbe}
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.MailboxACL.Right
import org.apache.james.mailbox.model.{ComposedMessageId, MailboxACL, MailboxConstants, MailboxId, MailboxPath, MessageId}
import org.apache.james.mailbox.probe.MailboxProbe
import org.apache.james.mailbox.{DefaultMailboxes, FlagsBuilder}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.{ACLProbeImpl, MailboxProbeImpl, QuotaProbesImpl}
import org.apache.james.util.ClassLoaderUtils
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.hamcrest.Matchers
import org.hamcrest.Matchers.{equalTo, not}
import org.junit.jupiter.api.{BeforeEach, Test}
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import play.api.libs.json.{JsNumber, JsString, Json}

import scala.jdk.CollectionConverters._

trait EmailSetMethodContract {
  private lazy val slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS
  private lazy val calmlyAwait = Awaitility.`with`
    .pollInterval(slowPacedPollInterval)
    .and.`with`.pollDelay(slowPacedPollInterval)
    .await
  private lazy val awaitAtMostTenSeconds = calmlyAwait.atMost(10, TimeUnit.SECONDS)
  private lazy val UTC_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")

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

  def invalidMessageIdMessage(invalid: String): String

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

  @ParameterizedTest
  @ValueSource(strings = Array(
    """"header:aheader": " a value"""",
    """"header:aheader:all": [" a value"]""",
    """"header:aheader:all": []""",
    """"header:aheader:all": [" abc", " def"]""",
    """"header:aheader:asRaw": " a value"""",
    """"header:aheader:asText": "a value"""",
    """"header:aheader:asText:all": []""",
    """"header:aheader:asText:all": ["abc"]""",
    """"header:aheader:asText:all": ["abc", "def"]""",
    """"header:aheader:asDate": "2020-10-29T06:39:04Z"""",
    """"header:aheader:asAddresses": [{"email": "rcpt1@apache.org"}, {"email": "rcpt2@apache.org"}]""",
    """"header:aheader:asURLs": ["url1", "url2"]""",
    """"header:aheader:asMessageIds": ["id1@domain.tld", "id2@domain.tld"]""",
    """"header:aheader:asGroupedAddresses": [{"name": null,"addresses": [{"name": "user1","email": "user1@domain.tld" },{"name": "user2", "email": "user2@domain.tld"}]},{"name": "Friends","addresses": [{"email": "user3@domain.tld"},{"name": "user4","email": "user4@domain.tld"}]}]"""
  ))
  def createShouldPositionSpecificHeaders(specificHeader: String, server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val propertyEnd = specificHeader.substring(1).indexOf('"')
    val property = specificHeader.substring(1, propertyEnd + 1)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa":{
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          $specificHeader
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |     {
         |       "accountId": "$ACCOUNT_ID",
         |       "ids": ["#aaaaaa"],
         |       "properties": ["mailboxIds", "$property"]
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

    val createResponse = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("aaaaaa")

    val messageId = createResponse
      .\("id")
      .get.asInstanceOf[JsString].value
    val size = createResponse
      .\("size")
      .get.asInstanceOf[JsNumber].value

    assertThatJson(response)
      .inPath("methodResponses[0][1].created.aaaaaa")
      .isEqualTo(
        s"""{
           | "id": "$messageId",
           | "blobId": "$messageId",
           | "threadId": "$messageId",
           | "size": $size
           |}""".stripMargin)

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[1][1].list[0].id")
      .inPath(s"methodResponses[1][1].list")
      .isEqualTo(s"""[{
                    |  "mailboxIds": {
                    |    "${mailboxId.serialize}": true
                    |  },
                    |  $specificHeader
                    |}]""".stripMargin)
  }

  @Test
  def specificHeaderShouldMatchSupportedType(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa":{
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "header:To:asMessageId": ["mid@domain.tld"]
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
      .inPath("methodResponses[0][1].notCreated")
      .isEqualTo(
        s"""{
           |  "aaaaaa": {
           |    "type": "invalidArguments",
           |    "description": "List((,List(JsonValidationError(List(header:To:asMessageId is an invalid specific header),List()))))"
           |  }
           |}""".stripMargin)
  }

  @Test
  def specificHeadersCannotOverrideConvenienceHeader(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa":{
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "header:To:asAddresses": [{"email": "rcpt1@apache.org"}, {"email": "rcpt2@apache.org"}],
         |          "to": [{"email": "rcpt1@apache.org"}, {"email": "rcpt2@apache.org"}]
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
      .inPath("methodResponses[0][1].notCreated")
      .isEqualTo(
        s"""{
           |  "aaaaaa": {
           |    "type": "invalidArguments",
           |    "description": "To was already defined by convenience headers"
           |  }
           |}""".stripMargin)
  }

  @Test
  def emailSetCreateShouldPositionMissingDateAndMessageId(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa":{
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "to": [{"email": "rcpt1@apache.org"}, {"email": "rcpt2@apache.org"}],
         |          "from": [{"email": "${BOB.asString}"}]
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |     {
         |       "accountId": "$ACCOUNT_ID",
         |       "ids": ["#aaaaaa"],
         |       "properties": ["sentAt", "messageId"]
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
      .inPath("methodResponses[1][1].list.[0].sentAt")
      .isEqualTo("\"${json-unit.ignore}\"")
    assertThatJson(response)
      .inPath("methodResponses[1][1].list.[0].messageId")
      .isEqualTo("[\"${json-unit.ignore}\"]")
  }

  @Test
  def emailSetCreateShouldSucceedWithEmptyKeywords(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa":{
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "keywords":{},
         |          "to": [{"email": "rcpt1@apache.org"}, {"email": "rcpt2@apache.org"}],
         |          "from": [{"email": "${BOB.asString}"}]
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |     {
         |       "accountId": "$ACCOUNT_ID",
         |       "ids": ["#aaaaaa"],
         |       "properties": ["sentAt", "messageId"]
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
      .inPath("methodResponses[1][1].list.[0].sentAt")
      .isEqualTo("\"${json-unit.ignore}\"")
    assertThatJson(response)
      .inPath("methodResponses[1][1].list.[0].messageId")
      .isEqualTo("[\"${json-unit.ignore}\"]")
  }

  @Test
  def shouldCombineCreateAndUpdateInASingleMethodCall(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val messageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobPath,
        AppendCommand.from(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_complex.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "destroy": ["${messageId.serialize()}"],
         |      "create": {
         |        "aaaaaa":{
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "keywords":{},
         |          "attachments": [
         |            {
         |              "blobId": "${messageId.serialize()}_5",
         |              "type":"text/plain",
         |              "charset":"UTF-8",
         |              "disposition": "attachment"
         |            }
         |          ],
         |          "to": [{"email": "rcpt1@apache.org"}, {"email": "rcpt2@apache.org"}],
         |          "from": [{"email": "${BOB.asString}"}]
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
      .inPath("methodResponses[0][1].destroyed[0]")
      .isPresent
    assertThatJson(response)
      .inPath("methodResponses[0][1].created.aaaaaa")
      .isPresent
  }

  @Test
  def createShouldFailWhenBadJsonPayloadForSpecificHeader(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa":{
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "header:To:asAddresses": "invalid"
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
      .inPath("methodResponses[0][1].notCreated")
      .isEqualTo(
        s"""{
           |  "aaaaaa": {
           |    "type": "invalidArguments",
           |    "description": "List((,List(JsonValidationError(List(List((,List(JsonValidationError(List(error.expected.jsarray),List()))))),List()))))"
           |  }
           |}""".stripMargin)
  }

  @Test
  def specificContentHeadersShouldBeRejected(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa":{
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "header:Content-Type:asText": "text/plain"
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
      .inPath("methodResponses[0][1].notCreated")
      .isEqualTo(
        s"""{
           |  "aaaaaa": {
           |    "type": "invalidArguments",
           |    "description": "Header fields beginning with `Content-` MUST NOT be specified on the Email object, only on EmailBodyPart objects."
           |  }
           |}""".stripMargin)
  }

  @Test
  def createShouldFailWhenEmailContainsHeadersProperties(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa":{
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "headers": [
         |            {
         |              "name": "Content-Type",
         |              "value": " text/plain; charset=utf-8; format=flowed"
         |            },
         |            {
         |              "name": "Content-Transfer-Encoding",
         |              "value": " 7bit"
         |            }
         |          ]
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
      .inPath("methodResponses[0][1].notCreated")
      .isEqualTo(
        s"""{
           |  "aaaaaa": {
           |    "type": "invalidArguments",
           |    "description": "List((,List(JsonValidationError(List('headers' is not allowed),List()))))"
           |  }
           |}""".stripMargin)
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
        s"""|{
          |   "type":"invalidPatch",
          |   "description": "Message update is invalid: List((,List(JsonValidationError(List(Value associated with keywords is invalid: List((/movie,List(JsonValidationError(List(map marker value can only be true),List()))))),List()))))"
          |}""".stripMargin)
  }

  @Test
  def createShouldAddAnEmailInTargetMailbox(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa":{
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "Boredome comes from a boring mind!"
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |     {
         |       "accountId": "$ACCOUNT_ID",
         |       "ids": ["#aaaaaa"],
         |       "properties": ["mailboxIds", "subject"]
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

    val responseAsJson = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("aaaaaa")

    val messageId = responseAsJson
      .\("id")
      .get.asInstanceOf[JsString].value
    val size = responseAsJson
      .\("size")
      .get.asInstanceOf[JsNumber].value

    assertThatJson(response)
      .inPath("methodResponses[0][1].created.aaaaaa")
      .isEqualTo(
        s"""{
           | "id": "$messageId",
           | "blobId": "$messageId",
           | "threadId": "$messageId",
           | "size": $size
           |}""".stripMargin)

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[1][1].list[0].id")
      .inPath(s"methodResponses[1][1].list")
      .isEqualTo(s"""[{
          |  "mailboxIds": {
          |    "${mailboxId.serialize}": true
          |  },
          |  "subject": "Boredome comes from a boring mind!"
          |}]""".stripMargin)
  }

  @Test
  def createShouldHandleAddressHeaders(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa":{
         |          "mailboxIds": {"${mailboxId.serialize}": true},
         |          "cc": [{"name": "MODALİF", "email": "modalif@domain.tld"}],
         |          "bcc": [{"email": "benwa@apache.org"}],
         |          "to": [{"email": "rcpt1@apache.org"}, {"email": "rcpt2@apache.org"}],
         |          "from": [{"email": "rcpt2@apache.org"}, {"email": "rcpt3@apache.org"}],
         |          "sender": [{"email": "rcpt4@apache.org"}],
         |          "replyTo": [{"email": "rcpt6@apache.org"}, {"email": "rcpt7@apache.org"}]
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |     {
         |       "accountId": "$ACCOUNT_ID",
         |       "ids": ["#aaaaaa"],
         |       "properties": ["cc", "bcc", "sender", "from", "to", "replyTo"]
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

    val responseAsJson = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("aaaaaa")

    val messageId = responseAsJson
      .\("id")
      .get.asInstanceOf[JsString].value
    val size = responseAsJson
      .\("size")
      .get.asInstanceOf[JsNumber].value

    assertThatJson(response)
      .inPath("methodResponses[0][1].created.aaaaaa")
      .isEqualTo(
        s"""{
           | "id": "$messageId",
           | "blobId": "$messageId",
           | "threadId": "$messageId",
           | "size": $size
           |}""".stripMargin)

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[1][1].list[0].id")
      .inPath(s"methodResponses[1][1].list")
      .isEqualTo(s"""[{
          |          "cc": [{"name": "MODALİF", "email": "modalif@domain.tld"}],
          |          "bcc": [{"email": "benwa@apache.org"}],
          |          "to": [{"email": "rcpt1@apache.org"}, {"email": "rcpt2@apache.org"}],
          |          "from": [{"email": "rcpt2@apache.org"}, {"email": "rcpt3@apache.org"}],
          |          "sender": [{"email": "rcpt4@apache.org"}],
          |          "replyTo": [{"email": "rcpt6@apache.org"}, {"email": "rcpt7@apache.org"}]
          |}]""".stripMargin)
  }

  @Test
  def createWithMultipleSenderShouldNotCrash(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa":{
         |          "mailboxIds": {"${mailboxId.serialize}": true},
         |          "sender": [{"email": "rcpt4@apache.org"}, {"email": "rcpt3@apache.org"}]
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |     {
         |       "accountId": "$ACCOUNT_ID",
         |       "ids": ["#aaaaaa"],
         |       "properties": ["sender"]
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

    val responseAsJson = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("aaaaaa")

    val messageId = responseAsJson
      .\("id")
      .get.asInstanceOf[JsString].value
    val size = responseAsJson
      .\("size")
      .get.asInstanceOf[JsNumber].value

    assertThatJson(response)
      .inPath("methodResponses[0][1].created.aaaaaa")
      .isEqualTo(
        s"""{
           | "id": "$messageId",
           | "blobId": "$messageId",
           | "threadId": "$messageId",
           | "size": $size
           |}""".stripMargin)

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[1][1].list[0].id")
      .inPath(s"methodResponses[1][1].list")
      .isEqualTo(s"""[{
          |  "sender": [{"email": "rcpt4@apache.org"}, {"email": "rcpt3@apache.org"}]
          |}]""".stripMargin)
  }

  @Test
  def createShouldSupportKeywords(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    val request = s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa":{
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "keywords": {
         |            "$$answered": true,
         |            "music": true
         |          }
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |     {
         |       "accountId": "$ACCOUNT_ID",
         |       "ids": ["#aaaaaa"],
         |       "properties": ["mailboxIds", "keywords"]
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

    val createResponse = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("aaaaaa")

    val messageId = createResponse
      .\("id")
      .get.asInstanceOf[JsString].value
    val size = createResponse
      .\("size")
      .get.asInstanceOf[JsNumber].value

    assertThatJson(response)
      .inPath("methodResponses[0][1].created.aaaaaa")
      .isEqualTo(
        s"""{
           | "id": "$messageId",
           | "blobId": "$messageId",
           | "threadId": "$messageId",
           | "size": $size
           |}""".stripMargin)

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[1][1].list[0].id")
      .inPath(s"methodResponses[1][1].list")
      .isEqualTo(
        s"""[{
          |  "mailboxIds": {
          |    "${mailboxId.serialize}": true
          |  },
          |  "keywords": {
          |    "$$answered": true,
          |    "music": true
          |  }
          |}]""".stripMargin)
  }

  @Test
  def createShouldSupportReceivedAt(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val receivedAt = ZonedDateTime.now().minusDays(1)

    val request = s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa":{
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "receivedAt": "${UTCDate(receivedAt).asUTC.format(UTC_DATE_FORMAT)}"
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |     {
         |       "accountId": "$ACCOUNT_ID",
         |       "ids": ["#aaaaaa"],
         |       "properties": ["mailboxIds", "receivedAt"]
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

    val createResponse = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("aaaaaa")

    val messageId = createResponse
      .\("id")
      .get.asInstanceOf[JsString].value
    val size = createResponse
      .\("size")
      .get.asInstanceOf[JsNumber].value

    assertThatJson(response)
      .inPath("methodResponses[0][1].created.aaaaaa")
      .isEqualTo(
        s"""{
           | "id": "$messageId",
           | "blobId": "$messageId",
           | "threadId": "$messageId",
           | "size": $size
           |}""".stripMargin)

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[1][1].list[0].id")
      .inPath(s"methodResponses[1][1].list")
      .isEqualTo(
        s"""[{
          |  "mailboxIds": {
          |    "${mailboxId.serialize}": true
          |  },
          |  "receivedAt": "${UTCDate(receivedAt).asUTC.format(UTC_DATE_FORMAT)}"
          |}]""".stripMargin)
  }

  @Test
  def createShouldSupportSentAt(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val sentAt = ZonedDateTime.now().minusDays(1)

    val request = s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa":{
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "sentAt": "${UTCDate(sentAt).asUTC.format(UTC_DATE_FORMAT)}"
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |     {
         |       "accountId": "$ACCOUNT_ID",
         |       "ids": ["#aaaaaa"],
         |       "properties": ["mailboxIds", "sentAt"]
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

    val createResponse = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("aaaaaa")

    val messageId = createResponse
      .\("id")
      .get.asInstanceOf[JsString].value
    val size = createResponse
      .\("size")
      .get.asInstanceOf[JsNumber].value

    assertThatJson(response)
      .inPath("methodResponses[0][1].created.aaaaaa")
      .isEqualTo(
        s"""{
           | "id": "$messageId",
           | "blobId": "$messageId",
           | "threadId": "$messageId",
           | "size": $size
           |}""".stripMargin)

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[1][1].list[0].id")
      .inPath(s"methodResponses[1][1].list")
      .isEqualTo(
        s"""[{
          |  "mailboxIds": {
          |    "${mailboxId.serialize}": true
          |  },
          |  "sentAt": "${UTCDate(sentAt).asUTC.format(UTC_DATE_FORMAT)}"
          |}]""".stripMargin)
  }

  @Test
  def createShouldSupportMessageIdHeaders(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    val request = s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa":{
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "references": ["aa@bb", "cc@dd"],
         |          "inReplyTo": ["ee@ff", "gg@hh"],
         |          "messageId": ["ii@jj", "kk@ll"]
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |     {
         |       "accountId": "$ACCOUNT_ID",
         |       "ids": ["#aaaaaa"],
         |       "properties": ["references", "inReplyTo", "messageId"]
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

    val responseAsJson = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("aaaaaa")

    val messageId = responseAsJson
      .\("id")
      .get.asInstanceOf[JsString].value
    val size = responseAsJson
      .\("size")
      .get.asInstanceOf[JsNumber].value

    assertThatJson(response)
      .inPath("methodResponses[0][1].created.aaaaaa")
      .isEqualTo(
        s"""{
           | "id": "$messageId",
           | "blobId": "$messageId",
           | "threadId": "$messageId",
           | "size": $size
           |}""".stripMargin)

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[1][1].list[0].id")
      .inPath(s"methodResponses[1][1].list")
      .isEqualTo(
        s"""[{
           |  "references": ["aa@bb", "cc@dd"],
           |  "inReplyTo": ["ee@ff", "gg@hh"],
           |  "messageId": ["ii@jj", "kk@ll"]
           |}]""".stripMargin)
  }

  @Test
  def createShouldFailIfForbidden(server: GuiceJamesServer): Unit = {
    val andrePath = MailboxPath.inbox(ANDRE)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andrePath)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa":{
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "Boredome comes from a boring mind!"
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
      .inPath("methodResponses[0][1].notCreated.aaaaaa")
      .isEqualTo(
        s"""{
          |  "description": "Mailbox ${mailboxId.serialize} can not be found",
          |  "type": "notFound"
          |}""".stripMargin)
  }

  @Test
  def createShouldRejectEmptyMailboxIds(server: GuiceJamesServer): Unit = {
    val andrePath = MailboxPath.inbox(ANDRE)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andrePath)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa":{
         |          "mailboxIds": {},
         |          "subject": "Boredome comes from a boring mind!"
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
      .inPath("methodResponses[0][1].notCreated.aaaaaa")
      .isEqualTo(
        s"""{
          |  "description": "mailboxIds need to have size 1",
          |  "type": "invalidArguments"
          |}""".stripMargin)
  }

  @Test
  def createShouldRejectInvalidMailboxIds(server: GuiceJamesServer): Unit = {
    val andrePath = MailboxPath.inbox(ANDRE)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andrePath)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa":{
         |          "mailboxIds": {
         |            "invalid": true
         |          },
         |          "subject": "Boredome comes from a boring mind!"
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
      .inPath("methodResponses[0][1].notCreated.aaaaaa")
      .isEqualTo(
        s"""{
          |  "description": "List((/mailboxIds/invalid,List(JsonValidationError(List(${invalidMessageIdMessage("invalid")}),List()))))",
          |  "type": "invalidArguments"
          |}""".stripMargin)
  }

  @Test
  def createShouldRejectNoMailboxIds(server: GuiceJamesServer): Unit = {
    val andrePath = MailboxPath.inbox(ANDRE)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andrePath)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa":{
         |          "subject": "Boredome comes from a boring mind!"
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
      .inPath("methodResponses[0][1].notCreated.aaaaaa")
      .isEqualTo(
        s"""{
          |  "description": "List((/mailboxIds,List(JsonValidationError(List(error.path.missing),List()))))",
          |  "type": "invalidArguments"
          |}""".stripMargin)
  }

  @Test
  def createShouldRejectInvalidJson(server: GuiceJamesServer): Unit = {
    val andrePath = MailboxPath.inbox(ANDRE)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andrePath)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa":{
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": ["Boredome comes from a boring mind!"]
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
      .inPath("methodResponses[0][1].notCreated.aaaaaa")
      .isEqualTo(
        s"""{
          |  "description": "List((/subject,List(JsonValidationError(List(error.expected.jsstring),List()))))",
          |  "type": "invalidArguments"
          |}""".stripMargin)
  }

  @Test
  def createShouldSucceedIfDelegated(server: GuiceJamesServer): Unit = {
    val andrePath = MailboxPath.inbox(ANDRE)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andrePath)

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andrePath, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Insert, Right.Lookup, Right.Read))

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa":{
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          }
         |        }
         |      }
         |    }, "c1"], ["Email/get",
         |     {
         |       "accountId": "$ACCOUNT_ID",
         |       "ids": ["#aaaaaa"],
         |       "properties": ["mailboxIds"]
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
      .whenIgnoringPaths("methodResponses[1][1].list[0].id")
      .inPath(s"methodResponses[1][1].list")
      .isEqualTo(
        s"""[{
           |  "mailboxIds": {
           |    "${mailboxId.serialize}": true
           |  }
           |}]""".stripMargin)
  }

  @Test
  def createShouldSupportHtmlBody(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val htmlBody: String = "<!DOCTYPE html><html><head><title></title></head><body><div>I have the most <b>brilliant</b> plan. Let me tell you all about it. What we do is, we</div></body></html>"

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "htmlBody": [
         |            {
         |              "partId": "a49d",
         |              "type": "text/html"
         |            }
         |          ],
         |          "bodyValues": {
         |            "a49d": {
         |              "value": "$htmlBody",
         |              "isTruncated": false,
         |              "isEncodingProblem": false
         |            }
         |          }
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |     {
         |       "accountId": "$ACCOUNT_ID",
         |       "ids": ["#aaaaaa"],
         |       "properties": ["mailboxIds", "subject", "bodyValues"],
         |       "fetchHTMLBodyValues": true
         |     },
         |     "c2"]
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

    val responseAsJson = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("aaaaaa")

    val messageId = responseAsJson
      .\("id")
      .get.asInstanceOf[JsString].value
    val size = responseAsJson
      .\("size")
      .get.asInstanceOf[JsNumber].value

    assertThatJson(response)
      .inPath("methodResponses[0][1].created.aaaaaa")
      .isEqualTo(
        s"""{
           | "id": "$messageId",
           | "blobId": "$messageId",
           | "threadId": "$messageId",
           | "size": $size
           |}""".stripMargin)

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[1][1].list[0].id")
      .inPath(s"methodResponses[1][1].list")
      .isEqualTo(
        s"""[{
           |  "mailboxIds": {
           |    "${mailboxId.serialize}": true
           |  },
           |  "subject": "World domination",
           |  "bodyValues": {
           |    "3": {
           |      "value": "$htmlBody",
           |      "isEncodingProblem": false,
           |      "isTruncated": false
           |    }
           |  }
           |}]""".stripMargin)
  }

  @Test
  def tooBigEmailsShouldBeRejected(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "htmlBody": [
         |            {
         |              "partId": "a49d",
         |              "type": "text/html"
         |            }
         |          ],
         |          "bodyValues": {
         |            "a49d": {
         |              "value": "${"0123456789\\r\\n".repeat(1024 * 1024)}",
         |              "isTruncated": false,
         |              "isEncodingProblem": false
         |            }
         |          }
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].notCreated.aaaaaa.type", equalTo("tooLarge"))
      .body("methodResponses[0][1].notCreated.aaaaaa.description",
        Matchers.allOf(Matchers.startsWith("Attempt to create a message of "),
          Matchers.endsWith(" bytes while the maximum allowed is 10485760")))
  }

  @Test
  def createShouldSucceedWhenPartPropertiesOmitted(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val htmlBody: String = "<!DOCTYPE html><html><head><title></title></head><body><div>I have the most <b>brilliant</b> plan. Let me tell you all about it. What we do is, we</div></body></html>"

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "htmlBody": [
         |            {
         |              "partId": "a49d",
         |              "type": "text/html"
         |            }
         |          ],
         |          "bodyValues": {
         |            "a49d": {
         |              "value": "$htmlBody"
         |            }
         |          }
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |     {
         |       "accountId": "$ACCOUNT_ID",
         |       "ids": ["#aaaaaa"],
         |       "properties": ["mailboxIds", "subject", "bodyValues"],
         |       "fetchHTMLBodyValues": true
         |     },
         |     "c2"]
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

    val responseAsJson = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("aaaaaa")

    val messageId = responseAsJson
      .\("id")
      .get.asInstanceOf[JsString].value
    val size = responseAsJson
      .\("size")
      .get.asInstanceOf[JsNumber].value

    assertThatJson(response)
      .inPath("methodResponses[0][1].created.aaaaaa")
      .isEqualTo(
        s"""{
           | "id": "$messageId",
           | "blobId": "$messageId",
           | "threadId": "$messageId",
           | "size": $size
           |}""".stripMargin)

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[1][1].list[0].id")
      .inPath(s"methodResponses[1][1].list")
      .isEqualTo(
        s"""[{
           |  "mailboxIds": {
           |    "${mailboxId.serialize}": true
           |  },
           |  "subject": "World domination",
           |  "bodyValues": {
           |    "3": {
           |      "value": "$htmlBody",
           |      "isEncodingProblem": false,
           |      "isTruncated": false
           |    }
           |  }
           |}]""".stripMargin)
  }

  @Test
  def createShouldFailWhenMultipleBodyParts(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val htmlBody: String = "<!DOCTYPE html><html><head><title></title></head><body><div>I have the most <b>brilliant</b> plan. Let me tell you all about it. What we do is, we</div></body></html>"

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "htmlBody": [
         |            {
         |              "partId": "a49d",
         |              "type": "text/html"
         |            },
         |            {
         |              "partId": "a49e",
         |              "type": "text/html"
         |            }
         |          ],
         |          "bodyValues": {
         |            "a49d": {
         |              "value": "$htmlBody",
         |              "isTruncated": false
         |            }
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
      .whenIgnoringPaths("methodResponses[0][1].created.aaaaaa.id")
      .inPath("methodResponses[0][1].notCreated.aaaaaa")
      .isEqualTo(
        s"""{
           |  "type": "invalidArguments",
           |  "description": "Expecting htmlBody to contains only 1 part"
           |}""".stripMargin)
  }

  @Test
  def createShouldFailWhenPartIdMisMatch(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val htmlBody: String = "<!DOCTYPE html><html><head><title></title></head><body><div>I have the most <b>brilliant</b> plan. Let me tell you all about it. What we do is, we</div></body></html>"

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "htmlBody": [
         |            {
         |              "partId": "a49d",
         |              "type": "text/html"
         |            }
         |          ],
         |          "bodyValues": {
         |            "a49e": {
         |              "value": "$htmlBody",
         |              "isTruncated": false
         |            }
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
      .whenIgnoringPaths("methodResponses[0][1].created.aaaaaa.id")
      .inPath("methodResponses[0][1].notCreated.aaaaaa")
      .isEqualTo(
        s"""{
           |  "type": "invalidArguments",
           |  "description": "Expecting bodyValues to contain the part specified in htmlBody"
           |}""".stripMargin)
  }

  @Test
  def createShouldFailWhenHtmlBodyIsNotHtmlType(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val htmlBody: String = "<!DOCTYPE html><html><head><title></title></head><body><div>I have the most <b>brilliant</b> plan. Let me tell you all about it. What we do is, we</div></body></html>"

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "htmlBody": [
         |            {
         |              "partId": "a49d",
         |              "type": "text/plain"
         |            }
         |          ],
         |          "bodyValues": {
         |            "a49d": {
         |              "value": "$htmlBody",
         |              "isTruncated": false
         |            }
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
      .whenIgnoringPaths("methodResponses[0][1].created.aaaaaa.id")
      .inPath("methodResponses[0][1].notCreated.aaaaaa")
      .isEqualTo(
        s"""{
           |  "type": "invalidArguments",
           |  "description": "Expecting htmlBody type to be text/html"
           |}""".stripMargin)
  }

  @Test
  def createShouldFailWhenIsTruncatedIsTrue(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val htmlBody: String = "<!DOCTYPE html><html><head><title></title></head><body><div>I have the most <b>brilliant</b> plan. Let me tell you all about it. What we do is, we</div></body></html>"

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "htmlBody": [
         |            {
         |              "partId": "a49d",
         |              "type": "text/html"
         |            }
         |          ],
         |          "bodyValues": {
         |            "a49d": {
         |              "value": "$htmlBody",
         |              "isTruncated": true
         |            }
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
      .whenIgnoringPaths("methodResponses[0][1].created.aaaaaa.id")
      .inPath("methodResponses[0][1].notCreated.aaaaaa")
      .isEqualTo(
        s"""{
           |  "type": "invalidArguments",
           |  "description": "Expecting isTruncated to be false"
           |}""".stripMargin)
  }

  @Test
  def createShouldFailWhenIsEncodingProblemIsTrue(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val htmlBody: String = "<!DOCTYPE html><html><head><title></title></head><body><div>I have the most <b>brilliant</b> plan. Let me tell you all about it. What we do is, we</div></body></html>"

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "htmlBody": [
         |            {
         |              "partId": "a49d",
         |              "type": "text/html"
         |            }
         |          ],
         |          "bodyValues": {
         |            "a49d": {
         |              "value": "$htmlBody",
         |              "isEncodingProblem": true
         |            }
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
      .whenIgnoringPaths("methodResponses[0][1].created.aaaaaa.id")
      .inPath("methodResponses[0][1].notCreated.aaaaaa")
      .isEqualTo(
        s"""{
           |  "type": "invalidArguments",
           |  "description": "Expecting isEncodingProblem to be false"
           |}""".stripMargin)
  }

  @Test
  def createShouldFailWhenCharsetIsSpecified(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val htmlBody: String = "<!DOCTYPE html><html><head><title></title></head><body><div>I have the most <b>brilliant</b> plan. Let me tell you all about it. What we do is, we</div></body></html>"

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "htmlBody": [
         |            {
         |              "partId": "a49d",
         |              "type": "text/html",
         |              "charset": "UTF-8"
         |            }
         |          ],
         |          "bodyValues": {
         |            "a49d": {
         |              "value": "$htmlBody"
         |            }
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
      .whenIgnoringPaths("methodResponses[0][1].created.aaaaaa.id")
      .inPath("methodResponses[0][1].notCreated.aaaaaa")
      .isEqualTo(
        s"""{
           |  "type": "invalidArguments",
           |  "description": "List((/htmlBody(0),List(JsonValidationError(List(charset must not be specified in htmlBody),List()))))"
           |}""".stripMargin)
  }

  @Test
  def createShouldFailWhenSizeIsSpecified(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val htmlBody: String = "<!DOCTYPE html><html><head><title></title></head><body><div>I have the most <b>brilliant</b> plan. Let me tell you all about it. What we do is, we</div></body></html>"

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "htmlBody": [
         |            {
         |              "partId": "a49d",
         |              "type": "text/html",
         |              "size": 123
         |            }
         |          ],
         |          "bodyValues": {
         |            "a49d": {
         |              "value": "$htmlBody"
         |            }
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
      .whenIgnoringPaths("methodResponses[0][1].created.aaaaaa.id")
      .inPath("methodResponses[0][1].notCreated.aaaaaa")
      .isEqualTo(
        s"""{
           |  "type": "invalidArguments",
           |  "description": "List((/htmlBody(0),List(JsonValidationError(List(size must not be specified in htmlBody),List()))))"
           |}""".stripMargin)
  }

  @Test
  def createShouldFailWhenContentTransferEncodingIsSpecified(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val htmlBody: String = "<!DOCTYPE html><html><head><title></title></head><body><div>I have the most <b>brilliant</b> plan. Let me tell you all about it. What we do is, we</div></body></html>"

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "htmlBody": [
         |            {
         |              "partId": "a49d",
         |              "type": "text/html",
         |              "header:Content-Transfer-Encoding:asText": "8BIT"
         |            }
         |          ],
         |          "bodyValues": {
         |            "a49d": {
         |              "value": "$htmlBody"
         |            }
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
      .whenIgnoringPaths("methodResponses[0][1].created.aaaaaa.id")
      .inPath("methodResponses[0][1].notCreated.aaaaaa")
      .isEqualTo(
        s"""{
           |  "type": "invalidArguments",
           |  "description": "List((/htmlBody(0),List(JsonValidationError(List(Content-Transfer-Encoding must not be specified in htmlBody or textBody),List()))))"
           |}""".stripMargin)
  }

  @Test
  def createShouldSupportAttachment(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val payload = "123456789\r\n".getBytes(StandardCharsets.UTF_8)

    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType("text/plain")
      .body(payload)
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val blobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "attachments": [
         |            {
         |              "blobId": "$blobId",
         |              "type":"text/plain",
         |              "charset":"UTF-8",
         |              "disposition": "attachment",
         |              "language": ["fr", "en"],
         |              "location": "http://125.26.23.36/content"
         |            }
         |          ]
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |      {
         |        "accountId": "$ACCOUNT_ID",
         |        "ids": ["#aaaaaa"],
         |        "properties": ["mailboxIds", "subject", "attachments"],
         |        "bodyProperties": ["partId", "blobId", "size", "name", "type", "charset", "disposition", "cid", "language", "location"]
         |      },
         |    "c2"]
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

    val responseAsJson = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("aaaaaa")

    val messageId = responseAsJson
      .\("id")
      .get.asInstanceOf[JsString].value
    val size = responseAsJson
      .\("size")
      .get.asInstanceOf[JsNumber].value

    assertThatJson(response)
      .inPath("methodResponses[0][1].created.aaaaaa")
      .isEqualTo(
        s"""{
           | "id": "$messageId",
           | "blobId": "$messageId",
           | "threadId": "$messageId",
           | "size": $size
           |}""".stripMargin)

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[1][1].list[0].id")
      .inPath(s"methodResponses[1][1].list")
      .isEqualTo(
        s"""[{
           |  "mailboxIds": {
           |    "${mailboxId.serialize}": true
           |  },
           |  "subject": "World domination",
           |  "attachments": [
           |    {
           |      "partId": "4",
           |      "blobId": "${messageId}_4",
           |      "size": 11,
           |      "type": "text/plain",
           |      "charset": "UTF-8",
           |      "disposition": "attachment",
           |      "language": ["fr", "en"],
           |      "location": "http://125.26.23.36/content"
           |    }
           |  ]
           |}]""".stripMargin)

    val downloadResponse = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$accountId/${messageId}_4")
    .`then`
      .statusCode(SC_OK)
      .contentType("text/plain")
      .extract
      .body
      .asInputStream()

    assertThat(downloadResponse)
      .hasSameContentAs(new ByteArrayInputStream(payload))
  }

  @Test
  def createShouldSupportCustomCharsetInAttachment(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val payload = "123456789\r\n".getBytes(StandardCharsets.UTF_8)

    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType("text/plain")
      .body(payload)
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val blobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "attachments": [
         |            {
         |              "blobId": "$blobId",
         |              "type":"text/plain",
         |              "charset":"ascii",
         |              "disposition": "attachment"
         |            }
         |          ]
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |      {
         |        "accountId": "$ACCOUNT_ID",
         |        "ids": ["#aaaaaa"],
         |        "properties": ["mailboxIds", "subject", "attachments"],
         |        "bodyProperties": ["partId", "blobId", "size", "name", "type", "charset", "disposition", "cid"]
         |      },
         |    "c2"]
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

    val responseAsJson = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("aaaaaa")

    val messageId = responseAsJson
      .\("id")
      .get.asInstanceOf[JsString].value
    val size = responseAsJson
      .\("size")
      .get.asInstanceOf[JsNumber].value

    assertThatJson(response)
      .inPath("methodResponses[0][1].created.aaaaaa")
      .isEqualTo(
        s"""{
           | "id": "$messageId",
           | "blobId": "$messageId",
           | "threadId": "$messageId",
           | "size": $size
           |}""".stripMargin)

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[1][1].list[0].id")
      .inPath(s"methodResponses[1][1].list")
      .isEqualTo(
        s"""[{
           |  "mailboxIds": {
           |    "${mailboxId.serialize}": true
           |  },
           |  "subject": "World domination",
           |  "attachments": [
           |    {
           |      "partId": "4",
           |      "blobId": "${messageId}_4",
           |      "size": 11,
           |      "type": "text/plain",
           |      "charset": "ascii",
           |      "disposition": "attachment"
           |    }
           |  ]
           |}]""".stripMargin)

    val downloadResponse = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$accountId/${messageId}_4")
    .`then`
      .statusCode(SC_OK)
      .contentType("text/plain")
      .extract
      .body
      .asInputStream()

    assertThat(downloadResponse)
      .hasSameContentAs(new ByteArrayInputStream(payload))
  }

  @Test
  def rejectAttahmentCreationRequestWithContentTransferEncoding(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val payload = "123456789\r\n".getBytes(StandardCharsets.UTF_8)

    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType("text/plain")
      .body(payload)
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val blobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "attachments": [
         |            {
         |              "blobId": "$blobId",
         |              "type":"text/plain",
         |              "charset":"UTF-8",
         |              "header:Content-Transfer-Encoding:asText":"7bit"
         |            }
         |          ]
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
      .inPath("methodResponses[0][1].notCreated.aaaaaa")
      .isEqualTo(
        s"""{
           |  "type": "invalidArguments",
           |  "description": "List((/attachments(0),List(JsonValidationError(List(Content-Transfer-Encoding should not be specified on attachment),List()))))"
           |}""".stripMargin)
  }

  @Test
  def createShouldSupportAttachmentAndHtmlBody(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val payload = "123456789\r\n".getBytes(StandardCharsets.UTF_8)
    val htmlBody: String = "<!DOCTYPE html><html><head><title></title></head><body><div>I have the most <b>brilliant</b> plan. Let me tell you all about it. What we do is, we</div></body></html>"

    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType("text/plain")
      .body(payload)
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val blobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "attachments": [
         |            {
         |              "blobId": "$blobId",
         |              "type":"text/plain",
         |              "charset":"UTF-8",
         |              "disposition": "attachment"
         |            }
         |          ],
         |          "htmlBody": [
         |            {
         |              "partId": "a49d",
         |              "type": "text/html"
         |            }
         |          ],
         |          "bodyValues": {
         |            "a49d": {
         |              "value": "$htmlBody",
         |              "isTruncated": false,
         |              "isEncodingProblem": false
         |            }
         |          }
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |      {
         |        "accountId": "$ACCOUNT_ID",
         |        "ids": ["#aaaaaa"],
         |        "properties": ["mailboxIds", "subject", "attachments", "htmlBody", "bodyValues"],
         |        "fetchHTMLBodyValues": true
         |      },
         |    "c2"]
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

    val responseAsJson = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("aaaaaa")

    val messageId = responseAsJson
      .\("id")
      .get.asInstanceOf[JsString].value
    val size = responseAsJson
      .\("size")
      .get.asInstanceOf[JsNumber].value

    assertThatJson(response)
      .inPath("methodResponses[0][1].created.aaaaaa")
      .isEqualTo(
        s"""{
           | "id": "$messageId",
           | "blobId": "$messageId",
           | "threadId": "$messageId",
           | "size": $size
           |}""".stripMargin)

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[1][1].list[0].id")
      .inPath(s"methodResponses[1][1].list")
      .isEqualTo(
        s"""[{
           |  "mailboxIds": {
           |    "${mailboxId.serialize}": true
           |  },
           |  "subject": "World domination",
           |  "attachments": [
           |    {
           |      "partId": "5",
           |      "blobId": "${messageId}_5",
           |      "size": 11,
           |      "type": "text/plain",
           |      "charset": "UTF-8",
           |      "disposition": "attachment"
           |    }
           |  ],
           |  "htmlBody": [
           |    {
           |      "partId": "4",
           |      "blobId": "${messageId}_4",
           |      "size": 166,
           |      "type": "text/html",
           |      "charset": "UTF-8"
           |    }
           |  ],
           |  "bodyValues": {
           |    "4": {
           |      "value": "$htmlBody",
           |      "isEncodingProblem": false,
           |      "isTruncated": false
           |    }
           |  }
           |}]""".stripMargin)
  }

  @Test
  def createShouldSupportAttachmentAndTextBody(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val payload = "123456789\r\n".getBytes(StandardCharsets.UTF_8)
    val textBody: String = "Let me tell you all about it."

    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType("text/plain")
      .body(payload)
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val blobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "attachments": [
         |            {
         |              "blobId": "$blobId",
         |              "type":"text/plain",
         |              "charset":"UTF-8",
         |              "disposition": "attachment"
         |            }
         |          ],
         |          "textBody": [
         |            {
         |              "partId": "a49d",
         |              "type": "text/plain"
         |            }
         |          ],
         |          "bodyValues": {
         |            "a49d": {
         |              "value": "$textBody",
         |              "isTruncated": false,
         |              "isEncodingProblem": false
         |            }
         |          }
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |      {
         |        "accountId": "$ACCOUNT_ID",
         |        "ids": ["#aaaaaa"],
         |        "properties": ["mailboxIds", "subject", "attachments", "textBody", "bodyValues"],
         |        "fetchTextBodyValues": true
         |      },
         |    "c2"]
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

    val responseAsJson = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("aaaaaa")

    val messageId = responseAsJson
      .\("id")
      .get.asInstanceOf[JsString].value
    val size = responseAsJson
      .\("size")
      .get.asInstanceOf[JsNumber].value

    assertThatJson(response)
      .inPath("methodResponses[0][1].created.aaaaaa")
      .isEqualTo(
        s"""{
           | "id": "$messageId",
           | "blobId": "$messageId",
           | "threadId": "$messageId",
           | "size": $size
           |}""".stripMargin)

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[1][1].list[0].id")
      .inPath(s"methodResponses[1][1].list")
      .isEqualTo(
        s"""[{
           |  "mailboxIds": {
           |    "${mailboxId.serialize}": true
           |  },
           |  "subject": "World domination",
           |  "attachments": [
           |    {
           |      "partId": "4",
           |      "blobId": "${messageId}_4",
           |      "size": 11,
           |      "type": "text/plain",
           |      "charset": "UTF-8",
           |      "disposition": "attachment"
           |    }
           |  ],
           |  "textBody": [
           |    {
           |      "partId": "3",
           |      "blobId": "${messageId}_3",
           |      "size": 29,
           |      "type": "text/plain",
           |      "charset": "UTF-8"
           |    }
           |  ],
           |  "bodyValues": {
           |    "3": {
           |      "value": "$textBody",
           |      "isEncodingProblem": false,
           |      "isTruncated": false
           |    }
           |  }
           |}]""".stripMargin)
  }

  @Test
  def textContentTransferEncodingShouldBeRejectedInTextBody(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val textBody: String = "Let me tell you all about it."

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "attachments": [],
         |          "textBody": [
         |            {
         |              "partId": "a49d",
         |              "type": "text/plain",
         |              "header:Content-Transfer-Encoding:asText": "gabou"
         |            }
         |          ],
         |          "bodyValues": {
         |            "a49d": {
         |              "value": "$textBody",
         |              "isTruncated": false,
         |              "isEncodingProblem": false
         |            }
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
      .inPath("methodResponses[0][1].notCreated.aaaaaa")
      .isEqualTo(
        s"""{
           |  "type":"invalidArguments",
           |  "description":"List((/textBody(0),List(JsonValidationError(List(Content-Transfer-Encoding must not be specified in htmlBody or textBody),List()))))"
           |}""".stripMargin)
  }

  @Test
  def binaryContentTransferEncodingShouldBeRejectedInTextBody(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val textBody: String = "Let me tell you all about it."

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "attachments": [],
         |          "textBody": [
         |            {
         |              "partId": "a49d",
         |              "type": "text/plain",
         |              "header:Content-Transfer-Encoding": " gabou"
         |            }
         |          ],
         |          "bodyValues": {
         |            "a49d": {
         |              "value": "$textBody",
         |              "isTruncated": false,
         |              "isEncodingProblem": false
         |            }
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
      .inPath("methodResponses[0][1].notCreated.aaaaaa")
      .isEqualTo(
        s"""{
           |  "type":"invalidArguments",
           |  "description":"List((/textBody(0),List(JsonValidationError(List(Content-Transfer-Encoding must not be specified in htmlBody or textBody),List()))))"
           |}""".stripMargin)
  }

  @Test
  def createShouldSupportInlinedAttachmentsMixedWithRegularAttachmentsAndHtmlBody(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val payload = "123456789\r\n".getBytes(StandardCharsets.UTF_8)
    val htmlBody: String = "<!DOCTYPE html><html><head><title></title></head><body><div>I have the most <b>brilliant</b> plan. Let me tell you all about it. What we do is, we</div></body></html>"

    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType("text/plain")
      .body(payload)
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val blobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "attachments": [
         |            {
         |              "blobId": "$blobId",
         |              "type":"text/plain",
         |              "charset":"UTF-8",
         |              "disposition": "inline",
         |              "cid": "abc"
         |            },
         |            {
         |              "blobId": "$blobId",
         |              "type":"text/plain",
         |              "charset":"UTF-8",
         |              "disposition": "inline",
         |              "cid": "def"
         |            },
         |            {
         |              "blobId": "$blobId",
         |              "type":"text/plain",
         |              "charset":"UTF-8",
         |              "disposition": "attachment"
         |            }
         |          ],
         |          "htmlBody": [
         |            {
         |              "partId": "a49d",
         |              "type": "text/html"
         |            }
         |          ],
         |          "bodyValues": {
         |            "a49d": {
         |              "value": "$htmlBody",
         |              "isTruncated": false,
         |              "isEncodingProblem": false
         |            }
         |          }
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |      {
         |        "accountId": "$ACCOUNT_ID",
         |        "ids": ["#aaaaaa"],
         |        "properties": ["mailboxIds", "subject", "attachments", "htmlBody", "bodyValues"],
         |        "fetchHTMLBodyValues": true
         |      },
         |    "c2"]
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

    val responseAsJson = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("aaaaaa")

    val messageId = responseAsJson
      .\("id")
      .get.asInstanceOf[JsString].value
    val size = responseAsJson
      .\("size")
      .get.asInstanceOf[JsNumber].value

    assertThatJson(response)
      .inPath("methodResponses[0][1].created.aaaaaa")
      .isEqualTo(
        s"""{
           | "id": "$messageId",
           | "blobId": "$messageId",
           | "threadId": "$messageId",
           | "size": $size
           |}""".stripMargin)

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[1][1].list[0].id")
      .inPath(s"methodResponses[1][1].list")
      .isEqualTo(
        s"""[{
           |  "mailboxIds": {
           |    "${mailboxId.serialize}": true
           |  },
           |  "subject": "World domination",
           |  "attachments": [
           |    {
           |      "partId": "6",
           |      "blobId": "${messageId}_6",
           |      "size": 11,
           |      "type": "text/plain",
           |      "charset": "UTF-8",
           |      "disposition": "inline",
           |      "cid": "abc"
           |    },
           |    {
           |      "partId": "7",
           |      "blobId": "${messageId}_7",
           |      "size": 11,
           |      "type": "text/plain",
           |      "charset": "UTF-8",
           |      "disposition": "inline",
           |      "cid": "def"
           |    },
           |    {
           |      "partId": "8",
           |      "blobId": "${messageId}_8",
           |      "size": 11,
           |      "type": "text/plain",
           |      "charset": "UTF-8",
           |      "disposition": "attachment"
           |    }
           |  ],
           |  "htmlBody": [
           |    {
           |      "partId": "5",
           |      "blobId": "${messageId}_5",
           |      "size": 166,
           |      "type": "text/html",
           |      "charset": "UTF-8"
           |    },
           |    {
           |        "charset": "UTF-8",
           |        "disposition": "inline",
           |        "size": 11,
           |        "partId": "6",
           |        "blobId": "${messageId}_6",
           |        "type": "text/plain",
           |        "cid": "abc"
           |    },
           |    {
           |        "charset": "UTF-8",
           |        "disposition": "inline",
           |        "size": 11,
           |        "partId": "7",
           |        "blobId": "${messageId}_7",
           |        "type": "text/plain",
           |        "cid": "def"
           |    }
           |  ],
           |  "bodyValues": {
           |    "5": {
           |      "value": "$htmlBody",
           |      "isEncodingProblem": false,
           |      "isTruncated": false
           |    },
           |    "6": {
           |        "value": "123456789\\r\\n",
           |        "isEncodingProblem": false,
           |        "isTruncated": false
           |    },
           |    "7": {
           |        "value": "123456789\\r\\n",
           |        "isEncodingProblem": false,
           |        "isTruncated": false
           |    }
           |  }
           |}]""".stripMargin)
  }

  @Test
  def inlinedAttachmentsShouldBeWrappedInRelatedMultipart(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val payload = "123456789\r\n".getBytes(StandardCharsets.UTF_8)
    val htmlBody: String = "<!DOCTYPE html><html><head><title></title></head><body><div>I have the most <b>brilliant</b> plan. Let me tell you all about it. What we do is, we</div></body></html>"

    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType("text/plain")
      .body(payload)
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val blobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "attachments": [
         |            {
         |              "blobId": "$blobId",
         |              "type":"text/plain",
         |              "charset":"UTF-8",
         |              "disposition": "inline",
         |              "cid": "abc"
         |            },
         |            {
         |              "blobId": "$blobId",
         |              "type":"text/plain",
         |              "charset":"UTF-8",
         |              "disposition": "inline",
         |              "cid": "def"
         |            },
         |            {
         |              "blobId": "$blobId",
         |              "type":"text/plain",
         |              "charset":"UTF-8",
         |              "disposition": "attachment"
         |            }
         |          ],
         |          "htmlBody": [
         |            {
         |              "partId": "a49d",
         |              "type": "text/html"
         |            }
         |          ],
         |          "bodyValues": {
         |            "a49d": {
         |              "value": "$htmlBody",
         |              "isTruncated": false,
         |              "isEncodingProblem": false
         |            }
         |          }
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |      {
         |        "accountId": "$ACCOUNT_ID",
         |        "ids": ["#aaaaaa"],
         |        "properties": ["bodyStructure"],
         |        "bodyProperties": ["type", "disposition", "cid", "subParts"]
         |      },
         |    "c2"]
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

    val responseAsJson = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("aaaaaa")

    val messageId = responseAsJson
      .\("id")
      .get.asInstanceOf[JsString].value
    responseAsJson
      .\("size")
      .get.asInstanceOf[JsNumber].value

    assertThatJson(response)
      .inPath(s"methodResponses[1][1].list[0]")
      .isEqualTo(
        s"""{
           |  "id": "$messageId",
           |  "bodyStructure": {
           |    "type": "multipart/mixed",
           |    "subParts": [
           |      {
           |        "type": "multipart/related",
           |        "subParts": [
           |          {
           |            "type": "multipart/alternative",
           |            "subParts": [
           |              {
           |                "type": "text/plain"
           |              },
           |              {
           |                "type": "text/html"
           |              }
           |            ]
           |          },
           |          {
           |            "type": "text/plain",
           |            "disposition": "inline",
           |            "cid": "abc"
           |          },
           |          {
           |            "type": "text/plain",
           |            "disposition": "inline",
           |            "cid": "def"
           |          }
           |        ]
           |      },
           |      {
           |        "type": "text/plain",
           |        "disposition": "attachment"
           |      }
           |    ]
           |  }
           |}""".stripMargin)
  }

  @Test
  def htmlBodyPartWithOnlyNormalAttachmentsShouldNotBeWrappedInARelatedMultipart(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val payload = "123456789\r\n".getBytes(StandardCharsets.UTF_8)
    val htmlBody: String = "<!DOCTYPE html><html><head><title></title></head><body><div>I have the most <b>brilliant</b> plan. Let me tell you all about it. What we do is, we</div></body></html>"

    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType("text/plain")
      .body(payload)
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val blobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "attachments": [
         |            {
         |              "blobId": "$blobId",
         |              "type":"text/plain",
         |              "charset":"UTF-8",
         |              "disposition": "attachment"
         |            }
         |          ],
         |          "htmlBody": [
         |            {
         |              "partId": "a49d",
         |              "type": "text/html"
         |            }
         |          ],
         |          "bodyValues": {
         |            "a49d": {
         |              "value": "$htmlBody",
         |              "isTruncated": false,
         |              "isEncodingProblem": false
         |            }
         |          }
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |      {
         |        "accountId": "$ACCOUNT_ID",
         |        "ids": ["#aaaaaa"],
         |        "properties": ["bodyStructure"],
         |        "bodyProperties": ["type", "disposition", "cid", "subParts"]
         |      },
         |    "c2"]
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

    val responseAsJson = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("aaaaaa")

    val messageId = responseAsJson
      .\("id")
      .get.asInstanceOf[JsString].value
    val size = responseAsJson
      .\("size")
      .get.asInstanceOf[JsNumber].value

    assertThatJson(response)
      .inPath("methodResponses[0][1].created.aaaaaa")
      .isEqualTo(
        s"""{
           | "id": "$messageId",
           | "blobId": "$messageId",
           | "threadId": "$messageId",
           | "size": $size
           |}""".stripMargin)

    assertThatJson(response)
      .inPath(s"methodResponses[1][1].list[0]")
      .isEqualTo(
        s"""{
           |  "id": "$messageId",
           |  "bodyStructure": {
           |    "type": "multipart/mixed",
           |    "subParts": [
           |      {
           |        "type":"multipart/alternative",
           |        "subParts": [
           |          {
           |            "type":"text/plain"
           |          },
           |          {
           |            "type":"text/html"
           |          }
           |        ]
           |      },
           |      {
           |        "type": "text/plain",
           |        "disposition": "attachment"
           |      }
           |    ]
           |  }
           |}""".stripMargin)
  }

  @Test
  def bodyPartShouldSupportSpecificHeaders(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val payload = "123456789\r\n".getBytes(StandardCharsets.UTF_8)
    val htmlBody: String = "<!DOCTYPE html><html><head><title></title></head><body><div>I have the most <b>brilliant</b> plan. Let me tell you all about it. What we do is, we</div></body></html>"

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "htmlBody": [
         |            {
         |              "partId": "a49d",
         |              "type": "text/html"
         |            }
         |          ],
         |          "bodyValues": {
         |            "a49d": {
         |              "value": "$htmlBody",
         |              "isTruncated": false,
         |              "isEncodingProblem": false,
         |              "header:Specific:asText": "MATCHME"
         |            }
         |          }
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |      {
         |        "accountId": "$ACCOUNT_ID",
         |        "ids": ["#aaaaaa"],
         |        "properties": ["bodyStructure"],
         |        "bodyProperties": ["type", "disposition", "cid", "subParts", "header:Specific:asText"]
         |      },
         |    "c2"]
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

    val responseAsJson = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("aaaaaa")

    val messageId = responseAsJson
      .\("id")
      .get.asInstanceOf[JsString].value
    val size = responseAsJson
      .\("size")
      .get.asInstanceOf[JsNumber].value

    assertThatJson(response)
      .inPath("methodResponses[0][1].created.aaaaaa")
      .isEqualTo(
        s"""{
           | "id": "$messageId",
           | "blobId": "$messageId",
           | "threadId": "$messageId",
           | "size": $size
           |}""".stripMargin)

    assertThatJson(response)
      .inPath(s"methodResponses[1][1].list[0]")
      .isEqualTo(
        s"""{
           |  "id": "$messageId",
           |  "bodyStructure": {
           |    "subParts": [
           |      {
           |        "header:Specific:asText": "MATCHME",
           |        "type": "text/plain"
           |      },
           |      {
           |        "header:Specific:asText": "MATCHME",
           |        "type": "text/html"
           |      }
           |    ],
           |    "header:Specific:asText": null,
           |    "type": "multipart/alternative"
           |  }
           |}""".stripMargin)
  }

  @Test
  def inlinedAttachmentsOnlyShouldNotBeWrappedInAMixedMultipart(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val payload = "123456789\r\n".getBytes(StandardCharsets.UTF_8)
    val htmlBody: String = "<!DOCTYPE html><html><head><title></title></head><body><div>I have the most <b>brilliant</b> plan. Let me tell you all about it. What we do is, we</div></body></html>"

    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType("text/plain")
      .body(payload)
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val blobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "attachments": [
         |            {
         |              "blobId": "$blobId",
         |              "type":"text/plain",
         |              "charset":"UTF-8",
         |              "disposition": "inline",
         |              "cid": "abc"
         |            },
         |            {
         |              "blobId": "$blobId",
         |              "type":"text/plain",
         |              "charset":"UTF-8",
         |              "disposition": "inline",
         |              "cid": "def"
         |            }
         |          ],
         |          "htmlBody": [
         |            {
         |              "partId": "a49d",
         |              "type": "text/html"
         |            }
         |          ],
         |          "bodyValues": {
         |            "a49d": {
         |              "value": "$htmlBody",
         |              "isTruncated": false,
         |              "isEncodingProblem": false
         |            }
         |          }
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |      {
         |        "accountId": "$ACCOUNT_ID",
         |        "ids": ["#aaaaaa"],
         |        "properties": ["bodyStructure"],
         |        "bodyProperties": ["type", "disposition", "cid", "subParts"]
         |      },
         |    "c2"]
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

    val responseAsJson = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("aaaaaa")

    val messageId = responseAsJson
      .\("id")
      .get.asInstanceOf[JsString].value
    val size = responseAsJson
      .\("size")
      .get.asInstanceOf[JsNumber].value

    assertThatJson(response)
      .inPath("methodResponses[0][1].created.aaaaaa")
      .isEqualTo(
        s"""{
           | "id": "$messageId",
           | "blobId": "$messageId",
           | "threadId": "$messageId",
           | "size": $size
           |}""".stripMargin)

    assertThatJson(response)
      .inPath(s"methodResponses[1][1].list[0]")
      .isEqualTo(
        s"""{
           |  "id": "$messageId",
           |  "bodyStructure": {
           |    "type": "multipart/related",
           |    "subParts": [
           |      {
           |        "type":"multipart/alternative",
           |        "subParts": [
           |          {
           |            "type":"text/plain"
           |          },
           |          {
           |            "type":"text/html"
           |          }
           |        ]
           |      },
           |      {
           |        "type": "text/plain",
           |        "disposition": "inline",
           |        "cid": "abc"
           |      },
           |      {
           |        "type": "text/plain",
           |        "disposition": "inline",
           |        "cid": "def"
           |      }
           |    ]
           |  }
           |}""".stripMargin)
  }

  @Test
  def htmlBodyOnlyShouldNotBeWrappedInMultiparts(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val payload = "123456789\r\n".getBytes(StandardCharsets.UTF_8)
    val htmlBody: String = "<!DOCTYPE html><html><head><title></title></head><body><div>I have the most <b>brilliant</b> plan. Let me tell you all about it. What we do is, we</div></body></html>"

    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType("text/plain")
      .body(payload)
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "attachments": [],
         |          "htmlBody": [
         |            {
         |              "partId": "a49d",
         |              "type": "text/html"
         |            }
         |          ],
         |          "bodyValues": {
         |            "a49d": {
         |              "value": "$htmlBody",
         |              "isTruncated": false,
         |              "isEncodingProblem": false
         |            }
         |          }
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |      {
         |        "accountId": "$ACCOUNT_ID",
         |        "ids": ["#aaaaaa"],
         |        "properties": ["bodyStructure"],
         |        "bodyProperties": ["type", "disposition", "cid", "subParts"]
         |      },
         |    "c2"]
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

    val responseAsJson = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("aaaaaa")

    val messageId = responseAsJson
      .\("id")
      .get.asInstanceOf[JsString].value
    val size = responseAsJson
      .\("size")
      .get.asInstanceOf[JsNumber].value

    assertThatJson(response)
      .inPath("methodResponses[0][1].created.aaaaaa")
      .isEqualTo(
        s"""{
           | "id": "$messageId",
           | "blobId": "$messageId",
           | "threadId": "$messageId",
           | "size": $size
           |}""".stripMargin)

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState",
        "methodResponses[0][1].newState",
        "methodResponses[1][1].state")
      .isEqualTo(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [
           |    ["Email/set", {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "newState": "${INSTANCE.value}",
           |      "created": {
           |        "aaaaaa": {
           |          "id": "$messageId",
           |          "blobId": "$messageId",
           |          "threadId": "$messageId",
           |          "size": $size
           |        }
           |      }
           |    }, "c1"],
           |    ["Email/get", {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "list": [
           |        {
           |          "id": "$messageId",
           |          "bodyStructure": {
           |            "type":"multipart/alternative",
           |            "subParts": [
           |              {
           |                "type":"text/plain"
           |              },
           |              {
           |                "type":"text/html"
           |              }
           |            ]
           |          }
           |        }
           |      ], "notFound": []
           |    }, "c2"]
           |  ]
           |}""".stripMargin)
  }

  @Test
  def createShouldSupportHtmlAndTextBody(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val htmlBody: String = "<!DOCTYPE html><html><head><title></title></head><body><div>I have the most <b>brilliant</b> plan. Let me tell you all about it. What we do is, we</div></body></html>"

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "htmlBody": [
         |            {
         |              "partId": "a49d",
         |              "type": "text/html"
         |            }
         |          ],
         |          "bodyValues": {
         |            "a49d": {
         |              "value": "$htmlBody",
         |              "isTruncated": false,
         |              "isEncodingProblem": false
         |            }
         |          }
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |      {
         |        "accountId": "$ACCOUNT_ID",
         |        "ids": ["#aaaaaa"],
         |        "properties": ["bodyStructure", "bodyValues"],
         |        "bodyProperties": ["type", "disposition", "cid", "subParts", "charset"],
         |        "fetchAllBodyValues": true
         |      },
         |    "c2"]
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

    val responseAsJson = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("aaaaaa")

    val messageId = responseAsJson
      .\("id")
      .get.asInstanceOf[JsString].value
    val size = responseAsJson
      .\("size")
      .get.asInstanceOf[JsNumber].value

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState",
        "methodResponses[1][1].state")
      .isEqualTo(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [
           |    ["Email/set", {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "oldState": "${State.INITIAL.getValue}",
           |      "created": {
           |        "aaaaaa": {
           |          "id": "$messageId",
           |          "blobId": "$messageId",
           |          "threadId": "$messageId",
           |          "size": $size
           |        }
           |      }
           |    }, "c1"],
           |    ["Email/get", {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "list": [
           |        {
           |          "id": "$messageId",
           |          "bodyStructure": {
           |            "type": "multipart/alternative",
           |            "charset": "us-ascii",
           |            "subParts": [
           |              {
           |                "type": "text/plain",
           |                "charset": "UTF-8"
           |              },
           |              {
           |                "type": "text/html",
           |                "charset": "UTF-8"
           |              }
           |            ]
           |          },
           |          "bodyValues": {
           |            "3": {
           |              "value": "$htmlBody",
           |              "isEncodingProblem": false,
           |              "isTruncated": false
           |            },
           |            "2": {
           |              "value": "I have the most brilliant plan. Let me tell you all about it. What we do is, we",
           |              "isEncodingProblem": false,
           |              "isTruncated": false
           |            }
           |          }
           |        }
           |      ],
           |      "notFound": []
           |    }, "c2"]
           |  ]
           |}""".stripMargin)
  }

  @Test
  def createShouldWrapInlineBodyWithAlternativeMultipart(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val payload = "123456789\r\n".getBytes(StandardCharsets.UTF_8)
    val htmlBody: String = "<!DOCTYPE html><html><head><title></title></head><body><div>I have the most <b>brilliant</b> plan. Let me tell you all about it. What we do is, we</div></body></html>"

    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType("text/plain")
      .body(payload)
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val blobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "attachments": [
         |            {
         |              "blobId": "$blobId",
         |              "type":"text/plain",
         |              "charset":"UTF-8",
         |              "disposition": "inline",
         |              "cid": "abc"
         |            },
         |            {
         |              "blobId": "$blobId",
         |              "type":"text/plain",
         |              "charset":"UTF-8",
         |              "disposition": "inline",
         |              "cid": "def"
         |            },
         |            {
         |              "blobId": "$blobId",
         |              "type":"text/plain",
         |              "charset":"UTF-8",
         |              "disposition": "attachment"
         |            }
         |          ],
         |          "htmlBody": [
         |            {
         |              "partId": "a49d",
         |              "type": "text/html"
         |            }
         |          ],
         |          "bodyValues": {
         |            "a49d": {
         |              "value": "$htmlBody",
         |              "isTruncated": false,
         |              "isEncodingProblem": false
         |            }
         |          }
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |      {
         |        "accountId": "$ACCOUNT_ID",
         |        "ids": ["#aaaaaa"],
         |        "properties": ["bodyStructure"],
         |        "bodyProperties": ["type", "disposition", "cid", "subParts"]
         |      },
         |    "c2"]
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

    val createResponse = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("aaaaaa")

    val messageId = createResponse
      .\("id")
      .get.asInstanceOf[JsString].value
    val size = createResponse
      .\("size")
      .get.asInstanceOf[JsNumber].value

    assertThatJson(response)
      .inPath("methodResponses[0][1].created.aaaaaa")
      .isEqualTo(
        s"""{
           | "id": "$messageId",
           | "blobId": "$messageId",
           | "threadId": "$messageId",
           | "size": $size
           |}""".stripMargin)

    assertThatJson(response)
      .inPath(s"methodResponses[1][1].list[0]")
      .isEqualTo(
        s"""{
           |  "id": "$messageId",
           |  "bodyStructure": {
           |    "type": "multipart/mixed",
           |    "subParts": [
           |      {
           |        "type": "multipart/related",
           |        "subParts": [
           |          {
           |            "type": "multipart/alternative",
           |            "subParts": [
           |              {
           |                "type": "text/plain"
           |              },
           |              {
           |                "type": "text/html"
           |              }
           |            ]
           |          },
           |          {
           |            "type": "text/plain",
           |            "disposition": "inline",
           |            "cid": "abc"
           |          },
           |          {
           |            "type": "text/plain",
           |            "disposition": "inline",
           |            "cid": "def"
           |          }
           |        ]
           |      },
           |      {
           |        "type": "text/plain",
           |        "disposition": "attachment"
           |      }
           |    ]
           |  }
           |}""".stripMargin)
  }

  @Test
  def createShouldSupportAttachmentWithName(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val payload = "123456789\r\n".getBytes(StandardCharsets.UTF_8)

    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType("text/plain")
      .body(payload)
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val blobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "attachments": [
         |            {
         |              "name": "myAttachment",
         |              "blobId": "$blobId",
         |              "type":"text/plain",
         |              "charset":"UTF-8",
         |              "disposition": "attachment",
         |              "language": ["fr", "en"],
         |              "location": "http://125.26.23.36/content"
         |            }
         |          ]
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |      {
         |        "accountId": "$ACCOUNT_ID",
         |        "ids": ["#aaaaaa"],
         |        "properties": ["mailboxIds", "subject", "attachments"],
         |        "bodyProperties": ["partId", "blobId", "size", "name", "type", "charset", "disposition", "cid", "language", "location"]
         |      },
         |    "c2"]
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

    val responseAsJson = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("aaaaaa")

    val messageId = responseAsJson
      .\("id")
      .get.asInstanceOf[JsString].value
    val size = responseAsJson
      .\("size")
      .get.asInstanceOf[JsNumber].value

    assertThatJson(response)
      .inPath("methodResponses[0][1].created.aaaaaa")
      .isEqualTo(
        s"""{
           | "id": "$messageId",
           | "blobId": "$messageId",
           | "threadId": "$messageId",
           | "size": $size
           |}""".stripMargin)

    assertThatJson(response)
      .inPath(s"methodResponses[1][1].list[0]")
      .isEqualTo(
        s"""{
           |  "id": "$messageId",
           |  "mailboxIds": {
           |    "${mailboxId.serialize}": true
           |  },
           |  "subject": "World domination",
           |  "attachments": [
           |    {
           |      "name": "myAttachment",
           |      "partId": "4",
           |      "blobId": "${messageId}_4",
           |      "size": 11,
           |      "type": "text/plain",
           |      "charset": "UTF-8",
           |      "disposition": "attachment",
           |      "language": ["fr", "en"],
           |      "location": "http://125.26.23.36/content"
           |    }
           |  ]
           |}""".stripMargin)
  }

  @Test
  def createShouldSupportAttachedMessages(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(ANDRE.asString())
      .setFrom(ANDRE.asString())
      .setSubject("I'm happy to be attached")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val attachedMessageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, bobPath, AppendCommand.from(message))
      .getMessageId

    val blobId: String = attachedMessageId.serialize()

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "attachments": [
         |            {
         |              "blobId": "$blobId",
         |              "charset":"us-ascii",
         |              "disposition": "attachment",
         |              "type":"message/rfc822"
         |            }
         |          ]
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |      {
         |        "accountId": "$ACCOUNT_ID",
         |        "ids": ["#aaaaaa"],
         |        "properties": ["mailboxIds", "subject", "attachments"],
         |        "bodyProperties": ["partId", "blobId", "size", "type", "charset", "disposition"]
         |      },
         |    "c2"]
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

    val responseAsJson = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("aaaaaa")

    val messageId = responseAsJson
      .\("id")
      .get.asInstanceOf[JsString].value
    val size = responseAsJson
      .\("size")
      .get.asInstanceOf[JsNumber].value

    assertThatJson(response)
      .inPath("methodResponses[0][1].created.aaaaaa")
      .isEqualTo(
        s"""{
           | "id": "$messageId",
           | "blobId": "$messageId",
           | "threadId": "$messageId",
           | "size": $size
           |}""".stripMargin)

    assertThatJson(response)
      .inPath(s"methodResponses[1][1].list[0]")
      .isEqualTo(
        s"""{
           |  "id": "$messageId",
           |  "mailboxIds": {
           |    "${mailboxId.serialize}": true
           |  },
           |  "subject": "World domination",
           |  "attachments": [
           |    {
           |      "partId": "4",
           |      "blobId": "${messageId}_4",
           |      "size": 155,
           |      "type": "message/rfc822",
           |      "charset": "us-ascii",
           |      "disposition": "attachment"
           |    }
           |  ]
           |}""".stripMargin)
  }

  @Test
  def createShouldFailWhenAttachmentNotFound(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "attachments": [
         |            {
         |              "blobId": "123",
         |              "type":"text/plain",
         |              "charset":"UTF-8",
         |              "disposition": "attachment"
         |            }
         |          ]
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
      .whenIgnoringPaths("methodResponses[0][1].created.aaaaaa.id")
      .inPath("methodResponses[0][1].notCreated.aaaaaa")
      .isEqualTo(s"""{
        |  "type": "invalidArguments",
        |  "description": "Attachment not found: 123",
        |  "properties": ["attachments"]
        |}""".stripMargin)
  }

  @Test
  def createShouldFailWhenAttachmentDoesNotBelongToUser(server: GuiceJamesServer): Unit = {
    val andrePath = MailboxPath.inbox(ANDRE)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andrePath)

    val payload = "123456789\r\n".repeat(1).getBytes

    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType("text/plain")
      .body(payload)
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val blobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ANDRE_ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa": {
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "subject": "World domination",
         |          "attachments": [
         |            {
         |              "blobId": "$blobId",
         |              "type":"text/plain",
         |              "charset":"UTF-8",
         |              "disposition": "attachment"
         |            }
         |          ]
         |        }
         |      }
         |    }, "c1"]]
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
      .whenIgnoringPaths("methodResponses[0][1].created.aaaaaa.id")
      .inPath("methodResponses[0][1].notCreated.aaaaaa")
      .isEqualTo(s"""{
                    |  "type": "invalidArguments",
                    |  "description": "Attachment not found: $blobId",
                    |  "properties": ["attachments"]
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
           |   "description": "Message update is invalid: List((,List(JsonValidationError(List(Value associated with keywords is invalid: List((/mus*c,List(JsonValidationError(List(FlagName must not be null or empty, must have length form 1-255,must not contain characters with hex from '\\u0000' to '\\u00019' or {'(' ')' '{' ']' '%' '*' '\"' '\\'} ),List()))))),List()))))"
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
           |      "description":"Message update is invalid: List((,List(JsonValidationError(List(Value associated with keywords is invalid: List((,List(JsonValidationError(List(Does not allow to update 'Deleted' or 'Recent' flag),List()))))),List()))))"}
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
     .isEqualTo(s"""{
        | "invalid": {
        |     "type":"invalidPatch",
        |     "description":"Message update is invalid: ${invalidMessageIdMessage("invalid")}"
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
          |     "description": "Message update is invalid: Partial update and reset specified for keywords"
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
           |   "description": "Message update is invalid: List((,List(JsonValidationError(List(keywords/mus*c is an invalid entry in an Email/set update patch: FlagName must not be null or empty, must have length form 1-255,must not contain characters with hex from '\\u0000' to '\\u00019' or {'(' ')' '{' ']' '%' '*' '\"' '\\'} ),List()))))"}"
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
        s"""|{
          |   "type":"invalidPatch",
          |   "description": "Message update is invalid: List((,List(JsonValidationError(List(Value associated with keywords/movie is invalid: Keywords partial updates requires a JsBoolean(true) (set) or a JsNull (unset)),List()))))"
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
           |      "description":"Message update is invalid: List((,List(JsonValidationError(List(Does not allow to update 'Deleted' or 'Recent' flag),List()))))"}
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState",
        "methodResponses[0][1].newState",
        "methodResponses[1][1].state")
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "destroyed": ["${messageId.serialize}"]
           |      }, "c1"],
           |      ["Email/get", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "state": "${INSTANCE.value}",
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState",
        "methodResponses[0][1].newState")
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "notDestroyed": {
           |          "invalid": {
           |            "type": "invalidArguments",
           |            "description": "UnparsedMessageId(invalid) is not a messageId: ${invalidMessageIdMessage("invalid")}"
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState",
        "methodResponses[0][1].newState")
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState",
        "methodResponses[0][1].newState")
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "notDestroyed": {
           |          "${messageId.serialize}": {
           |            "type": "notFound",
           |            "description": "Cannot find message with messageId: ${messageId.serialize}"
           |          }
           |        }
           |      }, "c1"]
           |    ]
           |}""".stripMargin)
    assertThat(server.getProbe(classOf[MessageIdProbe]).getMessages(messageId, ANDRE))
      .hasSize(1)
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState",
        "methodResponses[0][1].newState")
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState",
        "methodResponses[0][1].newState",
        "methodResponses[1][1].state")
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "destroyed": ["${messageId.serialize}"]
           |      }, "c1"],
           |      ["Email/get", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState",
        "methodResponses[0][1].newState",
        "methodResponses[1][1].state")
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "destroyed": ["${messageId.serialize}"]
           |      }, "c1"],
           |      ["Email/get", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
    mailboxProbe.createMailbox(MailboxPath.inbox(BOB))

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
         |  "${messageId.serialize}": {
         |    "type": "invalidPatch",
         |    "description": "Message update is invalid: List((,List(JsonValidationError(List(invalid is an invalid entry in an Email/set update patch),List()))))"
         |  }
         |}""".stripMargin)
  }

  @Test
  def invalidMailboxPartialUpdatePropertyShouldFail(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxPath.inbox(BOB))

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
         |  "${messageId.serialize}": {
         |    "type": "invalidPatch",
         |    "description": "Message update is invalid: List((,List(JsonValidationError(List(mailboxIds/invalid is an invalid entry in an Email/set update patch: ${invalidMessageIdMessage("invalid")}),List()))))"
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
         |  "${messageId.serialize}": {
         |    "type": "invalidPatch",
         |    "description": "Message update is invalid: List((,List(JsonValidationError(List(Value associated with mailboxIds/${mailboxId1.serialize} is invalid: MailboxId partial updates requires a JsBoolean(true) (set) or a JsNull (unset)),List()))))"
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
         |  "${messageId.serialize}": {
         |    "type": "invalidPatch",
         |    "description": "Message update is invalid: Partial update and reset specified for mailboxIds"
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState",
        "methodResponses[0][1].newState")
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "destroyed": ["${messageId.serialize}"],
           |        "notDestroyed": {
           |          "invalid": {
           |            "type": "invalidArguments",
           |            "description": "UnparsedMessageId(invalid) is not a messageId: ${invalidMessageIdMessage("invalid")}"
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState",
        "methodResponses[0][1].newState",
        "methodResponses[1][1].state")
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "newState": "${INSTANCE.value}",
           |        "updated": {
           |          "${messageId.serialize}": null
           |        }
           |      }, "c1"],
           |      ["Email/get",{
           |        "accountId":"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
      .whenIgnoringPaths("methodResponses[0][1].oldState",
        "methodResponses[0][1].newState",
        "methodResponses[1][1].state")
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "updated": {
           |          "${messageId1.serialize}": null,
           |          "${messageId2.serialize}": null
           |        }
           |      }, "c1"],
           |      ["Email/get", {
           |        "accountId":"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
      .whenIgnoringPaths("methodResponses[0][1].oldState",
        "methodResponses[0][1].newState")
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        ["Email/set", {
           |          "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |          "notUpdated": {
           |            "${messageId.serialize}": {
           |              "type": "notFound",
           |              "description": "Cannot find message with messageId: ${messageId.serialize}"
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
      .whenIgnoringPaths("methodResponses[0][1].oldState",
        "methodResponses[0][1].newState",
        "methodResponses[1][1].state")
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "newState": "${INSTANCE.value}",
           |        "notUpdated": {
           |          "${messageId.serialize}": {
           |            "type": "notFound",
           |            "description": "Mailbox not found"
           |          }
           |        }
           |      }, "c1"],
           |      ["Email/get", {
           |        "accountId":"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
      .whenIgnoringPaths("methodResponses[0][1].oldState",
        "methodResponses[0][1].newState")
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "notUpdated": {
           |          "${messageId.serialize}": {
           |            "type": "notFound",
           |            "description": "Mailbox not found"
           |          }
           |        }
           |      }, "c1"],
           |      ["Email/get", {
           |        "accountId":"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "state":"${INSTANCE.value}",
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
      .whenIgnoringPaths("methodResponses[0][1].oldState",
        "methodResponses[0][1].newState",
        "methodResponses[1][1].state")
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "updated": {
           |          "${messageId.serialize}": null
           |        }
           |      }, "c1"],
           |      ["Email/get", {
           |        "accountId":"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
      .whenIgnoringPaths("methodResponses[0][1].oldState",
        "methodResponses[0][1].newState",
        "methodResponses[1][1].state")
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "updated": {
           |          "${messageId.serialize}": null
           |        }
           |      }, "c1"],
           |      ["Email/get", {
           |        "accountId":"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
      .whenIgnoringPaths("methodResponses[0][1].oldState",
        "methodResponses[0][1].newState")
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
      .whenIgnoringPaths("methodResponses[0][1].oldState",
        "methodResponses[0][1].newState")
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "notUpdated": {
           |          "${messageId.serialize}": {
           |            "type": "invalidPatch",
           |            "description": "Message update is invalid: List((,List(JsonValidationError(List(Value associated with mailboxIds is invalid: List((/invalid,List(JsonValidationError(List(${invalidMessageIdMessage("invalid")}),List()))))),List()))))"
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
      .whenIgnoringPaths("methodResponses[0][1].oldState",
        "methodResponses[0][1].newState")
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "notUpdated": {
           |          "${messageId.serialize}": {
           |            "type": "invalidPatch",
           |            "description": "Message update is invalid: List((,List(JsonValidationError(List(Value associated with mailboxIds is invalid: List((/${mailboxId.serialize},List(JsonValidationError(List(Expecting mailboxId value to be a boolean),List()))))),List()))))"
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
      .whenIgnoringPaths("methodResponses[0][1].oldState",
        "methodResponses[0][1].newState")
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "notUpdated": {
           |          "${messageId.serialize}": {
           |            "type": "invalidPatch",
           |            "description": "Message update is invalid: List((,List(JsonValidationError(List(Value associated with mailboxIds is invalid: List((/${mailboxId.serialize},List(JsonValidationError(List(Expecting mailboxId value to be a boolean),List()))))),List()))))"
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
      .whenIgnoringPaths("methodResponses[0][1].oldState",
        "methodResponses[0][1].newState")
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      ["Email/set", {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "updated": {
           |          "${messageId1.serialize}": null
           |        },
           |        "notUpdated": {
           |          "${messageId2.serialize}": {
           |            "type": "invalidPatch",
           |            "description": "Message update is invalid: List((,List(JsonValidationError(List(Value associated with mailboxIds is invalid: List((/invalid,List(JsonValidationError(List(${invalidMessageIdMessage("invalid")}),List()))))),List()))))"
           |          }
           |        }
           |      }, "c1"]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def newStateShouldBeUpToDate(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "mailbox"))

    val request =
      s"""
         |{
         |   "using": [
         |     "urn:ietf:params:jmap:core",
         |     "urn:ietf:params:jmap:mail"],
         |   "methodCalls": [
         |       ["Email/set", {
         |         "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |         "create": {
         |           "aaaaaa": {
         |             "mailboxIds": {
         |               "${mailboxId.serialize()}": true
         |             }
         |           }
         |         }
         |       }, "c1"],
         |       ["Email/changes", {
         |         "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |         "#sinceState": {
         |           "resultOf":"c1",
         |           "name":"Email/set",
         |           "path":"newState"
         |         }
         |       }, "c2"]
         |   ]
         |}
         |""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .withOptions(new Options(Option.IGNORING_ARRAY_ORDER))
      .whenIgnoringPaths("methodResponses[1][1].oldState",
        "methodResponses[1][1].newState")
      .inPath("methodResponses[1][1]")
      .isEqualTo(
        s"""{
           |  "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |  "hasMoreChanges": false,
           |  "created": [],
           |  "updated": [],
           |  "destroyed": []
           |}""".stripMargin)
  }

  @Test
  def oldStateShouldIncludeSetChanges(server: GuiceJamesServer): Unit = {
    val path: MailboxPath = MailboxPath.forUser(BOB, "mailbox")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)

    val message: Message = Fixture.createTestMessage
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), path,
        AppendCommand.builder()
          .build(message))
      .getMessageId

    val request =
      s"""
         |{
         |   "using": [
         |     "urn:ietf:params:jmap:core",
         |     "urn:ietf:params:jmap:mail"],
         |   "methodCalls": [
         |       ["Email/set", {
         |         "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |         "update": {
         |           "${messageId.serialize}": {
         |             "keywords": {
         |               "music": true
         |             }
         |           }
         |         }
         |       }, "c1"],
         |       ["Email/changes", {
         |         "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |         "#sinceState": {
         |            "resultOf":"c1",
         |            "name":"Email/set",
         |            "path":"oldState"
         |          }
         |       }, "c2"]
         |   ]
         |}
         |""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .withOptions(new Options(Option.IGNORING_ARRAY_ORDER))
      .whenIgnoringPaths("methodResponses[1][1].oldState", "methodResponses[1][1].newState")
      .inPath("methodResponses[1][1]")
      .isEqualTo(
        s"""{
           |  "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |  "hasMoreChanges": false,
           |  "created": [],
           |  "updated": ["${messageId.serialize}"],
           |  "destroyed": []
           |}""".stripMargin)
  }

  @Test
  def stateShouldNotTakeIntoAccountDelegationWhenNoCapability(server: GuiceJamesServer): Unit = {
    val state: String = `with`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:mail"],
           |  "methodCalls": [
           |    ["Email/get", {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "ids": []
           |    }, "c1"]
           |  ]
           |}""".stripMargin)
      .post
    .`then`()
      .extract()
      .jsonPath()
      .get("methodResponses[0][1].state")

    val sharedMailboxName = "AndreShared"
    val andreMailboxPath = MailboxPath.forUser(ANDRE, sharedMailboxName)
    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(andreMailboxPath)

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailboxPath, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Lookup))

    val message: Message = Fixture.createTestMessage
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString(), andreMailboxPath,
        AppendCommand.builder()
          .build(message))
      .getMessageId

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [
               |    ["Email/set", {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
               |    }, "c1"]
               |  ]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body("methodResponses[0][1].oldState", equalTo(state))
  }

  @Test
  def stateShouldTakeIntoAccountDelegationWhenCapability(server: GuiceJamesServer): Unit = {
    val state: String = `with`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |  "using": [
           |  "urn:ietf:params:jmap:core",
           |  "urn:ietf:params:jmap:mail"],
           |  "methodCalls": [
           |    ["Email/get", {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "ids":[]
           |    }, "c1"]
           |  ]
           |}""".stripMargin)
      .post
    .`then`()
      .extract()
      .jsonPath()
      .get("methodResponses[0][1].state")

    val sharedMailboxName = "AndreShared"
    val andreMailboxPath = MailboxPath.forUser(ANDRE, sharedMailboxName)
    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(andreMailboxPath)

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailboxPath, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Lookup))

    val message: Message = Fixture.createTestMessage
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString(), andreMailboxPath,
        AppendCommand.builder()
          .build(message))
      .getMessageId

    awaitAtMostTenSeconds.untilAsserted { () =>
      `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(
          s"""{
             |  "using": [
             |    "urn:ietf:params:jmap:core",
             |    "urn:ietf:params:jmap:mail",
             |    "urn:apache:james:params:jmap:mail:shares"],
             |  "methodCalls": [
             |    ["Email/set", {
             |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
             |    }, "c1"]
             |  ]
             |}""".stripMargin)
        .when
          .post
        .`then`
          .statusCode(SC_OK)
          .body("methodResponses[0][1].oldState", not(equalTo(state)))
    }
  }

  @Test
  def createShouldEnforceQuotas(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    quotaProbe.setMaxMessageCount(quotaProbe.getQuotaRoot(MailboxPath.inbox(BOB)), QuotaCountLimit.count(2L))
    val id1 = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))

    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), MailboxPath.inbox(BOB), AppendCommand.from(buildTestMessage))
      .getMessageId.serialize()
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), MailboxPath.inbox(BOB), AppendCommand.from(buildTestMessage))
      .getMessageId.serialize()

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "create": {
         |        "K39": {
         |          "mailboxIds": {"${id1.serialize()}":true}
         |        }
         |      }
         |    }, "c1"]]
         |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
        s"""{
           |	"sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"methodResponses": [
           |		["Email/set", {
           |			"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |			"notCreated": {
           |				"K39": {
           |					"type": "overQuota",
           |					"description": "You have too many messages in #private&bob@domain.tld"
           |				}
           |			}
           |		}, "c1"]
           |	]
           |}""".stripMargin)
  }

  @Test
  def copyShouldEnforceQuotas(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    quotaProbe.setMaxMessageCount(quotaProbe.getQuotaRoot(MailboxPath.inbox(BOB)), QuotaCountLimit.count(2L))
    val id1 = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB)).serialize()
    val id2 = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "aBox")).serialize()

    val message1 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), MailboxPath.inbox(BOB), AppendCommand.from(buildTestMessage))
      .getMessageId.serialize()
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), MailboxPath.inbox(BOB), AppendCommand.from(buildTestMessage))
      .getMessageId.serialize()

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "$message1": {
         |          "mailboxIds": {"$id1":true, "$id2":true}
         |        }
         |      }
         |    }, "c1"]]
         |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
        s"""{
           |	"sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"methodResponses": [
           |		["Email/set", {
           |			"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |			"notUpdated": {
           |				"$message1": {
           |					"type": "overQuota",
           |					"description": "You have too many messages in #private&bob@domain.tld"
           |				}
           |			}
           |		}, "c1"]
           |	]
           |}""".stripMargin)
  }

  @Test
  def bobShouldBeAbleToUpdateEmailInAndreMailboxWhenDelegated(server: GuiceJamesServer): Unit = {
    val message: Message = Fixture.createTestMessage
    val flags: Flags = new Flags(Flags.Flag.ANSWERED)
    val path = MailboxPath.inbox(ANDRE)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(ANDRE.asString(), path, AppendCommand.builder()
      .withFlags(flags)
      .build(message))
      .getMessageId

    server.getProbe(classOf[DelegationProbe]).addAuthorizedUser(ANDRE, BOB)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ANDRE_ACCOUNT_ID",
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
         |       "accountId": "$ANDRE_ACCOUNT_ID",
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
  def bobShouldNotBeAbleToUpdateEmailInAndreMailboxWhenNotDelegated(server: GuiceJamesServer): Unit = {
    val message: Message = Fixture.createTestMessage
    val flags: Flags = new Flags(Flags.Flag.ANSWERED)
    val path = MailboxPath.inbox(ANDRE)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(ANDRE.asString(), path, AppendCommand.builder()
      .withFlags(flags)
      .build(message))
      .getMessageId

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |	"methodCalls": [
         |		["Email/set", {
         |			"accountId": "$ANDRE_ACCOUNT_ID",
         |			"update": {
         |				"${messageId.serialize}": {
         |					"keywords": {
         |						"music": true
         |					}
         |				}
         |			}
         |		}, "c1"]
         |	]
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
      .inPath("methodResponses[0][1]")
      .isEqualTo(
        """{
          |	"type": "accountNotFound"
          |}""".stripMargin)
  }

  @Test
  def emailSetShouldSucceedWhenInvalidToMailAddressAndHaveDraftKeyword(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa":{
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "keywords":{ "$$draft": true },
         |          "to": [{"email": "invalid1"}],
         |          "from": [{"email": "${BOB.asString}"}]
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |     {
         |       "accountId": "$ACCOUNT_ID",
         |       "ids": ["#aaaaaa"],
         |       "properties": ["sentAt", "messageId"]
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
      .inPath("methodResponses[1][1].list.[0].sentAt")
      .isEqualTo("\"${json-unit.ignore}\"")
    assertThatJson(response)
      .inPath("methodResponses[1][1].list.[0].messageId")
      .isEqualTo("[\"${json-unit.ignore}\"]")

  }

  @Test
  def emailGetShouldReturnUncheckedMailAddressValueWhenDraftEmail(server: GuiceJamesServer): Unit = {
    val bobDraftsPath = MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS)
    val draftId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "e1526":{
         |          "mailboxIds": {
         |             "${draftId.serialize}": true
         |          },
         |          "keywords":{ "$$draft": true },
         |          "to": [{"email": "invalid1", "name" : "name1"}],
         |          "from": [{"email": "${BOB.asString}"}]
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |     {
         |       "accountId": "$ACCOUNT_ID",
         |       "ids": ["#e1526"],
         |       "properties": ["to", "from" ]
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

    val messageId = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("e1526")
      .\("id")
      .get.asInstanceOf[JsString].value

    assertThatJson(response)
      .inPath("methodResponses[1][1].list")
      .isEqualTo(s"""[{
                   |    "to": [{
                   |        "name": "name1",
                   |        "email": "invalid1"
                   |      }],
                   |    "id": "$messageId",
                   |    "from": [{
                   |        "email": "bob@domain.tld"
                   |      }
                   |    ]}]""".stripMargin)

  }

  @Test
  def emailSubmissionSetShouldFailWhenInvalidEmailAddressHeader(server: GuiceJamesServer): Unit = {
    val bobDraftsPath = MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS)

    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val draftId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "e1526":{
         |          "mailboxIds": {
         |             "${draftId.serialize}": true
         |          },
         |          "keywords":{ "$$draft": true },
         |          "to": [{"email": "invalid1"}],
         |          "from": [{"email": "${BOB.asString}"}]
         |        }
         |      }
         |    }, "c1"],
         |    ["Email/get",
         |     {
         |       "accountId": "$ACCOUNT_ID",
         |       "ids": ["#e1526"],
         |       "properties": ["sentAt"]
         |     },
         |     "c2"],
         |     ["EmailSubmission/set", {
         |       "accountId": "$ACCOUNT_ID",
         |       "create": {
         |         "k1490": {
         |           "emailId": "#e1526",
         |           "envelope": {
         |             "mailFrom": {"email": "${BOB.asString}"},
         |             "rcptTo": [{"email": "${BOB.asString}"}]
         |           }
         |         }
         |    }
         |  }, "c3"]]
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
      .inPath("methodResponses[2]")
      .isEqualTo(s"""[
                   |  "EmailSubmission/set",
                   |  {
                   |    "accountId": "$${json-unit.ignore}",
                   |    "newState": "$${json-unit.ignore}",
                   |    "notCreated": {
                   |      "k1490": {
                   |        "type": "invalidArguments",
                   |        "description": "Invalid mail address: invalid1 in to header"
                   |      }
                   |    }
                   |  },
                   |  "c3"
                   |]""".stripMargin)

  }

  @Test
  def emailSetShouldFailWhenInvalidToEmailAddressAndHaveNotDraftKeyword(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "aaaaaa":{
         |          "mailboxIds": {
         |             "${mailboxId.serialize}": true
         |          },
         |          "keywords":{ },
         |          "to": [{"email": "invalid1"}],
         |          "from": [{"email": "${BOB.asString}"}]
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
      .inPath("methodResponses[0][1].notCreated")
      .isEqualTo(
        """{
          |    "aaaaaa": {
          |        "type": "invalidArguments",
          |        "description": "/to: Invalid email address `invalid1`"
          |    }
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
