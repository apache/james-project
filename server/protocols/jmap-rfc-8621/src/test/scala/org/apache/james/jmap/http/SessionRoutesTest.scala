/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/

package org.apache.james.jmap.http

import java.nio.charset.StandardCharsets

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured
import io.restassured.builder.RequestSpecBuilder
import io.restassured.config.EncoderConfig.encoderConfig
import io.restassured.config.RestAssuredConfig.newConfig
import io.restassured.http.ContentType
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus
import org.apache.james.core.Username
import org.apache.james.jmap._
import org.apache.james.jmap.http.SessionRoutesTest.{BOB, TEST_CONFIGURATION}
import org.apache.james.jmap.model.JmapRfc8621Configuration
import org.apache.james.jmap.model.JmapRfc8621Configuration.LOCALHOST_URL_PREFIX
import org.apache.james.mailbox.MailboxSession
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import reactor.core.publisher.Mono

import scala.jdk.CollectionConverters._

object SessionRoutesTest {
  private val JMAP_SESSION = "/jmap/session"
  private val TEST_CONFIGURATION = JMAPConfiguration.builder.enable.randomPort.build
  private val BOB = Username.of("bob@james.org")
}

class SessionRoutesTest extends AnyFlatSpec with BeforeAndAfter with Matchers {

  var jmapServer: JMAPServer = _

  before {
    val mockedSession = mock(classOf[MailboxSession])
    when(mockedSession.getUser)
      .thenReturn(BOB)

    val mockedAuthFilter = mock(classOf[Authenticator])
    when(mockedAuthFilter.authenticate(any()))
      .thenReturn(Mono.just(mockedSession))

    val sessionRoutes = new SessionRoutes(
      sessionSupplier = new SessionSupplier(JmapRfc8621Configuration.LOCALHOST_CONFIGURATION),
      authenticator = mockedAuthFilter)
    jmapServer = new JMAPServer(
      TEST_CONFIGURATION,
      Set(new JMAPRoutesHandler(Version.RFC8621, sessionRoutes)).asJava,
      new VersionParser(Set(Version.RFC8621).asJava))
    jmapServer.start()

    RestAssured.requestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader(ACCEPT.toString, s"application/json; jmapVersion=${Version.RFC8621.asString}")
      .setConfig(newConfig.encoderConfig(encoderConfig.defaultContentCharset(StandardCharsets.UTF_8)))
      .setPort(jmapServer.getPort.getValue)
      .setBasePath(SessionRoutesTest.JMAP_SESSION)
      .build()
  }

  after {
    jmapServer.stop()
  }

  "get" should "return OK status" in {
    RestAssured.when()
      .get
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(ContentType.JSON)
  }

  "options" should "return OK status" in {
    RestAssured.when()
      .options
    .`then`
      .statusCode(HttpStatus.SC_OK)
  }

  "get .well-known/jmap" should "redirect" in {
    RestAssured.`given`()
      .basePath(".well-known/jmap")
      .get
    .`then`
      .statusCode(308)
      .header("Location", "/jmap/session")
  }

  "get" should "return correct session" in {
    val sessionJson = RestAssured.`with`()
        .get
      .thenReturn
        .getBody
        .asString()
    val downloadPath: String = "download/{accountId}/{blobId}/?type={type}&name={name}"
    val expectedJson = s"""{
                         |  "capabilities" : {
                         |    "urn:ietf:params:jmap:core" : {
                         |      "maxSizeUpload" : 31457280,
                         |      "maxConcurrentUpload" : 4,
                         |      "maxSizeRequest" : 10000000,
                         |      "maxConcurrentRequests" : 4,
                         |      "maxCallsInRequest" : 16,
                         |      "maxObjectsInGet" : 500,
                         |      "maxObjectsInSet" : 500,
                         |      "collationAlgorithms" : [ "i;unicode-casemap" ]
                         |    },
                         |    "urn:ietf:params:jmap:mail" : {
                         |      "maxMailboxesPerEmail" : 10000000,
                         |      "maxMailboxDepth" : null,
                         |      "maxSizeMailboxName" : 200,
                         |      "maxSizeAttachmentsPerEmail" : 20000000,
                         |      "emailQuerySortOptions" : ["receivedAt", "sentAt"],
                         |      "mayCreateTopLevelMailbox" : true
                         |    },
                         |    "urn:apache:james:params:jmap:mail:quota": {},
                         |    "urn:apache:james:params:jmap:mail:shares": {},
                         |    "urn:ietf:params:jmap:vacationresponse":{}
                         |  },
                         |  "accounts" : {
                         |    "0fe275bf13ff761407c17f64b1dfae2f4b3186feea223d7267b79f873a105401" : {
                         |      "name" : "bob@james.org",
                         |      "isPersonal" : true,
                         |      "isReadOnly" : false,
                         |      "accountCapabilities" : {
                         |        "urn:ietf:params:jmap:core" : {
                         |          "maxSizeUpload" : 31457280,
                         |          "maxConcurrentUpload" : 4,
                         |          "maxSizeRequest" : 10000000,
                         |          "maxConcurrentRequests" : 4,
                         |          "maxCallsInRequest" : 16,
                         |          "maxObjectsInGet" : 500,
                         |          "maxObjectsInSet" : 500,
                         |          "collationAlgorithms" : [ "i;unicode-casemap" ]
                         |        },
                         |        "urn:ietf:params:jmap:mail" : {
                         |          "maxMailboxesPerEmail" : 10000000,
                         |          "maxMailboxDepth" : null,
                         |          "maxSizeMailboxName" : 200,
                         |          "maxSizeAttachmentsPerEmail" : 20000000,
                         |          "emailQuerySortOptions" : ["receivedAt", "sentAt"],
                         |          "mayCreateTopLevelMailbox" : true
                         |        },
                         |        "urn:apache:james:params:jmap:mail:quota": {},
                         |        "urn:apache:james:params:jmap:mail:shares": {},
                         |        "urn:ietf:params:jmap:vacationresponse":{}
                         |      }
                         |    }
                         |  },
                         |  "primaryAccounts" : {
                         |    "urn:ietf:params:jmap:core" : "0fe275bf13ff761407c17f64b1dfae2f4b3186feea223d7267b79f873a105401",
                         |    "urn:ietf:params:jmap:mail" : "0fe275bf13ff761407c17f64b1dfae2f4b3186feea223d7267b79f873a105401",
                         |    "urn:apache:james:params:jmap:mail:quota": "0fe275bf13ff761407c17f64b1dfae2f4b3186feea223d7267b79f873a105401",
                         |    "urn:apache:james:params:jmap:mail:shares": "0fe275bf13ff761407c17f64b1dfae2f4b3186feea223d7267b79f873a105401",
                         |    "urn:ietf:params:jmap:vacationresponse": "0fe275bf13ff761407c17f64b1dfae2f4b3186feea223d7267b79f873a105401"
                         |  },
                         |  "username" : "bob@james.org",
                         |  "apiUrl" : "$LOCALHOST_URL_PREFIX/jmap",
                         |  "downloadUrl" : "$LOCALHOST_URL_PREFIX/$downloadPath",
                         |  "uploadUrl" : "$LOCALHOST_URL_PREFIX/upload/{accountId}",
                         |  "eventSourceUrl" : "$LOCALHOST_URL_PREFIX/eventSource",
                         |  "state" : "000001"
                         |}""".stripMargin

    assertThatJson(sessionJson).isEqualTo(expectedJson)
  }
}
