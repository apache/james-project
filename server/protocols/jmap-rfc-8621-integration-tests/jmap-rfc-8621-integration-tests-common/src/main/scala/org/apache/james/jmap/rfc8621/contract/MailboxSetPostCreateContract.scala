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
import io.restassured.response.ValidatableResponse
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.rfc8621.contract.Fixture.ACCEPT_RFC8621_VERSION_HEADER
import org.apache.james.mailbox.model.MailboxACL.Right
import org.apache.james.mailbox.model.{MailboxACL, MailboxPath}
import org.apache.james.modules.{ACLProbeImpl, MailboxProbeImpl}
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.{contains, containsInAnyOrder, equalTo, hasKey, not}
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

    val creation = responseArgumentsPath("createAndUpdate")
    val response = execute(request)
    val createdId = mailboxId(server, "after")

    response
      .body(s"$creation.created.'$creationId'.id", equalTo(createdId))
      .body(s"$creation.updated.keySet().toList()", contains(createdId))
    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString())).doesNotContain("before")
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

    val creation = responseArgumentsPath("createAndDestroy")
    val response = execute(request)
    val createdId = response.extract().path[String](s"$creation.created.'$creationId'.id")

    response.body(s"$creation.destroyed", contains(createdId))
    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString())).doesNotContain("before")
  }

  @Test
  def postCreateShouldResolveParentAndRealTarget(server: GuiceJamesServer): Unit = {
    val child = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(bobUsername, "child")).serialize()
    val parent = "p" * 255
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"$parent": {"name": "parent"}},
      |      "update": {${Json.toJson(child)}: {"parentId": "#$parent", "name": "renamed"}}
      |    }, "createParentAndMoveChild"]
      |  ]
      |}""".stripMargin

    val move = responseArgumentsPath("createParentAndMoveChild")
    execute(request)
      .body(move, not(hasKey("notUpdated")))
      .body(s"$move.updated.keySet().toList()", contains(child))

    assertThat(mailboxId(server, "parent.renamed")).isEqualTo(child)
  }

  @Test
  def postCreateShouldUseTheLatestSuccessfulMapping(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"x": {"name": "old"}, "p": {"name": "oldParent"}}
      |    }, "createOriginals"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"x": {"name": "new"}, "p": {"name": "newParent"}},
      |      "update": {"#x": {"name": "renamed", "parentId": "#p"}}
      |    }, "replaceAndUpdate"],
      |    ["Mailbox/get", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "ids": ["#x"], "properties": ["id", "parentId"]
      |    }, "getReplacement"],
      |    ["Mailbox/set", {"accountId": ${Json.toJson(bobAccountId)}, "destroy": ["#x"]}, "destroyReplacement"]
      |  ]
      |}""".stripMargin

    val original = responseArgumentsPath("createOriginals")
    val replacement = responseArgumentsPath("replaceAndUpdate")
    val fetched = responseArgumentsPath("getReplacement")
    val deletion = responseArgumentsPath("destroyReplacement")
    val response = execute(request)
    val newId = response.extract().path[String](s"$replacement.created.x.id")
    val newParentId = response.extract().path[String](s"$replacement.created.p.id")

    response
      .body(s"$original.created.x.id", equalTo(mailboxId(server, "old")))
      .body(s"$replacement.updated.keySet().toList()", contains(newId))
      .body(s"$fetched.list.id", contains(newId))
      .body(s"$fetched.list.parentId", contains(newParentId))
      .body(s"$deletion.destroyed", contains(newId))
    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString()))
      .doesNotContain("new", "newParent.renamed", "oldParent.renamed")
  }

  @Test
  def postCreateShouldDestroyTheLatestReusedMapping(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)}, "create": {"x": {"name": "old"}}
      |    }, "createOriginal"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)}, "create": {"x": {"name": "new"}}, "destroy": ["#x"]
      |    }, "replaceAndDestroy"]
      |  ]
      |}""".stripMargin

    val original = responseArgumentsPath("createOriginal")
    val replacement = responseArgumentsPath("replaceAndDestroy")
    val response = execute(request)
    val newId = response.extract().path[String](s"$replacement.created.x.id")

    response
      .body(s"$original.created.x.id", equalTo(mailboxId(server, "old")))
      .body(s"$replacement.destroyed", contains(newId))
    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString())).doesNotContain("new")
  }

  @Test
  def postCreateShouldKeepThePreviousSuccessfulMappingWhenReplacementFails(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)}, "create": {"x": {"name": "old"}}
      |    }, "createOriginal"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)}, "create": {"x": {"name": "bad.name"}},
      |      "update": {"#x": {"name": "renamed"}}
      |    }, "failReplacementAndUpdate"]
      |  ]
      |}""".stripMargin

    val original = responseArgumentsPath("createOriginal")
    val replacement = responseArgumentsPath("failReplacementAndUpdate")
    val response = execute(request)
    val oldId = mailboxId(server, "renamed")

    response
      .body(s"$original.created.x.id", equalTo(oldId))
      .body(s"$replacement.notCreated.x.type", equalTo("invalidArguments"))
      .body(s"$replacement.updated.keySet().toList()", contains(oldId))
  }

  @Test
  def postCreateShouldIsolateMissingUpdateTargets(server: GuiceJamesServer): Unit = {
    val unknownId = "m" * 255
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"bad": {"name": "bad.name"}, "other": {"name": "other"}},
      |      "update": {"#bad": {"name": "badUpdate"}, "#$unknownId": {"name": "missing"}, "#other": {"name": "independent"}}
      |    }, "updateTargets"]
      |  ]
      |}""".stripMargin

    val update = responseArgumentsPath("updateTargets")
    val response = execute(request)
      .body(s"$update.notCreated.bad.type", equalTo("invalidArguments"))

    Seq("bad", unknownId).foreach(creationId => {
      val error = s"$update.notUpdated.'#$creationId'"
      response
        .body(s"$error.type", equalTo("notFound"))
        .body(s"$error.description", equalTo(s"Unable to resolve mailbox reference '#$creationId': no successful creation is recorded for creation ID '$creationId'"))
    })
    val independentId = mailboxId(server, "independent")
    response
      .body(s"$update.created.other.id", equalTo(independentId))
      .body(s"$update.updated.keySet().toList()", contains(independentId))
  }

  @Test
  def postCreateShouldIsolateMissingDestroyTargets(server: GuiceJamesServer): Unit = {
    val unknownId = "m" * 255
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"bad": {"name": "bad.name"}, "other": {"name": "other"}},
      |      "destroy": ["#bad", "#$unknownId", "#other"]
      |    }, "destroyTargets"]
      |  ]
      |}""".stripMargin

    val deletion = responseArgumentsPath("destroyTargets")
    val response = execute(request)
      .body(s"$deletion.notCreated.bad.type", equalTo("invalidArguments"))

    Seq("bad", unknownId).foreach(creationId => {
      val error = s"$deletion.notDestroyed.'#$creationId'"
      response
        .body(s"$error.type", equalTo("notFound"))
        .body(s"$error.description", equalTo(s"Unable to resolve mailbox reference '#$creationId': no successful creation is recorded for creation ID '$creationId'"))
    })
    val otherId = response.extract().path[String](s"$deletion.created.other.id")
    response.body(s"$deletion.destroyed", contains(otherId))
    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString())).doesNotContain("other")
  }

  @Test
  def postCreateShouldRejectInvalidParentWithoutPartialUpdate(server: GuiceJamesServer): Unit = {
    val unknownId = "m" * 255
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"good": {"name": "good"}, "other": {"name": "other"}},
      |      "update": {
      |        "#good": {"name": "partial", "parentId": "#$unknownId", "isSubscribed": false},
      |        "#other": {"name": "independent"}
      |      }
      |    }, "updateWithMissingParent"],
      |    ["Mailbox/get", {
      |      "accountId": ${Json.toJson(bobAccountId)}, "ids": ["#good"], "properties": ["id", "name", "isSubscribed"]
      |    }, "getUnchangedMailbox"]
      |  ]
      |}""".stripMargin

    val update = responseArgumentsPath("updateWithMissingParent")
    val fetched = responseArgumentsPath("getUnchangedMailbox")
    val response = execute(request)
    val goodId = mailboxId(server, "good")
    val independentId = mailboxId(server, "independent")

    response
      .body(s"$update.notUpdated.'#good'.type", equalTo("invalidArguments"))
      .body(s"$update.notUpdated.'#good'.properties", contains("parentId"))
      .body(s"$update.created.other.id", equalTo(independentId))
      .body(s"$fetched.list.id", contains(goodId))
      .body(s"$fetched.list.isSubscribed", contains(true))
    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString())).doesNotContain("partial")
  }

  @Test
  def postCreateShouldUpdateDistinctCreationReferenceAndRealIdTargets(server: GuiceJamesServer): Unit = {
    val originalId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(bobUsername, "original")).serialize()
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)}, "create": {"x": {"name": "unused"}},
      |      "update": {"#x": {"name": "alias"}, ${Json.toJson(originalId)}: {"name": "real"}}
      |    }, "createAndUpdateDistinctTargets"]
      |  ]
      |}""".stripMargin

    val update = responseArgumentsPath("createAndUpdateDistinctTargets")
    val response = execute(request)
    val newId = mailboxId(server, "alias")

    response
      .body(s"$update.created.x.id", equalTo(newId))
      .body(s"$update.updated.keySet().toList()", containsInAnyOrder(originalId, newId))
    assertThat(mailboxId(server, "real")).isEqualTo(originalId)
  }

  @Test
  def postCreateShouldResolveCreationReferencesReturnedByAResultReference(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Core/echo", {"patches": {"#x": {"parentId": "#p", "name": "after"}}}, "echoPatches"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"x": {"name": "before"}, "p": {"name": "parent"}},
      |      "#update": {"resultOf": "echoPatches", "name": "Core/echo", "path": "/patches"}
      |    }, "createAndUpdateFromResult"]
      |  ]
      |}""".stripMargin

    val update = responseArgumentsPath("createAndUpdateFromResult")
    val response = execute(request)
    val updatedId = mailboxId(server, "parent.after")

    response
      .body(s"$update.created.x.id", equalTo(updatedId))
      .body(s"$update.updated.keySet().toList()", contains(updatedId))
  }

  @Test
  def postCreateShouldUpdateThenDestroyTheSameTarget(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)}, "create": {"x": {"name": "before"}},
      |      "update": {"#x": {"name": "after"}}, "destroy": ["#x", "#x"]
      |    }, "createUpdateAndDestroy"]
      |  ]
      |}""".stripMargin

    val operation = responseArgumentsPath("createUpdateAndDestroy")
    val response = execute(request)
    val createdId = response.extract().path[String](s"$operation.created.x.id")

    response
      .body(s"$operation.updated.keySet().toList()", contains(createdId))
      .body(s"$operation.destroyed", contains(createdId))
      .body(operation, not(hasKey("notDestroyed")))
    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString())).doesNotContain("before", "after")
  }

  @Test
  def postCreateShouldUseTheLatestMappingForUpdateTargetsFromAResultReference(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Core/echo", {
      |      "patches": {"#x": {"name": "after", "parentId": "#p"}}
      |    }, "echoPatches"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"x": {"name": "old"}, "p": {"name": "oldParent"}}
      |    }, "createOriginals"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"x": {"name": "new"}, "p": {"name": "newParent"}},
      |      "#update": {"resultOf": "echoPatches", "name": "Core/echo", "path": "/patches"}
      |    }, "replaceAndUpdateFromResult"]
      |  ]
      |}""".stripMargin

    val original = responseArgumentsPath("createOriginals")
    val replacement = responseArgumentsPath("replaceAndUpdateFromResult")
    val response = execute(request)
    val updatedId = mailboxId(server, "newParent.after")

    response
      .body(s"$original.created.x.id", equalTo(mailboxId(server, "old")))
      .body(s"$replacement.created.x.id", equalTo(updatedId))
      .body(s"$replacement.updated.keySet().toList()", contains(updatedId))
  }

  @Test
  def postCreateShouldUseTheLatestMappingForDestroyTargetsFromAResultReference(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Core/echo", {"targets": ["#x"]}, "echoTargets"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)}, "create": {"x": {"name": "old"}}
      |    }, "createOriginal"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)}, "create": {"x": {"name": "new"}},
      |      "#destroy": {"resultOf": "echoTargets", "name": "Core/echo", "path": "/targets"}
      |    }, "replaceAndDestroyFromResult"]
      |  ]
      |}""".stripMargin

    val original = responseArgumentsPath("createOriginal")
    val replacement = responseArgumentsPath("replaceAndDestroyFromResult")
    val response = execute(request)
    val newId = response.extract().path[String](s"$replacement.created.x.id")

    response
      .body(s"$original.created.x.id", equalTo(mailboxId(server, "old")))
      .body(s"$replacement.destroyed", contains(newId))
    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString())).doesNotContain("new")
  }

  @ParameterizedTest
  @ValueSource(strings = Array("#", "#bad/reference"))
  def postCreateShouldValidateUpdateTargetSyntaxBeforeCreating(target: String, server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"x": {"name": "mustNotBeCreated"}},
      |      "update": {${Json.toJson(target)}: {"name": "invalid"}}
      |    }, "invalidTarget"]
      |  ]
      |}""".stripMargin

    val invalidTarget = responseArgumentsPath("invalidTarget")
    execute(request).body(s"$invalidTarget.type", equalTo("invalidArguments"))

    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString())).doesNotContain("mustNotBeCreated")
  }

  @ParameterizedTest
  @ValueSource(strings = Array("#", "#bad/reference"))
  def postCreateShouldValidateDestroyTargetSyntaxBeforeCreating(target: String, server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {"x": {"name": "mustNotBeCreated"}},
      |      "destroy": [${Json.toJson(target)}]
      |    }, "invalidTarget"]
      |  ]
      |}""".stripMargin

    val invalidTarget = responseArgumentsPath("invalidTarget")
    execute(request).body(s"$invalidTarget.type", equalTo("invalidArguments"))

    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString())).doesNotContain("mustNotBeCreated")
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
      |        "u": {"name": "u", "parentId": ${Json.toJson(parent)}}
      |      },
      |      "update": {"#u": {"name": "renamed"}}
      |    }, "createAndUpdateSharedMailbox"]
      |  ]
      |}""".stripMargin

    val update = responseArgumentsPath("createAndUpdateSharedMailbox")
    val response = execute(request)

    if (supportShares) {
      response
        .body(s"$update.created.keySet().toList()", contains("u"))
        .body(s"$update.notUpdated.'#u'.type", equalTo("forbidden"))
      val mailboxPaths = probe.listUserMailboxes(andreUsername.asString())
      assertThat(mailboxPaths).contains("shared.u")
      assertThat(mailboxPaths).doesNotContain("shared.renamed")
    } else {
      response
        .body(s"$update.notCreated.keySet().toList()", contains("u"))
        .body(s"$update.notUpdated.'#u'.type", equalTo("notFound"))
      assertThat(probe.listUserMailboxes(andreUsername.asString())).doesNotContain("shared.u")
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
      |        "d": {"name": "d", "parentId": ${Json.toJson(parent)}}
      |      },
      |      "destroy": ["#d"]
      |    }, "createAndDestroySharedMailbox"]
      |  ]
      |}""".stripMargin

    val deletion = responseArgumentsPath("createAndDestroySharedMailbox")
    val response = execute(request)

    if (supportShares) {
      response
        .body(s"$deletion.created.keySet().toList()", contains("d"))
        .body(s"$deletion.notDestroyed.'#d'.type", equalTo("invalidArguments"))
      assertThat(probe.listUserMailboxes(andreUsername.asString())).contains("shared.d")
    } else {
      response
        .body(s"$deletion.notCreated.keySet().toList()", contains("d"))
        .body(s"$deletion.notDestroyed.'#d'.type", equalTo("notFound"))
      assertThat(probe.listUserMailboxes(andreUsername.asString())).doesNotContain("shared.d")
    }
  }
}
