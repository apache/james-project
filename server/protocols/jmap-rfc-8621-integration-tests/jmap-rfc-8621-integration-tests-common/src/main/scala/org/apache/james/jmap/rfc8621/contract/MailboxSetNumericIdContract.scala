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

import io.restassured.RestAssured.`given`
import io.restassured.http.ContentType.JSON
import io.restassured.response.{ExtractableResponse, Response}
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.rfc8621.contract.Fixture.ACCEPT_RFC8621_VERSION_HEADER
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.modules.MailboxProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.Json

trait MailboxSetNumericIdContract extends MailboxSetMethodContract {
  private def execute(request: String): ExtractableResponse[Response] =
    `given`
      .header("Accept", ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()

  private def responseArgumentsPath(callId: String): String =
    s"methodResponses.find { it[2] == '$callId' }[1]"

  @Test
  def updateShouldApplyBothPatchesForTokensThatParseToTheSameMailbox(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[MailboxProbeImpl])
    val id = probe.createMailbox(MailboxPath.forUser(bobUsername, "original")).serialize()
    val alias = s"0$id"
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/get", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "ids": [${Json.toJson(id)}],
      |      "properties": ["id", "name", "isSubscribed"]
      |    }, "getOriginalMailbox"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "update": {
      |        ${Json.toJson(id)}: {"name": "renamed"},
      |        ${Json.toJson(alias)}: {"isSubscribed": true}
      |      }
      |    }, "updateAliases"],
      |    ["Mailbox/get", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "ids": [${Json.toJson(id)}],
      |      "properties": ["id", "name", "isSubscribed"]
      |    }, "getUpdatedMailbox"]
      |  ]
      |}""".stripMargin

    val response = execute(request)

    val originalMailbox = response.path[Object](s"${responseArgumentsPath("getOriginalMailbox")}.list")
    assertThatJson(originalMailbox)
      .isEqualTo(s"""[{"id": ${Json.toJson(id)}, "name": "original", "isSubscribed": false}]""")
    val update = response.path[Object](s"${responseArgumentsPath("updateAliases")}.subMap(['updated', 'notUpdated'])")
    assertThatJson(update)
      .isEqualTo(s"""{"updated": {${Json.toJson(id)}: {}}}""")
    val updatedMailbox = response.path[Object](s"${responseArgumentsPath("getUpdatedMailbox")}.list")
    assertThatJson(updatedMailbox)
      .isEqualTo(s"""[{"id": ${Json.toJson(id)}, "name": "renamed", "isSubscribed": true}]""")
  }

  @Test
  def updateShouldKeepLeadingZeroInTheErrorKeyForARejectedSortOrderPatch(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[MailboxProbeImpl])
    val id = probe.createMailbox(MailboxPath.forUser(bobUsername, "original")).serialize()
    val alias = s"0$id"
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "update": {
      |        ${Json.toJson(id)}: {"name": "renamed"},
      |        ${Json.toJson(alias)}: {"sortOrder": 123}
      |      }
      |    }, "updateAliases"]
      |  ]
      |}""".stripMargin

    val response = execute(request)

    val update = response.path[Object](s"${responseArgumentsPath("updateAliases")}.subMap(['updated', 'notUpdated'])")
    assertThatJson(update).isEqualTo(s"""{
      |  "updated": {${Json.toJson(id)}: "$${json-unit.ignore}"},
      |  "notUpdated": {
      |    ${Json.toJson(alias)}: {
      |      "type": "invalidArguments",
      |      "description": "Can not modify server-set properties",
      |      "properties": ["sortOrder"]
      |    }
      |  }
      |}""".stripMargin)
    assertThat(probe.getMailboxId("#private", bobUsername.asString(), "renamed").serialize()).isEqualTo(id)
  }

  @Test
  def destroyShouldKeepDistinctErrorKeysForTokensThatParseToTheSameMailbox(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[MailboxProbeImpl])
    val id = probe.createMailbox(MailboxPath.forUser(bobUsername, "INBOX")).serialize()
    val alias = s"0$id"
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "destroy": [${Json.toJson(id)}, ${Json.toJson(alias)}]
      |    }, "destroyAliases"]
      |  ]
      |}""".stripMargin

    val response = execute(request)

    val destroy = response.path[Object](s"${responseArgumentsPath("destroyAliases")}.subMap(['destroyed', 'notDestroyed'])")
    assertThatJson(destroy).isEqualTo(s"""{
      |  "notDestroyed": {
      |    ${Json.toJson(id)}: {"type": "invalidArguments", "description": "System mailboxes cannot be destroyed"},
      |    ${Json.toJson(alias)}: {"type": "invalidArguments", "description": "System mailboxes cannot be destroyed"}
      |  }
      |}""".stripMargin)
    assertThat(probe.getMailboxId("#private", bobUsername.asString(), "INBOX").serialize()).isEqualTo(id)
  }
}
