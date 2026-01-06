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

import com.google.common.base.Strings
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType
import io.restassured.path.json.JsonPath
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.{SC_CREATED, SC_OK}
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.AccountId
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.BlobCopyContract.{ALICE_ACCOUNT_ID, TEN_KILO_BYTES}
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ALICE, ALICE_PASSWORD, ANDRE, ANDRE_ACCOUNT_ID, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, _2_DOT_DOMAIN, authScheme, baseRequestSpecBuilder, ACCOUNT_ID => BOB_ACCOUNT_ID}
import org.apache.james.junit.categories.BasicFeature
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.experimental.categories.Category
import org.junit.jupiter.api.{BeforeEach, Test}

object BlobCopyContract {
  val TWENTY_KILO_BYTES_UPLOAD_QUOTA_LIMIT: String = "20K"
  val TEN_KILO_BYTES: Array[Byte] = Strings.repeat("0123456789\r\n", 853).getBytes(StandardCharsets.UTF_8)
  val ALICE_ACCOUNT_ID: String = AccountId.from(ALICE).toOption.get.id.value
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
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)

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
    val bobBlobId: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType(ContentType.BINARY)
      .body(TEN_KILO_BYTES)
    .when
      .post(s"/upload/$BOB_ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .jsonPath()
      .getString("blobId")

    // Bob copies the blob from his account to Alice's account
    val request: String =
      s"""{
         |  "using": [ "urn:ietf:params:jmap:core" ],
         |  "methodCalls": [[
         |    "Blob/copy",
         |    {
         |      "fromAccountId": "$BOB_ACCOUNT_ID",
         |      "accountId": "$ALICE_ACCOUNT_ID",
         |      "blobIds": [ "$bobBlobId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val copiedBlobId: String = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(ContentType.JSON)
      .extract
      .jsonPath()
      .getString(s"methodResponses[0][1].copied.$bobBlobId")

    // Alice downloads the copied blob from her account
    val downloadResponse: Array[Byte] = `given`
      .auth().basic(ALICE.asString(), ALICE_PASSWORD)
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$ALICE_ACCOUNT_ID/$copiedBlobId")
    .`then`
      .statusCode(SC_OK)
      .extract
      .body
      .asByteArray()

    assertThat(new ByteArrayInputStream(downloadResponse))
      .hasBinaryContent(TEN_KILO_BYTES)
  }

  @Test
  def shouldCopyBlobFromAliceToBobWhenBobDelegated(server: GuiceJamesServer): Unit = {
    // Alice delegates Bob to access her account
    server.getProbe(classOf[DataProbeImpl]).addAuthorizedUser(ALICE, BOB)

    // Alice uploads a blob to her account
    val aliceBlobId: String = `given`
      .auth().preemptive().basic(ALICE.asString(), ALICE_PASSWORD)
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType(ContentType.BINARY)
      .body(TEN_KILO_BYTES)
    .when
      .post(s"/upload/$ALICE_ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .jsonPath()
      .getString("blobId")

    // Bob copies the blob from Alice's account to his account
    val request: String =
      s"""{
         |  "using": [ "urn:ietf:params:jmap:core" ],
         |  "methodCalls": [[
         |    "Blob/copy",
         |    {
         |      "fromAccountId": "$ALICE_ACCOUNT_ID",
         |      "accountId": "$BOB_ACCOUNT_ID",
         |      "blobIds": [ "$aliceBlobId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val copiedBlobId: String = `given`
      .auth().preemptive().basic(BOB.asString(), BOB_PASSWORD)
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(ContentType.JSON)
      .extract
      .jsonPath()
      .getString(s"methodResponses[0][1].copied.$aliceBlobId")

    // Bob downloads the copied blob from his account
    val downloadResponse: Array[Byte] = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$BOB_ACCOUNT_ID/$copiedBlobId")
    .`then`
      .statusCode(SC_OK)
      .extract
      .body
      .asByteArray()

    assertThat(new ByteArrayInputStream(downloadResponse))
      .hasBinaryContent(TEN_KILO_BYTES)
  }

  @Test
  def transitiveDelegationShouldNotWorkForTargetAccountId(server: GuiceJamesServer): Unit = {
    // Andre delegates to Alice; Alice delegates to Bob
    server.getProbe(classOf[DataProbeImpl]).addAuthorizedUser(ANDRE, ALICE)
    server.getProbe(classOf[DataProbeImpl]).addAuthorizedUser(ALICE, BOB)

    // Bob uploads a blob to his account
    val bobBlobId: String = `given`
      .auth().preemptive().basic(BOB.asString(), BOB_PASSWORD)
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType(ContentType.BINARY)
      .body(TEN_KILO_BYTES)
    .when
      .post(s"/upload/$ALICE_ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .jsonPath()
      .getString("blobId")

    // Bob tries to copy from his account to Andre (transitive delegation should not work)
    val request: String =
      s"""{
         |  "using": [ "urn:ietf:params:jmap:core" ],
         |  "methodCalls": [[
         |    "Blob/copy",
         |    {
         |      "fromAccountId": "$BOB_ACCOUNT_ID",
         |      "accountId": "$ANDRE_ACCOUNT_ID",
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

  @Test
  def transitiveDelegationShouldNotWorkForFromAccountId(server: GuiceJamesServer): Unit = {
    // Andre delegates to Alice; Alice delegates to Bob
    server.getProbe(classOf[DataProbeImpl]).addAuthorizedUser(ANDRE, ALICE)
    server.getProbe(classOf[DataProbeImpl]).addAuthorizedUser(ALICE, BOB)

    // Andre uploads a blob to his account
    val andreBlobId: String = `given`
      .auth().preemptive().basic(ANDRE.asString(), ANDRE_PASSWORD)
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType(ContentType.BINARY)
      .body(TEN_KILO_BYTES)
    .when
      .post(s"/upload/$ANDRE_ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .jsonPath()
      .getString("blobId")

    // Bob tries to copy blob from Andre to his account (transitive delegation should fail)
    val request: String =
      s"""{
         |  "using": [ "urn:ietf:params:jmap:core" ],
         |  "methodCalls": [[
         |    "Blob/copy",
         |    {
         |      "fromAccountId": "$ANDRE_ACCOUNT_ID",
         |      "accountId": "$BOB_ACCOUNT_ID",
         |      "blobIds": [ "$andreBlobId" ]
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

  @Test
  def copyBlobShouldSucceedWhenUploadQuotaExceeded(): Unit = {
    // Given upload quota is 20KB, upload 2 x 10KB blobs to reach the upload quota
    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType(ContentType.BINARY)
      .body(TEN_KILO_BYTES)
    .when
      .post(s"/upload/$BOB_ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .jsonPath()
      .getString("blobId")

    val secondBlobId: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType(ContentType.BINARY)
      .body(TEN_KILO_BYTES)
    .when
      .post(s"/upload/$BOB_ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .jsonPath()
      .getString("blobId")

    // Upload quota should be reached, now copy 10KB blob to the same account
    val request: String =
      s"""{
         |  "using": [ "urn:ietf:params:jmap:core" ],
         |  "methodCalls": [[
         |    "Blob/copy",
         |    {
         |      "fromAccountId": "$BOB_ACCOUNT_ID",
         |      "accountId": "$BOB_ACCOUNT_ID",
         |      "blobIds": [ "$secondBlobId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val copiedBlobId: String = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(ContentType.JSON)
      .extract
      .jsonPath()
      .getString(s"methodResponses[0][1].copied.$secondBlobId")

    // Download the copied blob should succeed
    val downloadResponse: Array[Byte] = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$BOB_ACCOUNT_ID/$copiedBlobId")
    .`then`
      .statusCode(SC_OK)
      .extract
      .body
      .asByteArray()
    assertThat(new ByteArrayInputStream(downloadResponse))
      .hasBinaryContent(TEN_KILO_BYTES)
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
  def shouldReturnCopiedAndNotCopiedWhenMixingExistingAndMissingBlobs(): Unit = {
    val existingBlobId: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType(ContentType.BINARY)
      .body(TEN_KILO_BYTES)
    .when
      .post(s"/upload/$BOB_ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .jsonPath()
      .getString("blobId")

    val notFoundBlobId: String = "notFoundBlobId"

    val request: String =
      s"""{
         |  "using": [ "urn:ietf:params:jmap:core" ],
         |  "methodCalls": [[
         |    "Blob/copy",
         |    {
         |      "fromAccountId": "$BOB_ACCOUNT_ID",
         |      "accountId": "$BOB_ACCOUNT_ID",
         |      "blobIds": [ "$existingBlobId", "$notFoundBlobId" ]
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

    val copiedBlobId: String = JsonPath.from(response).getString(s"methodResponses[0][1].copied.$existingBlobId")

    assertThatJson(response)
      .inPath("methodResponses")
      .isEqualTo(
        s"""[[
           |  "Blob/copy",
           |  {
           |    "fromAccountId":"$BOB_ACCOUNT_ID",
           |    "accountId":"$BOB_ACCOUNT_ID",
           |    "copied":{
           |      "$existingBlobId":"$copiedBlobId"
           |    },
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
    val bobBlobId: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType(ContentType.BINARY)
      .body(TEN_KILO_BYTES)
    .when
      .post(s"/upload/$BOB_ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .jsonPath()
      .getString("blobId")

    // Bob tries to copy the blob from his account to Alice's account
    val request: String =
      s"""{
         |  "using": [ "urn:ietf:params:jmap:core" ],
         |  "methodCalls": [[
         |    "Blob/copy",
         |    {
         |      "fromAccountId": "$BOB_ACCOUNT_ID",
         |      "accountId": "$ALICE_ACCOUNT_ID",
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

  @Test
  def shouldFailWhenAliceCopiesToBobWithoutDelegation(server: GuiceJamesServer): Unit = {
    // Alice delegates Bob to access her account
    server.getProbe(classOf[DataProbeImpl]).addAuthorizedUser(ALICE, BOB)

    // Alice uploads a blob to her account
    val aliceBlobId: String = `given`
      .auth().preemptive().basic(ALICE.asString(), ALICE_PASSWORD)
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType(ContentType.BINARY)
      .body(TEN_KILO_BYTES)
    .when
      .post(s"/upload/$ALICE_ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .jsonPath()
      .getString("blobId")

    // Alice tries to copy the blob to Bob's account (no delegation from Bob to Alice)
    val request: String =
      s"""{
         |  "using": [ "urn:ietf:params:jmap:core" ],
         |  "methodCalls": [[
         |    "Blob/copy",
         |    {
         |      "fromAccountId": "$ALICE_ACCOUNT_ID",
         |      "accountId": "$BOB_ACCOUNT_ID",
         |      "blobIds": [ "$aliceBlobId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response: String = `given`
      .auth().preemptive().basic(ALICE.asString(), ALICE_PASSWORD)
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
