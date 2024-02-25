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

import java.net.URI
import java.security.KeyPair
import java.security.interfaces.ECPublicKey
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.{Base64, UUID}

import com.google.common.collect.ImmutableSet
import com.google.crypto.tink.subtle.EllipticCurves.CurveType
import com.google.crypto.tink.subtle.{EllipticCurves, Random}
import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import jakarta.inject.Inject
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import net.javacrumbs.jsonunit.core.internal.Options
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.api.model.{DeviceClientId, PushSubscription, PushSubscriptionCreationRequest, PushSubscriptionId, PushSubscriptionServerURL, TypeName}
import org.apache.james.jmap.api.pushsubscription.PushSubscriptionRepository
import org.apache.james.jmap.change.{EmailDeliveryTypeName, EmailTypeName, MailboxTypeName}
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UTCDate
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.PushSubscriptionSetMethodContract.TIME_FORMATTER
import org.apache.james.utils.{DataProbeImpl, GuiceProbe}
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.{BeforeEach, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.JsonBody.json
import org.mockserver.model.Not.not
import org.mockserver.model.NottableString.string
import org.mockserver.model.{HttpRequest, HttpResponse}
import org.mockserver.verify.VerificationTimes
import reactor.core.scala.publisher.SMono

import scala.jdk.CollectionConverters._

object PushSubscriptionSetMethodContract {
  val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")
}
class PushSubscriptionProbe @Inject()(pushSubscriptionRepository: PushSubscriptionRepository) extends GuiceProbe {
  def createPushSubscription(username: Username, url: PushSubscriptionServerURL, deviceId: DeviceClientId, types: Seq[TypeName]): PushSubscription =
    SMono(pushSubscriptionRepository.save(username, PushSubscriptionCreationRequest(
      deviceClientId = deviceId,
      url = url,
      types = types)))
      .block()

  def retrievePushSubscription(username: Username, id: PushSubscriptionId): PushSubscription =
    SMono(pushSubscriptionRepository.get(username, ImmutableSet.of(id))).block()

  def validatePushSubscription(username: Username, id: PushSubscriptionId): Void =
    SMono(pushSubscriptionRepository.validateVerificationCode(username, id)).block()
}

class PushSubscriptionProbeModule extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[GuiceProbe])
      .addBinding()
      .to(classOf[PushSubscriptionProbe])
  }
}

trait PushSubscriptionSetMethodContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer, pushServer: ClientAndServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()

    pushServer
      .when(request
        .withPath("/subscribe")
        .withMethod("POST")
        .withHeader(string("Content-type"), string("application/json charset=utf-8")))
      .respond(response
        .withStatusCode(201))
  }

  private def getPushServerUrl(pushServer: ClientAndServer) : String =
    s"http://127.0.0.1:${pushServer.getLocalPort}/subscribe"

  @Test
  def setMethodShouldNotRequireAccountId(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": ["Mailbox"]
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
  }

  @Test
  def setMethodShouldFailWhenMissingCapability(): Unit = {
    val request: String =
      """{
        |    "using": [],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": ["Mailbox"]
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
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
           |                "type": "unknownMethod",
           |                "description": "Missing capability(ies): urn:ietf:params:jmap:core"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def updateShouldModifyTypes(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[PushSubscriptionProbe])
    val pushSubscription = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URI("https://example.com/push/?device=X8980fc&client=12c6d086").toURL),
        deviceId = DeviceClientId("12c6d086"),
        types = Seq(MailboxTypeName))

    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [
         |      [
         |        "PushSubscription/set",
         |        {
         |            "update": {
         |                "${pushSubscription.id.serialise}": {
         |                  "types": ["Mailbox", "Email"]
         |                }
         |              }
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "updated": {
           |                    "${pushSubscription.id.serialise}": {}
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)

    assertThat(probe.retrievePushSubscription(BOB, pushSubscription.id).types.asJava)
      .containsExactlyInAnyOrder(MailboxTypeName, EmailTypeName)
  }

  @Test
  def updateShouldRejectUnknownTypes(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[PushSubscriptionProbe])
    val pushSubscription = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URI("https://example.com/push/?device=X8980fc&client=12c6d086").toURL()),
        deviceId = DeviceClientId("12c6d086"),
        types = Seq(MailboxTypeName))

    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [
         |      [
         |        "PushSubscription/set",
         |        {
         |            "update": {
         |                "${pushSubscription.id.serialise}": {
         |                  "types": ["Mailbox", "Unknown"]
         |                }
         |              }
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "notUpdated":{
           |                    "${pushSubscription.id.serialise}":{
           |                        "type":"invalidArguments",
           |                        "description":"Unknown typeName Unknown",
           |                        "properties":["types"]
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def updateShouldRejectBadTypes(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[PushSubscriptionProbe])
    val pushSubscription = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URI("https://example.com/push/?device=X8980fc&client=12c6d086").toURL()),
        deviceId = DeviceClientId("12c6d086"),
        types = Seq(MailboxTypeName))

    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [
         |      [
         |        "PushSubscription/set",
         |        {
         |            "update": {
         |                "${pushSubscription.id.serialise}": {
         |                  "types": 36
         |                }
         |              }
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "notUpdated":{
           |                    "${pushSubscription.id.serialise}":{
           |                        "type":"invalidArguments",
           |                        "description":"Expecting an array of JSON strings as an argument",
           |                        "properties":["types"]
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def updateShouldRejectBadType(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[PushSubscriptionProbe])
    val pushSubscription = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URI("https://example.com/push/?device=X8980fc&client=12c6d086").toURL),
        deviceId = DeviceClientId("12c6d086"),
        types = Seq(MailboxTypeName))

    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [
         |      [
         |        "PushSubscription/set",
         |        {
         |            "update": {
         |                "${pushSubscription.id.serialise}": {
         |                  "types": ["Email", 36]
         |                }
         |              }
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "notUpdated":{
           |                    "${pushSubscription.id.serialise}":{
           |                        "type":"invalidArguments",
           |                        "description":"Expecting an array of JSON strings as an argument",
           |                        "properties":["types"]
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldNotCreatedWhenMissingTypesPropertyInCreationRequest(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086"
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "Missing '/types' property"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldNotCreatedWhenTypesPropertyIsEmpty(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": []
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "types must not be empty"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def getShouldReturnEmptyWhenNone(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [ [ "PushSubscription/get", { }, "c1" ] ]
        |}""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/get",
           |            {
           |                "list": []
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def getShouldReturnAllRecords(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[PushSubscriptionProbe])
    val pushSubscription1 = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URI("https://example.com/push/?device=X8980fc&client=12c6d086").toURL()),
        deviceId = DeviceClientId("12c6d086"),
        types = Seq(MailboxTypeName))
    val pushSubscription2 = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URI("https://example.com/push/?device=X8980fc&client=12c6d086").toURL()),
        deviceId = DeviceClientId("12c6d087"),
        types = Seq(EmailTypeName))

    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [ [ "PushSubscription/get", { }, "c1" ] ]
        |}""".stripMargin

    val response: String = `given`
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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "PushSubscription/get",
           |            {
           |                "list": [
           |                    {
           |                        "id": "${pushSubscription1.id.serialise}",
           |                        "deviceClientId": "12c6d086",
           |                        "expires": "${UTCDate(pushSubscription1.expires.value).asUTC.format(TIME_FORMATTER)}",
           |                        "types": ["Mailbox"]
           |                    },
           |                    {
           |                        "id": "${pushSubscription2.id.serialise}",
           |                        "deviceClientId": "12c6d087",
           |                        "expires": "${UTCDate(pushSubscription2.expires.value).asUTC.format(TIME_FORMATTER)}",
           |                        "types": ["Email"]
           |                    }
           |                ]
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def getShouldReturnValidatedVerificationCode(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[PushSubscriptionProbe])
    val pushSubscription1 = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URI("https://example.com/push/?device=X8980fc&client=12c6d086").toURL()),
        deviceId = DeviceClientId("12c6d086"),
        types = Seq(MailboxTypeName))
    probe.validatePushSubscription(BOB, pushSubscription1.id)

    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [ [ "PushSubscription/get", { }, "c1" ] ]
        |}""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/get",
           |            {
           |                "list": [
           |                    {
           |                        "id": "${pushSubscription1.id.serialise}",
           |                        "deviceClientId": "12c6d086",
           |                        "expires": "${UTCDate(pushSubscription1.expires.value).asUTC.format(TIME_FORMATTER)}",
           |                        "verificationCode": "${pushSubscription1.verificationCode.value}",
           |                        "types": ["Mailbox"]
           |                    }
           |                ]
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def getByIdShouldReturnRecords(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[PushSubscriptionProbe])
    val pushSubscription1 = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URI("https://example.com/push/?device=X8980fc&client=12c6d086").toURL),
        deviceId = DeviceClientId("12c6d086"),
        types = Seq(MailboxTypeName))
    val pushSubscription2 = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URI("https://example.com/push/?device=X8980fc&client=12c6d086").toURL),
        deviceId = DeviceClientId("12c6d087"),
        types = Seq(EmailTypeName))
    val pushSubscription3 = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URI("https://example.com/push/?device=X8980fc&client=12c6d086").toURL),
        deviceId = DeviceClientId("12c6d088"),
        types = Seq(EmailTypeName))

    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [ [ "PushSubscription/get", {
         |       "ids": ["${pushSubscription1.id.serialise}", "${pushSubscription2.id.serialise}"]
         |     }, "c1" ] ]
         |}""".stripMargin

    val response: String = `given`
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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "PushSubscription/get",
           |            {
           |                "list": [
           |                    {
           |                        "id": "${pushSubscription1.id.serialise}",
           |                        "deviceClientId": "12c6d086",
           |                        "expires": "${UTCDate(pushSubscription1.expires.value).asUTC.format(TIME_FORMATTER)}",
           |                        "types": ["Mailbox"]
           |                    },
           |                    {
           |                        "id": "${pushSubscription2.id.serialise}",
           |                        "deviceClientId": "12c6d087",
           |                        "expires": "${UTCDate(pushSubscription2.expires.value).asUTC.format(TIME_FORMATTER)}",
           |                        "types": ["Email"]
           |                    }
           |                ]
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def getByIdShouldReturnEmptyWhenEmpty(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[PushSubscriptionProbe])
    val pushSubscription1 = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URI("https://example.com/push/?device=X8980fc&client=12c6d086").toURL),
        deviceId = DeviceClientId("12c6d086"),
        types = Seq(MailboxTypeName))

    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [ [ "PushSubscription/get", {
         |       "ids": []
         |     }, "c1" ] ]
         |}""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/get",
           |            {
           |                "list": []
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def getByIdShouldReturnNotFound(): Unit = {
    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [ [ "PushSubscription/get", {
         |       "ids": ["notFound"]
         |     }, "c1" ] ]
         |}""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/get",
           |            {
           |                "notFound": ["notFound"],
           |                "list": []
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def getShouldFilterProperties(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[PushSubscriptionProbe])
    val pushSubscription1 = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URI("https://example.com/push/?device=X8980fc&client=12c6d086").toURL()),
        deviceId = DeviceClientId("12c6d086"),
        types = Seq(MailboxTypeName))
    val pushSubscription2 = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URI("https://example.com/push/?device=X8980fc&client=12c6d086").toURL()),
        deviceId = DeviceClientId("12c6d087"),
        types = Seq(EmailTypeName))

    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [ [ "PushSubscription/get", { "properties": ["types"] }, "c1" ] ]
        |}""".stripMargin

    val response: String = `given`
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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "PushSubscription/get",
           |            {
           |                "list": [
           |                    {
           |                        "id": "${pushSubscription1.id.serialise}",
           |                        "types": ["Mailbox"]
           |                    },
           |                    {
           |                        "id": "${pushSubscription2.id.serialise}",
           |                        "types": ["Email"]
           |                    }
           |                ]
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }


  @Test
  def setMethodShouldNotCreatedWhenInvalidURLProperty(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "invalid",
        |                  "types": ["Mailbox"]
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "'/url' property is not valid"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldNotCreatedWhenCreationRequestHasVerificationCodeProperty(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": ["Mailbox"],
        |                  "verificationCode": "abc"
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "Some server-set properties were specified",
           |                        "properties": [
           |                            "verificationCode"
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
  def setMethodShouldNotCreatedWhenCreationRequestHasIdProperty(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": ["Mailbox"],
        |                  "id": "abc"
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "Some server-set properties were specified",
           |                        "properties": [
           |                            "id"
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
  def setMethodShouldNotCreatedWhenInValidExpiresProperty(): Unit = {
    val invalidExpire: String = UTCDate(ZonedDateTime.now().minusDays(1)).asUTC.format(TIME_FORMATTER)
    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [
         |      [
         |        "PushSubscription/set",
         |        {
         |            "create": {
         |                "4f29": {
         |                  "deviceClientId": "a889-ffea-910",
         |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
         |                  "expires": "$invalidExpire",
         |                  "types": ["Mailbox"]
         |                }
         |              }
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "`$invalidExpire` expires must be greater than now",
           |                        "properties": ["expires"]
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldNotCreatedWhenInValidTypesProperty(): Unit = {
    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [
         |      [
         |        "PushSubscription/set",
         |        {
         |            "create": {
         |                "4f29": {
         |                  "deviceClientId": "a889-ffea-910",
         |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
         |                  "types": ["invalid"]
         |                }
         |              }
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "'/types(0)' property is not valid: Unknown typeName invalid"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldNotCreatedWhenDeviceClientIdExists(pushServer: ClientAndServer): Unit = {
    val request: String =
      s"""{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "${getPushServerUrl(pushServer)}",
        |                  "types": ["Mailbox"]
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "`a889-ffea-910` deviceClientId must be unique"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldAcceptValidExpiresProperty(pushServer: ClientAndServer): Unit = {
    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [
         |      [
         |        "PushSubscription/set",
         |        {
         |            "create": {
         |                "4f29": {
         |                  "deviceClientId": "a889-ffea-910",
         |                  "url": "${getPushServerUrl(pushServer)}",
         |                  "expires": "${UTCDate(ZonedDateTime.now().plusDays(1)).asUTC.format(TIME_FORMATTER)}",
         |                  "types": ["Mailbox"]
         |                }
         |              }
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "created": {
           |                    "4f29": {
           |                        "id": "$${json-unit.ignore}",
           |                        "expires": "$${json-unit.ignore}"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldCreatedWhenValidRequest(pushServer: ClientAndServer): Unit = {
    val request: String =
      s"""{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "${getPushServerUrl(pushServer)}",
        |                  "types": ["Mailbox"]
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "created": {
           |                    "4f29": {
           |                        "id": "$${json-unit.ignore}",
           |                        "expires": "$${json-unit.ignore}"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldCreatedSeveralValidCreationRequest(pushServer: ClientAndServer): Unit = {
    val request: String =
      s"""{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f28": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "${getPushServerUrl(pushServer)}",
        |                  "types": ["Mailbox"]
        |                },
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-912",
        |                  "url": "${getPushServerUrl(pushServer)}",
        |                  "types": ["Email"]
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "created": {
           |                    "4f28": {
           |                        "id": "$${json-unit.ignore}",
           |                        "expires": "$${json-unit.ignore}"
           |                    },
           |                    "4f29": {
           |                        "id": "$${json-unit.ignore}",
           |                        "expires": "$${json-unit.ignore}"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldSuccessWhenMixCase(pushServer: ClientAndServer): Unit = {
    val request: String =
      s"""{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f28": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "${getPushServerUrl(pushServer)}",
        |                  "types": ["Mailbox"]
        |                },
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-912",
        |                  "url": "${getPushServerUrl(pushServer)}",
        |                  "types": ["invalid"]
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "created": {
           |                    "4f28": {
           |                        "id": "$${json-unit.ignore}",
           |                        "expires": "$${json-unit.ignore}"
           |                    }
           |                },
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "'/types(0)' property is not valid: Unknown typeName invalid"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def updateShouldValidateVerificationCode(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[PushSubscriptionProbe])
    val pushSubscription = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URI("https://example.com/push/?device=X8980fc&client=12c6d086").toURL()),
        deviceId = DeviceClientId("12c6d086"),
        types = Seq(MailboxTypeName, EmailDeliveryTypeName, EmailTypeName))

    val request: String =
      s"""{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "update": {
        |                "${pushSubscription.id.serialise}": {
        |                  "verificationCode": "${pushSubscription.verificationCode.value}"
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "updated": {
           |                    "${pushSubscription.id.serialise}": {}
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)

    assertThat(probe.retrievePushSubscription(BOB, pushSubscription.id).validated).isTrue
  }

  @Test
  def setMethodShouldRejectInvalidKey(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": ["Mailbox"],
        |                  "keys": {
        |                    "p256dh": "QmFkIGtleQo",
        |                    "auth": "YXV0aCBzZWNyZXQK"
        |                  }
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "java.security.spec.InvalidKeySpecException: java.security.InvalidKeyException: Unable to decode key"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldAcceptValidKey(pushServer: ClientAndServer): Unit = {
    val request: String =
      s"""{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "${getPushServerUrl(pushServer)}",
        |                  "types": ["Mailbox"],
        |                  "keys": {
        |                    "p256dh": "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE5ozzvKUAB7GIfJ44eG-sxEcjT1O2jtk9QVD-MzFOH988CAPlSdkitm16NsMxUWksq6qGwu-r6zT7GCM9oGPXtQ==",
        |                    "auth": "Z7B0LmM6iTZD85EWtNRwIg=="
        |                  }
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "created": {
           |                    "4f29": {
           |                        "id": "$${json-unit.ignore}",
           |                        "expires": "$${json-unit.ignore}"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def updateMixed(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[PushSubscriptionProbe])
    val pushSubscription1 = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URI("https://example.com/push/?device=X8980fc&client=12c6d086").toURL()),
        deviceId = DeviceClientId("12c6d081"),
        types = Seq(MailboxTypeName, EmailDeliveryTypeName, EmailTypeName))
    val pushSubscription2 = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URI("https://example.com/push/?device=X8980fc&client=12c6d086").toURL()),
        deviceId = DeviceClientId("12c6d082"),
        types = Seq(MailboxTypeName, EmailDeliveryTypeName, EmailTypeName))
    val pushSubscription3 = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URI("https://example.com/push/?device=X8980fc&client=12c6d086").toURL()),
        deviceId = DeviceClientId("12c6d083"),
        types = Seq(MailboxTypeName, EmailDeliveryTypeName, EmailTypeName))
    val pushSubscription4 = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URI("https://example.com/push/?device=X8980fc&client=12c6d086").toURL()),
        deviceId = DeviceClientId("12c6d084"),
        types = Seq(MailboxTypeName, EmailDeliveryTypeName, EmailTypeName))

    val request: String =
      s"""{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "update": {
        |                "${pushSubscription1.id.serialise}": {
        |                  "verificationCode": "${pushSubscription1.verificationCode.value}"
        |                },
        |                "${pushSubscription2.id.serialise}": {
        |                  "verificationCode": "wrong"
        |                },
        |                "${pushSubscription3.id.serialise}": {
        |                  "verificationCode": "${pushSubscription3.verificationCode.value}"
        |                },
        |                "${pushSubscription4.id.serialise}": {
        |                  "verificationCode": "wrongAgain"
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "updated": {
           |                    "${pushSubscription1.id.serialise}": {},
           |                    "${pushSubscription3.id.serialise}": {}
           |                },
           |                "notUpdated": {
           |                    "${pushSubscription2.id.serialise}": {
           |                        "type": "invalidProperties",
           |                        "description": "Wrong verification code",
           |                        "properties": [
           |                            "verificationCode"
           |                        ]
           |                    },
           |                    "${pushSubscription4.id.serialise}": {
           |                        "type": "invalidProperties",
           |                        "description": "Wrong verification code",
           |                        "properties": [
           |                            "verificationCode"
           |                        ]
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(probe.retrievePushSubscription(BOB, pushSubscription1.id).validated).isTrue
      softly.assertThat(probe.retrievePushSubscription(BOB, pushSubscription2.id).validated).isFalse
      softly.assertThat(probe.retrievePushSubscription(BOB, pushSubscription3.id).validated).isTrue
      softly.assertThat(probe.retrievePushSubscription(BOB, pushSubscription4.id).validated).isFalse
    })
  }

  @Test
  def updateShouldNotValidateVerificationCodeWhenWrong(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[PushSubscriptionProbe])
    val pushSubscription = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URI("https://example.com/push/?device=X8980fc&client=12c6d086").toURL),
        deviceId = DeviceClientId("12c6d086"),
        types = Seq(MailboxTypeName, EmailDeliveryTypeName, EmailTypeName))

    val request: String =
      s"""{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "update": {
        |                "${pushSubscription.id.serialise}": {
        |                  "verificationCode": "wrong"
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "notUpdated": {
           |                    "${pushSubscription.id.serialise}": {
           |                        "type": "invalidProperties",
           |                        "description": "Wrong verification code",
           |                        "properties": [
           |                            "verificationCode"
           |                        ]
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)

    assertThat(probe.retrievePushSubscription(BOB, pushSubscription.id).validated).isFalse
  }

  @Test
  def updateValidExpiresShouldSucceed(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[PushSubscriptionProbe])
    val pushSubscription = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URI("https://example.com/push/?device=X8980fc&client=12c6d086").toURL()),
        deviceId = DeviceClientId("12c6d086"),
        types = Seq(MailboxTypeName, EmailDeliveryTypeName, EmailTypeName))

    val validExpiresString = UTCDate(ZonedDateTime.now().plusDays(1)).asUTC.format(TIME_FORMATTER)
    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [
         |      [
         |        "PushSubscription/set",
         |        {
         |            "update": {
         |                "${pushSubscription.id.serialise}": {
         |                 "expires": "$validExpiresString"
         |                }
         |              }
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "updated": {
           |                    "${pushSubscription.id.serialise}": {}
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)

    assertThat(probe.retrievePushSubscription(BOB, pushSubscription.id)
      .expires.value.format(TIME_FORMATTER))
      .isEqualTo(validExpiresString)
  }

  @Test
  def updateInvalidExpiresStringShouldFail(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[PushSubscriptionProbe])
    val pushSubscription = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URI("https://example.com/push/?device=X8980fc&client=12c6d086").toURL()),
        deviceId = DeviceClientId("12c6d086"),
        types = Seq(MailboxTypeName, EmailDeliveryTypeName, EmailTypeName))

    val invalidExpiresString = "whatever"
    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [
         |      [
         |        "PushSubscription/set",
         |        {
         |            "update": {
         |                "${pushSubscription.id.serialise}": {
         |                 "expires": "$invalidExpiresString"
         |                }
         |              }
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
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
           |	"sessionState": "${SESSION_STATE.value}",
           |	"methodResponses": [
           |		[
           |			"PushSubscription/set",
           |			{
           |				"notUpdated": {
           |					"${pushSubscription.id.serialise}": {
           |						"type": "invalidArguments",
           |						"description": "This string can not be parsed to UTCDate",
           |						"properties": ["expires"]
           |					}
           |				}
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
  }

  @Test
  def updateWithBiggerExpiresThanServerLimitShouldSetToServerLimitAndExplicitlyReturned(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[PushSubscriptionProbe])
    val pushSubscription = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URI("https://example.com/push/?device=X8980fc&client=12c6d086").toURL()),
        deviceId = DeviceClientId("12c6d086"),
        types = Seq(MailboxTypeName, EmailDeliveryTypeName, EmailTypeName))

    val biggerExpiresString = UTCDate(ZonedDateTime.now().plusDays(10)).asUTC.format(TIME_FORMATTER)
    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [
         |      [
         |        "PushSubscription/set",
         |        {
         |            "update": {
         |                "${pushSubscription.id.serialise}": {
         |                 "expires": "$biggerExpiresString"
         |                }
         |              }
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    val fixedExpires = probe.retrievePushSubscription(BOB, pushSubscription.id)
      .expires.value.format(TIME_FORMATTER)

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "PushSubscription/set",
           |            {
           |                "updated": {
           |                    "${pushSubscription.id.serialise}": {
           |                        "expires": "$fixedExpires"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def updateOutdatedExpiresShouldFail(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[PushSubscriptionProbe])
    val pushSubscription = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URI("https://example.com/push/?device=X8980fc&client=12c6d086").toURL()),
        deviceId = DeviceClientId("12c6d086"),
        types = Seq(MailboxTypeName, EmailDeliveryTypeName, EmailTypeName))

    val invalidExpiresString = UTCDate(ZonedDateTime.now().minusDays(1)).asUTC.format(TIME_FORMATTER)
    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [
         |      [
         |        "PushSubscription/set",
         |        {
         |            "update": {
         |                "${pushSubscription.id.serialise}": {
         |                 "expires": "$invalidExpiresString"
         |                }
         |              }
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
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
           |	"sessionState": "${SESSION_STATE.value}",
           |	"methodResponses": [
           |		[
           |			"PushSubscription/set",
           |			{
           |				"notUpdated": {
           |					"${pushSubscription.id.serialise}": {
           |						"type": "invalidArguments",
           |						"description": "`$invalidExpiresString` expires must be greater than now",
           |						"properties": ["expires"]
           |					}
           |				}
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenUnknownProperty(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[PushSubscriptionProbe])
    val pushSubscription = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URI("https://example.com/push/?device=X8980fc&client=12c6d086").toURL()),
        deviceId = DeviceClientId("12c6d086"),
        types = Seq(MailboxTypeName, EmailDeliveryTypeName, EmailTypeName))

    val request: String =
      s"""{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "update": {
        |                "${pushSubscription.id.serialise}": {
        |                  "unknown": "whatever"
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "notUpdated": {
           |                    "${pushSubscription.id.serialise}": {
           |                        "description":"unknown property do not exist thus cannot be updated",
           |                        "properties":["unknown"],
           |                        "type":"invalidArguments"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)

    assertThat(probe.retrievePushSubscription(BOB, pushSubscription.id).validated).isFalse
  }

  @Test
  def updateShouldFailWhenInvalidId(): Unit = {
    val request: String =
      s"""{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "update": {
        |                "bad": {
        |                  "verificationCode": "anyValue"
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "notUpdated": {
           |                    "bad": {
           |                        "type": "invalidArguments",
           |                        "description": "Invalid UUID string: bad"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenNotFound(): Unit = {
    val id = UUID.randomUUID().toString
    val request: String =
      s"""{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "update": {
        |                "$id": {
        |                  "verificationCode": "anyValue"
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "notUpdated": {
           |                    "$id": {
           |                        "type": "notFound",
           |                        "description": null
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodCreateShouldCallVerificationToPushServer(pushServer: ClientAndServer): Unit = {
    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [
         |      [
         |        "PushSubscription/set",
         |        {
         |            "create": {
         |                "4f29": {
         |                  "deviceClientId": "a889-ffea-910",
         |                  "url": "${getPushServerUrl(pushServer)}",
         |                  "types": ["Mailbox"]
         |                }
         |              }
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

    val pushSubscriptionId: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .extract()
      .jsonPath()
      .get("methodResponses[0][1].created.4f29.id")

    pushServer.verify(HttpRequest.request()
      .withPath("/subscribe")
      .withBody(json(s"""{
                        |    "@type": "PushVerification",
                        |    "pushSubscriptionId": "$pushSubscriptionId",
                        |    "verificationCode": "$${json-unit.any-string}"
                        |}""".stripMargin)),
      VerificationTimes.atLeast(1))
  }

  @Test
  def setMethodCreateShouldNotCreatedWhenCallVerificationToPushServerHasError(pushServer: ClientAndServer): Unit = {
    pushServer
      .when(HttpRequest.request
        .withPath("/invalid")
        .withMethod("POST"))
      .respond(HttpResponse.response
        .withStatusCode(500))

    val pushServerUrl = s"http://127.0.0.1:${pushServer.getLocalPort}/invalid"
    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [
         |      [
         |        "PushSubscription/set",
         |        {
         |            "create": {
         |                "4f29": {
         |                  "deviceClientId": "a889-ffea-910",
         |                  "url": "$pushServerUrl",
         |                  "types": ["Mailbox"]
         |                }
         |              }
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "serverFail",
           |                        "description": "Error when call to Push Server. "
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodCreateShouldNotSaveSubscriptionEntryWhenCallVerificationToPushServerHasError(pushServer: ClientAndServer): Unit = {
    pushServer
      .when(HttpRequest.request
        .withPath("/invalid")
        .withMethod("POST"))
      .respond(HttpResponse.response
        .withStatusCode(500))

    val deviceId: String = "a889-ffea-910"
    val invalidPushServerUrl: String = s"http://127.0.0.1:${pushServer.getLocalPort}/invalid"

    `given`
      .body(
        s"""{
           |    "using": ["urn:ietf:params:jmap:core"],
           |    "methodCalls": [
           |      [
           |        "PushSubscription/set",
           |        {
           |            "create": {
           |                "4f29": {
           |                  "deviceClientId": "$deviceId",
           |                  "url": "$invalidPushServerUrl",
           |                  "types": ["Mailbox"]
           |                }
           |              }
           |        },
           |        "c1"
           |      ]
           |    ]
           |  }""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)

    val response: String = `given`
      .body(
        s"""{
           |    "using": ["urn:ietf:params:jmap:core"],
           |    "methodCalls": [
           |      [
           |        "PushSubscription/set",
           |        {
           |            "create": {
           |                "4f29": {
           |                  "deviceClientId": "$deviceId",
           |                  "url": "${getPushServerUrl(pushServer)}",
           |                  "types": ["Mailbox"]
           |                }
           |              }
           |        },
           |        "c1"
           |      ]
           |    ]
           |  }""".stripMargin)
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
           |            "PushSubscription/set",
           |            {
           |                "created": {
           |                    "4f29": {
           |                        "id": "$${json-unit.ignore}",
           |                        "expires": "$${json-unit.ignore}"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldAcceptValidKeys(pushServer: ClientAndServer) : Unit = {
    val uaKeyPair: KeyPair = EllipticCurves.generateKeyPair(CurveType.NIST_P256)
    val uaPublicKey: ECPublicKey = uaKeyPair.getPublic.asInstanceOf[ECPublicKey]
    val authSecret: Array[Byte] = Random.randBytes(16)

    val p256dh: String = Base64.getUrlEncoder.encodeToString(uaPublicKey.getEncoded)
    val auth: String = Base64.getUrlEncoder.encodeToString(authSecret)

    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [
         |      [
         |        "PushSubscription/set",
         |        {
         |            "create": {
         |                "4f29": {
         |                  "deviceClientId": "a889-ffea-910",
         |                  "url": "${getPushServerUrl(pushServer)}",
         |                  "types": ["Mailbox"],
         |                  "keys": {
         |                    "p256dh": "$p256dh",
         |                    "auth": "$auth"
         |                  }
         |                }
         |              }
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "created": {
           |                    "4f29": {
           |                        "id": "$${json-unit.ignore}",
           |                        "expires": "$${json-unit.ignore}"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def bodyRequestToPushServerShouldBeEncryptedWhenClientAssignEncryptionKeys(pushServer: ClientAndServer) : Unit = {
    val uaKeyPair: KeyPair = EllipticCurves.generateKeyPair(CurveType.NIST_P256)
    val uaPublicKey: ECPublicKey = uaKeyPair.getPublic.asInstanceOf[ECPublicKey]
    val authSecret: Array[Byte] = "secret123secret1".getBytes

    val p256dh: String = Base64.getUrlEncoder.encodeToString(uaPublicKey.getEncoded)
    val auth: String = Base64.getUrlEncoder.encodeToString(authSecret)

    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [
         |      [
         |        "PushSubscription/set",
         |        {
         |            "create": {
         |                "4f29": {
         |                  "deviceClientId": "a889-ffea-910",
         |                  "url": "${getPushServerUrl(pushServer)}",
         |                  "types": ["Mailbox"],
         |                  "keys": {
         |                    "p256dh": "$p256dh",
         |                    "auth": "$auth"
         |                  }
         |                }
         |              }
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

    `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)

    pushServer.verify(HttpRequest.request()
      .withPath("/subscribe")
      .withBody(not(json(
        s"""{
           |    "@type": "PushVerification",
           |    "pushSubscriptionId": "$${json-unit.any-string}",
           |    "verificationCode": "$${json-unit.any-string}"
           |}""".stripMargin))),
      VerificationTimes.atLeast(1))
  }

  @Test
  def destroyShouldSucceed(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[PushSubscriptionProbe])
    val pushSubscription = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URI("https://example.com/push/?device=X8980fc&client=12c6d086").toURL),
        deviceId = DeviceClientId("12c6d086"),
        types = Seq(MailboxTypeName, EmailDeliveryTypeName, EmailTypeName))

    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [
         |      [
         |        "PushSubscription/set",
         |        {
         |            "destroy": ["${pushSubscription.id.value.toString}"]
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "destroyed": ["${pushSubscription.id.value.toString}"]
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)

    assertThat(probe.retrievePushSubscription(BOB, pushSubscription.id)).isNull()
  }

  @Test
  def destroyShouldFailWhenInvalidId(): Unit = {
    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [
         |      [
         |        "PushSubscription/set",
         |        {
         |            "destroy": ["invalid"]
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "notDestroyed": {
           |                    "invalid": {
           |                        "type": "invalidArguments",
           |                        "description": "invalid is not a PushSubscriptionId: Invalid UUID string: invalid"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def destroyShouldNotFailWhenUnknownId(): Unit = {
    val id = UUID.randomUUID().toString

    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [
         |      [
         |        "PushSubscription/set",
         |        {
         |            "destroy": ["$id"]
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
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
           |            "PushSubscription/set",
           |            {
           |                "destroyed":["$id"]
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def destroyShouldHandleMixedCases(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[PushSubscriptionProbe])
    val pushSubscription1 = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URI("https://example.com/push/?device=X8980fc&client=12c6d086").toURL),
        deviceId = DeviceClientId("12c6d086"),
        types = Seq(MailboxTypeName, EmailDeliveryTypeName, EmailTypeName))
    val pushSubscription2 = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URI("https://example.com/push/?device=X8980fc&client=12c6d087").toURL),
        deviceId = DeviceClientId("12c6d087"),
        types = Seq(MailboxTypeName, EmailDeliveryTypeName, EmailTypeName))

    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [
         |      [
         |        "PushSubscription/set",
         |        {
         |            "destroy": ["${pushSubscription1.id.value.toString}", "${pushSubscription2.id.value.toString}", "invalid"]
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
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
      .when(net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "PushSubscription/set",
           |            {
           |                "destroyed": [
           |                    "${pushSubscription1.id.value.toString}",
           |                    "${pushSubscription2.id.value.toString}"
           |                ],
           |                "notDestroyed": {
           |                    "invalid": {
           |                        "type": "invalidArguments",
           |                        "description": "invalid is not a PushSubscriptionId: Invalid UUID string: invalid"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }
}
