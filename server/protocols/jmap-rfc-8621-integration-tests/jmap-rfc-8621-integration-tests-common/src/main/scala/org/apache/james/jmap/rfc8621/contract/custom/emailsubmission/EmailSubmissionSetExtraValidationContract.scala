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

package org.apache.james.jmap.rfc8621.contract.custom.emailsubmission

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference

import com.google.common.collect.ImmutableList
import com.google.inject.AbstractModule
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured
import io.restassured.RestAssured.`given`
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
import org.junit.jupiter.api.{AfterEach, Test}

/**
 * `EmailSubmission/set` behaviour when third party validations are declared through
 * `send.extra.validations`.
 */
object EmailSubmissionSetExtraValidationContract {
  val currentServer: AtomicReference[GuiceJamesServer] = new AtomicReference[GuiceJamesServer]()
}

trait EmailSubmissionSetExtraValidationContract {
  private def jmapServer: GuiceJamesServer = EmailSubmissionSetExtraValidationContract.currentServer.get()

  @AfterEach
  def tearDown(): Unit = jmapServer.stop()

  private def startServerWith(basedServer: GuiceJamesServer, extraValidations: java.util.List[String]): Unit = {
    EmailSubmissionSetExtraValidationContract.currentServer.set(basedServer.overrideWith(new AbstractModule {
      override def configure(): Unit =
        bind(classOf[JmapRfc8621Configuration])
          .toInstance(JmapRfc8621Configuration.LOCALHOST_CONFIGURATION
            .withExtraEmailSubmissionValidations(extraValidations))
    }))
    jmapServer.start()

    jmapServer.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)
      .addUser(ANDRE.asString, ANDRE_PASSWORD)

    jmapServer.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS))

    RestAssured.requestSpecification = baseRequestSpecBuilder(jmapServer)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  @Test
  def setShouldSucceedWhenNoExtraValidationIsDeclared(server: GuiceJamesServer): Unit = {
    startServerWith(server, ImmutableList.of())

    assertThatJson(submit())
      .inPath("methodResponses[0][1].created")
      .isObject
      .containsKey("k1490")
  }

  @Test
  def setShouldSucceedWhenTheExtraValidationAccepts(server: GuiceJamesServer): Unit = {
    startServerWith(server, ImmutableList.of(classOf[AcceptAllEmailSubmissionSetValidation].getCanonicalName))

    assertThatJson(submit())
      .inPath("methodResponses[0][1].created")
      .isObject
      .containsKey("k1490")
  }

  @Test
  def setShouldBeRejectedWhenTheExtraValidationRejects(server: GuiceJamesServer): Unit = {
    startServerWith(server, ImmutableList.of(classOf[RejectAllEmailSubmissionSetValidation].getCanonicalName))

    assertThatJson(submit())
      .inPath("methodResponses[0][1].notCreated")
      .isEqualTo(s"""{
                    |  "k1490": {
                    |    "type": "${RejectAllEmailSubmissionSetValidation.REJECTED.value}",
                    |    "description": "${RejectAllEmailSubmissionSetValidation.DESCRIPTION}"
                    |  }
                    |}""".stripMargin)
  }

  @Test
  def setShouldBeRejectedWhenASingleExtraValidationAmongSeveralRejects(server: GuiceJamesServer): Unit = {
    startServerWith(server, ImmutableList.of(
      classOf[AcceptAllEmailSubmissionSetValidation].getCanonicalName,
      classOf[RejectAllEmailSubmissionSetValidation].getCanonicalName))

    assertThatJson(submit())
      .inPath("methodResponses[0][1].notCreated")
      .isEqualTo(s"""{
                    |  "k1490": {
                    |    "type": "${RejectAllEmailSubmissionSetValidation.REJECTED.value}",
                    |    "description": "${RejectAllEmailSubmissionSetValidation.DESCRIPTION}"
                    |  }
                    |}""".stripMargin)
  }

  private def submit(): String = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(BOB.asString)
      .setFrom(BOB.asString)
      .setTo(ANDRE.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val messageId = jmapServer.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS), AppendCommand.builder().build(message))
      .getMessageId

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
         |             "rcptTo": [{"email": "${ANDRE.asString}"}]
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
