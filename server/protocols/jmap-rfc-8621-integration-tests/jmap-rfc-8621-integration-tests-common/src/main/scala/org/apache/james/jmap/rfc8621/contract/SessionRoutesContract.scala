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
package org.apache.james.jmap.rfc8621.contract

import java.nio.charset.StandardCharsets

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured._
import io.restassured.builder.RequestSpecBuilder
import io.restassured.config.EncoderConfig.encoderConfig
import io.restassured.config.RestAssuredConfig.newConfig
import io.restassured.http.ContentType
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.draft.JmapGuiceProbe
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.jmap.rfc8621.contract.SessionRoutesContract.{EXPECTED_BASE_PATH, expected_session_object}
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Tag, Test}

object SessionRoutesContract {
  private val expected_session_object: String = """{
                         |  "capabilities" : {
                         |    "urn:ietf:params:jmap:core" : {
                         |      "maxSizeUpload" : 10000000,
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
                         |    "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6" : {
                         |      "name" : "bob@domain.tld",
                         |      "isPersonal" : true,
                         |      "isReadOnly" : false,
                         |      "accountCapabilities" : {
                         |        "urn:ietf:params:jmap:core" : {
                         |          "maxSizeUpload" : 10000000,
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
                         |    "urn:ietf:params:jmap:core" : "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                         |    "urn:ietf:params:jmap:mail" : "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                         |    "urn:apache:james:params:jmap:mail:quota": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                         |    "urn:apache:james:params:jmap:mail:shares": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                         |    "urn:ietf:params:jmap:vacationresponse": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
                         |  },
                         |  "username" : "bob@domain.tld",
                         |  "apiUrl" : "http://domain.com/jmap",
                         |  "downloadUrl" : "http://domain.com/download/$accountId/$blobId/?type=$type&name=$name",
                         |  "uploadUrl" : "http://domain.com/upload",
                         |  "eventSourceUrl" : "http://domain.com/eventSource",
                         |  "state" : "000001"
                         |}""".stripMargin
  private val EXPECTED_BASE_PATH: String = "/jmap"
}

trait SessionRoutesContract {

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)
      .addUser(ANDRE.asString, ANDRE_PASSWORD)

    val jmapGuiceProbe: JmapGuiceProbe = server.getProbe(classOf[JmapGuiceProbe])
    requestSpecification = new RequestSpecBuilder()
    .setContentType(ContentType.JSON)
    .setAccept(ContentType.JSON)
    .setConfig(newConfig.encoderConfig(encoderConfig.defaultContentCharset(StandardCharsets.UTF_8)))
    .setPort(jmapGuiceProbe
      .getJmapPort
      .getValue)
    .setBasePath(EXPECTED_BASE_PATH)
        .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def getShouldReturnCorrectSession(): Unit = {
    val sessionJson: String = `given`()
      .when()
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .get("/session")
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
      .extract()
        .body()
        .asString()

    assertThatJson(sessionJson).isEqualTo(expected_session_object)
  }
}