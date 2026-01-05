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
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.{SC_CREATED, SC_OK}
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.AccountId
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.BlobCopyContract.BYTES
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ALICE, ALICE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, _2_DOT_DOMAIN, authScheme, baseRequestSpecBuilder, ACCOUNT_ID => BOB_ACCOUNT_ID}
import org.apache.james.junit.categories.BasicFeature
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.experimental.categories.Category
import org.junit.jupiter.api.{BeforeEach, Test}
import play.api.libs.json.{JsObject, JsString, Json}

object BlobCopyContract {
  val BYTES: Array[Byte] = "123456789\r\n".repeat(1024 * 64).getBytes(StandardCharsets.UTF_8)
}

trait BlobCopyContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addDomain(_2_DOT_DOMAIN.asString())
      .addUser(BOB.asString, BOB_PASSWORD)
      .addUser(ALICE.asString(), ALICE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  @Category(Array(classOf[BasicFeature]))
  @Test
  def shouldCopyBlobBetweenAccountsWhenDelegated(server: GuiceJamesServer): Unit = {
    // Alice delegates Bob to access her account
    server.getProbe(classOf[DataProbeImpl]).addAuthorizedUser(ALICE, BOB)

    // Bob uploads a blob to his account
    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType(ContentType.BINARY)
      .body(BYTES)
    .when
      .post(s"/upload/$BOB_ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val bobBlobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value

    val aliceAccountId: String = AccountId.from(ALICE).toOption.get.id.value

    // Bob copies the blob from his account to Alice's account
    val request: String =
      s"""{
         |  "using": [ "urn:ietf:params:jmap:core" ],
         |  "methodCalls": [[
         |    "Blob/copy",
         |    {
         |      "fromAccountId": "$BOB_ACCOUNT_ID",
         |      "accountId": "$aliceAccountId",
         |      "blobIds": [ "$bobBlobId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response: String = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(ContentType.JSON)
      .extract
      .body
      .asString

    val copied: JsObject = (Json.parse(response) \ "methodResponses")(0)(1).\("copied").get.as[JsObject]
    val copiedBlobId: String = copied.value(bobBlobId).as[JsString].value

    // Alice downloads the copied blob from her account
    val downloadResponse: Array[Byte] = `given`
      .auth().basic(ALICE.asString(), ALICE_PASSWORD)
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$aliceAccountId/$copiedBlobId")
    .`then`
      .statusCode(SC_OK)
      .extract
      .body
      .asByteArray()

    assertThat(new ByteArrayInputStream(downloadResponse))
      .hasBinaryContent(BYTES)
  }

  @Test
  def shouldReturnNotFoundForNonExistingBlob(): Unit = {
    val notFoundBlobId: String = "notFoundBlobId"

    val request: String =
      s"""{
         |  "using": [ "urn:ietf:params:jmap:core" ],
         |  "methodCalls": [[
         |    "Blob/copy",
         |    {
         |      "fromAccountId": "$BOB_ACCOUNT_ID",
         |      "accountId": "$BOB_ACCOUNT_ID",
         |      "blobIds": [ "$notFoundBlobId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response: String = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(ContentType.JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .inPath("methodResponses")
      .isEqualTo(
        s"""[[
           |  "Blob/copy",
           |  {
           |    "fromAccountId":"$BOB_ACCOUNT_ID",
           |    "accountId":"$BOB_ACCOUNT_ID",
           |    "notCopied":{
           |      "$notFoundBlobId":{
           |        "type":"notFound",
           |        "description":"Blob BlobId($notFoundBlobId) could not be found"
           |      }
           |    }
           |  },
           |  "c1"
           |]]""".stripMargin)
  }

  @Test
  def shouldReturnFromAccountNotFoundWhenFromAccountInvalid(): Unit = {
    val request: String =
      s"""{
         |  "using": [ "urn:ietf:params:jmap:core" ],
         |  "methodCalls": [[
         |    "Blob/copy",
         |    {
         |      "fromAccountId": "unknownFromAccountId",
         |      "accountId": "$BOB_ACCOUNT_ID",
         |      "blobIds": [ "blobId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response: String = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(ContentType.JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .inPath("methodResponses")
      .isEqualTo(
        """[["error",{"type":"fromAccountNotFound"},"c1"]]""")
  }

  @Category(Array(classOf[BasicFeature]))
  @Test
  def shouldFailWhenTargetAccountNotDelegated(): Unit = {
    // Alice does NOT delegate her account to Bob

    // upload a blob to Bob's account
    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType(ContentType.BINARY)
      .body(BYTES)
    .when
      .post(s"/upload/$BOB_ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val bobBlobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value
    val aliceAccountId: String = AccountId.from(ALICE).toOption.get.id.value

    // Bob tries to copy the blob from his account to Alice's account
    val request: String =
      s"""{
         |  "using": [ "urn:ietf:params:jmap:core" ],
         |  "methodCalls": [[
         |    "Blob/copy",
         |    {
         |      "fromAccountId": "$BOB_ACCOUNT_ID",
         |      "accountId": "$aliceAccountId",
         |      "blobIds": [ "$bobBlobId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response: String = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(ContentType.JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .inPath("methodResponses")
      .isEqualTo(
        """[["error",{"type":"accountNotFound"},"c1"]]""")
  }
}
