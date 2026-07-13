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
import java.util.UUID
import java.util.concurrent.{TimeUnit, atomic}

import com.google.common.hash.Hashing
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.core.quota.{QuotaCountLimit, QuotaSizeLimit}
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.mail.{CountResourceType, OctetsResourceType, QuotaIdFactory, ResourceType}
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE_PASSWORD, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.model.MailboxACL.Right.Read
import org.apache.james.mailbox.model.{MailboxACL, MailboxPath}
import org.apache.james.modules.{ACLProbeImpl, MailboxProbeImpl, QuotaProbesImpl}
import org.apache.james.utils.DataProbeImpl
import org.awaitility.Awaitility
import org.junit.jupiter.api.{BeforeEach, Test}

object QuotaQueryMethodContract {
  case class TestContext(bobUsername: Username, bobAccountId: String, andreUsername: Username)

  val currentContext: atomic.AtomicReference[TestContext] = new atomic.AtomicReference[TestContext]()
}

trait QuotaQueryMethodContract {
  import QuotaQueryMethodContract.{TestContext, currentContext}

  def bobUsername: Username = currentContext.get().bobUsername
  def bobAccountId: String = currentContext.get().bobAccountId
  def andreUsername: Username = currentContext.get().andreUsername

  private def accountId(username: Username): String =
    Hashing.sha256().hashString(username.asString(), StandardCharsets.UTF_8).toString

  private def quotaId(server: GuiceJamesServer, username: Username, resourceType: ResourceType): String =
    QuotaIdFactory.from(server.getProbe(classOf[QuotaProbesImpl]).getQuotaRoot(MailboxPath.inbox(username)), resourceType).value

  private def queryState(ids: String*): String =
    Hashing.murmur3_32_fixed().hashUnencodedChars(ids.sorted.mkString(" ")).toString

  private lazy val awaitAtMostTenSeconds = Awaitility.`with`
    .await
    .pollInterval(Duration.ofMillis(100))
    .atMost(10, TimeUnit.SECONDS)

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    val uniqueSuffix = UUID.randomUUID().toString.replace("-", "").take(8)
    val bob = Username.fromLocalPartWithDomain(s"bob$uniqueSuffix", DOMAIN)
    val andre = Username.fromLocalPartWithDomain(s"andre$uniqueSuffix", DOMAIN)
    currentContext.set(TestContext(
      bobUsername = bob,
      bobAccountId = accountId(bob),
      andreUsername = andre))

    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(bob.asString, BOB_PASSWORD)
      .addUser(andre.asString, ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(bob, BOB_PASSWORD)))
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
           |      "accountId": "$bobAccountId",
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
         |                "accountId": "$bobAccountId",
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
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
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
           |      "accountId": "$bobAccountId",
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
         |                "accountId": "$bobAccountId",
         |                "queryState": "${queryState(quotaId(server, bobUsername, CountResourceType), quotaId(server, bobUsername, OctetsResourceType))}",
         |                "canCalculateChanges": false,
         |                "ids": [
         |                    "${quotaId(server, bobUsername, CountResourceType)}",
         |                    "${quotaId(server, bobUsername, OctetsResourceType)}"
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
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
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
           |      "accountId": "$bobAccountId",
           |      "filter" : {
           |        "resourceType": "count"
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
         |                "accountId": "$bobAccountId",
         |                "queryState": "${queryState(quotaId(server, bobUsername, CountResourceType))}",
         |                "canCalculateChanges": false,
         |                "ids": [
         |                    "${quotaId(server, bobUsername, CountResourceType)}"
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
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
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
           |      "accountId": "$bobAccountId",
           |      "filter" : {
           |        "resourceType": "invalid"
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
         |                "description": "'/filter/resourceType' property is not valid: Unexpected value invalid, only 'count' and 'octets' are managed"
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def filterDataTypeShouldWork(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
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
           |      "accountId": "$bobAccountId",
           |      "filter" : {
           |        "type": "Mail"
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
         |                "accountId": "$bobAccountId",
         |                "queryState": "${queryState(quotaId(server, bobUsername, CountResourceType), quotaId(server, bobUsername, OctetsResourceType))}",
         |                "canCalculateChanges": false,
         |                "ids": [
         |                    "${quotaId(server, bobUsername, CountResourceType)}",
         |                    "${quotaId(server, bobUsername, OctetsResourceType)}"
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
  def filterDataTypeShouldFailWhenInvalidDataType(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
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
           |      "accountId": "$bobAccountId",
           |      "filter" : {
           |        "type": "invalid"
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
         |                "description": "'/filter/type' property is not valid: Unexpected value invalid, only 'Mail' are managed"
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def filterScopeShouldWork(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
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
           |      "accountId": "$bobAccountId",
           |      "filter" : {
           |        "scope": "account"
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
         |                "accountId": "$bobAccountId",
         |                "queryState": "${queryState(quotaId(server, bobUsername, CountResourceType), quotaId(server, bobUsername, OctetsResourceType))}",
         |                "canCalculateChanges": false,
         |                "ids": [
         |                    "${quotaId(server, bobUsername, CountResourceType)}",
         |                    "${quotaId(server, bobUsername, OctetsResourceType)}"
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
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
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
           |      "accountId": "$bobAccountId",
           |      "filter" : {
           |        "scope": "invalidScope"
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
         |                "description": "'/filter/scope' property is not valid: Unexpected value invalidScope, only 'account' is managed"
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def filterQuotaNameShouldWork(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
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
           |      "accountId": "$bobAccountId",
           |      "filter" : {
           |        "name": "#private&${bobUsername.asString}@domain.tld:account:octets:Mail"
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
         |                "accountId": "$bobAccountId",
         |                "queryState": "${queryState(quotaId(server, bobUsername, OctetsResourceType))}",
         |                "canCalculateChanges": false,
         |                "ids": [
         |                    "${quotaId(server, bobUsername, OctetsResourceType)}"
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
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
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
           |      "accountId": "$bobAccountId",
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
         |                "accountId": "$bobAccountId",
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
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
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
           |      "accountId": "$bobAccountId",
           |      "filter" : {
           |        "name": "#private&${bobUsername.asString}@domain.tld:account:octets:Mail",
           |        "type": "Mail",
           |        "scope": "account",
           |        "resourceType": "octets"
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
         |                "accountId": "$bobAccountId",
         |                "queryState": "${queryState(quotaId(server, bobUsername, OctetsResourceType))}",
         |                "canCalculateChanges": false,
         |                "ids": [
         |                    "${quotaId(server, bobUsername, OctetsResourceType)}"
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
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
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
           |      "accountId": "$bobAccountId",
           |      "filter" : {
           |        "name": "#private&${bobUsername.asString}@domain.tld:account:octets:Mail",
           |        "resourceType": "count"
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
         |                "accountId": "$bobAccountId",
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
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
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
           |      "accountId": "$bobAccountId",
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
         |      "accountId": "$bobAccountId",
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
         |      "accountId": "$bobAccountId",
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
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
    quotaProbe.setMaxMessageCount(bobQuotaRoot, QuotaCountLimit.count(100L))

    val andreMailbox = MailboxPath.forUser(andreUsername, "mailbox")
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
           |      "accountId": "$bobAccountId",
           |      "filter" : {
           |        "name" : "#private&${andreUsername.asString}@domain.tld:account:count:Mail"
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
         |                "accountId": "$bobAccountId",
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
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
    quotaProbe.setMaxMessageCount(bobQuotaRoot, QuotaCountLimit.count(100L))

    val andreMailbox = MailboxPath.forUser(andreUsername, "mailbox")
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreMailbox)
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailbox, bobUsername.asString, new MailboxACL.Rfc4314Rights(Read))

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
           |      "accountId": "$bobAccountId",
           |      "filter" : {
           |        "name" : "#private&${andreUsername.asString}@domain.tld:account:count:Mail"
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
         |                "accountId": "$bobAccountId",
         |                "queryState": "${queryState(quotaId(server, andreUsername, CountResourceType))}",
         |                "canCalculateChanges": false,
         |                "ids": ["${quotaId(server, andreUsername, CountResourceType)}"],
         |                "position": 0,
         |                "limit": 256
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

}
