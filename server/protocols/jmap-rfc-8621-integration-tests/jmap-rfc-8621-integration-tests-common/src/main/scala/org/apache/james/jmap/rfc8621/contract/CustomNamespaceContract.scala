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

import com.google.inject.AbstractModule
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured._
import io.restassured.http.ContentType.JSON
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.mail.{DelegatedNamespace, MailboxNamespace, NamespaceFactory, PersonalNamespace}
import org.apache.james.jmap.rfc8621.contract.CustomNamespaceContract.{CUSTOM_NAMESPACE_MAILBOX_PATH, DELEGATED_NAMESPACE_MAILBOX_PATH, PERSONAL_NAMESPACE_MAILBOX_PATH}
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}

import javax.inject.Inject

case class CustomMailboxNamespace(value: String) extends MailboxNamespace {
  override def serialize(): String = value
}

class CustomNamespaceModule extends AbstractModule {
  override def configure(): Unit =
    bind(classOf[NamespaceFactory]).to(classOf[CustomNamespaceFactory])
}

class CustomNamespaceFactory extends NamespaceFactory {

  def from(mailboxPath: MailboxPath, mailboxSession: MailboxSession): MailboxNamespace = mailboxPath match {
    case PERSONAL_NAMESPACE_MAILBOX_PATH => PersonalNamespace()
    case DELEGATED_NAMESPACE_MAILBOX_PATH => DelegatedNamespace(mailboxSession.getUser)
    case CUSTOM_NAMESPACE_MAILBOX_PATH => CustomMailboxNamespace("custom 123")
  }
}

object CustomNamespaceContract {
  val CUSTOM_NAMESPACE_MAILBOX_PATH: MailboxPath = MailboxPath.forUser(BOB, "custom")
  val PERSONAL_NAMESPACE_MAILBOX_PATH: MailboxPath = MailboxPath.forUser(BOB, "personal")
  val DELEGATED_NAMESPACE_MAILBOX_PATH: MailboxPath = MailboxPath.forUser(BOB, "delegated")
}

trait CustomNamespaceContract {

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  @Test
  def getMailboxShouldIncludeCustomNamespaceWhenAssociated(server: GuiceJamesServer): Unit = {
    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(CUSTOM_NAMESPACE_MAILBOX_PATH)
      .serialize

    val namespace: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:mail",
           |    "urn:apache:james:params:jmap:mail:shares"],
           |  "methodCalls": [[
           |    "Mailbox/get",
           |    {
           |      "accountId": "$ACCOUNT_ID",
           |      "ids": ["${mailboxId}"]
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
      .get("methodResponses[0][1].list[0].namespace")

    assertThat(namespace)
      .isEqualTo("custom 123")
  }

  @Test
  def getMailboxShouldIncludePersonalNamespaceWhenAssociated(server: GuiceJamesServer): Unit = {
    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(PERSONAL_NAMESPACE_MAILBOX_PATH)
      .serialize

    val namespace: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:mail",
           |    "urn:apache:james:params:jmap:mail:shares"],
           |  "methodCalls": [[
           |    "Mailbox/get",
           |    {
           |      "accountId": "$ACCOUNT_ID",
           |      "ids": ["${mailboxId}"]
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
      .get("methodResponses[0][1].list[0].namespace")

    assertThat(namespace)
      .isEqualTo("Personal")
  }

  @Test
  def getMailboxShouldIncludeDelegatedNamespaceWhenAssociated(server: GuiceJamesServer): Unit = {
    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(DELEGATED_NAMESPACE_MAILBOX_PATH)
      .serialize

    val namespace: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:mail",
           |    "urn:apache:james:params:jmap:mail:shares"],
           |  "methodCalls": [[
           |    "Mailbox/get",
           |    {
           |      "accountId": "$ACCOUNT_ID",
           |      "ids": ["${mailboxId}"]
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
      .get("methodResponses[0][1].list[0].namespace")

    assertThat(namespace)
      .isEqualTo("Delegated[bob@domain.tld]")
  }
}
