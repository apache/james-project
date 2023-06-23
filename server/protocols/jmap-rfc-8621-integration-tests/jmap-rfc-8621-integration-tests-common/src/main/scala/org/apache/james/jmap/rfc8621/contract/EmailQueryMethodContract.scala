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

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZonedDateTime}
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

import com.google.common.hash.Hashing
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import javax.mail.Flags
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import net.javacrumbs.jsonunit.core.internal.Options
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UTCDate
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.FlagsBuilder
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.MailboxACL.Right
import org.apache.james.mailbox.model.MailboxPath.inbox
import org.apache.james.mailbox.model.{MailboxACL, MailboxPath, MessageId}
import org.apache.james.mime4j.dom.Message
import org.apache.james.mime4j.field.address.DefaultAddressParser
import org.apache.james.mime4j.message.{DefaultMessageWriter, MultipartBuilder}
import org.apache.james.mime4j.stream.RawField
import org.apache.james.modules.{ACLProbeImpl, MailboxProbeImpl}
import org.apache.james.util.ClassLoaderUtils
import org.apache.james.utils.DataProbeImpl
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.junit.jupiter.api.{BeforeEach, Test}
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.{Arguments, MethodSource, ValueSource}
import org.threeten.extra.Seconds

object EmailQueryMethodContract {
  def jmapSystemKeywords : Stream[Arguments] = {
    Stream.of(
      Arguments.of(new Flags(Flags.Flag.SEEN), "$Seen"),
      Arguments.of(new Flags(Flags.Flag.ANSWERED), "$Answered"),
      Arguments.of(new Flags(Flags.Flag.FLAGGED), "$Flagged"),
      Arguments.of(new Flags(Flags.Flag.DRAFT), "$Draft"),
      Arguments.of(new Flags("$Forwarded"), "$Forwarded"))
  }
}

trait EmailQueryMethodContract {

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

  @Test
  def emailQueryShouldFailWhenWrongAccountId(server: GuiceJamesServer): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "unknownAccountId"
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

  private def buildTestMessage = {
    Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
  }

  @Test
  def hasAttachmentShouldKeepMessageWithAttachmentWhenTrue(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxPath.inbox(BOB))
    mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.from(
          buildTestMessage))
      .getMessageId

    val messageId2: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {"hasAttachment":true}
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId2)}",
           |                "canCalculateChanges": false,
           |                "position": 0,
           |                "limit": 256,
           |                "ids": ["${messageId2.serialize}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def messagesMarkedAsDeletedShouldNotBeExposedOverJMAP(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxPath.inbox(BOB))

    mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder()
          .withFlags(new Flags(Flags.Flag.DELETED))
          .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {"hasAttachment":true}
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
         |            "Email/query",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "queryState": "${generateQueryState()}",
         |                "canCalculateChanges": false,
         |                "position": 0,
         |                "limit": 256,
         |                "ids": []
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def emailQueryShouldAcceptMailboxCreationIdForInMailboxOtherThan(server: GuiceJamesServer): Unit = {
    val request =
      s"""{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "create": {
         |                    "C42": {
         |                      "name": "myMailbox"
         |                    }
         |                }
         |           },
         |           "c1"
         |       ], [
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "inMailboxOtherThan": ["#C42"]
         |      }
         |    },
         |    "c2"
         |  ]]
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

    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId("#private", BOB.asString(), "myMailbox")
      .serialize

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Mailbox/set",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "created": {
         |                    "C42": {
         |                        "id": "${mailboxId}",
         |                        "sortOrder": 1000,
         |                        "totalEmails": 0,
         |                        "unreadEmails": 0,
         |                        "totalThreads": 0,
         |                        "unreadThreads": 0,
         |                        "myRights": {
         |                            "mayReadItems": true,
         |                            "mayAddItems": true,
         |                            "mayRemoveItems": true,
         |                            "maySetSeen": true,
         |                            "maySetKeywords": true,
         |                            "mayCreateChild": true,
         |                            "mayRename": true,
         |                            "mayDelete": true,
         |                            "maySubmit": true
         |                        },
         |                        "isSubscribed": true
         |                    }
         |                }
         |            },
         |            "c1"
         |        ],
         |        [
         |            "Email/query",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "queryState": "00000000",
         |                "canCalculateChanges": false,
         |                "ids": [
         |
         |                ],
         |                "position": 0,
         |                "limit": 256
         |            },
         |            "c2"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def emailQueryShouldAcceptMailboxCreationIdForInMailbox(server: GuiceJamesServer): Unit = {
    val request =
      s"""{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "create": {
         |                    "C42": {
         |                      "name": "myMailbox"
         |                    }
         |                }
         |           },
         |           "c1"
         |       ], [
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "inMailbox": "#C42"
         |      }
         |    },
         |    "c2"
         |  ]]
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

    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId("#private", BOB.asString(), "myMailbox")
      .serialize

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Mailbox/set",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "created": {
         |                    "C42": {
         |                        "id": "${mailboxId}",
         |                        "sortOrder": 1000,
         |                        "totalEmails": 0,
         |                        "unreadEmails": 0,
         |                        "totalThreads": 0,
         |                        "unreadThreads": 0,
         |                        "myRights": {
         |                            "mayReadItems": true,
         |                            "mayAddItems": true,
         |                            "mayRemoveItems": true,
         |                            "maySetSeen": true,
         |                            "maySetKeywords": true,
         |                            "mayCreateChild": true,
         |                            "mayRename": true,
         |                            "mayDelete": true,
         |                            "maySubmit": true
         |                        },
         |                        "isSubscribed": true
         |                    }
         |                }
         |            },
         |            "c1"
         |        ],
         |        [
         |            "Email/query",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "queryState": "00000000",
         |                "canCalculateChanges": false,
         |                "ids": [
         |
         |                ],
         |                "position": 0,
         |                "limit": 256
         |            },
         |            "c2"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def emailInSharedMailboxesShouldNotBeDisplayedWhenNoExtension(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(inbox(ANDRE))
    mailboxProbe
      .appendMessage(ANDRE.asString, inbox(ANDRE),
        AppendCommand.from(
          Message.Builder
            .of
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(inbox(ANDRE), BOB.asString, new MailboxACL.Rfc4314Rights(Right.Read))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState()}",
           |                "canCalculateChanges": false,
           |                "position": 0,
           |                "limit": 256,
           |                "ids": []
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def emailInSharedMailboxesShouldBeDisplayedWhenExtension(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(inbox(ANDRE))
    val messageId1: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString, inbox(ANDRE),
        AppendCommand.from(
          Message.Builder
            .of
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(inbox(ANDRE), BOB.asString, new MailboxACL.Rfc4314Rights(Right.Read))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId1)}",
           |                "canCalculateChanges": false,
           |                "position": 0,
           |                "limit": 256,
           |                "ids": ["${messageId1.serialize()}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def inMailboxFilterShouldReturnEmptyForSharedMailboxesWhenNoExtension(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val andreInboxId = mailboxProbe.createMailbox(inbox(ANDRE))
    mailboxProbe
      .appendMessage(ANDRE.asString, inbox(ANDRE),
        AppendCommand.from(
          Message.Builder
            .of
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(inbox(ANDRE), BOB.asString, new MailboxACL.Rfc4314Rights(Right.Read))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {"inMailbox": "${andreInboxId.serialize()}"}
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
           |    "methodResponses": [
           |        [
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState()}",
           |                "canCalculateChanges": false,
           |                "ids": [
           |
           |                ],
           |                "position": 0,
           |                "limit": 256
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def inMailboxFilterShouldAcceptSharedMailboxesWhenExtension(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val andreInboxId = mailboxProbe.createMailbox(inbox(ANDRE))
    val messageId1: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString, inbox(ANDRE),
        AppendCommand.from(
          Message.Builder
            .of
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(inbox(ANDRE), BOB.asString, new MailboxACL.Rfc4314Rights(Right.Read))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {"inMailbox": "${andreInboxId.serialize()}"}
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId1)}",
           |                "canCalculateChanges": false,
           |                "position": 0,
           |                "limit": 256,
           |                "ids": ["${messageId1.serialize()}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def inMailboxFilterWithSortedByReceivedAtShouldReturnEmptyForSharedMailboxesWhenNoSharesExtension(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val andreInboxId = mailboxProbe.createMailbox(inbox(ANDRE))
    mailboxProbe
      .appendMessage(ANDRE.asString, inbox(ANDRE),
        AppendCommand.from(
          Message.Builder
            .of
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId

    server.getProbe(classOf[ACLProbeImpl])
      .addRights(inbox(ANDRE), BOB.asString, new MailboxACL.Rfc4314Rights(Right.Read, Right.Lookup))

    val request =
      s"""{
         |	"using": [
         |		"urn:ietf:params:jmap:core",
         |		"urn:ietf:params:jmap:mail"
         |	],
         |	"methodCalls": [
         |		[
         |			"Email/query",
         |			{
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"filter": {
         |					"inMailbox": "${andreInboxId.serialize()}"
         |				},
         |				"sort": [{
         |					"isAscending": false,
         |					"property": "receivedAt"
         |				}]
         |			},
         |			"c1"
         |		]
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState()}",
           |                "canCalculateChanges": false,
           |                "ids": [
           |
           |                ],
           |                "position": 0,
           |                "limit": 256
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def inMailboxFilterWithSortedByReceivedAtShouldAcceptSharedMailboxesWhenSharesExtension(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val andreInboxId = mailboxProbe.createMailbox(inbox(ANDRE))
    val messageId1: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString, inbox(ANDRE),
        AppendCommand.from(
          Message.Builder
            .of
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId

    server.getProbe(classOf[ACLProbeImpl])
      .addRights(inbox(ANDRE), BOB.asString, new MailboxACL.Rfc4314Rights(Right.Read, Right.Lookup))

    val request =
      s"""{
         |	"using": [
         |		"urn:ietf:params:jmap:core",
         |		"urn:ietf:params:jmap:mail",
         |		"urn:apache:james:params:jmap:mail:shares"
         |	],
         |	"methodCalls": [
         |		[
         |			"Email/query",
         |			{
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"filter": {
         |					"inMailbox": "${andreInboxId.serialize()}"
         |				},
         |				"sort": [{
         |					"isAscending": false,
         |					"property": "receivedAt"
         |				}]
         |			},
         |			"c1"
         |		]
         |	]
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId1)}",
           |                "canCalculateChanges": false,
           |                "position": 0,
           |                "limit": 256,
           |                "ids": ["${messageId1.serialize()}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def inMailboxFilterWithSortedBySentAtShouldReturnEmptyForSharedMailboxesWhenNoSharesExtension(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val andreInboxId = mailboxProbe.createMailbox(inbox(ANDRE))
    mailboxProbe
      .appendMessage(ANDRE.asString, inbox(ANDRE),
        AppendCommand.from(
          Message.Builder
            .of
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId

    server.getProbe(classOf[ACLProbeImpl])
      .addRights(inbox(ANDRE), BOB.asString, new MailboxACL.Rfc4314Rights(Right.Read, Right.Lookup))

    val request =
      s"""{
         |	"using": [
         |		"urn:ietf:params:jmap:core",
         |		"urn:ietf:params:jmap:mail"
         |	],
         |	"methodCalls": [
         |		[
         |			"Email/query",
         |			{
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"filter": {
         |					"inMailbox": "${andreInboxId.serialize()}"
         |				},
         |				"sort": [{
         |					"isAscending": false,
         |					"property": "sentAt"
         |				}]
         |			},
         |			"c1"
         |		]
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState()}",
           |                "canCalculateChanges": false,
           |                "ids": [
           |
           |                ],
           |                "position": 0,
           |                "limit": 256
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def inMailboxFilterWithSortedBySentAtShouldAcceptSharedMailboxesWhenSharesExtension(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val andreInboxId = mailboxProbe.createMailbox(inbox(ANDRE))
    val messageId1: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString, inbox(ANDRE),
        AppendCommand.from(
          Message.Builder
            .of
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId

    server.getProbe(classOf[ACLProbeImpl])
      .addRights(inbox(ANDRE), BOB.asString, new MailboxACL.Rfc4314Rights(Right.Read, Right.Lookup))

    val request =
      s"""{
         |	"using": [
         |		"urn:ietf:params:jmap:core",
         |		"urn:ietf:params:jmap:mail",
         |		"urn:apache:james:params:jmap:mail:shares"
         |	],
         |	"methodCalls": [
         |		[
         |			"Email/query",
         |			{
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"filter": {
         |					"inMailbox": "${andreInboxId.serialize()}"
         |				},
         |				"sort": [{
         |					"isAscending": false,
         |					"property": "sentAt"
         |				}]
         |			},
         |			"c1"
         |		]
         |	]
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId1)}",
           |                "canCalculateChanges": false,
           |                "position": 0,
           |                "limit": 256,
           |                "ids": ["${messageId1.serialize()}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def inMailboxOtherThanFilterShouldReturnEmptyForSharedMailboxesWhenNoExtension(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val andreInboxId = mailboxProbe.createMailbox(inbox(ANDRE))
    mailboxProbe
      .appendMessage(ANDRE.asString, inbox(ANDRE),
        AppendCommand.from(
          Message.Builder
            .of
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(inbox(ANDRE), BOB.asString, new MailboxACL.Rfc4314Rights(Right.Read))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {"inMailboxOtherThan": ["${andreInboxId.serialize()}"]}
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
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState()}",
           |                "canCalculateChanges": false,
           |                "ids": [],
           |                "position": 0,
           |                "limit": 256
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
  }

  @Test
  def inMailboxAfterWithSortedBySentAtShouldReturnEmptyForSharedMailboxesWhenNoSharesExtension(server: GuiceJamesServer): Unit = {
    val requestDate = ZonedDateTime.now().minusDays(1)
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val andreInboxId = mailboxProbe.createMailbox(inbox(ANDRE))
    mailboxProbe
      .appendMessage(ANDRE.asString, inbox(ANDRE),
        AppendCommand.from(
          Message.Builder
            .of
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId

    server.getProbe(classOf[ACLProbeImpl])
      .addRights(inbox(ANDRE), BOB.asString, new MailboxACL.Rfc4314Rights(Right.Read, Right.Lookup))

    val request =
      s"""{
         |	"using": [
         |		"urn:ietf:params:jmap:core",
         |		"urn:ietf:params:jmap:mail"
         |	],
         |	"methodCalls": [
         |		[
         |			"Email/query",
         |			{
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"filter": {
         |					"inMailbox": "${andreInboxId.serialize()}",
         |					"after": "${UTCDate(requestDate).asUTC.format(UTC_DATE_FORMAT)}"
         |				},
         |				"sort": [{
         |					"isAscending": false,
         |					"property": "sentAt"
         |				}]
         |			},
         |			"c1"
         |		]
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState()}",
           |                "canCalculateChanges": false,
           |                "ids": [],
           |                "position": 0,
           |                "limit": 256
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
  }

  @Test
  def inMailboxAfterWithSortedBySentAtShouldAcceptSharedMailboxesWhenSharesExtension(server: GuiceJamesServer): Unit = {
    val requestDate = ZonedDateTime.now().minusDays(1)
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val andreInboxId = mailboxProbe.createMailbox(inbox(ANDRE))
    val messageId1: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString, inbox(ANDRE),
        AppendCommand.from(
          Message.Builder
            .of
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId

    server.getProbe(classOf[ACLProbeImpl])
      .addRights(inbox(ANDRE), BOB.asString, new MailboxACL.Rfc4314Rights(Right.Read, Right.Lookup))

    val request =
      s"""{
         |	"using": [
         |		"urn:ietf:params:jmap:core",
         |		"urn:ietf:params:jmap:mail",
         |		"urn:apache:james:params:jmap:mail:shares"
         |	],
         |	"methodCalls": [
         |		[
         |			"Email/query",
         |			{
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"filter": {
         |					"inMailbox": "${andreInboxId.serialize()}",
         |					"after": "${UTCDate(requestDate).asUTC.format(UTC_DATE_FORMAT)}"
         |				},
         |				"sort": [{
         |					"isAscending": false,
         |					"property": "sentAt"
         |				}]
         |			},
         |			"c1"
         |		]
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId1)}",
           |                "canCalculateChanges": false,
           |                "ids": ["${messageId1.serialize()}"],
           |                "position": 0,
           |                "limit": 256
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
  }

  @Test
  def inMailboxAfterWithSortedByReceivedAtShouldReturnEmptyForSharedMailboxesWhenNoSharesExtension(server: GuiceJamesServer): Unit = {
    val requestDate = ZonedDateTime.now().minusDays(1)
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val andreInboxId = mailboxProbe.createMailbox(inbox(ANDRE))
    mailboxProbe
      .appendMessage(ANDRE.asString, inbox(ANDRE),
        AppendCommand.from(
          Message.Builder
            .of
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId

    server.getProbe(classOf[ACLProbeImpl])
      .addRights(inbox(ANDRE), BOB.asString, new MailboxACL.Rfc4314Rights(Right.Read, Right.Lookup))

    val request =
      s"""{
         |	"using": [
         |		"urn:ietf:params:jmap:core",
         |		"urn:ietf:params:jmap:mail"
         |	],
         |	"methodCalls": [
         |		[
         |			"Email/query",
         |			{
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"filter": {
         |					"inMailbox": "${andreInboxId.serialize()}",
         |					"after": "${UTCDate(requestDate).asUTC.format(UTC_DATE_FORMAT)}"
         |				},
         |				"sort": [{
         |					"isAscending": false,
         |					"property": "receivedAt"
         |				}]
         |			},
         |			"c1"
         |		]
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState()}",
           |                "canCalculateChanges": false,
           |                "ids": [],
           |                "position": 0,
           |                "limit": 256
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
  }

  @Test
  def inMailboxAfterWithSortedByReceivedAtShouldAcceptSharedMailboxesWhenSharesExtension(server: GuiceJamesServer): Unit = {
    val requestDate = ZonedDateTime.now().minusDays(1)
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val andreInboxId = mailboxProbe.createMailbox(inbox(ANDRE))
    val messageId1: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString, inbox(ANDRE),
        AppendCommand.from(
          Message.Builder
            .of
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId

    server.getProbe(classOf[ACLProbeImpl])
      .addRights(inbox(ANDRE), BOB.asString, new MailboxACL.Rfc4314Rights(Right.Read, Right.Lookup))

    val request =
      s"""{
         |	"using": [
         |		"urn:ietf:params:jmap:core",
         |		"urn:ietf:params:jmap:mail",
         |		"urn:apache:james:params:jmap:mail:shares"
         |	],
         |	"methodCalls": [
         |		[
         |			"Email/query",
         |			{
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"filter": {
         |					"inMailbox": "${andreInboxId.serialize()}",
         |					"after": "${UTCDate(requestDate).asUTC.format(UTC_DATE_FORMAT)}"
         |				},
         |				"sort": [{
         |					"isAscending": false,
         |					"property": "receivedAt"
         |				}]
         |			},
         |			"c1"
         |		]
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId1)}",
           |                "canCalculateChanges": false,
           |                "ids": ["${messageId1.serialize()}"],
           |                "position": 0,
           |                "limit": 256
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
  }

  @Test
  def inMailboxOtherThanFilterShouldAcceptSharedMailboxesWhenExtension(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val andreInboxId = mailboxProbe.createMailbox(inbox(ANDRE))
    mailboxProbe.createMailbox(inbox(BOB))
    mailboxProbe
      .appendMessage(ANDRE.asString, inbox(ANDRE),
        AppendCommand.from(
          Message.Builder
            .of
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId
    val messageId2: MessageId = mailboxProbe
      .appendMessage(BOB.asString, inbox(BOB),
        AppendCommand.from(
          Message.Builder
            .of
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(inbox(ANDRE), BOB.asString, new MailboxACL.Rfc4314Rights(Right.Read))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {"inMailboxOtherThan": ["${andreInboxId.serialize()}"]}
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId2)}",
           |                "canCalculateChanges": false,
           |                "position": 0,
           |                "limit": 256,
           |                "ids": ["${messageId2.serialize()}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  def bodyFilterShouldMatchTextPlainOnBody(server: GuiceJamesServer): Unit = {
    val message: Message = simpleMessage("this message body are uniqueeee")
    val mailboxPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(mailboxPath)
    val requestDate = Date.from(ZonedDateTime.now().minusDays(1).toInstant)
    val messageId1: MessageId = sendMessageToBobInbox(server, message, requestDate)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, mailboxPath, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "body":"this message"
         |       }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId1.serialize}"]}""")
    }
  }

  @Test
  def bodyFilterShouldMatchTextPlainOnAttachmentsContent(server: GuiceJamesServer): Unit = {
    val message: Message = simpleMessage("this message body are uniqueeee")
    val mailboxPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(mailboxPath)
    val requestDate = Date.from(ZonedDateTime.now().minusDays(1).toInstant)
    sendMessageToBobInbox(server, message, requestDate)
    val messageId2: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "body":"RSA PRIVATE"
         |       }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId2.serialize}"]}""")
    }
  }

  @Test
  def bodyFilterShouldMatchTextPlainOnAttachmentsFileName(server: GuiceJamesServer): Unit = {
    val message: Message = simpleMessage("this message body are uniqueeee")
    val mailboxPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(mailboxPath)
    val requestDate = Date.from(ZonedDateTime.now().minusDays(1).toInstant)
    sendMessageToBobInbox(server, message, requestDate)
    val messageId2: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "body":"text2"
         |       }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(
        s"""["${messageId2.serialize}"]""")
    }
  }

  @Test
  def headerExistsShouldBeCaseInsentive(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(inbox(BOB))
    val messageId1: MessageId = mailboxProbe
      .appendMessage(BOB.asString, inbox(BOB),
        AppendCommand.from(
          Message.Builder
            .of
            .addField(new RawField("X-Specific", "value"))
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId
    mailboxProbe
      .appendMessage(BOB.asString, inbox(BOB),
        AppendCommand.from(
          Message.Builder
            .of
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {"header": ["X-SpEcIfIc"]}
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId1)}",
           |                "canCalculateChanges": false,
           |                "position": 0,
           |                "limit": 256,
           |                "ids": ["${messageId1.serialize()}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def headerShouldAllowToMatchMailWithSpecificHeaderSet(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(inbox(BOB))
    val messageId1: MessageId = mailboxProbe
      .appendMessage(BOB.asString, inbox(BOB),
        AppendCommand.from(
          Message.Builder
            .of
            .addField(new RawField("X-Specific", "value"))
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId
    mailboxProbe
      .appendMessage(BOB.asString, inbox(BOB),
        AppendCommand.from(
          Message.Builder
            .of
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {"header": ["X-Specific"]}
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId1)}",
           |                "canCalculateChanges": false,
           |                "position": 0,
           |                "limit": 256,
           |                "ids": ["${messageId1.serialize()}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def headerShouldAllowToMatchMailWithSpecificValueHeaderSet(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(inbox(BOB))
    val messageId1: MessageId = mailboxProbe
      .appendMessage(BOB.asString, inbox(BOB),
        AppendCommand.from(
          Message.Builder
            .of
            .addField(new RawField("X-Specific", "value"))
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId
    mailboxProbe
      .appendMessage(BOB.asString, inbox(BOB),
        AppendCommand.from(
          Message.Builder
            .of
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId
    mailboxProbe
      .appendMessage(BOB.asString, inbox(BOB),
        AppendCommand.from(
          Message.Builder
            .of
            .addField(new RawField("X-Specific", "other"))
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {"header": ["X-Specific", "value"]}
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId1)}",
           |                "canCalculateChanges": false,
           |                "position": 0,
           |                "limit": 256,
           |                "ids": ["${messageId1.serialize()}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def headerContainsShouldBeCaseInsentive(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(inbox(BOB))
    val messageId1: MessageId = mailboxProbe
      .appendMessage(BOB.asString, inbox(BOB),
        AppendCommand.from(
          Message.Builder
            .of
            .addField(new RawField("X-Specific", "VaLuE"))
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId
    mailboxProbe
      .appendMessage(BOB.asString, inbox(BOB),
        AppendCommand.from(
          Message.Builder
            .of
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId
    mailboxProbe
      .appendMessage(BOB.asString, inbox(BOB),
        AppendCommand.from(
          Message.Builder
            .of
            .addField(new RawField("X-Specific", "other"))
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {"header": ["X-Specific", "value"]}
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId1)}",
           |                "canCalculateChanges": false,
           |                "position": 0,
           |                "limit": 256,
           |                "ids": ["${messageId1.serialize()}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def headerShouldRejectWhenMoreThanTwoItems(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(inbox(BOB))
    mailboxProbe
      .appendMessage(BOB.asString, inbox(BOB),
        AppendCommand.from(
          Message.Builder
            .of
            .addField(new RawField("X-Specific", "value"))
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId
    mailboxProbe
      .appendMessage(BOB.asString, inbox(BOB),
        AppendCommand.from(
          Message.Builder
            .of
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId
    mailboxProbe
      .appendMessage(BOB.asString, inbox(BOB),
        AppendCommand.from(
          Message.Builder
            .of
            .addField(new RawField("X-Specific", "other"))
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {"header": ["X-Specific", "value", "invalid"]}
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
        .isEqualTo(s"""{
                      |    "sessionState": "${SESSION_STATE.value}",
                      |    "methodResponses": [
                      |        [
                      |            "error",
                      |            {
                      |                "type": "invalidArguments",
                      |                "description": "'/filter/header' property is not valid: header filter needs to be an array of one or two strings"
                      |            },
                      |            "c1"
                      |        ]
                      |    ]
                      |}""".stripMargin)
  }

  @Test
  def hasAttachmentShouldKeepMessageWithoutAttachmentWhenFalse(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxPath.inbox(BOB))
    val messageId1: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.from(
          buildTestMessage))
      .getMessageId

    mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {"hasAttachment":false}
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId1)}",
           |                "canCalculateChanges": false,
           |                "position": 0,
           |                "limit": 256,
           |                "ids": ["${messageId1.serialize}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def queryShouldTakeAllFiltersIntoAccount(server: GuiceJamesServer): Unit = {
    val beforeRequestDate = Date.from(ZonedDateTime.now().minusDays(2).toInstant)
    val requestDate = ZonedDateTime.now().minusDays(1)
    val afterRequestDate = Date.from(ZonedDateTime.now().toInstant)
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxPath.inbox(BOB))
    mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder()
          .withInternalDate(beforeRequestDate)
          .build(buildTestMessage))
      .getMessageId

    mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder()
          .withInternalDate(beforeRequestDate)
        .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    val messageId3: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder()
          .withInternalDate(afterRequestDate)
          .build(buildTestMessage))
      .getMessageId

    mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder()
        .withInternalDate(afterRequestDate)
        .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "hasAttachment":false,
         |        "after": "${UTCDate(requestDate).asUTC.format(UTC_DATE_FORMAT)}"
         |       }
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId3)}",
           |                "canCalculateChanges": false,
           |                "position": 0,
           |                "limit": 256,
           |                "ids": ["${messageId3.serialize}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def inMailboxAfterSortedByReceivedAtShouldYieldExpectedResult(server: GuiceJamesServer): Unit = {
    val beforeRequestDate1 = Date.from(ZonedDateTime.now().minusDays(3).toInstant)
    val beforeRequestDate2 = Date.from(ZonedDateTime.now().minusDays(2).toInstant)
    val requestDate = ZonedDateTime.now().minusDays(1)
    val afterRequestDate1 = Date.from(ZonedDateTime.now().toInstant)
    val afterRequestDate2 = Date.from(ZonedDateTime.now().plusDays(1).toInstant)
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId = mailboxProbe.createMailbox(MailboxPath.inbox(BOB))
    val messageId1: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder()
          .withInternalDate(beforeRequestDate1)
          .build(buildTestMessage))
      .getMessageId

    val messageId2: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder()
          .withInternalDate(beforeRequestDate2)
        .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    val messageId3: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder()
          .withInternalDate(afterRequestDate1)
          .build(buildTestMessage))
      .getMessageId

    val messageId4: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder()
        .withInternalDate(afterRequestDate2)
        .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "inMailbox": "${mailboxId.serialize()}",
         |        "after": "${UTCDate(requestDate).asUTC.format(UTC_DATE_FORMAT)}"
         |       },
         |      "sort": [{
         |        "property":"receivedAt",
         |        "isAscending": false
         |      }]
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId4, messageId3)}",
           |                "canCalculateChanges": false,
           |                "position": 0,
           |                "limit": 256,
           |                "ids": ["${messageId4.serialize}", "${messageId3.serialize}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def inMailboxAfterSortedByReceivedAtShouldYieldExpectedResultWithOffsetAndLimit(server: GuiceJamesServer): Unit = {
    val beforeRequestDate1 = Date.from(ZonedDateTime.now().minusDays(3).toInstant)
    val beforeRequestDate2 = Date.from(ZonedDateTime.now().minusDays(2).toInstant)
    val requestDate = ZonedDateTime.now().minusDays(1)
    val afterRequestDate1 = Date.from(ZonedDateTime.now().toInstant)
    val afterRequestDate2 = Date.from(ZonedDateTime.now().plusDays(1).toInstant)
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId = mailboxProbe.createMailbox(MailboxPath.inbox(BOB))
    val messageId1: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder()
          .withInternalDate(beforeRequestDate1)
          .build(buildTestMessage))
      .getMessageId

    val messageId2: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder()
          .withInternalDate(beforeRequestDate2)
        .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    val messageId3: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder()
          .withInternalDate(afterRequestDate1)
          .build(buildTestMessage))
      .getMessageId

    val messageId4: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder()
        .withInternalDate(afterRequestDate2)
        .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "inMailbox": "${mailboxId.serialize()}",
         |        "after": "${UTCDate(requestDate).asUTC.format(UTC_DATE_FORMAT)}"
         |       },
         |      "sort": [{
         |        "property":"receivedAt",
         |        "isAscending": false
         |      }],
         |      "limit": 1,
         |      "position": 1
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId3)}",
           |                "canCalculateChanges": false,
           |                "position": 1,
           |                "ids": ["${messageId3.serialize}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def inMailboxSortedByReceivedAtShouldYieldExpectedResult(server: GuiceJamesServer): Unit = {
    val beforeRequestDate1 = Date.from(ZonedDateTime.now().minusDays(3).toInstant)
    val beforeRequestDate2 = Date.from(ZonedDateTime.now().minusDays(2).toInstant)
    val requestDate = ZonedDateTime.now().minusDays(1)
    val afterRequestDate1 = Date.from(ZonedDateTime.now().toInstant)
    val afterRequestDate2 = Date.from(ZonedDateTime.now().plusDays(1).toInstant)
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId = mailboxProbe.createMailbox(MailboxPath.inbox(BOB))
    val messageId1: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder()
          .withInternalDate(beforeRequestDate1)
          .build(buildTestMessage))
      .getMessageId

    val messageId2: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder()
          .withInternalDate(beforeRequestDate2)
        .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    val messageId3: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder()
          .withInternalDate(afterRequestDate1)
          .build(buildTestMessage))
      .getMessageId

    val messageId4: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder()
        .withInternalDate(afterRequestDate2)
        .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "inMailbox": "${mailboxId.serialize()}"
         |       },
         |      "sort": [{
         |        "property":"receivedAt",
         |        "isAscending": false
         |      }]
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId4, messageId3, messageId2, messageId1)}",
           |                "canCalculateChanges": false,
           |                "position": 0,
           |                "limit": 256,
           |                "ids": ["${messageId4.serialize}", "${messageId3.serialize}", "${messageId2.serialize}", "${messageId1.serialize}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def inMailboxSortedByReceivedAtShouldYieldExpectedResultWithOffsetAndLimit(server: GuiceJamesServer): Unit = {
    val beforeRequestDate1 = Date.from(ZonedDateTime.now().minusDays(3).toInstant)
    val beforeRequestDate2 = Date.from(ZonedDateTime.now().minusDays(2).toInstant)
    val requestDate = ZonedDateTime.now().minusDays(1)
    val afterRequestDate1 = Date.from(ZonedDateTime.now().toInstant)
    val afterRequestDate2 = Date.from(ZonedDateTime.now().plusDays(1).toInstant)
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId = mailboxProbe.createMailbox(MailboxPath.inbox(BOB))
    val messageId1: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder()
          .withInternalDate(beforeRequestDate1)
          .build(buildTestMessage))
      .getMessageId

    val messageId2: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder()
        .withInternalDate(beforeRequestDate2)
        .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    val messageId3: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder()
          .withInternalDate(afterRequestDate1)
          .build(buildTestMessage))
      .getMessageId

    val messageId4: MessageId = mailboxProbe
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder()
        .withInternalDate(afterRequestDate2)
        .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "inMailbox": "${mailboxId.serialize()}"
         |      },
         |      "sort": [{
         |        "property":"receivedAt",
         |        "isAscending": false
         |      }],
         |      "limit": 2,
         |      "position": 1
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId3, messageId2)}",
           |                "canCalculateChanges": false,
           |                "position": 1,
           |                "ids": ["${messageId3.serialize}", "${messageId2.serialize}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def shouldListMailsInAllUserMailboxes(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    val requestDate = Date.from(ZonedDateTime.now().minusDays(1).toInstant)
    val messageId1: MessageId = sendMessageToBobInbox(server, message, requestDate)

    val messageId2: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(
        s"""["${messageId2.serialize}", "${messageId1.serialize}"]""".stripMargin)
    }
  }

  @Test
  def shouldNotListMailsFromOtherUserMailboxes(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(ANDRE, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, inbox(BOB), AppendCommand.from(message))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId1)}",
           |                "canCalculateChanges": false,
           |                "position": 0,
           |                "limit": 256,
           |                "ids": ["${messageId1.serialize}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def listMailsShouldBeSortedByDescendingOrderOfSentAt(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val requestDateMessage1 = Date.from(ZonedDateTime.now().minusDays(1).toInstant)
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder()
          .withInternalDate(requestDateMessage1)
          .build(message))
      .getMessageId

    val requestDateMessage2 = Date.from(ZonedDateTime.now().minusDays(2).toInstant)
    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder()
          .withInternalDate(requestDateMessage2)
          .build(message))
      .getMessageId

    val requestDateMessage3 = Date.from(ZonedDateTime.now().minusDays(3).toInstant)
    val messageId3 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder()
          .withInternalDate(requestDateMessage3)
          .build(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sort": [{
         |        "property":"sentAt",
         |        "isAscending": false
         |      }]
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
      .inPath("$.methodResponses[0][1].ids")
      .isEqualTo(s"""["${messageId1.serialize}", "${messageId2.serialize}", "${messageId3.serialize}"]""")
    }
  }

  @Test
  def listMailsShouldBeSortedByDescendingOrderOfSentAtAndInMailbox(server: GuiceJamesServer): Unit = {
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val requestDateMessage1 = Date.from(ZonedDateTime.now().minusDays(1).toInstant)
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder()
          .withInternalDate(requestDateMessage1)
          .build(message))
      .getMessageId

    val requestDateMessage2 = Date.from(ZonedDateTime.now().minusDays(2).toInstant)
    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder()
          .withInternalDate(requestDateMessage2)
          .build(message))
      .getMessageId

    val requestDateMessage3 = Date.from(ZonedDateTime.now().minusDays(3).toInstant)
    val messageId3 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder()
          .withInternalDate(requestDateMessage3)
          .build(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "inMailbox": "${mailboxId.serialize}"
         |      },
         |      "sort": [{
         |        "property":"sentAt",
         |        "isAscending": false
         |      }]
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
      .inPath("$.methodResponses[0][1].ids")
      .isEqualTo(s"""["${messageId1.serialize}", "${messageId2.serialize}", "${messageId3.serialize}"]""")
    }
  }

  @Test
  def listMailsShouldBeSortedByAscendingOrderOfSentAt(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)

    val requestDateMessage1 = Date.from(ZonedDateTime.now().minusDays(3).toInstant)
    val message1: Message = Message.Builder
      .of
      .setSubject("test")
      .setDate(requestDateMessage1)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().build(message1))
      .getMessageId

    val requestDateMessage2 = Date.from(ZonedDateTime.now().minusDays(2).toInstant)
    val message2: Message = Message.Builder
      .of
      .setSubject("test")
      .setDate(requestDateMessage2)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().build(message2))
      .getMessageId

    val requestDateMessage3 = Date.from(ZonedDateTime.now().minusDays(1).toInstant)
    val message3: Message = Message.Builder
      .of
      .setSubject("test")
      .setDate(requestDateMessage3)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId3 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().build(message3))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sort": [{
         |        "property":"sentAt",
         |        "isAscending": true
         |      }]
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
      .inPath("$.methodResponses[0][1].ids")
      .isEqualTo(s"""["${messageId1.serialize}", "${messageId2.serialize}", "${messageId3.serialize}"]""")
    }
  }

  @Test
  def listMailsShouldBeSortedByAscendingOrderWhenSortingBySentAtWithoutOrdering(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)

    val requestDateMessage1 = Date.from(ZonedDateTime.now().minusDays(3).toInstant)
    val message1: Message = Message.Builder
      .of
      .setSubject("test")
      .setDate(requestDateMessage1)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().build(message1))
      .getMessageId

    val requestDateMessage2 = Date.from(ZonedDateTime.now().minusDays(2).toInstant)
    val message2: Message = Message.Builder
      .of
      .setSubject("test")
      .setDate(requestDateMessage2)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().build(message2))
      .getMessageId

    val requestDateMessage3 = Date.from(ZonedDateTime.now().minusDays(1).toInstant)
    val message3: Message = Message.Builder
      .of
      .setSubject("test")
      .setDate(requestDateMessage3)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId3 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().build(message3))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sort": [{
         |        "property":"sentAt"
         |      }]
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
      .inPath("$.methodResponses[0][1].ids")
      .isEqualTo(s"""["${messageId1.serialize}", "${messageId2.serialize}", "${messageId3.serialize}"]""")
    }
  }

  @Test
  def listMailsShouldBeSortedWhenUsingSize(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)

    val message1: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("short", StandardCharsets.UTF_8)
      .build
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().build(message1))
      .getMessageId

    val message2: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("definitly looooooooooooooooooooooooooooooooooooong", StandardCharsets.UTF_8)
      .build
    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().build(message2))
      .getMessageId

    val message3: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("not that short", StandardCharsets.UTF_8)
      .build
    val messageId3 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().build(message3))
      .getMessageId

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sort": [{"property":"size"}]
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
      .inPath("$.methodResponses[0][1].ids")
      .isEqualTo(s"""["${messageId1.serialize}", "${messageId3.serialize}", "${messageId2.serialize}"]""")
    }
  }

  @Test
  def listMailsShouldBeSortedWhenUsingSubject(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)

    val message1: Message = Message.Builder
      .of
      .setSubject("aba")
      .setBody("any body", StandardCharsets.UTF_8)
      .build
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().build(message1))
      .getMessageId

    val message2: Message = Message.Builder
      .of
      .setSubject("aaa")
      .setBody("any body", StandardCharsets.UTF_8)
      .build
    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().build(message2))
      .getMessageId

    val message3: Message = Message.Builder
      .of
      .setSubject("ccc")
      .setBody("any body", StandardCharsets.UTF_8)
      .build
    val messageId3 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().build(message3))
      .getMessageId

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sort": [{"property":"subject"}]
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
      .inPath("$.methodResponses[0][1].ids")
      .isEqualTo(s"""["${messageId2.serialize}", "${messageId1.serialize}", "${messageId3.serialize}"]""")
    }
  }

  @Test
  def listMailsShouldBeSortedWhenUsingTo(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)

    val message1: Message = Message.Builder
      .of
      .setTo("aaa@domain.tld")
      .setSubject("subject")
      .setBody("any body", StandardCharsets.UTF_8)
      .build
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().build(message1))
      .getMessageId

    val message2: Message = Message.Builder
      .of
      .setTo("ccc@domain.tld")
      .setSubject("subject")
      .setBody("any body", StandardCharsets.UTF_8)
      .build
    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().build(message2))
      .getMessageId

    val message3: Message = Message.Builder
      .of
      .setTo("aba@domain.tld")
      .setSubject("subject")
      .setBody("any body", StandardCharsets.UTF_8)
      .build
    val messageId3 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().build(message3))
      .getMessageId

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sort": [{"property":"to"}]
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
      .inPath("$.methodResponses[0][1].ids")
      .isEqualTo(s"""["${messageId1.serialize}", "${messageId3.serialize}", "${messageId2.serialize}"]""")
    }
  }

  @Test
  def listMailsShouldBeSortedWhenUsingFrom(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)

    val message1: Message = Message.Builder
      .of
      .setFrom("aaa@domain.tld")
      .setSubject("subject")
      .setBody("any body", StandardCharsets.UTF_8)
      .build
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().build(message1))
      .getMessageId

    val message2: Message = Message.Builder
      .of
      .setFrom("ccc@domain.tld")
      .setSubject("subject")
      .setBody("any body", StandardCharsets.UTF_8)
      .build
    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().build(message2))
      .getMessageId

    val message3: Message = Message.Builder
      .of
      .setFrom("aba@domain.tld")
      .setSubject("subject")
      .setBody("any body", StandardCharsets.UTF_8)
      .build
    val messageId3 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().build(message3))
      .getMessageId

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sort": [{"property":"from"}]
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
      .inPath("$.methodResponses[0][1].ids")
      .isEqualTo(s"""["${messageId1.serialize}", "${messageId3.serialize}", "${messageId2.serialize}"]""")
    }
  }

  @Test
  def listMailsShouldBeSortedByAscendingOrderOfInternalDateByDefaultWhenNoDateInHeader(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)

    val requestDateMessage1 = Date.from(ZonedDateTime.now().minusDays(2).toInstant)
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().withInternalDate(requestDateMessage1).build(message))
      .getMessageId

    val requestDateMessage2 = Date.from(ZonedDateTime.now().minusDays(1).toInstant)
    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().withInternalDate(requestDateMessage2).build(message))
      .getMessageId

    val requestDateMessage3 = Date.from(ZonedDateTime.now().toInstant)
    val messageId3 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().withInternalDate(requestDateMessage3).build(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sort": [{
         |        "property":"sentAt"
         |      }]
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
      .inPath("$.methodResponses[0][1].ids")
      .isEqualTo(s"""["${messageId1.serialize}", "${messageId2.serialize}", "${messageId3.serialize}"]""")
    }
  }

  @Test
  def listMailsShouldBeIdempotent(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, inbox(BOB), AppendCommand.from(message))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val responseFirstCall = `given`
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

      val responseSecondCall = `given`
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

      assertThatJson(responseFirstCall).isEqualTo(responseSecondCall)
    }
  }

  @Test
  def listMailsShouldBeSortedInAscendingOrderByDefault(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    val now: Instant = Instant.now()
    val requestDateMessage1: Date = Date.from(now)
    val messageId1 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withInternalDate(requestDateMessage1).build(message))
      .getMessageId
    val requestDateMessage2: Date = Date.from(now.plus(Seconds.of(3)))
    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withInternalDate(requestDateMessage2).build(message))
      .getMessageId
    val requestDateMessage3: Date = Date.from(now.plus(Seconds.of(6)))
    val messageId3 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withInternalDate(requestDateMessage3).build(message))
      .getMessageId
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sort": [{
         |        "property":"receivedAt"
         |      }]
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
      .inPath("$.methodResponses[0][1].ids")
      .isEqualTo(s"""["${messageId1.serialize}", "${messageId2.serialize}", "${messageId3.serialize}"]""")
    }
  }

  @Test
  def listMailsShouldBeSortedInAscendingOrder(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    val now: Instant = Instant.now()
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    val requestDateMessage1: Date = Date.from(now)
    val messageId1 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withInternalDate(requestDateMessage1).build(message))
      .getMessageId
    val requestDateMessage2: Date = Date.from(now.plus(Seconds.of(3)))
    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withInternalDate(requestDateMessage2).build(message))
      .getMessageId
    val requestDateMessage3: Date = Date.from(now.plus(Seconds.of(6)))
    val messageId3 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withInternalDate(requestDateMessage3).build(message))
      .getMessageId
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sort": [{
         |        "property":"receivedAt",
         |        "isAscending": true
         |      }]
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
      .inPath("$.methodResponses[0][1].ids")
      .isEqualTo(s"""["${messageId1.serialize}", "${messageId2.serialize}", "${messageId3.serialize}"]""")
    }
  }

  @Test
  def listMailsShouldBeSortedInDescendingOrder(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    val now: Instant = Instant.now()
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    val requestDateMessage1: Date = Date.from(now)
    val messageId1 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withInternalDate(requestDateMessage1).build(message))
      .getMessageId
    val requestDateMessage2: Date = Date.from(now.plus(Seconds.of(3)))
    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withInternalDate(requestDateMessage2).build(message))
      .getMessageId
    val requestDateMessage3: Date = Date.from(now.plus(Seconds.of(6)))
    val messageId3 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withInternalDate(requestDateMessage3).build(message))
      .getMessageId
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sort": [{
         |        "property":"receivedAt",
         |        "isAscending": false
         |      }]
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
      .inPath("$.methodResponses[0][1].ids")
      .isEqualTo(s"""["${messageId3.serialize}", "${messageId2.serialize}", "${messageId1.serialize}"]""")
    }
  }

  @Test
  def listMailsShouldReturnErrorWhenPropertyFieldInComparatorIsOmitted(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    val otherMailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "inMailbox": "${otherMailboxId.serialize}"
         |        },
         |      "sort": [{
         |        "isAscending":true
         |      }]
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
        .inPath("$.methodResponses[0][1]")
        .isEqualTo("""
         {
            "type": "invalidArguments",
            "description": "Missing '/sort(0)/property' property"
          }
         """)
    }
  }

  @Test
  def shouldListMailsInASpecificUserMailboxes(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    val otherMailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, inbox(BOB), AppendCommand.from(message))
      .getMessageId
    val messageId2: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "inMailbox": "${otherMailboxId.serialize}"
         |        }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId2.serialize}"]""")
    }
  }

  @Test
  def listMailsShouldReturnErrorWhenPropertyFieldInComparatorIsInvalid(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    val otherMailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "inMailbox": "${otherMailboxId.serialize}"
         |        },
         |      "sort": [{
         |        "property":"unsupported",
         |        "isAscending":true
         |      }]
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
        .inPath("$.methodResponses[0][1]")
        .isEqualTo("""
         {
            "type": "invalidArguments",
            "description": "'/sort(0)/property' property is not valid: 'unsupported' is not a supported sort property"
          }
         """)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = Array(
    "allInThreadHaveKeyword",
    "someInThreadHaveKeyword",
    "hasKeyword"
  ))
  def listMailsShouldReturnUnsupportedSortWhenPropertyFieldInComparatorIsValidButUnsupported(unsupported: String): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sort": [{
         |        "property":"$unsupported"
         |      }]
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
      .inPath("$.methodResponses[0][1]")
      .isEqualTo(s"""
       {
          "type": "unsupportedSort",
          "description": "The sort $unsupported is syntactically valid, but it includes a property the server does not support sorting on or a collation method it does not recognise."
       }
       """)
  }

  @ParameterizedTest
  @ValueSource(strings = Array(
    "allInThreadHaveKeyword",
    "someInThreadHaveKeyword",
    "noneInThreadHaveKeyword"
  ))
  def listMailsShouldReturnUnsupportedFilterWhenValidButUnsupported(unsupportedFilter: String): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "$unsupportedFilter": "abc"
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
      .inPath("$.methodResponses[0][1]")
      .isEqualTo(s"""
       {
          "type": "unsupportedFilter",
          "description": "The filter $unsupportedFilter is syntactically valid, but the server cannot process it. If the filter was the result of a user’s search input, the client SHOULD suggest that the user simplify their search."
       }
       """)
  }

  @ParameterizedTest
  @ValueSource(strings = Array(
    "true",
    "false"
  ))
  def collapseThreadsParameterShouldNoop(collapseThreads: Boolean, server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    val otherMailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(message))
      .getMessageId
    val messageId2: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "inMailbox": "${otherMailboxId.serialize}"
         |       },
         |       "collapseThreads": $collapseThreads
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId2.serialize}"]""")
    }
  }

  @Test
  def listMailsShouldReturnInvalidArgumentsWhenAnchorParameterIsPresent(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "anchor": "123"
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
      .inPath("$.methodResponses[0][1]")
      .isEqualTo(s"""
       {
          "type": "invalidArguments",
          "description": "The following parameter anchor is syntactically valid, but is not supported by the server."
       }
       """)
  }

  @Test
  def listMailsShouldReturnInvalidArgumentsWhenAnchorOffsetParameterIsPresent(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "anchorOffset": 0
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
      .inPath("$.methodResponses[0][1]")
      .isEqualTo(s"""
       {
          "type": "invalidArguments",
          "description": "The following parameter anchorOffset is syntactically valid, but is not supported by the server."
       }
       """)
  }

  @Test
  def shouldReturnIllegalArgumentErrorForAnUnknownSpecificUserMailboxes(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    val otherMailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, inbox(BOB), AppendCommand.from(message))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    server.getProbe(classOf[MailboxProbeImpl]).deleteMailbox(otherMailboxPath.getNamespace, BOB.asString(), otherMailboxPath.getName)

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "inMailbox": "${otherMailboxId.serialize}"
         |      }
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "error",
           |            {
           |                "type": "invalidArguments",
           |                "description": "${otherMailboxId.serialize} can not be found"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
    }
  }

  @Test
  def minSizeShouldBeInclusive(server: GuiceJamesServer): Unit = {
    val message1: Message = simpleMessage("short")
    computeSize(message1)
    // One char more than message1
    val message2: Message = simpleMessage("short!")
    val size2: Int = computeSize(message2)
    // One char more than message2
    val message3: Message = simpleMessage("short!!")
    computeSize(message3)

    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(inbox(BOB))
    mailboxProbe.appendMessage(BOB.asString, inbox(BOB), AppendCommand.from(message1)).getMessageId
    val id2 = mailboxProbe.appendMessage(BOB.asString, inbox(BOB), AppendCommand.from(message2)).getMessageId
    val id3 = mailboxProbe.appendMessage(BOB.asString, inbox(BOB), AppendCommand.from(message3)).getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "minSize": $size2
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(
          s"""[
             |  "${id2.serialize}",
             |  "${id3.serialize}"
             |]""".stripMargin)
    }
  }

  @Test
  def maxSizeShouldBeExclusive(server: GuiceJamesServer): Unit = {
    val message1: Message = simpleMessage("looooooooooooooong")
    computeSize(message1)
    // One char more than message3
    val message2: Message = simpleMessage("looooooooooooooong!")
    val size2: Int = computeSize(message2)
    // One char more than message4
    val message3: Message = simpleMessage("looooooooooooooong!!")
    computeSize(message3)

    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(inbox(BOB))
    val id1 = mailboxProbe.appendMessage(BOB.asString, inbox(BOB), AppendCommand.from(message1)).getMessageId
    mailboxProbe.appendMessage(BOB.asString, inbox(BOB), AppendCommand.from(message2)).getMessageId
    mailboxProbe.appendMessage(BOB.asString, inbox(BOB), AppendCommand.from(message3)).getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "maxSize": $size2
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(
          s"""[
             |  "${id1.serialize}"
             |]""".stripMargin)
    }
  }

  @Test
  def maxSizeShouldRejectNegative(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "maxSize": -1
         |      }
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
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |        [
             |            "error",
             |            {
             |                "type": "invalidArguments",
             |                "description": "'/filter/maxSize' property is not valid: Predicate (-1 < 0) did not fail."
             |            },
             |            "c1"
             |        ]
             |    ]
             |}""".stripMargin)
    }
  }

  @Test
  def minSizeShouldRejectNegative(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "minSize": -1
         |      }
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
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |        [
             |            "error",
             |            {
             |                "type": "invalidArguments",
             |                "description": "'/filter/minSize' property is not valid: Predicate (-1 < 0) did not fail."
             |            },
             |            "c1"
             |        ]
             |    ]
             |}""".stripMargin)
    }
  }

  private def simpleMessage(message: String) = {
    Message.Builder
      .of
      .setSubject("test")
      .setBody(message, StandardCharsets.UTF_8)
      .build
  }

  private def computeSize(message: Message): Int = {
    val writer = new DefaultMessageWriter()
    val stream = new ByteArrayOutputStream()
    writer.writeMessage(message, stream)
    stream.toByteArray.length
  }

  @Test
  def shouldListMailsNotInASpecificUserMailboxes(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    val otherMailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, inbox(BOB), AppendCommand.from(message))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "inMailboxOtherThan": [ "${otherMailboxId.serialize}" ]
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId1.serialize}"]""")
    }
  }

  @Test
  def shouldListMailsNotInZeroMailboxes(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    val requestDateMessage1 = Date.from(ZonedDateTime.now().minusDays(1).toInstant)
    val messageId1: MessageId = sendMessageToBobInbox(server, message, requestDateMessage1)
    val messageId2: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "inMailboxOtherThan": [  ]
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId2.serialize}", "${messageId1.serialize}"]""")
    }
  }

  @Test
  def listMailsInAFirstMailboxAndNotSomeOtherMailboxShouldReturnMailsInFirstMailbox(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    val inbox = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    val otherMailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(message))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "inMailbox":  "${inbox.serialize}",
         |        "inMailboxOtherThan": [ "${otherMailboxId.serialize}" ]
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId1.serialize}"]""")
    }
  }

  @Test
  def listMailsInAFirstMailboxAndNotInTheSameMailboxShouldReturnEmptyResult(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    val inbox = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(message))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "inMailbox":  "${inbox.serialize}",
         |        "inMailboxOtherThan": [ "${inbox.serialize}" ]
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo("[]")
    }
  }

  @Test
  def shouldListMailsReceivedBeforeADate(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(inbox(BOB))
    val requestDate = ZonedDateTime.now().minusDays(1)
    val messageId1 = sendMessageToBobInbox(server, message, Date.from(requestDate.toInstant))


    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "before": "${UTCDate(requestDate.plusHours(1)).asUTC.format(UTC_DATE_FORMAT)}"
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId1.serialize}"]""")
    }
  }

  @Test
  def shouldListMailsReceivedBeforeADateInclusively(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(inbox(BOB))
    val requestDate = ZonedDateTime.now().minusDays(1)
    val messageId1 = sendMessageToBobInbox(server, message, Date.from(requestDate.toInstant))

    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "before": "${UTCDate(requestDate).asUTC.format(UTC_DATE_FORMAT)}"
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId1.serialize}"]""")
    }
  }

  @Test
  def shouldListMailsReceivedBeforeInAMailbox(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(inbox(BOB))
    val requestDate = ZonedDateTime.now().minusDays(1)
    val messageId1 = sendMessageToBobInbox(server, message, Date.from(requestDate.toInstant))

    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "before": "${UTCDate(requestDate).asUTC.plusSeconds(1).format(UTC_DATE_FORMAT)}",
         |        "inMailbox": "${mailboxId.serialize()}"
         |      },
         |      "sort": [{
         |        "property":"receivedAt",
         |        "isAscending": false
         |      }]
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId1.serialize}"]""")
    }
  }

  @Test
  def shouldListMailsReceivedAfterADate(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(inbox(BOB))
    val receivedDateMessage1 = ZonedDateTime.now().minusDays(1)
    sendMessageToBobInbox(server, message, Date.from(receivedDateMessage1.toInstant))

    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    val receivedDateMessage2 = receivedDateMessage1.plusHours(2)
    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.builder().withInternalDate(Date.from(receivedDateMessage2.toInstant)).build(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "after": "${UTCDate(receivedDateMessage2.minusHours(1)).asUTC.format(UTC_DATE_FORMAT)}"
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId2.serialize}"]""")
    }
  }

  @Test
  def listMailsReceivedAfterADateShouldBeExclusive(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val receivedDateMessage1 = ZonedDateTime.now().minusDays(1)
    sendMessageToBobInbox(server, message, Date.from(receivedDateMessage1.toInstant))

    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "after": "${UTCDate(receivedDateMessage1).asUTC.format(UTC_DATE_FORMAT)}"
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId2.serialize}"]""")
    }
  }

  @Test
  def shouldLimitResultByTheLimitProvidedByTheClient(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    val requestDate = Date.from(ZonedDateTime.now().minusDays(1).toInstant)
    sendMessageToBobInbox(server, message, Date.from(requestDate.toInstant))

    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    val messageId2: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "limit": 1,
         |      "sort": [{
         |        "property":"receivedAt",
         |        "isAscending": false
         |      }]
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId2)}",
           |                "canCalculateChanges": false,
           |                "position": 0,
           |                "ids": ["${messageId2.serialize}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def shouldLimitResultByLimitAndPosition(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    mailboxProbe.createMailbox(MailboxPath.inbox(BOB))

    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    mailboxProbe.createMailbox(otherMailboxPath)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.builder()
        .withInternalDate(Date.from(ZonedDateTime.now().minusDays(4).toInstant))
        .build(message))
      .getMessageId

    val messageId2: MessageId = mailboxProbe
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.builder()
        .withInternalDate(Date.from(ZonedDateTime.now().minusDays(3).toInstant))
        .build(message))
      .getMessageId

    val messageId3: MessageId = mailboxProbe
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.builder()
        .withInternalDate(Date.from(ZonedDateTime.now().minusDays(2).toInstant))
        .build(message))
      .getMessageId

    mailboxProbe
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.builder()
        .withInternalDate(Date.from(ZonedDateTime.now().minusDays(1).toInstant))
        .build(message))
      .getMessageId

    sendMessageToBobInbox(server, message, Date.from(Date.from(ZonedDateTime.now().toInstant).toInstant))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "limit": 2,
         |      "position": 2,
         |      "sort": [{
         |        "property":"receivedAt",
         |        "isAscending": false
         |      }]
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted {() =>
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
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId3, messageId2)}",
           |                "canCalculateChanges": false,
           |                "position": 2,
           |                "ids": [
           |                  "${messageId3.serialize}",
           |                  "${messageId2.serialize}"
           |                ]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def shouldReturnEmptyWhenPositionIsOutOfBoundAndWithLimit(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    val requestDate = Date.from(ZonedDateTime.now().minusDays(1).toInstant)
    sendMessageToBobInbox(server, message, Date.from(requestDate.toInstant))

    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "limit": 2,
         |      "position": 2
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted {() =>
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
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState()}",
           |                "canCalculateChanges": false,
           |                "position": 2,
           |                "ids": []
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def shouldReturnAnIllegalArgumentExceptionIfTheLimitIsNegative(server: GuiceJamesServer): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "limit": -1
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "error",
           |            {
           |                "type": "invalidArguments",
           |                "description": "The limit can not be negative. -1 was provided."
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
    }
  }

  @Test
  def theLimitshouldBeEnforcedByTheServerIfNoLimitProvidedByTheClient(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))

    val allMessages = (0 to 300).toList.foldLeft(List[MessageId](), ZonedDateTime.now().minusYears(1))((acc, _) => {
      val (messageList, date) = acc
      val dateForNewMessage = date.plusDays(1)
      val messageId = sendMessageToBobInbox(server, message, Date.from(dateForNewMessage.toInstant))
      (messageId :: messageList, dateForNewMessage)
    })

    val expectedMessages = allMessages._1.take(256)

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sort": [{
         |        "property":"receivedAt",
         |        "isAscending": false
         |      }]
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo("[" + expectedMessages.map(message => s""""${message.serialize}"""").mkString(", ") + "]")

      assertThatJson(response)
        .inPath("$.methodResponses[0][1].limit")
        .isEqualTo("256")
    }
  }

  @Test
  def theLimitshouldBeEnforcedByTheServerIfAGreaterLimitProvidedByTheClient(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))

    val allMessages = (0 to 300).toList.foldLeft(List[MessageId](), ZonedDateTime.now().minusYears(1))((acc, _) => {
      val (messageList, date) = acc
      val dateForNewMessage = date.plusDays(1)
      val messageId = sendMessageToBobInbox(server, message, Date.from(dateForNewMessage.toInstant))
      (messageId :: messageList, dateForNewMessage)
    })

    val expectedMessages = allMessages._1.take(256)

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "limit": 2000,
         |      "sort": [{
         |        "property":"receivedAt",
         |        "isAscending": false
         |      }]
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo("[" + expectedMessages.map(message => s""""${message.serialize}"""").mkString(", ") + "]")

      assertThatJson(response)
        .inPath("$.methodResponses[0][1].limit")
        .isEqualTo("256")
    }
  }

  @Test
  def resultsShouldStartAtThePositionProvidedByTheClient(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl])
        .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder()
          .withInternalDate(Date.from(ZonedDateTime.now().minusDays(2).toInstant))
          .build(message))
        .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
        .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder()
          .withInternalDate(Date.from(ZonedDateTime.now().minusDays(1).toInstant))
          .build(message))
        .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "position": 1,
         |      "sort": [{
         |        "property":"receivedAt",
         |        "isAscending": false
         |      }]
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId1)}",
           |                "position": 1,
           |                "limit": 256,
           |                "canCalculateChanges": false,
           |                "ids": ["${messageId1.serialize}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def zeroPositionQueryShouldReturnItemsFromTheStart(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl])
        .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder()
          .withInternalDate(Date.from(ZonedDateTime.now().minusDays(2).toInstant))
          .build(message))
        .getMessageId
    val messageId2: MessageId = server.getProbe(classOf[MailboxProbeImpl])
        .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder()
          .withInternalDate(Date.from(ZonedDateTime.now().minusDays(1).toInstant))
          .build(message))
        .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "position": 0,
         |      "sort": [{
         |        "property":"receivedAt",
         |        "isAscending": false
         |      }]
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId2, messageId1)}",
           |                "position": 0,
           |                "limit": 256,
           |                "canCalculateChanges": false,
           |                "ids": ["${messageId2.serialize}", "${messageId1.serialize}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def resultsShouldBeEmptyWithoutErrorWhenThePositionProvidedByTheClientIsGreaterThanTheNumberOfResults(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    val requestDate = Date.from(ZonedDateTime.now().minusDays(1).toInstant)
    sendMessageToBobInbox(server, message, Date.from(requestDate.toInstant))

    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "position": 2
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState()}",
           |                "position": 2,
           |                "limit": 256,
           |                "canCalculateChanges": false,
           |                "ids": []
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def combiningSortPositionAndLimitShouldYieldExpectedResult(server: GuiceJamesServer): Unit = {
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val message: Message = buildTestMessage
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder()
        .withInternalDate(Date.from(ZonedDateTime.now().minusDays(2).toInstant))
        .build(message))
      .getMessageId
    val messageId2: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder()
        .withInternalDate(Date.from(ZonedDateTime.now().minusDays(2).toInstant))
        .build(message))
      .getMessageId
    val messageId3: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder()
        .withInternalDate(Date.from(ZonedDateTime.now().minusDays(2).toInstant))
        .build(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "position": 2,
         |      "limit": 2,
         |      "filter": {
         |          "inMailbox": "${mailboxId.serialize()}"
         |      },
         |      "sort": [{
         |          "property":"sentAt",
         |          "isAscending": false
         |      }]
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId1)}",
           |                "position": 2,
           |                "canCalculateChanges": false,
           |                "ids": ["${messageId1.serialize()}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def shouldReturnAnIllegalArgumentExceptionIfThePositionIsNegative(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val requestDate = Date.from(ZonedDateTime.now().minusDays(1).toInstant)
    sendMessageToBobInbox(server, message, Date.from(requestDate.toInstant))

    val otherMailboxPath = MailboxPath.forUser(BOB, "other")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(otherMailboxPath)

    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, otherMailboxPath, AppendCommand.from(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "position": -1
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "error",
           |            {
           |                "type": "invalidArguments",
           |                "description": "Negative position are not supported yet. -1 was provided."
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
    }
  }

  @ParameterizedTest
  @MethodSource(value = Array("jmapSystemKeywords"))
  def listMailsBySystemKeywordShouldReturnOnlyMailsWithThisSystemKeyword(keywordFlag: Flags, keywordName: String, server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withFlags(keywordFlag).build(message))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "hasKeyword": "$keywordName"
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId.serialize}"]""")
    }
  }

  @Test
  def hasKeywordShouldRejectEmpty(server: GuiceJamesServer): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "hasKeyword": ""
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
      .whenIgnoringPaths("methodResponses[0][1].description")
      .isEqualTo(s"""{
                    |    "sessionState": "${SESSION_STATE.value}",
                    |    "methodResponses": [
                    |        [
                    |            "error",
                    |            {
                    |                "type": "invalidArguments"
                    |            },
                    |            "c1"
                    |        ]
                    |    ]
                    |}""".stripMargin)
  }

  @Test
  def hasKeywordShouldRejectTooLong(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "hasKeyword": "${"a".repeat(257)}"
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
      .whenIgnoringPaths("methodResponses[0][1].description")
      .isEqualTo(s"""{
                   |    "sessionState": "${SESSION_STATE.value}",
                   |    "methodResponses": [
                   |        [
                   |            "error",
                   |            {
                   |                "type": "invalidArguments"
                   |            },
                   |            "c1"
                   |        ]
                   |    ]
                   |}""".stripMargin)
  }

  @Test
  def hasKeywordShouldNotAcceptIMAPDeletedKeyword(): Unit = {
    val request =
      """{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "hasKeyword": "$Deleted"
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
      .whenIgnoringPaths("methodResponses[0][1].description")
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "error",
           |            {
           |                "type": "invalidArguments"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def hasKeywordShouldNotAcceptIMAPRecentKeyword(): Unit = {
    val request =
      """{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "hasKeyword": "$Recent"
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
      .whenIgnoringPaths("methodResponses[0][1].description")
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "error",
           |            {
           |                "type": "invalidArguments"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def hasKeywordShouldRejectInvalid(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "hasKeyword": "custom&invalid"
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
      .whenIgnoringPaths("methodResponses[0][1].description")
      .isEqualTo(s"""{
                   |    "sessionState": "${SESSION_STATE.value}",
                   |    "methodResponses": [
                   |        [
                   |            "error",
                   |            {
                   |                "type": "invalidArguments"
                   |            },
                   |            "c1"
                   |        ]
                   |    ]
                   |}""".stripMargin)
  }

  @Test
  def listMailsByCustomKeywordShouldReturnOnlyMailsWithThisCustomKeyword(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withFlags(new Flags("custom")).build(message))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "hasKeyword": "custom"
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId.serialize}"]""")
    }
  }

  @Test
  def fromShouldFilterResultsWhenAddress(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    def messageBuilder = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
    val messageId1 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setFrom("user@domain.tld")
          .build))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setFrom("other@domain.tld")
          .build))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setFrom("yet@other.tld")
          .build))
      .getMessageId
    val messageId4 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setFrom("yet@other.tld", "user@domain.tld")
          .build))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder.build))
      .getMessageId
    val messageId6 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setFrom("\"User\" <user@domain.tld>")
          .build))
      .getMessageId
    val messageId7 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setFrom("\"user@domain.tld\" <other@domain.tld>")
          .build))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "from": "user@domain.tld"
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId7.serialize}", "${messageId6.serialize}", "${messageId4.serialize}", "${messageId1.serialize}"]""")
    }
  }

  @Test
  def fromShouldFilterResultsWhenNotAnAddress(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    def messageBuilder = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setFrom("user@domain.tld")
          .build))
      .getMessageId
    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setFrom("\"Display\" <other@domain.tld>")
          .build))
      .getMessageId
    val messageId3 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setFrom("display@other.tld")
          .build))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder.build))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "from": "Display"
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId2.serialize}", "${messageId3.serialize}"]""")
    }
  }

  @Test
  def toShouldFilterResultsWhenAddress(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    def messageBuilder = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
    val messageId1 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setTo("user@domain.tld")
          .build))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setTo("other@domain.tld")
          .build))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setTo("yet@other.tld")
          .build))
      .getMessageId
    val messageId4 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setTo("yet@other.tld", "user@domain.tld")
          .build))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder.build))
      .getMessageId
    val messageId6 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setTo("\"User\" <user@domain.tld>")
          .build))
      .getMessageId
    val messageId7 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setTo("\"user@domain.tld\" <other@domain.tld>")
          .build))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "to": "user@domain.tld"
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId7.serialize}", "${messageId6.serialize}", "${messageId4.serialize}", "${messageId1.serialize}"]""")
    }
  }

  @Test
  def toShouldFilterResultsWhenNotAnAddress(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    def messageBuilder = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setTo("user@domain.tld")
          .build))
      .getMessageId
    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setTo("\"Display\" <other@domain.tld>")
          .build))
      .getMessageId
    val messageId3 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setTo("display@other.tld")
          .build))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder.build))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "to": "Display"
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId2.serialize}", "${messageId3.serialize}"]""")
    }
  }

  @Test
  def ccShouldFilterResultsWhenAddress(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    def messageBuilder = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
    val messageId1 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setCc(DefaultAddressParser.DEFAULT.parseMailbox("user@domain.tld"))
          .build))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setCc(DefaultAddressParser.DEFAULT.parseMailbox("other@domain.tld"))
          .build))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setCc(DefaultAddressParser.DEFAULT.parseMailbox("yet@other.tld"))
          .build))
      .getMessageId
    val messageId4 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setCc(DefaultAddressParser.DEFAULT.parseMailbox("yet@other.tld"),
            DefaultAddressParser.DEFAULT.parseMailbox("user@domain.tld"))
          .build))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder.build))
      .getMessageId
    val messageId6 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setCc(DefaultAddressParser.DEFAULT.parseMailbox("\"User\" <user@domain.tld>"))
          .build))
      .getMessageId
    val messageId7 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setCc(DefaultAddressParser.DEFAULT.parseMailbox("\"user@domain.tld\" <other@domain.tld>"))
          .build))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "cc": "user@domain.tld"
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId7.serialize}", "${messageId6.serialize}", "${messageId4.serialize}", "${messageId1.serialize}"]""")
    }
  }

  @Test
  def ccShouldFilterResultsWhenNotAnAddress(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    def messageBuilder = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setCc(DefaultAddressParser.DEFAULT.parseMailbox("user@domain.tld"))
          .build))
      .getMessageId
    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setCc(DefaultAddressParser.DEFAULT.parseMailbox("\"Display\" <other@domain.tld>"))
          .build))
      .getMessageId
    val messageId3 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setCc(DefaultAddressParser.DEFAULT.parseMailbox("display@other.tld"))
          .build))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder.build))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "cc": "Display"
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId2.serialize}", "${messageId3.serialize}"]""")
    }
  }

  @Test
  def bccShouldFilterResultsWhenAddress(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    def messageBuilder = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
    val messageId1 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setBcc(DefaultAddressParser.DEFAULT.parseMailbox("user@domain.tld"))
          .build))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setBcc(DefaultAddressParser.DEFAULT.parseMailbox("other@domain.tld"))
          .build))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setBcc(DefaultAddressParser.DEFAULT.parseMailbox("yet@other.tld"))
          .build))
      .getMessageId
    val messageId4 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setBcc(DefaultAddressParser.DEFAULT.parseMailbox("yet@other.tld"),
            DefaultAddressParser.DEFAULT.parseMailbox("user@domain.tld"))
          .build))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder.build))
      .getMessageId
    val messageId6 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setBcc(DefaultAddressParser.DEFAULT.parseMailbox("\"User\" <user@domain.tld>"))
          .build))
      .getMessageId
    val messageId7 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setBcc(DefaultAddressParser.DEFAULT.parseMailbox("\"user@domain.tld\" <other@domain.tld>"))
          .build))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "bcc": "user@domain.tld"
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId7.serialize}", "${messageId6.serialize}", "${messageId4.serialize}", "${messageId1.serialize}"]""")
    }
  }

  @Test
  def bccShouldFilterResultsWhenNotAnAddress(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    def messageBuilder = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setBcc(DefaultAddressParser.DEFAULT.parseMailbox("user@domain.tld"))
          .build))
      .getMessageId
    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setBcc(DefaultAddressParser.DEFAULT.parseMailbox("\"Display\" <other@domain.tld>"))
          .build))
      .getMessageId
    val messageId3 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setBcc(DefaultAddressParser.DEFAULT.parseMailbox("display@other.tld"))
          .build))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder.build))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "bcc": "Display"
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId2.serialize}", "${messageId3.serialize}"]""")
    }
  }

  @Test
  def subjectShouldFilterEmails(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    def messageBuilder = Message.Builder
      .of
      .setBody("testmail", StandardCharsets.UTF_8)
    val messageId1 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setSubject("Yet another day in paradise")
          .build))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setSubject("Welcome to hell")
          .build))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder.build))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "subject": "paradise"
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId1.serialize}"]""")
    }
  }

  @Test
  def subjectShouldBeCaseInsensitive(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    def messageBuilder = Message.Builder
      .of
      .setBody("testmail", StandardCharsets.UTF_8)
    val messageId1 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setSubject("Yet another day in paradise")
          .build))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setSubject("Welcome to hell")
          .build))
      .getMessageId
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder.build))
      .getMessageId
    val messageId4 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        messageBuilder
          .setSubject("Yet another day in PaRaDiSe")
          .build))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "subject": "paradise"
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId1.serialize}", "${messageId4.serialize}"]""")
    }
  }

  @ParameterizedTest
  @MethodSource(value = Array("jmapSystemKeywords"))
  def listMailsNotBySystemKeywordShouldReturnOnlyMailsWithoutThisSystemKeyword(keywordFlag: Flags, keywordName: String, server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withFlags(keywordFlag).build(message))
      .getMessageId
    val messageWithoudFlagId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "notKeyword": "$keywordName"
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageWithoudFlagId.serialize}"]""")
    }
  }

  @Test
  def listMailsNotByCustomKeywordShouldReturnOnlyMailsWithoutThisCustomKeyword(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withFlags(new Flags("custom")).build(message))
      .getMessageId
    val messageWithoudFlagId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "notKeyword": "custom"
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageWithoudFlagId.serialize}"]""")
    }
  }

  @Test
  def emailQueryShouldSupportTextFilterForHeaders(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    mailboxProbe.createMailbox(inbox(BOB))

    val messageId1: MessageId = mailboxProbe
      .appendMessage(BOB.asString, inbox(BOB), AppendCommand.from(Message.Builder
        .of
        .setSubject("a mail")
        .setFrom("bloblah@domain.tld")
        .setFrom("test@other.tld")
        .setBody("lorem ipsum", StandardCharsets.UTF_8)
        .build))
      .getMessageId

    val messageId2: MessageId = mailboxProbe
      .appendMessage(BOB.asString, inbox(BOB), AppendCommand.from(Message.Builder
        .of
        .setSubject("another mail")
        .setTo("bloblah@domain.tld")
        .setTo("test@other.tld")
        .setBody("lorem ipsum", StandardCharsets.UTF_8)
        .build))
      .getMessageId

    val messageId3: MessageId = mailboxProbe
      .appendMessage(BOB.asString, inbox(BOB), AppendCommand.from(Message.Builder
        .of
        .setSubject("another mail")
        .addField(new RawField("Cc", "<bloblah@domain.tld>, <test@other.tld>"))
        .setBody("lorem ipsum", StandardCharsets.UTF_8)
        .build))
      .getMessageId

    val messageId4: MessageId = mailboxProbe
      .appendMessage(BOB.asString, inbox(BOB), AppendCommand.from(Message.Builder
        .of
        .setSubject("another mail")
        .addField(new RawField("Bcc", "<bloblah@domain.tld>, <test@other.tld>"))
        .setBody("lorem ipsum", StandardCharsets.UTF_8)
        .build))
      .getMessageId

    mailboxProbe
      .appendMessage(BOB.asString, inbox(BOB), AppendCommand.from(Message.Builder
        .of
        .setSubject("should not be found mail")
        .setBody("lorem ipsum", StandardCharsets.UTF_8)
        .build))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "text": "test@other.tld"
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(
          s"""[
             |  "${messageId4.serialize}",
             |  "${messageId3.serialize}",
             |  "${messageId2.serialize}",
             |  "${messageId1.serialize}"
             |]""".stripMargin)
    }
  }

  @Test
  def emailQueryShouldSupportTextFilterForTextBody(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(inbox(BOB))
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, inbox(BOB), AppendCommand.from(Message.Builder
        .of
        .setSubject("a mail")
        .setBody("This is a test body", StandardCharsets.UTF_8)
        .build))
      .getMessageId

    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, inbox(BOB), AppendCommand.from(Message.Builder
        .of
        .setSubject("should not be found mail")
        .setBody("lorem ipsum", StandardCharsets.UTF_8)
        .build))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "text": "test"
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(
          s"""[
             |  "${messageId1.serialize}"
             |]""".stripMargin)
    }
  }

  @Test
  def emailQueryShouldSupportTextFilterForHtmlBody(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(inbox(BOB))
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, inbox(BOB), AppendCommand.from(Message.Builder
        .of
        .setSubject("a mail")
        .setBody("<body>This is a test body</body>", "html", StandardCharsets.UTF_8)
        .build))
      .getMessageId

    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, inbox(BOB), AppendCommand.from(Message.Builder
        .of
        .setSubject("should not be found mail")
        .setBody("<body>This is another body</body>", "html", StandardCharsets.UTF_8)
        .build))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "text": "test"
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(
          s"""[
             |  "${messageId1.serialize}"
             |]""".stripMargin)
    }
  }

  @Test
  def emailQueryShouldSupportTextFilterForMultipartMessage(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(inbox(BOB))

    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, inbox(BOB), AppendCommand.from(Message.Builder
        .of
        .setSubject("a mail")
        .setBody(MultipartBuilder.create()
          .addTextPart("This is a test body", StandardCharsets.UTF_8)
          .build())
        .build))
      .getMessageId

    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, inbox(BOB), AppendCommand.from(Message.Builder
        .of
        .setSubject("should not be found mail")
        .setBody(MultipartBuilder.create()
          .addTextPart("This is another body", StandardCharsets.UTF_8)
          .build())
        .build))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "text": "test"
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(
          s"""[
             |  "${messageId1.serialize}"
             |]""".stripMargin)
    }
  }

  @Test
  def emailQueryFilterByTextShouldIgnoreMarkupsInHtmlBody(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(inbox(BOB))
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, inbox(BOB), AppendCommand.from(Message.Builder
        .of
        .setSubject("A mail")
        .setBody("<body><test>This is a html body<test></body>", "html", StandardCharsets.UTF_8)
        .build))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "text": "test"
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(
          s"""[]""".stripMargin)
    }
  }

  @Test
  def emailQueryFilterByTextShouldIgnoreAttachmentName(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(inbox(BOB))
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "text": "text2"
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(
          s"""[]""".stripMargin)
    }
  }

  @Test
  def emailQueryShouldSupportAndOperator(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withFlags(new Flags("custom")).build(message))
      .getMessageId

    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withFlags(new Flags("another_custom")).build(message))
      .getMessageId

    val messageId3 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withFlags(new FlagsBuilder().add("custom", "another_custom").build()).build(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "operator": "AND",
         |        "conditions": [
         |          { "hasKeyword": "custom" }, { "hasKeyword": "another_custom" }
         |        ]
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId3.serialize}"]""")
    }
  }

  @Test
  def emailQueryShouldSupportOrOperator(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val messageId1 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder()
        .withFlags(new Flags("custom"))
        .build(message))
      .getMessageId

    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder()
        .withFlags(new Flags("another_custom"))
        .build(message))
      .getMessageId

    val messageId3 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder()
        .withFlags(new FlagsBuilder().add("custom", "another_custom").build())
        .build(message))
      .getMessageId

    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "operator": "OR",
         |        "conditions": [
         |          { "hasKeyword": "custom" }, { "hasKeyword": "another_custom" }
         |        ]
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId3.serialize}", "${messageId2.serialize}", "${messageId1.serialize}"]""")
    }
  }

  @Test
  def emailQueryShouldSupportNotOperator(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder()
        .withFlags(new Flags("custom"))
        .build(message))
      .getMessageId

    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder()
        .withFlags(new Flags("another_custom"))
        .build(message))
      .getMessageId

    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder()
        .withFlags(new FlagsBuilder().add("custom", "another_custom").build())
        .build(message))
      .getMessageId

    val messageId4 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "operator": "NOT",
         |        "conditions": [
         |          { "hasKeyword": "custom" }, { "hasKeyword": "another_custom" }
         |        ]
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId4.serialize}"]""")
    }
  }

  @Test
  def emailQueryShouldRejectFilterOperatorWithExtraFields(server: GuiceJamesServer): Unit = {
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val requestDate = ZonedDateTime.now().minusDays(1)

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "inMailbox": "${mailboxId.serialize}",
         |        "before": "${UTCDate(requestDate.plusHours(1)).asUTC.format(UTC_DATE_FORMAT)}",
         |        "operator": "AND",
         |        "conditions": [
         |          { "hasKeyword": "custom" }, { "hasKeyword": "another_custom" }
         |        ]
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
      .isEqualTo(s"""{
                    |    "sessionState": "${SESSION_STATE.value}",
                    |    "methodResponses": [
                    |        [
                    |            "error",
                    |            {
                    |                "type": "invalidArguments",
                    |                "description": "'/filter' property is not valid: Expecting filterOperator to contain only operator and conditions"
                    |            },
                    |            "c1"
                    |        ]
                    |    ]
                    |}""".stripMargin)
  }

  @Test
  def emailQueryShouldRejectOperatorWithoutCondition(server: GuiceJamesServer): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "operator": "AND"
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
      .isEqualTo(s"""{
                    |    "sessionState": "${SESSION_STATE.value}",
                    |    "methodResponses": [
                    |        [
                    |            "error",
                    |            {
                    |                "type": "invalidArguments",
                    |                "description": "'/filter' property is not valid: Expecting filterOperator to contain only operator and conditions"
                    |            },
                    |            "c1"
                    |        ]
                    |    ]
                    |}""".stripMargin)
  }

  @Test
  def inMailboxShouldResolveSimpleFilter(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "other"))
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withFlags(new Flags("custom")).build(message))
      .getMessageId

    val messageId1 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withFlags(new Flags("another_custom")).build(message))
      .getMessageId

    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withFlags(new FlagsBuilder().add("custom", "another_custom").build()).build(message))
      .getMessageId

    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.forUser(BOB, "other"), AppendCommand.builder().withFlags(new Flags("custom")).build(message))
      .getMessageId

    val messageId3 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.forUser(BOB, "other"), AppendCommand.builder().withFlags(new Flags("another_custom")).build(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "operator": "AND",
         |        "conditions": [
         |          { "inMailbox": "${mailboxId.serialize()}" }, { "hasKeyword": "another_custom" }
         |        ]
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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .inPath("$.methodResponses[0][1].ids")
      .isEqualTo(s"""["${messageId1.serialize}","${messageId2.serialize}"]""")
  }

  @Test
  def inMailboxShouldBeRejectedWhenInOperator(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val mailboxId2 = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "other"))
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withFlags(new Flags("custom")).build(message))
      .getMessageId

    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withFlags(new Flags("another_custom")).build(message))
      .getMessageId

    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withFlags(new FlagsBuilder().add("custom", "another_custom").build()).build(message))
      .getMessageId

    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.forUser(BOB, "other"), AppendCommand.builder().withFlags(new Flags("custom")).build(message))
      .getMessageId

    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.forUser(BOB, "other"), AppendCommand.builder().withFlags(new Flags("another_custom")).build(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "operator": "AND",
         |        "conditions": [
         |          { "inMailbox": "${mailboxId.serialize()}" },
         |           {
         |             "operator": "OR",
         |             "conditions": [
         |                 { "inMailbox": "${mailboxId2.serialize()}" }, { "hasKeyword": "another_custom" }
         |             ]
         |           },
         |           { "hasKeyword": "another_custom" }
         |        ]
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
      .isEqualTo(s"""{
                    |    "sessionState": "${SESSION_STATE.value}",
                    |    "methodResponses": [
                    |        [
                    |            "error",
                    |            {
                    |                "type": "unsupportedFilter",
                    |                "description": "Nested inMailbox filters are not supported"
                    |            },
                    |            "c1"
                    |        ]
                    |    ]
                    |}""".stripMargin)
  }

  @Test
  def inMailboxOtherThanShouldBeRejectedWhenInOperator(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "other"))
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withFlags(new Flags("custom")).build(message))
      .getMessageId

     server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withFlags(new Flags("another_custom")).build(message))
      .getMessageId

    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withFlags(new FlagsBuilder().add("custom", "another_custom").build()).build(message))
      .getMessageId

    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.forUser(BOB, "other"), AppendCommand.builder().withFlags(new Flags("custom")).build(message))
      .getMessageId

    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.forUser(BOB, "other"), AppendCommand.builder().withFlags(new Flags("another_custom")).build(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "operator": "AND",
         |        "conditions": [
         |          { "inMailboxOtherThan": ["${mailboxId.serialize()}"] }, { "hasKeyword": "another_custom" }
         |        ]
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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .inPath("$.methodResponses[0][1].ids")
      .isEqualTo(s"""["${messageId.serialize}"]""")
  }

  @Test
  def inMailboxOtherThanShouldBeRejectedWhenOrOperator(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "other"))
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withFlags(new Flags("custom")).build(message))
      .getMessageId

    val messageId1 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withFlags(new Flags("another_custom")).build(message))
      .getMessageId

    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withFlags(new FlagsBuilder().add("custom", "another_custom").build()).build(message))
      .getMessageId

    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.forUser(BOB, "other"), AppendCommand.builder().withFlags(new Flags("custom")).build(message))
      .getMessageId

    val messageId3 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.forUser(BOB, "other"), AppendCommand.builder().withFlags(new Flags("another_custom")).build(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "operator": "OR",
         |        "conditions": [
         |          { "inMailboxOtherThan": ["${mailboxId.serialize()}"] }, { "hasKeyword": "another_custom" }
         |        ]
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
      .isEqualTo(s"""{
                    |    "sessionState": "${SESSION_STATE.value}",
                    |    "methodResponses": [
                    |        [
                    |            "error",
                    |            {
                    |                "type": "unsupportedFilter",
                    |                "description": "Nested inMailboxOtherThan filter are not supported"
                    |            },
                    |            "c1"
                    |        ]
                    |    ]
                    |}""".stripMargin)
  }

  @Test
  def emailQueryFilterByTextShouldIgnoreAttachmentContent(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(inbox(BOB))
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "text": "RSA PRIVATE"
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(
          s"""[]""".stripMargin)
    }
  }

  @Test
  def nestedOperatorsShouldBeSupported(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withFlags(new Flags("custom")).build(message))
      .getMessageId

    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withFlags(new Flags("another_custom")).build(message))
      .getMessageId

    val messageId3 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().withFlags(new FlagsBuilder().add("custom", "another_custom").build()).build(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "operator": "AND",
         |        "conditions": [
         |          {
         |            "operator": "AND",
         |            "conditions": [
         |              { "hasKeyword": "custom" }, { "hasKeyword": "another_custom" }
         |            ]
         |          }
         |        ]
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId3.serialize}"]""")
    }
  }

  @Test
  def shouldReturnInvalidArgumentsWhenInvalidFilterCondition(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter":{
         |        "unsupported_option": "blahh_blahh",
         |        "role":"Inbox"
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
        .inPath("methodResponses[0][1]")
        .isEqualTo(
        """{
          |  "type": "invalidArguments",
          |  "description": "'/filter' property is not valid: These '[unsupported_option, role]' was unsupported filter options"
          |}
          |""".stripMargin)
  }

  @Test
  def priorityOfNestedOperatorsShouldBePreserved(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))

    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder()
          .withFlags(new FlagsBuilder().add("custom_1", "custom_2").build())
          .build(message))
      .getMessageId

    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder()
          .withFlags(new FlagsBuilder().add("custom_1", "custom_3").build())
          .build(message))
      .getMessageId

    val messageId3 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder()
          .withFlags(new FlagsBuilder().add("custom_2", "custom_3").build())
          .build(message))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter" : {
         |        "operator": "AND",
         |        "conditions": [
         |          {
         |            "operator": "OR",
         |            "conditions": [
         |              { "hasKeyword": "custom_1" }, { "hasKeyword": "custom_2" }
         |            ]
         |          },
         |          { "hasKeyword": "custom_3" }
         |        ]
         |      }
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId2.serialize}", "${messageId3.serialize}"]""")
    }
  }

  private def sendMessageToBobInbox(server: GuiceJamesServer, message: Message, requestDate: Date): MessageId = {
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder().withInternalDate(requestDate).build(message))
      .getMessageId
  }

  private def generateQueryState(messages: MessageId*): String =
    Hashing.murmur3_32_fixed()
      .hashUnencodedChars(messages.toList.map(_.serialize).mkString(" "))
      .toString
}
