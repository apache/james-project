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
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import com.google.common.hash.Hashing
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.mailbox.DefaultMailboxes
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.{MailboxPath, MessageId}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Tag, Test}

object EmailSubmissionSetRejectDelayContract {
  case class TestContext(bob: Username, bobAccountId: String)
  val currentContext: AtomicReference[TestContext] = new AtomicReference[TestContext]()
}

/**
 * For servers whose mail queue drops delays (RabbitMQ): delayed sends need to be rejected
 * even when delay.sends.enabled is set, as the mail would otherwise be sent right away.
 */
trait EmailSubmissionSetRejectDelayContract {
  def bob: Username = EmailSubmissionSetRejectDelayContract.currentContext.get().bob
  def bobAccountId: String = EmailSubmissionSetRejectDelayContract.currentContext.get().bobAccountId

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    val bob = Username.fromLocalPartWithDomain(s"bob${UUID.randomUUID().toString.replace("-", "").take(8)}", DOMAIN)
    EmailSubmissionSetRejectDelayContract.currentContext.set(EmailSubmissionSetRejectDelayContract.TestContext(
      bob = bob,
      bobAccountId = Hashing.sha256().hashString(bob.asString(), StandardCharsets.UTF_8).toString))

    val dataProbe = server.getProbe(classOf[DataProbeImpl])
    if (!dataProbe.containsDomain(DOMAIN.asString)) {
      dataProbe.addDomain(DOMAIN.asString)
    }
    dataProbe.addUser(bob.asString, BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(bob, BOB_PASSWORD)))
      .build
  }

  private def appendDraft(server: GuiceJamesServer): MessageId = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(bob.asString)
      .setFrom(bob.asString)
      .setTo(ANDRE.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(bob, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    server.getProbe(classOf[MailboxProbeImpl]).appendMessage(bob.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId
  }

  private def submit(messageId: MessageId, parameters: String): String =
    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
               |  "methodCalls": [
               |     ["EmailSubmission/set", {
               |       "accountId": "$bobAccountId",
               |       "create": {
               |         "k1490": {
               |           "emailId": "${messageId.serialize}",
               |           "envelope": {
               |             "mailFrom": {"email": "${bob.asString}", "parameters": $parameters},
               |             "rcptTo": [{"email": "${ANDRE.asString}"}]
               |           }
               |         }
               |    }
               |  }, "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def holdForShouldBeRejected(server: GuiceJamesServer): Unit = {
    val response = submit(appendDraft(server), """{"holdFor": "20"}""")

    assertThatJson(response)
      .inPath("methodResponses[0][1].notCreated.k1490")
      .isEqualTo("""{
                   |  "type": "invalidProperties",
                   |  "description": "The mail queue of this server does not support delayed sends",
                   |  "properties": ["envelope"]
                   |}""".stripMargin)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def holdUntilShouldBeRejected(server: GuiceJamesServer): Unit = {
    val response = submit(appendDraft(server), """{"holdUntil": "2100-01-01T00:00:00Z"}""")

    assertThatJson(response)
      .inPath("methodResponses[0][1].notCreated.k1490.type")
      .isEqualTo("invalidProperties")
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def zeroHoldForShouldBeAccepted(server: GuiceJamesServer): Unit = {
    val response = submit(appendDraft(server), """{"holdFor": "0"}""")

    assertThatJson(response)
      .inPath("methodResponses[0][1].created")
      .isObject
      .containsKey("k1490")
  }
}
