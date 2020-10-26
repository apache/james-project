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

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.charset.StandardCharsets

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType
import org.apache.commons.io.IOUtils
import org.apache.http.HttpStatus.{SC_BAD_REQUEST, SC_CREATED, SC_OK, SC_UNAUTHORIZED}
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ALICE, ALICE_ACCOUNT_ID, ALICE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, RFC8621_VERSION_HEADER, _2_DOT_DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.UploadContract.{BIG_INPUT_STREAM, VALID_INPUT_STREAM}
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}
import play.api.libs.json.{JsString, Json}

object UploadContract {
  private val BIG_INPUT_STREAM: InputStream = new ByteArrayInputStream("123456789\r\n".repeat(1024 * 1024 * 4).getBytes)
  private val VALID_INPUT_STREAM: InputStream = new ByteArrayInputStream("123456789\r\n".repeat(1024 * 1024 * 3).getBytes)
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

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  @Test
  def shouldUploadFileAndAllowToDownloadIt(): Unit = {
    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(VALID_INPUT_STREAM)
    .when
      .post(s"/upload/$ACCOUNT_ID/")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val blobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value

    val downloadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$ACCOUNT_ID/$blobId")
    .`then`
      .statusCode(SC_OK)
      .extract
      .body
      .asString

    val expectedResponse: String = IOUtils.toString(VALID_INPUT_STREAM, StandardCharsets.UTF_8)

    assertThat(new ByteArrayInputStream(downloadResponse.getBytes(StandardCharsets.UTF_8)))
      .hasContent(expectedResponse)
  }

  @Test
  def bobShouldNotBeAllowedToUploadInAliceAccount(): Unit = {
    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(VALID_INPUT_STREAM)
    .when
      .post(s"/upload/$ALICE_ACCOUNT_ID/")
    .`then`
      .statusCode(SC_UNAUTHORIZED)
  }

  @Test
  def aliceShouldNotAccessOrDownloadFileUploadedByBob(): Unit = {
    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(VALID_INPUT_STREAM)
    .when
      .post(s"/upload/$ACCOUNT_ID/")
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
      .statusCode(SC_UNAUTHORIZED)
  }

  @Test
  def shouldRejectWhenUploadFileTooBig(): Unit = {
    val response: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType(ContentType.BINARY)
      .body(BIG_INPUT_STREAM)
    .when
      .post(s"/upload/$ACCOUNT_ID/")
    .`then`
      .statusCode(SC_BAD_REQUEST)
      .extract
      .body
      .asString

    assertThat(response)
      .contains("Attempt to upload exceed max size")
  }

  @Test
  def uploadShouldRejectWhenUnauthenticated(): Unit = {
    `given`
      .auth()
      .none()
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType(ContentType.BINARY)
      .body(VALID_INPUT_STREAM)
    .when
      .post(s"/upload/$ACCOUNT_ID/")
    .`then`
      .statusCode(SC_UNAUTHORIZED)
  }
}
