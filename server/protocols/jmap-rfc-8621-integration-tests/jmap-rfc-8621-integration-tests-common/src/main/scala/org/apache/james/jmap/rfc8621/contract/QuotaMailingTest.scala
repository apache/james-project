/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
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
import java.util.concurrent.TimeUnit

import com.google.common.base.Strings
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured
import io.restassured.RestAssured.`given`
import io.restassured.builder.ResponseSpecBuilder
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.quota.QuotaSizeLimit
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ANDRE, ANDRE_ACCOUNT_ID, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.QuotaMailingTest.andreDraftsPath
import org.apache.james.junit.categories.BasicFeature
import org.apache.james.mailbox.DefaultMailboxes
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.{MailboxConstants, MailboxPath, MessageId}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.{MailboxProbeImpl, QuotaProbesImpl}
import org.apache.james.utils.DataProbeImpl
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.hamcrest.Matchers.{containsString, hasItem}
import org.junit.experimental.categories.Category
import org.junit.jupiter.api.{BeforeEach, Test}

import scala.jdk.CollectionConverters._

object QuotaMailingTest {
  private val andreDraftsPath = MailboxPath.forUser(ANDRE, DefaultMailboxes.DRAFTS)
}

trait QuotaMailingTest {
  private lazy val slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS
  private lazy val calmlyAwait = Awaitility.`with`
    .pollInterval(slowPacedPollInterval)
    .and.`with`.pollDelay(slowPacedPollInterval)
    .await
  private lazy val awaitAtMostTwoMinutes = calmlyAwait.atMost(2, TimeUnit.MINUTES)

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)
      .addUser(ANDRE.asString, ANDRE_PASSWORD)

    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB.asString, DefaultMailboxes.INBOX)
    mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ANDRE.asString, DefaultMailboxes.INBOX)

    mailboxProbe.createMailbox(andreDraftsPath)

    RestAssured.requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  @Category(Array(classOf[BasicFeature]))
  @Test
  def shouldSendANoticeWhenThresholdExceeded(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    quotaProbe.setMaxStorage(quotaProbe.getQuotaRoot(MailboxPath.inbox(BOB)), QuotaSizeLimit.size(100 * 1000))

    andreSendMailToBob(server)

    // Bob receives a mail big enough to trigger a configured threshold
    awaitAtMostTwoMinutes.until(() => listMessageIds().size == 2)

    val ids: List[String] = listMessageIds().asScala.toList
    val idString: String = concatMessageIds(ids)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |     "Email/get",
         |     {
         |       "accountId": "$ACCOUNT_ID",
         |       "ids": [$idString]
         |     },
         |     "c1"]]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post()
    .`then`
      .statusCode(SC_OK)
      .log.ifValidationFails()
      .body("methodResponses[0][1].list.subject",
        hasItem("Warning: Your email usage just exceeded a configured threshold"))
  }

  @Test
  def configurationShouldBeWellLoaded(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    quotaProbe.setMaxStorage(quotaProbe.getQuotaRoot(MailboxPath.inbox(BOB)), QuotaSizeLimit.size(100 * 1000))

    andreSendMailToBob(server)

    // Bob receives a mail big enough to trigger a 10% configured threshold
    awaitAtMostTwoMinutes.until(() => listMessageIds().size == 2)

    andreSendMailToBob(server)

    // Bob receives a mail big enough to trigger a 20% configured threshold
    awaitAtMostTwoMinutes.until(() => listMessageIds().size == 4)
    val ids: List[String] = listMessageIds().asScala.toList
    val idString: String = concatMessageIds(ids)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |     "Email/get",
         |     {
         |       "accountId": "$ACCOUNT_ID",
         |       "ids": [$idString]
         |     },
         |     "c1"]]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post()
    .`then`
      .statusCode(SC_OK)
      .log.ifValidationFails()
      .body("methodResponses[0][1].list.preview",
        hasItem(containsString("You currently occupy more than 10 % of the total size allocated to you")))
      .body("methodResponses[0][1].list.preview",
        hasItem(containsString("You currently occupy more than 20 % of the total size allocated to you")))
  }

  private def andreSendMailToBob(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(ANDRE.asString)
      .setFrom("ANDRE <" + ANDRE.asString + ">")
      .setTo(BOB.asString)
      .setBody(Strings.repeat("123456789\n", 12 * 100), StandardCharsets.UTF_8)
      .build

    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString(), andreDraftsPath, AppendCommand.builder().build(message))
      .getMessageId

    val requestAndre =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$ANDRE_ACCOUNT_ID",
         |       "create": {
         |         "k1490": {
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${ANDRE.asString}"},
         |             "rcptTo": [{"email": "${BOB.asString}"}]
         |           }
         |         }
         |    }
         |  }, "c1"]]
         |}""".stripMargin

    `given`(
      baseRequestSpecBuilder(server)
        .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
        .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .setBody(requestAndre)
        .build, new ResponseSpecBuilder().build)
      .post
    .`then`
      .statusCode(SC_OK)
  }

  private def listMessageIds(): util.ArrayList[String] = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$ACCOUNT_ID"
         |    },
         |    "c1"]]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post()
    .`then`
      .statusCode(SC_OK)
      .extract
      .body
      .path("methodResponses[0][1].ids")
  }

  private def concatMessageIds(ids: List[String]): String =
    ids.map(id => "\"" + id + "\"")
      .mkString(",")
}
