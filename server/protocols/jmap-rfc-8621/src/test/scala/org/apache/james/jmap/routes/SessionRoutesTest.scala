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

package org.apache.james.jmap.routes

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
import org.apache.james.jmap.core.JmapRfc8621Configuration.URL_PREFIX_DEFAULT
import org.apache.james.jmap.core.UuidState.INSTANCE
import org.apache.james.jmap.core.{DefaultCapabilities, JmapRfc8621Configuration}
import org.apache.james.jmap.http.Authenticator
import org.apache.james.jmap.routes.SessionRoutesTest.{BOB, TEST_CONFIGURATION}
import org.apache.james.jmap.{JMAPConfiguration, JMAPRoutesHandler, JMAPServer, Version, VersionParser}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.user.api.DelegationStore
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

    val mockDelegationStore = mock(classOf[DelegationStore])
    when(mockDelegationStore.delegatedUsers(any()))
      .thenReturn(Mono.empty())

    val sessionRoutes = new SessionRoutes(
      sessionSupplier = new SessionSupplier(DefaultCapabilities.supported(JmapRfc8621Configuration.LOCALHOST_CONFIGURATION)),
      delegationStore = mockDelegationStore,
      authenticator = mockedAuthFilter,
      jmapRfc8621Configuration = JmapRfc8621Configuration.LOCALHOST_CONFIGURATION)
    jmapServer = new JMAPServer(
      TEST_CONFIGURATION,
      Set(new JMAPRoutesHandler(Version.RFC8621, sessionRoutes)).asJava,
      new VersionParser(Set(Version.RFC8621, Version.DRAFT).asJava, JMAPConfiguration.DEFAULT))
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
    .when()
      .redirects().follow(false)
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
    val downloadPath: String = "download/{accountId}/{blobId}?type={type}&name={name}"
    val expectedJson = s"""{
                         |  "capabilities" : {
                         |    "urn:ietf:params:jmap:submission": {
                         |      "maxDelayedSend": 0,
                         |      "submissionExtensions": {}
                         |    },
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
                         |      "emailQuerySortOptions" : ["receivedAt", "sentAt", "size", "from", "to", "subject"],
                         |      "mayCreateTopLevelMailbox" : true
                         |    },
                         |    "urn:ietf:params:jmap:websocket": {
                         |      "supportsPush": true,
                         |      "url": "ws://localhost/jmap/ws"
                         |    },
                         |    "urn:apache:james:params:jmap:mail:identity:sortorder": {},
                         |    "urn:apache:james:params:jmap:delegation": {},
                         |    "urn:apache:james:params:jmap:mail:quota": {},
                         |    "urn:ietf:params:jmap:quota": {},
                         |    "urn:apache:james:params:jmap:mail:shares": {},
                         |    "urn:ietf:params:jmap:vacationresponse":{}
                         |  },
                         |  "accounts" : {
                         |    "0fe275bf13ff761407c17f64b1dfae2f4b3186feea223d7267b79f873a105401" : {
                         |      "name" : "bob@james.org",
                         |      "isPersonal" : true,
                         |      "isReadOnly" : false,
                         |      "accountCapabilities" : {
                         |        "urn:ietf:params:jmap:submission": {
                         |          "maxDelayedSend": 0,
                         |          "submissionExtensions": {}
                         |        },
                         |        "urn:ietf:params:jmap:websocket": {
                         |            "supportsPush": true,
                         |            "url": "ws://localhost/jmap/ws"
                         |        },
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
                         |          "emailQuerySortOptions" : ["receivedAt", "sentAt", "size", "from", "to", "subject"],
                         |          "mayCreateTopLevelMailbox" : true
                         |        },
                         |        "urn:apache:james:params:jmap:mail:quota": {},
                         |        "urn:ietf:params:jmap:quota": {},
                         |        "urn:apache:james:params:jmap:mail:identity:sortorder": {},
                         |        "urn:apache:james:params:jmap:delegation": {},
                         |        "urn:apache:james:params:jmap:mail:shares": {},
                         |        "urn:ietf:params:jmap:vacationresponse":{}
                         |      }
                         |    }
                         |  },
                         |  "primaryAccounts" : {
                         |    "urn:ietf:params:jmap:submission": "0fe275bf13ff761407c17f64b1dfae2f4b3186feea223d7267b79f873a105401",
                         |    "urn:ietf:params:jmap:websocket": "0fe275bf13ff761407c17f64b1dfae2f4b3186feea223d7267b79f873a105401",
                         |    "urn:ietf:params:jmap:core" : "0fe275bf13ff761407c17f64b1dfae2f4b3186feea223d7267b79f873a105401",
                         |    "urn:ietf:params:jmap:mail" : "0fe275bf13ff761407c17f64b1dfae2f4b3186feea223d7267b79f873a105401",
                         |    "urn:apache:james:params:jmap:mail:quota": "0fe275bf13ff761407c17f64b1dfae2f4b3186feea223d7267b79f873a105401",
                         |    "urn:ietf:params:jmap:quota": "0fe275bf13ff761407c17f64b1dfae2f4b3186feea223d7267b79f873a105401",
                         |    "urn:apache:james:params:jmap:mail:identity:sortorder": "0fe275bf13ff761407c17f64b1dfae2f4b3186feea223d7267b79f873a105401",
                         |    "urn:apache:james:params:jmap:delegation": "0fe275bf13ff761407c17f64b1dfae2f4b3186feea223d7267b79f873a105401",
                         |    "urn:apache:james:params:jmap:mail:shares": "0fe275bf13ff761407c17f64b1dfae2f4b3186feea223d7267b79f873a105401",
                         |    "urn:ietf:params:jmap:vacationresponse": "0fe275bf13ff761407c17f64b1dfae2f4b3186feea223d7267b79f873a105401"
                         |  },
                         |  "username" : "bob@james.org",
                         |  "apiUrl" : "$URL_PREFIX_DEFAULT/jmap",
                         |  "downloadUrl" : "$URL_PREFIX_DEFAULT/$downloadPath",
                         |  "uploadUrl" : "$URL_PREFIX_DEFAULT/upload/{accountId}",
                         |  "eventSourceUrl" : "$URL_PREFIX_DEFAULT/eventSource?types={types}&closeAfter={closeafter}&ping={ping}",
                         |  "state" : "${INSTANCE.value}"
                         |}""".stripMargin

    assertThatJson(sessionJson).isEqualTo(expectedJson)
  }
}
