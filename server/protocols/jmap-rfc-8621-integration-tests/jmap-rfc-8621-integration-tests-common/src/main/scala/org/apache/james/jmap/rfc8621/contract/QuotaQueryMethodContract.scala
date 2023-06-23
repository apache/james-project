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

import java.time.Duration
import java.util.concurrent.TimeUnit

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.quota.{QuotaCountLimit, QuotaSizeLimit}
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.model.MailboxACL.Right.Read
import org.apache.james.mailbox.model.{MailboxACL, MailboxPath}
import org.apache.james.modules.{ACLProbeImpl, MailboxProbeImpl, QuotaProbesImpl}
import org.apache.james.utils.DataProbeImpl
import org.awaitility.Awaitility
import org.junit.jupiter.api.{BeforeEach, Test}

trait QuotaQueryMethodContract {

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
      .addUser(ANDRE.asString, ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build
  }

  @Test
  def queryShouldSucceedByDefault(): Unit = {
    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota"],
           |  "methodCalls": [[
           |    "Quota/query",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "filter" : {}
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
         |            "Quota/query",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "queryState": "00000000",
         |                "canCalculateChanges": false,
         |                "ids": [
         |
         |                ],
         |                "position": 0,
         |                "limit": 256
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def queryShouldReturnAllWhenFilterIsEmpty(server: GuiceJamesServer): Unit = {
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
           |    "Quota/query",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "filter" : {}
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
         |            "Quota/query",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "queryState": "a06886ff",
         |                "canCalculateChanges": false,
         |                "ids": [
         |                    "08417be420b6dd6fa77d48fb2438e0d19108cd29424844bb109b52d356fab528",
         |                    "eab6ce8ac5d9730a959e614854410cf39df98ff3760a623b8e540f36f5184947"
         |                ],
         |                "position": 0,
         |                "limit": 256
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def filterResourceTypesShouldWork(server: GuiceJamesServer): Unit = {
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
           |    "Quota/query",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "filter" : {
           |        "resourceTypes": ["count"]
           |      }
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
         |            "Quota/query",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "queryState": "d8d3e770",
         |                "canCalculateChanges": false,
         |                "ids": [
         |                    "08417be420b6dd6fa77d48fb2438e0d19108cd29424844bb109b52d356fab528"
         |                ],
         |                "position": 0,
         |                "limit": 256
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def filterResourceTypesShoulFailWhenInvalidResourceTypes(server: GuiceJamesServer): Unit = {
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
           |    "Quota/query",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "filter" : {
           |        "resourceTypes": ["invalid"]
           |      }
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
         |                "description": "'/filter/resourceTypes(0)' property is not valid: Unexpected value invalid, only 'count' and 'octets' are managed"
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def filterDataTypesShouldWork(server: GuiceJamesServer): Unit = {
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
           |    "Quota/query",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "filter" : {
           |        "dataTypes": ["Mail"]
           |      }
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
         |            "Quota/query",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "queryState": "a06886ff",
         |                "canCalculateChanges": false,
         |                "ids": [
         |                    "08417be420b6dd6fa77d48fb2438e0d19108cd29424844bb109b52d356fab528",
         |                    "eab6ce8ac5d9730a959e614854410cf39df98ff3760a623b8e540f36f5184947"
         |                ],
         |                "position": 0,
         |                "limit": 256
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def filterDataTypesShouldFailWhenInvalidDataTypes(server: GuiceJamesServer): Unit = {
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
           |    "Quota/query",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "filter" : {
           |        "dataTypes": ["invalid"]
           |      }
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
         |                "description": "'/filter/dataTypes(0)' property is not valid: Unexpected value invalid, only 'Mail' are managed"
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def filterScopeShouldWork(server: GuiceJamesServer): Unit = {
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
           |    "Quota/query",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "filter" : {
           |        "scope": ["account"]
           |      }
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
         |            "Quota/query",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "queryState": "a06886ff",
         |                "canCalculateChanges": false,
         |                "ids": [
         |                    "08417be420b6dd6fa77d48fb2438e0d19108cd29424844bb109b52d356fab528",
         |                    "eab6ce8ac5d9730a959e614854410cf39df98ff3760a623b8e540f36f5184947"
         |                ],
         |                "position": 0,
         |                "limit": 256
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def filterScopeShouldFailWhenInvalidScope(server: GuiceJamesServer): Unit = {
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
           |    "Quota/query",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "filter" : {
           |        "scope": ["invalidScope"]
           |      }
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
         |                "description": "'/filter/scope(0)' property is not valid: Unexpected value invalidScope, only 'account' is managed"
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def filterQuotaNameShouldWork(server: GuiceJamesServer): Unit = {
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
           |    "Quota/query",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "filter" : {
           |        "name": "#private&bob@domain.tld@domain.tld:account:octets:Mail"
           |      }
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
         |            "Quota/query",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "queryState": "6cacd8a2",
         |                "canCalculateChanges": false,
         |                "ids": [
         |                    "eab6ce8ac5d9730a959e614854410cf39df98ff3760a623b8e540f36f5184947"
         |                ],
         |                "position": 0,
         |                "limit": 256
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def filterQuotaNameShouldReturnEmptyResultWhenNameIsNotFound(server: GuiceJamesServer): Unit = {
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
           |    "Quota/query",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "filter" : {
           |        "name": "notFound"
           |      }
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
         |            "Quota/query",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "queryState": "00000000",
         |                "canCalculateChanges": false,
         |                "ids": [],
         |                "position": 0,
         |                "limit": 256
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def filterMultiPropertyShouldWork(server: GuiceJamesServer): Unit = {
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
           |    "Quota/query",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "filter" : {
           |        "name": "#private&bob@domain.tld@domain.tld:account:octets:Mail",
           |        "dataTypes": ["Mail"],
           |        "scope": ["account"],
           |        "resourceTypes": ["octets"]
           |      }
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
         |            "Quota/query",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "queryState": "6cacd8a2",
         |                "canCalculateChanges": false,
         |                "ids": [
         |                    "eab6ce8ac5d9730a959e614854410cf39df98ff3760a623b8e540f36f5184947"
         |                ],
         |                "position": 0,
         |                "limit": 256
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def filterShouldBeANDLogic(server: GuiceJamesServer): Unit = {
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
           |    "Quota/query",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "filter" : {
           |        "name": "#private&bob@domain.tld@domain.tld:account:octets:Mail",
           |        "resourceTypes": ["count"]
           |      }
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
         |            "Quota/query",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "queryState": "00000000",
         |                "canCalculateChanges": false,
         |                "ids": [],
         |                "position": 0,
         |                "limit": 256
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def filterShouldFailWhenInvalidFilter(server: GuiceJamesServer): Unit = {
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
           |    "Quota/query",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "filter" : {
           |        "filterName1": "filterValue2"
           |      }
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
         |                "description": "'/filter' property is not valid: These '[filterName1]' was unsupported filter options"
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }


  @Test
  def quotaQueryShouldFailWhenWrongAccountId(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:quota"],
         |  "methodCalls": [[
         |    "Quota/query",
         |    {
         |      "accountId": "unknownAccountId",
         |      "filter": {}
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
  def quotaQueryShouldFailWhenOmittingOneCapability(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core"],
         |  "methodCalls": [[
         |    "Quota/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {}
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
  def quotaQueryShouldFailWhenOmittingAllCapability(): Unit = {
    val request =
      s"""{
         |  "using": [],
         |  "methodCalls": [[
         |    "Quota/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {}
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
  def quotaQueryShouldReturnEmptyIdsWhenDoesNotPermission(server: GuiceJamesServer): Unit ={
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(BOB))
    quotaProbe.setMaxMessageCount(bobQuotaRoot, QuotaCountLimit.count(100L))

    val andreMailbox = MailboxPath.forUser(ANDRE, "mailbox")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreMailbox)
    quotaProbe.setMaxMessageCount(quotaProbe.getQuotaRoot(andreMailbox), QuotaCountLimit.count(88L))

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota",
           |    "urn:apache:james:params:jmap:mail:shares" ],
           |  "methodCalls": [[
           |    "Quota/query",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "filter" : {
           |        "name" : "#private&andre@domain.tld@domain.tld:account:count:Mail"
           |      }
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
         |            "Quota/query",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "queryState": "00000000",
         |                "canCalculateChanges": false,
         |                "ids": [],
         |                "position": 0,
         |                "limit": 256
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def quotaQueryShouldReturnIdsWhenHasPermission(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(BOB))
    quotaProbe.setMaxMessageCount(bobQuotaRoot, QuotaCountLimit.count(100L))

    val andreMailbox = MailboxPath.forUser(ANDRE, "mailbox")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreMailbox)
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailbox, BOB.asString, new MailboxACL.Rfc4314Rights(Read))

    quotaProbe.setMaxMessageCount(quotaProbe.getQuotaRoot(andreMailbox), QuotaCountLimit.count(88L))

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:quota",
           |    "urn:apache:james:params:jmap:mail:shares"],
           |  "methodCalls": [[
           |    "Quota/query",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "filter" : {
           |        "name" : "#private&andre@domain.tld@domain.tld:account:count:Mail"
           |      }
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
         |            "Quota/query",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "queryState": "980c0716",
         |                "canCalculateChanges": false,
         |                "ids": ["04cbe4578878e02a74e47ae6be66c88cc8aafd3a5fc698457d712ee5f9a5b4ca"],
         |                "position": 0,
         |                "limit": 256
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

}
