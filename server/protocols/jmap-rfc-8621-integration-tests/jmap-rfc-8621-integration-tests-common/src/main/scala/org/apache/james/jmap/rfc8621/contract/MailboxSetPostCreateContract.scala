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

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.`given`
import io.restassured.http.ContentType.JSON
import io.restassured.response.{ExtractableResponse, Response, ValidatableResponse}
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.ConfigurationWhen.{path, paths, `then`}
import net.javacrumbs.jsonunit.core.Option.IGNORING_EXTRA_FIELDS
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.rfc8621.contract.Fixture.ACCEPT_RFC8621_VERSION_HEADER
import org.apache.james.mailbox.model.MailboxACL.Right
import org.apache.james.mailbox.model.{MailboxACL, MailboxPath}
import org.apache.james.modules.{ACLProbeImpl, MailboxProbeImpl}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import play.api.libs.json.Json

trait MailboxSetPostCreateContract {
  def bobUsername: Username
  def bobAccountId: String
  def andreUsername: Username

  private def execute(request: String): ValidatableResponse =
    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)

  private def responseArgumentsPath(callId: String): String =
    s"methodResponses.find { it[2] == '$callId' }[1]"

  private def setResult(response: ExtractableResponse[Response], callId: String): java.util.Map[String, Object] =
    response.path(s"${responseArgumentsPath(callId)}.subMap(['created', 'notCreated', 'updated', 'notUpdated', 'destroyed', 'notDestroyed'])")

  private def mailboxId(server: GuiceJamesServer, path: String): String =
    server.getProbe(classOf[MailboxProbeImpl]).getMailboxId("#private", bobUsername.asString(), path).serialize()

  @ParameterizedTest
  @ValueSource(ints = Array(1, 255))
  def postCreateShouldUpdateNewMailbox(length: Int, server: GuiceJamesServer): Unit = {
    val creationId = "x" * length
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"$creationId": {"name": "before"}},
      |      "update": {"#$creationId": {"name": "after"}}
      |    }, "createAndUpdate"]
      |  ]
      |}""".stripMargin

    val response = execute(request).extract()
    val creation = setResult(response, "createAndUpdate")
    val createdId = mailboxId(server, "after")

    assertThatJson(creation)
      .when(path(s"created.$creationId"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {
        |    ${Json.toJson(creationId)}: {"id": ${Json.toJson(createdId)}}
        |  },
        |  "updated": {
        |    ${Json.toJson(createdId)}: "$${json-unit.ignore}"
        |  }
        |}""".stripMargin)

    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString()))
      .doesNotContain("before")
  }

  @ParameterizedTest
  @ValueSource(ints = Array(1, 255))
  def postCreateShouldDestroyNewMailbox(length: Int, server: GuiceJamesServer): Unit = {
    val creationId = "x" * length
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"$creationId": {"name": "before"}},
      |      "destroy": ["#$creationId"]
      |    }, "createAndDestroy"]
      |  ]
      |}""".stripMargin

    val response = execute(request).extract()
    val creation = setResult(response, "createAndDestroy")
    val createdId = response.path[String](s"${responseArgumentsPath("createAndDestroy")}.created.'$creationId'.id")

    assertThatJson(creation)
      .when(path(s"created.$creationId"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {
        |    ${Json.toJson(creationId)}: {"id": ${Json.toJson(createdId)}}
        |  },
        |  "destroyed": [${Json.toJson(createdId)}]
        |}""".stripMargin)

    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString()))
      .doesNotContain("before")
  }

  @Test
  def postCreateShouldResolveParentAndRealTarget(server: GuiceJamesServer): Unit = {
    val childId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(bobUsername, "child")).serialize()
    val parentCreationId = "p" * 255
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"$parentCreationId": {"name": "parent"}},
      |      "update": {${Json.toJson(childId)}: {"parentId": "#$parentCreationId", "name": "renamed"}}
      |    }, "createParentAndMoveChild"]
      |  ]
      |}""".stripMargin

    val response = execute(request).extract()
    val move = setResult(response, "createParentAndMoveChild")

    assertThatJson(move)
      .isEqualTo(s"""{
        |  "created": {
        |    ${Json.toJson(parentCreationId)}: "$${json-unit.ignore}"
        |  },
        |  "updated": {
        |    ${Json.toJson(childId)}: "$${json-unit.ignore}"
        |  }
        |}""".stripMargin)

    assertThat(mailboxId(server, "parent.renamed")).isEqualTo(childId)
  }

  @Test
  def postCreateShouldUseTheLatestSuccessfulMapping(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"child": {"name": "old"}, "parent": {"name": "oldParent"}}
      |    }, "createOriginals"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"child": {"name": "new"}, "parent": {"name": "newParent"}},
      |      "update": {"#child": {"name": "renamed", "parentId": "#parent"}}
      |    }, "replaceAndUpdate"],
      |    ["Mailbox/get", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "ids": ["#child"], "properties": ["id", "parentId"]
      |    }, "getReplacement"],
      |    ["Mailbox/set", {"accountId": ${Json.toJson(bobAccountId)}, "destroy": ["#child"]}, "destroyReplacement"]
      |  ]
      |}""".stripMargin

    val response = execute(request).extract()
    val original = setResult(response, "createOriginals")
    val replacement = setResult(response, "replaceAndUpdate")
    val deletion = setResult(response, "destroyReplacement")
    val fetched = response.path[java.util.List[Object]](s"${responseArgumentsPath("getReplacement")}.list")
    val newId = response.path[String](s"${responseArgumentsPath("replaceAndUpdate")}.created.child.id")
    val newParentId = response.path[String](s"${responseArgumentsPath("replaceAndUpdate")}.created.parent.id")
    val oldId = mailboxId(server, "old")

    assertThatJson(original)
      .when(path("created.child"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {
        |    "child": {"id": ${Json.toJson(oldId)}},
        |    "parent": "$${json-unit.ignore}"
        |  }
        |}""".stripMargin)

    assertThatJson(replacement)
      .when(paths("created.child", "created.parent"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {
        |    "child": {"id": ${Json.toJson(newId)}},
        |    "parent": {"id": ${Json.toJson(newParentId)}}
        |  },
        |  "updated": {
        |    ${Json.toJson(newId)}: "$${json-unit.ignore}"
        |  }
        |}""".stripMargin)

    assertThatJson(fetched)
      .isEqualTo(s"""[
        |  {
        |    "id": ${Json.toJson(newId)},
        |    "parentId": ${Json.toJson(newParentId)}
        |  }
        |]""".stripMargin)

    assertThatJson(deletion)
      .isEqualTo(s"""{
        |  "destroyed": [${Json.toJson(newId)}]
        |}""".stripMargin)

    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString()))
      .doesNotContain("new", "newParent.renamed", "oldParent.renamed")
  }

  @Test
  def postCreateShouldDestroyTheLatestReusedMapping(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)}, "create": {"reusedMailbox": {"name": "old"}}
      |    }, "createOriginal"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"reusedMailbox": {"name": "new"}},
      |      "destroy": ["#reusedMailbox"]
      |    }, "replaceAndDestroy"]
      |  ]
      |}""".stripMargin

    val response = execute(request).extract()
    val original = setResult(response, "createOriginal")
    val replacement = setResult(response, "replaceAndDestroy")
    val oldId = mailboxId(server, "old")
    val newId = response.path[String](s"${responseArgumentsPath("replaceAndDestroy")}.created.reusedMailbox.id")

    assertThatJson(original)
      .when(path("created.reusedMailbox"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {
        |    "reusedMailbox": {"id": ${Json.toJson(oldId)}}
        |  }
        |}""".stripMargin)

    assertThatJson(replacement)
      .when(path("created.reusedMailbox"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {
        |    "reusedMailbox": {"id": ${Json.toJson(newId)}}
        |  },
        |  "destroyed": [${Json.toJson(newId)}]
        |}""".stripMargin)

    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString()))
      .doesNotContain("new")
  }

  @Test
  def postCreateShouldKeepThePreviousSuccessfulMappingWhenReplacementFails(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)}, "create": {"reusedMailbox": {"name": "old"}}
      |    }, "createOriginal"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)}, "create": {"reusedMailbox": {"name": "bad.name"}},
      |      "update": {"#reusedMailbox": {"name": "renamed"}}
      |    }, "failReplacementAndUpdate"]
      |  ]
      |}""".stripMargin

    val response = execute(request).extract()
    val original = setResult(response, "createOriginal")
    val replacement = setResult(response, "failReplacementAndUpdate")
    val oldId = mailboxId(server, "renamed")

    assertThatJson(original)
      .when(path("created.reusedMailbox"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {
        |    "reusedMailbox": {"id": ${Json.toJson(oldId)}}
        |  }
        |}""".stripMargin)

    assertThatJson(replacement)
      .when(path("notCreated.reusedMailbox"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "notCreated": {
        |    "reusedMailbox": {"type": "invalidArguments"}
        |  },
        |  "updated": {
        |    ${Json.toJson(oldId)}: "$${json-unit.ignore}"
        |  }
        |}""".stripMargin)
  }

  @Test
  def postCreateShouldIsolateMissingUpdateTargets(server: GuiceJamesServer): Unit = {
    val unknownId = "m" * 255
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"failedMailbox": {"name": "bad.name"}, "independentMailbox": {"name": "other"}},
      |      "update": {
      |        "#failedMailbox": {"name": "badUpdate"},
      |        "#$unknownId": {"name": "missing"},
      |        "#independentMailbox": {"name": "independent"}
      |      }
      |    }, "updateTargets"]
      |  ]
      |}""".stripMargin

    val response = execute(request).extract()
    val update = setResult(response, "updateTargets")
    val independentId = mailboxId(server, "independent")

    assertThatJson(update)
      .when(paths("created.independentMailbox", "notCreated.failedMailbox"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {
        |    "independentMailbox": {"id": ${Json.toJson(independentId)}}
        |  },
        |  "updated": {
        |    ${Json.toJson(independentId)}: "$${json-unit.ignore}"
        |  },
        |  "notCreated": {
        |    "failedMailbox": {"type": "invalidArguments"}
        |  },
        |  "notUpdated": {
        |    "#failedMailbox": {
        |      "type": "invalidArguments",
        |      "description": "Unable to resolve mailbox reference '#failedMailbox': no successful creation is recorded for creation ID 'failedMailbox'"
        |    },
        |    "#$unknownId": {
        |      "type": "invalidArguments",
        |      "description": "Unable to resolve mailbox reference '#$unknownId': no successful creation is recorded for creation ID '$unknownId'"
        |    }
        |  }
        |}""".stripMargin)
  }

  @Test
  def postCreateShouldIsolateMissingDestroyTargets(server: GuiceJamesServer): Unit = {
    val unknownId = "m" * 255
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"failedMailbox": {"name": "bad.name"}, "independentMailbox": {"name": "other"}},
      |      "destroy": ["#failedMailbox", "#$unknownId", "#independentMailbox"]
      |    }, "destroyTargets"]
      |  ]
      |}""".stripMargin

    val response = execute(request).extract()
    val deletion = setResult(response, "destroyTargets")
    val independentId = response.path[String](s"${responseArgumentsPath("destroyTargets")}.created.independentMailbox.id")

    assertThatJson(deletion)
      .when(paths("created.independentMailbox", "notCreated.failedMailbox"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {
        |    "independentMailbox": {"id": ${Json.toJson(independentId)}}
        |  },
        |  "notCreated": {
        |    "failedMailbox": {"type": "invalidArguments"}
        |  },
        |  "destroyed": [${Json.toJson(independentId)}],
        |  "notDestroyed": {
        |    "#failedMailbox": {
        |      "type": "invalidArguments",
        |      "description": "Unable to resolve mailbox reference '#failedMailbox': no successful creation is recorded for creation ID 'failedMailbox'"
        |    },
        |    "#$unknownId": {
        |      "type": "invalidArguments",
        |      "description": "Unable to resolve mailbox reference '#$unknownId': no successful creation is recorded for creation ID '$unknownId'"
        |    }
        |  }
        |}""".stripMargin)

    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString()))
      .doesNotContain("other")
  }

  @Test
  def postCreateShouldRejectInvalidParentWithoutPartialUpdate(server: GuiceJamesServer): Unit = {
    val unknownId = "m" * 255
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"child": {"name": "good"}, "independentMailbox": {"name": "other"}},
      |      "update": {
      |        "#child": {"name": "partial", "parentId": "#$unknownId", "isSubscribed": false},
      |        "#independentMailbox": {"name": "independent"}
      |      }
      |    }, "updateWithMissingParent"],
      |    ["Mailbox/get", {
      |      "accountId": ${Json.toJson(bobAccountId)}, "ids": ["#child"], "properties": ["id", "name", "isSubscribed"]
      |    }, "getUnchangedMailbox"]
      |  ]
      |}""".stripMargin

    val response = execute(request).extract()
    val update = setResult(response, "updateWithMissingParent")
    val fetched = response.path[java.util.List[Object]](s"${responseArgumentsPath("getUnchangedMailbox")}.list")
    val childId = mailboxId(server, "good")
    val independentId = mailboxId(server, "independent")

    assertThatJson(update)
      .when(paths("created.independentMailbox", "notUpdated.#child"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {
        |    "child": "$${json-unit.ignore}",
        |    "independentMailbox": {"id": ${Json.toJson(independentId)}}
        |  },
        |  "updated": {
        |    ${Json.toJson(independentId)}: "$${json-unit.ignore}"
        |  },
        |  "notUpdated": {
        |    "#child": {
        |      "type": "invalidArguments",
        |      "properties": ["parentId"]
        |    }
        |  }
        |}""".stripMargin)

    assertThatJson(fetched)
      .isEqualTo(s"""[
        |  {
        |    "id": ${Json.toJson(childId)},
        |    "name": "good",
        |    "isSubscribed": true
        |  }
        |]""".stripMargin)

    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString()))
      .doesNotContain("partial")
  }

  @Test
  def postCreateShouldUpdateDistinctCreationReferenceAndRealIdTargets(server: GuiceJamesServer): Unit = {
    val originalId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(bobUsername, "original")).serialize()
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)}, "create": {"newMailbox": {"name": "unused"}},
      |      "update": {"#newMailbox": {"name": "alias"}, ${Json.toJson(originalId)}: {"name": "real"}}
      |    }, "createAndUpdateDistinctTargets"]
      |  ]
      |}""".stripMargin

    val response = execute(request).extract()
    val update = setResult(response, "createAndUpdateDistinctTargets")
    val newId = mailboxId(server, "alias")

    assertThatJson(update)
      .when(path("created.newMailbox"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {
        |    "newMailbox": {"id": ${Json.toJson(newId)}}
        |  },
        |  "updated": {
        |    ${Json.toJson(originalId)}: "$${json-unit.ignore}",
        |    ${Json.toJson(newId)}: "$${json-unit.ignore}"
        |  }
        |}""".stripMargin)

    assertThat(mailboxId(server, "real")).isEqualTo(originalId)
  }

  @Test
  def postCreateShouldResolveCreationReferencesReturnedByAResultReference(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Core/echo", {"patches": {"#child": {"parentId": "#parent", "name": "after"}}}, "echoPatches"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"child": {"name": "before"}, "parent": {"name": "parent"}},
      |      "#update": {"resultOf": "echoPatches", "name": "Core/echo", "path": "/patches"}
      |    }, "createAndUpdateFromResult"]
      |  ]
      |}""".stripMargin

    val response = execute(request).extract()
    val update = setResult(response, "createAndUpdateFromResult")
    val updatedId = mailboxId(server, "parent.after")

    assertThatJson(update)
      .when(path("created.child"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {
        |    "child": {"id": ${Json.toJson(updatedId)}},
        |    "parent": "$${json-unit.ignore}"
        |  },
        |  "updated": {
        |    ${Json.toJson(updatedId)}: "$${json-unit.ignore}"
        |  }
        |}""".stripMargin)
  }

  @Test
  def postCreateShouldUpdateThenDestroyTheSameTarget(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)}, "create": {"newMailbox": {"name": "before"}},
      |      "update": {"#newMailbox": {"name": "after"}}, "destroy": ["#newMailbox", "#newMailbox"]
      |    }, "createUpdateAndDestroy"]
      |  ]
      |}""".stripMargin

    val response = execute(request).extract()
    val operation = setResult(response, "createUpdateAndDestroy")
    val createdId = response.path[String](s"${responseArgumentsPath("createUpdateAndDestroy")}.created.newMailbox.id")

    assertThatJson(operation)
      .when(path("created.newMailbox"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {
        |    "newMailbox": {"id": ${Json.toJson(createdId)}}
        |  },
        |  "updated": {
        |    ${Json.toJson(createdId)}: "$${json-unit.ignore}"
        |  },
        |  "destroyed": [${Json.toJson(createdId)}]
        |}""".stripMargin)

    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString()))
      .doesNotContain("before", "after")
  }

  @Test
  def postCreateShouldUseTheLatestMappingForUpdateTargetsFromAResultReference(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Core/echo", {
      |      "patches": {"#child": {"name": "after", "parentId": "#parent"}}
      |    }, "echoPatches"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"child": {"name": "old"}, "parent": {"name": "oldParent"}}
      |    }, "createOriginals"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"child": {"name": "new"}, "parent": {"name": "newParent"}},
      |      "#update": {"resultOf": "echoPatches", "name": "Core/echo", "path": "/patches"}
      |    }, "replaceAndUpdateFromResult"]
      |  ]
      |}""".stripMargin

    val response = execute(request).extract()
    val original = setResult(response, "createOriginals")
    val replacement = setResult(response, "replaceAndUpdateFromResult")
    val oldId = mailboxId(server, "old")
    val updatedId = mailboxId(server, "newParent.after")

    assertThatJson(original)
      .when(path("created.child"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {
        |    "child": {"id": ${Json.toJson(oldId)}},
        |    "parent": "$${json-unit.ignore}"
        |  }
        |}""".stripMargin)

    assertThatJson(replacement)
      .when(path("created.child"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {
        |    "child": {"id": ${Json.toJson(updatedId)}},
        |    "parent": "$${json-unit.ignore}"
        |  },
        |  "updated": {
        |    ${Json.toJson(updatedId)}: "$${json-unit.ignore}"
        |  }
        |}""".stripMargin)
  }

  @Test
  def postCreateShouldUseTheLatestMappingForDestroyTargetsFromAResultReference(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Core/echo", {"targets": ["#reusedMailbox"]}, "echoTargets"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)}, "create": {"reusedMailbox": {"name": "old"}}
      |    }, "createOriginal"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)}, "create": {"reusedMailbox": {"name": "new"}},
      |      "#destroy": {"resultOf": "echoTargets", "name": "Core/echo", "path": "/targets"}
      |    }, "replaceAndDestroyFromResult"]
      |  ]
      |}""".stripMargin

    val response = execute(request).extract()
    val original = setResult(response, "createOriginal")
    val replacement = setResult(response, "replaceAndDestroyFromResult")
    val oldId = mailboxId(server, "old")
    val newId = response.path[String](s"${responseArgumentsPath("replaceAndDestroyFromResult")}.created.reusedMailbox.id")

    assertThatJson(original)
      .when(path("created.reusedMailbox"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {
        |    "reusedMailbox": {"id": ${Json.toJson(oldId)}}
        |  }
        |}""".stripMargin)

    assertThatJson(replacement)
      .when(path("created.reusedMailbox"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {
        |    "reusedMailbox": {"id": ${Json.toJson(newId)}}
        |  },
        |  "destroyed": [${Json.toJson(newId)}]
        |}""".stripMargin)

    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString()))
      .doesNotContain("new")
  }

  @ParameterizedTest
  @ValueSource(strings = Array("#", "#bad/reference"))
  def postCreateShouldValidateUpdateTargetSyntaxBeforeCreating(target: String, server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"mustNotBeCreated": {"name": "mustNotBeCreated"}},
      |      "update": {${Json.toJson(target)}: {"name": "invalid"}}
      |    }, "invalidTarget"]
      |  ]
      |}""".stripMargin

    val error = execute(request).extract()
      .path[java.util.Map[String, Object]](responseArgumentsPath("invalidTarget"))

    assertThatJson(error)
      .withOptions(IGNORING_EXTRA_FIELDS)
      .isEqualTo("""{
        |  "type": "invalidArguments"
        |}""".stripMargin)

    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString()))
      .doesNotContain("mustNotBeCreated")
  }

  @ParameterizedTest
  @ValueSource(strings = Array("#", "#bad/reference"))
  def postCreateShouldValidateDestroyTargetSyntaxBeforeCreating(target: String, server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"mustNotBeCreated": {"name": "mustNotBeCreated"}},
      |      "destroy": [${Json.toJson(target)}]
      |    }, "invalidTarget"]
      |  ]
      |}""".stripMargin

    val error = execute(request).extract()
      .path[java.util.Map[String, Object]](responseArgumentsPath("invalidTarget"))

    assertThatJson(error)
      .withOptions(IGNORING_EXTRA_FIELDS)
      .isEqualTo("""{
        |  "type": "invalidArguments"
        |}""".stripMargin)

    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString()))
      .doesNotContain("mustNotBeCreated")
  }

  @ParameterizedTest
  @ValueSource(booleans = Array(false, true))
  def postCreateShouldKeepSharedMailboxUpdateChecks(supportShares: Boolean, server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[MailboxProbeImpl])
    val parentPath = MailboxPath.forUser(andreUsername, "shared")
    val parent = probe.createMailbox(parentPath).serialize()
    server.getProbe(classOf[ACLProbeImpl]).replaceRights(parentPath, bobUsername.asString(),
      new MailboxACL.Rfc4314Rights(Right.Lookup, Right.CreateMailbox))
    val sharesCapability = if (supportShares) ", \"urn:apache:james:params:jmap:mail:shares\"" else ""
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"$sharesCapability],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {
      |        "sharedChild": {"name": "child", "parentId": ${Json.toJson(parent)}}
      |      },
      |      "update": {"#sharedChild": {"name": "renamed"}}
      |    }, "createAndUpdateSharedMailbox"]
      |  ]
      |}""".stripMargin

    val response = setResult(execute(request).extract(), "createAndUpdateSharedMailbox")

    if (supportShares) {
      assertThatJson(response)
        .when(path("notUpdated.#sharedChild"), `then`(IGNORING_EXTRA_FIELDS))
        .isEqualTo("""{
          |  "created": {
          |    "sharedChild": "${json-unit.ignore}"
          |  },
          |  "notUpdated": {
          |    "#sharedChild": {"type": "forbidden"}
          |  }
          |}""".stripMargin)
      val mailboxPaths = probe.listUserMailboxes(andreUsername.asString())
      assertThat(mailboxPaths).contains("shared.child")
      assertThat(mailboxPaths).doesNotContain("shared.renamed")
    } else {
      assertThatJson(response)
        .when(path("notUpdated.#sharedChild"), `then`(IGNORING_EXTRA_FIELDS))
        .isEqualTo("""{
          |  "notCreated": {
          |    "sharedChild": "${json-unit.ignore}"
          |  },
          |  "notUpdated": {
          |    "#sharedChild": {"type": "invalidArguments"}
          |  }
          |}""".stripMargin)
      assertThat(probe.listUserMailboxes(andreUsername.asString())).doesNotContain("shared.child")
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = Array(false, true))
  def postCreateShouldKeepSharedMailboxDestroyChecks(supportShares: Boolean, server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[MailboxProbeImpl])
    val parentPath = MailboxPath.forUser(andreUsername, "shared")
    val parent = probe.createMailbox(parentPath).serialize()
    server.getProbe(classOf[ACLProbeImpl]).replaceRights(parentPath, bobUsername.asString(),
      new MailboxACL.Rfc4314Rights(Right.Lookup, Right.CreateMailbox))
    val sharesCapability = if (supportShares) ", \"urn:apache:james:params:jmap:mail:shares\"" else ""
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"$sharesCapability],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {
      |        "sharedChild": {"name": "child", "parentId": ${Json.toJson(parent)}}
      |      },
      |      "destroy": ["#sharedChild"]
      |    }, "createAndDestroySharedMailbox"]
      |  ]
      |}""".stripMargin

    val response = setResult(execute(request).extract(), "createAndDestroySharedMailbox")

    if (supportShares) {
      assertThatJson(response)
        .when(path("notDestroyed.#sharedChild"), `then`(IGNORING_EXTRA_FIELDS))
        .isEqualTo("""{
          |  "created": {
          |    "sharedChild": "${json-unit.ignore}"
          |  },
          |  "notDestroyed": {
          |    "#sharedChild": {"type": "invalidArguments"}
          |  }
          |}""".stripMargin)
      assertThat(probe.listUserMailboxes(andreUsername.asString())).contains("shared.child")
    } else {
      assertThatJson(response)
        .when(path("notDestroyed.#sharedChild"), `then`(IGNORING_EXTRA_FIELDS))
        .isEqualTo("""{
          |  "notCreated": {
          |    "sharedChild": "${json-unit.ignore}"
          |  },
          |  "notDestroyed": {
          |    "#sharedChild": {"type": "invalidArguments"}
          |  }
          |}""".stripMargin)
      assertThat(probe.listUserMailboxes(andreUsername.asString())).doesNotContain("shared.child")
    }
  }
}
