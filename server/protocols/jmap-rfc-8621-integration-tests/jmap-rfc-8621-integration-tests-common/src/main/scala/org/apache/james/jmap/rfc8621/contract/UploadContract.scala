/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *  http://www.apache.org/licenses/LICENSE-2.0                  *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.jmap.rfc8621.contract

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType
import org.apache.http.HttpStatus.{SC_BAD_REQUEST, SC_CREATED, SC_FORBIDDEN, SC_OK, SC_UNAUTHORIZED}
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.AccountId
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ALICE, ALICE_ACCOUNT_ID, ALICE_PASSWORD, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, _2_DOT_DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.UploadContract.{BIG_INPUT, VALID_INPUT}
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.{BeforeEach, RepeatedTest, Test}
import play.api.libs.json.{JsString, Json}

object UploadContract {
  private val BIG_INPUT: Array[Byte] = "123456789\r\n".repeat(1024 * 1024 * 4).getBytes(StandardCharsets.UTF_8)
  private val VALID_INPUT: Array[Byte] = "123456789\r\n".repeat(1024 * 1024).getBytes(StandardCharsets.UTF_8)
}

trait UploadContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)
      .addDomain(_2_DOT_DOMAIN.asString())
      .addUser(ALICE.asString(), ALICE_PASSWORD)
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  @RepeatedTest(20)
  def shouldUploadFileAndAllowToDownloadIt(): Unit = {
    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(VALID_INPUT)
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val blobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value

    val downloadResponse: Array[Byte] = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$ACCOUNT_ID/$blobId")
    .`then`
      .statusCode(SC_OK)
      .contentType("application/json")
      .extract
      .body
      .asByteArray()

    assertThat(new ByteArrayInputStream(downloadResponse))
      .hasBinaryContent(VALID_INPUT)
  }

  @Test
  def bobShouldNotBeAllowedToUploadInAliceAccount(): Unit = {
    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(VALID_INPUT)
    .when
      .post(s"/upload/$ALICE_ACCOUNT_ID")
    .`then`
      .statusCode(SC_FORBIDDEN)
      .header("Content-Length", "84")
      .body("status", equalTo(403))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("Upload to other accounts is forbidden"))
  }

  @Test
  def aliceShouldNotAccessOrDownloadFileUploadedByBob(): Unit = {
    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(VALID_INPUT)
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val blobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value

    `given`
      .auth().basic(ALICE.asString(), ALICE_PASSWORD)
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$ALICE_ACCOUNT_ID/$blobId")
    .`then`
      .statusCode(SC_FORBIDDEN)
      .body("status", equalTo(403))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("You cannot download in others accounts"))
  }

  @Test
  def shouldRejectWhenUploadFileTooBig(): Unit = {
    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType(ContentType.BINARY)
      .body(BIG_INPUT)
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_BAD_REQUEST)
      .body("status", equalTo(400))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("Attempt to upload exceed max size"))
  }

  @Test
  def uploadShouldRejectWhenUnauthenticated(): Unit = {
    `given`
      .auth()
      .none()
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType(ContentType.BINARY)
      .body(VALID_INPUT)
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_UNAUTHORIZED)
      .header("WWW-Authenticate", "Basic realm=\"simple\", Bearer realm=\"JWT\"")
      .body("status", equalTo(401))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("No valid authentication methods provided"))
  }

  @Test
  def bobShouldBeAllowedToUploadInAliceAccountWhenDelegated(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl]).addAuthorizedUser(ALICE, BOB)

    val aliceAccountId: String = AccountId.from(ALICE).toOption.get.id.value

    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(VALID_INPUT)
    .when
      .post(s"/upload/$aliceAccountId")
    .`then`
      .statusCode(SC_CREATED)
      .body("size", equalTo(11534336))
      .body("type", equalTo("application/json; charset=UTF-8"))
      .body("blobId", Matchers.notNullValue())
      .body("accountId", equalTo(aliceAccountId))
  }

  @Test
  def bobShouldBeNotAllowedToUploadInAliceAccountWhenNotDelegated(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl]).addAuthorizedUser(ALICE, ANDRE)

    val aliceAccountId: String = AccountId.from(ALICE).toOption.get.id.value

    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(VALID_INPUT)
    .when
      .post(s"/upload/$aliceAccountId")
    .`then`
      .statusCode(SC_FORBIDDEN)
      .body("status", equalTo(403))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("Upload to other accounts is forbidden"))
  }

}
