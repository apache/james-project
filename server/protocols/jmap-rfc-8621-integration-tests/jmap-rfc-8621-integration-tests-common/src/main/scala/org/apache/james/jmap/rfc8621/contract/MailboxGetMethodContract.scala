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

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.Date

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured._
import io.restassured.http.ContentType.JSON
import javax.mail.Flags
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option
import net.javacrumbs.jsonunit.core.internal.Options
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.quota.{QuotaCountLimit, QuotaSizeLimit}
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.MailboxACL.Right
import org.apache.james.mailbox.model.{MailboxACL, MailboxId, MailboxPath}
import org.apache.james.mailbox.{DefaultMailboxes, Role}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.{ACLProbeImpl, MailboxProbeImpl, QuotaProbesImpl}
import org.apache.james.utils.DataProbeImpl
import org.hamcrest.Matchers._
import org.junit.jupiter.api.{BeforeEach, Tag, Test}

import scala.jdk.CollectionConverters._

object MailboxGetMethodContract {
  private val ARGUMENTS: String = "methodResponses[0][1]"
  private val FIRST_MAILBOX: String = ARGUMENTS + ".list[0]"
  private val SECOND_MAILBOX: String = ARGUMENTS + ".list[1]"

  private val LOOKUP: String = Right.Lookup.asCharacter.toString
  private val READ: String = Right.Read.asCharacter.toString
  private val ADMINISTER: String = Right.Administer.asCharacter.toString
}

trait MailboxGetMethodContract {
  import MailboxGetMethodContract._

  def randomMailboxId: MailboxId

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)
      .addUser(ANDRE.asString, ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  @Test
  def mailboxGetShouldFailWhenWrongAccountId(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:vacationresponse"],
         |  "methodCalls": [[
         |    "Mailbox/get",
         |    {
         |      "accountId": "unknownAccountId",
         |      "ids": null
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
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
  def getMailboxesShouldIncludeRightsAndNamespaceIfSharesCapabilityIsUsed(server: GuiceJamesServer): Unit = {
    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "custom"))
      .serialize

    val response: String = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:apache:james:params:jmap:mail:shares"],
               |  "methodCalls": [[
               |    "Mailbox/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "ids": ["${mailboxId}"]
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
      .whenIgnoringPaths("methodResponses[0][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [
         |        {
         |          "id": "${mailboxId}",
         |          "name": "custom",
         |          "sortOrder": 1000,
         |          "totalEmails": 0,
         |          "unreadEmails": 0,
         |          "totalThreads": 0,
         |          "unreadThreads": 0,
         |          "myRights": {
         |            "mayReadItems": true,
         |            "mayAddItems": true,
         |            "mayRemoveItems": true,
         |            "maySetSeen": true,
         |            "maySetKeywords": true,
         |            "mayCreateChild": true,
         |            "mayRename": true,
         |            "mayDelete": true,
         |            "maySubmit": true
         |          },
         |          "isSubscribed": false,
         |          "namespace": "Personal",
         |          "rights": {}
         |        }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def getMailboxesShouldIncludeQuotasIfQuotaCapabilityIsUsed(server: GuiceJamesServer): Unit = {
    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "custom"))
      .serialize

    val response: String = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:apache:james:params:jmap:mail:quota"],
               |  "methodCalls": [[
               |    "Mailbox/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "ids": ["${mailboxId}"]
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
      .whenIgnoringPaths("methodResponses[0][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [
         |        {
         |          "id": "${mailboxId}",
         |          "name": "custom",
         |          "sortOrder": 1000,
         |          "totalEmails": 0,
         |          "unreadEmails": 0,
         |          "totalThreads": 0,
         |          "unreadThreads": 0,
         |          "myRights": {
         |            "mayReadItems": true,
         |            "mayAddItems": true,
         |            "mayRemoveItems": true,
         |            "maySetSeen": true,
         |            "maySetKeywords": true,
         |            "mayCreateChild": true,
         |            "mayRename": true,
         |            "mayDelete": true,
         |            "maySubmit": true
         |          },
         |          "isSubscribed": false,
         |          "quotas": {
         |            "#private&bob@domain.tld": {
         |              "Storage": { "used": 0},
         |              "Message": {"used": 0}
         |            }
         |          }
         |        }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def getMailboxesShouldIncludeBothQuotasAndRightsIfUsed(server: GuiceJamesServer): Unit = {
    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "custom"))
      .serialize

    val response: String = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:apache:james:params:jmap:mail:shares",
               |    "urn:apache:james:params:jmap:mail:quota"],
               |  "methodCalls": [[
               |    "Mailbox/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "ids": ["${mailboxId}"]
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
      .whenIgnoringPaths("methodResponses[0][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [
         |        {
         |          "id": "${mailboxId}",
         |          "name": "custom",
         |          "sortOrder": 1000,
         |          "totalEmails": 0,
         |          "unreadEmails": 0,
         |          "totalThreads": 0,
         |          "unreadThreads": 0,
         |          "myRights": {
         |            "mayReadItems": true,
         |            "mayAddItems": true,
         |            "mayRemoveItems": true,
         |            "maySetSeen": true,
         |            "maySetKeywords": true,
         |            "mayCreateChild": true,
         |            "mayRename": true,
         |            "mayDelete": true,
         |            "maySubmit": true
         |          },
         |          "isSubscribed": false,
         |          "namespace": "Personal",
         |          "rights": {},
         |          "quotas": {
         |            "#private&bob@domain.tld": {
         |              "Storage": { "used": 0},
         |              "Message": {"used": 0}
         |            }
         |          }
         |        }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def getMailboxesShouldReturnAllPropertiesWhenNull(server: GuiceJamesServer): Unit = {
    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "custom"))
      .serialize

    val response: String = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [[
               |    "Mailbox/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "properties": null,
               |      "ids": ["${mailboxId}"]
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
      .whenIgnoringPaths("methodResponses[0][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [
         |        {
         |          "id": "${mailboxId}",
         |          "name": "custom",
         |          "sortOrder": 1000,
         |          "totalEmails": 0,
         |          "unreadEmails": 0,
         |          "totalThreads": 0,
         |          "unreadThreads": 0,
         |          "myRights": {
         |            "mayReadItems": true,
         |            "mayAddItems": true,
         |            "mayRemoveItems": true,
         |            "maySetSeen": true,
         |            "maySetKeywords": true,
         |            "mayCreateChild": true,
         |            "mayRename": true,
         |            "mayDelete": true,
         |            "maySubmit": true
         |          },
         |          "isSubscribed": false
         |        }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def getMailboxesShouldReturnIdWhenNoPropertiesRequested(server: GuiceJamesServer): Unit = {
    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "custom"))
      .serialize

    val response: String = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [[
               |    "Mailbox/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "properties": [],
               |      "ids": ["${mailboxId}"]
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
      .whenIgnoringPaths("methodResponses[0][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [
         |        {
         |          "id": "${mailboxId}"
         |        }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def getMailboxesShouldReturnOnlyNameAndIdWhenPropertiesRequested(server: GuiceJamesServer): Unit = {
    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "custom"))
      .serialize

    val response: String = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [[
               |    "Mailbox/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "properties": ["id", "name"],
               |      "ids": ["${mailboxId}"]
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
      .whenIgnoringPaths("methodResponses[0][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [
         |        {
         |          "id": "${mailboxId}",
         |          "name": "custom"
         |        }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def getMailboxesShouldAlwaysReturnIdEvenIfNotRequested(server: GuiceJamesServer): Unit = {
    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "custom"))
      .serialize

    val response: String = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [[
               |    "Mailbox/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "properties": ["name"],
               |      "ids": ["${mailboxId}"]
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
      .whenIgnoringPaths("methodResponses[0][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [
         |        {
         |          "id": "${mailboxId}",
         |          "name": "custom"
         |        }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def getMailboxesShouldNotIncludeNamespaceIfSharesCapabilityIsUsedAndNamespaceIsNotRequested(server: GuiceJamesServer): Unit = {
    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "custom"))
      .serialize

    val response: String = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:apache:james:params:jmap:mail:shares"],
               |  "methodCalls": [[
               |    "Mailbox/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "properties": ["id", "name", "rights"],
               |      "ids": ["${mailboxId}"]
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
      .whenIgnoringPaths("methodResponses[0][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [
         |        {
         |          "id": "${mailboxId}",
         |          "name": "custom",
         |          "rights": {}
         |        }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def getMailboxesShouldReturnNotFoundWhenInvalid(): Unit = {
    val response: String = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [[
               |    "Mailbox/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "ids": ["invalid"]
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
      .whenIgnoringPaths("methodResponses[0][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [],
         |      "notFound": ["invalid"]
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def getMailboxesShouldReturnNotFoundWhenUnknownClientId(): Unit = {
    val response: String = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [[
               |    "Mailbox/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "ids": ["#C42"]
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
      .whenIgnoringPaths("methodResponses[0][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [],
         |      "notFound": ["#C42"]
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def getMailboxesShouldReturnInvalidArgumentsErrorWhenInvalidProperty(server: GuiceJamesServer): Unit = {
    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "custom"))
      .serialize

    val response: String = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
              |  "using": [
              |    "urn:ietf:params:jmap:core",
              |    "urn:ietf:params:jmap:mail"],
              |  "methodCalls": [[
              |      "Mailbox/get",
              |      {
              |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
              |        "properties": ["invalidProperty"],
              |        "ids": ["${mailboxId}"]
              |      },
              |      "c1"]]
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
         |   "error", {
         |     "type": "invalidArguments",
         |     "description": "The following properties [invalidProperty] do not exist."
         |},
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def getMailboxesShouldReturnAllExistingMailboxes(server: GuiceJamesServer): Unit = {
    val customMailbox: String = "custom"
    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, customMailbox))
      .serialize

    val expectedList = DefaultMailboxes.DEFAULT_MAILBOXES.asScala ++ List(customMailbox)

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(GET_ALL_MAILBOXES_REQUEST)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body(s"$ARGUMENTS.list", hasSize(DefaultMailboxes.DEFAULT_MAILBOXES.size()))
      .body(s"$ARGUMENTS.list.name", hasItems(expectedList.toArray:_*))
  }

  @Test
  def getMailboxesShouldReturnOnlyMailboxesOfCurrentUser(server: GuiceJamesServer): Unit = {
    val andreMailbox: String = "andrecustom"
    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(ANDRE, andreMailbox))

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(GET_ALL_MAILBOXES_REQUEST)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body(s"$ARGUMENTS.list", hasSize(6))
      .body(s"$ARGUMENTS.list.name", hasItems(DefaultMailboxes.DEFAULT_MAILBOXES.toArray:_*))
      .body(s"$ARGUMENTS.list.name", not(hasItem(andreMailbox)))
  }

  @Test
  def getMailboxesShouldReturnSharedRights(server: GuiceJamesServer): Unit = {
    val targetUser1: String = "touser1@" + DOMAIN.asString
    val targetUser2: String = "touser2@" + DOMAIN.asString
    val mailboxName: String = "myMailbox"
    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, mailboxName))
      .serialize

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(MailboxPath.forUser(BOB, mailboxName), targetUser1, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Administer))
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(MailboxPath.forUser(BOB, mailboxName), targetUser2, new MailboxACL.Rfc4314Rights(Right.Read, Right.Lookup))

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:apache:james:params:jmap:mail:shares"],
               |  "methodCalls": [[
               |      "Mailbox/get",
               |      {
               |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |        "ids": ["${mailboxId}"]
               |      },
               |      "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body(s"$ARGUMENTS.list", hasSize(1))
      .body(s"$FIRST_MAILBOX.name", equalTo(mailboxName))
      .body(s"$FIRST_MAILBOX.rights['$targetUser1']", contains(ADMINISTER, LOOKUP))
      .body(s"$FIRST_MAILBOX.rights['$targetUser2']", contains(LOOKUP, READ))
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def getMailboxesShouldReturnDelegatedNamespaceWhenSharedMailbox(server: GuiceJamesServer): Unit = {
    val sharedMailboxName = "AndreShared"
    val andreMailboxPath = MailboxPath.forUser(ANDRE, sharedMailboxName)
    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(andreMailboxPath)
      .serialize

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailboxPath, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Lookup))

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:apache:james:params:jmap:mail:shares"],
               |  "methodCalls": [[
               |      "Mailbox/get",
               |      {
               |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |        "ids": ["${mailboxId}"]
               |      },
               |      "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body(s"$ARGUMENTS.list", hasSize(1))
      .body(s"$FIRST_MAILBOX.name", equalTo(sharedMailboxName))
      .body(s"$FIRST_MAILBOX.namespace", equalTo(s"Delegated[${ANDRE.asString}]"))
      .body(s"$FIRST_MAILBOX.rights['${BOB.asString}']", contains(LOOKUP))
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def getMailboxesShouldReturnNotFoundWhenSharedMailboxAndNoExtension(server: GuiceJamesServer): Unit = {
    val sharedMailboxName = "AndreShared"
    val andreMailboxPath = MailboxPath.forUser(ANDRE, sharedMailboxName)
    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(andreMailboxPath)
      .serialize

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailboxPath, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Lookup))

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [[
               |      "Mailbox/get",
               |      {
               |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |        "ids": ["${mailboxId}"]
               |      },
               |      "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body(s"$ARGUMENTS.notFound", hasSize(1))
      .body(s"$ARGUMENTS.notFound[0]", equalTo(mailboxId))
  }

  @Test
  def getMailboxesShouldNotIncludeDelegatedMailboxesWhenExtensionNotPresent(server: GuiceJamesServer): Unit = {
    val sharedMailboxName = "AndreShared"
    val andreMailboxPath = MailboxPath.forUser(ANDRE, sharedMailboxName)
    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(andreMailboxPath)

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailboxPath, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Lookup))

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [[
               |      "Mailbox/get",
               |      {
               |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
               |      },
               |      "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      // Only system mailboxes are included
      .body(s"$ARGUMENTS.list", hasSize(6))
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def getMailboxesShouldNotReturnOtherPeopleRightsAsSharee(server: GuiceJamesServer): Unit = {
    val toUser1: String = "touser1@" + DOMAIN.asString
    val sharedMailboxName: String = "AndreShared"
    val andreMailboxPath: MailboxPath = MailboxPath.forUser(ANDRE, sharedMailboxName)
    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(andreMailboxPath)
      .serialize

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailboxPath, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailboxPath, toUser1, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:apache:james:params:jmap:mail:shares"],
               |  "methodCalls": [[
               |      "Mailbox/get",
               |      {
               |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |        "ids": ["${mailboxId}"]
               |      },
               |      "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body(s"$ARGUMENTS.list", hasSize(1))
      .body(s"$FIRST_MAILBOX.name", equalTo(sharedMailboxName))
      .body(s"$FIRST_MAILBOX.rights['${BOB.asString}']", contains(LOOKUP, READ))
      .body(s"$FIRST_MAILBOX.rights['$toUser1']", nullValue)
  }

  @Test
  def getMailboxesShouldReturnPartiallyAllowedMayPropertiesWhenDelegated(server: GuiceJamesServer): Unit = {
    val toUser1: String = "touser1@" + DOMAIN.asString
    val sharedMailboxName: String = "AndreShared"
    val andreMailboxPath: MailboxPath = MailboxPath.forUser(ANDRE, sharedMailboxName)
    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(andreMailboxPath)
      .serialize

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailboxPath, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailboxPath, toUser1, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:apache:james:params:jmap:mail:shares"],
               |  "methodCalls": [[
               |      "Mailbox/get",
               |      {
               |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |        "ids": ["${mailboxId}"]
               |      },
               |      "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body(s"$ARGUMENTS.list", hasSize(1))
      .body(s"$FIRST_MAILBOX.name", equalTo(sharedMailboxName))
      .body(s"$FIRST_MAILBOX.myRights.mayReadItems", equalTo(true))
      .body(s"$FIRST_MAILBOX.myRights.mayAddItems", equalTo(false))
      .body(s"$FIRST_MAILBOX.myRights.mayRemoveItems", equalTo(false))
      .body(s"$FIRST_MAILBOX.myRights.mayCreateChild", equalTo(false))
      .body(s"$FIRST_MAILBOX.myRights.mayDelete", equalTo(false))
      .body(s"$FIRST_MAILBOX.myRights.mayRename", equalTo(false))
      .body(s"$FIRST_MAILBOX.myRights.maySubmit", equalTo(false))
      .body(s"$FIRST_MAILBOX.myRights.maySetSeen", equalTo(false))
      .body(s"$FIRST_MAILBOX.myRights.maySetKeywords", equalTo(false))
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def getMailboxesShouldNotReturnInboxRoleToShareeWhenDelegatedInbox(server: GuiceJamesServer): Unit = {
    val andreMailboxPath = MailboxPath.forUser(ANDRE, DefaultMailboxes.INBOX)
    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(andreMailboxPath)
      .serialize

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailboxPath, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Lookup))

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:apache:james:params:jmap:mail:shares"],
               |  "methodCalls": [[
               |      "Mailbox/get",
               |      {
               |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |        "ids": ["${mailboxId}"]
               |      },
               |      "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body(s"$ARGUMENTS.list", hasSize(1))
      .body(s"$FIRST_MAILBOX.name", equalTo(DefaultMailboxes.INBOX))
      .body(s"$FIRST_MAILBOX.role", nullValue)
      .body(s"$FIRST_MAILBOX.sortOrder", equalTo(1000))
  }

  @Test
  def getMailboxesShouldReturnCorrectMailboxRole(server: GuiceJamesServer): Unit = {
    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, DefaultMailboxes.INBOX))
      .serialize

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:apache:james:params:jmap:mail:shares"],
               |  "methodCalls": [[
               |      "Mailbox/get",
               |      {
               |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |        "ids": ["${mailboxId}"]
               |      },
               |      "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body(s"$ARGUMENTS.list", hasSize(1))
      .body(s"$FIRST_MAILBOX.name", equalTo(DefaultMailboxes.INBOX))
      .body(s"$FIRST_MAILBOX.role", equalTo(Role.INBOX.serialize))
      .body(s"$FIRST_MAILBOX.sortOrder", equalTo(10))
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def getMailboxesShouldReturnUpdatedQuotasForInboxWhenMailReceived(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, DefaultMailboxes.INBOX))
      .serialize

    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.forUser(BOB, DefaultMailboxes.INBOX), AppendCommand.from(message))
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.forUser(BOB, DefaultMailboxes.INBOX),
        new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags(Flags.Flag.SEEN))

    Thread.sleep(1000) //dirty fix for distributed integration test...

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:apache:james:params:jmap:mail:quota"],
               |  "methodCalls": [[
               |      "Mailbox/get",
               |      {
               |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |        "ids": ["${mailboxId}"]
               |      },
               |      "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body(s"$ARGUMENTS.list", hasSize(1))
      .body(s"$FIRST_MAILBOX.name", equalTo(DefaultMailboxes.INBOX))
      .body(s"$FIRST_MAILBOX.quotas['#private&bob@domain.tld']['Storage'].used", equalTo(110))
      .body(s"$FIRST_MAILBOX.quotas['#private&bob@domain.tld']['Storage'].max", nullValue)
      .body(s"$FIRST_MAILBOX.quotas['#private&bob@domain.tld']['Message'].used", equalTo(2))
      .body(s"$FIRST_MAILBOX.quotas['#private&bob@domain.tld']['Message'].max", nullValue)
  }

  @Test
  def getMailboxesShouldReturnMaximumQuotasWhenSet(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[QuotaProbesImpl])
      .setGlobalMaxStorage(QuotaSizeLimit.size(142))
    server.getProbe(classOf[QuotaProbesImpl])
      .setGlobalMaxMessageCount(QuotaCountLimit.count(31))

    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, DefaultMailboxes.INBOX))
      .serialize

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:apache:james:params:jmap:mail:quota"],
               |  "methodCalls": [[
               |      "Mailbox/get",
               |      {
               |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |        "ids": ["${mailboxId}"]
               |      },
               |      "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body(s"$ARGUMENTS.list", hasSize(1))
      .body(s"$FIRST_MAILBOX.name", equalTo(DefaultMailboxes.INBOX))
      .body(s"$FIRST_MAILBOX.quotas['#private&bob@domain.tld']['Storage'].max", equalTo(142))
      .body(s"$FIRST_MAILBOX.quotas['#private&bob@domain.tld']['Message'].max", equalTo(31))
  }

  @Test
  def getMailboxesShouldReturnMailboxesInSorteredOrder(server: GuiceJamesServer): Unit = {
    val mailboxId1: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, DefaultMailboxes.TRASH))
      .serialize
    val mailboxId2: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, DefaultMailboxes.INBOX))
      .serialize

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
              |  "using": [
              |    "urn:ietf:params:jmap:core",
              |    "urn:ietf:params:jmap:mail"],
              |  "methodCalls": [[
              |      "Mailbox/get",
              |      {
              |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
              |        "ids": ["${mailboxId1}", "${mailboxId2}"]
              |      },
              |      "c1"]]
              |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body(s"$ARGUMENTS.list", hasSize(2))
      .body(s"$FIRST_MAILBOX.id", equalTo(mailboxId2))
      .body(s"$FIRST_MAILBOX.name", equalTo(DefaultMailboxes.INBOX))
      .body(s"$FIRST_MAILBOX.sortOrder", equalTo(10))
      .body(s"$SECOND_MAILBOX.id", equalTo(mailboxId1))
      .body(s"$SECOND_MAILBOX.name", equalTo(DefaultMailboxes.TRASH))
      .body(s"$SECOND_MAILBOX.sortOrder", equalTo(60))
  }

  @Test
  def getMailboxesByIdsShouldReturnCorrespondingMailbox(server: GuiceJamesServer): Unit = {
    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "custom"))
      .serialize

    val response: String = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:apache:james:params:jmap:mail:quota",
               |    "urn:apache:james:params:jmap:mail:shares"],
               |  "methodCalls": [[
               |    "Mailbox/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "ids": ["${mailboxId}"]
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
      .whenIgnoringPaths("methodResponses[0][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/get",
         |      {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "list": [
         |          {
         |            "id": "${mailboxId}",
         |            "name": "custom",
         |            "sortOrder": 1000,
         |            "totalEmails": 0,
         |            "unreadEmails": 0,
         |            "totalThreads": 0,
         |            "unreadThreads": 0,
         |            "myRights": {
         |              "mayReadItems": true,
         |              "mayAddItems": true,
         |              "mayRemoveItems": true,
         |              "maySetSeen": true,
         |              "maySetKeywords": true,
         |              "mayCreateChild": true,
         |              "mayRename": true,
         |              "mayDelete": true,
         |              "maySubmit": true
         |            },
         |            "isSubscribed": false,
         |            "namespace": "Personal",
         |            "rights": {},
         |            "quotas": {
         |              "#private&bob@domain.tld": {
         |                "Storage": { "used": 0},
         |                "Message": {"used": 0}
         |              }
         |            }
         |          }
         |        ],
         |        "notFound": []
         |      },
         |      "c1"]]
         |}""".stripMargin)
  }

  @Test
  def getMailboxesByIdsShouldReturnCorrespondingMailboxWithoutPropertiesFromNotProvidedCapabilities(server: GuiceJamesServer): Unit = {
    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "custom"))
      .serialize

    val response: String = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [[
               |    "Mailbox/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "ids": ["${mailboxId}"]
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
      .whenIgnoringPaths("methodResponses[0][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/get",
         |      {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "list": [
         |          {
         |            "id": "${mailboxId}",
         |            "name": "custom",
         |            "sortOrder": 1000,
         |            "totalEmails": 0,
         |            "unreadEmails": 0,
         |            "totalThreads": 0,
         |            "unreadThreads": 0,
         |            "myRights": {
         |              "mayReadItems": true,
         |              "mayAddItems": true,
         |              "mayRemoveItems": true,
         |              "maySetSeen": true,
         |              "maySetKeywords": true,
         |              "mayCreateChild": true,
         |              "mayRename": true,
         |              "mayDelete": true,
         |              "maySubmit": true
         |            },
         |            "isSubscribed": false
         |          }
         |        ],
         |        "notFound": []
         |      },
         |      "c1"]]
         |}""".stripMargin)
  }

  @Test
  def getMailboxesByIdsShouldReturnOnlyRequestedMailbox(server: GuiceJamesServer): Unit = {
    val mailboxName: String = "custom"
    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "custom"))
      .serialize
    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "othercustom"))

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [[
               |    "Mailbox/get",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "ids": ["${mailboxId}"]
               |    },
               |    "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body(s"$ARGUMENTS.list", hasSize(1))
      .body(s"$FIRST_MAILBOX.id", equalTo(mailboxId))
      .body(s"$FIRST_MAILBOX.name", equalTo(mailboxName))
  }

  @Test
  def getMailboxesByIdsShouldReturnBothFoundAndNotFound(server: GuiceJamesServer): Unit = {
    val mailboxName: String = "custom"
    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "custom"))
      .serialize
    val randomId = randomMailboxId.serialize()
    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "othercustom"))

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [[
               |     "Mailbox/get",
               |     {
               |       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |       "ids": ["$mailboxId", "$randomId"]
               |     },
               |     "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body(s"$ARGUMENTS.list", hasSize(1))
      .body(s"$FIRST_MAILBOX.id", equalTo(mailboxId))
      .body(s"$FIRST_MAILBOX.name", equalTo(mailboxName))
      .body(s"$ARGUMENTS.notFound", hasSize(1))
      .body(s"$ARGUMENTS.notFound", contains(randomId))
  }

  @Test
  def getMailboxesByIdsShouldReturnNotFoundWhenMailboxDoesNotExist(): Unit = {
    val randomId = randomMailboxId.serialize()

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:mail"],
           |  "methodCalls": [[
           |    "Mailbox/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "ids": ["$randomId"]
           |    },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body(s"$ARGUMENTS.list", empty())
      .body(s"$ARGUMENTS.notFound", hasSize(1))
      .body(s"$ARGUMENTS.notFound", contains(randomId))
  }

  @Test
  def getMailboxesByIdsShouldReturnMailboxesInSortedOrder(server: GuiceJamesServer): Unit = {
    val mailboxId1: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, DefaultMailboxes.TRASH))
      .serialize
    val mailboxId2: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, DefaultMailboxes.INBOX))
      .serialize

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [[
               |     "Mailbox/get",
               |     {
               |       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |       "ids": ["$mailboxId1", "$mailboxId2"]
               |     },
               |     "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body(s"$ARGUMENTS.list", hasSize(2))
      .body(s"$FIRST_MAILBOX.id", equalTo(mailboxId2))
      .body(s"$FIRST_MAILBOX.name", equalTo(DefaultMailboxes.INBOX))
      .body(s"$FIRST_MAILBOX.sortOrder", equalTo(10))
      .body(s"$SECOND_MAILBOX.id", equalTo(mailboxId1))
      .body(s"$SECOND_MAILBOX.name", equalTo(DefaultMailboxes.TRASH))
      .body(s"$SECOND_MAILBOX.sortOrder", equalTo(60))
  }

  @Test
  def getMailboxesShouldReturnUnknownMethodWhenMissingOneCapability(): Unit = {
    val mailboxId = randomMailboxId

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [ "urn:ietf:params:jmap:mail" ],
               |  "methodCalls": [[
               |     "Mailbox/get",
               |     {
               |       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |       "ids": ["$mailboxId"]
               |     },
               |     "c1"]]
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
         |      "description": "Missing capability(ies): urn:ietf:params:jmap:core"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def getMailboxesShouldReturnUnknownMethodWhenMissingAllCapabilities(): Unit = {
    val mailboxId = randomMailboxId

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [],
               |  "methodCalls": [[
               |     "Mailbox/get",
               |     {
               |       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |       "ids": ["$mailboxId"]
               |     },
               |     "c1"]]
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
         |      "description": "Missing capability(ies): urn:ietf:params:jmap:core, urn:ietf:params:jmap:mail"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def mailboxStateShouldBeTheLatestOne(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxPath.inbox(BOB))

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
           |  "methodCalls": [[
           |     "Mailbox/get",
           |     {
           |       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |       "ids": null,
           |       "properties": ["id"]
           |     },
           |     "c1"],[
           |      "Mailbox/changes",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "#sinceState": {
           |           "resultOf":"c1",
           |           "name":"Mailbox/get",
           |           "path":"state"
           |         }
           |      },
           |      "c2"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .extract()
      .body()
      .asString()

    // No changes as Mailbox/get state property is the latest one
    assertThatJson(response)
      .withOptions(new Options(Option.IGNORING_ARRAY_ORDER))
      .whenIgnoringPaths("methodResponses[1][1].oldState", "methodResponses[1][1].newState")
      .inPath("methodResponses[1][1]")
      .isEqualTo(
        s"""{
           |  "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |  "hasMoreChanges": false,
           |  "updatedProperties": null,
           |  "created": [],
           |  "updated": [],
           |  "destroyed": []
           |}""".stripMargin)
  }

  @Test
  def stateShouldNotTakeIntoAccountDelegationWhenNoCapability(server: GuiceJamesServer): Unit = {
    val state: String = `with`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
           |  "methodCalls": [[
           |      "Mailbox/get",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
           |      },
           |      "c1"]]
           |}""".stripMargin)
      .post
      .`then`()
      .extract()
      .jsonPath()
      .get("methodResponses[0][1].state")

    val sharedMailboxName = "AndreShared"
    val andreMailboxPath = MailboxPath.forUser(ANDRE, sharedMailboxName)
    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(andreMailboxPath)
      .serialize
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailboxPath, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Lookup))

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [[
               |      "Mailbox/get",
               |      {
               |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
               |      },
               |      "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body(s"$ARGUMENTS.state", equalTo(state))
  }

  @Test
  def stateShouldTakeIntoAccountDelegationWhenCapability(server: GuiceJamesServer): Unit = {
    val state: String = `with`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
           |  "methodCalls": [[
           |      "Mailbox/get",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
           |      },
           |      "c1"]]
           |}""".stripMargin)
      .post
      .`then`()
      .extract()
      .jsonPath()
      .get("methodResponses[0][1].state")

    val sharedMailboxName = "AndreShared"
    val andreMailboxPath = MailboxPath.forUser(ANDRE, sharedMailboxName)
    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(andreMailboxPath)
      .serialize
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailboxPath, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Lookup))

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:apache:james:params:jmap:mail:shares"],
               |  "methodCalls": [[
               |      "Mailbox/get",
               |      {
               |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
               |      },
               |      "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body(s"$ARGUMENTS.state", not(equalTo(state)))
  }
}
