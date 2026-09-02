/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.rfc8621.contract

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.JmapGuiceProbe
import org.apache.james.jmap.change.MailboxChangeListenerGroup
import org.apache.james.jmap.core.JmapRfc8621Configuration
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.pushsubscription.PushListenerGroup
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}

object WebPushDisabledContract {
  val configuration: JmapRfc8621Configuration = JmapRfc8621Configuration(
    urlPrefixString = "http://127.0.0.1",
    websocketPrefixString = "ws://127.0.0.1",
    webPushEnabled = false)
}

trait WebPushDisabledContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()
  }

  @Test
  def pushListenerShouldNotBeRegistered(server: GuiceJamesServer): Unit = {
    val groups = server.getProbe(classOf[JmapGuiceProbe]).listRegisteredGroups()

    assertThat(groups).doesNotContain(PushListenerGroup())
    // Ensures the above is not vacuously true: other JMAP listeners are registered on that very event bus
    assertThat(groups).contains(MailboxChangeListenerGroup())
  }

  @Test
  def pushSubscriptionGetShouldBeRejected(): Unit = {
    val response = `given`
      .body(
        """{
          |  "using": ["urn:ietf:params:jmap:core"],
          |  "methodCalls": [[
          |    "PushSubscription/get",
          |    {
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
         |    "error",
         |    {
         |      "type": "unknownMethod",
         |      "description": "PushSubscription is disabled on this server: set `webpush.enabled` in jmap.properties to enable it"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def pushSubscriptionSetShouldBeRejected(): Unit = {
    val response = `given`
      .body(
        """{
          |  "using": ["urn:ietf:params:jmap:core"],
          |  "methodCalls": [[
          |    "PushSubscription/set",
          |    {
          |      "create": {
          |        "4f29": {
          |          "deviceClientId": "a889-ffea-910",
          |          "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
          |          "types": ["Mailbox"]
          |        }
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
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "error",
         |    {
         |      "type": "unknownMethod",
         |      "description": "PushSubscription is disabled on this server: set `webpush.enabled` in jmap.properties to enable it"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }
}
