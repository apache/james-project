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
import org.apache.james.jmap.core.JmapRfc8621Configuration
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.DefaultMailboxes
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Test}

object EmailSubmissionSetValidateRcptContract {
  val configuration: JmapRfc8621Configuration = JmapRfc8621Configuration(
    urlPrefixString = "http://127.0.0.1",
    websocketPrefixString = "ws://127.0.0.1",
    validateRecipientsOnSend = true)
}

/**
 * `EmailSubmission/set` behaviour when `send.validate.rcpt` is turned on.
 */
trait EmailSubmissionSetValidateRcptContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)
      .addUser(ANDRE.asString, ANDRE_PASSWORD)

    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS))

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  @Test
  def setShouldAcceptRecipientHavingALocalMailbox(server: GuiceJamesServer): Unit =
    assertThatJson(submit(server, ANDRE.asString))
      .inPath("methodResponses[0][1].created")
      .isObject
      .containsKey("k1490")

  @Test
  def setShouldAcceptRecipientOfARemoteDomain(server: GuiceJamesServer): Unit =
    assertThatJson(submit(server, "someone@remote.tld"))
      .inPath("methodResponses[0][1].created")
      .isObject
      .containsKey("k1490")

  @Test
  def setShouldAcceptRecipientResolvedByRecipientRewriteTable(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .addAddressMapping("alias", DOMAIN.asString, ANDRE.asString)

    assertThatJson(submit(server, s"alias@${DOMAIN.asString}"))
      .inPath("methodResponses[0][1].created")
      .isObject
      .containsKey("k1490")
  }

  @Test
  def setShouldRejectRecipientOfALocalDomainWithoutMailboxNorMapping(server: GuiceJamesServer): Unit =
    assertThatJson(submit(server, s"unknown@${DOMAIN.asString}"))
      .inPath("methodResponses[0][1].notCreated")
      .isEqualTo(s"""{
                    |  "k1490": {
                    |    "type": "invalidRecipients",
                    |    "description": "Invalid recipients: unknown@${DOMAIN.asString}",
                    |    "properties": ["envelope.rcptTo"]
                    |  }
                    |}""".stripMargin)

  @Test
  def setShouldRejectTheWholeSubmissionWhenASingleRecipientIsInvalid(server: GuiceJamesServer): Unit =
    assertThatJson(submit(server, ANDRE.asString, s"unknown@${DOMAIN.asString}"))
      .inPath("methodResponses[0][1].notCreated")
      .isEqualTo(s"""{
                    |  "k1490": {
                    |    "type": "invalidRecipients",
                    |    "description": "Invalid recipients: unknown@${DOMAIN.asString}",
                    |    "properties": ["envelope.rcptTo"]
                    |  }
                    |}""".stripMargin)

  private def submit(server: GuiceJamesServer, recipients: String*): String = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(BOB.asString)
      .setFrom(BOB.asString)
      .setTo(recipients: _*)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS), AppendCommand.builder().build(message))
      .getMessageId

    val rcptTo = recipients.map(recipient => s"""{"email": "$recipient"}""").mkString(", ")
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "$ACCOUNT_ID",
         |       "create": {
         |         "k1490": {
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${BOB.asString}"},
         |             "rcptTo": [$rcptTo]
         |           }
         |         }
         |    }
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
}
