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
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import com.google.common.hash.Hashing
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.core.quota.QuotaCountLimit
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UuidState.INSTANCE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.mail.{CountResourceType, QuotaIdFactory}
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE_PASSWORD, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.MailboxACL.Right.Read
import org.apache.james.mailbox.model.{MailboxACL, MailboxPath}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.{ACLProbeImpl, MailboxProbeImpl, QuotaProbesImpl}
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.junit.jupiter.api.{BeforeEach, Test}


object QuotaChangesMethodContract {
  case class TestContext(bobUsername: Username, bobAccountId: String, andreUsername: Username)

  val currentContext: AtomicReference[TestContext] = new AtomicReference[TestContext]()
}

trait QuotaChangesMethodContract {
  import QuotaChangesMethodContract.{TestContext, currentContext}

  def bobUsername: Username = currentContext.get().bobUsername
  def bobAccountId: String = currentContext.get().bobAccountId
  def andreUsername: Username = currentContext.get().andreUsername

  private def accountId(username: Username): String =
    Hashing.sha256().hashString(username.asString(), StandardCharsets.UTF_8).toString

  private lazy val awaitAtMostTenSeconds = Awaitility.`with`
    .await
    .pollInterval(Duration.ofMillis(100))
    .atMost(10, TimeUnit.SECONDS)

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    val uniqueSuffix = UUID.randomUUID().toString.replace("-", "").take(8)
    val bob = Username.fromLocalPartWithDomain(s"bob$uniqueSuffix", DOMAIN)
    val andre = Username.fromLocalPartWithDomain(s"andre$uniqueSuffix", DOMAIN)
    currentContext.set(TestContext(bob, accountId(bob), andre))

    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(bob.asString, BOB_PASSWORD)
      .addUser(andre.asString, ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(bob, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build
  }

  @Test
  def quotaChangeShouldReturnCorrectResponse(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
    quotaProbe.setMaxMessageCount(bobQuotaRoot, QuotaCountLimit.count(100L))

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota"],
           |  "methodCalls": [[
           |    "Quota/changes",
           |    {
           |      "accountId": "$bobAccountId",
           |      "sinceState": "${INSTANCE.value}"
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
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].updated")
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Quota/changes",
         |            {
         |                "accountId": "$bobAccountId",
         |                "oldState": "${INSTANCE.value}",
                |                "newState": "",
         |                "hasMoreChanges": false,
         |                "updatedProperties": null,
         |                "created": [],
                |                "updated": [],
         |                "destroyed": []
         |            },
         |            "c1"
         |        ]
                |    ]
         |}""".stripMargin)

    val responseObject = play.api.libs.json.Json.parse(response)
    assertThat((responseObject \ "methodResponses" \ 0 \ 1 \ "newState").as[String]).isNotEqualTo(INSTANCE.value)
    assertThat((responseObject \ "methodResponses" \ 0 \ 1 \ "updated").as[Seq[String]])
      .isEqualTo(Seq(QuotaIdFactory.from(bobQuotaRoot, CountResourceType).value))
  }

  @Test
  def quotaChangeShouldReturnSameResponseWhenSameRequest(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
    quotaProbe.setMaxMessageCount(bobQuotaRoot, QuotaCountLimit.count(100L))

    val response1 = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota"],
           |  "methodCalls": [[
           |    "Quota/changes",
           |    {
           |      "accountId": "$bobAccountId",
           |      "sinceState": "${INSTANCE.value}"
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

    val response2 = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota"],
           |  "methodCalls": [[
           |    "Quota/changes",
           |    {
           |      "accountId": "$bobAccountId",
           |      "sinceState": "${INSTANCE.value}"
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

    assertThatJson(response1).isEqualTo(response2)
  }

  @Test
  def hasMoreChangesShouldBeFalseWhenNoQuotaChanges(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
    quotaProbe.setMaxMessageCount(bobQuotaRoot, QuotaCountLimit.count(100L))

    val newState: String = getLastState()

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota"],
           |  "methodCalls": [[
           |    "Quota/changes",
           |    {
           |      "accountId": "$bobAccountId",
           |      "sinceState": "${newState}"
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Quota/changes",
         |            {
         |                "accountId": "$bobAccountId",
         |                "oldState": "${newState}",
         |                "newState": "${newState}",
         |                "hasMoreChanges": false,
         |                "updatedProperties": null,
         |                "created": [],
         |                "updated": [],
         |                "destroyed": []
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }


  @Test
  def stateShouldBeChangedWhenQuotaIsUpdated(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
    quotaProbe.setMaxMessageCount(bobQuotaRoot, QuotaCountLimit.count(100L))
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(bobUsername))

    val newState: String = getLastState()

    // update quota usage
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(bobUsername.asString(), MailboxPath.inbox(bobUsername), AppendCommand.from(Message.Builder
        .of
        .setSubject("test")
        .setBody("testmail", StandardCharsets.UTF_8)
        .build))
      .getMessageId.serialize()

    awaitAtMostTenSeconds.untilAsserted(() => assertThat(getLastState())
      .isNotEqualTo(newState))
  }

  private def getLastState(): String = {
    `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota"],
           |  "methodCalls": [[
           |    "Quota/changes",
           |    {
           |      "accountId": "$bobAccountId",
           |      "sinceState": "${INSTANCE.value}"
           |    },
           |    "c1"]]
           |}""".stripMargin)
      .when
      .post
      .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .jsonPath()
      .get("methodResponses[0][1].newState")
  }

  @Test
  def quotaChangesShouldFailWhenWrongAccountId(): Unit = {
    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota"],
           |  "methodCalls": [[
           |    "Quota/changes",
           |    {
           |      "accountId": "unknownAccountId",
           |      "sinceState": "${INSTANCE.value}"
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
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["error", {
         |      "type": "accountNotFound"
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def quotaChangesShouldFailWhenMissSinceState(): Unit = {
    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota"],
           |  "methodCalls": [[
           |    "Quota/changes",
           |    {
           |      "accountId": "$bobAccountId"
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "error",
         |            {
         |                "type": "invalidArguments",
         |                "description": "Missing '/sinceState' property"
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def quotaChangesShouldFailWhenSinceStateIsInvalid(): Unit = {
    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota"],
           |  "methodCalls": [[
           |    "Quota/changes",
           |    {
           |      "accountId": "$bobAccountId",
           |      "sinceState": "invaLid@"
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "error",
         |            {
         |                "type": "invalidArguments",
         |                "description": "'/sinceState' property is not valid: error.expected.uuid"
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def quotaChangeShouldFailWhenOmittingOneCapability(): Unit = {
    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core"],
           |  "methodCalls": [[
           |    "Quota/changes",
           |    {
           |      "accountId": "unknownAccountId",
           |      "sinceState": "${INSTANCE.value}"
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
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "error",
         |    {
         |      "type": "unknownMethod",
         |      "description":"Missing capability(ies): urn:ietf:params:jmap:quota"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def quotaChangesShouldFailWhenOmittingAllCapability(): Unit = {
    val response = `given`
      .body(
        s"""{
           |  "using": [],
           |  "methodCalls": [[
           |    "Quota/changes",
           |    {
           |      "accountId": "unknownAccountId",
           |      "sinceState": "${INSTANCE.value}"
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
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "error",
         |    {
         |      "type": "unknownMethod",
         |      "description":"Missing capability(ies): urn:ietf:params:jmap:quota, urn:ietf:params:jmap:core"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def quotaChangesShouldReturnDifferenceStateWhenProvideDelegatedMailbox(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
    quotaProbe.setMaxMessageCount(bobQuotaRoot, QuotaCountLimit.count(100L))

    // setup delegated Mailbox
    val andreMailbox = MailboxPath.forUser(andreUsername, "mailbox")
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreMailbox)
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailbox, bobUsername.asString, new MailboxACL.Rfc4314Rights(Read))
    quotaProbe.setMaxMessageCount(quotaProbe.getQuotaRoot(andreMailbox), QuotaCountLimit.count(88L))

    val stateWithOutShareCapability: String = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota"],
           |  "methodCalls": [[
           |    "Quota/changes",
           |    {
           |      "accountId": "$bobAccountId",
           |      "sinceState": "${INSTANCE.value}"
           |    },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .jsonPath()
      .get("methodResponses[0][1].newState")

    val stateWithShareCapability: String = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota",
           |    "urn:apache:james:params:jmap:mail:shares"],
           |  "methodCalls": [[
           |    "Quota/changes",
           |    {
           |      "accountId": "$bobAccountId",
           |      "sinceState": "${INSTANCE.value}"
           |    },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .jsonPath()
      .get("methodResponses[0][1].newState")

    assertThat(stateWithShareCapability).isNotEqualTo(stateWithOutShareCapability)
  }
}
