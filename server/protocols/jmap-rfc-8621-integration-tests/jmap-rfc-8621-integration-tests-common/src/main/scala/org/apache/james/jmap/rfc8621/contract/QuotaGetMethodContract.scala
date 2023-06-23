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
import java.time.Duration
import java.util.concurrent.TimeUnit

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import net.javacrumbs.jsonunit.core.internal.Options
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.quota.{QuotaCountLimit, QuotaSizeLimit}
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.mail.{CountResourceType, QuotaIdFactory}
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.MailboxACL.Right.{Lookup, Read}
import org.apache.james.mailbox.model.{MailboxACL, MailboxPath}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.{ACLProbeImpl, MailboxProbeImpl, QuotaProbesImpl}
import org.apache.james.utils.DataProbeImpl
import org.awaitility.Awaitility
import org.junit.jupiter.api.{BeforeEach, Test}

trait QuotaGetMethodContract {

  private lazy val awaitAtMostTenSeconds = Awaitility.`with`
    .await
    .pollInterval(Duration.ofMillis(100))
    .atMost(10, TimeUnit.SECONDS)

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build
  }

  @Test
  def listShouldEmptyWhenAccountDoesNotHaveQuotas(): Unit = {
    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota"],
           |  "methodCalls": [[
           |    "Quota/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "ids": null
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
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Quota/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "state": "1a9d5db2-2c73-3993-bf0b-42f64b396873",
         |      "list": [],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def quotaGetShouldReturnListWhenQuotasIsProvided(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(BOB))
    quotaProbe.setMaxMessageCount(bobQuotaRoot, QuotaCountLimit.count(100L))
    quotaProbe.setMaxStorage(bobQuotaRoot, QuotaSizeLimit.size(99L))

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota"],
           |  "methodCalls": [[
           |    "Quota/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "ids": null
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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Quota/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "notFound": [],
         |                "state": "6d7199ed-f1ce-31f3-8f02-c2e824004e55",
         |                "list": [
         |                    {
         |                        "used": 0,
         |                        "name": "#private&bob@domain.tld@domain.tld:account:count:Mail",
         |                        "id": "08417be420b6dd6fa77d48fb2438e0d19108cd29424844bb109b52d356fab528",
         |                        "dataTypes": [
         |                            "Mail"
         |                        ],
         |                        "limit": 100,
         |                        "warnLimit": 90,
         |                        "resourceType": "count",
         |                        "scope": "account"
         |                    },
         |                    {
         |                        "used": 0,
         |                        "name": "#private&bob@domain.tld@domain.tld:account:octets:Mail",
         |                        "id": "eab6ce8ac5d9730a959e614854410cf39df98ff3760a623b8e540f36f5184947",
         |                        "dataTypes": [
         |                            "Mail"
         |                        ],
         |                        "limit": 99,
         |                        "warnLimit": 89,
         |                        "resourceType": "octets",
         |                        "scope": "account"
         |                    }
         |                ]
         |            },
         |            "c1"
         |        ]
         |    ]
         |}
         |""".stripMargin)
  }

  @Test
  def quotaGetShouldFilterOutUnlimitedQuota(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(BOB))
    quotaProbe.setMaxMessageCount(bobQuotaRoot, QuotaCountLimit.count(100L))
    quotaProbe.setMaxStorage(bobQuotaRoot, QuotaSizeLimit.unlimited())

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota"],
           |  "methodCalls": [[
           |    "Quota/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "ids": null
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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Quota/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "notFound": [],
         |                "state": "84c40a2e-76a1-3f84-a1e8-862104c7a697",
         |                "list": [
         |                    {
         |                        "used": 0,
         |                        "name": "#private&bob@domain.tld@domain.tld:account:count:Mail",
         |                        "id": "08417be420b6dd6fa77d48fb2438e0d19108cd29424844bb109b52d356fab528",
         |                        "dataTypes": [
         |                            "Mail"
         |                        ],
         |                        "limit": 100,
         |                        "warnLimit": 90,
         |                        "resourceType": "count",
         |                        "scope": "account"
         |                    }
         |                ]
         |            },
         |            "c1"
         |        ]
         |    ]
         |}
         |""".stripMargin)
  }

  @Test
  def quotaGetShouldReturnEmptyListWhenIdsAreEmpty(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(BOB))
    quotaProbe.setMaxMessageCount(bobQuotaRoot, QuotaCountLimit.count(100L))

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota"],
           |  "methodCalls": [[
           |    "Quota/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "ids": []
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Quota/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "notFound": [],
         |                "state": "84c40a2e-76a1-3f84-a1e8-862104c7a697",
         |                "list": []
         |            },
         |            "c1"
         |        ]
         |    ]
         |}
         |""".stripMargin)
  }


  @Test
  def quotaGetShouldReturnListWhenGlobalQuota(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    quotaProbe.setGlobalMaxMessageCount(QuotaCountLimit.count(100L))
    quotaProbe.setGlobalMaxStorage(QuotaSizeLimit.size(99L))

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota"],
           |  "methodCalls": [[
           |    "Quota/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "ids": null
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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "Quota/get",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "notFound": [],
           |                "state": "6d7199ed-f1ce-31f3-8f02-c2e824004e55",
           |                "list": [
           |                    {
           |                        "used": 0,
           |                        "name": "#private&bob@domain.tld@domain.tld:account:count:Mail",
           |                        "id": "08417be420b6dd6fa77d48fb2438e0d19108cd29424844bb109b52d356fab528",
           |                        "dataTypes": [
           |                            "Mail"
           |                        ],
           |                        "limit": 100,
           |                        "warnLimit": 90,
           |                        "resourceType": "count",
           |                        "scope": "account"
           |                    },
           |                    {
           |                        "used": 0,
           |                        "name": "#private&bob@domain.tld@domain.tld:account:octets:Mail",
           |                        "id": "eab6ce8ac5d9730a959e614854410cf39df98ff3760a623b8e540f36f5184947",
           |                        "dataTypes": [
           |                            "Mail"
           |                        ],
           |                        "limit": 99,
           |                        "warnLimit": 89,
           |                        "resourceType": "octets",
           |                        "scope": "account"
           |                    }
           |                ]
           |            },
           |            "c1"
           |        ]
           |    ]
           |}
           |""".stripMargin)
  }



  @Test
  def quotaGetShouldReturnNotFoundWhenIdDoesNotExist(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(BOB))
    quotaProbe.setMaxMessageCount(bobQuotaRoot, QuotaCountLimit.count(100L))

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota"],
           |  "methodCalls": [[
           |    "Quota/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "ids": ["notfound123"]
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Quota/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "notFound": [ "notfound123" ],
         |                "state": "84c40a2e-76a1-3f84-a1e8-862104c7a697",
         |                "list": []
         |            },
         |            "c1"
         |        ]
         |    ]
         |}
         |""".stripMargin)
  }

  @Test
  def quotaGetShouldReturnNotFoundAndListWhenMixCases(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(BOB))
    quotaProbe.setMaxMessageCount(bobQuotaRoot, QuotaCountLimit.count(100L))
    quotaProbe.setMaxStorage(bobQuotaRoot, QuotaSizeLimit.size(900))

    val quotaId = QuotaIdFactory.from(bobQuotaRoot, CountResourceType)

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota"],
           |  "methodCalls": [[
           |    "Quota/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "ids": ["notfound123", "${quotaId.value}"]
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Quota/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "notFound": [ "notfound123" ],
         |                "state": "461cef39-0c47-352b-a9e9-052093c20d5d",
         |                "list": [
         |                    {
         |                        "used": 0,
         |                        "name": "#private&bob@domain.tld@domain.tld:account:count:Mail",
         |                        "id": "08417be420b6dd6fa77d48fb2438e0d19108cd29424844bb109b52d356fab528",
         |                        "dataTypes": [
         |                            "Mail"
         |                        ],
         |                        "limit": 100,
         |                        "warnLimit": 90,
         |                        "resourceType": "count",
         |                        "scope": "account"
         |                    }
         |                ]
         |            },
         |            "c1"
         |        ]
         |    ]
         |}
         |""".stripMargin)
  }

  @Test
  def quotaGetShouldReturnRightUsageQuota(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(BOB))
    quotaProbe.setMaxMessageCount(bobQuotaRoot, QuotaCountLimit.count(100L))
    quotaProbe.setMaxStorage(bobQuotaRoot, QuotaSizeLimit.size(900L))

    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), MailboxPath.inbox(BOB), AppendCommand.from(Message.Builder
        .of
        .setSubject("test")
        .setBody("testmail", StandardCharsets.UTF_8)
        .build))
      .getMessageId.serialize()

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota"],
           |  "methodCalls": [[
           |    "Quota/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "ids": null
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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Quota/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "notFound": [ ],
         |                "state": "3c51d50a-d766-38b7-9fa4-c9ff12de87a4",
         |                "list": [
         |                    {
         |                        "used": 1,
         |                        "name": "#private&bob@domain.tld@domain.tld:account:count:Mail",
         |                        "id": "08417be420b6dd6fa77d48fb2438e0d19108cd29424844bb109b52d356fab528",
         |                        "dataTypes": [
         |                            "Mail"
         |                        ],
         |                        "limit": 100,
         |                        "warnLimit": 90,
         |                        "resourceType": "count",
         |                        "scope": "account"
         |                    },
         |                    {
         |                        "used": 85,
         |                        "name": "#private&bob@domain.tld@domain.tld:account:octets:Mail",
         |                        "id": "eab6ce8ac5d9730a959e614854410cf39df98ff3760a623b8e540f36f5184947",
         |                        "dataTypes": [
         |                            "Mail"
         |                        ],
         |                        "limit": 900,
         |                        "warnLimit": 810,
         |                        "resourceType": "octets",
         |                        "scope": "account"
         |                    }
         |                ]
         |            },
         |            "c1"
         |        ]
         |    ]
         |}
         |""".stripMargin)
  }


  @Test
  def quotaGetShouldFailWhenWrongAccountId(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:quota"],
         |  "methodCalls": [[
         |    "Quota/get",
         |    {
         |      "accountId": "unknownAccountId",
         |      "ids": null
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
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
  def quotaGetShouldFailWhenOmittingOneCapability(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core"],
         |  "methodCalls": [[
         |    "Quota/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": null
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
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
         |  "methodResponses": [[
         |    "error",
         |    {
         |      "type": "unknownMethod",
         |      "description":"Missing capability(ies): urn:ietf:params:jmap:quota"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def quotaGetShouldFailWhenOmittingAllCapability(): Unit = {
    val request =
      s"""{
         |  "using": [],
         |  "methodCalls": [[
         |    "Quota/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": null
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
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
         |  "methodResponses": [[
         |    "error",
         |    {
         |      "type": "unknownMethod",
         |      "description":"Missing capability(ies): urn:ietf:params:jmap:quota, urn:ietf:params:jmap:core"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def quotaGetShouldNotReturnQuotaDataOfOtherAccount(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val andreQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(ANDRE))
    quotaProbe.setMaxMessageCount(andreQuotaRoot, QuotaCountLimit.count(100L))

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota"],
           |  "methodCalls": [[
           |    "Quota/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "ids": null
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
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Quota/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "state": "1a9d5db2-2c73-3993-bf0b-42f64b396873",
         |      "list": [],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def quotaGetShouldReturnNotFoundWhenDoesNotPermission(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val andreQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(ANDRE))
    quotaProbe.setMaxMessageCount(andreQuotaRoot, QuotaCountLimit.count(100L))

    val quotaId = QuotaIdFactory.from(andreQuotaRoot, CountResourceType)

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota"],
           |  "methodCalls": [[
           |    "Quota/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "ids": ["${quotaId.value}"]
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
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Quota/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "state": "1a9d5db2-2c73-3993-bf0b-42f64b396873",
         |      "list": [],
         |      "notFound": [ "${quotaId}" ]
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def quotaGetShouldReturnIdWhenNoPropertiesRequested(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val quotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(BOB))
    quotaProbe.setMaxMessageCount(quotaRoot, QuotaCountLimit.count(100L))

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota"],
           |  "methodCalls": [[
           |    "Quota/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "ids": null,
           |      "properties": []
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
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Quota/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "state": "84c40a2e-76a1-3f84-a1e8-862104c7a697",
         |      "list": [
         |        {
         |          "id": "08417be420b6dd6fa77d48fb2438e0d19108cd29424844bb109b52d356fab528"
         |        }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def quotaGetShouldReturnOnlyRequestedProperties(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val quotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(BOB))
    quotaProbe.setMaxMessageCount(quotaRoot, QuotaCountLimit.count(100L))

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota"],
           |  "methodCalls": [[
           |    "Quota/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "ids": null,
           |      "properties": ["name","used","limit"]
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
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Quota/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "state": "84c40a2e-76a1-3f84-a1e8-862104c7a697",
         |      "list": [
         |        {
         |          "id": "08417be420b6dd6fa77d48fb2438e0d19108cd29424844bb109b52d356fab528",
         |          "used": 0,
         |          "name": "#private&bob@domain.tld@domain.tld:account:count:Mail",
         |          "limit": 100
         |        }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def quotaGetShouldFailWhenInvalidProperties(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val quotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(BOB))
    quotaProbe.setMaxMessageCount(quotaRoot, QuotaCountLimit.count(100L))

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota"],
           |  "methodCalls": [[
           |    "Quota/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "ids": null,
           |      "properties": ["invalid"]
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "error",
         |            {
         |                "type": "invalidArguments",
         |                "description": "The following properties [invalid] do not exist."
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def quotaGetShouldFailWhenInvalidIds(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val quotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(BOB))
    quotaProbe.setMaxMessageCount(quotaRoot, QuotaCountLimit.count(100L))

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota"],
           |  "methodCalls": [[
           |    "Quota/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "ids": ["#==id"]
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "error",
         |            {
         |                "type": "invalidArguments",
         |                "description": "$${json-unit.any-string}"
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def quotaGetShouldReturnOnlyUserQuota(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(BOB))
    quotaProbe.setMaxMessageCount(bobQuotaRoot, QuotaCountLimit.count(100L))
    quotaProbe.setMaxStorage(bobQuotaRoot, QuotaSizeLimit.size(101L))

    quotaProbe.setGlobalMaxMessageCount(QuotaCountLimit.count(90L))
    quotaProbe.setGlobalMaxStorage(QuotaSizeLimit.size(99L))

    quotaProbe.setDomainMaxMessage(DOMAIN, QuotaCountLimit.count(80L))
    quotaProbe.setDomainMaxStorage(DOMAIN, QuotaSizeLimit.size(88L))

    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), MailboxPath.inbox(BOB), AppendCommand.from(Message.Builder
        .of
        .setSubject("test")
        .setBody("testmail", StandardCharsets.UTF_8)
        .build))
      .getMessageId.serialize()

    awaitAtMostTenSeconds.untilAsserted(() => {
      val response = `given`
        .body(
          s"""{
             |  "using": [
             |    "urn:ietf:params:jmap:core",
             |    "urn:ietf:params:jmap:quota"],
             |  "methodCalls": [[
             |    "Quota/get",
             |    {
             |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |      "ids": null
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
        .withOptions(new Options(IGNORING_ARRAY_ORDER))
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |        [
             |            "Quota/get",
             |            {
             |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |                "notFound": [],
             |                "state": "7d53031b-2819-3584-9e9d-e10ac1067906",
             |                "list": [
             |                    {
             |                        "used": 1,
             |                        "name": "#private&bob@domain.tld@domain.tld:account:count:Mail",
             |                        "id": "08417be420b6dd6fa77d48fb2438e0d19108cd29424844bb109b52d356fab528",
             |                        "dataTypes": [
             |                            "Mail"
             |                        ],
             |                        "limit": 100,
             |                        "warnLimit": 90,
             |                        "resourceType": "count",
             |                        "scope": "account"
             |                    },
             |                    {
             |                        "used": 85,
             |                        "name": "#private&bob@domain.tld@domain.tld:account:octets:Mail",
             |                        "id": "eab6ce8ac5d9730a959e614854410cf39df98ff3760a623b8e540f36f5184947",
             |                        "dataTypes": [
             |                            "Mail"
             |                        ],
             |                        "limit": 101,
             |                        "warnLimit": 90,
             |                        "resourceType": "octets",
             |                        "scope": "account"
             |                    }
             |                ]
             |            },
             |            "c1"
             |        ]
             |    ]
             |}
             |""".stripMargin)
    })
  }

  @Test
  def quotaGetShouldNotReturnQuotaRootOfDelegatedMailboxWhenNotExtension(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(BOB))
    quotaProbe.setMaxMessageCount(bobQuotaRoot, QuotaCountLimit.count(100L))

    // setup delegated Mailbox
    val andreMailbox = MailboxPath.forUser(ANDRE, "mailbox")
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreMailbox)
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailbox, BOB.asString, new MailboxACL.Rfc4314Rights(Read))

    quotaProbe.setMaxMessageCount(quotaProbe.getQuotaRoot(andreMailbox), QuotaCountLimit.count(88L))


    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota"],
           |  "methodCalls": [[
           |    "Quota/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "ids": null
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Quota/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "notFound": [],
         |                "state": "84c40a2e-76a1-3f84-a1e8-862104c7a697",
         |                "list": [
         |                    {
         |                        "used": 0,
         |                        "name": "#private&bob@domain.tld@domain.tld:account:count:Mail",
         |                        "id": "08417be420b6dd6fa77d48fb2438e0d19108cd29424844bb109b52d356fab528",
         |                        "dataTypes": [
         |                            "Mail"
         |                        ],
         |                        "limit": 100,
         |                        "warnLimit": 90,
         |                        "resourceType": "count",
         |                        "scope": "account"
         |                    }
         |                ]
         |            },
         |            "c1"
         |        ]
         |    ]
         |}
         |""".stripMargin)
  }

  @Test
  def quotaGetShouldReturnQuotaRootOfDelegatedMailboxWhenExtension(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(BOB))
    quotaProbe.setMaxMessageCount(bobQuotaRoot, QuotaCountLimit.count(100L))

    // setup delegated Mailbox
    val andreMailbox = MailboxPath.forUser(ANDRE, "mailbox")
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreMailbox)
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailbox, BOB.asString, new MailboxACL.Rfc4314Rights(Read))

    quotaProbe.setMaxMessageCount(quotaProbe.getQuotaRoot(andreMailbox), QuotaCountLimit.count(88L))

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota",
           |    "urn:apache:james:params:jmap:mail:shares" ],
           |  "methodCalls": [[
           |    "Quota/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "ids": null
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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Quota/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "notFound": [],
         |                "state": "5dc809dd-d059-3fab-bc7e-c0f1fcacf2f2",
         |                "list": [
         |                    {
         |                        "used": 0,
         |                        "name": "#private&bob@domain.tld@domain.tld:account:count:Mail",
         |                        "id": "08417be420b6dd6fa77d48fb2438e0d19108cd29424844bb109b52d356fab528",
         |                        "dataTypes": [
         |                            "Mail"
         |                        ],
         |                        "limit": 100,
         |                        "warnLimit": 90,
         |                        "resourceType": "count",
         |                        "scope": "account"
         |                    },
         |                    {
         |                        "used": 0,
         |                        "name": "#private&andre@domain.tld@domain.tld:account:count:Mail",
         |                        "warnLimit": 79,
         |                        "id": "04cbe4578878e02a74e47ae6be66c88cc8aafd3a5fc698457d712ee5f9a5b4ca",
         |                        "dataTypes": [
         |                            "Mail"
         |                        ],
         |                        "limit": 88,
         |                        "resourceType": "count",
         |                        "scope": "account"
         |                    }
         |                ]
         |            },
         |            "c1"
         |        ]
         |    ]
         |}
         |""".stripMargin)
  }

  @Test
  def quotaGetShouldReturnQuotaRootOfDelegatedMailboxWhenNotHasReadRight(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(BOB))
    quotaProbe.setMaxMessageCount(bobQuotaRoot, QuotaCountLimit.count(100L))

    // setup delegated Mailbox
    val andreMailbox = MailboxPath.forUser(ANDRE, "mailbox")
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreMailbox)
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailbox, BOB.asString, new MailboxACL.Rfc4314Rights(Lookup))

    quotaProbe.setMaxMessageCount(quotaProbe.getQuotaRoot(andreMailbox), QuotaCountLimit.count(88L))

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota",
           |    "urn:apache:james:params:jmap:mail:shares" ],
           |  "methodCalls": [[
           |    "Quota/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "ids": null
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Quota/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "notFound": [],
         |                "state": "84c40a2e-76a1-3f84-a1e8-862104c7a697",
         |                "list": [
         |                    {
         |                        "used": 0,
         |                        "name": "#private&bob@domain.tld@domain.tld:account:count:Mail",
         |                        "id": "08417be420b6dd6fa77d48fb2438e0d19108cd29424844bb109b52d356fab528",
         |                        "dataTypes": [
         |                            "Mail"
         |                        ],
         |                        "limit": 100,
         |                        "warnLimit": 90,
         |                        "resourceType": "count",
         |                        "scope": "account"
         |                    }
         |                ]
         |            },
         |            "c1"
         |        ]
         |    ]
         |}
         |""".stripMargin)
  }

  @Test
  def quotaGetShouldReturnQuotaRootOfDelegatedMailboxWhenProvideCorrectId(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(BOB))
    quotaProbe.setMaxMessageCount(bobQuotaRoot, QuotaCountLimit.count(100L))

    // setup delegated Mailbox
    val andreMailbox = MailboxPath.forUser(ANDRE, "mailbox")
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreMailbox)
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailbox, BOB.asString, new MailboxACL.Rfc4314Rights(Read))

    quotaProbe.setMaxMessageCount(quotaProbe.getQuotaRoot(andreMailbox), QuotaCountLimit.count(88L))

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota",
           |    "urn:apache:james:params:jmap:mail:shares" ],
           |  "methodCalls": [[
           |    "Quota/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "ids": ["04cbe4578878e02a74e47ae6be66c88cc8aafd3a5fc698457d712ee5f9a5b4ca"]
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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Quota/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "notFound": [],
         |                "state": "5dc809dd-d059-3fab-bc7e-c0f1fcacf2f2",
         |                "list": [
         |                    {
         |                        "used": 0,
         |                        "name": "#private&andre@domain.tld@domain.tld:account:count:Mail",
         |                        "warnLimit": 79,
         |                        "id": "04cbe4578878e02a74e47ae6be66c88cc8aafd3a5fc698457d712ee5f9a5b4ca",
         |                        "dataTypes": [
         |                            "Mail"
         |                        ],
         |                        "limit": 88,
         |                        "resourceType": "count",
         |                        "scope": "account"
         |                    }
         |                ]
         |            },
         |            "c1"
         |        ]
         |    ]
         |}
         |""".stripMargin)
  }

}
