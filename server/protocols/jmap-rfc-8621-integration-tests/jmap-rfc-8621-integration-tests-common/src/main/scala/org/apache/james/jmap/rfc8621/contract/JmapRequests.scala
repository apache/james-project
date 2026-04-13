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

import java.nio.charset.StandardCharsets

import com.google.common.hash.Hashing
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.`given`
import io.restassured.http.ContentType.JSON
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.core.Username
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, BOB}
import org.apache.james.mailbox.model.MessageId

object JmapRequests {
  private def accountId(username: Username): String =
    Hashing.sha256().hashString(username.asString(), StandardCharsets.UTF_8).toString

  def renameMailbox(mailboxId: String, name: String, username: Username = BOB): Unit = {
    val request =
      s"""
         |{
         |  "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |  "methodCalls": [[
         |    "Mailbox/set", {
         |      "accountId": "${accountId(username)}",
         |      "update": {
         |        "$mailboxId": {
         |          "name": "$name"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}
         |""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
  }

  def destroyMailbox(mailboxId: String, username: Username = BOB): Unit = {
    val request =
      s"""
         |{
         |  "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |  "methodCalls": [[
         |    "Mailbox/set", {
         |      "accountId": "${accountId(username)}",
         |      "destroy": ["$mailboxId"]
         |    }, "c1"]
         |  ]
         |}
         |""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
  }

  def markEmailAsSeen(messageId: MessageId, username: Username = BOB): Unit = {
    val request = String.format(
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "${accountId(username)}",
         |      "update": {
         |        "${messageId.serialize}": {
         |          "keywords": {
         |             "$$seen": true
         |          }
         |        }
         |      }
         |    }, "c1"]]
         |}""".stripMargin)

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
  }

  def markEmailAsNotSeen(messageId: MessageId, username: Username = BOB): Unit = {
    val request = String.format(
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "${accountId(username)}",
         |      "update": {
         |        "${messageId.serialize}": {
         |          "keywords/$$seen": null
         |        }
         |      }
         |    }, "c1"]]
         |}""".stripMargin)

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
  }

  def destroyEmail(messageId: MessageId, username: Username = BOB): Unit = {
    val request = String.format(
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [
         |    ["Email/set", {
         |      "accountId": "${accountId(username)}",
         |      "destroy": ["${messageId.serialize}"]
         |    }, "c1"]]
         |}""".stripMargin)

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
  }

  def subscribe(mailboxId: String, username: Username = BOB): Unit = {
    val request =
      s"""
         |{
         |  "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |  "methodCalls": [[
         |    "Mailbox/set", {
         |      "accountId": "${accountId(username)}",
         |      "update": {
         |        "$mailboxId": {
         |          "isSubscribed": true
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}
         |""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
  }

  def unSubscribe(mailboxId: String, username: Username = BOB): Unit = {
    val request =
      s"""
         |{
         |  "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
         |  "methodCalls": [[
         |    "Mailbox/set", {
         |      "accountId": "${accountId(username)}",
         |      "update": {
         |        "$mailboxId": {
         |          "isSubscribed": false
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}
         |""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
  }
}
