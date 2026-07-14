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

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.authentication.NoAuthScheme
import io.restassured.http.Header
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.core.AccountId
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, AUTHORIZATION_HEADER, BOB_PASSWORD, DOMAIN, USER, USER_TOKEN, baseRequestSpecBuilder, toBase64}
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.mailbox.DefaultMailboxes
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.{hasItems, hasSize}
import org.junit.jupiter.api.{BeforeEach, Tag, Test}

object ProvisioningContract {
  private val ARGUMENTS: String = "methodResponses[0][1]"

  case class TestContext(bobAccountId: String, bobBasicAuthHeader: Header)

  val currentContext: AtomicReference[TestContext] = new AtomicReference[TestContext]()
}

trait ProvisioningContract {
  import ProvisioningContract._

  private def getAllMailboxesRequest(accountId: String): String =
    s"""{
       |  "using": [
       |    "urn:ietf:params:jmap:core",
       |    "urn:ietf:params:jmap:mail"],
       |  "methodCalls": [[
       |      "Mailbox/get",
       |      {
       |        "accountId": "$accountId",
       |        "ids": null
       |      },
       |      "c1"]]
       |}""".stripMargin

  @BeforeEach
  def setup(server: GuiceJamesServer): Unit = {
    val bob = Username.fromLocalPartWithDomain(s"bob${UUID.randomUUID().toString.replace("-", "").take(8)}", DOMAIN)
    val bobAccountId = AccountId.from(bob).toOption.get.id.value
    val bobBasicAuthHeader = new Header(AUTHORIZATION_HEADER, s"Basic ${toBase64(s"${bob.asString}:$BOB_PASSWORD")}")
    currentContext.set(TestContext(bobAccountId, bobBasicAuthHeader))

    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(bob.asString, BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(new NoAuthScheme())
      .build
  }

  @Tag(CategoryTags.BASIC_FEATURE)
  @Test
  def provisionUserShouldAddMissingValidUser(server: GuiceJamesServer): Unit = {
    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .header(new Header(AUTHORIZATION_HEADER, s"Bearer $USER_TOKEN"))
      .body(getAllMailboxesRequest(currentContext.get().bobAccountId))
    .when
      .post

    assertThat(server.getProbe(classOf[DataProbeImpl]).listUsers())
      .contains(USER.asString())
  }

  @Tag(CategoryTags.BASIC_FEATURE)
  @Test
  def provisionMailboxesShouldCreateMissingMailboxes(): Unit = {
    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .header(currentContext.get().bobBasicAuthHeader)
      .body(getAllMailboxesRequest(currentContext.get().bobAccountId))
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body(s"$ARGUMENTS.list", hasSize(DefaultMailboxes.DEFAULT_MAILBOXES.size()))
      .body(s"$ARGUMENTS.list.name", hasItems(DefaultMailboxes.DEFAULT_MAILBOXES.toArray:_*))
  }
}
