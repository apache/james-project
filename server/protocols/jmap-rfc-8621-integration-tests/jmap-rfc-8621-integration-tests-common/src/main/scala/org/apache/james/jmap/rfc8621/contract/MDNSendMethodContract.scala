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
import java.time.Duration
import java.util.concurrent.TimeUnit

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured._
import io.restassured.http.ContentType.JSON
import io.restassured.specification.RequestSpecification
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.draft.MessageIdProbe
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ANDRE, ANDRE_ACCOUNT_ID, ANDRE_IDENTITY_ID, ANDRE_PASSWORD, BOB, BOB_PASSWORD, CEDRIC, DAVID, DOMAIN, IDENTITY_ID, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.MDNSendMethodContract.TAG_MDN_MESSAGE_FORMAT
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.{MailboxId, MailboxPath, MessageId, MultimailboxesSearchQuery, SearchQuery}
import org.apache.james.mime4j.codec.DecodeMonitor
import org.apache.james.mime4j.dom.{Message, Multipart}
import org.apache.james.mime4j.message.DefaultMessageBuilder
import org.apache.james.mime4j.stream.{MimeConfig, RawField}
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.awaitility.core.ConditionFactory
import org.junit.jupiter.api.{BeforeEach, Tag, Test}

import scala.jdk.CollectionConverters._

object MDNSendMethodContract {
  val TAG_MDN_MESSAGE_FORMAT: "MDN_MESSAGE_FORMAT" = "MDN_MESSAGE_FORMAT"
}

trait MDNSendMethodContract {
  private lazy val slowPacedPollInterval: Duration = ONE_HUNDRED_MILLISECONDS

  private lazy val calmlyAwait: ConditionFactory = Awaitility.`with`
    .pollInterval(slowPacedPollInterval)
    .and.`with`.pollDelay(slowPacedPollInterval)
    .await

  private lazy val awaitAtMostTenSeconds: ConditionFactory = calmlyAwait.atMost(10, TimeUnit.SECONDS)

  private def getFirstMessageInMailBox(guiceJamesServer: GuiceJamesServer, username: Username): Option[Message] = {
    val searchByRFC822MessageId: MultimailboxesSearchQuery = MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.all())).build
    val defaultMessageBuilder: DefaultMessageBuilder = new DefaultMessageBuilder
    defaultMessageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE)
    defaultMessageBuilder.setDecodeMonitor(DecodeMonitor.SILENT)

    guiceJamesServer.getProbe(classOf[MailboxProbeImpl]).searchMessage(searchByRFC822MessageId, username.asString(), 100)
      .asScala.headOption
      .flatMap(messageId => guiceJamesServer.getProbe(classOf[MessageIdProbe]).getMessages(messageId, username).asScala.headOption)
      .map(messageResult => defaultMessageBuilder.parseMessage(messageResult.getFullContent.getInputStream))
  }

  private def buildOriginalMessage(tag : String) :Message =
    Message.Builder
      .of
      .setSubject(s"Subject of original message$tag")
      .setSender(BOB.asString)
      .setFrom(BOB.asString)
      .setTo(ANDRE.asString)
      .addField(new RawField("Disposition-Notification-To", s"Bob <${BOB.asString()}>"))
      .setBody(s"Body of mail$tag, that mdn related", StandardCharsets.UTF_8)
      .build

  private def buildBOBRequestSpecification(server: GuiceJamesServer): RequestSpecification =
    baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build

  def randomMessageId: MessageId

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)
      .addUser(ANDRE.asString, ANDRE_PASSWORD)
      .addUser(DAVID.asString, DAVID.asString())

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()
  }

  @Test
  def mdnSendShouldBeSuccessAndSendMailSuccessfully(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    val bobMailBoxPath: MailboxPath = MailboxPath.inbox(BOB)
    val bobInboxId: MailboxId = mailboxProbe.createMailbox(bobMailBoxPath)

    val andreMailBoxPath: MailboxPath = MailboxPath.inbox(ANDRE)
    mailboxProbe.createMailbox(andreMailBoxPath)

    val relatedEmailId: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString(), andreMailBoxPath, AppendCommand.builder()
        .build(buildOriginalMessage("1")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:mdn"
         |  ],
         |  "methodCalls": [
         |    [
         |      "MDN/send",
         |      {
         |        "accountId": "$ANDRE_ACCOUNT_ID",
         |        "identityId": "$ANDRE_IDENTITY_ID",
         |        "send": {
         |          "k1546": {
         |            "forEmailId": "${relatedEmailId.serialize()}",
         |            "subject": "Read receipt for: World domination",
         |            "textBody": "This receipt shows that the email has been displayed on your recipient's computer. ",
         |            "reportingUA": "joes-pc.cs.example.com; Foomail 97.1",
         |            "disposition": {
         |              "actionMode": "manual-action",
         |              "sendingMode": "mdn-sent-manually",
         |              "type": "displayed"
         |            },
         |            "extensionFields": {
         |              "X-EXTENSION-EXAMPLE": "example.com"
         |            }
         |          }
         |        },
         |        "onSuccessUpdateEmail": {
         |          "#k1546": {
         |            "keywords/$$mdnsent": true
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
         |  ]
         |}""".stripMargin

   val mdnSendResponse: String =
      `given`
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

    assertThatJson(mdnSendResponse)
      .whenIgnoringPaths("methodResponses[1][1].newState",
        "methodResponses[1][1].oldState")
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "MDN/send",
           |            {
           |                "accountId": "$ANDRE_ACCOUNT_ID",
           |                "sent": {
           |                    "k1546": {
           |                        "finalRecipient": "rfc822; ${ANDRE.asString}",
           |                        "includeOriginalMessage": false,
           |                        "originalRecipient": "rfc822; ${ANDRE.asString()}"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ],
           |        [
           |            "Email/set",
           |            {
           |                "accountId": "$ANDRE_ACCOUNT_ID",
           |                "oldState": "23",
           |                "newState": "42",
           |                "updated": {
           |                    "${relatedEmailId.serialize()}": null
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)

    val requestQueryMDNMessage: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$ACCOUNT_ID",
         |      "filter": {"inMailbox": "${bobInboxId.serialize}"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response: String =
        `given`(buildBOBRequestSpecification(server))
          .body(requestQueryMDNMessage)
        .when
          .post
        .`then`
          .statusCode(SC_OK)
          .contentType(JSON)
          .extract
          .body
          .asString

      assertThatJson(response)
        .inPath("methodResponses[0][1].ids")
        .isArray
        .hasSize(1)
    }
  }

  @Test
  def mdnSendShouldBeSuccessWhenRequestAssignFinalRecipient(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    server.getProbe(classOf[DataProbeImpl]).addUserAliasMapping("david", "domain.tld", "andre@domain.tld")

    val bobMailBoxPath: MailboxPath = MailboxPath.inbox(BOB)
    mailboxProbe.createMailbox(bobMailBoxPath)

    val andreMailBoxPath: MailboxPath = MailboxPath.inbox(ANDRE)
    mailboxProbe.createMailbox(andreMailBoxPath)

    val relatedEmailId: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString(), andreMailBoxPath, AppendCommand.builder()
        .build(buildOriginalMessage("1")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:mdn"
         |  ],
         |  "methodCalls": [
         |    [
         |      "MDN/send",
         |      {
         |        "accountId": "$ANDRE_ACCOUNT_ID",
         |        "identityId": "$ANDRE_IDENTITY_ID",
         |        "send": {
         |          "k1546": {
         |            "forEmailId": "${relatedEmailId.serialize()}",
         |            "subject": "Read receipt for: World domination",
         |            "textBody": "This receipt shows that the email has been displayed on your recipient's computer. ",
         |            "finalRecipient": "rfc822; ${ANDRE.asString()}",
         |            "disposition": {
         |              "actionMode": "manual-action",
         |              "sendingMode": "mdn-sent-manually",
         |              "type": "displayed"
         |            }
         |          }
         |        },
         |        "onSuccessUpdateEmail": {
         |          "#k1546": {
         |            "keywords/$$mdnsent": true
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
         |  ]
         |}""".stripMargin

    val mdnSendResponse: String =
      `given`
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

    assertThatJson(mdnSendResponse)
      .whenIgnoringPaths("methodResponses[1][1].newState",
        "methodResponses[1][1].oldState")
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "MDN/send",
           |            {
           |                "accountId": "$ANDRE_ACCOUNT_ID",
           |                "sent": {
           |                    "k1546": {
           |                        "includeOriginalMessage": false,
           |                        "originalRecipient": "rfc822; ${ANDRE.asString()}"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ],
           |        [
           |            "Email/set",
           |            {
           |                "accountId": "$ANDRE_ACCOUNT_ID",
           |                "oldState": "23",
           |                "newState": "42",
           |                "updated": {
           |                    "${relatedEmailId.serialize()}": null
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def mdnSendShouldBeFailWhenDispositionPropertyIsInvalid(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    val bobMailBoxPath: MailboxPath = MailboxPath.inbox(BOB)
    mailboxProbe.createMailbox(bobMailBoxPath)

    val andreMailBoxPath: MailboxPath = MailboxPath.inbox(ANDRE)
    mailboxProbe.createMailbox(andreMailBoxPath)

    val relatedEmailId: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString(), andreMailBoxPath, AppendCommand.builder()
        .build(buildOriginalMessage("1")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:mdn"
         |  ],
         |  "methodCalls": [
         |    [
         |      "MDN/send",
         |      {
         |        "accountId": "$ANDRE_ACCOUNT_ID",
         |        "identityId": "$ANDRE_IDENTITY_ID",
         |        "send": {
         |          "k1546": {
         |            "forEmailId": "${relatedEmailId.serialize()}",
         |            "subject": "Read receipt for: World domination",
         |            "textBody": "This receipt shows that the email has been displayed on your recipient's computer. ",
         |            "disposition": {
         |              "actionMode": "invalidAction",
         |              "sendingMode": "mdn-sent-manually",
         |              "type": "displayed"
         |            }
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
         |  ]
         |}""".stripMargin

    val mdnSendResponse: String =
      `given`
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

    assertThatJson(mdnSendResponse)
      .inPath(s"methodResponses[0][1].notSent")
      .isEqualTo("""{
                   |    "k1546": {
                   |        "type": "invalidArguments",
                   |        "description": "Disposition \"ActionMode\" is invalid.",
                   |        "properties":["disposition"]
                   |    }
                   |}""".stripMargin)
  }

  @Test
  def mdnSendShouldBeFailWhenFinalRecipientIsInvalid(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    val andreMailBoxPath: MailboxPath = MailboxPath.inbox(ANDRE)
    mailboxProbe.createMailbox(andreMailBoxPath)

    val relatedEmailId: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString(), andreMailBoxPath, AppendCommand.builder()
        .build(buildOriginalMessage("1")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:mdn"
         |  ],
         |  "methodCalls": [
         |    [
         |      "MDN/send",
         |      {
         |        "accountId": "$ANDRE_ACCOUNT_ID",
         |        "identityId": "$ANDRE_IDENTITY_ID",
         |        "send": {
         |          "k1546": {
         |            "forEmailId": "${relatedEmailId.serialize()}",
         |            "subject": "Read receipt for: World domination",
         |            "textBody": "This receipt shows that the email has been displayed on your recipient's computer. ",
         |            "finalRecipient" : "invalid",
         |            "disposition": {
         |              "actionMode": "manual-action",
         |              "sendingMode": "mdn-sent-manually",
         |              "type": "displayed"
         |            }
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
         |  ]
         |}""".stripMargin

    val mdnSendResponse: String =
      `given`
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

    assertThatJson(mdnSendResponse)
      .inPath(s"methodResponses[0][1].notSent")
      .isEqualTo("""{
                   |    "k1546": {
                   |        "type": "invalidArguments",
                   |        "description": "FinalRecipient can't be parse.",
                   |        "properties": [
                   |            "finalRecipient"
                   |        ]
                   |    }
                   |}""".stripMargin)
  }

  @Test
  def mdnSendShouldBeFailWhenIdentityIsNotAllowedToUseFinalRecipient(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    val andreMailBoxPath: MailboxPath = MailboxPath.inbox(ANDRE)
    mailboxProbe.createMailbox(andreMailBoxPath)

    val relatedEmailId: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString(), andreMailBoxPath, AppendCommand.builder()
        .build(buildOriginalMessage("1")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:mdn"
         |  ],
         |  "methodCalls": [
         |    [
         |      "MDN/send",
         |      {
         |        "accountId": "$ANDRE_ACCOUNT_ID",
         |        "identityId": "$ANDRE_IDENTITY_ID",
         |        "send": {
         |          "k1546": {
         |            "forEmailId": "${relatedEmailId.serialize()}",
         |            "subject": "Read receipt for: World domination",
         |            "textBody": "This receipt shows that the email has been displayed on your recipient's computer. ",
         |            "finalRecipient" : "rfc822; ${CEDRIC.asString}",
         |            "disposition": {
         |              "actionMode": "manual-action",
         |              "sendingMode": "mdn-sent-manually",
         |              "type": "displayed"
         |            }
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
         |  ]
         |}""".stripMargin

    val mdnSendResponse: String =
      `given`
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

    assertThatJson(mdnSendResponse)
      .inPath(s"methodResponses[0][1].notSent")
      .isEqualTo("""{
                   |    "k1546": {
                   |        "type": "forbiddenFrom",
                   |        "description": "The user is not allowed to use the given \"finalRecipient\" property"
                   |    }
                   |}""".stripMargin)
  }

  @Test
  def implicitEmailSetShouldNotBeAttemptedWhenMDNIsNotSent(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    val andreMailBoxPath: MailboxPath = MailboxPath.inbox(ANDRE)
    mailboxProbe.createMailbox(andreMailBoxPath)

    val relatedEmailId: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString(), andreMailBoxPath, AppendCommand.builder()
        .build(buildOriginalMessage("1")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:mdn"
         |  ],
         |  "methodCalls": [
         |    [
         |      "MDN/send",
         |      {
         |        "accountId": "$ANDRE_ACCOUNT_ID",
         |        "identityId": "$ANDRE_IDENTITY_ID",
         |        "send": {
         |          "k1546": {
         |            "forEmailId": "${relatedEmailId.serialize()}",
         |            "subject": "Read receipt for: World domination",
         |            "textBody": "This receipt shows that the email has been displayed on your recipient's computer. ",
         |            "finalRecipient" : "invalid",
         |            "disposition": {
         |              "actionMode": "manual-action",
         |              "sendingMode": "mdn-sent-manually",
         |              "type": "displayed"
         |            }
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
         |  ]
         |}""".stripMargin

    val mdnSendResponse: String =
      `given`
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

    assertThatJson(mdnSendResponse)
      .inPath("methodResponses[1]")
      .isAbsent()
  }

  @Test
  def implicitEmailSetShouldNotBeAttemptedWhenOnSuccessUpdateEmailIsNull(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    val bobMailBoxPath: MailboxPath = MailboxPath.inbox(BOB)
    mailboxProbe.createMailbox(bobMailBoxPath)

    val andreMailBoxPath: MailboxPath = MailboxPath.inbox(ANDRE)
    mailboxProbe.createMailbox(andreMailBoxPath)

    val relatedEmailId: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString(), andreMailBoxPath, AppendCommand.builder()
        .build(buildOriginalMessage("1")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:mdn"
         |  ],
         |  "methodCalls": [
         |    [
         |      "MDN/send",
         |      {
         |        "accountId": "$ANDRE_ACCOUNT_ID",
         |        "identityId": "$ANDRE_IDENTITY_ID",
         |        "send": {
         |          "k1546": {
         |            "forEmailId": "${relatedEmailId.serialize()}",
         |            "subject": "Read receipt for: World domination",
         |            "textBody": "This receipt shows that the email has been displayed on your recipient's computer. ",
         |            "disposition": {
         |              "actionMode": "manual-action",
         |              "sendingMode": "mdn-sent-manually",
         |              "type": "displayed"
         |            }
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
         |  ]
         |}""".stripMargin

    val mdnSendResponse: String =
      `given`
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

    assertThatJson(mdnSendResponse)
      .whenIgnoringPaths("methodResponses[1][1].newState",
        "methodResponses[1][1].oldState")
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "MDN/send",
           |            {
           |                "accountId": "$ANDRE_ACCOUNT_ID",
           |                "sent": {
           |                    "k1546": {
           |                        "finalRecipient": "rfc822; ${ANDRE.asString}",
           |                        "includeOriginalMessage": false,
           |                        "originalRecipient": "rfc822; ${ANDRE.asString}"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def mdnSendShouldAcceptSeveralMDNObjects(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    val bobMailBoxPath: MailboxPath = MailboxPath.inbox(BOB)
    mailboxProbe.createMailbox(bobMailBoxPath)

    val andreMailBoxPath: MailboxPath = MailboxPath.inbox(ANDRE)
    mailboxProbe.createMailbox(andreMailBoxPath)

    val relatedEmailId1: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString(), andreMailBoxPath, AppendCommand.builder()
        .build(buildOriginalMessage("1")))
      .getMessageId
    val relatedEmailId2: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString(), andreMailBoxPath, AppendCommand.builder()
        .build(buildOriginalMessage("2")))
      .getMessageId
    val relatedEmailId3: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString(), andreMailBoxPath, AppendCommand.builder()
        .build(buildOriginalMessage("3")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:mdn"
         |  ],
         |  "methodCalls": [
         |    [
         |      "MDN/send",
         |      {
         |        "accountId": "$ANDRE_ACCOUNT_ID",
         |        "identityId": "$ANDRE_IDENTITY_ID",
         |        "send": {
         |          "k1546": {
         |            "forEmailId": "${relatedEmailId1.serialize()}",
         |            "disposition": {
         |              "actionMode": "manual-action",
         |              "sendingMode": "mdn-sent-manually",
         |              "type": "displayed"
         |            }
         |          },
         |          "k1547": {
         |            "forEmailId": "${relatedEmailId2.serialize()}",
         |            "disposition": {
         |              "actionMode": "manual-action",
         |              "sendingMode": "mdn-sent-manually",
         |              "type": "displayed"
         |            }
         |          },
         |          "k1548": {
         |            "forEmailId": "${relatedEmailId3.serialize()}",
         |            "disposition": {
         |              "actionMode": "manual-action",
         |              "sendingMode": "mdn-sent-manually",
         |              "type": "displayed"
         |            }
         |          }
         |        },
         |        "onSuccessUpdateEmail": {
         |          "#k1546": {
         |            "keywords/$$mdnsent": true
         |          },
         |          "#k1547": {
         |            "keywords/$$mdnsent": true
         |          },
         |          "#k1548": {
         |            "keywords/$$mdnsent": true
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
         |  ]
         |}""".stripMargin

   val mdnSendResponse: String =
      `given`
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

    assertThatJson(mdnSendResponse)
      .whenIgnoringPaths("methodResponses[1][1].newState",
        "methodResponses[1][1].oldState")
      .isEqualTo(
        s"""{
           |	"sessionState": "${SESSION_STATE.value}",
           |	"methodResponses": [
           |		[
           |			"MDN/send",
           |			{
           |				"accountId": "$ANDRE_ACCOUNT_ID",
           |				"sent": {
           |					"k1546": {
           |						"subject": "[Received] Subject of original message1",
           |						"textBody": "The email has been displayed on your recipient's computer",
           |						"originalRecipient": "rfc822; ${ANDRE.asString()}",
           |						"finalRecipient": "rfc822; ${ANDRE.asString()}",
           |						"includeOriginalMessage": false
           |					},
           |					"k1547": {
           |						"subject": "[Received] Subject of original message2",
           |						"textBody": "The email has been displayed on your recipient's computer",
           |						"originalRecipient": "rfc822; ${ANDRE.asString()}",
           |						"finalRecipient": "rfc822; ${ANDRE.asString()}",
           |						"includeOriginalMessage": false
           |					},
           |					"k1548": {
           |						"subject": "[Received] Subject of original message3",
           |						"textBody": "The email has been displayed on your recipient's computer",
           |						"originalRecipient": "rfc822; ${ANDRE.asString()}",
           |						"finalRecipient": "rfc822; ${ANDRE.asString()}",
           |						"includeOriginalMessage": false
           |					}
           |				}
           |			},
           |			"c1"
           |		],
           |		[
           |			"Email/set",
           |			{
           |				"accountId": "$ANDRE_ACCOUNT_ID",
           |				"oldState": "3be4a1bc-0b41-4e33-aaf0-585e567a5af5",
           |				"newState": "3e1d5c70-9ca4-4c02-a35c-f54a51d253e3",
           |				"updated": {
           |					"${relatedEmailId1.serialize()}": null,
           |					"${relatedEmailId2.serialize()}": null,
           |					"${relatedEmailId3.serialize()}": null
           |				}
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
  }

  @Test
  def mdnSendMixValidAndNotFoundAndInvalid(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    val bobMailBoxPath: MailboxPath = MailboxPath.inbox(BOB)
    mailboxProbe.createMailbox(bobMailBoxPath)

    val andreMailBoxPath: MailboxPath = MailboxPath.inbox(ANDRE)
    mailboxProbe.createMailbox(andreMailBoxPath)

    val validEmailId1: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString(), andreMailBoxPath, AppendCommand.builder()
        .build(buildOriginalMessage("1")))
      .getMessageId
    val validEmailId2: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString(), andreMailBoxPath, AppendCommand.builder()
        .build(buildOriginalMessage("2")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:mdn"
         |  ],
         |  "methodCalls": [
         |    [
         |      "MDN/send",
         |      {
         |        "accountId": "$ANDRE_ACCOUNT_ID",
         |        "identityId": "$ANDRE_IDENTITY_ID",
         |        "send": {
         |          "k1546": {
         |            "forEmailId": "${validEmailId1.serialize()}",
         |            "disposition": {
         |              "actionMode": "manual-action",
         |              "sendingMode": "mdn-sent-manually",
         |              "type": "displayed"
         |            }
         |          },
         |          "k1547": {
         |            "forEmailId": "${validEmailId2.serialize()}",
         |            "badProperty": {
         |              "actionMode": "manual-action",
         |              "sendingMode": "mdn-sent-manually",
         |              "type": "displayed"
         |            }
         |          },
         |          "k1548": {
         |            "forEmailId": "${randomMessageId.serialize()}",
         |            "disposition": {
         |              "actionMode": "manual-action",
         |              "sendingMode": "mdn-sent-manually",
         |              "type": "displayed"
         |            }
         |          }
         |        },
         |        "onSuccessUpdateEmail": {
         |          "#k1546": {
         |            "keywords/$$mdnsent": true
         |          },
         |          "#k1547": {
         |            "keywords/$$mdnsent": true
         |          },
         |          "#k1548": {
         |            "keywords/$$mdnsent": true
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
         |  ]
         |}""".stripMargin

   val mdnSendResponse: String =
      `given`
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

    assertThatJson(mdnSendResponse)
      .whenIgnoringPaths("methodResponses[1][1].newState",
        "methodResponses[1][1].oldState")
      .isEqualTo(s"""{
                   |    "sessionState": "${SESSION_STATE.value}",
                   |    "methodResponses": [
                   |        [
                   |            "MDN/send",
                   |            {
                   |                "accountId": "$ANDRE_ACCOUNT_ID",
                   |                "sent": {
                   |                    "k1546": {
                   |                        "subject": "[Received] Subject of original message1",
                   |                        "textBody": "The email has been displayed on your recipient's computer",
                   |                        "originalRecipient": "rfc822; ${ANDRE.asString()}",
                   |                        "finalRecipient": "rfc822; ${ANDRE.asString()}",
                   |                        "includeOriginalMessage": false
                   |                    }
                   |                },
                   |                "notSent": {
                   |                    "k1547": {
                   |                        "type": "invalidArguments",
                   |                        "description": "Some unknown properties were specified",
                   |                        "properties": [
                   |                            "badProperty"
                   |                        ]
                   |                    },
                   |                    "k1548": {
                   |                        "type": "notFound",
                   |                        "description": "The reference \\"forEmailId\\" cannot be found."
                   |                    }
                   |                }
                   |            },
                   |            "c1"
                   |        ],
                   |        [
                   |            "Email/set",
                   |            {
                   |                "accountId": "1e8584548eca20f26faf6becc1704a0f352839f12c208a47fbd486d60f491f7c",
                   |                "oldState": "eda83b09-6aca-4215-b493-2b4af19c50f0",
                   |                "newState": "8bd671b2-e9fd-4ce3-b9b2-c3e1f35cc8ee",
                   |                "updated": {
                   |                    "${validEmailId1.serialize()}": null
                   |                }
                   |            },
                   |            "c1"
                   |        ]
                   |    ]
                   |}""".stripMargin)
  }

  @Test
  def mdnSendShouldBeFailWhenMDNHasAlreadyBeenSet(server: GuiceJamesServer): Unit = {
    val path: MailboxPath = MailboxPath.inbox(BOB)
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(path)

    val relatedEmailId: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.builder()
        .build(buildOriginalMessage("1")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:mdn"
         |  ],
         |  "methodCalls": [
         |    [
         |      "MDN/send",
         |      {
         |        "accountId": "$ACCOUNT_ID",
         |        "identityId": "$IDENTITY_ID",
         |        "send": {
         |          "k1546": {
         |            "forEmailId": "${relatedEmailId.serialize()}",
         |            "disposition": {
         |              "actionMode": "manual-action",
         |              "sendingMode": "mdn-sent-manually",
         |              "type": "displayed"
         |            }
         |          }
         |        },
         |        "onSuccessUpdateEmail": {
         |          "#k1546": {
         |            "keywords/$$mdnsent": true
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
         |  ]
         |}""".stripMargin

      `given`(buildBOBRequestSpecification(server))
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)

    val response: String =
      `given`(buildBOBRequestSpecification(server))
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

    assertThatJson(response)
      .inPath(s"methodResponses[0][1].notSent")
      .isEqualTo("""{
                   |    "k1546": {
                   |        "type": "mdnAlreadySent",
                   |        "description": "The message has the $mdnsent keyword already set."
                   |    }
                   |}""".stripMargin)
  }

  @Test
  def mdnSendShouldReturnUnknownMethodWhenMissingOneCapability(server: GuiceJamesServer): Unit = {
    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"
         |  ],
         |  "methodCalls": [
         |    [
         |      "MDN/send",
         |      {
         |        "accountId": "ue150411c",
         |        "identityId": "$ANDRE_IDENTITY_ID",
         |        "send": {
         |          "k1546": {
         |            "forEmailId": "Md45b47b4877521042cec0938",
         |            "disposition": {
         |              "actionMode": "manual-action",
         |              "sendingMode": "mdn-sent-manually",
         |              "type": "displayed"
         |            }
         |          }
         |        },
         |        "onSuccessUpdateEmail": {
         |          "#k1546": {
         |            "keywords/mdnsent": true
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
         |  ]
         |}""".stripMargin

    val response: String =
      `given`
        .body(request)
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
         |      "description": "Missing capability(ies): urn:ietf:params:jmap:mdn"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def mdnSendShouldReturnUnknownMethodWhenMissingAllCapabilities(server: GuiceJamesServer): Unit = {
    val request: String =
      s"""{
         |  "using": [],
         |  "methodCalls": [
         |    [
         |      "MDN/send",
         |      {
         |        "accountId": "ue150411c",
         |        "identityId": "$ANDRE_IDENTITY_ID",
         |        "send": {
         |          "k1546": {
         |            "forEmailId": "Md45b47b4877521042cec0938",
         |            "disposition": {
         |              "actionMode": "manual-action",
         |              "sendingMode": "mdn-sent-manually",
         |              "type": "displayed"
         |            }
         |          }
         |        },
         |        "onSuccessUpdateEmail": {
         |          "#k1546": {
         |            "keywords/mdnsent": true
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
         |  ]
         |}""".stripMargin

    val response: String =
      `given`(buildBOBRequestSpecification(server))
        .body(request)
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
         |      "description": "Missing capability(ies): urn:ietf:params:jmap:mdn, urn:ietf:params:jmap:mail, urn:ietf:params:jmap:core"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def mdnSendShouldReturnNotFoundWhenForEmailIdIsNotExist(server: GuiceJamesServer): Unit = {
    val path: MailboxPath = MailboxPath.inbox(BOB)
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(path)

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:mdn"
         |  ],
         |  "methodCalls": [
         |    [
         |      "MDN/send",
         |      {
         |        "accountId": "$ACCOUNT_ID",
         |        "identityId": "$IDENTITY_ID",
         |        "send": {
         |          "k1546": {
         |            "forEmailId": "${randomMessageId.serialize()}",
         |            "disposition": {
         |              "actionMode": "manual-action",
         |              "sendingMode": "mdn-sent-manually",
         |              "type": "displayed"
         |            }
         |          }
         |        },
         |        "onSuccessUpdateEmail": {
         |          "#k1546": {
         |            "keywords/mdnsent": true
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
         |  ]
         |}""".stripMargin

    val response: String =
      `given`(buildBOBRequestSpecification(server))
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

    assertThatJson(response)
      .inPath("methodResponses[0][1].notSent")
      .isEqualTo("""{
                   |    "k1546": {
                   |        "type": "notFound",
                   |        "description": "The reference \"forEmailId\" cannot be found."
                   |    }
                   |}""".stripMargin)
  }

  @Test
  def mdnSendShouldReturnNotFoundWhenMessageRelateHasNotDispositionNotificationTo(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    val bobMailBoxPath: MailboxPath = MailboxPath.inbox(BOB)
    mailboxProbe.createMailbox(bobMailBoxPath)

    val andreMailBoxPath: MailboxPath = MailboxPath.inbox(ANDRE)
    mailboxProbe.createMailbox(andreMailBoxPath)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(BOB.asString)
      .setFrom(BOB.asString)
      .setTo(ANDRE.asString)
      .setBody("Body of mail, that mdn related", StandardCharsets.UTF_8)
      .build

    val relatedEmailId: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString(), andreMailBoxPath, AppendCommand.builder()
        .build(message))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:mdn"
         |  ],
         |  "methodCalls": [
         |    [
         |      "MDN/send",
         |      {
         |        "accountId": "$ANDRE_ACCOUNT_ID",
         |        "identityId": "$ANDRE_IDENTITY_ID",
         |        "send": {
         |          "k1546": {
         |            "forEmailId": "${relatedEmailId.serialize()}",
         |            "disposition": {
         |              "actionMode": "manual-action",
         |              "sendingMode": "mdn-sent-manually",
         |              "type": "displayed"
         |            }
         |          }
         |        },
         |        "onSuccessUpdateEmail": {
         |          "#k1546": {
         |            "keywords/mdnsent": true
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
         |  ]
         |}""".stripMargin

    val response: String =
      `given`
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

    assertThatJson(response)
      .inPath("methodResponses[0][1].notSent")
      .isEqualTo("""{
                    |    "k1546": {
                    |        "type": "notFound",
                    |        "description": "Invalid \"Disposition-Notification-To\" header field."
                    |    }
                    |}""".stripMargin)
  }

  @Test
  def mdnSendShouldReturnInvalidWhenIdentityDoesNotExist(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    val bobMailBoxPath: MailboxPath = MailboxPath.inbox(BOB)
    mailboxProbe.createMailbox(bobMailBoxPath)

    val andreMailBoxPath: MailboxPath = MailboxPath.inbox(ANDRE)
    mailboxProbe.createMailbox(andreMailBoxPath)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(BOB.asString)
      .setFrom(BOB.asString)
      .setTo(ANDRE.asString)
      .setBody("Body of mail, that mdn related", StandardCharsets.UTF_8)
      .build

    val relatedEmailId: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString(), andreMailBoxPath, AppendCommand.builder()
        .build(message))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:mdn"
         |  ],
         |  "methodCalls": [
         |    [
         |      "MDN/send",
         |      {
         |        "accountId": "$ANDRE_ACCOUNT_ID",
         |        "identityId": "notFound",
         |        "send": {
         |          "k1546": {
         |            "forEmailId": "${relatedEmailId.serialize()}",
         |            "disposition": {
         |              "actionMode": "manual-action",
         |              "sendingMode": "mdn-sent-manually",
         |              "type": "displayed"
         |            }
         |          }
         |        },
         |        "onSuccessUpdateEmail": {
         |          "#k1546": {
         |            "keywords/mdnsent": true
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
         |  ]
         |}""".stripMargin

    val response: String =
      `given`
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

    assertThatJson(response)
      .isEqualTo(s"""{
                   |    "sessionState": "${SESSION_STATE.value}",
                   |    "methodResponses": [
                   |        [
                   |            "error",
                   |            {
                   |                "type": "invalidArguments",
                   |                "description": "The IdentityId cannot be found"
                   |            },
                   |            "c1"
                   |        ]
                   |    ]
                   |}""".stripMargin)
  }

  @Test
  def mdnSendShouldBeFailWhenWrongAccountId(server: GuiceJamesServer): Unit = {
    val path: MailboxPath = MailboxPath.inbox(BOB)
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(path)

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:mdn"
         |  ],
         |  "methodCalls": [
         |    [
         |      "MDN/send",
         |      {
         |        "accountId": "unknownAccountId",
         |        "identityId": "$ANDRE_IDENTITY_ID",
         |        "send": {
         |          "k1546": {
         |            "forEmailId": "1",
         |            "disposition": {
         |              "actionMode": "manual-action",
         |              "sendingMode": "mdn-sent-manually",
         |              "type": "displayed"
         |            }
         |          }
         |        },
         |        "onSuccessUpdateEmail": {
         |          "#k1546": {
         |            "keywords/mdnsent": true
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
         |  ]
         |}""".stripMargin

    val response: String =
      `given`(buildBOBRequestSpecification(server))
        .body(request)
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [[
         |            "error",
         |            {
         |                "type": "accountNotFound"
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def mdnSendShouldBeFailWhenOnSuccessUpdateEmailMissesTheCreationIdSharp(server: GuiceJamesServer): Unit = {
    val path: MailboxPath = MailboxPath.inbox(BOB)
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(path)

    val relatedEmailId: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.builder()
        .build(buildOriginalMessage("1")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:mdn"
         |  ],
         |  "methodCalls": [
         |    [
         |      "MDN/send",
         |      {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "identityId": "$ANDRE_IDENTITY_ID",
         |        "send": {
         |          "k1546": {
         |            "forEmailId": "${relatedEmailId.serialize()}",
         |            "subject": "Read receipt for: World domination",
         |            "textBody": "This receipt shows that the email has been displayed on your recipient's computer. ",
         |            "reportingUA": "joes-pc.cs.example.com; Foomail 97.1",
         |            "disposition": {
         |              "actionMode": "manual-action",
         |              "sendingMode": "mdn-sent-manually",
         |              "type": "displayed"
         |            }
         |          }
         |        },
         |        "onSuccessUpdateEmail": {
         |          "notStored": {
         |            "keywords/$$mdnsent": true
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
         |  ]
         |}""".stripMargin

    val response: String =
      `given`(buildBOBRequestSpecification(server))
        .body(request)
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
      .isEqualTo(
        s"""[
           |    "error",
           |    {
           |        "type": "invalidArguments",
           |        "description": "notStored cannot be retrieved as storage for MDNSend is not yet implemented"
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def mdnSendShouldBeFailWhenOnSuccessUpdateEmailDoesNotReferenceACreationWithinThisCall(server: GuiceJamesServer): Unit = {
    val path: MailboxPath = MailboxPath.inbox(BOB)
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(path)

    val relatedEmailId: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.builder()
        .build(buildOriginalMessage("1")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:mdn"
         |  ],
         |  "methodCalls": [
         |    [
         |      "MDN/send",
         |      {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "identityId": "$ANDRE_IDENTITY_ID",
         |        "send": {
         |          "k1546": {
         |            "forEmailId": "${relatedEmailId.serialize()}",
         |            "subject": "Read receipt for: World domination",
         |            "textBody": "This receipt shows that the email has been displayed on your recipient's computer. ",
         |            "reportingUA": "joes-pc.cs.example.com; Foomail 97.1",
         |            "disposition": {
         |              "actionMode": "manual-action",
         |              "sendingMode": "mdn-sent-manually",
         |              "type": "displayed"
         |            }
         |          }
         |        },
         |        "onSuccessUpdateEmail": {
         |          "#notReference": {
         |            "keywords/$$mdnsent": true
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
         |  ]
         |}""".stripMargin

    val response: String =
      `given`(buildBOBRequestSpecification(server))
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "error",
           |            {
           |                "type": "invalidArguments",
           |                "description": "#notReference cannot be referenced in current method call"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Tag(TAG_MDN_MESSAGE_FORMAT)
  @Test
  def mdnSendShouldReturnSubjectWhenRequestDoNotSet(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    val bobMailBoxPath: MailboxPath = MailboxPath.inbox(BOB)
    mailboxProbe.createMailbox(bobMailBoxPath)

    val andreMailBoxPath: MailboxPath = MailboxPath.inbox(ANDRE)
    mailboxProbe.createMailbox(andreMailBoxPath)

    val relatedEmailId: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString(), andreMailBoxPath, AppendCommand.builder()
        .build(buildOriginalMessage("1")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:mdn"
         |  ],
         |  "methodCalls": [
         |    [
         |      "MDN/send",
         |      {
         |        "accountId": "$ANDRE_ACCOUNT_ID",
         |        "identityId": "$ANDRE_IDENTITY_ID",
         |        "send": {
         |          "k1546": {
         |            "forEmailId": "${relatedEmailId.serialize()}",
         |            "textBody": "This receipt shows that the email has been displayed on your recipient's computer. ",
         |            "reportingUA": "joes-pc.cs.example.com; Foomail 97.1",
         |            "disposition": {
         |              "actionMode": "manual-action",
         |              "sendingMode": "mdn-sent-manually",
         |              "type": "displayed"
         |            }
         |          }
         |        },
         |        "onSuccessUpdateEmail": {
         |          "#k1546": {
         |            "keywords/$$mdnsent": true
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
         |  ]
         |}""".stripMargin

    val mdnSendResponse: String =
      `given`
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

    assertThatJson(mdnSendResponse)
      .inPath("methodResponses[0][1].sent.k1546.subject")
      .asString().isEqualTo("[Received] Subject of original message1")
  }

  @Tag(TAG_MDN_MESSAGE_FORMAT)
  @Test
  def mdnSendShouldReturnTextBodyWhenRequestDoNotSet(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    val bobMailBoxPath: MailboxPath = MailboxPath.inbox(BOB)
    mailboxProbe.createMailbox(bobMailBoxPath)

    val andreMailBoxPath: MailboxPath = MailboxPath.inbox(ANDRE)
    mailboxProbe.createMailbox(andreMailBoxPath)

    val relatedEmailId: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString(), andreMailBoxPath, AppendCommand.builder()
        .build(buildOriginalMessage("1")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:mdn"
         |  ],
         |  "methodCalls": [
         |    [
         |      "MDN/send",
         |      {
         |        "accountId": "$ANDRE_ACCOUNT_ID",
         |        "identityId": "$ANDRE_IDENTITY_ID",
         |        "send": {
         |          "k1546": {
         |            "forEmailId": "${relatedEmailId.serialize()}",
         |            "reportingUA": "joes-pc.cs.example.com; Foomail 97.1",
         |            "disposition": {
         |              "actionMode": "manual-action",
         |              "sendingMode": "mdn-sent-manually",
         |              "type": "displayed"
         |            }
         |          }
         |        },
         |        "onSuccessUpdateEmail": {
         |          "#k1546": {
         |            "keywords/$$mdnsent": true
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
         |  ]
         |}""".stripMargin

    val mdnSendResponse: String =
      `given`
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

    assertThatJson(mdnSendResponse)
      .inPath("methodResponses[0][1].sent.k1546.textBody")
      .asString().isEqualTo("The email has been displayed on your recipient's computer")
  }

  @Tag(TAG_MDN_MESSAGE_FORMAT)
  @Test
  def mdnSendShouldReturnOriginalMessageIdWhenRelatedMessageHasMessageIDHeader(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    val bobMailBoxPath: MailboxPath = MailboxPath.inbox(BOB)
    mailboxProbe.createMailbox(bobMailBoxPath)

    val andreMailBoxPath: MailboxPath = MailboxPath.inbox(ANDRE)
    mailboxProbe.createMailbox(andreMailBoxPath)

    val relatedEmailId: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString(), andreMailBoxPath, AppendCommand.builder()
        .build(Message.Builder
          .of
          .setSubject(s"Subject of original message")
          .setSender(BOB.asString)
          .setFrom(BOB.asString)
          .setTo(ANDRE.asString)
          .addField(new RawField("Disposition-Notification-To", s"Bob <${BOB.asString()}>"))
          .addField(new RawField("Message-Id", "<199509192301.23456@example.org>"))
          .setBody(s"Body of mail, that mdn related", StandardCharsets.UTF_8)
          .build
        ))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:mdn"
         |  ],
         |  "methodCalls": [
         |    [
         |      "MDN/send",
         |      {
         |        "accountId": "$ANDRE_ACCOUNT_ID",
         |        "identityId": "$ANDRE_IDENTITY_ID",
         |        "send": {
         |          "k1546": {
         |            "forEmailId": "${relatedEmailId.serialize()}",
         |            "reportingUA": "joes-pc.cs.example.com; Foomail 97.1",
         |            "disposition": {
         |              "actionMode": "manual-action",
         |              "sendingMode": "mdn-sent-manually",
         |              "type": "displayed"
         |            }
         |          }
         |        },
         |        "onSuccessUpdateEmail": {
         |          "#k1546": {
         |            "keywords/$$mdnsent": true
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
         |  ]
         |}""".stripMargin

    val mdnSendResponse: String =
      `given`
        .body(request)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

    assertThatJson(mdnSendResponse)
      .inPath("methodResponses[0][1].sent.k1546.originalMessageId")
      .asString().isEqualTo("<199509192301.23456@example.org>")
  }

  @Tag(TAG_MDN_MESSAGE_FORMAT)
  @Test
  def mdnMessageShouldHasThirdBodyPartWhenIncludeOriginalMessageIsTrue(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    val bobMailBoxPath: MailboxPath = MailboxPath.inbox(BOB)
    mailboxProbe.createMailbox(bobMailBoxPath)

    val andreMailBoxPath: MailboxPath = MailboxPath.inbox(ANDRE)
    mailboxProbe.createMailbox(andreMailBoxPath)

    val relatedEmailId: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString(), andreMailBoxPath, AppendCommand.builder()
        .build(buildOriginalMessage("1")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:mdn"
         |  ],
         |  "methodCalls": [
         |    [
         |      "MDN/send",
         |      {
         |        "accountId": "$ANDRE_ACCOUNT_ID",
         |        "identityId": "$ANDRE_IDENTITY_ID",
         |        "send": {
         |          "k1546": {
         |            "forEmailId": "${relatedEmailId.serialize()}",
         |            "subject": "Read receipt for: World domination",
         |            "textBody": "This receipt shows that the email has been displayed on your recipient's computer. ",
         |            "reportingUA": "joes-pc.cs.example.com; Foomail 97.1",
         |            "disposition": {
         |              "actionMode": "manual-action",
         |              "sendingMode": "mdn-sent-manually",
         |              "type": "displayed"
         |            },
         |            "includeOriginalMessage": true,
         |            "extensionFields": {
         |              "X-EXTENSION-EXAMPLE": "example.com"
         |            }
         |          }
         |        },
         |        "onSuccessUpdateEmail": {
         |          "#k1546": {
         |            "keywords/$$mdnsent": true
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
         |  ]
         |}""".stripMargin

    `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    awaitAtMostTenSeconds.untilAsserted { () =>
      val mdnBodyPartCounter = getFirstMessageInMailBox(server, BOB)
        .filter(msg => msg.isMultipart)
        .map(msg => msg.getBody.asInstanceOf[Multipart].getBodyParts)
      assert(mdnBodyPartCounter.isDefined && mdnBodyPartCounter.get.size == 3)
    }
  }
}
