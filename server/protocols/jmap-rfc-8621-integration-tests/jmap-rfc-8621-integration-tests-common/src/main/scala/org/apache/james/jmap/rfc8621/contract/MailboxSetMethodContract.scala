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

import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeUnit

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured._
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option
import net.javacrumbs.jsonunit.core.internal.Options
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UuidState.INSTANCE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ANDRE, BOB, BOB_PASSWORD, CEDRIC, DAVID, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.jmap.{JmapGuiceProbe, MessageIdProbe}
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.MailboxACL.{EntryKey, Right}
import org.apache.james.mailbox.model.{MailboxACL, MailboxId, MailboxPath}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.{ACLProbeImpl, MailboxProbeImpl}
import org.apache.james.util.concurrency.ConcurrentTestRunner
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.{Assertions, SoftAssertions, ThrowingConsumer}
import org.awaitility.Awaitility
import org.hamcrest.Matchers.{equalTo, hasSize, not}
import org.junit.jupiter.api.{BeforeEach, RepeatedTest, Tag, Test}
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers
import reactor.netty.http.client.HttpClient
import sttp.capabilities.WebSockets
import sttp.client3.monad.IdMonad
import sttp.client3.okhttp.OkHttpSyncBackend
import sttp.client3.{Identity, SttpBackend, asWebSocket, basicRequest}
import sttp.model.Uri
import sttp.monad.MonadError
import sttp.ws.WebSocketFrame

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.MILLISECONDS
import scala.jdk.CollectionConverters._


trait MailboxSetMethodContract {
  private lazy val slowPacedPollInterval = Duration.ofMillis(100)
  private lazy val calmlyAwait = Awaitility.`with`
    .pollInterval(slowPacedPollInterval)
    .and.`with`.pollDelay(slowPacedPollInterval)
    .await
  private lazy val awaitAtMostTenSeconds = calmlyAwait.atMost(10, TimeUnit.SECONDS)
  private lazy val backend: SttpBackend[Identity, WebSockets] = OkHttpSyncBackend()
  private lazy implicit val monadError: MonadError[Identity] = IdMonad

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
  def errorInvalidMailboxIdMessage(value: String): String

  @Test
  def mailboxSetShouldFailWhenWrongAccountId(server: GuiceJamesServer): Unit = {
    val request =
      """
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "unknownAccountId",
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
  def updateShouldFailWhenModifyingRole(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "INBOX"))
    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       ["Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "role": "Draft"
         |                    }
         |                }
         |           },
         |    "c1"]]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId.serialize}": {
         |          "type": "invalidArguments",
         |          "description": "Can not modify server-set properties",
         |          "properties":["role"]
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenModifyingSortOrder(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "INBOX"))
    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       ["Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "sortOrder": 42
         |                    }
         |                }
         |           },
         |    "c1"]]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId.serialize}": {
         |          "type": "invalidArguments",
         |          "description": "Can not modify server-set properties",
         |          "properties":["sortOrder"]
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenModifyingQuotas(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "INBOX"))
    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:quota" ],
         |   "methodCalls": [
         |       ["Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "quotas": {}
         |                    }
         |                }
         |           },
         |    "c1"]]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId.serialize}": {
         |          "type": "invalidArguments",
         |          "description": "Can not modify server-set properties",
         |          "properties":["quotas"]
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenModifyingNamespace(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "INBOX"))
    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       ["Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "namespace": "Personal"
         |                    }
         |                }
         |           },
         |    "c1"]]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId.serialize}": {
         |          "type": "invalidArguments",
         |          "description": "Can not modify server-set properties",
         |          "properties":["namespace"]
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenModifyingMyRights(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "INBOX"))
    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       ["Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "myRights": {}
         |                    }
         |                }
         |           },
         |    "c1"]]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId.serialize}": {
         |          "type": "invalidArguments",
         |          "description": "Can not modify server-set properties",
         |          "properties":["myRights"]
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenModifyingNameToBlank(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "mailbox"))
    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       ["Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize()}": {
         |                      "name": "   "
         |                    }
         |                }
         |           },
         |    "c1"]]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId.serialize()}": {
         |          "type": "invalidArguments",
         |          "description": "'#private:bob@domain.tld:   ' has an empty part within its mailbox name considering . as a delimiter",
         |          "properties":["name"]
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def createShouldFailWhenNameWithDelimiter(): Unit = {
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
        |                      "name": "my.mailbox"
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notCreated": {
         |        "C42": {
         |          "type": "invalidArguments",
         |          "description": "The mailbox 'my.mailbox' contains an illegal character: '.'",
         |          "properties": ["name"]
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenModifyingUnreadThreads(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "INBOX"))
    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       ["Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "unreadThreads": 42
         |                    }
         |                }
         |           },
         |    "c1"]]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId.serialize}": {
         |          "type": "invalidArguments",
         |          "description": "Can not modify server-set properties",
         |          "properties":["unreadThreads"]
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenModifyingTotalThreads(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "INBOX"))
    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       ["Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "totalThreads": 42
         |                    }
         |                }
         |           },
         |    "c1"]]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId.serialize}": {
         |          "type": "invalidArguments",
         |          "description": "Can not modify server-set properties",
         |          "properties":["totalThreads"]
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenModifyingUnreadEmails(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "INBOX"))
    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       ["Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "unreadEmails": 42
         |                    }
         |                }
         |           },
         |    "c1"]]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId.serialize}": {
         |          "type": "invalidArguments",
         |          "description": "Can not modify server-set properties",
         |          "properties":["unreadEmails"]
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenModifyingTotalEmails(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "INBOX"))
    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       ["Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "totalEmails": 42
         |                    }
         |                }
         |           },
         |    "c1"]]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId.serialize}": {
         |          "type": "invalidArguments",
         |          "description": "Can not modify server-set properties",
         |          "properties":["totalEmails"]
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notCreated": {
         |        "C42": {
         |          "type": "invalidArguments",
         |          "description": "Missing '/name' property"
         |        }
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def mailboxSetShouldReturnNotCreatedWhenNameIsEmpty(): Unit = {
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
        |                      "name": ""
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notCreated": {
         |        "C42": {
         |          "type": "invalidArguments",
         |          "description": "'/name' property is not valid: Predicate isEmpty() did not fail."
         |        }
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def mailboxSetShouldReturnNotCreatedWhenNameIsBlank(): Unit = {
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
        |                      "name": "      "
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notCreated": {
         |        "C42": {
         |          "type": "invalidArguments",
         |          "description": "'#private:bob@domain.tld:      ' has an empty part within its mailbox name considering . as a delimiter",
         |          "properties":["name"]
         |        }
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notCreated": {
         |        "C42": {
         |          "type": "invalidArguments",
         |          "description": "Some unknown properties were specified",
         |          "properties": ["unknown"]
         |        }
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def mailboxSetShouldReturnNotCreatedWhenServerSetParameter(): Unit = {
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
        |                      "id": "what?"
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notCreated": {
         |        "C42": {
         |          "type": "invalidArguments",
         |          "description": "Some server-set properties were specified",
         |          "properties": ["id"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notCreated": {
         |        "C42": {
         |          "type": "invalidArguments",
         |          "description": "'/parentId' property is not valid: mailboxId does not match Id constraints: Predicate failed: '' contains some invalid characters. Should be [#a-zA-Z0-9-_] and no longer than 255 chars."
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
  @Tag(CategoryTags.BASIC_FEATURE)
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
  def createShouldNotReturnSubscribeWhenSpecified(server: GuiceJamesServer): Unit = {
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
      .serialize

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		["Mailbox/set", {
         |			"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |			"created": {
         |				"C42": {
         |					"id": "${mailboxId}",
         |          "sortOrder":1000,
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
         |					}
         |				}
         |			}
         |		}, "c1"]
         |	]
         |}""".stripMargin)
  }

  @Test
  def mailboxSetShouldNotSubscribeMailboxWhenRequired(server: GuiceJamesServer): Unit = {
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

    Assertions.assertThat(server.getProbe(classOf[MailboxProbeImpl])
      .listSubscriptions(BOB.asString())).contains("myMailbox")
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def mailboxSetCreationShouldHandleRights(server: GuiceJamesServer): Unit = {
    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "create": {
        |                    "C42": {
        |                      "name": "myMailbox",
        |                      "rights": {
        |                        "${ANDRE.asString()}": ["l", "r"]
        |                      }
        |                    }
        |                }
        |           },
        |    "c1"
        |       ],
        |       ["Mailbox/get",
        |         {
        |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |           "properties": ["id", "name", "rights"],
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
      .serialize

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState", "methodResponses[1][1].state")
      .isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		["Mailbox/set", {
         |			"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |			"created": {
         |				"C42": {
         |					"id": "$mailboxId",
         |          "sortOrder": 1000,
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
         |			"state": "${INSTANCE.value}",
         |			"list": [{
         |				"id": "$mailboxId",
         |				"name": "myMailbox",
         |				"rights": {
         |					"andre@domain.tld": ["l", "r"]
         |				}
         |			}],
         |      "notFound":[]
         |		}, "c2"]
         |	]
         |}""".stripMargin)
  }

  @Test
  def mailboxSetCreationShouldValidateRights(): Unit = {
    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "create": {
        |                    "C42": {
        |                      "name": "myMailbox",
        |                      "rights": {
        |                        "${ANDRE.asString()}": ["invalid"]
        |                      }
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		["Mailbox/set", {
         |			"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |			"notCreated": {
         |				"C42": {
         |					"type": "invalidArguments",
         |					"description": "'/rights/andre@domain.tld(0)' property is not valid: Rights must have size 1"
         |				}
         |			}
         |		}, "c1"]
         |	]
         |}""".stripMargin)
  }

  @Test
  def mailboxSetCreationShouldValidateRightsWhenWrongValue(): Unit = {
    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "create": {
        |                    "C42": {
        |                      "name": "myMailbox",
        |                      "rights": {
        |                        "${ANDRE.asString()}": ["z"]
        |                      }
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		["Mailbox/set", {
         |			"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |			"notCreated": {
         |				"C42": {
         |					"type": "invalidArguments",
         |					"description": "'/rights/andre@domain.tld(0)' property is not valid: Unknown right 'z'"
         |				}
         |			}
         |		}, "c1"]
         |	]
         |}""".stripMargin)
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
        |           "properties": ["id", "name"],
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
      .serialize

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState", "methodResponses[1][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "created": {
         |        "C42": {
         |          "id": "$mailboxId",
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
         |          "isSubscribed": true
         |        }
         |      }
         |    }, "c1"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "state": "${INSTANCE.value}",
         |      "list": [{
         |        "id": "$mailboxId",
         |        "name": "myMailbox"
         |      }],
         |      "notFound":[]
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def destroyShouldUnsubscribeMailboxes(server: GuiceJamesServer): Unit = {
    val request =
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
  @Tag(CategoryTags.BASIC_FEATURE)
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
      .serialize

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "created": {
         |        "C42": {
         |          "id": "$mailboxId",
         |          "sortOrder": 1000,
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
      .serialize

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "created": {
         |        "C42": {
         |          "id": "$mailboxId",
         |          "sortOrder": 1000,
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
         |          "description": "Missing '/name' property"
         |        }
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def mailboxCreationShouldReturnQuotaWhenUsingQuotaExtension(server: GuiceJamesServer): Unit = {
    val request =
      """
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:quota" ],
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
      .serialize

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "created": {
         |        "C42": {
         |          "id": "$mailboxId",
         |          "sortOrder":1000,
         |          "isSubscribed":true,
         |          "myRights":{"mayAddItems":true,"mayCreateChild":true,"mayDelete":true,"mayReadItems":true,"mayRemoveItems":true,"mayRename":true,"maySetKeywords":true,"maySetSeen":true,"maySubmit":true},
         |          "totalEmails":0,
         |          "totalThreads":0,
         |          "unreadEmails":0,
         |          "unreadThreads":0,
         |          "quotas": {
         |            "#private&bob@domain.tld": {
         |              "Storage": {
         |                "used": 0
         |              },
         |              "Message": {
         |                "used": 0
         |              }
         |            }
         |          }
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notCreated": {
         |        "C42": {
         |          "type": "invalidArguments",
         |          "description": "${mailboxId.serialize} can not be found",
         |          "properties":["parentId"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notCreated": {
         |        "C42": {
         |          "type": "invalidArguments",
         |          "description": "Mailbox with name=#private:bob@domain.tld:mailbox already exists.",
         |          "properties":["name"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notCreated": {
         |        "C42": {
         |          "type": "invalidArguments",
         |          "description": "Mailbox name exceeds maximum size of 200 characters",
         |          "properties":["name"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notCreated": {
         |        "C42": {
         |          "type": "forbidden",
         |          "description": "Insufficient rights",
         |          "properties":["parentId"]
         |        }
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
               |        "ids": ["${mailboxId.serialize}"]
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
      .whenIgnoringPaths("methodResponses[0][1].state")
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Mailbox/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "${INSTANCE.value}",
         |                "list": [
         |
         |                ],
         |                "notFound": [
         |                    "${mailboxId.serialize}"
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
               |        "ids": ["${mailboxId1.serialize}", "${mailboxId2.serialize}"]
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
      .whenIgnoringPaths("methodResponses[0][1].state")
      .withOptions(new Options(Option.IGNORING_ARRAY_ORDER))
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Mailbox/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "state": "${INSTANCE.value}",
         |                "list": [
         |
         |                ],
         |                "notFound": [
         |                    "${mailboxId1.serialize}", "${mailboxId2.serialize}"
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notDestroyed": {
         |        "${mailboxId.serialize}": {
         |          "type": "notFound",
         |          "description": "${mailboxId.serialize} can not be found"
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notDestroyed": {
         |        "${mailboxId.serialize}": {
         |          "type": "mailboxHasEmail",
         |          "description": "${mailboxId.serialize} is not empty"
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notDestroyed": {
         |        "${mailboxId.serialize}": {
         |          "type": "mailboxHasChild",
         |          "description": "${mailboxId.serialize} has child mailboxes"
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notDestroyed": {
         |        "${mailboxId.serialize}": {
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notDestroyed": {
         |        "invalid": {
         |          "type": "invalidArguments",
         |          "description": "${errorInvalidMailboxIdMessage("invalid")}"
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
      .serialize
    val childId: String = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId("#private", BOB.asString(), "parent.child")
      .serialize

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState",
        "methodResponses[1][1].newState", "methodResponses[1][1].oldState")
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Mailbox/set",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "created": {
         |                    "C42": {
         |                        "id": "$parentId",
         |                        "sortOrder": 1000,
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
         |                "created": {
         |                    "C43": {
         |                        "id": "$childId",
         |                        "sortOrder": 1000,
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
      .serialize

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState",
        "methodResponses[1][1].newState", "methodResponses[1][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notDestroyed": {
         |        "#C42": {
         |          "type": "invalidArguments",
         |          "description": "#C42 is not a mailboxId: #C42 was not used in previously defined creationIds"
         |        }
         |      }
         |    }, "c2"],
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "created": {
         |        "C42": {
         |          "id": "$mailboxId",
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
         |          "isSubscribed": true
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def creationIdReferencesShouldFailWhenNone(): Unit = {
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notDestroyed": {
         |        "#C42": {
         |          "type": "invalidArguments",
         |          "description": "#C42 is not a mailboxId: #C42 was not used in previously defined creationIds"
         |        }
         |      }
         |    }, "c2"]
         |  ]
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

    val message = "# is not a mailboxId: # was not used in previously defined creationIds"
    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notDestroyed": {
         |        "#": {
         |          "type": "invalidArguments",
         |          "description": "$message"
         |        }
         |      }
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def updateShouldRenameMailboxes(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "mailbox1"))

    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |      ["Mailbox/set",
        |          {
        |               "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |               "update": {
        |                 "${mailboxId.serialize}" : {
        |                   "name": "newName"
        |                 }
        |               }
        |          },
        |   "c2"],
        |      ["Mailbox/get",
        |         {
        |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |           "properties": ["id", "name"],
        |           "ids": ["${mailboxId.serialize}"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[1][1].state", "methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${mailboxId.serialize}": {}
         |      }
         |    }, "c2"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "state": "${INSTANCE.value}",
         |      "list": [{
         |        "id": "${mailboxId.serialize}",
         |        "name": "newName"
         |      }],
         |      "notFound": []
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def updateShouldRenameMailboxesWithManyChildren(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "mailbox1"))
    SFlux.range(0, 100)
      .concatMap(i =>  SMono.fromCallable(() => server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, s"mailbox1.$i"))))
      .asJava()
      .blockLast()

    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |      ["Mailbox/set",
        |          {
        |               "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |               "update": {
        |                 "${mailboxId.serialize}" : {
        |                   "name": "newName"
        |                 }
        |               }
        |          },
        |   "c2"],
        |      ["Mailbox/get",
        |         {
        |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |           "properties": ["id", "name"],
        |           "ids": ["${mailboxId.serialize}"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[1][1].state", "methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${mailboxId.serialize}": {}
         |      }
         |    }, "c2"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "state": "${INSTANCE.value}",
         |      "list": [{
         |        "id": "${mailboxId.serialize}",
         |        "name": "newName"
         |      }],
         |      "notFound": []
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldAcceptCreationIds(server: GuiceJamesServer): Unit = {
    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       ["Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "create": {
        |                    "C43": {
        |                      "name": "mailbox"
        |                    }
        |                }
        |           },
        |    "c1"],
        |      ["Mailbox/set",
        |          {
        |               "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |               "update": {
        |                 "#C43" : {
        |                   "name": "newName"
        |                 }
        |               }
        |          },
        |     "c2"]
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
      .getMailboxId("#private", BOB.asString(), "newName")
      .serialize

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState",
        "methodResponses[1][1].oldState", "methodResponses[1][1].newState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "created": {
         |        "C43": {
         |          "id": "$mailboxId",
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
         |          "isSubscribed": true
         |        }
         |      }
         |    }, "c1"],
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "$mailboxId": {}
         |      }
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def updateShouldRenameSubscriptions(server: GuiceJamesServer): Unit = {
    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       ["Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "create": {
        |                    "C43": {
        |                      "name": "mailbox"
        |                    }
        |                }
        |           },
        |    "c1"],
        |      ["Mailbox/set",
        |          {
        |               "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |               "update": {
        |                 "#C43" : {
        |                   "name": "newName"
        |                 }
        |               }
        |          },
        |     "c2"]
        |   ]
        |}
        |""".stripMargin

    `with`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
      .post

    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listSubscriptions(BOB.asString()))
      .contains("newName")
    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listSubscriptions(BOB.asString()))
      .doesNotContain("mailbox")
  }

  @Test
  def updateShouldFailWhenTargetMailboxAlreadyExist(server: GuiceJamesServer): Unit = {
    val mailboxId1: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "previousName"))
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "newName"))
    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       ["Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "update": {
        |                    "${mailboxId1.serialize}": {
        |                      "name": "newName"
        |                    }
        |                }
        |           },
        |    "c1"]]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId1.serialize}": {
         |          "type": "invalidArguments",
         |          "description": "Mailbox with name=#private:bob@domain.tld:newName already exists.",
         |          "properties": ["name"]
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenWrongJsonObject(server: GuiceJamesServer): Unit = {
    val mailboxId1: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "previousName"))
    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       ["Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "update": {
        |                    "${mailboxId1.serialize}": {
        |                      "name": ["newName"]
        |                    }
        |                }
        |           },
        |    "c1"]]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId1.serialize}": {
         |          "type": "invalidArguments",
         |          "description": "Expecting a JSON string as an argument",
         |          "properties": ["name"]
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenUnknownProperty(server: GuiceJamesServer): Unit = {
    val mailboxId1: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "previousName"))
    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       ["Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "update": {
        |                    "${mailboxId1.serialize}": {
        |                      "unknown": "newValue"
        |                    }
        |                }
        |           },
        |    "c1"]]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId1.serialize}": {
         |          "type": "invalidArguments",
         |          "description": "unknown property do not exist thus cannot be updated",
         |          "properties": ["unknown"]
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenPropertyStartWithImplicitSlash(server: GuiceJamesServer): Unit = {
    val mailboxId1: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "previousName"))
    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       ["Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "update": {
        |                    "${mailboxId1.serialize}": {
        |                      "/name": "newValue"
        |                    }
        |                }
        |           },
        |    "c1"]]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId1.serialize}": {
         |          "type": "invalidArguments",
         |          "description": "/name property do not exist thus cannot be updated",
         |          "properties": ["/name"]
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenEmptyProperty(server: GuiceJamesServer): Unit = {
    val mailboxId1: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "previousName"))
    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       ["Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "update": {
        |                    "${mailboxId1.serialize}": {
        |                      "": "newValue"
        |                    }
        |                }
        |           },
        |    "c1"]]
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

    val message = "Invalid property specified in a patch object: Predicate isEmpty() did not fail."
    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId1.serialize}": {
         |          "type": "invalidPatch",
         |          "description": "$message"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenMailboxNameIsTooLong(server: GuiceJamesServer): Unit = {
    val mailboxId1: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "previousName"))
    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       ["Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "update": {
        |                    "${mailboxId1.serialize}": {
        |                      "name": "${"a".repeat(201)}"
        |                    }
        |                }
        |           },
        |    "c1"]]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId1.serialize}": {
         |          "type": "invalidArguments",
         |          "description": "Mailbox name exceeds maximum size of 200 characters",
         |          "properties": ["name"]
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenMailboxNotFound(): Unit = {
    val mailboxId1: MailboxId = randomMailboxId
    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       ["Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "update": {
        |                    "${mailboxId1.serialize}": {
        |                      "name": "newName"
        |                    }
        |                }
        |           },
        |    "c1"]]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId1.serialize}": {
         |          "type": "notFound",
         |          "description": "${mailboxId1.serialize} can not be found"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldNotRenameDelegatedMailboxes(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.forUser(ANDRE, "previousName")
    val mailboxId1: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, BOB.asString, MailboxACL.FULL_RIGHTS)
    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       ["Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "update": {
        |                    "${mailboxId1.serialize}": {
        |                      "name": "newName"
        |                    }
        |                }
        |           },
        |    "c1"]]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId1.serialize}": {
         |          "type": "notFound",
         |          "description": "#private:andre@domain.tld:previousName"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldNotRenameSystemMailboxes(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "INBOX"))
    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       ["Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "update": {
        |                    "${mailboxId.serialize}": {
        |                      "name": "newName"
        |                    }
        |                }
        |           },
        |    "c1"]]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId.serialize}": {
         |          "type": "invalidArguments",
         |          "description": "Invalid change to a system mailbox",
         |          "properties":["name"]
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def destroyShouldNotRemoveSystemMailboxes(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "INBOX"))
    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       ["Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "destroy": ["${mailboxId.serialize}"]
        |           },
        |    "c1"]]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notDestroyed": {
         |        "${mailboxId.serialize}": {
         |          "type": "invalidArguments",
         |          "description": "System mailboxes cannot be destroyed"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def nameUpdatesShouldNotAffectParentId(server: GuiceJamesServer): Unit = {
    val parentId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "parent"))
    val childId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "parent.oldChild"))
    val request=
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "update": {
        |                    "${childId.serialize}": {
        |                      "name": "newChild"
        |                    }
        |                }
        |           },
        |    "c1"
        |       ],
        |       ["Mailbox/get",
        |         {
        |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |           "properties": ["id", "name", "parentId"],
        |           "ids": ["${childId.serialize}"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState", "methodResponses[1][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${childId.serialize}": {}
         |      }
         |    }, "c1"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "state": "${INSTANCE.value}",
         |      "list": [{
         |        "id": "${childId.serialize}",
         |        "name": "newChild",
         |        "parentId": "${parentId.serialize}"
         |      }],
         |      "notFound": []
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def parentRenameShouldNotAffectChild(server: GuiceJamesServer): Unit = {
    val parentId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "parent"))
    val childId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "parent.child"))
    val request=
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "update": {
        |                    "${parentId.serialize}": {
        |                      "name": "newParent"
        |                    }
        |                }
        |           },
        |    "c1"
        |       ],
        |       ["Mailbox/get",
        |         {
        |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |           "properties": ["id", "name", "parentId"],
        |           "ids": ["${childId.serialize}"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState", "methodResponses[1][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${parentId.serialize}": {}
         |      }
         |    }, "c1"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [{
         |        "id": "${childId.serialize}",
         |        "name": "child",
         |        "parentId": "${parentId.serialize}"
         |      }],
         |      "notFound": []
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def deleteShouldSucceedWhenOnDestroyRemoveEmails(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "mailbox"))
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.forUser(BOB, "mailbox"), AppendCommand.from(message))

    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "destroy": ["${mailboxId.serialize}"],
         |                "onDestroyRemoveEmails": true
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "destroyed": ["${mailboxId.serialize}"]
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def deleteShouldRemoveMailboxWhenOnDestroyRemoveEmails(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "mailbox"))
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.forUser(BOB, "mailbox"), AppendCommand.from(message))

    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "destroy": ["${mailboxId.serialize}"],
         |                "onDestroyRemoveEmails": true
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
               |    "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [[
               |      "Mailbox/get",
               |      {
               |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |        "ids": ["${mailboxId.serialize}"]
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
      .whenIgnoringPaths("methodResponses[0][1].state")
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Mailbox/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "list": [],
         |                "notFound": ["${mailboxId.serialize}"]
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def deleteShouldRemoveMessagesWhenOnDestroyRemoveEmails(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "mailbox"))
    val message1: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail1", StandardCharsets.UTF_8)
      .build

    val message2: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail2", StandardCharsets.UTF_8)
      .build

    val messageId1 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.forUser(BOB, "mailbox"), AppendCommand.from(message1))
    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.forUser(BOB, "mailbox"), AppendCommand.from(message2))

    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "destroy": ["${mailboxId.serialize}"],
         |                "onDestroyRemoveEmails": true
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

    //Should be replaced with JMAP message query when it is available
    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(server.getProbe(classOf[MessageIdProbe]).getMessages(messageId1.getMessageId, BOB)).isEmpty()
      softly.assertThat(server.getProbe(classOf[MessageIdProbe]).getMessages(messageId2.getMessageId, BOB)).isEmpty()
    })
  }

  @Test
  def deleteShouldFailWhenMailboxIsNotEmptyAndOnDestroyRemoveEmailsIsFalse(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "mailbox"))
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.forUser(BOB, "mailbox"), AppendCommand.from(message))

    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "destroy": ["${mailboxId.serialize}"],
         |                "onDestroyRemoveEmails": false
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Mailbox/set",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notDestroyed": {
         |        "${mailboxId.serialize}": {
         |          "type": "mailboxHasEmail",
         |          "description": "${mailboxId.serialize} is not empty"
         |        }
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def updateShouldSubscribeMailboxes(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "mailbox"))

    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |      ["Mailbox/set",
         |          {
         |               "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |               "update": {
         |                 "${mailboxId.serialize}" : {
         |                   "isSubscribed": true
         |                 }
         |               }
         |          },
         |   "c2"],
         |      ["Mailbox/get",
         |         {
         |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |           "properties": ["id", "isSubscribed"],
         |           "ids": ["${mailboxId.serialize}"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState",
        "methodResponses[1][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${mailboxId.serialize}": {}
         |      }
         |    }, "c2"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [{
         |        "id": "${mailboxId.serialize}",
         |        "isSubscribed": true
         |      }],
         |      "notFound": []
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldUnsubscribeMailboxes(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "mailbox"))

    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |      ["Mailbox/set",
         |          {
         |               "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |               "update": {
         |                 "${mailboxId.serialize}" : {
         |                   "isSubscribed": true
         |                 }
         |               }
         |          },
         |   "c2"],
         |      ["Mailbox/set",
         |          {
         |               "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |               "update": {
         |                 "${mailboxId.serialize}" : {
         |                   "isSubscribed": false
         |                 }
         |               }
         |          },
         |   "c3"],
         |      ["Mailbox/get",
         |         {
         |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |           "properties": ["id", "isSubscribed"],
         |           "ids": ["${mailboxId.serialize}"]
         |          },
         |       "c4"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState",
        "methodResponses[1][1].oldState", "methodResponses[1][1].newState",
        "methodResponses[2][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${mailboxId.serialize}": {}
         |      }
         |    }, "c2"],
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${mailboxId.serialize}": {}
         |      }
         |    }, "c3"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [{
         |        "id": "${mailboxId.serialize}",
         |        "isSubscribed": false
         |      }],
         |      "notFound": []
         |    }, "c4"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldSubscribeMailboxesWhenNull(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "mailbox"))

    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |      ["Mailbox/set",
         |          {
         |               "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |               "update": {
         |                 "${mailboxId.serialize}" : {
         |                   "isSubscribed": null
         |                 }
         |               }
         |          },
         |   "c3"],
         |      ["Mailbox/get",
         |         {
         |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |           "properties": ["id", "isSubscribed"],
         |           "ids": ["${mailboxId.serialize}"]
         |          },
         |       "c4"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState",
        "methodResponses[1][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${mailboxId.serialize}": {}
         |      }
         |    }, "c3"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [{
         |        "id": "${mailboxId.serialize}",
         |        "isSubscribed": true
         |      }],
         |      "notFound": []
         |    }, "c4"]
         |  ]
         |}""".stripMargin)
  }


  @Test
  def updateShouldSubscribeDelegatedMailboxes(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.forUser(ANDRE, "mailbox")
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(path)
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Lookup))

    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares" ],
         |   "methodCalls": [
         |      ["Mailbox/set",
         |          {
         |               "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |               "update": {
         |                 "${mailboxId.serialize}" : {
         |                   "isSubscribed": true
         |                 }
         |               }
         |          },
         |   "c3"],
         |      ["Mailbox/get",
         |         {
         |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |           "properties": ["id", "isSubscribed", "namespace"],
         |           "ids": ["${mailboxId.serialize}"]
         |          },
         |       "c4"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[1][1].state", "methodResponses[0][1].oldState", "methodResponses[0][1].newState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${mailboxId.serialize}": {}
         |      }
         |    }, "c3"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [{
         |        "id": "${mailboxId.serialize}",
         |        "isSubscribed": true,
         |        "namespace": "Delegated[andre@domain.tld]"
         |      }],
         |      "notFound": []
         |    }, "c4"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldUnsubscribeDelegatedMailboxes(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.forUser(ANDRE, "mailbox")
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(path)
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Lookup))

    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares" ],
         |   "methodCalls": [
         |      ["Mailbox/set",
         |          {
         |               "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |               "update": {
         |                 "${mailboxId.serialize}" : {
         |                   "isSubscribed": true
         |                 }
         |               }
         |          },
         |   "c2"],
         |      ["Mailbox/set",
         |          {
         |               "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |               "update": {
         |                 "${mailboxId.serialize}" : {
         |                   "isSubscribed": false
         |                 }
         |               }
         |          },
         |   "c3"],
         |      ["Mailbox/get",
         |         {
         |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |           "properties": ["id", "isSubscribed", "namespace"],
         |           "ids": ["${mailboxId.serialize}"]
         |          },
         |       "c4"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState",
        "methodResponses[1][1].oldState", "methodResponses[1][1].newState",
        "methodResponses[2][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${mailboxId.serialize}": {}
         |      }
         |    }, "c2"],
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${mailboxId.serialize}": {}
         |      }
         |    }, "c3"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [{
         |        "id": "${mailboxId.serialize}",
         |        "isSubscribed": false,
         |        "namespace": "Delegated[andre@domain.tld]"
         |      }],
         |      "notFound": []
         |    }, "c4"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def subscribeChildMailboxShouldPropagateSubscriptionToParentMailboxes(server: GuiceJamesServer): Unit = {
    val parentMailboxPath = MailboxPath.forUser(BOB, "parent")
    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(parentMailboxPath)
    val childMailboxPath = MailboxPath.forUser(BOB, "parent.child")
    val childMailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(childMailboxPath)

    assertThat(server.getProbe(classOf[MailboxProbeImpl])
      .listSubscriptions(BOB.asString()))
      .doesNotContain(childMailboxPath.getName(), parentMailboxPath.getName())

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |	"methodCalls": [
         |		["Mailbox/set",
         |			{
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"update": {
         |					"${childMailboxId.serialize}": {
         |						"isSubscribed": true
         |					}
         |				}
         |			},
         |			"c3"
         |		]
         |	]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)

    awaitAtMostTenSeconds.untilAsserted(() => assertThat(server.getProbe(classOf[MailboxProbeImpl])
      .listSubscriptions(BOB.asString()))
      .contains(childMailboxPath.getName(), parentMailboxPath.getName()))
  }

  @Test
  def unsubscribeParentMailboxShouldPropagateUnsubscriptionToChildrenMailboxes(server: GuiceJamesServer): Unit = {
    val parentMailboxPath = MailboxPath.forUser(BOB, "parent")
    val parentMailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(parentMailboxPath)
    val childMailboxPath = MailboxPath.forUser(BOB, "parent.child")
    val childMailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(childMailboxPath)

    // subscribe to both parent and child mailboxes
    val subscribeToChildMailboxRequest =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |	"methodCalls": [
         |		["Mailbox/set",
         |			{
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"update": {
         |					"${childMailboxId.serialize}": {
         |						"isSubscribed": true
         |					}
         |				}
         |			},
         |			"c3"
         |		]
         |	]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(subscribeToChildMailboxRequest)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)

    awaitAtMostTenSeconds.untilAsserted(() => assertThat(server.getProbe(classOf[MailboxProbeImpl])
      .listSubscriptions(BOB.asString()))
      .contains(childMailboxPath.getName(), parentMailboxPath.getName()))

    // unsubscribe parent mailbox
    val unsubscribeToParentMailboxRequest =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |	"methodCalls": [
         |		["Mailbox/set",
         |			{
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"update": {
         |					"${parentMailboxId.serialize}": {
         |						"isSubscribed": false
         |					}
         |				}
         |			},
         |			"c3"
         |		]
         |	]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(unsubscribeToParentMailboxRequest)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)

    // should propagate unsubscription to child mailboxes
    awaitAtMostTenSeconds.untilAsserted(() => assertThat(server.getProbe(classOf[MailboxProbeImpl])
      .listSubscriptions(BOB.asString()))
      .doesNotContain(childMailboxPath.getName(), parentMailboxPath.getName()))
  }

  @Test
  def updateShouldFailWhenMailboxNameContainsDelimiter(server: GuiceJamesServer): Unit = {
    val mailboxId1: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "previousName"))
    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       ["Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId1.serialize}": {
         |                      "name": "a.b"
         |                    }
         |                }
         |           },
         |    "c1"]]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId1.serialize}": {
         |          "type": "invalidArguments",
         |          "description": "The mailbox 'a.b' contains an illegal character: '.'",
         |          "properties": ["name"]
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldAllowDifferentIsSubscribedValuesWhenMailboxHaveTheSameName(server: GuiceJamesServer): Unit = {
    val andrePath = MailboxPath.forUser(ANDRE, "mailbox")
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val andreId: MailboxId = mailboxProbe.createMailbox(andrePath)
    val bobId: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "mailbox"))
    server.getProbe(classOf[ACLProbeImpl]).replaceRights(andrePath, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Lookup))

    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares" ],
         |   "methodCalls": [
         |      ["Mailbox/set",
         |          {
         |               "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |               "update": {
         |                 "${andreId.serialize}" : {
         |                   "isSubscribed": true
         |                 },
         |                 "${bobId.serialize}" : {
         |                   "isSubscribed": false
         |                 }
         |               }
         |          },
         |   "c2"],
         |      ["Mailbox/get",
         |         {
         |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |           "properties": ["id", "isSubscribed"],
         |           "ids": ["${andreId.serialize}"]
         |          },
         |       "c3"],
         |      ["Mailbox/get",
         |         {
         |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |           "properties": ["id", "isSubscribed"],
         |           "ids": ["${bobId.serialize}"]
         |          },
         |       "c4"],
         |      ["Mailbox/set",
         |          {
         |               "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |               "update": {
         |                 "${andreId.serialize}" : {
         |                   "isSubscribed": false
         |                 },
         |                 "${bobId.serialize}" : {
         |                   "isSubscribed": true
         |                 }
         |               }
         |          },
         |   "c5"],
         |      ["Mailbox/get",
         |         {
         |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |           "properties": ["id", "isSubscribed"],
         |           "ids": ["${andreId.serialize}"]
         |          },
         |       "c6"],
         |      ["Mailbox/get",
         |         {
         |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |           "properties": ["id", "isSubscribed"],
         |           "ids": ["${bobId.serialize}"]
         |          },
         |       "c7"]
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

    assertThatJson(response)
      .whenIgnoringPaths(
        "methodResponses[0][1].newState",
        "methodResponses[0][1].oldState",
        "methodResponses[2][1].state",
        "methodResponses[1][1].state",
        "methodResponses[3][1].newState",
        "methodResponses[3][1].oldState",
        "methodResponses[4][1].state",
        "methodResponses[5][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${andreId.serialize}": {},
         |        "${bobId.serialize}": {}
         |      }
         |    }, "c2"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [{
         |        "id": "${andreId.serialize}",
         |        "isSubscribed": true
         |      }],
         |      "notFound": []
         |    }, "c3"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [{
         |        "id": "${bobId.serialize}",
         |        "isSubscribed": false
         |      }],
         |      "notFound": []
         |    }, "c4"],
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${andreId.serialize}": {},
         |        "${bobId.serialize}": {}
         |      }
         |    }, "c5"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [{
         |        "id": "${andreId.serialize}",
         |        "isSubscribed": false
         |      }],
         |      "notFound": []
         |    }, "c6"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [{
         |        "id": "${bobId.serialize}",
         |        "isSubscribed": true
         |      }],
         |      "notFound": []
         |    }, "c7"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldUnsubscribeDelegatedMailboxesWhenNull(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.forUser(ANDRE, "mailbox")
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(path)
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Lookup))

    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares" ],
         |   "methodCalls": [
         |      ["Mailbox/set",
         |          {
         |               "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |               "update": {
         |                 "${mailboxId.serialize}" : {
         |                   "isSubscribed": true
         |                 }
         |               }
         |          },
         |   "c2"],
         |      ["Mailbox/set",
         |          {
         |               "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |               "update": {
         |                 "${mailboxId.serialize}" : {
         |                   "isSubscribed": null
         |                 }
         |               }
         |          },
         |   "c3"],
         |      ["Mailbox/get",
         |         {
         |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |           "properties": ["id", "isSubscribed", "namespace"],
         |           "ids": ["${mailboxId.serialize}"]
         |          },
         |       "c4"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState",
        "methodResponses[1][1].oldState", "methodResponses[1][1].newState",
        "methodResponses[2][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${mailboxId.serialize}": {}
         |      }
         |    }, "c2"],
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${mailboxId.serialize}": {}
         |      }
         |    }, "c3"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [{
         |        "id": "${mailboxId.serialize}",
         |        "isSubscribed": false,
         |        "namespace": "Delegated[andre@domain.tld]"
         |      }],
         |      "notFound": []
         |    }, "c4"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldNotAffectSubscriptionOfOthers(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.forUser(BOB, "mailbox")
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId: MailboxId = mailboxProbe
      .createMailbox(path)
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, ANDRE.asString, new MailboxACL.Rfc4314Rights(Right.Lookup))

    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares" ],
         |   "methodCalls": [
         |      ["Mailbox/set",
         |          {
         |               "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |               "update": {
         |                 "${mailboxId.serialize}" : {
         |                   "isSubscribed": true
         |                 }
         |               }
         |          },
         |   "c2"]
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

    assertThat(mailboxProbe.listSubscriptions(ANDRE.asString())).isEmpty()
  }

  @Test
  def updateShouldFailWhenInvalidIsSubscribedJSON(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "mailbox"))

    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |      ["Mailbox/set",
         |          {
         |               "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |               "update": {
         |                 "${mailboxId.serialize}" : {
         |                   "isSubscribed": "invalid"
         |                 }
         |               }
         |          },
         |   "c3"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId.serialize}": {
         |          "type": "invalidArguments",
         |          "description": "Expecting a JSON boolean as an argument",
         |          "properties": ["isSubscribed"]
         |        }
         |      }
         |    }, "c3"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def deleteShouldNotRemoveMessageWhenMailboxIsNotEmptyAndOnDestroyRemoveEmailsIsFalse(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "mailbox"))
    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.forUser(BOB, "mailbox"), AppendCommand.from(message))

    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "destroy": ["${mailboxId.serialize}"],
         |                "onDestroyRemoveEmails": false
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

    //Should be replaced with JMAP message query when it is available
    assertThat(server.getProbe(classOf[MessageIdProbe]).getMessages(messageId.getMessageId, BOB)).isNotEmpty
  }

  @Test
  def updateRightsResetShouldFailWhenOmittingCapability(server: GuiceJamesServer): Unit = {
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "mailbox"))
    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "sharedWith": {
         |                        "${ANDRE.asString()}":["r", "l"]
         |                      }
         |                    }
         |                }
         |           },
         |           "c1"
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Mailbox/set",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "notUpdated": {
         |                    "${mailboxId.serialize}": {
         |                        "type": "invalidArguments",
         |                        "description": "sharedWith property do not exist thus cannot be updated",
         |                        "properties": ["sharedWith"]
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def updateRightsShouldFailWhenOmittingCapability(server: GuiceJamesServer): Unit = {
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "mailbox"))
    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "sharedWith/${ANDRE.asString()}": ["r", "l"]
         |                    }
         |                }
         |           },
         |           "c1"
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Mailbox/set",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "notUpdated": {
         |                    "${mailboxId.serialize}": {
         |                        "type": "invalidArguments",
         |                        "description": "sharedWith/${ANDRE.asString()} property do not exist thus cannot be updated",
         |                        "properties": ["sharedWith/${ANDRE.asString()}"]
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def updateQuotasShouldFailWhenOmittingCapability(server: GuiceJamesServer): Unit = {
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "mailbox"))
    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "/quotas": "toto"
         |                    }
         |                }
         |           },
         |           "c1"
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Mailbox/set",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "notUpdated": {
         |                    "${mailboxId.serialize}": {
         |                        "type": "invalidArguments",
         |                        "description": "/quotas property do not exist thus cannot be updated",
         |                        "properties": ["/quotas"]
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def updateShouldAllowSettingRights(server: GuiceJamesServer): Unit = {
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "mailbox"))
    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "sharedWith": {
         |                        "${ANDRE.asString()}":["r", "l"]
         |                      }
         |                    }
         |                }
         |           },
         |    "c1"
         |       ],
         |       ["Mailbox/get",
         |         {
         |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |           "properties": ["id", "rights"],
         |           "ids": ["${mailboxId.serialize}"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState",
        "methodResponses[1][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${mailboxId.serialize}": {}
         |      }
         |    }, "c1"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [{
         |        "id": "${mailboxId.serialize}",
         |        "rights": {
         |          "${ANDRE.asString()}": ["l", "r"]
         |        }
         |      }],
         |      "notFound": []
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def updateShouldAllowResettingRights(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.forUser(BOB, "mailbox")
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, ANDRE.asString(), new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Administer))
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, CEDRIC.asString(), new MailboxACL.Rfc4314Rights(Right.Read, Right.Lookup))

    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "sharedWith": {
         |                        "${ANDRE.asString()}":["r", "l"],
         |                        "${DAVID.asString()}":["r", "l", "w"]
         |                      }
         |                    }
         |                }
         |           },
         |    "c1"
         |       ],
         |       ["Mailbox/get",
         |         {
         |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |           "properties": ["id", "rights"],
         |           "ids": ["${mailboxId.serialize}"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState",
        "methodResponses[1][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${mailboxId.serialize}": {}
         |      }
         |    }, "c1"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [{
         |        "id": "${mailboxId.serialize}",
         |        "rights": {
         |          "${ANDRE.asString()}": ["l", "r"],
         |          "${DAVID.asString()}": ["l", "r", "w"]
         |        }
         |      }],
         |      "notFound": []
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldNotResetRightsInSharedMailboxes(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.forUser(ANDRE, "mailbox")
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, BOB.asString(), new MailboxACL.Rfc4314Rights(Right.Lookup))

    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "sharedWith": {
         |                        "${DAVID.asString()}":["r", "l", "w"],
         |                        "${BOB.asString()}":["r", "l", "w", "a"]
         |                      }
         |                    }
         |                }
         |           },
         |    "c1"
         |       ],
         |       ["Mailbox/get",
         |         {
         |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |           "properties": ["id", "namespace", "rights"],
         |           "ids": ["${mailboxId.serialize}"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState",
        "methodResponses[1][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId.serialize}": {
         |          "type": "invalidArguments",
         |          "description": "Invalid change to a delegated mailbox"
         |        }
         |      }
         |    }, "c1"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [{
         |        "id": "${mailboxId.serialize}",
         |        "namespace": "Delegated[andre@domain.tld]",
         |        "rights": {
         |          "bob@domain.tld": ["l"]
         |        }
         |      }],
         |      "notFound": []
         |    }, "c2"]
         |  ]
         |}""".stripMargin)

    assertThat(server.getProbe(classOf[ACLProbeImpl]).retrieveRights(path)
      .getEntries)
      .doesNotContainKeys(EntryKey.createUserEntryKey(ANDRE))
  }

  @Test
  def updateShouldFailOnOthersMailbox(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.forUser(ANDRE, "mailbox")
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)

    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "sharedWith": {
         |                        "${DAVID.asString()}":["r", "l", "w"],
         |                        "${BOB.asString()}":["r", "l", "w", "a"]
         |                      }
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId.serialize}": {
         |          "type": "notFound",
         |          "description": "${mailboxId.serialize} can not be found"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)

    assertThat(server.getProbe(classOf[ACLProbeImpl]).retrieveRights(path)
      .getEntries)
      .doesNotContainKeys(EntryKey.createUserEntryKey(ANDRE))
  }

  @Test
  def updateShouldFailWhenInvalidRightReset(server: GuiceJamesServer): Unit = {
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "mailbox"))

    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "sharedWith":  "invalid"
         |                    }
         |                 }
         |           },
         |           "c1"
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId.serialize}": {
         |          "type": "invalidArguments",
         |          "description": "Specified value do not match the expected JSON format: List((,List(JsonValidationError(List(error.expected.jsobject),List()))))",
         |          "properties": ["sharedWith"]
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldAllowPerRightsSetting(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.forUser(BOB, "mailbox")
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, DAVID.asString(), new MailboxACL.Rfc4314Rights(Right.Lookup))

    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "sharedWith/${ANDRE.asString()}": ["r", "l"]
         |                    }
         |                }
         |           },
         |    "c1"
         |       ],
         |       ["Mailbox/get",
         |         {
         |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |           "properties": ["id", "rights"],
         |           "ids": ["${mailboxId.serialize}"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState",
        "methodResponses[1][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${mailboxId.serialize}": {}
         |      }
         |    }, "c1"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [{
         |        "id": "${mailboxId.serialize}",
         |        "rights": {
         |          "${ANDRE.asString()}": ["l", "r"],
         |          "${DAVID.asString()}": ["l"]
         |        }
         |      }],
         |      "notFound": []
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def partialRightsUpdateShouldFailWhenInvalidUsername(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.forUser(BOB, "mailbox")
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)

    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "sharedWith/invalid@invalid@${DOMAIN.asString()}": ["r", "l"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId.serialize}": {
         |          "type": "invalidPatch",
         |          "description": "Domain parts ASCII chars must be a-z A-Z 0-9 - or _"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def partialRightsUpdateShouldFailWhenInvalidRights(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.forUser(BOB, "mailbox")
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)

    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "sharedWith/${ANDRE.asString()}": ["p"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Mailbox/set",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "notUpdated": {
         |                    "${mailboxId.serialize}": {
         |                        "type": "invalidArguments",
         |                        "description": "Specified value do not match the expected JSON format: List(((0),List(JsonValidationError(List(Unknown right 'p'),List()))))",
         |                        "properties": [
         |                            "sharedWith/andre@domain.tld"
         |                        ]
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def rightsUpdateShouldFailWhenBothPartialAndReset(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.forUser(BOB, "mailbox")
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)

    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "sharedWith": {
         |                        "${DAVID.asString()}":["r", "l", "w"]
         |                      },
         |                      "sharedWith/${ANDRE.asString()}": ["r"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Mailbox/set",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "notUpdated": {
         |                    "${mailboxId.serialize}": {
         |                        "type": "invalidPatch",
         |                        "description": "Resetting rights and partial updates cannot be done in the same method call"
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def partialUpdateShouldFailOnOthersMailbox(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.forUser(ANDRE, "mailbox")
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)

    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                          "sharedWith/${BOB.asString()}": ["r"],
         |                          "sharedWith/${DAVID.asString()}": ["r"]
         |                      }
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId.serialize}": {
         |          "type": "notFound",
         |          "description": "${mailboxId.serialize} can not be found"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)

    assertThat(server.getProbe(classOf[ACLProbeImpl]).retrieveRights(path)
      .getEntries)
      .doesNotContainKeys(EntryKey.createUserEntryKey(DAVID))
  }

  @Test
  def partialUpdateShouldFailWhenDelegatedMailbox(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.forUser(ANDRE, "mailbox")
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, BOB.asString(), new MailboxACL.Rfc4314Rights(Right.Lookup))

    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                          "sharedWith/${BOB.asString()}": ["r"],
         |                          "sharedWith/${DAVID.asString()}": ["r"]
         |                      }
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId.serialize}": {
         |          "type": "invalidArguments",
         |          "description": "Invalid change to a delegated mailbox"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)

    assertThat(server.getProbe(classOf[ACLProbeImpl]).retrieveRights(path)
      .getEntries)
      .doesNotContainKeys(EntryKey.createUserEntryKey(DAVID))
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def updateShouldAllowParentIdChangeWhenTopLevelMailbox(server: GuiceJamesServer): Unit = {
    val parentId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "parent"))
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "mailbox"))
    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "parentId": "${parentId.serialize}"
         |                    }
         |                }
         |           },
         |    "c1"
         |       ],
         |       ["Mailbox/get",
         |         {
         |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |           "ids": ["${mailboxId.serialize}"],
         |           "properties": ["id", "name", "parentId"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState",
        "methodResponses[1][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${mailboxId.serialize}": {}
         |      }
         |    }, "c1"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [{
         |        "id": "${mailboxId.serialize}",
         |        "name": "mailbox",
         |        "parentId": "${parentId.serialize}"
         |      }],
         |      "notFound": []
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def parentIdUpdateSetToSelfShouldBeRejected(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "mailbox"))
    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "parentId": "${mailboxId.serialize}"
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Mailbox/set",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "notUpdated": {
         |                    "${mailboxId.serialize}": {
         |                        "type": "invalidArguments",
         |                        "description": "A mailbox parentId property can not be set to itself or one of its child",
         |                        "properties": [
         |                            "parentId"
         |                        ]
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def parentIdUpdateSetToAChildShouldBeRejected(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "mailbox"))
    val childId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "mailbox.child"))
    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "parentId": "${childId.serialize}"
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Mailbox/set",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "notUpdated": {
         |                    "${mailboxId.serialize}": {
         |                        "type": "invalidArguments",
         |                        "description": "A mailbox parentId property can not be set to itself or one of its child",
         |                        "properties": [
         |                            "parentId"
         |                        ]
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @RepeatedTest(20)
  def concurrencyChecksUponParentIdUpdate(server: GuiceJamesServer): Unit = {
    val mailboxId1: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "mailbox1"))
    val mailboxId2: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "mailbox2"))
    val request1 =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId1.serialize}": {
         |                      "parentId": "${mailboxId2.serialize}"
         |                    }
         |                }
         |           },
         |    "c1"
         |       ]
         |   ]
         |}
         |""".stripMargin
    val request2 =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId2.serialize}": {
         |                      "parentId": "${mailboxId1.serialize}"
         |                    }
         |                }
         |           },
         |    "c1"
         |       ]
         |   ]
         |}
         |""".stripMargin

    ConcurrentTestRunner.builder()
      .operation {
        case (threadNb, step) =>
          if ((threadNb + step) % 2 == 0) {
            `with`
              .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
              .body(request1)
              .post
          } else {
            `with`
              .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
              .body(request2)
              .post
          }
      }
      .threadCount(2)
      .operationCount(1)
      .runSuccessfullyWithin(Duration.ofSeconds(10))

    // Test a list all request
    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       ["Mailbox/get",
         |         {
         |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |           "ids": null,
         |           "properties": ["id", "name"]
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

    assertThatJson(response)
      .withOptions(new Options(Option.IGNORING_ARRAY_ORDER))
      .inPath("methodResponses[0][1].list")
      .isArray
      .contains(
        s"""{"id": "${mailboxId1.serialize}", "name":"mailbox1"}""",
        s"""{"id": "${mailboxId2.serialize}", "name":"mailbox2"}""")

    //Test retrieving these mailboxes specifically
    val requestSpecific =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       ["Mailbox/get",
         |         {
         |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |           "ids": ["${mailboxId1.serialize}", "${mailboxId2.serialize}"],
         |           "properties": ["id", "name"]
         |          },
         |       "c2"]
         |   ]
         |}
         |""".stripMargin

    val responseSpecific = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(requestSpecific)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(responseSpecific)
      .withOptions(new Options(Option.IGNORING_ARRAY_ORDER))
      .inPath("methodResponses[0][1].list")
      .isArray
      .contains(
        s"""{"id": "${mailboxId1.serialize}", "name":"mailbox1"}""",
        s"""{"id": "${mailboxId2.serialize}", "name":"mailbox2"}""")

    /*
    Data race happens within the hierarchical level upon mailbox rename,
    and might lead to missing intermediary mailboxes. This is because renaming
    mailboxes is not concurrent proof.

    This leads to a mailbox having no parentId in JMAP while in IMAP it has some.

    The impact is very low. Note that the mailbox can still be accessed, renamed and moved.
     */
  }

  @Test
  def updateShouldAllowParentIdChangeWhenChildMailbox(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "parent"))
    val newParentId: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "newParent"))
    val mailboxId: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "parent.mailbox"))
    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "parentId": "${newParentId.serialize}"
         |                    }
         |                }
         |           },
         |    "c1"
         |       ],
         |       ["Mailbox/get",
         |         {
         |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |           "ids": ["${mailboxId.serialize}"],
         |           "properties": ["id", "name", "parentId"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState",
        "methodResponses[1][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${mailboxId.serialize}": {}
         |      }
         |    }, "c1"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [{
         |        "id": "${mailboxId.serialize}",
         |        "name": "mailbox",
         |        "parentId": "${newParentId.serialize}"
         |      }],
         |      "notFound": []
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldAllowParentIdChangeWhenTopLevelMailboxAndNewName(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val parentId: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "parent"))
    val mailboxId: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "mailbox"))
    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "parentId": "${parentId.serialize}",
         |                      "name": "newName"
         |                    }
         |                }
         |           },
         |    "c1"
         |       ],
         |       ["Mailbox/get",
         |         {
         |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |           "ids": ["${mailboxId.serialize}"],
         |           "properties": ["id", "name", "parentId"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState",
        "methodResponses[1][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${mailboxId.serialize}": {}
         |      }
         |    }, "c1"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [{
         |        "id": "${mailboxId.serialize}",
         |        "name": "newName",
         |        "parentId": "${parentId.serialize}"
         |      }],
         |      "notFound": []
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldAllowParentIdChangeWhenChildMailboxAndNewName(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "parent"))
    val newParentId: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "newParent"))
    val mailboxId: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "parent.mailbox"))
    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "parentId": "${newParentId.serialize}",
         |                      "name": "newName"
         |                    }
         |                }
         |           },
         |    "c1"
         |       ],
         |       ["Mailbox/get",
         |         {
         |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |           "ids": ["${mailboxId.serialize}"],
         |           "properties": ["id", "name", "parentId"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState",
        "methodResponses[1][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${mailboxId.serialize}": {}
         |      }
         |    }, "c1"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [{
         |        "id": "${mailboxId.serialize}",
         |        "name": "newName",
         |        "parentId": "${newParentId.serialize}"
         |      }],
         |      "notFound": []
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldAllowParentIdDropWhenChildMailbox(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "parent"))
    mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "newParent"))
    val mailboxId: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "parent.mailbox"))
    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "parentId": null
         |                    }
         |                }
         |           },
         |    "c1"
         |       ],
         |       ["Mailbox/get",
         |         {
         |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |           "ids": ["${mailboxId.serialize}"],
         |           "properties": ["id", "name", "parentId"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[1][1].state", "methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${mailboxId.serialize}": {}
         |      }
         |    }, "c1"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [{
         |        "id": "${mailboxId.serialize}",
         |        "name": "mailbox"
         |      }],
         |      "notFound": []
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldAllowParentIdDropWhenTopLevelMailboxAndNewName(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "parent"))
    val mailboxId: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "mailbox"))
    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "parentId": null,
         |                      "name": "newName"
         |                    }
         |                }
         |           },
         |    "c1"
         |       ],
         |       ["Mailbox/get",
         |         {
         |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |           "ids": ["${mailboxId.serialize}"],
         |           "properties": ["id", "name", "parentId"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState",
        "methodResponses[1][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${mailboxId.serialize}": {}
         |      }
         |    }, "c1"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [{
         |        "id": "${mailboxId.serialize}",
         |        "name": "newName"
         |      }],
         |      "notFound": []
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldAllowParentIdDropWhenChildMailboxAndNewName(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "parent"))
    mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "newParent"))
    val mailboxId: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "parent.mailbox"))
    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "parentId": null,
         |                      "name": "newName"
         |                    }
         |                }
         |           },
         |    "c1"
         |       ],
         |       ["Mailbox/get",
         |         {
         |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |           "ids": ["${mailboxId.serialize}"],
         |           "properties": ["id", "name", "parentId"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState",
        "methodResponses[1][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${mailboxId.serialize}": {}
         |      }
         |    }, "c1"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [{
         |        "id": "${mailboxId.serialize}",
         |        "name": "newName"
         |      }],
         |      "notFound": []
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldAllowNameNoop(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "mailbox"))
    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "update": {
        |                    "${mailboxId.serialize}": {
        |                      "name": "mailbox"
        |                    }
        |                }
        |           },
        |    "c1"
        |       ],
        |       ["Mailbox/get",
        |         {
        |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |           "ids": ["${mailboxId.serialize}"],
        |           "properties": ["id", "name", "parentId"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState",
        "methodResponses[1][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${mailboxId.serialize}": {}
         |      }
         |    }, "c1"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [{
         |        "id": "${mailboxId.serialize}",
         |        "name": "mailbox"
         |      }],
         |      "notFound": []
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldAllowParentIdNoop(server: GuiceJamesServer): Unit = {
    val parentId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "parent"))
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "parent.mailbox"))
    val request =
      s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "update": {
        |                    "${mailboxId.serialize}": {
        |                      "parentId": "${parentId.serialize}"
        |                    }
        |                }
        |           },
        |    "c1"
        |       ],
        |       ["Mailbox/get",
        |         {
        |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |           "ids": ["${mailboxId.serialize}"],
        |           "properties": ["id", "name", "parentId"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState",
        "methodResponses[1][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${mailboxId.serialize}": {}
         |      }
         |    }, "c1"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [{
         |        "id": "${mailboxId.serialize}",
         |        "parentId": "${parentId.serialize}",
         |        "name": "mailbox"
         |      }],
         |      "notFound": []
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldAcceptCreationIdsForParentId(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "mailbox"))
    val request = s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "create": {
        |                    "C42": {
        |                        "name": "parent"
        |                    }
        |                }
        |           },
        |           "c1"
        |       ],
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "update": {
        |                    "${mailboxId.serialize}": {
        |                      "parentId": "#C42"
        |                    }
        |                }
        |           },
        |           "c2"
        |       ],
        |       ["Mailbox/get",
        |         {
        |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |           "ids": ["${mailboxId.serialize}"],
        |           "properties": ["id", "name", "parentId"]
        |          },
        |          "c3"
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

    val parentId: String = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId("#private", BOB.asString(), "parent")
      .serialize

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState",
        "methodResponses[1][1].oldState", "methodResponses[1][1].newState",
        "methodResponses[2][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "created": {
         |        "C42": {
         |          "id": "$parentId",
         |          "sortOrder":1000,
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
         |          "isSubscribed": true
         |        }
         |      }
         |    }, "c1"],
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${mailboxId.serialize}": {}
         |      }
         |    }, "c2"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [{
         |        "id": "${mailboxId.serialize}",
         |        "name": "mailbox",
         |        "parentId": "$parentId"
         |      }],
         |      "notFound": []
         |    }, "c3"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldRejectInvalidParentIdJsonPayload(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "mailbox"))
    val request = s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "update": {
        |                    "${mailboxId.serialize}": {
        |                      "parentId": ["invalid"]
        |                    }
        |                }
        |           },
        |           "c2"
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId.serialize}": {
         |          "type": "invalidArguments",
         |          "description": "Expecting a JSON string or null as an argument",
         |          "properties": ["parentId"]
         |        }
         |      }
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldRejectUnresolvableCreationIds(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "mailbox"))
    val request = s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "update": {
        |                    "${mailboxId.serialize}": {
        |                      "parentId": "#C42"
        |                    }
        |                }
        |           },
        |           "c2"
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId.serialize}": {
         |          "type": "invalidArguments",
         |          "description": "#C42 was not used in previously defined creationIds",
         |          "properties": ["parentId"]
         |        }
         |      }
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenParentIdNotFound(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "mailbox"))
    val parentId = randomMailboxId
    val request = s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "update": {
        |                    "${mailboxId.serialize}": {
        |                      "parentId": "${parentId.serialize}"
        |                    }
        |                }
        |           },
        |           "c2"
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId.serialize}": {
         |          "type": "notFound",
         |          "description": "${parentId.serialize} can not be found"
         |        }
         |      }
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenTargetMailboxAlreadyExists(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "mailbox"))
    val parentId = mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "parent"))
    mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "parent.mailbox"))
    val request = s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "update": {
        |                    "${mailboxId.serialize}": {
        |                      "parentId": "${parentId.serialize}"
        |                    }
        |                }
        |           },
        |           "c2"
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId.serialize}": {
         |          "type": "invalidArguments",
         |          "description": "Mailbox with name=#private:bob@domain.tld:parent.mailbox already exists.",
         |          "properties":["parentId"]
         |        }
         |      }
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenTargetMailboxAlreadyExistsAndBothPropertiesSpecified(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "mailbox"))
    val parentId = mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "parent"))
    mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "parent.newMailbox"))
    val request = s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "update": {
        |                    "${mailboxId.serialize}": {
        |                      "parentId": "${parentId.serialize}",
        |                      "name": "newMailbox"
        |                    }
        |                }
        |           },
        |           "c2"
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId.serialize}": {
         |          "type": "invalidArguments",
         |          "description": "Mailbox with name=#private:bob@domain.tld:parent.newMailbox already exists.",
         |          "properties":["name", "parentId"]
         |        }
         |      }
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenRenamingParentIdWithinADelegatedAccount(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val path = MailboxPath.forUser(ANDRE, "mailbox")
    val mailboxId: MailboxId = mailboxProbe.createMailbox(path)
    val parentPath = MailboxPath.forUser(ANDRE, "parent")
    val parentId = mailboxProbe.createMailbox(parentPath)

    val aCLProbeImpl = server.getProbe(classOf[ACLProbeImpl])
    aCLProbeImpl.replaceRights(path, BOB.asString, MailboxACL.FULL_RIGHTS)
    aCLProbeImpl.replaceRights(parentPath, BOB.asString, MailboxACL.FULL_RIGHTS)

    val request = s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "update": {
        |                    "${mailboxId.serialize}": {
        |                      "parentId": "${parentId.serialize}"
        |                    }
        |                }
        |           },
        |           "c2"
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId.serialize}": {
         |          "type": "notFound",
         |          "description": "#private:andre@domain.tld:parent.mailbox"
         |        }
         |      }
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenRenamingParentIdFromADelegatedAccount(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val path = MailboxPath.forUser(ANDRE, "mailbox")
    val mailboxId: MailboxId = mailboxProbe.createMailbox(path)
    val parentPath = MailboxPath.forUser(BOB, "parent")
    val parentId = mailboxProbe.createMailbox(parentPath)

    val aCLProbeImpl = server.getProbe(classOf[ACLProbeImpl])
    aCLProbeImpl.replaceRights(path, BOB.asString, MailboxACL.FULL_RIGHTS)

    val request = s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "update": {
        |                    "${mailboxId.serialize}": {
        |                      "parentId": "${parentId.serialize}"
        |                    }
        |                }
        |           },
        |           "c2"
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId.serialize}": {
         |          "type": "notFound",
         |          "description": "#private:andre@domain.tld:mailbox"
         |        }
         |      }
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenRenamingParentIdToADelegatedAccount(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val path = MailboxPath.forUser(BOB, "mailbox")
    val mailboxId: MailboxId = mailboxProbe.createMailbox(path)
    val parentPath = MailboxPath.forUser(ANDRE, "parent")
    val parentId = mailboxProbe.createMailbox(parentPath)

    val aCLProbeImpl = server.getProbe(classOf[ACLProbeImpl])
    aCLProbeImpl.replaceRights(parentPath, BOB.asString, MailboxACL.FULL_RIGHTS)

    val request = s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "update": {
        |                    "${mailboxId.serialize}": {
        |                      "parentId": "${parentId.serialize}"
        |                    }
        |                }
        |           },
        |           "c2"
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notUpdated": {
         |        "${mailboxId.serialize}": {
         |          "type": "notFound",
         |          "description": "#private:andre@domain.tld:parent.mailbox"
         |        }
         |      }
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def lookupRightShouldBePropagated(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val path = MailboxPath.forUser(BOB, "mailbox")
    val mailboxId: MailboxId = mailboxProbe.createMailbox(path)
    val parentPath = MailboxPath.forUser(BOB, "parent")
    val parentId = mailboxProbe.createMailbox(parentPath)

    val aCLProbeImpl = server.getProbe(classOf[ACLProbeImpl])
    aCLProbeImpl.replaceRights(path, ANDRE.asString, MailboxACL.FULL_RIGHTS)

    val request = s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "update": {
        |                    "${mailboxId.serialize}": {
        |                      "parentId": "${parentId.serialize}"
        |                    }
        |                }
        |           },
        |           "c1"
        |       ],
        |       ["Mailbox/get",
        |         {
        |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |           "ids": ["${parentId.serialize}"],
        |           "properties": ["id", "name", "parentId", "rights"]
        |          },
        |          "c2"]
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState", "methodResponses[1][1].state")
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Mailbox/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "updated": {
         |        "${mailboxId.serialize}": {}
         |      }
         |    }, "c1"],
         |    ["Mailbox/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "list": [{
         |        "id": "${parentId.serialize}",
         |        "name": "parent",
         |        "rights": {
         |          "${ANDRE.asString()}": ["l"]
         |        }
         |      }],
         |      "notFound": []
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updatingParentIdShouldSucceedWhenMailboxHasAChild(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "mailbox"))
    mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "mailbox.child"))
    val parentId: MailboxId = mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "parent"))

    val request = s"""
        |{
        |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
        |   "methodCalls": [
        |       [
        |           "Mailbox/set",
        |           {
        |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |                "update": {
        |                    "${mailboxId.serialize}": {
        |                      "parentId": "${parentId.serialize}"
        |                    }
        |                }
        |           },
        |           "c1"
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Mailbox/set",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "updated": {
         |                    "${mailboxId.serialize}": {}
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def mailboxSetShouldReturnUnknownMethodWhenMissingOneCapability(): Unit = {
    val mailboxId = randomMailboxId

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [ "urn:ietf:params:jmap:mail" ],
               |  "methodCalls": [[
               |     "Mailbox/set",
               |     {
               |       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |       "update": {
               |         "${mailboxId.serialize}": {}
               |       }
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
  def mailboxSetShouldReturnUnknownMethodWhenMissingAllCapabilities(): Unit = {
    val mailboxId = randomMailboxId

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [],
               |  "methodCalls": [[
               |     "Mailbox/set",
               |     {
               |       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |       "update": {
               |         "${mailboxId.serialize}": {}
               |       }
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
  def updateShouldHandleNotFoundClientId(server: GuiceJamesServer): Unit = {
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "mailbox"))

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
               |  "methodCalls": [
               |    [
               |     "Mailbox/set",
               |     {
               |       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |       "update": {
               |         "#invalid": {
               |           "name": "newName"
               |         }
               |       }
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

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Mailbox/set",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "notUpdated": {
         |                    "#invalid": {
         |                        "type": "invalidArguments",
         |                        "description": "#invalid was not used in previously defined creationIds"
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def newStateShouldBeUpToDate(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "mailbox"))
    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "name": "newName"
         |                    }
         |                }
         |           }, "c1"],
         |       [ "Mailbox/changes",
         |       {
         |         "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |         "#sinceState": {
         |            "resultOf":"c1",
         |            "name":"Mailbox/set",
         |            "path":"newState"
         |          }
         |       },
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
  def oldStateShouldIncludeSetChanges(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "mailbox"))
    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "${mailboxId.serialize}": {
         |                      "name": "newName"
         |                    }
         |                }
         |           }, "c1"],
         |       [ "Mailbox/changes",
         |       {
         |         "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |         "#sinceState": {
         |            "resultOf":"c1",
         |            "name":"Mailbox/set",
         |            "path":"oldState"
         |          }
         |       },
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
           |  "updated": ["${mailboxId.serialize}"],
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
               |      "Mailbox/set",
               |           {
               |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
               |           }, "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body("methodResponses[0][1].oldState", equalTo(state))
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
               |      "Mailbox/set",
               |      {
               |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
               |      },
               |      "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body("methodResponses[0][1].oldState", not(equalTo(state)))
  }

  @Test
  def webSocketShouldPushNewMessageWhenChangeSubscriptionOfMailbox(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    Thread.sleep(100)

    val socketPort = server.getProbe(classOf[JmapGuiceProbe])
      .getJmapPort
      .getValue

    val response: Either[String, List[String]] =
      basicRequest.get(Uri.apply(new URI(s"ws://127.0.0.1:$socketPort/jmap/ws")))
        .header("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        .header("Accept", ACCEPT_RFC8621_VERSION_HEADER)
        .response(asWebSocket[Identity, List[String]] {
          ws =>
            ws.send(WebSocketFrame.text(
              """{
                |  "@type": "WebSocketPushEnable",
                |  "dataTypes": ["Mailbox"]
                |}""".stripMargin))

            Thread.sleep(100)

            ws.send(WebSocketFrame.text(
              s"""{
                 |  "@type": "Request",
                 |  "id": "req-36",
                 |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
                 |  "methodCalls": [
                 |    ["Mailbox/set", {
                 |      "accountId": "$ACCOUNT_ID",
                 |      "update": {
                 |        "${mailboxId.serialize}" : {
                 |           "isSubscribed": true
                 |        }
                 |      }
                 |    }, "c1"]]
                 |}""".stripMargin))
            ws.receiveMessageInTimespan(scala.concurrent.duration.Duration(1000, MILLISECONDS))
        })
        .send(backend)
        .body

    val hasMailboxStateChangeConsumer : ThrowingConsumer[String] = (s: String) => assertThat(s)
        .startsWith("{\"@type\":\"StateChange\",\"changed\":{\"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6\":{\"Mailbox\":")
    assertThat(response.toOption.get.asJava)
      .anySatisfy(hasMailboxStateChangeConsumer)
  }

  @Test
  def sseShouldHasNewEventWhenChangeSubscribeOfMailbox(server: GuiceJamesServer): Unit = {
    val port = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(BOB, "mailbox12"))
    Thread.sleep(500)

    val seq = new ListBuffer[String]()
    HttpClient.create
      .baseUrl(s"http://127.0.0.1:$port/eventSource?types=*&ping=0&closeAfter=no")
      .headers(builder => {
        builder.add("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
        builder.add("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      })
      .get()
      .responseContent()
      .map(buffer => {
        val bytes = new Array[Byte](buffer.readableBytes)
        buffer.readBytes(bytes)
        new String(bytes, StandardCharsets.UTF_8)
      })
      .doOnNext(seq.addOne)
      .subscribeOn(Schedulers.boundedElastic())
      .subscribe()

    Awaitility.`with`
      .pollInterval(Duration.ofMillis(100))
      .atMost(Duration.ofSeconds(100))
      .await
      .untilAsserted { () =>
        // change subscription
        JmapRequests.subscribe(mailboxId.serialize())
        Thread.sleep(200)

        assertThat(seq.asJava)
          .hasSize(1)
        assertThat(seq.head)
          .startsWith("event: state\ndata: {\"@type\":\"StateChange\",\"changed\":{\"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6\":{\"Mailbox\":")
        assertThat(seq.head).endsWith("\n\n")
      }
  }

}
