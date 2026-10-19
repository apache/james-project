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
import java.time.temporal.ChronoUnit
import java.time.{Duration, Instant}
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import com.google.common.hash.Hashing
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.builder.ResponseSpecBuilder
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.{SC_CREATED, SC_OK}
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UuidState.INSTANCE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.method.EmailSubmissionSetMethod
import org.apache.james.jmap.rfc8621.contract.EmailSubmissionSetMethodContract.TestContext
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE_PASSWORD, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder, identityIdOf}
import org.apache.james.junit.categories.BasicFeature
import org.apache.james.mailbox.DefaultMailboxes
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.MailboxACL.Right
import org.apache.james.mailbox.model.{MailboxACL, MailboxId, MailboxPath, MessageId}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.{ACLProbeImpl, MailboxProbeImpl}
import org.apache.james.utils.DataProbeImpl
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.junit.experimental.categories.Category
import org.junit.jupiter.api.{BeforeEach, Test}
import play.api.libs.json.{JsObject, JsString, Json}

object EmailSubmissionSetMethodContract {
  case class TestContext(bobUsername: Username, bobAccountId: String,
                         andreUsername: Username, andreAccountId: String)
  val currentContext: AtomicReference[TestContext] = new AtomicReference[TestContext]()
}

trait EmailSubmissionSetMethodContract {
  def bobUsername: Username = EmailSubmissionSetMethodContract.currentContext.get().bobUsername
  def bobAccountId: String = EmailSubmissionSetMethodContract.currentContext.get().bobAccountId
  def andreUsername: Username = EmailSubmissionSetMethodContract.currentContext.get().andreUsername
  def andreAccountId: String = EmailSubmissionSetMethodContract.currentContext.get().andreAccountId

  private lazy val slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS
  private lazy val calmlyAwait = Awaitility.`with`
    .pollInterval(slowPacedPollInterval)
    .and.`with`.pollDelay(slowPacedPollInterval)
    .await
  private lazy val awaitAtMostTenSeconds = calmlyAwait.atMost(10, TimeUnit.SECONDS)

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    val uniqueSuffix = UUID.randomUUID().toString.replace("-", "").take(8)
    val bob = Username.fromLocalPartWithDomain(s"bob$uniqueSuffix", DOMAIN)
    val andre = Username.fromLocalPartWithDomain(s"andre$uniqueSuffix", DOMAIN)
    EmailSubmissionSetMethodContract.currentContext.set(TestContext(
      bobUsername = bob,
      bobAccountId = Hashing.sha256().hashString(bob.asString(), StandardCharsets.UTF_8).toString,
      andreUsername = andre,
      andreAccountId = Hashing.sha256().hashString(andre.asString(), StandardCharsets.UTF_8).toString))

    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(bob.asString, BOB_PASSWORD)
      .addUser(andre.asString, ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(bob, BOB_PASSWORD)))
      .build
  }

  def randomMessageId: MessageId

  @Test
  def emailSubmissionSetCreateShouldSendMailSuccessfully(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(bobUsername.asString)
      .setFrom(bobUsername.asString)
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val andreInboxPath = MailboxPath.inbox(andreUsername)
    val andreInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreInboxPath)

    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${andreUsername.asString}"}]
         |           }
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
    .when
      .post
    .`then`
      .statusCode(SC_OK)

    val requestAndre =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$andreAccountId",
         |      "filter": {"inMailbox": "${andreInboxId.serialize}"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`(
        baseRequestSpecBuilder(server)
          .setAuth(authScheme(UserCredential(andreUsername, ANDRE_PASSWORD)))
          .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
          .setBody(requestAndre)
          .build, new ResponseSpecBuilder().build)
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(response)
        .inPath("methodResponses[0][1].ids")
        .isArray
        .hasSize(1)
    }
  }
  
  @Test
  def emailSubmissionSetCreateShouldSendMailSuccessfullyWithNamedFrom(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(bobUsername.asString)
      .setFrom(s"Bob <${bobUsername.asString}>")
      .setTo(andreUsername.asString, s"Bob <${bobUsername.asString}>")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val andreInboxPath = MailboxPath.inbox(andreUsername)
    val andreInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreInboxPath)

    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${andreUsername.asString}"}]
         |           }
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
    .when
      .post
    .`then`
      .statusCode(SC_OK)

    val requestAndre =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$andreAccountId",
         |      "filter": {"inMailbox": "${andreInboxId.serialize}"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`(
        baseRequestSpecBuilder(server)
          .setAuth(authScheme(UserCredential(andreUsername, ANDRE_PASSWORD)))
          .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
          .setBody(requestAndre)
          .build, new ResponseSpecBuilder().build)
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(response)
        .inPath("methodResponses[0][1].ids")
        .isArray
        .hasSize(1)
    }
  }

  @Test
  def envelopeShouldBeOptional(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(bobUsername.asString)
      .setFrom(bobUsername.asString)
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val andreInboxPath = MailboxPath.inbox(andreUsername)
    val andreInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreInboxPath)

    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${messageId.serialize}"
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
    .when
      .post
    .`then`
      .statusCode(SC_OK)

    val requestAndre =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$andreAccountId",
         |      "filter": {"inMailbox": "${andreInboxId.serialize}"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`(
        baseRequestSpecBuilder(server)
          .setAuth(authScheme(UserCredential(andreUsername, ANDRE_PASSWORD)))
          .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
          .setBody(requestAndre)
          .build, new ResponseSpecBuilder().build)
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(response)
        .inPath("methodResponses[0][1].ids")
        .isArray
        .hasSize(1)
    }
  }

  @Test
  def emailSubmissionSetCreateShouldSendMailSuccessfullyToSelf(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(bobUsername.asString)
      .setFrom(bobUsername.asString)
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val bobInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(bobUsername))
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${bobUsername.asString}"}]
         |           }
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
    .when
      .post
    .`then`
      .statusCode(SC_OK)

    val requestReadMail =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$bobAccountId",
         |      "filter": {"inMailbox": "${bobInboxId.serialize}"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(requestReadMail)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(response)
        .inPath("methodResponses[0][1].ids")
        .isArray
        .hasSize(1)
    }
  }

  @Test
  def mimeSenderShouldAcceptAliases(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(s"bob.alias@${DOMAIN.asString}")
      .setFrom(bobUsername.asString)
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    server.getProbe(classOf[DataProbeImpl]).addUserAliasMapping("bob.alias", DOMAIN.asString(), bobUsername.asString)

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val bobInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(bobUsername))
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${bobUsername.asString}"}]
         |           }
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
    .when
      .post
    .`then`
      .statusCode(SC_OK)

    val requestReadMail =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$bobAccountId",
         |      "filter": {"inMailbox": "${bobInboxId.serialize}"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(requestReadMail)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(response)
        .inPath("methodResponses[0][1].ids")
        .isArray
        .hasSize(1)
    }
  }

  @Test
  def mimeFromShouldAcceptAliases(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(bobUsername.asString)
      .setFrom(s"bob.alias@${DOMAIN.asString}")
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    server.getProbe(classOf[DataProbeImpl]).addUserAliasMapping("bob.alias", DOMAIN.asString(), bobUsername.asString)

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val bobInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(bobUsername))
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${bobUsername.asString}"}]
         |           }
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
    .when
      .post
    .`then`
      .statusCode(SC_OK)

    val requestReadMail =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$bobAccountId",
         |      "filter": {"inMailbox": "${bobInboxId.serialize}"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(requestReadMail)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(response)
        .inPath("methodResponses[0][1].ids")
        .isArray
        .hasSize(1)
    }
  }

  @Test
  def envelopeFromShouldAcceptAliases(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(bobUsername.asString)
      .setFrom(bobUsername.asString)
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    server.getProbe(classOf[DataProbeImpl]).addUserAliasMapping("bob.alias", DOMAIN.asString(), bobUsername.asString)

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val bobInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(bobUsername))
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "bob.alias@${DOMAIN.asString}"},
         |             "rcptTo": [{"email": "${bobUsername.asString}"}]
         |           }
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
    .when
      .post
    .`then`
      .statusCode(SC_OK)

    val requestReadMail =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$bobAccountId",
         |      "filter": {"inMailbox": "${bobInboxId.serialize}"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(requestReadMail)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(response)
        .inPath("methodResponses[0][1].ids")
        .isArray
        .hasSize(1)
    }
  }

  @Test
  def emailSubmissionSetCreateShouldSendMailSuccessfullyToBothRecipients(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(bobUsername.asString)
      .setFrom(bobUsername.asString)
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val bobInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(bobUsername))
    val andreInboxPath = MailboxPath.inbox(andreUsername)
    val andreInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreInboxPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId


    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${bobUsername.asString}"}, {"email": "${andreUsername.asString}"}]
         |           }
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
    .when
      .post
    .`then`
      .statusCode(SC_OK)

    val requestReadMailBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$bobAccountId",
         |      "filter": {"inMailbox": "${bobInboxId.serialize}"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    val requestReadMailAndre =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$andreAccountId",
         |      "filter": {"inMailbox": "${andreInboxId.serialize}"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val responseBob = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(requestReadMailBob)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString
      val responseAndre = `given`(
        baseRequestSpecBuilder(server)
          .setAuth(authScheme(UserCredential(andreUsername, ANDRE_PASSWORD)))
          .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
          .setBody(requestReadMailAndre)
          .build, new ResponseSpecBuilder().build)
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(responseBob)
        .inPath("methodResponses[0][1].ids")
        .isArray
        .hasSize(1)
      assertThatJson(responseAndre)
        .inPath("methodResponses[0][1].ids")
        .isArray
        .hasSize(1)
    }
  }

  @Test
  def emailSubmissionSetCanBeChainedAfterEmailSet(server: GuiceJamesServer): Unit = {
    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    val draftId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val bobInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(bobUsername))

    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$bobAccountId",
         |      "create": {
         |        "e1526":{
         |          "mailboxIds": {"${draftId.serialize}": true},
         |          "to": [{"email": "${bobUsername.asString}"}],
         |          "from": [{"email": "${bobUsername.asString}"}]
         |        }
         |      }
         |    }, "c1"],
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "#e1526",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${bobUsername.asString}"}]
         |           }
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
    .when
      .post
    .`then`
      .statusCode(SC_OK)

    val requestReadMailBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$bobAccountId",
         |      "filter": {"inMailbox": "${bobInboxId.serialize}"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val responseBob = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(requestReadMailBob)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(responseBob)
        .inPath("methodResponses[0][1].ids")
        .isArray
        .hasSize(1)
    }
  }

  @Test
  def emailSubmissionSetCreateShouldReturnSuccess(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(bobUsername.asString)
      .setFrom(bobUsername.asString)
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${andreUsername.asString}"}]
         |           }
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      // Ids are randomly generated, and not stored, let's ignore it
      .whenIgnoringPaths("methodResponses[0][1].created.k1490")
      .inPath("methodResponses[0][1].created")
      .isEqualTo("""{"k1490": {}}""")
  }

  @Test
  def emailSubmissionSetCreateShouldAcceptIdentityId(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(bobUsername.asString)
      .setFrom(bobUsername.asString)
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${andreUsername.asString}"}]
         |           }
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      // Ids are randomly generated, and not stored, let's ignore it
      .whenIgnoringPaths("methodResponses[0][1].created.k1490")
      .inPath("methodResponses[0][1].created")
      .isEqualTo("""{"k1490": {}}""")
  }

  @Test
  def emailSubmissionSetCreateShouldAcceptCustomIdentityId(server: GuiceJamesServer): Unit = {
    val messageId: MessageId = appendBobDraft(server, "test")

    val identityCreation =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |    ["Identity/set", {
         |      "accountId": "$bobAccountId",
         |      "create": {
         |        "i1": {
         |          "name": "Bob custom",
         |          "email": "${bobUsername.asString}"
         |        }
         |      }
         |    }, "c1"]]
         |}""".stripMargin

    val customIdentityId: String = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(identityCreation)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .jsonPath()
      .getString("methodResponses[0][1].created.i1.id")

    val response = submit(messageId, Some(customIdentityId))

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].created.k1490")
      .inPath("methodResponses[0][1].created")
      .isEqualTo("""{"k1490": {}}""")
  }

  @Test
  def setShouldRejectUnparsableIdentityId(server: GuiceJamesServer): Unit = {
    val messageId: MessageId = appendBobDraft(server, "test")

    val response = submit(messageId, Some("Iunknown0000"))

    assertThatJson(response)
      .inPath("methodResponses[0][1].notCreated")
      .isEqualTo("""{
                   |  "k1490": {
                   |    "type": "invalidProperties",
                   |    "description": "The identity to be used cannot be found",
                   |    "properties": ["identityId"]
                   |  }
                   |}""".stripMargin)
  }

  @Test
  def setShouldRejectUnknownIdentityId(server: GuiceJamesServer): Unit = {
    val messageId: MessageId = appendBobDraft(server, "test")

    val response = submit(messageId, Some(UUID.randomUUID().toString))

    assertThatJson(response)
      .inPath("methodResponses[0][1].notCreated")
      .isEqualTo("""{
                   |  "k1490": {
                   |    "type": "invalidProperties",
                   |    "description": "The identity to be used cannot be found",
                   |    "properties": ["identityId"]
                   |  }
                   |}""".stripMargin)
  }

  @Test
  @Category(Array(classOf[BasicFeature]))
  def setShouldRejectIdentityIdOfOtherUsers(server: GuiceJamesServer): Unit = {
    val andreInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(andreUsername))
    val rejectedMessageId: MessageId = appendBobDraft(server, "rejected")
    val acceptedMessageId: MessageId = appendBobDraft(server, "accepted")

    val response = submit(
      "rejected" -> (rejectedMessageId, Some(identityIdOf(andreUsername))),
      "accepted" -> (acceptedMessageId, Some(identityIdOf(bobUsername))))

    assertThatJson(response)
      .inPath("methodResponses[0][1].notCreated")
      .isEqualTo("""{
                   |  "rejected": {
                   |    "type": "invalidProperties",
                   |    "description": "The identity to be used cannot be found",
                   |    "properties": ["identityId"]
                   |  }
                   |}""".stripMargin)
    andreInboxShouldOnlyContain(server, andreInboxId, "accepted")
  }

  @Test
  @Category(Array(classOf[BasicFeature]))
  def setShouldAcceptMissingIdentityId(server: GuiceJamesServer): Unit = {
    val andreInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(andreUsername))
    val messageId: MessageId = appendBobDraft(server, "accepted")

    val response = submit("accepted" -> (messageId, None))

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].created.accepted")
      .inPath("methodResponses[0][1].created")
      .isEqualTo("""{"accepted": {}}""")
    andreInboxShouldOnlyContain(server, andreInboxId, "accepted")
  }

  @Test
  def setShouldRejectMissingIdentityIdWhenCompatibilityIsDisabled(server: GuiceJamesServer): Unit = {
    System.setProperty(EmailSubmissionSetMethod.MISSING_IDENTITY_COMPATIBILITY_PROPERTY, "false")
    try {
      val andreInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(andreUsername))
      val rejectedMessageId: MessageId = appendBobDraft(server, "rejected")
      val acceptedMessageId: MessageId = appendBobDraft(server, "accepted")

      val response = submit(
        "rejected" -> (rejectedMessageId, None),
        "accepted" -> (acceptedMessageId, Some(identityIdOf(bobUsername))))

      assertThatJson(response)
        .inPath("methodResponses[0][1].notCreated")
        .isEqualTo("""{
                     |  "rejected": {
                     |    "type": "invalidProperties",
                     |    "description": "Missing 'identityId' property",
                     |    "properties": ["identityId"]
                     |  }
                     |}""".stripMargin)
      andreInboxShouldOnlyContain(server, andreInboxId, "accepted")
    } finally {
      System.clearProperty(EmailSubmissionSetMethod.MISSING_IDENTITY_COMPATIBILITY_PROPERTY)
    }
  }

  private def appendBobDraft(server: GuiceJamesServer, subject: String): MessageId = {
    val message: Message = Message.Builder
      .of
      .setSubject(subject)
      .setSender(bobUsername.asString)
      .setFrom(bobUsername.asString)
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    if (!mailboxProbe.listUserMailboxes(bobUsername.asString).contains(DefaultMailboxes.DRAFTS)) {
      mailboxProbe.createMailbox(bobDraftsPath)
    }
    mailboxProbe.appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId
  }

  private def submit(messageId: MessageId, identityId: Option[String]): String =
    submit("k1490" -> (messageId, identityId))

  private def submit(creations: (String, (MessageId, Option[String]))*): String = {
    val create: String = creations.map {
      case (creationId, (messageId, identityId)) =>
        s"""    "$creationId": {
           |      ${identityId.map(id => s""""identityId": "$id",""").getOrElse("")}
           |      "emailId": "${messageId.serialize}",
           |      "envelope": {
           |        "mailFrom": {"email": "${bobUsername.asString}"},
           |        "rcptTo": [{"email": "${andreUsername.asString}"}]
           |      }
           |    }""".stripMargin
    }.mkString(",\n")

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |$create
         |       }
         |  }, "c1"]]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString
  }

  private def andreInboxShouldOnlyContain(server: GuiceJamesServer, andreInboxId: MailboxId, subject: String): Unit = {
    val requestAndre =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/query", {
         |      "accountId": "$andreAccountId",
         |      "filter": {"inMailbox": "${andreInboxId.serialize}"}
         |    }, "c1"],
         |    ["Email/get", {
         |      "accountId": "$andreAccountId",
         |      "#ids": {"resultOf": "c1", "name": "Email/query", "path": "ids/*"},
         |      "properties": ["subject"]
         |    }, "c2"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`(
        baseRequestSpecBuilder(server)
          .setAuth(authScheme(UserCredential(andreUsername, ANDRE_PASSWORD)))
          .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
          .setBody(requestAndre)
          .build, new ResponseSpecBuilder().build)
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(response)
        .whenIgnoringPaths("methodResponses[1][1].list[*].id")
        .inPath("methodResponses[1][1].list")
        .isEqualTo(s"""[{"subject": "$subject"}]""")
    }
  }

  @Test
  def onSuccessUpdateEmailShouldTriggerAnImplicitEmailSetCall(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(bobUsername.asString)
      .setFrom(bobUsername.asString)
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${andreUsername.asString}"}]
         |           }
         |         }
         |       },
         |       "onSuccessUpdateEmail": {
         |         "#k1490": {
         |           "keywords": {"$$sent":true}
         |         }
         |       }
         |   }, "c1"],
         |   ["Email/get",
         |     {
         |       "accountId": "$bobAccountId",
         |       "ids": ["${messageId.serialize}"],
         |       "properties": ["keywords"]
         |     },
         |     "c2"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      // Ids are randomly generated, and not stored, let's ignore it
      .whenIgnoringPaths("methodResponses[0][1].created.k1490",
        "methodResponses[1][1].newState",
        "methodResponses[1][1].oldState",
        "methodResponses[2][1].state")
      .isEqualTo(s"""{
                   |    "sessionState": "${SESSION_STATE.value}",
                   |    "methodResponses": [
                   |        [
                   |            "EmailSubmission/set",
                   |            {
                   |                "accountId": "$bobAccountId",
                   |                "newState": "${INSTANCE.value}",
                   |                "created": {
                   |                    "k1490": "f0850507-bb63-4675-b14f-d560f8dca21f"
                   |                }
                   |            },
                   |            "c1"
                   |        ],
                   |        [
                   |            "Email/set",
                   |            {
                   |                "accountId": "$bobAccountId",
                   |                "newState": "${INSTANCE.value}",
                   |                "updated": {
                   |                    "${messageId.serialize}": null
                   |                }
                   |            },
                   |            "c1"
                   |        ],
                   |        [
                   |            "Email/get",
                   |            {
                   |                "accountId": "$bobAccountId",
                   |                "list": [
                   |                    {
                   |                        "keywords": {"$$sent": true},
                   |                        "id": "${messageId.serialize}"
                   |                    }
                   |                ],
                   |                "notFound": []
                   |            },
                   |            "c2"
                   |        ]
                   |    ]
                   |}""".stripMargin)
  }

  @Test
  def setShouldFailWhenOnSuccessUpdateEmailMissesTheCreationIdSharp(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(bobUsername.asString)
      .setFrom(bobUsername.asString)
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${andreUsername.asString}"}]
         |           }
         |         }
         |       },
         |       "onSuccessUpdateEmail": {
         |         "notStored": {
         |           "keywords": {"$$sent":true}
         |         }
         |       }
         |   }, "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
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
                    |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
                    |    "methodResponses": [
                    |        [
                    |            "error",
                    |            {
                    |                "type": "invalidArguments",
                    |                "description": "notStored cannot be retrieved as storage for EmailSubmission is not yet implemented"
                    |            },
                    |            "c1"
                    |        ]
                    |    ]
                    |}""".stripMargin)
  }

  @Test
  def setShouldFailWhenOnSuccessUpdateEmailDoesNotReferenceACreationWithinThisCall(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(bobUsername.asString)
      .setFrom(bobUsername.asString)
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${andreUsername.asString}"}]
         |           }
         |         }
         |       },
         |       "onSuccessUpdateEmail": {
         |         "#badReference": {
         |           "keywords": {"$$sent":true}
         |         }
         |       }
         |   }, "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
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
                    |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
                    |    "methodResponses": [
                    |        [
                    |            "error",
                    |            {
                    |                "type": "invalidArguments",
                    |                "description": "#badReference cannot be referenced in current method call"
                    |            },
                    |            "c1"
                    |        ]
                    |    ]
                    |}""".stripMargin)
  }

  @Test
  def onSuccessDestroyEmailShouldTriggerAnImplicitEmailSetCall(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(bobUsername.asString)
      .setFrom(bobUsername.asString)
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${andreUsername.asString}"}]
         |           }
         |         }
         |       },
         |       "onSuccessDestroyEmail": ["#k1490"]
         |   }, "c1"],
         |   ["Email/get",
         |     {
         |       "accountId": "$bobAccountId",
         |       "ids": ["${messageId.serialize}"],
         |       "properties": ["keywords"]
         |     },
         |     "c2"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      // Ids are randomly generated, and not stored, let's ignore it
      .whenIgnoringPaths("methodResponses[0][1].created.k1490",
        "methodResponses[1][1].newState",
        "methodResponses[1][1].oldState",
        "methodResponses[2][1].state")
      .isEqualTo(s"""{
                   |    "sessionState": "${SESSION_STATE.value}",
                   |    "methodResponses": [
                   |        [
                   |            "EmailSubmission/set",
                   |            {
                   |                "accountId": "$bobAccountId",
                   |                "newState": "${INSTANCE.value}",
                   |                "created": {
                   |                    "k1490": "f0850507-bb63-4675-b14f-d560f8dca21f"
                   |                }
                   |            },
                   |            "c1"
                   |        ],
                   |        [
                   |            "Email/set",
                   |            {
                   |                "accountId": "$bobAccountId",
                   |                "newState": "${INSTANCE.value}",
                   |                "destroyed": ["${messageId.serialize}"]
                   |            },
                   |            "c1"
                   |        ],
                   |        [
                   |            "Email/get",
                   |            {
                   |                "accountId": "$bobAccountId",
                   |                "list":[],
                   |                "notFound": ["${messageId.serialize}"]
                   |            },
                   |            "c2"
                   |        ]
                   |    ]
                   |}""".stripMargin)
  }

  @Test
  def setShouldFailWhenOnSuccessDestroyEmailMissesTheCreationIdSharp(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(bobUsername.asString)
      .setFrom(bobUsername.asString)
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${andreUsername.asString}"}]
         |           }
         |         }
         |       },
         |       "onSuccessDestroyEmail": ["notFound"]
         |   }, "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
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
                    |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
                    |    "methodResponses": [
                    |        [
                    |            "error",
                    |            {
                    |                "type": "invalidArguments",
                    |                "description": "notFound cannot be retrieved as storage for EmailSubmission is not yet implemented"
                    |            },
                    |            "c1"
                    |        ]
                    |    ]
                    |}""".stripMargin)
  }

  @Test
  def setShouldFailWhenOnSuccessDestroyEmailDoesNotReferenceACreationWithinThisCall(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(bobUsername.asString)
      .setFrom(bobUsername.asString)
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${andreUsername.asString}"}]
         |           }
         |         }
         |       },
         |       "onSuccessDestroyEmail": ["#notFound"]
         |   }, "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
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
                    |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
                    |    "methodResponses": [
                    |        [
                    |            "error",
                    |            {
                    |                "type": "invalidArguments",
                    |                "description": "#notFound cannot be referenced in current method call"
                    |            },
                    |            "c1"
                    |        ]
                    |    ]
                    |}""".stripMargin)
  }

  @Test
  def setShouldRejectOtherAccountIds(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(bobUsername.asString)
      .setFrom(bobUsername.asString)
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val otherAccountId = UUID.randomUUID().toString
    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$otherAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${andreUsername.asString}"}]
         |           }
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
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
      .isEqualTo("""[
                   |  "error",
                   |  {"type": "accountNotFound"},
                   |  "c1"
                   |]""".stripMargin)
  }

  @Test
  def setShouldRejectMissingCapability(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(bobUsername.asString)
      .setFrom(bobUsername.asString)
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${andreUsername.asString}"}]
         |           }
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
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
      .isEqualTo("""[
                   |  "error",
                   |  {
                   |    "type": "unknownMethod",
                   |    "description": "Missing capability(ies): urn:ietf:params:jmap:submission"
                   |  },
                   |  "c1"
                   |]""".stripMargin)
  }

  @Test
  def setShouldRejectMessageNotFound(): Unit = {
    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${randomMessageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${andreUsername.asString}"}]
         |           }
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
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
      .isEqualTo("""{
                   |  "k1490": {
                   |    "type": "invalidProperties",
                   |    "description": "The email to be sent cannot be found",
                   |    "properties": ["emailId"]
                   |  }
                   |}""".stripMargin)
  }

  @Test
  def setShouldRejectUnparsableEmailId(): Unit = {
    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "Munknown0000",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${andreUsername.asString}"}]
         |           }
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].notCreated.k1490.description")
      .inPath("methodResponses[0][1].notCreated")
      .isEqualTo("""{
                   |  "k1490": {
                   |    "type": "invalidProperties",
                   |    "properties": ["emailId"]
                   |  }
                   |}""".stripMargin)
  }

  @Test
  def implicitSetShouldNotBeAttemptedWhenNotSpecified(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(andreUsername.asString)
      .setFrom(bobUsername.asString)
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${andreUsername.asString}"}]
         |           }
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .inPath("methodResponses[1]")
      .isAbsent()
  }

  @Test
  def setShouldRejectExtraProperties(): Unit = {
    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${randomMessageId.serialize}",
         |           "extra": true,
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${andreUsername.asString}"}]
         |           }
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
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
      .isEqualTo("""{
                   |  "k1490": {
                   |    "type": "invalidArguments",
                   |    "description": "Some unknown properties were specified",
                   |    "properties": ["extra"]
                   |  }
                   |}""".stripMargin)
  }

  @Test
  def setShouldRejectMessageOfOtherUsers(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(bobUsername.asString)
      .setFrom(bobUsername.asString)
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val andreDraftsPath = MailboxPath.forUser(andreUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(andreUsername.asString(), andreDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${andreUsername.asString}"}]
         |           }
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
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
      .isEqualTo("""{
                   |  "k1490": {
                   |    "type": "invalidProperties",
                   |    "description": "The email to be sent cannot be found",
                   |    "properties": ["emailId"]
                   |  }
                   |}""".stripMargin)
  }

  @Test
  def setShouldAcceptDelegatedMessages(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(bobUsername.asString)
      .setFrom(bobUsername.asString)
      .setTo(bobUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val andreDraftsPath = MailboxPath.forUser(andreUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreDraftsPath)
    val bobInboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(bobUsername))
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreDraftsPath, bobUsername.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(andreUsername.asString(), andreDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${bobUsername.asString}"}]
         |           }
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
    .when
      .post
    .`then`
      .statusCode(SC_OK)

    val requestReadMail =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$bobAccountId",
         |      "filter": {"inMailbox": "${bobInboxId.serialize}"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(requestReadMail)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(response)
        .inPath("methodResponses[0][1].ids")
        .isArray
        .hasSize(1)
    }
  }

  @Test
  def setShouldRejectOtherUserUsageInSenderMimeField(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(andreUsername.asString)
      .setFrom(bobUsername.asString)
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${andreUsername.asString}"}]
         |           }
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
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
      .isEqualTo(s"""{
                   |  "k1490": {
                   |    "type": "forbiddenFrom",
                   |    "properties": ["sender"],
                   |    "description": "Attempt to send a mail whose MimeMessage From and Sender fields not allowed for connected user: List(${andreUsername.asString})"
                   |  }
                   |}""".stripMargin)
  }

  @Test
  def setShouldRejectOtherUserUsageInFromMimeField(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(bobUsername.asString)
      .setFrom(bobUsername.asString, andreUsername.asString())
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${andreUsername.asString}"}]
         |           }
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
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
      .isEqualTo(s"""{
                   |  "k1490": {
                   |    "type": "forbiddenFrom",
                   |    "properties": ["from"],
                   |    "description": "Attempt to send a mail whose MimeMessage From and Sender fields not allowed for connected user: List(${andreUsername.asString})"
                   |  }
                   |}""".stripMargin)
  }

  @Test
  def setShouldRejectWhenMissingFromMimeField(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
        .build(message))
      .getMessageId

    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${andreUsername.asString}"}]
         |           }
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
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
      .isEqualTo("""{
                   |  "k1490": {
                   |    "type": "invalidEmail",
                   |    "properties": ["from"],
                   |    "description": "Attempt to send a mail whose MimeMessage From is missing"
                   |  }
                   |}""".stripMargin)
  }

  @Test
  def setShouldRejectOtherUserUsageInFromEnvelopeField(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(bobUsername.asString)
      .setFrom(bobUsername.asString)
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${andreUsername.asString}"},
         |             "rcptTo": [{"email": "${andreUsername.asString}"}]
         |           }
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
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
      .isEqualTo(s"""{
                   |  "k1490": {
                   |    "type": "forbiddenMailFrom",
                   |    "description": "Attempt to send a mail whose envelope From not allowed for connected user: ${andreUsername.asString}",
                   |    "properties":["envelope.mailFrom"]
                   |  }
                   |}""".stripMargin)
  }

  @Test
  def setShouldRejectMissingFromWithImplicitEnvelope(server: GuiceJamesServer): Unit = {
    val message = Message.Builder.of.setBody("testmail", StandardCharsets.UTF_8).setTo(andreUsername.asString).setSubject("test").build
    assertSubmissionRejected(server, message, None, "invalidEmail", Seq("from"))
  }

  @Test
  def setShouldRejectMissingFromEvenWhenSenderIsPresent(server: GuiceJamesServer): Unit = {
    val message = Message.Builder.of.setBody("testmail", StandardCharsets.UTF_8).setSender(bobUsername.asString).setTo(andreUsername.asString).build
    assertSubmissionRejected(server, message, Some(validSenderEnvelope), "invalidEmail", Seq("from"))
  }

  @Test
  def setShouldRejectForbiddenFromWithImplicitEnvelope(server: GuiceJamesServer): Unit = {
    val message = Message.Builder.of.setBody("testmail", StandardCharsets.UTF_8).setFrom("ghost@domain.tld").setTo(andreUsername.asString).build
    assertSubmissionRejected(server, message, None, "forbiddenFrom", Seq("from"))
  }

  @Test
  def setShouldReportBothForbiddenHeaders(server: GuiceJamesServer): Unit = {
    val message = Message.Builder.of.setBody("testmail", StandardCharsets.UTF_8).setFrom(andreUsername.asString).setSender("ghost@domain.tld")
      .setTo(andreUsername.asString).build
    assertSubmissionRejected(server, message, Some(validSenderEnvelope), "forbiddenFrom", Seq("from", "sender"))
  }

  @Test
  def setShouldReportBothHeadersWhenForbiddenAddressIsRepeated(server: GuiceJamesServer): Unit = {
    val message = Message.Builder.of.setBody("testmail", StandardCharsets.UTF_8).setFrom(andreUsername.asString).setSender(andreUsername.asString)
      .setTo(andreUsername.asString).build
    assertSubmissionRejected(server, message, Some(validSenderEnvelope), "forbiddenFrom", Seq("from", "sender"))
  }

  @Test
  def setShouldPreferForbiddenHeaderOverForbiddenEnvelope(server: GuiceJamesServer): Unit = {
    val message = Message.Builder.of.setBody("testmail", StandardCharsets.UTF_8).setFrom("ghost@domain.tld").setTo(andreUsername.asString).build
    val envelope = validSenderEnvelope ++ Json.obj("mailFrom" -> Json.obj("email" -> andreUsername.asString))
    assertSubmissionRejected(server, message, Some(envelope), "forbiddenFrom", Seq("from"))
  }

  private def validSenderEnvelope: JsObject = Json.obj(
    "mailFrom" -> Json.obj("email" -> bobUsername.asString),
    "rcptTo" -> Json.arr(Json.obj("email" -> andreUsername.asString)))

  private def assertSubmissionRejected(server: GuiceJamesServer, message: Message, envelope: Option[JsObject],
                                       errorType: String, properties: Seq[String]): Unit = {
    val drafts = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(drafts)
    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(bobUsername.asString, drafts, AppendCommand.builder().build(message)).getMessageId
    val creation = Json.obj("emailId" -> messageId.serialize) ++ envelope
      .map(value => Json.obj("envelope" -> value)).getOrElse(Json.obj())
    val request = Json.obj(
      "using" -> Json.arr("urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"),
      "methodCalls" -> Json.arr(Json.arr("EmailSubmission/set", Json.obj(
        "accountId" -> bobAccountId, "create" -> Json.obj("rejected" -> creation)), "c1")))

    val response = `given`.header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request.toString).when.post.`then`.statusCode(SC_OK).contentType(JSON).extract.asString
    assertThatJson(response).inPath("methodResponses[0][1].created").isAbsent()
    assertThatJson(response).inPath("methodResponses[0][1].notCreated.rejected.type").isEqualTo(errorType)
    assertThatJson(response).when(net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER)
      .inPath("methodResponses[0][1].notCreated.rejected.properties")
      .isEqualTo(Json.toJson(properties).toString)
  }

  @Test
  def setShouldRejectNoRecipients(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(bobUsername.asString)
      .setFrom(bobUsername.asString)
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": []
         |           }
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
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
      .isEqualTo("""{
                   |  "k1490": {
                   |    "type": "noRecipients",
                   |    "description": "Attempt to send a mail with no recipients"
                   |  }
                   |}""".stripMargin)
  }

  @Test
  def recipientShouldBeCaseInsensitive(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(bobUsername.asString)
      .setFrom(bobUsername.asString)
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val andreInboxPath = MailboxPath.inbox(andreUsername)
    val andreInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreInboxPath)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
           |  "methodCalls": [
           |     ["EmailSubmission/set", {
           |       "accountId": "$bobAccountId",
           |       "create": {
           |         "k1490": {
           |           "identityId": "${identityIdOf(bobUsername)}",
           |           "emailId": "${messageId.serialize}",
           |           "envelope": {
           |             "mailFrom": {"email": "${bobUsername.asString}"},
           |             "rcptTo": [{"email": "${andreUsername.asString.toUpperCase}"}]
           |           }
           |         }
           |    }
           |  }, "c1"]]
           |}""".stripMargin)
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .post
    .`then`
      .statusCode(SC_OK)

    val queryAndreInboxRequest =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$andreAccountId",
         |      "filter": {"inMailbox": "${andreInboxId.serialize}"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`(
        baseRequestSpecBuilder(server)
          .setAuth(authScheme(UserCredential(andreUsername, ANDRE_PASSWORD)))
          .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
          .setBody(queryAndreInboxRequest)
          .build, new ResponseSpecBuilder().build)
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(response)
        .inPath("methodResponses[0][1].ids")
        .isArray
        .hasSize(1)
    }
  }

  @Test
  def onSuccessUpdateEmailShouldNotInvokedWhenEmailSubmissionNotCreated(): Unit = {
    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${randomMessageId.serialize}",
         |           "extra": true,
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${andreUsername.asString}"}]
         |           }
         |         }
         |    },
         |    "onSuccessUpdateEmail": {
         |         "#k1490": {
         |           "keywords": {"$$sent":true}
         |         }
         |       }
         |  }, "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
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
                   |  "sessionState": "${SESSION_STATE.value}",
                   |  "methodResponses": [
                   |    [
                   |      "EmailSubmission/set",
                   |      {
                   |        "accountId": "$bobAccountId",
                   |        "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
                   |        "notCreated": {
                   |          "k1490": {
                   |            "type": "invalidArguments",
                   |            "description": "Some unknown properties were specified",
                   |            "properties": [
                   |              "extra"
                   |            ]
                   |          }
                   |        }
                   |      },
                   |      "c1"
                   |    ]
                   |  ]
                   |}""".stripMargin)
  }

  @Test
  def onSuccessDestroyEmailShouldNotInvokedWhenEmailSubmissionNotCreated(): Unit = {
    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${randomMessageId.serialize}",
         |           "extra": true,
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${andreUsername.asString}"}]
         |           }
         |         }
         |    },
         |    "onSuccessDestroyEmail": ["#k1490"]
         |  }, "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
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
                    |  "sessionState": "${SESSION_STATE.value}",
                    |  "methodResponses": [
                    |    [
                    |      "EmailSubmission/set",
                    |      {
                    |        "accountId": "$bobAccountId",
                    |        "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
                    |        "notCreated": {
                    |          "k1490": {
                    |            "type": "invalidArguments",
                    |            "description": "Some unknown properties were specified",
                    |            "properties": [
                    |              "extra"
                    |            ]
                    |          }
                    |        }
                    |      },
                    |      "c1"
                    |    ]
                    |  ]
                    |}""".stripMargin)
  }

  @Test
  def emailSubmissionSetShouldSetCorrectlyHasAttachmentPropertyOnMessageInSentMailbox(server: GuiceJamesServer): Unit = {
    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    val draftId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val bobInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(bobUsername))
    val bobSentId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(bobUsername, DefaultMailboxes.SENT))

    val payload = "123456789\r\n".getBytes(StandardCharsets.UTF_8)

    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType("text/plain")
      .body(payload)
    .when
      .post(s"/upload/$bobAccountId")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val blobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value

    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "$bobAccountId",
         |      "create": {
         |        "e1526":{
         |          "mailboxIds": {"${draftId.serialize}": true},
         |          "to": [{"email": "${bobUsername.asString}"}],
         |          "from": [{"email": "${bobUsername.asString}"}],
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
         |     }, "c0"],
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "#e1526",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${bobUsername.asString}"}]
         |           }
         |         }
         |      },
         |      "onSuccessUpdateEmail": {
         |         "#k1490": {
         |           "mailboxIds/${bobSentId.serialize}": true,
         |           "mailboxIds/${draftId.serialize}": null,
         |           "keywords/$$seen": true,
         |           "keywords/$$draft": null
         |         }
         |       }
         |    }, "c1"]]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
    .when
      .post
    .`then`
      .statusCode(SC_OK)

    val requestQueryMailBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$bobAccountId",
         |      "filter": {"inMailbox": "${bobSentId.serialize}"}
         |    },
         |    "c0"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val responseBob = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(requestQueryMailBob)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(responseBob)
        .inPath("methodResponses[0][1].ids")
        .isArray
        .hasSize(1)
    }

    val requestReadMailBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$bobAccountId",
         |      "filter": {"inMailbox": "${bobSentId.serialize}"}
         |    },
         |    "c0"],
         |    [
         |      "Email/get",
         |      {
         |        "accountId": "$bobAccountId",
         |        "#ids": {
         |          "resultOf": "c0",
         |          "name": "Email/query",
         |          "path": "/ids/*"
         |        },
         |        "properties": [
         |          "id",
         |          "hasAttachment"
         |        ]
         |      }, "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestReadMailBob)
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
           |  "hasAttachment": true
           |}]""".stripMargin)
  }

  @Test
  def setShouldAcceptUnknownLocalRecipientWhenRecipientValidationIsDisabled(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(bobUsername.asString)
      .setFrom(bobUsername.asString)
      .setTo(s"unknown@${DOMAIN.asString}")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val requestBob =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "unknown@${DOMAIN.asString}"}]
         |           }
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestBob)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .inPath("methodResponses[0][1].created")
      .isObject
      .containsKey("k1490")
  }

  private def appendDraft(server: GuiceJamesServer): MessageId = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(bobUsername.asString)
      .setFrom(bobUsername.asString)
      .setTo(andreUsername.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(bobUsername, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bobUsername.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId
  }

  private def post(request: String): String =
    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

  private def creationWithParameters(messageId: MessageId, parameters: String): String =
    s"""{
       |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
       |  "methodCalls": [
       |     ["EmailSubmission/set", {
       |       "accountId": "$bobAccountId",
       |       "create": {
       |         "k1490": {
       |           "emailId": "${messageId.serialize}",
       |           "identityId": "${identityIdOf(bobUsername)}",
       |           "envelope": {
       |             "mailFrom": {"email": "${bobUsername.asString}", "parameters": $parameters},
       |             "rcptTo": [{"email": "${andreUsername.asString}"}]
       |           }
       |         }
       |    }
       |  }, "c1"]]
       |}""".stripMargin

  @Test
  def holdForShouldBeRejectedWhenDelayedSendsAreNotSupported(server: GuiceJamesServer): Unit = {
    val response = post(creationWithParameters(appendDraft(server), """{"holdFor": "20"}"""))

    assertThatJson(response)
      .inPath("methodResponses[0][1].notCreated.k1490")
      .isEqualTo("""{
                   |  "type": "invalidArguments",
                   |  "description": "Delayed sends are not supported",
                   |  "properties": ["envelope.mailFrom.parameters"]
                   |}""".stripMargin)
  }

  @Test
  def holdUntilShouldBeRejectedWhenDelayedSendsAreNotSupported(server: GuiceJamesServer): Unit = {
    val inOneHour = Instant.now().plus(Duration.ofHours(1)).truncatedTo(ChronoUnit.SECONDS)
    val response = post(creationWithParameters(appendDraft(server), s"""{"holdUntil": "$inOneHour"}"""))

    assertThatJson(response)
      .inPath("methodResponses[0][1].notCreated.k1490")
      .isEqualTo("""{
                   |  "type": "invalidArguments",
                   |  "description": "Delayed sends are not supported",
                   |  "properties": ["envelope.mailFrom.parameters"]
                   |}""".stripMargin)
  }

  @Test
  def zeroHoldForShouldBeAcceptedWhenDelayedSendsAreNotSupported(server: GuiceJamesServer): Unit = {
    val response = post(creationWithParameters(appendDraft(server), """{"holdFor": "0"}"""))

    assertThatJson(response)
      .inPath("methodResponses[0][1].created")
      .isObject
      .containsKey("k1490")
  }

  @Test
  def cancelingAnUnknownSubmissionShouldBeReportedAsNotUpdated(): Unit = {
    val response = post(
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "update": {
         |         "c41d3b57-7d2b-4de1-b0b9-0e4e54fa4b4f": {"undoStatus": "canceled"}
         |       }
         |  }, "c1"]]
         |}""".stripMargin)

    assertThatJson(response)
      .inPath("methodResponses[0][1]")
      .isEqualTo(s"""{
                    |  "accountId": "$bobAccountId",
                    |  "newState": "${INSTANCE.value}",
                    |  "notUpdated": {
                    |    "c41d3b57-7d2b-4de1-b0b9-0e4e54fa4b4f": {
                    |      "type": "notFound",
                    |      "description": "EmailSubmission c41d3b57-7d2b-4de1-b0b9-0e4e54fa4b4f was not found"
                    |    }
                    |  }
                    |}""".stripMargin)
  }

  @Test
  def cancelingASubmissionCreatedInTheSameCallShouldBeReportedAsCannotUnsend(server: GuiceJamesServer): Unit = {
    val messageId = appendDraft(server)
    val response = post(
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "emailId": "${messageId.serialize}",
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${andreUsername.asString}"}]
         |           }
         |         }
         |       },
         |       "update": {
         |         "#k1490": {"undoStatus": "canceled"}
         |       }
         |  }, "c1"]]
         |}""".stripMargin)

    val submissionId = (Json.parse(response) \ "methodResponses" \ 0 \ 1 \ "created" \ "k1490" \ "id").as[String]

    assertThatJson(response)
      .inPath("methodResponses[0][1].notUpdated")
      .isEqualTo(s"""{
                    |  "$submissionId": {
                    |    "type": "cannotUnsend",
                    |    "description": "EmailSubmission $submissionId was already sent and can no longer be canceled"
                    |  }
                    |}""".stripMargin)
  }

  @Test
  def destroyShouldBeReportedAsNotDestroyed(): Unit = {
    val response = post(
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "destroy": ["c41d3b57-7d2b-4de1-b0b9-0e4e54fa4b4f"]
         |  }, "c1"]]
         |}""".stripMargin)

    assertThatJson(response)
      .inPath("methodResponses[0][1]")
      .isEqualTo(s"""{
                    |  "accountId": "$bobAccountId",
                    |  "newState": "${INSTANCE.value}",
                    |  "notDestroyed": {
                    |    "c41d3b57-7d2b-4de1-b0b9-0e4e54fa4b4f": {
                    |      "type": "notFound",
                    |      "description": "EmailSubmission c41d3b57-7d2b-4de1-b0b9-0e4e54fa4b4f was not found"
                    |    }
                    |  }
                    |}""".stripMargin)
  }

  @Test
  def destroyingASubmissionCreatedInTheSameCallShouldBeForbidden(server: GuiceJamesServer): Unit = {
    val messageId = appendDraft(server)
    val response = post(
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$bobAccountId",
         |       "create": {
         |         "k1490": {
         |           "emailId": "${messageId.serialize}",
         |           "identityId": "${identityIdOf(bobUsername)}",
         |           "envelope": {
         |             "mailFrom": {"email": "${bobUsername.asString}"},
         |             "rcptTo": [{"email": "${andreUsername.asString}"}]
         |           }
         |         }
         |       },
         |       "destroy": ["#k1490"]
         |  }, "c1"]]
         |}""".stripMargin)

    val submissionId = (Json.parse(response) \ "methodResponses" \ 0 \ 1 \ "created" \ "k1490" \ "id").as[String]

    assertThatJson(response)
      .inPath("methodResponses[0][1].notDestroyed")
      .isEqualTo(s"""{
                    |  "$submissionId": {
                    |    "type": "forbidden",
                    |    "description": "EmailSubmission $submissionId cannot be destroyed as EmailSubmissions are not stored"
                    |  }
                    |}""".stripMargin)
  }
}
