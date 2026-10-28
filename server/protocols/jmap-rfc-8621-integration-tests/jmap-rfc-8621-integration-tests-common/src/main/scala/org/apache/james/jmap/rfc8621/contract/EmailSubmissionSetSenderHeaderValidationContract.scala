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

import com.google.common.collect.ImmutableList
import com.google.common.hash.Hashing
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.core.JmapRfc8621Configuration
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.EmailSubmissionSetSenderHeaderValidationContract.TestContext
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE_PASSWORD, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder, identityIdOf}
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.mailbox.DefaultMailboxes
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Test}

object EmailSubmissionSetSenderHeaderValidationContract {
  case class TestContext(bob: Username, andre: Username)

  val currentContext: AtomicReference[TestContext] = new AtomicReference[TestContext]()

  // The short class name relies on the implicit `org.apache.james.jmap.method` package
  val configuration: JmapRfc8621Configuration = JmapRfc8621Configuration.LOCALHOST_CONFIGURATION
    .withExtraEmailSubmissionValidations(ImmutableList.of("SenderHeaderEmailSubmissionSetValidation"))

  def accountId(username: Username): String = Hashing.sha256().hashString(username.asString, StandardCharsets.UTF_8).toString
}

/**
 * `EmailSubmission/set` behaviour when `SenderHeaderEmailSubmissionSetValidation` is declared in
 * `send.extra.validations`. Requests are authenticated as Bob.
 */
trait EmailSubmissionSetSenderHeaderValidationContract {
  private def bob: Username = EmailSubmissionSetSenderHeaderValidationContract.currentContext.get().bob
  private def andre: Username = EmailSubmissionSetSenderHeaderValidationContract.currentContext.get().andre

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    val uniqueSuffix = UUID.randomUUID().toString.replace("-", "").take(8)
    val bob = Username.fromLocalPartWithDomain(s"bob$uniqueSuffix", DOMAIN)
    val andre = Username.fromLocalPartWithDomain(s"andre$uniqueSuffix", DOMAIN)
    EmailSubmissionSetSenderHeaderValidationContract.currentContext.set(TestContext(bob, andre))

    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(bob.asString, BOB_PASSWORD)
      .addUser(andre.asString, ANDRE_PASSWORD)

    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(bob, DefaultMailboxes.DRAFTS))
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(andre, DefaultMailboxes.DRAFTS))

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(bob, BOB_PASSWORD)))
      .build
  }

  @Test
  def setShouldAcceptTheLoggedInUserAsSender(server: GuiceJamesServer): Unit =
    assertThatJson(submit(server, account = bob, senderFields = Seq(bob.asString)))
      .inPath("methodResponses[0][1].created")
      .isObject
      .containsKey("k1490")

  @Test
  def setShouldAcceptMailsWithoutSender(server: GuiceJamesServer): Unit =
    assertThatJson(submit(server, account = bob, senderFields = Seq()))
      .inPath("methodResponses[0][1].created")
      .isObject
      .containsKey("k1490")

  @Test
  def setShouldRejectAnAliasOfTheLoggedInUserAsSender(server: GuiceJamesServer): Unit = {
    val alias = s"alias-${bob.getLocalPart}"
    server.getProbe(classOf[DataProbeImpl]).addUserAliasMapping(alias, DOMAIN.asString, bob.asString)

    assertThatJson(submit(server, account = bob, senderFields = Seq(s"$alias@${DOMAIN.asString}")))
      .inPath("methodResponses[0][1].notCreated")
      .isEqualTo(s"""{
                    |  "k1490": {
                    |    "type": "forbiddenFrom",
                    |    "description": "Attempt to send a mail whose MimeMessage Sender field does not match the logged in user ${bob.asString}",
                    |    "properties": ["sender"]
                    |  }
                    |}""".stripMargin)
  }

  @Test
  def setShouldRejectAnotherAddressFollowingTheLoggedInUserInSender(server: GuiceJamesServer): Unit =
    assertThatJson(submit(server, account = bob, senderFields = Seq(s"${bob.asString}, ${andre.asString}")))
      .inPath("methodResponses[0][1].notCreated.k1490")
      .isEqualTo("""{
                   |  "type": "forbiddenFrom",
                   |  "description": "${json-unit.ignore}",
                   |  "properties": ["sender"]
                   |}""".stripMargin)

  @Test
  def setShouldRejectTheAccountOwnerAsSenderUponDelegation(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe]).addAuthorizedUser(andre, bob)

    assertThatJson(submit(server, account = andre, senderFields = Seq(andre.asString)))
      .inPath("methodResponses[0][1].notCreated")
      .isEqualTo(s"""{
                    |  "k1490": {
                    |    "type": "forbiddenFrom",
                    |    "description": "Attempt to send a mail whose MimeMessage Sender field does not match the logged in user ${bob.asString}",
                    |    "properties": ["sender"]
                    |  }
                    |}""".stripMargin)
  }

  @Test
  def setShouldAcceptTheLoggedInUserAsSenderUponDelegation(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe]).addAuthorizedUser(andre, bob)

    assertThatJson(submit(server, account = andre, senderFields = Seq(bob.asString)))
      .inPath("methodResponses[0][1].created")
      .isObject
      .containsKey("k1490")
  }

  @Test
  def setShouldStillRejectTheLoggedInUserAsFromUponDelegation(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe]).addAuthorizedUser(andre, bob)

    assertThatJson(submit(server, account = andre, senderFields = Seq(), from = Some(bob.asString)))
      .inPath("methodResponses[0][1].notCreated.k1490")
      .isEqualTo("""{
                   |  "type": "forbiddenFrom",
                   |  "description": "${json-unit.ignore}",
                   |  "properties": ["from"]
                   |}""".stripMargin)
  }

  @Test
  def setShouldAcceptMailsWithoutSenderUponDelegation(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe]).addAuthorizedUser(andre, bob)

    assertThatJson(submit(server, account = andre, senderFields = Seq()))
      .inPath("methodResponses[0][1].created")
      .isObject
      .containsKey("k1490")
  }

  /**
   * Submits, on the account of `account`, a mail from `from` (by default `account`) to a remote recipient
   * carrying the given `Sender` fields. The envelope sender is `account`.
   */
  private def submit(server: GuiceJamesServer, account: Username, senderFields: Seq[String], from: Option[String] = None): String = {
    val senders = senderFields.map(field => s"Sender: $field\r\n").mkString
    val message = s"From: ${from.getOrElse(account.asString)}\r\n${senders}To: someone@remote.tld\r\nSubject: test\r\n\r\ntestmail\r\n"

    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(account.asString, MailboxPath.forUser(account, DefaultMailboxes.DRAFTS), AppendCommand.builder().build(message))
      .getMessageId

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [
         |     ["EmailSubmission/set", {
         |       "accountId": "${EmailSubmissionSetSenderHeaderValidationContract.accountId(account)}",
         |       "create": {
         |         "k1490": {
         |           "identityId": "${identityIdOf(account)}",
         |           "emailId": "${messageId.serialize}",
         |           "envelope": {
         |             "mailFrom": {"email": "${account.asString}"},
         |             "rcptTo": [{"email": "someone@remote.tld"}]
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
