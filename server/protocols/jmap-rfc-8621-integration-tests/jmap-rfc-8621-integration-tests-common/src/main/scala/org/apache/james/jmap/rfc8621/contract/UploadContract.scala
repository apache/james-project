package org.apache.james.jmap.rfc8621.contract

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.charset.StandardCharsets
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.commons.io.IOUtils
import org.apache.http.HttpStatus.{SC_CREATED, SC_NOT_FOUND, SC_OK, SC_UNAUTHORIZED}
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, BOB, BOB_PASSWORD, DOMAIN, RFC8621_VERSION_HEADER, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.UploadContract.{BIG_INPUT_STREAM, VALID_INPUT_STREAM}
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}
import play.api.libs.json.{JsString, Json}

object UploadContract {
  private val BIG_INPUT_STREAM: InputStream = new ByteArrayInputStream("123456789\r\n".repeat(10025).getBytes)
  private val VALID_INPUT_STREAM: InputStream = new ByteArrayInputStream("123456789\r\n".repeat(1).getBytes)
}

trait UploadContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  @Test
  def shouldUploadFileAndOnlyOwnerCanAccess(): Unit = {
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
  def shouldRejectWhenUploadFileTooBig(): Unit = {
    val response: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType(ContentType.BINARY)
      .body(BIG_INPUT_STREAM)
    .when
      .post(s"/upload/$ACCOUNT_ID/")
    .`then`
      .statusCode(SC_OK)
      .extract
      .body
      .asString

    // fixme: dont know we limit size or not?
    assertThatJson(response)
      .isEqualTo("Should be error")
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

  @Test
  def uploadShouldSucceedButExpiredWhenDownload(): Unit = {
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

    // fixme: dont know how to delete file with existing attachment api
    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$ACCOUNT_ID/$blobId")
    .`then`
      .statusCode(SC_NOT_FOUND)
  }
}
