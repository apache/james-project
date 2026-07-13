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
import java.util.UUID

import com.google.common.hash.Hashing
import com.google.inject.AbstractModule
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured._
import io.restassured.http.ContentType.JSON
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.mail.{DelegatedNamespace, MailboxNamespace, NamespaceFactory, PersonalNamespace}
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}

object CustomNamespaceContractContext {
  case class TestContext(bobUsername: Username, bobAccountId: String)
  val currentContext: java.util.concurrent.atomic.AtomicReference[TestContext] = new java.util.concurrent.atomic.AtomicReference[TestContext]()
}

case class CustomMailboxNamespace(value: String) extends MailboxNamespace {
  override def serialize(): String = value
}

class CustomNamespaceModule extends AbstractModule {
  override def configure(): Unit =
    bind(classOf[NamespaceFactory]).to(classOf[CustomNamespaceFactory])
}

class CustomNamespaceFactory extends NamespaceFactory {

  def from(mailboxPath: MailboxPath, mailboxSession: MailboxSession): MailboxNamespace = {
    val user = mailboxSession.getUser
    if (mailboxPath == MailboxPath.forUser(user, "personal")) PersonalNamespace()
    else if (mailboxPath == MailboxPath.forUser(user, "delegated")) DelegatedNamespace(user)
    else if (mailboxPath == MailboxPath.forUser(user, "custom")) CustomMailboxNamespace("custom 123")
    else PersonalNamespace()
  }
}

object CustomNamespaceContract {
}

trait CustomNamespaceContract {
  import CustomNamespaceContractContext.currentContext

  def bobUsername: Username = currentContext.get().bobUsername
  def bobAccountId: String = currentContext.get().bobAccountId
  def customNamespaceMailboxPath: MailboxPath = MailboxPath.forUser(bobUsername, "custom")
  def personalNamespaceMailboxPath: MailboxPath = MailboxPath.forUser(bobUsername, "personal")
  def delegatedNamespaceMailboxPath: MailboxPath = MailboxPath.forUser(bobUsername, "delegated")

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    val bob = Username.fromLocalPartWithDomain(s"bob${UUID.randomUUID().toString.replace("-", "").take(8)}", DOMAIN)
    currentContext.set(CustomNamespaceContractContext.TestContext(
      bob, Hashing.sha256().hashString(bob.asString(), StandardCharsets.UTF_8).toString))
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(bobUsername.asString, BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(bobUsername, BOB_PASSWORD)))
      .build
  }

  @Test
  def getMailboxShouldIncludeCustomNamespaceWhenAssociated(server: GuiceJamesServer): Unit = {
    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(customNamespaceMailboxPath)
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
           |      "accountId": "$bobAccountId",
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
      .createMailbox(personalNamespaceMailboxPath)
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
           |      "accountId": "$bobAccountId",
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
      .createMailbox(delegatedNamespaceMailboxPath)
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
           |      "accountId": "$bobAccountId",
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
      .isEqualTo(s"Delegated[${bobUsername.asString}]")
  }
}
