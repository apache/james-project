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

import java.nio.charset.StandardCharsets
import java.util.UUID

import com.google.common.hash.Hashing
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured
import io.restassured.RestAssured.`given`
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.JMAPTestingConstants.DOMAIN
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, BOB_PASSWORD, authScheme, baseRequestSpecBuilder}
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Test}

object CorsHeaderAPITestContext {
  case class TestContext(bobUsername: Username, bobAccountId: String)
  val currentContext: java.util.concurrent.atomic.AtomicReference[TestContext] = new java.util.concurrent.atomic.AtomicReference[TestContext]()
}

trait CorsHeaderAPITest {
  import CorsHeaderAPITestContext.currentContext

  def bobUsername: Username = currentContext.get().bobUsername
  def bobAccountId: String = currentContext.get().bobAccountId

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    val bob = Username.fromLocalPartWithDomain(s"bob${UUID.randomUUID().toString.replace("-", "").take(8)}", DOMAIN)
    currentContext.set(CorsHeaderAPITestContext.TestContext(
      bob, Hashing.sha256().hashString(bob.asString(), StandardCharsets.UTF_8).toString))
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN)
      .addUser(bobUsername.asString, BOB_PASSWORD)

    RestAssured.requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(bobUsername, BOB_PASSWORD)))
      .build
  }

  @Test
  def apiShouldPositionCorsHeaders(): Unit =
    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail"],
               |  "methodCalls": [[
               |    "Mailbox/get",
               |    {
               |      "accountId": "$bobAccountId"
               |    },
               |    "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .header("Access-Control-Allow-Origin", "*")
      .header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
      .header("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept")
      .header("Access-Control-Max-Age", "86400")
}
