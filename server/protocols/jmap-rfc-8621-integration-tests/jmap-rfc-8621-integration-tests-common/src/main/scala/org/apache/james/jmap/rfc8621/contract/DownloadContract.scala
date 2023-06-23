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

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import org.apache.commons.io.IOUtils
import org.apache.http.HttpStatus.{SC_FORBIDDEN, SC_NOT_FOUND, SC_OK, SC_UNAUTHORIZED}
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.DownloadContract.accountId
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ALICE_ACCOUNT_ID, ANDRE, BOB, BOB_PASSWORD, CEDRIC, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.MailboxACL.Right
import org.apache.james.mailbox.model.{MailboxACL, MailboxPath, MessageId}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.{ACLProbeImpl, MailboxProbeImpl}
import org.apache.james.util.ClassLoaderUtils
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers
import org.hamcrest.Matchers.{containsString, equalTo}
import org.junit.jupiter.api.{BeforeEach, Test}

object DownloadContract {
  val accountId = "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
}

trait DownloadContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)
      .addUser(ANDRE.asString, BOB_PASSWORD)
      .addUser(CEDRIC.asString, BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  def randomMessageId: MessageId

  @Test
  def downloadMessage(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId


    val response = `given`
        .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$accountId/${messageId.serialize()}")
    .`then`
      .statusCode(SC_OK)
      .contentType("message/rfc822")
      .extract
      .body
      .asString

    val expectedResponse: String = IOUtils.toString(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml"),
      StandardCharsets.UTF_8)
    assertThat(new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)))
      .hasContent(expectedResponse)
  }

  @Test
  def downloadMessageShouldFailWhenUnauthentified(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    `given`
      .auth().none()
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$accountId/${messageId.serialize()}")
    .`then`
      .statusCode(SC_UNAUTHORIZED)
      .header("WWW-Authenticate", "Basic realm=\"simple\", Bearer realm=\"JWT\"")
      .body("status", equalTo(401))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("No valid authentication methods provided"))
  }

  @Test
  def downloadMessageShouldSucceedWhenAddedRightACL(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(ANDRE)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, BOB.asString(), new MailboxACL.Rfc4314Rights(Right.Read, Right.Lookup))

    val response = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$accountId/${messageId.serialize()}")
    .`then`
      .statusCode(SC_OK)
      .contentType("message/rfc822")
      .extract
      .body
      .asString

    val expectedResponse: String = IOUtils.toString(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml"),
      StandardCharsets.UTF_8)
    assertThat(new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)))
      .hasContent(expectedResponse)
  }

  @Test
  def downloadingOtherPeopleMessageShouldFail(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(ANDRE)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$accountId/${messageId.serialize()}")
    .`then`
      .statusCode(SC_NOT_FOUND)
      .body("status", equalTo(404))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("The resource could not be found"))
  }

  @Test
  def downloadingInOtherAccountsShouldFail(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$ALICE_ACCOUNT_ID/${messageId.serialize}")
    .`then`
      .statusCode(SC_FORBIDDEN)
      .body("status", equalTo(403))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("You cannot download in others accounts"))
  }

  @Test
  def downloadPartShouldSucceedWhenAddedRightACL(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(ANDRE)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, BOB.asString(), new MailboxACL.Rfc4314Rights(Right.Read, Right.Lookup))

    val response = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$accountId/${messageId.serialize()}_3")
    .`then`
      .statusCode(SC_OK)
      .contentType("text/plain")
      .extract
      .body
      .asString

    val expectedResponse: String =
      """-----BEGIN RSA PRIVATE KEY-----
        |MIIEogIBAAKCAQEAx7PG0+E//EMpm7IgI5Q9TMDSFya/1hE+vvTJrk0iGFllPeHL
        |A5/VlTM0YWgG6X50qiMfE3VLazf2c19iXrT0mq/21PZ1wFnogv4zxUNaih+Bng62
        |F0SyruE/O/Njqxh/Ccq6K/e05TV4T643USxAeG0KppmYW9x8HA/GvV832apZuxkV
        |i6NVkDBrfzaUCwu4zH+HwOv/pI87E7KccHYC++Biaj3
        |""".stripMargin
    assertThat(new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)))
      .hasContent(expectedResponse)
  }

  @Test
  def downloadingOtherPeopleMessagePartShouldFail(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(ANDRE)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$accountId/${messageId.serialize()}_3")
    .`then`
      .statusCode(SC_NOT_FOUND)
      .body("status", equalTo(404))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("The resource could not be found"))
  }

  @Test
  def downloadPart(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    val response = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$accountId/${messageId.serialize()}_3")
    .`then`
      .statusCode(SC_OK)
      .contentType("text/plain")
      .extract
      .body
      .asString

    val expectedResponse: String =
      """-----BEGIN RSA PRIVATE KEY-----
        |MIIEogIBAAKCAQEAx7PG0+E//EMpm7IgI5Q9TMDSFya/1hE+vvTJrk0iGFllPeHL
        |A5/VlTM0YWgG6X50qiMfE3VLazf2c19iXrT0mq/21PZ1wFnogv4zxUNaih+Bng62
        |F0SyruE/O/Njqxh/Ccq6K/e05TV4T643USxAeG0KppmYW9x8HA/GvV832apZuxkV
        |i6NVkDBrfzaUCwu4zH+HwOv/pI87E7KccHYC++Biaj3
        |""".stripMargin
    assertThat(new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)))
      .hasContent(expectedResponse)
  }

  @Test
  def userCanSpecifyContentTypeWhenDownloadingMessage(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .queryParam("type", "text/plain")
    .when
      .get(s"/download/$accountId/${messageId.serialize()}")
    .`then`
      .statusCode(SC_OK)
      .contentType("text/plain")
  }

  @Test
  def userCanSpecifyContentTypeWhenDownloadingPart(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .queryParam("type", "text/markdown")
    .when
      .get(s"/download/$accountId/${messageId.serialize()}_3")
    .`then`
      .statusCode(SC_OK)
      .contentType("text/markdown")
  }

  @Test
  def downloadPartShouldDiscardNameWhenNotSuppliedByTheClient(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    val contentDisposition = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$accountId/${messageId.serialize()}_3")
    .`then`
      .statusCode(SC_OK)
      .extract()
      .header("Content-Disposition")

    assertThat(contentDisposition).isNullOrEmpty()
  }

  @Test
  def userCanSpecifyNameWhenDownloadingPart(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .queryParam("name", "gabouzomeuh.txt")
    .when
      .get(s"/download/$accountId/${messageId.serialize()}_3")
    .`then`
      .statusCode(SC_OK)
      .header("Content-Disposition", containsString("filename=\"gabouzomeuh.txt\""))
  }

  @Test
  def downloadMessageShouldDiscardNameWhenNotSuppliedByTheClient(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    val contentDisposition = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$accountId/${messageId.serialize()}")
    .`then`
      .statusCode(SC_OK)
      .extract().header("Content-Disposition")

    assertThat(contentDisposition).isNullOrEmpty()
  }

  @Test
  def userCanSpecifyNameWhenDownloadingMessage(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .queryParam("name", "gabouzomeuh.eml")
    .when
      .get(s"/download/$accountId/${messageId.serialize()}")
    .`then`
      .statusCode(SC_OK)
      .header("Content-Disposition", containsString("filename=\"gabouzomeuh.eml\""))
  }

  @Test
  def downloadNotExistingPart(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$accountId/${messageId.serialize()}_333")
    .`then`
      .statusCode(SC_NOT_FOUND)
      .body("status", equalTo(404))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("The resource could not be found"))
  }

  @Test
  def downloadInvalidPart(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$accountId/${messageId.serialize()}_invalid")
    .`then`
      .statusCode(SC_NOT_FOUND)
      .body("status", equalTo(404))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("The resource could not be found"))
  }

  @Test
  def downloadWithInvalidId(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))

    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$accountId/invalid")
    .`then`
      .statusCode(SC_NOT_FOUND)
      .body("status", equalTo(404))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("The resource could not be found"))
  }

  @Test
  def downloadWithNotFoundId(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))

    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$accountId/${randomMessageId.serialize()}")
    .`then`
      .statusCode(SC_NOT_FOUND)
      .body("status", equalTo(404))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("The resource could not be found"))
  }

  @Test
  def downloadPartWhenMessageNotFound(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))

    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$accountId/${randomMessageId.serialize()}_3")
    .`then`
      .statusCode(SC_NOT_FOUND)
      .body("status", equalTo(404))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("The resource could not be found"))
  }

  @Test
  def downloadPartWhenMultipart(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))

    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$accountId/${randomMessageId.serialize()}_2")
    .`then`
      .statusCode(SC_NOT_FOUND)
      .body("status", equalTo(404))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("The resource could not be found"))
  }

  @Test
  def downloadPartWhenTooMuchUnderscore(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$accountId/${messageId.serialize()}_3_3")
    .`then`
      .statusCode(SC_NOT_FOUND)
      .body("status", equalTo(404))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("The resource could not be found"))
  }

  @Test
  def downloadMessageShouldSucceedWhenDelegatedAccount(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(ANDRE)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    server.getProbe(classOf[DataProbeImpl]).addAuthorizedUser(ANDRE, BOB)

    val response = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/${Fixture.ANDRE_ACCOUNT_ID}/${messageId.serialize()}")
    .`then`
      .statusCode(SC_OK)
      .contentType("message/rfc822")
      .extract
      .body
      .asString

    val expectedResponse: String = IOUtils.toString(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml"),
      StandardCharsets.UTF_8)
    assertThat(new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)))
      .hasContent(expectedResponse)
  }

  @Test
  def downloadMessageShouldFailWhenNotDelegatedAccount(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(ANDRE)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    server.getProbe(classOf[DataProbeImpl]).addAuthorizedUser(ANDRE, CEDRIC)

    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/${Fixture.ANDRE_ACCOUNT_ID}/${messageId.serialize()}")
    .`then`
      .statusCode(SC_FORBIDDEN)
      .body("status", equalTo(403))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("You cannot download in others accounts"))
  }

  @Test
  def downloadPartMessageShouldSucceedWhenDelegatedAccount(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(ANDRE)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple.eml")))
      .getMessageId

    server.getProbe(classOf[DataProbeImpl]).addAuthorizedUser(ANDRE, BOB)

    val response = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/${Fixture.ANDRE_ACCOUNT_ID}/${messageId.serialize()}_3")
    .`then`
      .statusCode(SC_OK)
      .contentType("text/plain")
    .extract
      .body
      .asString

    val expectedResponse: String =
      """-----BEGIN RSA PRIVATE KEY-----
        |MIIEogIBAAKCAQEAx7PG0+E//EMpm7IgI5Q9TMDSFya/1hE+vvTJrk0iGFllPeHL
        |A5/VlTM0YWgG6X50qiMfE3VLazf2c19iXrT0mq/21PZ1wFnogv4zxUNaih+Bng62
        |F0SyruE/O/Njqxh/Ccq6K/e05TV4T643USxAeG0KppmYW9x8HA/GvV832apZuxkV
        |i6NVkDBrfzaUCwu4zH+HwOv/pI87E7KccHYC++Biaj3
        |""".stripMargin
    assertThat(new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)))
      .hasContent(expectedResponse)
  }

  @Test
  def downloadShouldFailWhenEmailPartInvalid(server: GuiceJamesServer): Unit = {
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)

    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), path, AppendCommand.from(message))
      .getMessageId.serialize()

    val messageAndInvalidPart: String = messageId + "_"

    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$accountId/$messageAndInvalidPart")
    .`then`
      .statusCode(404)
      .body(Matchers.containsString("The resource could not be found"))
  }
}
