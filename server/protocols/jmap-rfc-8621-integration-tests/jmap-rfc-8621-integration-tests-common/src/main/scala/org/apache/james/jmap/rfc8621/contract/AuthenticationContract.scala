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

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured._
import io.restassured.authentication.NoAuthScheme
import io.restassured.http.{Header, Headers}
import org.apache.http.HttpStatus.{SC_OK, SC_UNAUTHORIZED}
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.rfc8621.contract.AuthenticationContract._
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Tag, Test}

object AuthenticationContract {
  private val AUTHORIZATION_HEADER: String = "Authorization"
}

trait AuthenticationContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)
      .addDomain(_2_DOT_DOMAIN.asString())
      .addUser(ALICE.asString(), ALICE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(new NoAuthScheme())
      .build
  }

  @Test
  def postShouldRespondUnauthorizedWhenNoAuthorizationHeader(): Unit = {
    `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(ECHO_REQUEST_OBJECT)
    .when()
      .post()
    .then
      .statusCode(SC_UNAUTHORIZED)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def postShouldRespond200WhenHasCredentials(): Unit = {
    val authHeader: Header = new Header(AUTHORIZATION_HEADER, s"Basic ${toBase64(s"${BOB.asString}:$BOB_PASSWORD")}")
    `given`()
      .headers(getHeadersWith(authHeader))
      .body(ECHO_REQUEST_OBJECT)
    .when()
      .post()
    .then
      .statusCode(SC_OK)
  }

  @Test
  def postShouldRespond401WhenCredentialsWithInvalidUser(): Unit = {
    val authHeader: Header = new Header(AUTHORIZATION_HEADER, s"Basic ${toBase64(s"${BOB.getLocalPart}@@$DOMAIN:$BOB_PASSWORD")}")
    `given`()
      .headers(getHeadersWith(authHeader))
      .body(ECHO_REQUEST_OBJECT)
    .when()
      .post()
    .then
      .statusCode(SC_UNAUTHORIZED)
  }

  @Test
  def postShouldRespondOKWhenCredentialsWith2DotDomain(): Unit = {
    val authHeader: Header = new Header(AUTHORIZATION_HEADER, s"Basic ${toBase64(s"${ALICE.asString}:$ALICE_PASSWORD")}")
    `given`()
      .headers(getHeadersWith(authHeader))
      .body(ECHO_REQUEST_OBJECT)
    .when()
      .post()
    .then
      .statusCode(SC_OK)
  }

  @Test
  def postShouldRespond401WhenCredentialsWithSpaceDomain(): Unit = {
    val authHeader: Header = new Header(AUTHORIZATION_HEADER, s"Basic ${toBase64(s"${BOB.getLocalPart}@$DOMAIN_WITH_SPACE:$BOB_PASSWORD")}")
    `given`()
      .headers(getHeadersWith(authHeader))
      .body(ECHO_REQUEST_OBJECT)
    .when()
      .post()
    .then
      .statusCode(SC_UNAUTHORIZED)
  }

  @Test
  def postShouldRespond401WhenUserNotFound(): Unit = {
    val authHeader: Header = new Header(AUTHORIZATION_HEADER, s"Basic ${toBase64(s"usernotfound@$DOMAIN:$BOB_PASSWORD")}")
    `given`()
      .headers(getHeadersWith(authHeader))
      .body(ECHO_REQUEST_OBJECT)
    .when()
      .post()
    .then
      .statusCode(SC_UNAUTHORIZED)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def postShouldRespond401WhenWrongPassword(): Unit = {
    val authHeader: Header = new Header(AUTHORIZATION_HEADER, s"Basic ${toBase64(s"${BOB.asString}:WRONG_PASSWORD")}")
    `given`()
      .headers(getHeadersWith(authHeader))
      .body(ECHO_REQUEST_OBJECT)
    .when()
      .post()
    .then
      .statusCode(SC_UNAUTHORIZED)
  }

  private def getHeadersWith(authHeader: Header): Headers = {
    new Headers(
      new Header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER),
      authHeader
    )
  }

  private def toBase64(stringValue: String): String = {
    Base64.getEncoder.encodeToString(stringValue.getBytes(UTF_8))
  }
}
