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
import java.util.concurrent.{TimeUnit, atomic}
import java.util.{Optional, UUID}

import com.google.common.hash.Hashing
import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import jakarta.inject.Inject
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.core.quota.{QuotaCountLimit, QuotaSizeLimit}
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.mail.{CountResourceType, OctetsResourceType, QuotaIdFactory}
import org.apache.james.jmap.method.QuotaGetMethod
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE_PASSWORD, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.QuotaGetMethodContract.TestContext
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.MailboxACL.Right.{Lookup, Read}
import org.apache.james.mailbox.model.{MailboxACL, MailboxPath, QuotaRoot}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.{ACLProbeImpl, MailboxProbeImpl, QuotaProbesImpl}
import org.apache.james.utils.{DataProbeImpl, GuiceProbe}
import org.awaitility.Awaitility
import org.junit.jupiter.api.{AfterEach, BeforeEach, Test}

object QuotaGetMethodContract {
  case class TestContext(bobUsername: Username, bobAccountId: String, andreUsername: Username)

  val currentContext: atomic.AtomicReference[TestContext] = new atomic.AtomicReference[TestContext]()
}

class QuotaGetMethodProbeModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[QuotaGetMethod]).in(Scopes.SINGLETON)
    Multibinder.newSetBinder(binder(), classOf[GuiceProbe])
      .addBinding()
      .to(classOf[QuotaGetMethodProbe])
  }
}

class QuotaGetMethodProbe @Inject()(quotaGetMethod: QuotaGetMethod) extends GuiceProbe {
  def forceDraftCompatibility(value: Boolean): Unit = {
    val draftCompatibilityField = classOf[QuotaGetMethod].getDeclaredField("JMAP_QUOTA_DRAFT_COMPATIBILITY")
    draftCompatibilityField.setAccessible(true)
    draftCompatibilityField.setBoolean(quotaGetMethod, value)

    val lazyValInitializedField = classOf[QuotaGetMethod].getDeclaredField("bitmap$0")
    lazyValInitializedField.setAccessible(true)
    lazyValInitializedField.setBoolean(quotaGetMethod, true)
  }
}

trait QuotaGetMethodContract {
  def bobUsername: Username = QuotaGetMethodContract.currentContext.get().bobUsername
  def bobAccountId: String = QuotaGetMethodContract.currentContext.get().bobAccountId
  def andreUsername: Username = QuotaGetMethodContract.currentContext.get().andreUsername

  private def quotaRoot(username: Username): QuotaRoot =
    QuotaRoot.quotaRoot(s"#private&${username.asString()}", Optional.of(DOMAIN))

  private def quotaName(quotaRoot: QuotaRoot, resourceType: String): String =
    s"${quotaRoot.asString()}:account:$resourceType:Mail"

  private def quotaState(entries: (String, Long, Long)*): String = {
    val namePart = entries.map(_._1).sorted.mkString("_")
    val sum = entries.map { case (_, used, hardLimit) => used + hardLimit }.sum
    UUID.nameUUIDFromBytes(s"$namePart:$sum".getBytes(StandardCharsets.UTF_8)).toString
  }

  def bobQuotaRoot: QuotaRoot = quotaRoot(bobUsername)
  def andreQuotaRoot: QuotaRoot = quotaRoot(andreUsername)

  def bobCountQuotaName: String = quotaName(bobQuotaRoot, CountResourceType.asString())
  def bobOctetsQuotaName: String = quotaName(bobQuotaRoot, OctetsResourceType.asString())
  def andreCountQuotaName: String = quotaName(andreQuotaRoot, CountResourceType.asString())

  def bobCountQuotaId: String = QuotaIdFactory.from(bobQuotaRoot, CountResourceType).value
  def bobOctetsQuotaId: String = QuotaIdFactory.from(bobQuotaRoot, OctetsResourceType).value
  def andreCountQuotaId: String = QuotaIdFactory.from(andreQuotaRoot, CountResourceType).value

  def emptyQuotaState: String = quotaState()
  def bobCount100State: String = quotaState((bobCountQuotaName, 0, 100))
  def bobCount100Octets99State: String = quotaState((bobCountQuotaName, 0, 100), (bobOctetsQuotaName, 0, 99))
  def bobCount100Octets900State: String = quotaState((bobCountQuotaName, 0, 100), (bobOctetsQuotaName, 0, 900))
  def bobCount100Octets900WithUsageState: String = quotaState((bobCountQuotaName, 1, 100), (bobOctetsQuotaName, 85, 900))
  def bobCount100Octets101WithUsageState: String = quotaState((bobCountQuotaName, 1, 100), (bobOctetsQuotaName, 85, 101))
  def bobCount100AndreCount88State: String = quotaState((bobCountQuotaName, 0, 100), (andreCountQuotaName, 0, 88))

  private lazy val awaitAtMostTenSeconds = Awaitility.`with`
    .await
    .pollInterval(Duration.ofMillis(100))
    .atMost(10, TimeUnit.SECONDS)

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    val uniqueSuffix = UUID.randomUUID().toString.replace("-", "").take(8)
    val bob = Username.fromLocalPartWithDomain(s"bob$uniqueSuffix", DOMAIN)
    val andre = Username.fromLocalPartWithDomain(s"andre$uniqueSuffix", DOMAIN)
    QuotaGetMethodContract.currentContext.set(TestContext(
      bobUsername = bob,
      bobAccountId = Hashing.sha256().hashString(bob.asString(), StandardCharsets.UTF_8).toString,
      andreUsername = andre))

    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(bob.asString, BOB_PASSWORD)
      .addUser(andre.asString(), ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(bob, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build

    server.getProbe(classOf[QuotaGetMethodProbe]).forceDraftCompatibility(false)
  }

  @AfterEach
  def tearDown(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    quotaProbe.removeGlobalMaxMessageCount()
    quotaProbe.removeGlobalMaxStorage()
    quotaProbe.removeDomainMaxMessage(DOMAIN)
    quotaProbe.removeDomainMaxStorage(DOMAIN)
    server.getProbe(classOf[QuotaGetMethodProbe]).forceDraftCompatibility(false)
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
           |      "accountId": "$bobAccountId",
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
         |      "accountId": "$bobAccountId",
         |      "state": "$emptyQuotaState",
         |      "list": [],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def quotaGetShouldReturnListWhenQuotasIsProvided(server: GuiceJamesServer): Unit = {
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
           |    "Quota/get",
           |    {
           |      "accountId": "$bobAccountId",
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
      .withOptions(IGNORING_ARRAY_ORDER)
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Quota/get",
         |            {
         |                "accountId": "$bobAccountId",
         |                "notFound": [],
         |                "state": "$bobCount100Octets99State",
         |                "list": [
         |                    {
         |                        "used": 0,
         |                        "name": "$bobCountQuotaName",
         |                        "id": "$bobCountQuotaId",
         |                        "types": [
         |                            "Mail"
         |                        ],
         |                        "hardLimit": 100,
         |                        "warnLimit": 90,
         |                        "resourceType": "count",
         |                        "scope": "account"
         |                    },
         |                    {
         |                        "used": 0,
         |                        "name": "$bobOctetsQuotaName",
         |                        "id": "$bobOctetsQuotaId",
         |                        "types": [
         |                            "Mail"
         |                        ],
         |                        "hardLimit": 99,
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
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
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
           |      "accountId": "$bobAccountId",
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
      .withOptions(IGNORING_ARRAY_ORDER)
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Quota/get",
         |            {
         |                "accountId": "$bobAccountId",
         |                "notFound": [],
         |                "state": "$bobCount100State",
         |                "list": [
         |                    {
         |                        "used": 0,
         |                        "name": "$bobCountQuotaName",
         |                        "id": "$bobCountQuotaId",
         |                        "types": [
         |                            "Mail"
         |                        ],
         |                        "hardLimit": 100,
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
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
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
           |      "accountId": "$bobAccountId",
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
         |                "accountId": "$bobAccountId",
         |                "notFound": [],
         |                "state": "$bobCount100State",
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
           |      "accountId": "$bobAccountId",
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
      .withOptions(IGNORING_ARRAY_ORDER)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "Quota/get",
           |            {
           |                "accountId": "$bobAccountId",
           |                "notFound": [],
           |                "state": "$bobCount100Octets99State",
           |                "list": [
           |                    {
           |                        "used": 0,
           |                        "name": "$bobCountQuotaName",
           |                        "id": "$bobCountQuotaId",
           |                        "types": [
           |                            "Mail"
           |                        ],
           |                        "hardLimit": 100,
           |                        "warnLimit": 90,
           |                        "resourceType": "count",
           |                        "scope": "account"
           |                    },
           |                    {
           |                        "used": 0,
           |                        "name": "$bobOctetsQuotaName",
           |                        "id": "$bobOctetsQuotaId",
           |                        "types": [
           |                            "Mail"
           |                        ],
           |                        "hardLimit": 99,
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
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
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
           |      "accountId": "$bobAccountId",
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
         |                "accountId": "$bobAccountId",
         |                "notFound": [ "notfound123" ],
         |                "state": "$bobCount100State",
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
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
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
           |      "accountId": "$bobAccountId",
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
         |                "accountId": "$bobAccountId",
         |                "notFound": [ "notfound123" ],
         |                "state": "$bobCount100Octets900State",
         |                "list": [
         |                    {
         |                        "used": 0,
         |                        "name": "$bobCountQuotaName",
         |                        "id": "$bobCountQuotaId",
         |                        "types": [
         |                            "Mail"
         |                        ],
         |                        "hardLimit": 100,
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
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
    quotaProbe.setMaxMessageCount(bobQuotaRoot, QuotaCountLimit.count(100L))
    quotaProbe.setMaxStorage(bobQuotaRoot, QuotaSizeLimit.size(900L))

    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(bobUsername))
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(bobUsername.asString(), MailboxPath.inbox(bobUsername), AppendCommand.from(Message.Builder
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
             |      "accountId": "$bobAccountId",
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
        .withOptions(IGNORING_ARRAY_ORDER)
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |        [
             |            "Quota/get",
             |            {
             |                "accountId": "$bobAccountId",
             |                "notFound": [ ],
             |                "state": "$bobCount100Octets900WithUsageState",
             |                "list": [
             |                    {
             |                        "used": 1,
             |                        "name": "$bobCountQuotaName",
             |                        "id": "$bobCountQuotaId",
             |                        "types": [
             |                            "Mail"
             |                        ],
             |                        "hardLimit": 100,
             |                        "warnLimit": 90,
             |                        "resourceType": "count",
             |                        "scope": "account"
             |                    },
             |                    {
             |                        "used": 85,
             |                        "name": "$bobOctetsQuotaName",
             |                        "id": "$bobOctetsQuotaId",
             |                        "types": [
             |                            "Mail"
             |                        ],
             |                        "hardLimit": 900,
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
    })
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
         |      "accountId": "$bobAccountId",
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
         |      "accountId": "$bobAccountId",
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
    val andreQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(andreUsername))
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
           |      "accountId": "$bobAccountId",
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
         |      "accountId": "$bobAccountId",
         |      "state": "$emptyQuotaState",
         |      "list": [],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def quotaGetShouldReturnNotFoundWhenDoesNotPermission(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val andreQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(andreUsername))
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
           |      "accountId": "$bobAccountId",
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
         |      "accountId": "$bobAccountId",
         |      "state": "$emptyQuotaState",
         |      "list": [],
         |      "notFound": [ "${quotaId}" ]
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def quotaGetShouldReturnIdWhenNoPropertiesRequested(server: GuiceJamesServer): Unit = {
    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val quotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
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
           |      "accountId": "$bobAccountId",
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
         |      "accountId": "$bobAccountId",
         |      "state": "$bobCount100State",
         |      "list": [
         |        {
         |          "id": "$bobCountQuotaId"
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
    val quotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
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
           |      "accountId": "$bobAccountId",
           |      "ids": null,
           |      "properties": ["name","used","hardLimit"]
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
         |      "accountId": "$bobAccountId",
         |      "state": "$bobCount100State",
         |      "list": [
         |        {
         |          "id": "$bobCountQuotaId",
         |          "used": 0,
         |          "name": "$bobCountQuotaName",
         |          "hardLimit": 100
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
    val quotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
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
           |      "accountId": "$bobAccountId",
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
    val quotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
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
           |      "accountId": "$bobAccountId",
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
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
    quotaProbe.setMaxMessageCount(bobQuotaRoot, QuotaCountLimit.count(100L))
    quotaProbe.setMaxStorage(bobQuotaRoot, QuotaSizeLimit.size(101L))

    quotaProbe.setGlobalMaxMessageCount(QuotaCountLimit.count(90L))
    quotaProbe.setGlobalMaxStorage(QuotaSizeLimit.size(99L))

    quotaProbe.setDomainMaxMessage(DOMAIN, QuotaCountLimit.count(80L))
    quotaProbe.setDomainMaxStorage(DOMAIN, QuotaSizeLimit.size(88L))

    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(bobUsername))
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(bobUsername.asString(), MailboxPath.inbox(bobUsername), AppendCommand.from(Message.Builder
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
             |      "accountId": "$bobAccountId",
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
        .withOptions(IGNORING_ARRAY_ORDER)
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |        [
             |            "Quota/get",
             |            {
             |                "accountId": "$bobAccountId",
             |                "notFound": [],
             |                "state": "$bobCount100Octets101WithUsageState",
             |                "list": [
             |                    {
             |                        "used": 1,
             |                        "name": "$bobCountQuotaName",
             |                        "id": "$bobCountQuotaId",
             |                        "types": [
             |                            "Mail"
             |                        ],
             |                        "hardLimit": 100,
             |                        "warnLimit": 90,
             |                        "resourceType": "count",
             |                        "scope": "account"
             |                    },
             |                    {
             |                        "used": 85,
             |                        "name": "$bobOctetsQuotaName",
             |                        "id": "$bobOctetsQuotaId",
             |                        "types": [
             |                            "Mail"
             |                        ],
             |                        "hardLimit": 101,
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
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
    quotaProbe.setMaxMessageCount(bobQuotaRoot, QuotaCountLimit.count(100L))

    // setup delegated Mailbox
    val andreMailbox = MailboxPath.forUser(andreUsername, "mailbox")
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreMailbox)
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailbox, bobUsername.asString, new MailboxACL.Rfc4314Rights(Read))

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
           |      "accountId": "$bobAccountId",
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
         |                "accountId": "$bobAccountId",
         |                "notFound": [],
         |                "state": "$bobCount100State",
         |                "list": [
         |                    {
         |                        "used": 0,
         |                        "name": "$bobCountQuotaName",
         |                        "id": "$bobCountQuotaId",
         |                        "types": [
         |                            "Mail"
         |                        ],
         |                        "hardLimit": 100,
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
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
    quotaProbe.setMaxMessageCount(bobQuotaRoot, QuotaCountLimit.count(100L))

    // setup delegated Mailbox
    val andreMailbox = MailboxPath.forUser(andreUsername, "mailbox")
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreMailbox)
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailbox, bobUsername.asString, new MailboxACL.Rfc4314Rights(Read))

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
           |      "accountId": "$bobAccountId",
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
      .withOptions(IGNORING_ARRAY_ORDER)
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Quota/get",
         |            {
         |                "accountId": "$bobAccountId",
         |                "notFound": [],
         |                "state": "$bobCount100AndreCount88State",
         |                "list": [
         |                    {
         |                        "used": 0,
         |                        "name": "$bobCountQuotaName",
         |                        "id": "$bobCountQuotaId",
         |                        "types": [
         |                            "Mail"
         |                        ],
         |                        "hardLimit": 100,
         |                        "warnLimit": 90,
         |                        "resourceType": "count",
         |                        "scope": "account"
         |                    },
         |                    {
         |                        "used": 0,
         |                        "name": "$andreCountQuotaName",
         |                        "warnLimit": 79,
         |                        "id": "$andreCountQuotaId",
         |                        "types": [
         |                            "Mail"
         |                        ],
         |                        "hardLimit": 88,
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
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
    quotaProbe.setMaxMessageCount(bobQuotaRoot, QuotaCountLimit.count(100L))

    // setup delegated Mailbox
    val andreMailbox = MailboxPath.forUser(andreUsername, "mailbox")
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreMailbox)
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailbox, bobUsername.asString, new MailboxACL.Rfc4314Rights(Lookup))

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
           |      "accountId": "$bobAccountId",
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
         |                "accountId": "$bobAccountId",
         |                "notFound": [],
         |                "state": "$bobCount100State",
         |                "list": [
         |                    {
         |                        "used": 0,
         |                        "name": "$bobCountQuotaName",
         |                        "id": "$bobCountQuotaId",
         |                        "types": [
         |                            "Mail"
         |                        ],
         |                        "hardLimit": 100,
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
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
    quotaProbe.setMaxMessageCount(bobQuotaRoot, QuotaCountLimit.count(100L))

    // setup delegated Mailbox
    val andreMailbox = MailboxPath.forUser(andreUsername, "mailbox")
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(andreMailbox)
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(andreMailbox, bobUsername.asString, new MailboxACL.Rfc4314Rights(Read))

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
           |      "accountId": "$bobAccountId",
           |      "ids": ["$andreCountQuotaId"]
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
      .withOptions(IGNORING_ARRAY_ORDER)
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Quota/get",
         |            {
         |                "accountId": "$bobAccountId",
         |                "notFound": [],
         |                "state": "$bobCount100AndreCount88State",
         |                "list": [
         |                    {
         |                        "used": 0,
         |                        "name": "$andreCountQuotaName",
         |                        "warnLimit": 79,
         |                        "id": "$andreCountQuotaId",
         |                        "types": [
         |                            "Mail"
         |                        ],
         |                        "hardLimit": 88,
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
  def shouldSupportQuotaGetDraftCompatibilityWhenEnabled(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[QuotaGetMethodProbe]).forceDraftCompatibility(true)

    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
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
           |      "accountId": "$bobAccountId",
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
      .withOptions(IGNORING_ARRAY_ORDER)
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Quota/get",
         |            {
         |                "accountId": "$bobAccountId",
         |                "notFound": [],
         |                "state": "$bobCount100State",
         |                "list": [
         |                    {
         |                        "used": 0,
         |                        "name": "$bobCountQuotaName",
         |                        "id": "$bobCountQuotaId",
         |                        "types": ["Mail"],
         |                        "dataTypes": ["Mail"],
         |                        "hardLimit": 100,
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
  def quotaGetDraftCompatibilityShouldStillSupportPropertiesFiltering(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[QuotaGetMethodProbe]).forceDraftCompatibility(true)

    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    val bobQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(bobUsername))
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
           |      "accountId": "$bobAccountId",
           |      "ids": null,
           |      "properties": ["limit", "dataTypes"]
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
      .withOptions(IGNORING_ARRAY_ORDER)
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Quota/get",
         |            {
         |                "accountId": "$bobAccountId",
         |                "notFound": [],
         |                "state": "$bobCount100State",
         |                "list": [
         |                    {
         |                        "id": "$bobCountQuotaId",
         |                        "dataTypes": ["Mail"],
         |                        "limit": 100
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
