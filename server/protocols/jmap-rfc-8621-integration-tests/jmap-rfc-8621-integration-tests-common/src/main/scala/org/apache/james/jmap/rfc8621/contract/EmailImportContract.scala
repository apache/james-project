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
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured._
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.{SC_CREATED, SC_OK}
import org.apache.james.GuiceJamesServer
import org.apache.james.core.quota.QuotaCountLimit
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UTCDate
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.MailboxACL.Right
import org.apache.james.mailbox.model.{MailboxACL, MailboxId, MailboxPath, MessageId}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.{ACLProbeImpl, MailboxProbeImpl, QuotaProbesImpl}
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.{BeforeEach, Test}
import play.api.libs.json.{JsString, Json}

trait EmailImportContract {
  private lazy val UTC_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
        .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  def randomMessageId: MessageId

  def randomMailboxId: MailboxId

  @Test
  def importShouldReturnUnknownMethodWhenMissingCoreCapability(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body("""{
              |  "using": ["urn:ietf:params:jmap:mail"],
              |  "methodCalls": [
              |    [
              |      "Email/import",
              |      {
              |        "arg1": "arg1data",
              |        "arg2": "arg2data"
              |      },
              |      "c1"
              |    ]
              |  ]
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
  def importShouldReturnUnknownMethodWhenMissingMailCapability(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body("""{
              |  "using": ["urn:ietf:params:jmap:core"],
              |  "methodCalls": [
              |    [
              |      "Email/import",
              |      {
              |        "arg1": "arg1data",
              |        "arg2": "arg2data"
              |      },
              |      "c1"
              |    ]
              |  ]
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
         |      "description": "Missing capability(ies): urn:ietf:params:jmap:mail"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def importShouldAddTheMailInTheMailbox(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val receivedAt = ZonedDateTime.now().minusDays(1)

    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(ClassLoader.getSystemResourceAsStream("eml/alternative.eml"))
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val blobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value

    val receivedAtString = UTCDate(receivedAt).asUTC.format(UTC_DATE_FORMAT)
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
              |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
              |  "methodCalls": [
              |    ["Email/import",
              |      {
              |        "accountId": "$ACCOUNT_ID",
              |        "emails": {
              |           "C42": {
              |             "blobId": "$blobId",
              |             "mailboxIds": {
              |               "${mailboxId.serialize()}": true
              |             },
              |             "keywords": {
              |               "toto": true
              |             },
              |             "receivedAt": "$receivedAtString"
              |           }
              |         }
              |      },
              |      "c1"],
              |    ["Email/get",
              |     {
              |       "accountId": "$ACCOUNT_ID",
              |       "ids": ["#C42"],
              |       "properties": ["keywords", "mailboxIds", "receivedAt", "subject", "size", "bodyValues", "htmlBody"],
              |       "fetchHTMLBodyValues": true
              |     },
              |     "c2"]
              |  ]
              |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    val responseAsJson = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("C42")

    val messageId = responseAsJson
      .\("id")
      .get.asInstanceOf[JsString].value

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState", "methodResponses[1][1].state")
      .inPath("methodResponses")
      .isEqualTo(
      s"""    [
         |        ["Email/import",
         |            {
         |                "accountId": "$ACCOUNT_ID",
         |                "created": {
         |                    "C42": {
         |                        "id": "$messageId",
         |                        "blobId": "$messageId",
         |                        "threadId": "$messageId",
         |                        "size": 836
         |                    }
         |                }
         |            }, "c1"],
         |        ["Email/get",
         |            {
         |                "accountId": "$ACCOUNT_ID",
         |                "notFound": [],
         |                "list": [
         |                    {
         |                        "id": "$messageId",
         |                        "htmlBody": [
         |                            {
         |                                "charset": "utf-8",
         |                                "size": 39,
         |                                "partId": "2",
         |                                "blobId": "${messageId}_2",
         |                                "type": "text/html"
         |                            }
         |                        ],
         |                        "size": 836,
         |                        "keywords": {
         |                            "toto": true
         |                        },
         |                        "subject": "MultiAttachment",
         |                        "mailboxIds": {
         |                            "${mailboxId.serialize()}": true
         |                        },
         |                        "receivedAt": "$receivedAtString",
         |                        "bodyValues": {
         |                            "2": {
         |                                "value": "<p>Send<br/>concerted from html</p>\\r\\n\\r\\n",
         |                                "isEncodingProblem": false,
         |                                "isTruncated": false
         |                            }
         |                        }
         |                    }
         |                ]
         |            }, "c2"]
         |    ]""".stripMargin)
  }

  @Test
  def importShouldSucceedWhenEmptyKeyword(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val receivedAt = ZonedDateTime.now().minusDays(1)

    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(ClassLoader.getSystemResourceAsStream("eml/alternative.eml"))
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val blobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value

    val receivedAtString = UTCDate(receivedAt).asUTC.format(UTC_DATE_FORMAT)
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
              |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
              |  "methodCalls": [
              |    ["Email/import",
              |      {
              |        "accountId": "$ACCOUNT_ID",
              |        "emails": {
              |           "C42": {
              |             "blobId": "$blobId",
              |             "mailboxIds": {
              |               "${mailboxId.serialize()}": true
              |             },
              |             "keywords": {},
              |             "receivedAt": "$receivedAtString"
              |           }
              |         }
              |      },
              |      "c1"],
              |    ["Email/get",
              |     {
              |       "accountId": "$ACCOUNT_ID",
              |       "ids": ["#C42"],
              |       "properties": ["keywords", "mailboxIds", "receivedAt", "subject", "size", "bodyValues", "htmlBody"],
              |       "fetchHTMLBodyValues": true
              |     },
              |     "c2"]
              |  ]
              |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    val responseAsJson = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("C42")

    val messageId = responseAsJson
      .\("id")
      .get.asInstanceOf[JsString].value

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState", "methodResponses[1][1].state")
      .inPath("methodResponses")
      .isEqualTo(
      s"""    [
         |        ["Email/import",
         |            {
         |                "accountId": "$ACCOUNT_ID",
         |                "created": {
         |                    "C42": {
         |                        "id": "$messageId",
         |                        "blobId": "$messageId",
         |                        "threadId": "$messageId",
         |                        "size": 836
         |                    }
         |                }
         |            }, "c1"],
         |        ["Email/get",
         |            {
         |                "accountId": "$ACCOUNT_ID",
         |                "notFound": [],
         |                "list": [
         |                    {
         |                        "id": "$messageId",
         |                        "htmlBody": [
         |                            {
         |                                "charset": "utf-8",
         |                                "size": 39,
         |                                "partId": "2",
         |                                "blobId": "${messageId}_2",
         |                                "type": "text/html"
         |                            }
         |                        ],
         |                        "size": 836,
         |                        "keywords": {},
         |                        "subject": "MultiAttachment",
         |                        "mailboxIds": {
         |                            "${mailboxId.serialize()}": true
         |                        },
         |                        "receivedAt": "$receivedAtString",
         |                        "bodyValues": {
         |                            "2": {
         |                                "value": "<p>Send<br/>concerted from html</p>\\r\\n\\r\\n",
         |                                "isEncodingProblem": false,
         |                                "isTruncated": false
         |                            }
         |                        }
         |                    }
         |                ]
         |            }, "c2"]
         |    ]""".stripMargin)
  }

  @Test
  def importShouldSucceedWhenNoKeyword(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val receivedAt = ZonedDateTime.now().minusDays(1)

    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(ClassLoader.getSystemResourceAsStream("eml/alternative.eml"))
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val blobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value

    val receivedAtString = UTCDate(receivedAt).asUTC.format(UTC_DATE_FORMAT)
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
              |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
              |  "methodCalls": [
              |    ["Email/import",
              |      {
              |        "accountId": "$ACCOUNT_ID",
              |        "emails": {
              |           "C42": {
              |             "blobId": "$blobId",
              |             "mailboxIds": {
              |               "${mailboxId.serialize()}": true
              |             },
              |             "receivedAt": "$receivedAtString"
              |           }
              |         }
              |      },
              |      "c1"],
              |    ["Email/get",
              |     {
              |       "accountId": "$ACCOUNT_ID",
              |       "ids": ["#C42"],
              |       "properties": ["keywords", "mailboxIds", "receivedAt", "subject", "size", "bodyValues", "htmlBody"],
              |       "fetchHTMLBodyValues": true
              |     },
              |     "c2"]
              |  ]
              |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    val responseAsJson = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("C42")

    val messageId = responseAsJson
      .\("id")
      .get.asInstanceOf[JsString].value

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState", "methodResponses[1][1].state")
      .inPath("methodResponses")
      .isEqualTo(
      s"""    [
         |        ["Email/import",
         |            {
         |                "accountId": "$ACCOUNT_ID",
         |                "created": {
         |                    "C42": {
         |                        "id": "$messageId",
         |                        "blobId": "$messageId",
         |                        "threadId": "$messageId",
         |                        "size": 836
         |                    }
         |                }
         |            }, "c1"],
         |        ["Email/get",
         |            {
         |                "accountId": "$ACCOUNT_ID",
         |                "notFound": [],
         |                "list": [
         |                    {
         |                        "id": "$messageId",
         |                        "htmlBody": [
         |                            {
         |                                "charset": "utf-8",
         |                                "size": 39,
         |                                "partId": "2",
         |                                "blobId": "${messageId}_2",
         |                                "type": "text/html"
         |                            }
         |                        ],
         |                        "size": 836,
         |                        "keywords": {},
         |                        "subject": "MultiAttachment",
         |                        "mailboxIds": {
         |                            "${mailboxId.serialize()}": true
         |                        },
         |                        "receivedAt": "$receivedAtString",
         |                        "bodyValues": {
         |                            "2": {
         |                                "value": "<p>Send<br/>concerted from html</p>\\r\\n\\r\\n",
         |                                "isEncodingProblem": false,
         |                                "isTruncated": false
         |                            }
         |                        }
         |                    }
         |                ]
         |            }, "c2"]
         |    ]""".stripMargin)
  }

  @Test
  def importShouldSucceedWhenNoReceivedAt(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val receivedAt = ZonedDateTime.now().minusDays(1)

    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(ClassLoader.getSystemResourceAsStream("eml/alternative.eml"))
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val blobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
              |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
              |  "methodCalls": [
              |    ["Email/import",
              |      {
              |        "accountId": "$ACCOUNT_ID",
              |        "emails": {
              |           "C42": {
              |             "blobId": "$blobId",
              |             "mailboxIds": {
              |               "${mailboxId.serialize()}": true
              |             }
              |           }
              |         }
              |      },
              |      "c1"],
              |    ["Email/get",
              |     {
              |       "accountId": "$ACCOUNT_ID",
              |       "ids": ["#C42"],
              |       "properties": ["keywords", "mailboxIds", "receivedAt", "subject", "size", "bodyValues", "htmlBody"],
              |       "fetchHTMLBodyValues": true
              |     },
              |     "c2"]
              |  ]
              |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    val responseAsJson = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("C42")

    val messageId = responseAsJson
      .\("id")
      .get.asInstanceOf[JsString].value

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState", "methodResponses[1][1].state")
      .inPath("methodResponses")
      .isEqualTo(
      s"""    [
         |        ["Email/import",
         |            {
         |                "accountId": "$ACCOUNT_ID",
         |                "created": {
         |                    "C42": {
         |                        "id": "$messageId",
         |                        "blobId": "$messageId",
         |                        "threadId": "$messageId",
         |                        "size": 836
         |                    }
         |                }
         |            }, "c1"],
         |        ["Email/get",
         |            {
         |                "accountId": "$ACCOUNT_ID",
         |                "notFound": [],
         |                "list": [
         |                    {
         |                        "id": "$messageId",
         |                        "htmlBody": [
         |                            {
         |                                "charset": "utf-8",
         |                                "size": 39,
         |                                "partId": "2",
         |                                "blobId": "${messageId}_2",
         |                                "type": "text/html"
         |                            }
         |                        ],
         |                        "size": 836,
         |                        "keywords": {},
         |                        "subject": "MultiAttachment",
         |                        "mailboxIds": {
         |                            "${mailboxId.serialize()}": true
         |                        },
         |                        "receivedAt": "$${json-unit.ignore}",
         |                        "bodyValues": {
         |                            "2": {
         |                                "value": "<p>Send<br/>concerted from html</p>\\r\\n\\r\\n",
         |                                "isEncodingProblem": false,
         |                                "isTruncated": false
         |                            }
         |                        }
         |                    }
         |                ]
         |            }, "c2"]
         |    ]""".stripMargin)
  }

  @Test
  def importShouldEnforceQuotas(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    quotaProbe.setMaxMessageCount(quotaProbe.getQuotaRoot(MailboxPath.inbox(BOB)), QuotaCountLimit.count(0L))
    val id1 = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))

    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(ClassLoader.getSystemResourceAsStream("eml/alternative.eml"))
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString
    val blobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value
    val receivedAt = ZonedDateTime.now().minusDays(1)
    val receivedAtString = UTCDate(receivedAt).asUTC.format(UTC_DATE_FORMAT)

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [["Email/import", {
         |        "accountId": "$ACCOUNT_ID",
         |        "emails": {
         |           "C42": {
         |             "blobId": "$blobId",
         |             "mailboxIds": {
         |               "${id1.serialize()}": true
         |             },
         |             "keywords": {
         |               "toto": true
         |             },
         |             "receivedAt": "$receivedAtString"
         |           }
         |         }
         |      }, "c1"]]
         |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
        s"""{
           |	"sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"methodResponses": [
           |		["Email/import", {
           |			"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |			"notCreated": {
           |				"C42": {
           |					"type": "overQuota",
           |					"description": "You have too many messages in #private&bob@domain.tld"
           |				}
           |			}
           |		}, "c1"]
           |	]
           |}""".stripMargin)
  }

  @Test
  def importShouldAddTheMailsInTheMailbox(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val receivedAt = ZonedDateTime.now().minusDays(1)

    val uploadResponse1: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(ClassLoader.getSystemResourceAsStream("eml/alternative.eml"))
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val uploadResponse2: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(ClassLoader.getSystemResourceAsStream("eml/html.eml"))
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val blobId1: String = Json.parse(uploadResponse1).\("blobId").get.asInstanceOf[JsString].value
    val blobId2: String = Json.parse(uploadResponse2).\("blobId").get.asInstanceOf[JsString].value

    val receivedAtString = UTCDate(receivedAt).asUTC.format(UTC_DATE_FORMAT)
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
              |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
              |  "methodCalls": [
              |    ["Email/import",
              |      {
              |        "accountId": "$ACCOUNT_ID",
              |        "emails": {
              |           "C42": {
              |             "blobId": "$blobId1",
              |             "mailboxIds": {
              |               "${mailboxId.serialize()}": true
              |             },
              |             "keywords": {
              |               "toto": true
              |             },
              |             "receivedAt": "$receivedAtString"
              |           },
              |           "C43": {
              |             "blobId": "$blobId2",
              |             "mailboxIds": {
              |               "${mailboxId.serialize()}": true
              |             },
              |             "keywords": {
              |               "toto": true
              |             },
              |             "receivedAt": "$receivedAtString"
              |           }
              |         }
              |      },
              |      "c1"],
              |    ["Email/get",
              |     {
              |       "accountId": "$ACCOUNT_ID",
              |       "ids": ["#C42", "#C43"],
              |       "properties": ["subject"],
              |       "fetchHTMLBodyValues": true
              |     },
              |     "c2"]
              |  ]
              |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    val messageId1 = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("C42")
      .\("id")
      .get.asInstanceOf[JsString].value

    val messageId2 = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("C43")
      .\("id")
      .get.asInstanceOf[JsString].value

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState", "methodResponses[1][1].state")
      .inPath("methodResponses")
      .isEqualTo(
      s"""[
         |        [
         |            "Email/import",
         |            {
         |                "accountId": "$ACCOUNT_ID",
         |                "created": {
         |                    "C42": {
         |                        "id": "$messageId1",
         |                        "blobId": "$messageId1",
         |                        "threadId": "$messageId1",
         |                        "size": 836
         |                    },
         |                    "C43": {
         |                        "id": "$messageId2",
         |                        "blobId": "$messageId2",
         |                        "threadId": "$messageId1",
         |                        "size": 2727
         |                    }
         |                }
         |            },
         |            "c1"
         |        ],
         |        [
         |            "Email/get",
         |            {
         |                "accountId": "$ACCOUNT_ID",
         |                "notFound": [],
         |                "list": [
         |                    {
         |                        "subject": "MultiAttachment",
         |                        "id": "$messageId1"
         |                    },
         |                    {
         |                        "subject": "MultiAttachment",
         |                        "id": "$messageId2"
         |                    }
         |                ]
         |            },
         |            "c2"
         |        ]
         |    ]""".stripMargin)
  }

  @Test
  def importShouldDisplayOldAndNewState(server: GuiceJamesServer): Unit = {
    val oldState: String = retrieveEmailState

    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val receivedAt = ZonedDateTime.now().minusDays(1)

    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(ClassLoader.getSystemResourceAsStream("eml/alternative.eml"))
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val blobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value

    val receivedAtString = UTCDate(receivedAt).asUTC.format(UTC_DATE_FORMAT)
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
              |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
              |  "methodCalls": [
              |    ["Email/import",
              |      {
              |        "accountId": "$ACCOUNT_ID",
              |        "emails": {
              |           "C42": {
              |             "blobId": "$blobId",
              |             "mailboxIds": {
              |               "${mailboxId.serialize()}": true
              |             },
              |             "keywords": {
              |               "toto": true
              |             },
              |             "receivedAt": "$receivedAtString"
              |           }
              |         }
              |      },
              |      "c1"]
              |  ]
              |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    val newState: String = retrieveEmailState
    val importOldState = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("oldState")
      .get.asInstanceOf[JsString].value
    val importNewState = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("newState")
      .get.asInstanceOf[JsString].value

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(importOldState).isEqualTo(oldState)
      softly.assertThat(importNewState).isEqualTo(newState)
    })
  }

  def retrieveEmailState: String = `with`()
    .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .body(
      s"""{
         |  "using": [
         |  "urn:ietf:params:jmap:core",
         |  "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids":[]
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
    .post
  .`then`()
    .extract()
    .jsonPath()
    .get("methodResponses[0][1].state")

  @Test
  def importShouldFailWhenMailboxNotOwned(server: GuiceJamesServer): Unit = {
    val alicePath = MailboxPath.inbox(ALICE)
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(alicePath)
    val receivedAt = ZonedDateTime.now().minusDays(1)

    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body("whatever")
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val blobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value

    val receivedAtString = UTCDate(receivedAt).asUTC.format(UTC_DATE_FORMAT)
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
              |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
              |  "methodCalls": [
              |    ["Email/import",
              |      {
              |        "accountId": "$ACCOUNT_ID",
              |        "emails": {
              |           "C42": {
              |             "blobId": "$blobId",
              |             "mailboxIds": {
              |               "${mailboxId.serialize()}": true
              |             },
              |             "keywords": {
              |               "toto": true
              |             },
              |             "receivedAt": "$receivedAtString"
              |           }
              |         }
              |      },
              |      "c1"]
              |  ]
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
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState", "methodResponses[1][1].state")
      .inPath("methodResponses[0]")
      .isEqualTo(
      s"""[
           |  "Email/import",
           |  {
           |    "accountId":"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |    "notCreated":{
           |      "C42":{
           |        "type":"notFound",
           |        "description":"Mailbox $mailboxId can not be found"
           |      }
           |    }
           |  },
           |  "c1"
           |]""".stripMargin)
  }

  @Test
  def importShouldSucceedWhenMailboxDelegated(server: GuiceJamesServer): Unit = {
    val andrePath = MailboxPath.inbox(ANDRE)
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andrePath)
    val receivedAt = ZonedDateTime.now().minusDays(1)
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andrePath, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Insert, Right.Lookup, Right.Read))

    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(ClassLoader.getSystemResourceAsStream("eml/alternative.eml"))
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val blobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value

    val receivedAtString = UTCDate(receivedAt).asUTC.format(UTC_DATE_FORMAT)
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
              |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
              |  "methodCalls": [
              |    ["Email/import",
              |      {
              |        "accountId": "$ACCOUNT_ID",
              |        "emails": {
              |           "C42": {
              |             "blobId": "$blobId",
              |             "mailboxIds": {
              |               "${mailboxId.serialize()}": true
              |             },
              |             "keywords": {
              |               "toto": true
              |             },
              |             "receivedAt": "$receivedAtString"
              |           }
              |         }
              |      },
              |      "c1"]
              |  ]
              |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    val responseAsJson = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("C42")

    val messageId = responseAsJson
      .\("id")
      .get.asInstanceOf[JsString].value

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState", "methodResponses[1][1].state")
      .inPath("methodResponses[0]")
      .isEqualTo(
      s"""[
           |  "Email/import",
           |  {
           |    "accountId":"$ACCOUNT_ID",
           |    "created":{
           |      "C42":{
           |        "id":"$messageId",
           |        "blobId":"$messageId",
           |        "threadId":"$messageId",
           |        "size":836
           |      }
           |    }
           |  },
           |  "c1"
           |]""".stripMargin)
  }

  @Test
  def importShouldFailWhenBlobNotOwned(server: GuiceJamesServer): Unit = {
    val andrePath = MailboxPath.inbox(ANDRE)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andrePath)
    val bobPath = MailboxPath.inbox(BOB)
    val bobId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val receivedAt = ZonedDateTime.now().minusDays(1)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString, andrePath, AppendCommand.from(message))
      .getMessageId
    val blobId: String = messageId.serialize()

    val receivedAtString = UTCDate(receivedAt).asUTC.format(UTC_DATE_FORMAT)
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
              |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
              |  "methodCalls": [
              |    ["Email/import",
              |      {
              |        "accountId": "$ACCOUNT_ID",
              |        "emails": {
              |           "C42": {
              |             "blobId": "$blobId",
              |             "mailboxIds": {
              |               "${bobId.serialize()}": true
              |             },
              |             "keywords": {
              |               "toto": true
              |             },
              |             "receivedAt": "$receivedAtString"
              |           }
              |         }
              |      },
              |      "c1"]
              |  ]
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
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState", "methodResponses[1][1].state")
      .inPath("methodResponses[0]")
      .isEqualTo(
      s"""[
           |  "Email/import",
           |  {
           |    "accountId":"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |    "notCreated":{
           |      "C42":{
           |        "type":"notFound",
           |        "description":"Blob BlobId($blobId) could not be found"
           |      }
           |    }
           |  },
           |  "c1"
           |]""".stripMargin)
  }

  @Test
  def importShouldSucceedWhenBlobDelegated(server: GuiceJamesServer): Unit = {
    val andrePath = MailboxPath.inbox(ANDRE)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andrePath)
    val bobPath = MailboxPath.inbox(BOB)
    val bobId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val receivedAt = ZonedDateTime.now().minusDays(1)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val blobId: String = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString, andrePath, AppendCommand.from(message))
      .getMessageId.serialize()

    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andrePath, BOB.asString, new MailboxACL.Rfc4314Rights(Right.Insert, Right.Lookup, Right.Read))

    val receivedAtString = UTCDate(receivedAt).asUTC.format(UTC_DATE_FORMAT)
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
              |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
              |  "methodCalls": [
              |    ["Email/import",
              |      {
              |        "accountId": "$ACCOUNT_ID",
              |        "emails": {
              |           "C42": {
              |             "blobId": "$blobId",
              |             "mailboxIds": {
              |               "${bobId.serialize()}": true
              |             },
              |             "keywords": {
              |               "toto": true
              |             },
              |             "receivedAt": "$receivedAtString"
              |           }
              |         }
              |      },
              |      "c1"]
              |  ]
              |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    val responseAsJson = Json.parse(response)
      .\("methodResponses")
      .\(0).\(1)
      .\("created")
      .\("C42")

    val messageId = responseAsJson
      .\("id")
      .get.asInstanceOf[JsString].value

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState", "methodResponses[1][1].state")
      .inPath("methodResponses[0]")
      .isEqualTo(
      s"""[
           |  "Email/import",
           |  {
           |    "accountId":"$ACCOUNT_ID",
           |    "created":{
           |      "C42":{
           |        "id":"$messageId",
           |        "blobId":"$messageId",
           |        "threadId":"$messageId",
           |        "size":85
           |      }
           |    }
           |  },
           |  "c1"
           |]""".stripMargin)
  }

  @Test
  def importShouldFailWhenNoMailboxes(): Unit = {
    val receivedAt = ZonedDateTime.now().minusDays(1)

    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body("whatever")
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val blobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value

    val receivedAtString = UTCDate(receivedAt).asUTC.format(UTC_DATE_FORMAT)
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
              |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
              |  "methodCalls": [
              |    ["Email/import",
              |      {
              |        "accountId": "$ACCOUNT_ID",
              |        "emails": {
              |           "C42": {
              |             "blobId": "$blobId",
              |             "mailboxIds": {},
              |             "keywords": {
              |               "toto": true
              |             },
              |             "receivedAt": "$receivedAtString"
              |           }
              |         }
              |      },
              |      "c1"]
              |  ]
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
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState", "methodResponses[1][1].state")
      .inPath("methodResponses[0]")
      .isEqualTo(
      s"""[
           |  "Email/import",
           |  {
           |    "accountId":"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |    "notCreated":{
           |      "C42":{
           |        "type":"invalidArguments",
           |        "description":"Email/import so far only supports a single mailboxId"
           |      }
           |    }
           |  },
           |  "c1"
           |]""".stripMargin)
  }

  @Test
  def importShouldFailWhenTooManyMailboxes(): Unit = {
    val receivedAt = ZonedDateTime.now().minusDays(1)

    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body("whatever")
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val blobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value

    val receivedAtString = UTCDate(receivedAt).asUTC.format(UTC_DATE_FORMAT)
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
              |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
              |  "methodCalls": [
              |    ["Email/import",
              |      {
              |        "accountId": "$ACCOUNT_ID",
              |        "emails": {
              |           "C42": {
              |             "blobId": "$blobId",
              |             "mailboxIds": {
              |               "${randomMailboxId.serialize()}": true,
              |               "${randomMailboxId.serialize()}": true
              |             },
              |             "keywords": {
              |               "toto": true
              |             },
              |             "receivedAt": "$receivedAtString"
              |           }
              |         }
              |      },
              |      "c1"]
              |  ]
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
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState", "methodResponses[1][1].state")
      .inPath("methodResponses[0]")
      .isEqualTo(
      s"""[
           |  "Email/import",
           |  {
           |    "accountId":"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |    "notCreated":{
           |      "C42":{
           |        "type":"invalidArguments",
           |        "description":"Email/import so far only supports a single mailboxId"
           |      }
           |    }
           |  },
           |  "c1"
           |]""".stripMargin)
  }

  @Test
  def importShouldFailWhenBlobNotFound(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val receivedAt = ZonedDateTime.now().minusDays(1)

    val blobId: String = randomMessageId.serialize()

    val receivedAtString = UTCDate(receivedAt).asUTC.format(UTC_DATE_FORMAT)
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
              |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
              |  "methodCalls": [
              |    ["Email/import",
              |      {
              |        "accountId": "$ACCOUNT_ID",
              |        "emails": {
              |           "C42": {
              |             "blobId": "$blobId",
              |             "mailboxIds": {
              |               "${mailboxId.serialize()}": true
              |             },
              |             "keywords": {
              |               "toto": true
              |             },
              |             "receivedAt": "$receivedAtString"
              |           }
              |         }
              |      },
              |      "c1"]
              |  ]
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
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState")
      .inPath("methodResponses[0][1]")
      .isEqualTo(
      s"""{
           |  "accountId":"$ACCOUNT_ID",
           |  "notCreated":{
           |    "C42":{
           |      "type":"notFound",
           |      "description":"Blob BlobId($blobId) could not be found"
           |    }
           |  }
           |}""".stripMargin)
  }

  @Test
  def importShouldFailWhenInvalidAccountId(server: GuiceJamesServer): Unit = {
    val bobPath = MailboxPath.inbox(BOB)
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val receivedAt = ZonedDateTime.now().minusDays(1)

    val blobId: String = randomMessageId.serialize()

    val receivedAtString = UTCDate(receivedAt).asUTC.format(UTC_DATE_FORMAT)
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
              |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
              |  "methodCalls": [
              |    ["Email/import",
              |      {
              |        "accountId": "bad",
              |        "emails": {
              |           "C42": {
              |             "blobId": "$blobId",
              |             "mailboxIds": {
              |               "${mailboxId.serialize()}": true
              |             },
              |             "keywords": {
              |               "toto": true
              |             },
              |             "receivedAt": "$receivedAtString"
              |           }
              |         }
              |      },
              |      "c1"]
              |  ]
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
      .inPath("methodResponses[0]")
      .isEqualTo("""["error",{"type":"accountNotFound"},"c1"]""")
  }
}
