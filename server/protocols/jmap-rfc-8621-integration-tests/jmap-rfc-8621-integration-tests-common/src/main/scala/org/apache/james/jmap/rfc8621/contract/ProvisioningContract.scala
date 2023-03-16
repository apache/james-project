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

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.authentication.NoAuthScheme
import io.restassured.http.Header
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.mailbox.DefaultMailboxes
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.{hasItems, hasSize}
import org.junit.jupiter.api.{BeforeEach, Tag, Test}

object ProvisioningContract {
  private val ARGUMENTS: String = "methodResponses[0][1]"
}

trait ProvisioningContract {
  import ProvisioningContract._

  @BeforeEach
  def setup(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)

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
      .body(GET_ALL_MAILBOXES_REQUEST)
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
      .header(BOB_BASIC_AUTH_HEADER)
      .body(GET_ALL_MAILBOXES_REQUEST)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body(s"$ARGUMENTS.list", hasSize(DefaultMailboxes.DEFAULT_MAILBOXES.size()))
      .body(s"$ARGUMENTS.list.name", hasItems(DefaultMailboxes.DEFAULT_MAILBOXES.toArray:_*))
  }
}
