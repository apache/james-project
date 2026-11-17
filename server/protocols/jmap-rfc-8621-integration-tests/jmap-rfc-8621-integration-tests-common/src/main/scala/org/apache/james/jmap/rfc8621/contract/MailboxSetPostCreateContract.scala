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

  @Test
  def postCreateShouldDestroyNewMailbox(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"mailboxToDestroy": {"name": "before"}},
      |      "destroy": ["#mailboxToDestroy"]
      |    }, "createAndDestroy"]
      |  ]
      |}""".stripMargin

    val response = execute(request).extract()

    val creation = setResult(response, "createAndDestroy")
    val createdId = response.path[String](s"${responseArgumentsPath("createAndDestroy")}.created.'mailboxToDestroy'.id")

    assertThatJson(creation)
      .when(path("created.mailboxToDestroy"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {
        |    "mailboxToDestroy": {"id": ${Json.toJson(createdId)}}
        |  },
        |  "destroyed": [${Json.toJson(createdId)}]
        |}""".stripMargin)

    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString()))
      .doesNotContain("before")
  }

  @Test
  def postCreateShouldResolveMaximumLengthCreationId(server: GuiceJamesServer): Unit = {
    val creationId = "m" * 255
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

  @Test
  def postCreateShouldResolveParentAndRealTarget(server: GuiceJamesServer): Unit = {
    val childId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(bobUsername, "child")).serialize()
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"parent": {"name": "parent"}},
      |      "update": {${Json.toJson(childId)}: {"parentId": "#parent", "name": "renamed"}}
      |    }, "createParentAndMoveChild"]
      |  ]
      |}""".stripMargin

    val response = execute(request).extract()

    val move = setResult(response, "createParentAndMoveChild")

    assertThatJson(move)
      .isEqualTo(s"""{
        |  "created": {
        |    "parent": "$${json-unit.ignore}"
        |  },
        |  "updated": {
        |    ${Json.toJson(childId)}: "$${json-unit.ignore}"
        |  }
        |}""".stripMargin)

    assertThat(mailboxId(server, "parent.renamed")).isEqualTo(childId)
  }

  @Test
  def postCreateShouldRejectKnownUpdateConflictsInResultReferences(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxToKeepId = probe.createMailbox(MailboxPath.forUser(bobUsername, "keep")).serialize()
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Core/echo", {"renamePatch": {"name": "renamed"}}, "prepareRenamePatch"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"alias": {"name": "original"}}
      |    }, "createOriginal"],
      |    ["Core/echo", {
      |      "patches": {
      |        "##alias": {"resultOf": "prepareRenamePatch", "name": "Core/echo", "path": "/renamePatch"},
      |        "#alias": {"isSubscribed": false}
      |      }
      |    }, "echoPatches"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"untouched": {"name": "untouched"}},
      |      "#update": {"resultOf": "echoPatches", "name": "Core/echo", "path": "/patches"},
      |      "destroy": [${Json.toJson(mailboxToKeepId)}]
      |    }, "updateFromResult"]
      |  ]
      |}""".stripMargin

    val response = execute(request).extract()

    val originalId = response.path[String](s"${responseArgumentsPath("createOriginal")}.created.alias.id")
    val conflict = response.path[Object]("methodResponses.find { it[2] == 'updateFromResult' }")
    assertThatJson(conflict).isEqualTo(s"""[
      |  "error", {
      |    "type": "invalidArguments",
      |    "description": ${Json.toJson(s"Multiple update keys resolve to the same mailbox: '#alias', '$originalId'")}
      |  }, "updateFromResult"
      |]""".stripMargin)

    assertThat(mailboxId(server, "original")).isEqualTo(originalId)
    assertThat(mailboxId(server, "keep")).isEqualTo(mailboxToKeepId)
    assertThat(probe.listUserMailboxes(bobUsername.asString())).doesNotContain("untouched", "renamed")
  }

  @Test
  def postCreateShouldApplyBothUpdatesWhenReplacementSeparatesTheirTargets(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Core/echo", {"aliasPatch": {"name": "renamedReplacement"}}, "preparePatches"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"reusedMailbox": {"name": "old"}}
      |    }, "createOriginal"],
      |    ["Core/echo", {
      |      "patches": {
      |        "##reusedMailbox": {"resultOf": "preparePatches", "name": "Core/echo", "path": "/aliasPatch"},
      |        "#reusedMailbox": {"name": "renamedOriginal"}
      |      }
      |    }, "echoPatches"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"reusedMailbox": {"name": "replacement"}},
      |      "#update": {"resultOf": "echoPatches", "name": "Core/echo", "path": "/patches"}
      |    }, "replaceAndUpdate"]
      |  ]
      |}""".stripMargin

    val response = execute(request).extract()

    val oldId = response.path[String](s"${responseArgumentsPath("createOriginal")}.created.reusedMailbox.id")
    val update = setResult(response, "replaceAndUpdate")
    val newId = mailboxId(server, "renamedReplacement")

    assertThatJson(update)
      .when(path("created.reusedMailbox"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {"reusedMailbox": {"id": ${Json.toJson(newId)}}},
        |  "updated": {${Json.toJson(newId)}: "$${json-unit.ignore}", ${Json.toJson(oldId)}: "$${json-unit.ignore}"}
        |}""".stripMargin)

    assertThat(mailboxId(server, "renamedOriginal")).isEqualTo(oldId)
  }

  @Test
  def postCreateShouldIsolateConflictsRevealedByAFailedReplacement(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[MailboxProbeImpl])
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Core/echo", {"aliasPatch": {"name": "aliasRename"}, "independentPatch": {"name": "after"}}, "preparePatches"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"reusedMailbox": {"name": "old"}}
      |    }, "createOriginal"],
      |    ["Core/echo", {
      |      "patches": {
      |        "##reusedMailbox": {"resultOf": "preparePatches", "name": "Core/echo", "path": "/aliasPatch"},
      |        "#reusedMailbox": {"name": "literalRename"},
      |        "##independent": {"resultOf": "preparePatches", "name": "Core/echo", "path": "/independentPatch"}
      |      }
      |    }, "echoPatches"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"reusedMailbox": {"name": "bad.name"}, "independent": {"name": "before"}},
      |      "#update": {"resultOf": "echoPatches", "name": "Core/echo", "path": "/patches"}
      |    }, "replaceAndUpdate"]
      |  ]
      |}""".stripMargin

    val response = execute(request).extract()

    val oldId = response.path[String](s"${responseArgumentsPath("createOriginal")}.created.reusedMailbox.id")
    val update = setResult(response, "replaceAndUpdate")
    val independentId = mailboxId(server, "after")

    assertThatJson(update)
      .when(paths("created.independent", "notCreated.reusedMailbox"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {"independent": {"id": ${Json.toJson(independentId)}}},
        |  "notCreated": {"reusedMailbox": {"type": "invalidArguments"}},
        |  "updated": {${Json.toJson(independentId)}: "$${json-unit.ignore}"},
        |  "notUpdated": {
        |    ${Json.toJson(oldId)}: {
        |      "type": "invalidArguments",
        |      "description": ${Json.toJson(s"Multiple update keys resolve to the same mailbox: '#reusedMailbox', '$oldId'")}
        |    }
        |  }
        |}""".stripMargin)

    assertThat(mailboxId(server, "old")).isEqualTo(oldId)
    assertThat(probe.listUserMailboxes(bobUsername.asString())).doesNotContain("aliasRename", "literalRename", "before")
  }

  @Test
  def postCreateShouldReportUpdateFailureUnderTheIdFromAnEarlierCall(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {"accountId": ${Json.toJson(bobAccountId)}, "create": {"child": {"name": "original"}}}, "createChild"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "update": {"#child": {"name": "renamed", "sortOrder": 123}}
      |    }, "updateChild"]
      |  ]
      |}""".stripMargin

    val response = execute(request).extract()

    val childId = response.path[String](s"${responseArgumentsPath("createChild")}.created.child.id")
    val update = setResult(response, "updateChild")
    assertThatJson(update).isEqualTo(s"""{
      |  "notUpdated": {
      |    ${Json.toJson(childId)}: {
      |      "type": "invalidArguments",
      |      "description": "Can not modify server-set properties",
      |      "properties": ["sortOrder"]
      |    }
      |  }
      |}""".stripMargin)

    assertThat(mailboxId(server, "original")).isEqualTo(childId)
    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString())).doesNotContain("renamed")
  }

  @Test
  def postCreateShouldReportOneDestroyFailureForRepeatedReferences(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"inbox": {"name": "INBOX"}}
      |    }, "createInbox"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "destroy": ["#inbox", "#inbox"]
      |    }, "destroyInbox"]
      |  ]
      |}""".stripMargin

    val response = execute(request).extract()

    val inboxId = response.path[String](s"${responseArgumentsPath("createInbox")}.created.inbox.id")
    val destroy = setResult(response, "destroyInbox")
    assertThatJson(destroy).isEqualTo(s"""{
      |  "notDestroyed": {
      |    ${Json.toJson(inboxId)}: {"type": "invalidArguments", "description": "System mailboxes cannot be destroyed"}
      |  }
      |}""".stripMargin)

    assertThat(mailboxId(server, "INBOX")).isEqualTo(inboxId)
  }

  @Test
  def postCreateShouldIsolateMissingUpdateTargets(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"failedMailbox": {"name": "bad.name"}, "independentMailbox": {"name": "other"}},
      |      "update": {
      |        "#failedMailbox": {"name": "badUpdate"},
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
        |    }
        |  }
        |}""".stripMargin)
  }

  @Test
  def postCreateShouldIsolateMissingDestroyTargets(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"failedMailbox": {"name": "bad.name"}, "independentMailbox": {"name": "other"}},
      |      "destroy": ["#failedMailbox", "#independentMailbox"]
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
        |    }
        |  }
        |}""".stripMargin)

    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString()))
      .doesNotContain("other")
  }

  @Test
  def postCreateShouldRejectInvalidParentWithoutPartialUpdate(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"child": {"name": "good"}},
      |      "update": {
      |        "#child": {"name": "partial", "parentId": "#missingMailbox", "isSubscribed": false}
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

    assertThatJson(update)
      .when(path(s"notUpdated.$childId"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {
        |    "child": "$${json-unit.ignore}"
        |  },
        |  "notUpdated": {
        |    ${Json.toJson(childId)}: {
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
  }

  @Test
  def postCreateShouldUpdateThenDestroyTheSameTarget(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)}, "create": {"newMailbox": {"name": "before"}},
      |      "update": {"#newMailbox": {"name": "after"}}, "destroy": ["#newMailbox"]
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

    val replacement = setResult(response, "replaceAndUpdateFromResult")
    val oldId = response.path[String](s"${responseArgumentsPath("createOriginals")}.created.child.id")
    val updatedId = mailboxId(server, "newParent.after")

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
    assertThat(mailboxId(server, "old")).isEqualTo(oldId)
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

    val replacement = setResult(response, "replaceAndDestroyFromResult")
    val oldId = response.path[String](s"${responseArgumentsPath("createOriginal")}.created.reusedMailbox.id")
    val newId = response.path[String](s"${responseArgumentsPath("replaceAndDestroyFromResult")}.created.reusedMailbox.id")

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
    assertThat(mailboxId(server, "old")).isEqualTo(oldId)
  }

  @Test
  def postCreateShouldValidateUpdateTargetSyntaxBeforeCreating(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"mustNotBeCreated": {"name": "mustNotBeCreated"}},
      |      "update": {"#bad/reference": {"name": "invalid"}}
      |    }, "invalidTarget"]
      |  ]
      |}""".stripMargin

    val response = execute(request).extract()

    val error = response.path[java.util.Map[String, Object]](responseArgumentsPath("invalidTarget"))
    assertThatJson(error)
      .withOptions(IGNORING_EXTRA_FIELDS)
      .isEqualTo("""{
        |  "type": "invalidArguments"
        |}""".stripMargin)

    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString()))
      .doesNotContain("mustNotBeCreated")
  }

  @Test
  def postCreateShouldValidateDestroyTargetSyntaxBeforeCreating(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"mustNotBeCreated": {"name": "mustNotBeCreated"}},
      |      "destroy": ["#"]
      |    }, "invalidTarget"]
      |  ]
      |}""".stripMargin

    val response = execute(request).extract()

    val error = response.path[java.util.Map[String, Object]](responseArgumentsPath("invalidTarget"))
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

    val response = execute(request).extract()

    val update = setResult(response, "createAndUpdateSharedMailbox")
    if (supportShares) {
      val childId = probe.getMailboxId("#private", andreUsername.asString(), "shared.child").serialize()
      assertThatJson(update)
        .when(path(s"notUpdated.$childId"), `then`(IGNORING_EXTRA_FIELDS))
        .isEqualTo(s"""{
          |  "created": {
          |    "sharedChild": "$${json-unit.ignore}"
          |  },
          |  "notUpdated": {
          |    ${Json.toJson(childId)}: {"type": "forbidden"}
          |  }
          |}""".stripMargin)
      val mailboxPaths = probe.listUserMailboxes(andreUsername.asString())
      assertThat(mailboxPaths).contains("shared.child")
      assertThat(mailboxPaths).doesNotContain("shared.renamed")
    } else {
      assertThatJson(update)
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

  @Test
  def postCreateShouldKeepSharedMailboxDestroyChecks(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[MailboxProbeImpl])
    val parentPath = MailboxPath.forUser(andreUsername, "shared")
    val parent = probe.createMailbox(parentPath).serialize()
    server.getProbe(classOf[ACLProbeImpl]).replaceRights(parentPath, bobUsername.asString(),
      new MailboxACL.Rfc4314Rights(Right.Lookup, Right.CreateMailbox))
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
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

    val response = execute(request).extract()

    val destroy = setResult(response, "createAndDestroySharedMailbox")
    val childId = probe.getMailboxId("#private", andreUsername.asString(), "shared.child").serialize()
    assertThatJson(destroy)
      .when(path(s"notDestroyed.$childId"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {
        |    "sharedChild": "$${json-unit.ignore}"
        |  },
        |  "notDestroyed": {
        |    ${Json.toJson(childId)}: {"type": "invalidArguments"}
        |  }
        |}""".stripMargin)
    assertThat(probe.listUserMailboxes(andreUsername.asString())).contains("shared.child")
  }
}
