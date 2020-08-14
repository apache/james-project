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

import java.nio.charset.StandardCharsets

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured._
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option
import net.javacrumbs.jsonunit.core.internal.Options
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.MailboxACL.Right
import org.apache.james.mailbox.model.{MailboxACL, MailboxId, MailboxPath}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.{ACLProbeImpl, MailboxProbeImpl}
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions
import org.hamcrest.Matchers.{equalTo, hasSize}
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
    val request =
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
    val request =
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
    val request =
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
         |          "description": "'/parentId' property in mailbox object is not valid: Predicate isEmpty() did not fail."
         |        }
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def mailboxSetShouldCreateMailboxWhenOnlyName(server: GuiceJamesServer): Unit = {
    val request =
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
  def mailboxSetShouldSubscribeMailboxWhenRequired(server: GuiceJamesServer): Unit = {
    val request =
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
        |                      "name": "myMailbox",
        |                      "isSubscribed": true
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

    Assertions.assertThat(server.getProbe(classOf[MailboxProbeImpl])
      .listSubscriptions(BOB.asString())).contains("myMailbox")
  }

  @Test
  def mailboxSetShouldNotSubscribeMailboxWhenRequired(server: GuiceJamesServer): Unit = {
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
        |                      "name": "myMailbox",
        |                      "isSubscribed": false
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

    Assertions.assertThat(server.getProbe(classOf[MailboxProbeImpl])
      .listSubscriptions(BOB.asString())).doesNotContain("myMailbox")
  }

  @Test
  def mailboxSetShouldSubscribeMailboxByDefault(server: GuiceJamesServer): Unit = {
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

    Assertions.assertThat(server.getProbe(classOf[MailboxProbeImpl])
      .listSubscriptions(BOB.asString())).contains("myMailbox")
  }

  @Test
  def mailboxGetShouldAllowTheUseOfCreationIds(server: GuiceJamesServer): Unit = {
    val request =
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
        |       ],
        |       ["Mailbox/get",
        |         {
        |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |           "ids": ["#C42"]
        |          },
        |       "c2"]
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

    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId("#private", BOB.asString(), "myMailbox")
      .serialize()

    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "75128aab4b1b",
         |	"methodResponses": [
         |		["Mailbox/set", {
         |			"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |			"newState": "000001",
         |			"created": {
         |				"C42": {
         |					"id": "$mailboxId",
         |					"totalEmails": 0,
         |					"unreadEmails": 0,
         |					"totalThreads": 0,
         |					"unreadThreads": 0,
         |					"myRights": {
         |						"mayReadItems": true,
         |						"mayAddItems": true,
         |						"mayRemoveItems": true,
         |						"maySetSeen": true,
         |						"maySetKeywords": true,
         |						"mayCreateChild": true,
         |						"mayRename": true,
         |						"mayDelete": true,
         |						"maySubmit": true
         |					},
         |					"isSubscribed": true
         |				}
         |			}
         |		}, "c1"],
         |		["Mailbox/get", {
         |			"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |			"state": "000001",
         |			"list": [{
         |				"id": "$mailboxId",
         |				"name": "myMailbox",
         |				"sortOrder": 1000,
         |				"totalEmails": 0,
         |				"unreadEmails": 0,
         |				"totalThreads": 0,
         |				"unreadThreads": 0,
         |				"myRights": {
         |					"mayReadItems": true,
         |					"mayAddItems": true,
         |					"mayRemoveItems": true,
         |					"maySetSeen": true,
         |					"maySetKeywords": true,
         |					"mayCreateChild": true,
         |					"mayRename": true,
         |					"mayDelete": true,
         |					"maySubmit": true
         |				},
         |				"isSubscribed": true
         |			}],
         |      "notFound":[]
         |		}, "c2"]
         |	]
         |}""".stripMargin)
  }

  @Test
  def destroyShouldUnsubscribeMailboxes(server: GuiceJamesServer): Unit = {
    val request=
      """
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       ["Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "create": {
        |                    "C42": {
        |                      "name": "myMailbox"
        |                    }
        |                }
        |           },
        |    "c1"],
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "destroy": ["#C42"]
        |           },
        |    "c2"]
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

    Assertions.assertThat(server.getProbe(classOf[MailboxProbeImpl])
      .listSubscriptions(BOB.asString())).doesNotContain("myMailbox")
  }

  @Test
  def mailboxSetShouldReturnCreatedWhenOnlyName(server: GuiceJamesServer): Unit = {
    val request =
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
    val request =
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
    val request =
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
  def mailboxSetShouldNotCreateMailboxWhenParentIdNotFound(): Unit = {
    val mailboxId: MailboxId  = randomMailboxId
    val request =
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

  @Test
  def mailboxSetShouldNotCreateMailboxWhenNameExists(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "mailbox"))
    val request =
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
        |                      "name": "mailbox"
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
         |          "description": "Mailbox with name=#private:bob@domain.tld:mailbox already exists.",
         |          "properties":{"value":["name"]}
         |        }
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def mailboxSetShouldNotCreateMailboxWhenNameTooLong(): Unit = {
    val request =
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
        |                      "name": "${"a".repeat(201)}"
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
         |          "description": "Mailbox name exceeds maximum size of 200 characters",
         |          "properties":{"value":["name"]}
         |        }
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def mailboxSetShouldNotCreateChildMailboxWhenSharedParentMailbox(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.forUser(ANDRE, "mailbox")
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read, Right.CreateMailbox))
    val request =
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
         |          "type": "forbidden",
         |          "description": "Insufficient rights",
         |          "properties":{"value":["parentId"]}
         |        }
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def deleteShouldSucceedWhenMailboxExists(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "mailbox"))

    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "destroy": ["${mailboxId.serialize}"]
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
         |      "destroyed": ["${mailboxId.serialize}"]
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def deleteShouldRemoveExistingMailbox(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "mailbox"))

    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "destroy": ["${mailboxId.serialize}"]
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

    val response = `given`
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
               |        "ids": ["${mailboxId.serialize()}"]
               |      },
               |      "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`()
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [
         |        [
         |            "Mailbox/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |
         |                ],
         |                "notFound": [
         |                    "${mailboxId.serialize()}"
         |                ]
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def deleteShouldRemoveExistingMailboxes(server: GuiceJamesServer): Unit = {
    val mailboxId1: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "mailbox1"))
    val mailboxId2: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "mailbox2"))

    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "destroy": ["${mailboxId1.serialize}", "${mailboxId2.serialize}"]
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

    val response = `given`
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
               |        "ids": ["${mailboxId1.serialize()}", "${mailboxId2.serialize()}"]
               |      },
               |      "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`()
      .extract
      .body
      .asString

    assertThatJson(response)
      .withOptions(new Options(Option.IGNORING_ARRAY_ORDER))
      .isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [
         |        [
         |            "Mailbox/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "000001",
         |                "list": [
         |
         |                ],
         |                "notFound": [
         |                    "${mailboxId1.serialize()}", "${mailboxId2.serialize()}"
         |                ]
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def deleteShouldFailWhenMailboxDoesNotExist(): Unit = {
    val mailboxId = randomMailboxId
    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "destroy": ["${mailboxId.serialize()}"]
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
         |      "notDestroyed": {
         |        "${mailboxId.serialize()}": {
         |          "type": "notFound",
         |          "description": "${mailboxId.serialize()} can not be found"
         |        }
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def deleteShouldFailWhenMailboxIsNotEmpty(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "mailbox1"))
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.forUser(BOB, "mailbox1"), AppendCommand.from(message))

    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "destroy": ["${mailboxId.serialize()}"]
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
         |      "notDestroyed": {
         |        "${mailboxId.serialize()}": {
         |          "type": "mailboxHasEmail",
         |          "description": "${mailboxId.serialize()} is not empty"
         |        }
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def deleteShouldFailWhenMailboxHasChild(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "mailbox1"))
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "mailbox1.mailbox2"))

    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "destroy": ["${mailboxId.serialize()}"]
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
         |      "notDestroyed": {
         |        "${mailboxId.serialize()}": {
         |          "type": "mailboxHasChild",
         |          "description": "${mailboxId.serialize()} has child mailboxes"
         |        }
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def deleteShouldFailWhenNotEnoughRights(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.forUser(ANDRE, "mailbox")
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read, Right.CreateMailbox))

    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "destroy": ["${mailboxId.serialize()}"]
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
         |      "notDestroyed": {
         |        "${mailboxId.serialize()}": {
         |          "type": "notFound",
         |          "description": "#private:andre@domain.tld:mailbox"
         |        }
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def deleteShouldHandleInvalidMailboxId(): Unit = {
    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "destroy": ["invalid"]
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

    val message: String = "invalid is not a mailboxId: For input string: \\\"invalid\\\""
    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "75128aab4b1b",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "000001",
         |      "notDestroyed": {
         |        "invalid": {
         |          "type": "invalidArguments",
         |          "description": "$message"
         |        }
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def deleteShouldAcceptCreationIdsWithinTheSameRequest(): Unit = {
    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       ["Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "create": {
        |                    "C42": {
        |                      "name": "myMailbox"
        |                    }
        |                }
        |           },
        |    "c1"],
        |       ["Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "destroy": ["#C42"]
        |           },
        |    "c2"]
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
         // We need to limit ourself to simple body assertions in order not to infer id allocation
         .body("methodResponses[0][1].created.C42.totalThreads", equalTo(0))
         .body("methodResponses[1][1].destroyed", hasSize(1))
  }

  @Test
  def createParentIdShouldAcceptCreationIdsWithinTheSameRequest(server: GuiceJamesServer): Unit = {
    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       ["Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "create": {
        |                    "C42": {
        |                      "name": "parent"
        |                    }
        |                }
        |           },
        |    "c1"],
        |       ["Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "create": {
        |                    "C43": {
        |                      "name": "child",
        |                      "parentId": "#C42"
        |                    }
        |                }
        |           },
        |    "c2"]
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

    val parentId: String = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId("#private", BOB.asString(), "parent")
      .serialize()
    val childId: String = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId("#private", BOB.asString(), "parent.child")
      .serialize()

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "75128aab4b1b",
         |    "methodResponses": [
         |        [
         |            "Mailbox/set",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "newState": "000001",
         |                "created": {
         |                    "C42": {
         |                        "id": "$parentId",
         |                        "totalEmails": 0,
         |                        "unreadEmails": 0,
         |                        "totalThreads": 0,
         |                        "unreadThreads": 0,
         |                        "myRights": {
         |                            "mayReadItems": true,
         |                            "mayAddItems": true,
         |                            "mayRemoveItems": true,
         |                            "maySetSeen": true,
         |                            "maySetKeywords": true,
         |                            "mayCreateChild": true,
         |                            "mayRename": true,
         |                            "mayDelete": true,
         |                            "maySubmit": true
         |                        },
         |                        "isSubscribed": true
         |                    }
         |                }
         |            },
         |            "c1"
         |        ],
         |        [
         |            "Mailbox/set",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "newState": "000001",
         |                "created": {
         |                    "C43": {
         |                        "id": "$childId",
         |                        "totalEmails": 0,
         |                        "unreadEmails": 0,
         |                        "totalThreads": 0,
         |                        "unreadThreads": 0,
         |                        "myRights": {
         |                            "mayReadItems": true,
         |                            "mayAddItems": true,
         |                            "mayRemoveItems": true,
         |                            "maySetSeen": true,
         |                            "maySetKeywords": true,
         |                            "mayCreateChild": true,
         |                            "mayRename": true,
         |                            "mayDelete": true,
         |                            "maySubmit": true
         |                        },
         |                        "isSubscribed": true
         |                    }
         |                }
         |            },
         |            "c2"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def creationIdReferencesShouldFailWhenWrongOrder(server: GuiceJamesServer): Unit = {
    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |      ["Mailbox/set",
        |          {
        |               "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |               "destroy": ["#C42"]
        |          },
        |   "c2"],
        |       ["Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "create": {
        |                    "C42": {
        |                      "name": "myMailbox"
        |                    }
        |                }
        |           },
        |    "c1"]
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

    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId("#private", BOB.asString(), "myMailbox")
      .serialize()

    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "75128aab4b1b",
         |	"methodResponses": [
         |		["Mailbox/set", {
         |			"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |			"newState": "000001",
         |			"notDestroyed": {
         |				"#C42": {
         |					"type": "invalidArguments",
         |					"description": "#C42 is not a mailboxId: ClientId(#C42) was not used in previously defined creationIds"
         |				}
         |			}
         |		}, "c2"],
         |		["Mailbox/set", {
         |			"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |			"newState": "000001",
         |			"created": {
         |				"C42": {
         |					"id": "$mailboxId",
         |					"totalEmails": 0,
         |					"unreadEmails": 0,
         |					"totalThreads": 0,
         |					"unreadThreads": 0,
         |					"myRights": {
         |						"mayReadItems": true,
         |						"mayAddItems": true,
         |						"mayRemoveItems": true,
         |						"maySetSeen": true,
         |						"maySetKeywords": true,
         |						"mayCreateChild": true,
         |						"mayRename": true,
         |						"mayDelete": true,
         |						"maySubmit": true
         |					},
         |					"isSubscribed": true
         |				}
         |			}
         |		}, "c1"]
         |	]
         |}""".stripMargin)
  }

  @Test
  def creationIdReferencesShouldFailWhenNone(server: GuiceJamesServer): Unit = {
    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |      ["Mailbox/set",
        |          {
        |               "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |               "destroy": ["#C42"]
        |          },
        |   "c2"]
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
         |	"sessionState": "75128aab4b1b",
         |	"methodResponses": [
         |		["Mailbox/set", {
         |			"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |			"newState": "000001",
         |			"notDestroyed": {
         |				"#C42": {
         |					"type": "invalidArguments",
         |					"description": "#C42 is not a mailboxId: ClientId(#C42) was not used in previously defined creationIds"
         |				}
         |			}
         |		}, "c2"]
         |	]
         |}""".stripMargin)
  }

  @Test
  def emptyCreationIdReferencesShouldFail(): Unit = {
    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |      ["Mailbox/set",
        |          {
        |               "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |               "destroy": ["#"]
        |          },
        |   "c2"]
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

    val message = "# is not a mailboxId: Left predicate of ((!(0 < 1) && !(0 > 255)) && \\\"\\\".matches(\\\"^[#a-zA-Z0-9-_]*$\\\")) failed: Predicate taking size() = 0 failed: Left predicate of (!(0 < 1) && !(0 > 255)) failed: Predicate (0 < 1) did not fail."
    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "75128aab4b1b",
         |	"methodResponses": [
         |		["Mailbox/set", {
         |			"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |			"newState": "000001",
         |			"notDestroyed": {
         |				"#": {
         |					"type": "invalidArguments",
         |					"description": "$message"
         |				}
         |			}
         |		}, "c2"]
         |	]
         |}""".stripMargin)
  }
}
