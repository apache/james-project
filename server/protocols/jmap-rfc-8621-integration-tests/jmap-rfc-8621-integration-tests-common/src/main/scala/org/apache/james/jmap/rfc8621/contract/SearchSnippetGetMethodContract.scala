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
import java.util.concurrent.TimeUnit

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import io.restassured.response.ResponseBodyExtractionOptions
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.MailboxACL.Right
import org.apache.james.mailbox.model.MailboxPath.inbox
import org.apache.james.mailbox.model.{MailboxACL, MailboxId, MailboxPath, MessageId}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.{ACLProbeImpl, MailboxProbeImpl}
import org.apache.james.util.ClassLoaderUtils
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.hamcrest.Matchers.{hasItem, hasSize}
import org.junit.jupiter.api.{BeforeEach, Test}

trait SearchSnippetGetMethodContract {

  private lazy val slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS
  private lazy val calmlyAwait = Awaitility.`with`
    .pollInterval(slowPacedPollInterval)
    .and.`with`.pollDelay(slowPacedPollInterval)
    .await
  private lazy val awaitAtMostTenSeconds = calmlyAwait.atMost(10, TimeUnit.SECONDS)

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
  def subjectShouldBeInSearchSnippetWhenMatched(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        Message.Builder
          .of
          .setSubject("Yet another day in paradise")
          .setBody("testmail", StandardCharsets.UTF_8)
          .build))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "SearchSnippet/get",
         |    {
         |      "accountId": "$ACCOUNT_ID",
         |      "filter" : {
         |        "subject": "paradise"
         |      },
         |      "emailIds": ["${messageId.serialize}"]
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
        .inPath("methodResponses[0]")
        .isEqualTo(
          s"""[
             |  "SearchSnippet/get",
             |  {
             |    "accountId": "$ACCOUNT_ID",
             |    "list": [
             |      {
             |        "emailId": "${messageId.serialize}",
             |        "subject": "Yet another day in <mark>paradise</mark>",
             |        "preview": null
             |      }
             |    ],
             |    "notFound": []
             |  },
             |  "c1"
             |]""".stripMargin)
    }
  }

  @Test
  def previewShouldBeInSearchSnippetWhenMatched(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        Message.Builder
          .of
          .setBody("You can close this page and return to the IDE intellij", StandardCharsets.UTF_8)
          .setSubject("Yet another day in paradise")
          .build))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "SearchSnippet/get",
         |    {
         |      "accountId": "$ACCOUNT_ID",
         |      "filter" : {
         |        "body": "IDE"
         |      },
         |      "emailIds": ["${messageId.serialize}"]
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response: ResponseBodyExtractionOptions = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract()
        .body

      assertThatJson(response.asString())
        .withOptions(IGNORING_ARRAY_ORDER)
        .inPath("methodResponses[0]")
        .isEqualTo(
          s"""[
             |  "SearchSnippet/get",
             |  {
             |    "accountId": "$ACCOUNT_ID",
             |    "list": [
             |      {
             |        "emailId": "${messageId.serialize}",
             |        "subject": null,
             |        "preview": "$${json-unit.ignore}"
             |      }
             |    ],
             |    "notFound": []
             |  },
             |  "c1"
             |]""".stripMargin)

      assertThat(response.jsonPath().get("methodResponses[0][1].list[0].preview").toString)
        .contains("return to the <mark>IDE</mark> intellij")
    }
  }

  @Test
  def searchSnippetGetShouldSupportBackReference(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        Message.Builder
          .of
          .setBody("You can close this page and return to the IDE intellij", StandardCharsets.UTF_8)
          .setSubject("Yet another day in paradise")
          .build))
      .getMessageId

    val request: String =
      s"""{
         |    "using": [
         |        "urn:ietf:params:jmap:core",
         |        "urn:ietf:params:jmap:mail"
         |    ],
         |    "methodCalls": [
         |        [
         |            "Email/query",
         |            {
         |                "accountId": "$ACCOUNT_ID",
         |                "filter": {
         |                    "body": "IDE"
         |                },
         |                "sort": [
         |                    {
         |                        "isAscending": false,
         |                        "property": "receivedAt"
         |                    }
         |                ],
         |                "limit": 20
         |            },
         |            "c0"
         |        ],
         |        [
         |            "SearchSnippet/get",
         |            {
         |                "accountId": "$ACCOUNT_ID",
         |                "filter": {
         |                    "body": "IDE"
         |                },
         |                "#emailIds": {
         |                    "resultOf": "c0",
         |                    "name": "Email/query",
         |                    "path": "/ids/*"
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response: ResponseBodyExtractionOptions = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract()
        .body

      assertThatJson(response.asString())
        .withOptions(IGNORING_ARRAY_ORDER)
        .inPath("methodResponses[1]")
        .isEqualTo(
          s"""[
             |  "SearchSnippet/get",
             |  {
             |    "accountId": "$ACCOUNT_ID",
             |    "list": [
             |      {
             |        "emailId": "${messageId.serialize}",
             |        "subject": null,
             |        "preview": "$${json-unit.ignore}"
             |      }
             |    ],
             |    "notFound": []
             |  },
             |  "c1"
             |]""".stripMargin)

      assertThat(response.jsonPath().get("methodResponses[1][1].list[0].preview").toString)
        .contains("return to the <mark>IDE</mark> intellij")
    }
  }

  @Test
  def searchSnippetShouldAcceptSharedMailboxesWhenExtension(server: GuiceJamesServer): Unit = {
    // Given: andres inbox with a message
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    val andreInboxId = mailboxProbe.createMailbox(inbox(ANDRE))
    val messageId: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString, inbox(ANDRE),
        AppendCommand.from(
          Message.Builder
            .of
            .setSubject("invite you to a sasuke meeting to discuss")
            .setBody("Please let naruto know if this time works for you or if you would prefer a different time.", StandardCharsets.UTF_8)
            .build)).getMessageId

    // Given: Bob has read rights on Andres mailbox
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(inbox(ANDRE), BOB.asString, new MailboxACL.Rfc4314Rights(Right.Read))

    // When: Bob search snippet request on Andres mailbox
    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [[
         |    "SearchSnippet/get",
         |    {
         |      "accountId": "$ACCOUNT_ID",
         |      "filter" : {
         |        "inMailbox": "${andreInboxId.serialize()}",
         |        "subject": "meeting"
         |      },
         |      "emailIds": ["${messageId.serialize}"]
         |    },
         |    "c1"]]
         |}""".stripMargin

    // Then: Bob should see the snippet
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
        .inPath("methodResponses[0]")
        .isEqualTo(
          s"""[
             |  "SearchSnippet/get",
             |  {
             |    "accountId": "$ACCOUNT_ID",
             |    "list": [
             |      {
             |        "emailId": "${messageId.serialize}",
             |        "subject": "invite you to a sasuke <mark>meeting</mark> to discuss",
             |        "preview": null
             |      }
             |    ],
             |    "notFound": []
             |  },
             |  "c1"
             |]""".stripMargin)
    }
  }

  @Test
  def searchSnippetShouldNotAcceptSharedMailboxesWhenNotExtension(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    val andreInboxId = mailboxProbe.createMailbox(inbox(ANDRE))
    val messageId: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString, inbox(ANDRE),
        AppendCommand.from(
          Message.Builder
            .of
            .setSubject("invite you to a sasuke meeting to discuss")
            .setBody("Please let naruto know if this time works for you or if you would prefer a different time.", StandardCharsets.UTF_8)
            .build))
      .getMessageId

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(inbox(ANDRE), BOB.asString, new MailboxACL.Rfc4314Rights(Right.Read))

    Thread.sleep(500)
    // request without urn:apache:james:params:jmap:mail:shares capability
    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [[
               |    "SearchSnippet/get",
               |    {
               |      "accountId": "$ACCOUNT_ID",
               |      "filter" : {
               |        "inMailbox": "${andreInboxId.serialize()}",
               |        "subject": "meeting"
               |      },
               |      "emailIds": ["${messageId.serialize}"]
               |    },
               |    "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].list", hasSize(0))
      .body("methodResponses[0][1].notFound", hasItem(messageId.serialize))
  }

  @Test
  def searchSnippetShouldNotAcceptNotSharedMailboxes(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    val andreInboxId = mailboxProbe.createMailbox(inbox(ANDRE))
    val messageId: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString, inbox(ANDRE),
        AppendCommand.from(
          Message.Builder
            .of
            .setSubject("invite you to a sasuke meeting to discuss")
            .setBody("Please let naruto know if this time works for you or if you would prefer a different time.", StandardCharsets.UTF_8)
            .build))
      .getMessageId

    Thread.sleep(300)
    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:apache:james:params:jmap:mail:shares"
               |  ],
               |  "methodCalls": [
               |    [
               |      "SearchSnippet/get",
               |      {
               |        "accountId": "$ACCOUNT_ID",
               |        "filter": {
               |          "inMailbox": "${andreInboxId.serialize()}",
               |          "subject": "meeting"
               |        },
               |        "emailIds": [ "${messageId.serialize}" ]
               |      },
               |      "c1"
               |    ]
               |  ]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].list", hasSize(0))
      .body("methodResponses[0][1].notFound", hasItem(messageId.serialize))
  }

  @Test
  def shouldReturnMultiSearchSnippetInListWhenMultiMatched(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))

    val messageId1 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        Message.Builder
          .of
          .setSubject("Weekly report - vttran 27/02-03/03/2023")
          .setBody("The weekly report has been in attachment. ", StandardCharsets.UTF_8)
          .build))
      .getMessageId

    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        Message.Builder
          .of
          .setSubject("Weekly report - vttran 19/08-23/08/2024")
          .setBody("The weekly report has been in attachment. ", StandardCharsets.UTF_8)
          .build))
      .getMessageId

    val messageId3 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        Message.Builder
          .of
          .setSubject("Weekly report - whynotme 12/08-16/08/2024")
          .setBody("The weekly report has been in attachment. ", StandardCharsets.UTF_8)
          .build))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "SearchSnippet/get",
         |    {
         |      "accountId": "$ACCOUNT_ID",
         |      "filter" : {
         |        "subject": "vttran"
         |      },
         |      "emailIds": ["${messageId1.serialize}", "${messageId2.serialize}", "${messageId3.serialize}"]
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
        .inPath("methodResponses[0]")
        .isEqualTo(
          s"""[
             |  "SearchSnippet/get",
             |  {
             |    "accountId": "$ACCOUNT_ID",
             |    "list": [
             |      {
             |        "emailId": "${messageId1.serialize}",
             |        "subject": "Weekly report - <mark>vttran</mark> 27/02-03/03/2023",
             |        "preview": null
             |      },
             |      {
             |        "emailId": "${messageId2.serialize}",
             |        "subject": "Weekly report - <mark>vttran</mark> 19/08-23/08/2024",
             |        "preview": null
             |      }
             |    ],
             |    "notFound": ["${messageId3.serialize}"]
             |  },
             |  "c1"
             |]""".stripMargin)
    }
  }

  @Test
  def shouldSupportOrOperatorInFilter(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))

    val messageId1 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        Message.Builder
          .of
          .setSubject("Weekly report - vttran 27/02-03/03/2023")
          .setBody("The weekly report has been in attachment", StandardCharsets.UTF_8)
          .build))
      .getMessageId

    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        Message.Builder
          .of
          .setSubject("Weekly report - whynotme 19/08-23/08/2024")
          .setBody("The weekly report of vttran has been in attachment", StandardCharsets.UTF_8)
          .build))
      .getMessageId

    val request =
      s"""{
         |  "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |  "methodCalls": [
         |    [
         |      "SearchSnippet/get",
         |      {
         |        "accountId": "$ACCOUNT_ID",
         |        "filter": {
         |          "operator": "OR",
         |          "conditions": [ { "subject": "vttran" }, { "body": "vttran" } ]
         |        },
         |        "emailIds": [ "${messageId1.serialize}", "${messageId2.serialize}" ]
         |      },
         |      "c1"
         |    ]
         |  ]
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
        .inPath("methodResponses[0]")
        .isEqualTo(
          s"""[
             |  "SearchSnippet/get",
             |  {
             |    "accountId": "$ACCOUNT_ID",
             |    "list": [
             |      {
             |        "emailId": "${messageId1.serialize}",
             |        "subject": "Weekly report - <mark>vttran</mark> 27/02-03/03/2023",
             |        "preview": null
             |      },
             |      {
             |        "emailId": "${messageId2.serialize}",
             |        "subject": null,
             |        "preview": "The weekly report of <mark>vttran</mark> has been in attachment"
             |      }
             |    ],
             |    "notFound": []
             |  },
             |  "c1"
             |]""".stripMargin)
    }
  }

  @Test
  def shouldReturnEmptyListWhenNotMatched(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))

    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        Message.Builder
          .of
          .setSubject("Weekly report - vttran 27/02-03/03/2023")
          .setBody("The weekly report has been in attachment", StandardCharsets.UTF_8)
          .build))
      .getMessageId

    Thread.sleep(500)

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [[
               |    "SearchSnippet/get",
               |    {
               |      "accountId": "$ACCOUNT_ID",
               |      "filter" : {
               |        "subject": "whynotme"
               |      },
               |      "emailIds": ["${messageId.serialize}"]
               |    },
               |    "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].list", hasSize(0))
      .body("methodResponses[0][1].notFound", hasItem(messageId.serialize))
  }

  @Test
  def shouldReturnMatchedSearchSnippetWhenDelegated(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe]).addAuthorizedUser(ANDRE, BOB)

    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(ANDRE))
    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString, MailboxPath.inbox(ANDRE), AppendCommand.builder().build(
        Message.Builder
          .of
          .setSubject("Yet another day in paradise")
          .setBody("testmail", StandardCharsets.UTF_8)
          .build))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "SearchSnippet/get",
         |    {
         |      "accountId": "$ANDRE_ACCOUNT_ID",
         |      "filter" : {
         |        "subject": "paradise"
         |      },
         |      "emailIds": ["${messageId.serialize}"]
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response: String = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
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
        .inPath("methodResponses[0]")
        .isEqualTo(
          s"""[
             |  "SearchSnippet/get",
             |  {
             |    "accountId": "$ANDRE_ACCOUNT_ID",
             |    "list": [
             |      {
             |        "emailId": "${messageId.serialize}",
             |        "subject": "Yet another day in <mark>paradise</mark>",
             |        "preview": null
             |      }
             |    ],
             |    "notFound": []
             |  },
             |  "c1"
             |]""".stripMargin)
    }
  }

  @Test
  def shouldFailWhenWrongAccountId(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        Message.Builder
          .of
          .setSubject("Yet another day in paradise")
          .setBody("testmail", StandardCharsets.UTF_8)
          .build))
      .getMessageId

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [[
               |    "SearchSnippet/get",
               |    {
               |      "accountId": "unknownAccountId",
               |      "filter" : {
               |        "subject": "paradise"
               |      },
               |      "emailIds": ["${messageId.serialize}"]
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

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "error",
           |  {
           |    "type": "accountNotFound"
           |  },
           |  "c1"
           |]""".stripMargin)
  }

  @Test
  def shouldReturnNotFoundWhenEmailIdDoesNotBelongToAccount(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(ANDRE))
    val messageIdOfAndre = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString, MailboxPath.inbox(ANDRE), AppendCommand.builder().build(
        Message.Builder
          .of
          .setSubject("Yet another day in paradise")
          .setBody("testmail", StandardCharsets.UTF_8)
          .build))
      .getMessageId

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body( s"""{
                |  "using": [
                |    "urn:ietf:params:jmap:core",
                |    "urn:ietf:params:jmap:mail"],
                |  "methodCalls": [[
                |    "SearchSnippet/get",
                |    {
                |      "accountId": "$ACCOUNT_ID",
                |      "filter" : {
                |        "subject": "paradise"
                |      },
                |      "emailIds": ["${messageIdOfAndre.serialize()}"]
                |    },
                |    "c1"]]
                |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].list", hasSize(0))
      .body("methodResponses[0][1].notFound", hasItem(messageIdOfAndre.serialize()))
  }

  @Test
  def shouldFailWhenEmailIdCanNotParse(server: GuiceJamesServer): Unit = {
    val response: String = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:mail"],
           |  "methodCalls": [[
           |    "SearchSnippet/get",
           |    {
           |      "accountId": "$ACCOUNT_ID",
           |      "filter" : {
           |        "subject": "paradise"
           |      },
           |      "emailIds": ["invalidMessageId@"]
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

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "error",
           |  {
           |    "type": "invalidArguments",
           |    "description": "$${json-unit.ignore}"
           |  },
           |  "c1"
           |]""".stripMargin)
  }

  @Test
  def shouldReturnEmptyListWhenEmailIdsInRequestEmpty(server: GuiceJamesServer): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body( s"""{
                |  "using": [
                |    "urn:ietf:params:jmap:core",
                |    "urn:ietf:params:jmap:mail"],
                |  "methodCalls": [[
                |    "SearchSnippet/get",
                |    {
                |      "accountId": "$ACCOUNT_ID",
                |      "filter" : {
                |        "subject": "paradise"
                |      },
                |      "emailIds": []
                |    },
                |    "c1"]]
                |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].list", hasSize(0))
      .body("methodResponses[0][1].notFound", hasSize(0))
  }

  @Test
  def shouldReturnUnknownMethodWhenMissingOneCapability(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        Message.Builder
          .of
          .setSubject("Yet another day in paradise")
          .setBody("testmail", StandardCharsets.UTF_8)
          .build))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core"
         |  ],
         |  "methodCalls": [[
         |    "SearchSnippet/get",
         |    {
         |      "accountId": "$ACCOUNT_ID",
         |      "filter" : {
         |        "subject": "paradise"
         |      },
         |      "emailIds": ["${messageId.serialize}"]
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
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "error",
           |  {
           |    "type": "unknownMethod",
           |    "description": "Missing capability(ies): urn:ietf:params:jmap:mail"
           |  },
           |  "c1"
           |]""".stripMargin)
  }

  @Test
  def shouldReturnUnknownMethodWhenMissingAllCapabilities(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.builder().build(
        Message.Builder
          .of
          .setSubject("Yet another day in paradise")
          .setBody("testmail", StandardCharsets.UTF_8)
          .build))
      .getMessageId

    val request =
      s"""{
         |  "using": [],
         |  "methodCalls": [[
         |    "SearchSnippet/get",
         |    {
         |      "accountId": "$ACCOUNT_ID",
         |      "filter" : {
         |        "subject": "paradise"
         |      },
         |      "emailIds": ["${messageId.serialize}"]
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
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "error",
           |  {
           |    "type": "unknownMethod",
           |    "description": "Missing capability(ies): urn:ietf:params:jmap:core, urn:ietf:params:jmap:mail"
           |  },
           |  "c1"
           |]""".stripMargin)
  }


  @Test
  def shouldReturnMatchingResultWhenSearchSnippetInHTMLBody(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val inboxId: MailboxId = mailboxProbe.createMailbox(MailboxPath.inbox(BOB))
    val messageId1: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), MailboxPath.inbox(BOB), AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/html_body.eml")))
      .getMessageId

    val keywordSearch: String = "barcamp"

    val request: String =
      s"""{
         |    "using": [
         |        "urn:ietf:params:jmap:core",
         |        "urn:ietf:params:jmap:mail"
         |    ],
         |    "methodCalls": [
         |        [
         |            "Email/query",
         |            {
         |                "accountId": "$ACCOUNT_ID",
         |                "filter": {
         |                    "body": "$keywordSearch"
         |                },
         |                "sort": [
         |                    {
         |                        "isAscending": false,
         |                        "property": "receivedAt"
         |                    }
         |                ],
         |                "limit": 20
         |            },
         |            "c0"
         |        ],
         |        [
         |            "SearchSnippet/get",
         |            {
         |                "accountId": "$ACCOUNT_ID",
         |                "filter": {
         |                    "body": "$keywordSearch"
         |                },
         |                "#emailIds": {
         |                    "resultOf": "c0",
         |                    "name": "Email/query",
         |                    "path": "/ids/*"
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response: ResponseBodyExtractionOptions = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract()
        .body

      assertThatJson(response.asString())
        .withOptions(IGNORING_ARRAY_ORDER)
        .inPath("methodResponses[1]")
        .isEqualTo(
          s"""[
             |  "SearchSnippet/get",
             |  {
             |    "accountId": "$ACCOUNT_ID",
             |    "list": [
             |      {
             |        "emailId": "${messageId1.serialize()}",
             |        "subject": null,
             |        "preview": "$${json-unit.ignore}"
             |      }
             |    ],
             |    "notFound": []
             |  },
             |  "c1"
             |]""".stripMargin)

      assertThat(response.jsonPath().get("methodResponses[1][1].list[0].preview").toString)
        .contains(s"<mark>$keywordSearch</mark>")
    }
  }
}
