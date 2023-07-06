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
import java.time.{Instant, LocalDateTime}
import java.util.concurrent.TimeUnit

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, `with`, requestSpecification}
import io.restassured.builder.ResponseSpecBuilder
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.EmailSubmissionSetMethodFutureReleaseContract.{DATE, future_release_session_object}
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ALICE, ANDRE, ANDRE_ACCOUNT_ID, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.mailbox.DefaultMailboxes
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.{MailboxId, MailboxPath, MessageId}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.{DataProbeImpl, UpdatableTickingClock}
import org.assertj.core.api.Assertions
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.awaitility.core.ConditionTimeoutException
import org.hamcrest.text.CharSequenceLength
import org.hamcrest.{Matcher, Matchers}
import org.junit.jupiter.api.{BeforeEach, Tag, Test}

case object EmailSubmissionSetMethodFutureReleaseContract {
  private val future_release_session_object: String =
    """{
      |  "capabilities" : {
      |    "urn:ietf:params:jmap:submission": {
      |      "maxDelayedSend": 86400,
      |      "submissionExtensions": {"FUTURERELEASE": ["86400", "2023-04-15T10:00:00Z"]}
      |    },
      |    "urn:ietf:params:jmap:core" : {
      |      "maxSizeUpload" : 20971520,
      |      "maxConcurrentUpload" : 4,
      |      "maxSizeRequest" : 10000000,
      |      "maxConcurrentRequests" : 4,
      |      "maxCallsInRequest" : 16,
      |      "maxObjectsInGet" : 500,
      |      "maxObjectsInSet" : 500,
      |      "collationAlgorithms" : [ "i;unicode-casemap" ]
      |    },
      |    "urn:ietf:params:jmap:mail" : {
      |      "maxMailboxesPerEmail" : 10000000,
      |      "maxMailboxDepth" : null,
      |      "maxSizeMailboxName" : 200,
      |      "maxSizeAttachmentsPerEmail" : 20000000,
      |      "emailQuerySortOptions" : ["receivedAt", "sentAt", "size", "from", "to", "subject"],
      |      "mayCreateTopLevelMailbox" : true
      |    },
      |    "urn:ietf:params:jmap:websocket": {
      |      "supportsPush": true,
      |      "url": "ws://domain.com/jmap/ws"
      |    },
      |    "urn:apache:james:params:jmap:mail:quota": {},
      |    "urn:ietf:params:jmap:quota": {},
      |    "urn:apache:james:params:jmap:mail:identity:sortorder": {},
      |    "urn:apache:james:params:jmap:delegation": {},
      |    "urn:apache:james:params:jmap:mail:shares": {},
      |    "urn:ietf:params:jmap:vacationresponse":{},
      |    "urn:ietf:params:jmap:mdn":{}
      |  },
      |  "accounts" : {
      |    "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6" : {
      |      "name" : "bob@domain.tld",
      |      "isPersonal" : true,
      |      "isReadOnly" : false,
      |      "accountCapabilities" : {
      |        "urn:ietf:params:jmap:submission": {
      |          "maxDelayedSend": 86400,
      |          "submissionExtensions": {"FUTURERELEASE": ["86400", "2023-04-15T10:00:00Z"]}
      |        },
      |        "urn:ietf:params:jmap:websocket": {
      |            "supportsPush": true,
      |            "url": "ws://domain.com/jmap/ws"
      |        },
      |        "urn:ietf:params:jmap:core" : {
      |          "maxSizeUpload" : 20971520,
      |          "maxConcurrentUpload" : 4,
      |          "maxSizeRequest" : 10000000,
      |          "maxConcurrentRequests" : 4,
      |          "maxCallsInRequest" : 16,
      |          "maxObjectsInGet" : 500,
      |          "maxObjectsInSet" : 500,
      |          "collationAlgorithms" : [ "i;unicode-casemap" ]
      |        },
      |        "urn:ietf:params:jmap:mail" : {
      |          "maxMailboxesPerEmail" : 10000000,
      |          "maxMailboxDepth" : null,
      |          "maxSizeMailboxName" : 200,
      |          "maxSizeAttachmentsPerEmail" : 20000000,
      |          "emailQuerySortOptions" : ["receivedAt", "sentAt", "size", "from", "to", "subject"],
      |          "mayCreateTopLevelMailbox" : true
      |        },
      |        "urn:apache:james:params:jmap:mail:quota": {},
      |        "urn:ietf:params:jmap:quota": {},
      |        "urn:apache:james:params:jmap:mail:identity:sortorder": {},
      |        "urn:apache:james:params:jmap:delegation": {},
      |        "urn:apache:james:params:jmap:mail:shares": {},
      |        "urn:ietf:params:jmap:vacationresponse":{},
      |        "urn:ietf:params:jmap:mdn":{}
      |      }
      |    }
      |  },
      |  "primaryAccounts" : {
      |    "urn:ietf:params:jmap:submission": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
      |    "urn:ietf:params:jmap:websocket": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
      |    "urn:ietf:params:jmap:core" : "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
      |    "urn:ietf:params:jmap:mail" : "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
      |    "urn:apache:james:params:jmap:mail:quota": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
      |    "urn:ietf:params:jmap:quota": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
      |    "urn:apache:james:params:jmap:mail:identity:sortorder": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
      |    "urn:apache:james:params:jmap:delegation": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
      |    "urn:apache:james:params:jmap:mail:shares": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
      |    "urn:ietf:params:jmap:vacationresponse": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
      |    "urn:ietf:params:jmap:mdn": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
      |  },
      |  "username" : "bob@domain.tld",
      |  "apiUrl" : "http://domain.com/jmap",
      |  "downloadUrl" : "http://domain.com/download/{accountId}/{blobId}?type={type}&name={name}",
      |  "uploadUrl" : "http://domain.com/upload/{accountId}",
      |  "eventSourceUrl" : "http://domain.com/eventSource?types={types}&closeAfter={closeafter}&ping={ping}",
      |  "state" : "2c9f1b12-b35a-43e6-9af2-0106fb53a943"
      |}""".stripMargin

  val DATE: Instant = Instant.parse("2023-04-14T10:00:00.00Z")
  private val CLOCK: UpdatableTickingClock = new UpdatableTickingClock(DATE)
  val now: LocalDateTime = LocalDateTime.now(CLOCK)
}

trait EmailSubmissionSetMethodFutureReleaseContract {
  private lazy val slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS
  private lazy val calmlyAwait = Awaitility.`with`
    .pollInterval(slowPacedPollInterval)
    .and.`with`.pollDelay(slowPacedPollInterval)
    .await
  private lazy val awaitAtMostTenSeconds = calmlyAwait.atMost(10, TimeUnit.SECONDS)

  @BeforeEach
  def setUp(server: GuiceJamesServer, updatableTickingClock: UpdatableTickingClock): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)
      .addUser(ANDRE.asString, ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build

    updatableTickingClock.setInstant(DATE)
  }

  def randomMessageId: MessageId

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def serverShouldBeAdvertisedFutureReleaseExtension(): Unit = {
    val sessionJson: String = `given`()
    .when()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()
    assertThatJson(sessionJson).isEqualTo(future_release_session_object)
  }

  @Test
  def emailSubmissionSetCreateShouldSubmitedMailSuccessfullyWithHoldFor(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(BOB.asString)
      .setFrom(BOB.asString)
      .setTo(ANDRE.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		["EmailSubmission/set", {
         |			"accountId": "$ACCOUNT_ID",
         |			"create": {
         |				"k1490": {
         |					"emailId": "${messageId.serialize}",
         |					"envelope": {
         |						"mailFrom": {
         |							"email": "${BOB.asString}",
         |							"parameters": {
         |							  "holdFor": "76000"
         |							}
         |						},
         |						"rcptTo": [{
         |							"email": "${ANDRE.asString}"
         |						}]
         |					}
         |				}
         |			}
         |		}, "c1"]
         |	]
         |}""".stripMargin

    val greaterThanZero: Matcher[Integer] = Matchers.greaterThan(0)
    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body("methodResponses[0][1].created.k1490.id", CharSequenceLength.hasLength(greaterThanZero))
  }

  @Test
  def emailSubmissionSetCreateShouldDelayEmailWithHoldFor(server: GuiceJamesServer, updatableTickingClock: UpdatableTickingClock): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(BOB.asString)
      .setFrom(BOB.asString)
      .setTo(ANDRE.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId
    val andreInboxPath = MailboxPath.inbox(ANDRE)
    val andreInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreInboxPath)

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		["EmailSubmission/set", {
         |			"accountId": "$ACCOUNT_ID",
         |			"create": {
         |				"k1490": {
         |					"emailId": "${messageId.serialize}",
         |					"envelope": {
         |						"mailFrom": {
         |							"email": "${BOB.asString}",
         |							"parameters": {
         |							  "holdFor": "76000"
         |							}
         |						},
         |						"rcptTo": [{
         |							"email": "${ANDRE.asString}"
         |						}]
         |					}
         |				}
         |			}
         |		}, "c1"]
         |	]
         |}""".stripMargin

    `with`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
      .post

    Thread.sleep(1000)

    // Ensure Andre did not receive the email
    val requestAndre =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$ANDRE_ACCOUNT_ID",
         |      "filter": {"inMailbox": "${andreInboxId.serialize}"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`(
        baseRequestSpecBuilder(server)
          .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
          .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
          .setBody(requestAndre)
          .build, new ResponseSpecBuilder().build)
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
        .hasSize(0)
    }
  }

  @Test
  def emailSubmissionSetCreateShouldDeliverEmailWhenHoldForExpired(server: GuiceJamesServer, updatableTickingClock: UpdatableTickingClock): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(BOB.asString)
      .setFrom(BOB.asString)
      .setTo(ANDRE.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId
    val andreInboxPath = MailboxPath.inbox(ANDRE)
    val andreInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreInboxPath)

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		["EmailSubmission/set", {
         |			"accountId": "$ACCOUNT_ID",
         |			"create": {
         |				"k1490": {
         |					"emailId": "${messageId.serialize}",
         |					"envelope": {
         |						"mailFrom": {
         |							"email": "${BOB.asString}",
         |							"parameters": {
         |							  "holdFor": "20000"
         |						  	}
         |						},
         |						"rcptTo": [{
         |							"email": "${ANDRE.asString}"
         |						}]
         |					}
         |				}
         |			}
         |		}, "c1"]
         |	]
         |}""".stripMargin

    `with`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
      .post

    val requestAndre =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$ANDRE_ACCOUNT_ID",
         |      "filter": {"inMailbox": "${andreInboxId.serialize}"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    Assertions.assertThatThrownBy(() => {
      calmlyAwait.atMost(1, TimeUnit.SECONDS).untilAsserted { () =>
        val response = `given`(
          baseRequestSpecBuilder(server)
            .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
            .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
            .setBody(requestAndre)
            .build, new ResponseSpecBuilder().build)
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
          .hasSizeGreaterThan(0)
      }
    })
      .isInstanceOf(classOf[ConditionTimeoutException])

    updatableTickingClock.setInstant(DATE.plusSeconds(20000))

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`(
        baseRequestSpecBuilder(server)
          .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
          .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
          .setBody(requestAndre)
          .build, new ResponseSpecBuilder().build)
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
  def emailSubmissionSetCreateShouldBeRejectedEmailWhenHoldForGreaterThanSupportedValue(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(BOB.asString)
      .setFrom(BOB.asString)
      .setTo(ANDRE.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		["EmailSubmission/set", {
         |			"accountId": "$ACCOUNT_ID",
         |			"create": {
         |				"k1490": {
         |					"emailId": "${messageId.serialize}",
         |					"envelope": {
         |						"mailFrom": {
         |							"email": "${BOB.asString}",
         |							"parameters": {
         |							  "holdFor": "7776000"
         |							}
         |						},
         |						"rcptTo": [{
         |							"email": "${ANDRE.asString}"
         |						}]
         |					}
         |				}
         |			}
         |		}, "c1"]
         |	]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
      .when
      .post
      .`then`
      .statusCode(SC_OK)
      .body("methodResponses[0][1].notCreated.k1490.type", Matchers.is("invalidArguments"))
      .body("methodResponses[0][1].notCreated.k1490.description", Matchers.is("Invalid delayed time!"))
  }

  @Test
  def emailSubmissionSetCreateShouldBeRejectedEmailWhenHoldForIsNegative (server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(BOB.asString)
      .setFrom(BOB.asString)
      .setTo(ANDRE.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		["EmailSubmission/set", {
         |			"accountId": "$ACCOUNT_ID",
         |			"create": {
         |				"k1490": {
         |					"emailId": "${messageId.serialize}",
         |					"envelope": {
         |						"mailFrom": {
         |							"email": "${BOB.asString}",
         |							"parameters": {
         |							  "holdFor": "-1000"
         |							}
         |						},
         |						"rcptTo": [{
         |							"email": "${ANDRE.asString}"
         |						}]
         |					}
         |				}
         |			}
         |		}, "c1"]
         |	]
         |}""".stripMargin

    `with`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
      .post
      .`then`
      .statusCode(SC_OK)
      .body("methodResponses[0][1].notCreated.k1490.type", Matchers.is("invalidArguments"))
      .body("methodResponses[0][1].notCreated.k1490.description", Matchers.is("Invalid delayed time!"))
  }

  @Test
  def emailSubmissionSetCreateShouldDeliveryEmailWhenHoldForIsZero(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(BOB.asString)
      .setFrom(BOB.asString)
      .setTo(ANDRE.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId
    val andreInboxPath = MailboxPath.inbox(ANDRE)
    val andreInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreInboxPath)

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		["EmailSubmission/set", {
         |			"accountId": "$ACCOUNT_ID",
         |			"create": {
         |				"k1490": {
         |					"emailId": "${messageId.serialize}",
         |					"envelope": {
         |						"mailFrom": {
         |							"email": "${BOB.asString}",
         |							"parameters": {
         |							  "holdFor": "0"
         |							}
         |						},
         |						"rcptTo": [{
         |							"email": "${ANDRE.asString}"
         |						}]
         |					}
         |				}
         |			}
         |		}, "c1"]
         |	]
         |}""".stripMargin

    `with`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
      .post

    // Ensure Andre receive the email immediately
    val requestAndre =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$ANDRE_ACCOUNT_ID",
         |      "filter": {"inMailbox": "${andreInboxId.serialize}"}
         |    },
         |    "c1"]]
         |}""".stripMargin
    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`(
        baseRequestSpecBuilder(server)
          .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
          .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
          .setBody(requestAndre)
          .build, new ResponseSpecBuilder().build)
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
  def emailSubmissionSetCreateShouldBeRejectedEmailWhenHoldForIsNotANumber(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(BOB.asString)
      .setFrom(BOB.asString)
      .setTo(ANDRE.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		["EmailSubmission/set", {
         |			"accountId": "$ACCOUNT_ID",
         |			"create": {
         |				"k1490": {
         |					"emailId": "${messageId.serialize}",
         |					"envelope": {
         |						"mailFrom": {
         |							"email": "${BOB.asString}",
         |							"parameters": {
         |							  "holdFor": "not a number"
         |							}
         |						},
         |						"rcptTo": [{
         |							"email": "${ANDRE.asString}"
         |						}]
         |					}
         |				}
         |			}
         |		}, "c1"]
         |	]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
      .when
      .post
      .`then`
      .statusCode(SC_OK)
      .body("methodResponses[0][1].notCreated.k1490.type", Matchers.is("invalidArguments"))
      .body("methodResponses[0][1].notCreated.k1490.description", Matchers.is(s"For input string: \"not a number\""))
  }

  @Test
  def emailSubmissionSetCreateShouldDelayEmailWithHoldUntil(server: GuiceJamesServer, updatableTickingClock: UpdatableTickingClock): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(BOB.asString)
      .setFrom(BOB.asString)
      .setTo(ANDRE.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId
    val andreInboxPath = MailboxPath.inbox(ANDRE)
    val andreInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreInboxPath)

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		["EmailSubmission/set", {
         |			"accountId": "$ACCOUNT_ID",
         |			"create": {
         |				"k1490": {
         |					"emailId": "${messageId.serialize}",
         |					"envelope": {
         |						"mailFrom": {
         |							"email": "${BOB.asString}",
         |							"parameters": {
         |							  "holdUntil": "2023-04-14T15:00:00Z"
         |							}
         |						},
         |						"rcptTo": [{
         |							"email": "${ANDRE.asString}"
         |						}]
         |					}
         |				}
         |			}
         |		}, "c1"]
         |	]
         |}""".stripMargin

    `with`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
      .post

    Thread.sleep(1000)

    // Ensure Andre did not receive the email
    val requestAndre =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$ANDRE_ACCOUNT_ID",
         |      "filter": {"inMailbox": "${andreInboxId.serialize}"}
         |    },
         |    "c1"]]
         |}""".stripMargin
    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`(
        baseRequestSpecBuilder(server)
          .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
          .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
          .setBody(requestAndre)
          .build, new ResponseSpecBuilder().build)
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
        .hasSize(0)
    }
  }

  @Test
  def emailSubmissionSetCreateShouldDeliverEmailWhenHoldUntilExpired(server: GuiceJamesServer, updatableTickingClock: UpdatableTickingClock): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(BOB.asString)
      .setFrom(BOB.asString)
      .setTo(ANDRE.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId
    val andreInboxPath = MailboxPath.inbox(ANDRE)
    val andreInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreInboxPath)

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		["EmailSubmission/set", {
         |			"accountId": "$ACCOUNT_ID",
         |			"create": {
         |				"k1490": {
         |					"emailId": "${messageId.serialize}",
         |					"envelope": {
         |						"mailFrom": {
         |							"email": "${BOB.asString}",
         |							"parameters": {
         |							  "holdUntil": "2023-04-14T15:00:00Z"
         |							}
         |						},
         |						"rcptTo": [{
         |							"email": "${ANDRE.asString}"
         |						}]
         |					}
         |				}
         |			}
         |		}, "c1"]
         |	]
         |}""".stripMargin

    `with`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
      .post

    val requestAndre =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$ANDRE_ACCOUNT_ID",
         |      "filter": {"inMailbox": "${andreInboxId.serialize}"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    Assertions.assertThatThrownBy(() => {
      calmlyAwait.atMost(1, TimeUnit.SECONDS).untilAsserted { () =>
        val response = `given`(
          baseRequestSpecBuilder(server)
            .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
            .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
            .setBody(requestAndre)
            .build, new ResponseSpecBuilder().build)
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
          .hasSizeGreaterThan(0)
      }
    })
      .isInstanceOf(classOf[ConditionTimeoutException])

    updatableTickingClock.setInstant(DATE.plusSeconds(18000))
    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`(
        baseRequestSpecBuilder(server)
          .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
          .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
          .setBody(requestAndre)
          .build, new ResponseSpecBuilder().build)
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
  def emailSubmissionSetCreateShouldBeRejectedEmailWhenHoldUntilIsInThePast(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(BOB.asString)
      .setFrom(BOB.asString)
      .setTo(ANDRE.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		["EmailSubmission/set", {
         |			"accountId": "$ACCOUNT_ID",
         |			"create": {
         |				"k1490": {
         |					"emailId": "${messageId.serialize}",
         |					"envelope": {
         |						"mailFrom": {
         |							"email": "${BOB.asString}",
         |							"parameters": {
         |							  "holdUntil": "2023-04-13T15:00:00Z"
         |							}
         |						},
         |						"rcptTo": [{
         |							"email": "${ANDRE.asString}"
         |						}]
         |					}
         |				}
         |			}
         |		}, "c1"]
         |	]
         |}""".stripMargin

    `with`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
      .post
      .`then`
      .statusCode(SC_OK)
      .body("methodResponses[0][1].notCreated.k1490.type", Matchers.is("invalidArguments"))
      .body("methodResponses[0][1].notCreated.k1490.description", Matchers.is("Invalid delayed time!"))
  }

  @Test
  def emailSubmissionSetCreateShouldBeRejectedEmailWhenHoldUntilTooFar(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(BOB.asString)
      .setFrom(BOB.asString)
      .setTo(ANDRE.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		["EmailSubmission/set", {
         |			"accountId": "$ACCOUNT_ID",
         |			"create": {
         |				"k1490": {
         |					"emailId": "${messageId.serialize}",
         |					"envelope": {
         |						"mailFrom": {
         |							"email": "${BOB.asString}",
         |							"parameters": {
         |							  "holdUntil": "2023-04-16T10:00:00Z"
         |							}
         |						},
         |						"rcptTo": [{
         |							"email": "${ANDRE.asString}"
         |						}]
         |					}
         |				}
         |			}
         |		}, "c1"]
         |	]
         |}""".stripMargin

    `with`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
      .post
      .`then`
      .statusCode(SC_OK)
      .body("methodResponses[0][1].notCreated.k1490.type", Matchers.is("invalidArguments"))
      .body("methodResponses[0][1].notCreated.k1490.description", Matchers.is("Invalid delayed time!"))
  }

  @Test
  def emailSubmissionSetCreateShouldSubmitedMailSuccessfullyWithHoldUntil(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(BOB.asString)
      .setFrom(BOB.asString)
      .setTo(ANDRE.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		["EmailSubmission/set", {
         |			"accountId": "$ACCOUNT_ID",
         |			"create": {
         |				"k1490": {
         |					"emailId": "${messageId.serialize}",
         |					"envelope": {
         |						"mailFrom": {
         |							"email": "${BOB.asString}",
         |							"parameters": {
         |							  "holdUntil": "2023-04-14T10:30:00Z"
         |							}
         |						},
         |						"rcptTo": [{
         |							"email": "${ANDRE.asString}"
         |						}]
         |					}
         |				}
         |			}
         |		}, "c1"]
         |	]
         |}""".stripMargin

    val greaterThanZero: Matcher[Integer] = Matchers.greaterThan(0)
    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
      .post
      .`then`
      .statusCode(SC_OK)
      .body("methodResponses[0][1].created.k1490.id", CharSequenceLength.hasLength(greaterThanZero))
  }

  @Test
  def emailSubmissionSetCreateShouldBeRejectedWhenMailContainsBothHoldForAndHoldUntil(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(BOB.asString)
      .setFrom(BOB.asString)
      .setTo(ANDRE.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		["EmailSubmission/set", {
         |			"accountId": "$ACCOUNT_ID",
         |			"create": {
         |				"k1490": {
         |					"emailId": "${messageId.serialize}",
         |					"envelope": {
         |						"mailFrom": {
         |							"email": "${BOB.asString}",
         |							"parameters": {
         |							  "holdUntil": "2023-04-14T10:30:00Z",
         |                "holdFor": "76000"
         |							}
         |						},
         |						"rcptTo": [{
         |							"email": "${ANDRE.asString}"
         |						}]
         |					}
         |				}
         |			}
         |		}, "c1"]
         |	]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
      .post
      .`then`
      .statusCode(SC_OK)
      .body("methodResponses[0][1].notCreated.k1490.type", Matchers.is("invalidArguments"))
      .body("methodResponses[0][1].notCreated.k1490.description", Matchers.is("Can't specify holdFor and holdUntil simultaneously"))
  }

  @Test
  def emailSubmissionSetCreateShouldBeRejectedEmailWhenHoldUntilIsNotADateTime(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(BOB.asString)
      .setFrom(BOB.asString)
      .setTo(ANDRE.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		["EmailSubmission/set", {
         |			"accountId": "$ACCOUNT_ID",
         |			"create": {
         |				"k1490": {
         |					"emailId": "${messageId.serialize}",
         |					"envelope": {
         |						"mailFrom": {
         |							"email": "${BOB.asString}",
         |							"parameters": {
         |							  "holdUntil": "not a date"
         |							}
         |						},
         |						"rcptTo": [{
         |							"email": "${ANDRE.asString}"
         |						}]
         |					}
         |				}
         |			}
         |		}, "c1"]
         |	]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
      .post
      .`then`
      .statusCode(SC_OK)
      .body("methodResponses[0][1].notCreated.k1490.type", Matchers.is("invalidArguments"))
      .body("methodResponses[0][1].notCreated.k1490.description", Matchers.is("Text 'not a date' could not be parsed at index 0"))
  }

  @Test
  def emailShouldBeRejectedWhenRcptToContainsDelayMailParameters(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(BOB.asString)
      .setFrom(BOB.asString)
      .setTo(ANDRE.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId
    val andreInboxPath = MailboxPath.inbox(ANDRE)
    val andreInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreInboxPath)

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		["EmailSubmission/set", {
         |			"accountId": "$ACCOUNT_ID",
         |			"create": {
         |				"k1490": {
         |					"emailId": "${messageId.serialize}",
         |					"envelope": {
         |						"mailFrom": {
         |							"email": "${BOB.asString}",
         |							"parameters": {
         |							  "holdFor": "1000"
         |							}
         |						},
         |						"rcptTo": [{
         |							  "email": "${ANDRE.asString}",
         |                "parameters": {
         |							    "holdFor": "1000"
         |							  }
         |              },
         |              {
         |                "email": "${ALICE.asString}"
         |						  }]
         |					}
         |				}
         |			}
         |		}, "c1"]
         |	]
         |}""".stripMargin
    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
      .post
      .`then`
      .statusCode(SC_OK)
      .body("methodResponses[0][1].notCreated.k1490.type", Matchers.is("invalidArguments"))
      .body("methodResponses[0][1].notCreated.k1490.description", Matchers.is("Some recipients have invalid delay parameters"))
  }

  @Test
  def responseShouldContainSendAtPropertyWhenUseHoldFor(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(BOB.asString)
      .setFrom(BOB.asString)
      .setTo(ANDRE.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		["EmailSubmission/set", {
         |			"accountId": "$ACCOUNT_ID",
         |			"create": {
         |				"k1490": {
         |					"emailId": "${messageId.serialize}",
         |					"envelope": {
         |						"mailFrom": {
         |							"email": "${BOB.asString}",
         |							"parameters": {
         |							  "holdFor": "1000"
         |							}
         |						},
         |						"rcptTo": [{
         |							"email": "${ANDRE.asString}"
         |						}]
         |					}
         |				}
         |			}
         |		}, "c1"]
         |	]
         |}""".stripMargin
    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
      .post
      .`then`
      .statusCode(SC_OK)
      .body("methodResponses[0][1].created.k1490.sendAt", Matchers.is("2023-04-14T10:16:40Z"))
  }

  @Test
  def responseShouldContainSendAtPropertyWhenUseHoldUntil(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(BOB.asString)
      .setFrom(BOB.asString)
      .setTo(ANDRE.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		["EmailSubmission/set", {
         |			"accountId": "$ACCOUNT_ID",
         |			"create": {
         |				"k1490": {
         |					"emailId": "${messageId.serialize}",
         |					"envelope": {
         |						"mailFrom": {
         |							"email": "${BOB.asString}",
         |							"parameters": {
         |							  "holdUntil": "2023-04-14T15:00:00Z"
         |							}
         |						},
         |						"rcptTo": [{
         |							"email": "${ANDRE.asString}"
         |						}]
         |					}
         |				}
         |			}
         |		}, "c1"]
         |	]
         |}""".stripMargin
    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
      .post
      .`then`
      .statusCode(SC_OK)
      .body("methodResponses[0][1].created.k1490.sendAt", Matchers.is("2023-04-14T15:00:00Z"))
  }

  @Test
  def emailShouldNotBeSentWhenParamNameIsInvalid(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(BOB.asString)
      .setFrom(BOB.asString)
      .setTo(ANDRE.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		["EmailSubmission/set", {
         |			"accountId": "$ACCOUNT_ID",
         |			"create": {
         |				"k1490": {
         |					"emailId": "${messageId.serialize}",
         |					"envelope": {
         |						"mailFrom": {
         |							"email": "${BOB.asString}",
         |							"parameters": {
         |							  "anotherParam": "whatever"
         |							}
         |						},
         |						"rcptTo": [{
         |							"email": "${ANDRE.asString}"
         |						}]
         |					}
         |				}
         |			}
         |		}, "c1"]
         |	]
         |}""".stripMargin
    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
      .post
      .`then`
      .statusCode(SC_OK)
      .body("methodResponses[0][1].notCreated.k1490.type", Matchers.is("invalidArguments"))
      .body("methodResponses[0][1].notCreated.k1490.description", Matchers.is("Unsupported parameterName"))
  }


  @Test
  def emailShouldBeRejectedWhenContainsInvalidParameterName(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(BOB.asString)
      .setFrom(BOB.asString)
      .setTo(ANDRE.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		["EmailSubmission/set", {
         |			"accountId": "$ACCOUNT_ID",
         |			"create": {
         |				"k1490": {
         |					"emailId": "${messageId.serialize}",
         |					"envelope": {
         |						"mailFrom": {
         |							"email": "${BOB.asString}",
         |							"parameters": {
         |							  "anotherParam": "whatever",
         |                 "holdFor": "86400"
         |							}
         |						},
         |						"rcptTo": [{
         |							"email": "${ANDRE.asString}"
         |						}]
         |					}
         |				}
         |			}
         |		}, "c1"]
         |	]
         |}""".stripMargin
    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
      .post
      .`then`
      .statusCode(SC_OK)
      .body("methodResponses[0][1].notCreated.k1490.type", Matchers.is("invalidArguments"))
      .body("methodResponses[0][1].notCreated.k1490.description", Matchers.is("Unsupported parameterName"))
  }

  @Test
  def emailShouldBeSentImmediatelyWhenHoldForIsNull(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(BOB.asString)
      .setFrom(BOB.asString)
      .setTo(ANDRE.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId
    val andreInboxPath = MailboxPath.inbox(ANDRE)
    val andreInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreInboxPath)

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		["EmailSubmission/set", {
         |			"accountId": "$ACCOUNT_ID",
         |			"create": {
         |				"k1490": {
         |					"emailId": "${messageId.serialize}",
         |					"envelope": {
         |						"mailFrom": {
         |							"email": "${BOB.asString}",
         |							"parameters": {
         |							  "holdFor": null
         |							}
         |						},
         |						"rcptTo": [{
         |							"email": "${ANDRE.asString}"
         |						}]
         |					}
         |				}
         |			}
         |		}, "c1"]
         |	]
         |}""".stripMargin
    `with`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
      .post

    // Ensure Andre receive the email immediately
    val requestAndre =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$ANDRE_ACCOUNT_ID",
         |      "filter": {"inMailbox": "${andreInboxId.serialize}"}
         |    },
         |    "c1"]]
         |}""".stripMargin
    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`(
        baseRequestSpecBuilder(server)
          .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
          .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
          .setBody(requestAndre)
          .build, new ResponseSpecBuilder().build)
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
  def emailShouldBeSentImmediatelyWhenHoldUntilIsNull(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(BOB.asString)
      .setFrom(BOB.asString)
      .setTo(ANDRE.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId
    val andreInboxPath = MailboxPath.inbox(ANDRE)
    val andreInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreInboxPath)

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		["EmailSubmission/set", {
         |			"accountId": "$ACCOUNT_ID",
         |			"create": {
         |				"k1490": {
         |					"emailId": "${messageId.serialize}",
         |					"envelope": {
         |						"mailFrom": {
         |							"email": "${BOB.asString}",
         |							"parameters": {
         |							  "holdUntil": null
         |							}
         |						},
         |						"rcptTo": [{
         |							"email": "${ANDRE.asString}"
         |						}]
         |					}
         |				}
         |			}
         |		}, "c1"]
         |	]
         |}""".stripMargin
    `with`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
      .post

    // Ensure Andre receive the email immediately
    val requestAndre =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "$ANDRE_ACCOUNT_ID",
         |      "filter": {"inMailbox": "${andreInboxId.serialize}"}
         |    },
         |    "c1"]]
         |}""".stripMargin
    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`(
        baseRequestSpecBuilder(server)
          .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
          .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
          .setBody(requestAndre)
          .build, new ResponseSpecBuilder().build)
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
}
