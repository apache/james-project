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
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.quota.{QuotaCountLimit, QuotaSizeLimit}
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

object MailboxGetMethodContract {
  private val ARGUMENTS: String = "methodResponses[0][1]"
  private val FIRST_MAILBOX: String = ARGUMENTS + ".list[0]"
  private val SECOND_MAILBOX: String = ARGUMENTS + ".list[1]"

  private val LOOKUP: String = Right.Lookup.asCharacter.toString
  private val READ: String = Right.Read.asCharacter.toString
  private val ADMINISTER: String = Right.Administer.asCharacter.toString

  private val GET_ALL_MAILBOXES_REQUEST: String =
    """{
    |  "using": [
    |    "urn:ietf:params:jmap:core",
    |    "urn:ietf:params:jmap:mail"],
    |  "methodCalls": [[
    |      "Mailbox/get",
    |      {
    |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
    |        "ids": null
    |      },
    |      "c1"]]
    |}""".stripMargin
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
  def getMailboxesShouldReturnExistingMailbox(server: GuiceJamesServer): Unit = {
    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "custom"))
      .serialize

    val response: String = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(GET_ALL_MAILBOXES_REQUEST)
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
         |  "sessionState": "75128aab4b1b",
         |  "methodResponses": [[
         |    "Mailbox/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "state": "000001",
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
  @Tag(CategoryTags.BASIC_FEATURE)
  def getMailboxesShouldReturnEmptyWhenNone(): Unit = {
    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(GET_ALL_MAILBOXES_REQUEST)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body(s"$ARGUMENTS.list", empty)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def getMailboxesShouldReturnAllExistingMailboxes(server: GuiceJamesServer): Unit = {
    val firstMailboxName: String = "custom"
    val mailboxId1: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, firstMailboxName))
      .serialize

    val secondMailboxName: String = "othercustom"
    val mailboxId2: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, secondMailboxName))
      .serialize

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(GET_ALL_MAILBOXES_REQUEST)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body(s"$ARGUMENTS.list", hasSize(2))
      .body(s"$FIRST_MAILBOX.id", equalTo(mailboxId1))
      .body(s"$FIRST_MAILBOX.name", equalTo(firstMailboxName))
      .body(s"$SECOND_MAILBOX.id", equalTo(mailboxId2))
      .body(s"$SECOND_MAILBOX.name", equalTo(secondMailboxName))
  }

  @Test
  def getMailboxesShouldReturnOnlyMailboxesOfCurrentUser(server: GuiceJamesServer): Unit = {
    val mailboxName: String = "custom"
    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "custom"))
      .serialize
    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(ANDRE, "andrecustom"))

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(GET_ALL_MAILBOXES_REQUEST)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body(s"$ARGUMENTS.list", hasSize(1))
      .body(s"$FIRST_MAILBOX.id", equalTo(mailboxId))
      .body(s"$FIRST_MAILBOX.name", equalTo(mailboxName))
  }

  @Test
  def getMailboxesShouldReturnSharedRights(server: GuiceJamesServer): Unit = {
    val targetUser1: String = "touser1@" + DOMAIN.asString
    val targetUser2: String = "touser2@" + DOMAIN.asString
    val mailboxName: String = "myMailbox"
    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, mailboxName))
      .serialize

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(MailboxPath.forUser(BOB, mailboxName), targetUser1, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Administer))
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(MailboxPath.forUser(BOB, mailboxName), targetUser2, new MailboxACL.Rfc4314Rights(Right.Read, Right.Lookup))

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(GET_ALL_MAILBOXES_REQUEST)
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
    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(andreMailboxPath)
      .serialize

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailboxPath, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Lookup))

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(GET_ALL_MAILBOXES_REQUEST)
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
  def getMailboxesShouldNotReturnOtherPeopleRightsAsSharee(server: GuiceJamesServer): Unit = {
    val toUser1: String = "touser1@" + DOMAIN.asString
    val sharedMailboxName: String = "AndreShared"
    val andreMailboxPath: MailboxPath = MailboxPath.forUser(ANDRE, sharedMailboxName)
    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(andreMailboxPath)
      .serialize

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailboxPath, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailboxPath, toUser1, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(GET_ALL_MAILBOXES_REQUEST)
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
    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(andreMailboxPath)
      .serialize

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailboxPath, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailboxPath, toUser1, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(GET_ALL_MAILBOXES_REQUEST)
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
    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(andreMailboxPath)
      .serialize

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailboxPath, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Lookup))

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(GET_ALL_MAILBOXES_REQUEST)
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
    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, DefaultMailboxes.INBOX))
      .serialize

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(GET_ALL_MAILBOXES_REQUEST)
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

    server.getProbe(classOf[MailboxProbeImpl])
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
      .body(GET_ALL_MAILBOXES_REQUEST)
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

    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, DefaultMailboxes.INBOX))
      .serialize

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(GET_ALL_MAILBOXES_REQUEST)
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
      .body(GET_ALL_MAILBOXES_REQUEST)
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

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "75128aab4b1b",
         |  "methodResponses": [[
         |    "Mailbox/get",
         |      {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "state": "000001",
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
  def getMailboxesByIdsShouldReturnMailboxesInSorteredOrder(server: GuiceJamesServer): Unit = {
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
}
