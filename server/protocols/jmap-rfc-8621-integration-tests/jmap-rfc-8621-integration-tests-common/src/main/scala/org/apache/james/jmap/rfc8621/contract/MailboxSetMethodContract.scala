/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *  http://www.apache.org/licenses/LICENSE-2.0                  *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.rfc8621.contract

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured._
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.mailbox.model.{MailboxId, MailboxPath}
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.{BeforeEach, Disabled, Test}

trait MailboxSetMethodContract {

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

  def randomMailboxId: MailboxId

  @Test
  def mailboxSetShouldReturnNotCreatedWhenNameIsMissing(): Unit = {
    val request=
      """
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "create": {
        |                    "C42": {
        |                    }
        |                }
        |           },
        |    "c1"
        |       ]
        |   ]
        |}
        |""".stripMargin

    val response: String =
    `given`
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
         |  "sessionState": "75128aab4b1b",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "000001",
         |      "notCreated": {
         |        "C42": {
         |          "type": "invalidArguments",
         |          "description": "Missing '/name' property in mailbox object"
         |        }
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  @Disabled("should we support that? Anyway seems hard with Play-JSON")
  def mailboxSetShouldReturnNotCreatedWhenUnknownParameter(): Unit = {
    val request=
      """
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "create": {
        |                    "C42": {
        |                      "name": "plop",
        |                      "unknown": "what?"
        |                    }
        |                }
        |           },
        |    "c1"
        |       ]
        |   ]
        |}
        |""".stripMargin

    val response: String =
      `given`
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
         |  "sessionState": "75128aab4b1b",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "000001",
         |      "notCreated": {
         |        "C42": {
         |          "type": "invalidArguments",
         |          "description": "Unknown 'unknown' property in mailbox object"
         |        }
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def mailboxSetShouldReturnNotCreatedWhenBadParameter(): Unit = {
    val request=
      """
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "create": {
        |                    "C42": {
        |                      "name": "plop",
        |                      "parentId": ""
        |                    }
        |                }
        |           },
        |    "c1"
        |       ]
        |   ]
        |}
        |""".stripMargin

    val response: String =
      `given`
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
         |  "sessionState": "75128aab4b1b",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "000001",
         |      "notCreated": {
         |        "C42": {
         |          "type": "invalidArguments",
         |          "description": "'/parentId' property in mailbox object is not valid"
         |        }
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def mailboxSetShouldCreateMailboxWhenOnlyName(server: GuiceJamesServer): Unit = {
    val request=
      """
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "create": {
        |                    "C42": {
        |                      "name": "myMailbox"
        |                    }
        |                }
        |           },
        |    "c1"
        |       ]
        |   ]
        |}
        |""".stripMargin

      `given`
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

    Assertions.assertThatCode(() => server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId("#private", BOB.asString(), "myMailbox")).doesNotThrowAnyException()
  }

  @Test
  def mailboxSetShouldReturnCreatedWhenOnlyName(server: GuiceJamesServer): Unit = {
    val request=
      """
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "create": {
        |                    "C42": {
        |                      "name": "myMailbox"
        |                    }
        |                }
        |           },
        |    "c1"
        |       ]
        |   ]
        |}
        |""".stripMargin

    val response: String =
    `given`
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

    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId("#private", BOB.asString(), "myMailbox")
      .serialize()

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "75128aab4b1b",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "000001",
         |      "created": {
         |        "C42": {
         |          "id": "$mailboxId",
         |          "isSubscribed":true,
         |          "myRights":{"mayAddItems":true,"mayCreateChild":true,"mayDelete":true,"mayReadItems":true,"mayRemoveItems":true,"mayRename":true,"maySetKeywords":true,"maySetSeen":true,"maySubmit":true},
         |          "totalEmails":0,
         |          "totalThreads":0,
         |          "unreadEmails":0,
         |          "unreadThreads":0
         |        }
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def mailboxSetShouldReturnCreatedAndNotCreatedWhenOneWithOnlyNameAndOneWithoutName(server: GuiceJamesServer): Unit = {
    val request=
      """
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "create": {
        |                    "C42": {
        |                      "name": "myMailbox"
        |                    },
        |                    "C43": {
        |                    }
        |                }
        |           },
        |    "c1"
        |       ]
        |   ]
        |}
        |""".stripMargin

    val response: String =
      `given`
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

    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId("#private", BOB.asString(), "myMailbox")
      .serialize()

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "75128aab4b1b",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "000001",
         |      "created": {
         |        "C42": {
         |          "id": "$mailboxId",
         |          "isSubscribed":true,
         |          "myRights":{"mayAddItems":true,"mayCreateChild":true,"mayDelete":true,"mayReadItems":true,"mayRemoveItems":true,"mayRename":true,"maySetKeywords":true,"maySetSeen":true,"maySubmit":true},
         |          "totalEmails":0,
         |          "totalThreads":0,
         |          "unreadEmails":0,
         |          "unreadThreads":0
         |        }
         |      },
         |      "notCreated": {
         |        "C43": {
         |          "type": "invalidArguments",
         |          "description": "Missing '/name' property in mailbox object"
         |        }
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def mailboxSetShouldCreateMailboxWhenNameAndParentId(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId  = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "parentMailbox"))
    val request=
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "create": {
        |                    "C42": {
        |                      "name": "childMailbox",
        |                      "parentId":"${mailboxId.serialize}"
        |                    }
        |                }
        |           },
        |    "c1"
        |       ]
        |   ]
        |}
        |""".stripMargin

      `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(request)
      .when
        .post
      .`then`
        .log().ifValidationFails()
        .statusCode(SC_OK)
        .contentType(JSON)

    Assertions.assertThatCode(() => server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId("#private", BOB.asString(), "parentMailbox.childMailbox")).doesNotThrowAnyException()
  }

  @Test
  def mailboxSetShouldNotCreateMailboxWhenParentIdNotFound(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId  = randomMailboxId
    val request=
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "create": {
        |                    "C42": {
        |                      "name": "childMailbox",
        |                      "parentId":"${mailboxId.serialize}"
        |                    }
        |                }
        |           },
        |    "c1"
        |       ]
        |   ]
        |}
        |""".stripMargin

     val response = `given`
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
         |  "sessionState": "75128aab4b1b",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "000001",
         |      "notCreated": {
         |        "C42": {
         |          "type": "invalidArguments",
         |          "description": "${mailboxId.serialize()} can not be found",
         |          "properties":{"value":["parentId"]}
         |        }
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }
}
