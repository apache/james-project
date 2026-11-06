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
import io.restassured.RestAssured.given
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
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}

trait MailboxSetPostCreateContract {
  def bobUsername: Username
  def bobAccountId: String
  def andreUsername: Username

  private def execute(request: String): JsValue = Json.parse(given()
    .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .body(request)
    .when().post().then().statusCode(200).extract().asString())

  private def responseFor(response: JsValue, callId: String): JsValue = {
    val methodResponses = (response \ "methodResponses").as[Seq[JsArray]]
    val matchingResponse = methodResponses.find(call => call(2).as[String] == callId)
      .getOrElse(throw new AssertionError(s"Missing response for call '$callId': $response"))
    matchingResponse(1)
  }

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

    val response = execute(request)

    val creationResponse = responseFor(response, "createAndUpdate")
    val createdId = (creationResponse \ "created" \ creationId \ "id").as[String]
    assertThat((creationResponse \ "updated").as[JsObject].keys).isEqualTo(Set(createdId))
    assertThat(mailboxId(server, "after")).isEqualTo(createdId)
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

    val response = execute(request)

    val creationResponse = responseFor(response, "createAndDestroy")
    val createdId = (creationResponse \ "created" \ creationId \ "id").as[String]
    assertThat((creationResponse \ "destroyed").as[Seq[String]]).isEqualTo(Seq(createdId))
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
      |      "update": {"$child": {"parentId": "#$parent", "name": "renamed"}}
      |    }, "createParentAndMoveChild"]
      |  ]
      |}""".stripMargin

    val response = execute(request)

    val moveResponse = responseFor(response, "createParentAndMoveChild")
    assertThat((moveResponse \ "notUpdated").toOption).isEqualTo(None)
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

    val response = execute(request)

    val originalResponse = responseFor(response, "createOriginals")
    val replacementResponse = responseFor(response, "replaceAndUpdate")
    val fetchedMailboxes = (responseFor(response, "getReplacement") \ "list").as[Seq[JsObject]]
    val destroyedResponse = responseFor(response, "destroyReplacement")
    val newId = (replacementResponse \ "created" \ "x" \ "id").as[String]
    assertThat((replacementResponse \ "updated").as[JsObject].keys).isEqualTo(Set(newId))
    assertThat((fetchedMailboxes.head \ "parentId").as[String])
      .isEqualTo((replacementResponse \ "created" \ "p" \ "id").as[String])
    assertThat((destroyedResponse \ "destroyed").as[Seq[String]]).isEqualTo(Seq(newId))
    assertThat(mailboxId(server, "old")).isEqualTo((originalResponse \ "created" \ "x" \ "id").as[String])
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

    val response = execute(request)

    val replacementResponse = responseFor(response, "replaceAndDestroy")
    val newId = (replacementResponse \ "created" \ "x" \ "id").as[String]
    assertThat((replacementResponse \ "destroyed").as[Seq[String]]).isEqualTo(Seq(newId))
    assertThat(mailboxId(server, "old"))
      .isEqualTo((responseFor(response, "createOriginal") \ "created" \ "x" \ "id").as[String])
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

    val response = execute(request)

    val oldId = (responseFor(response, "createOriginal") \ "created" \ "x" \ "id").as[String]
    val replacementResponse = responseFor(response, "failReplacementAndUpdate")
    assertThat((replacementResponse \ "notCreated" \ "x" \ "type").as[String]).isEqualTo("invalidArguments")
    assertThat((replacementResponse \ "updated").as[JsObject].keys).isEqualTo(Set(oldId))
    assertThat(mailboxId(server, "renamed")).isEqualTo(oldId)
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

    val response = execute(request)

    val operationResponse = responseFor(response, "updateTargets")
    assertThat((operationResponse \ "notCreated" \ "bad" \ "type").as[String]).isEqualTo("invalidArguments")
    Seq("bad", unknownId).foreach(creationId => {
      val error = operationResponse \ "notUpdated" \ ("#" + creationId)
      assertThat((error \ "type").as[String]).isEqualTo("notFound")
      assertThat((error \ "description").as[String])
        .isEqualTo(s"Unable to resolve mailbox reference '#$creationId': no successful creation is recorded for creation ID '$creationId'")
    })
    assertThat(mailboxId(server, "independent")).isEqualTo((operationResponse \ "created" \ "other" \ "id").as[String])
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

    val response = execute(request)

    val operationResponse = responseFor(response, "destroyTargets")
    assertThat((operationResponse \ "notCreated" \ "bad" \ "type").as[String]).isEqualTo("invalidArguments")
    Seq("bad", unknownId).foreach(creationId => {
      val error = operationResponse \ "notDestroyed" \ ("#" + creationId)
      assertThat((error \ "type").as[String]).isEqualTo("notFound")
      assertThat((error \ "description").as[String])
        .isEqualTo(s"Unable to resolve mailbox reference '#$creationId': no successful creation is recorded for creation ID '$creationId'")
    })
    val otherId = (operationResponse \ "created" \ "other" \ "id").as[String]
    assertThat((operationResponse \ "destroyed").as[Seq[String]]).isEqualTo(Seq(otherId))
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

    val response = execute(request)

    val updateResponse = responseFor(response, "updateWithMissingParent")
    val invalidParentError = updateResponse \ "notUpdated" \ "#good"
    assertThat((invalidParentError \ "type").as[String]).isEqualTo("invalidArguments")
    assertThat((invalidParentError \ "properties").as[Seq[String]]).isEqualTo(Seq("parentId"))
    val goodId = mailboxId(server, "good")
    val fetchedMailboxes = (responseFor(response, "getUnchangedMailbox") \ "list").as[Seq[JsObject]]
    assertThat((fetchedMailboxes.head \ "id").as[String]).isEqualTo(goodId)
    assertThat((fetchedMailboxes.head \ "isSubscribed").as[Boolean]).isTrue()
    assertThat(mailboxId(server, "independent")).isEqualTo((updateResponse \ "created" \ "other" \ "id").as[String])
    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString())).doesNotContain("partial")
  }

  @Test
  def postCreateShouldKeepDistinctTargetsWhenACreationIdIsReused(server: GuiceJamesServer): Unit = {
    val originalId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(bobUsername, "original")).serialize()
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/get", {"accountId": ${Json.toJson(bobAccountId)}, "ids": ["$originalId"]}, "getOriginal"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)}, "create": {"x": {"name": "unused"}},
      |      "update": {"#x": {"name": "alias"}, "$originalId": {"name": "real"}}
      |    }, "createAndUpdateDistinctTargets"]
      |  ]
      |}""".stripMargin

    val response = execute(request)

    val updateResponse = responseFor(response, "createAndUpdateDistinctTargets")
    val newId = (updateResponse \ "created" \ "x" \ "id").as[String]
    // The creation reference and existing ID denote different mailboxes, so both patches must survive.
    assertThat((updateResponse \ "updated").as[JsObject].keys).isEqualTo(Set(originalId, newId))
    assertThat(mailboxId(server, "real")).isEqualTo(originalId)
    assertThat(mailboxId(server, "alias")).isEqualTo(newId)
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

    val response = execute(request)

    val updateResponse = responseFor(response, "createAndUpdateFromResult")
    val id = (updateResponse \ "created" \ "x" \ "id").as[String]
    assertThat((updateResponse \ "updated").as[JsObject].keys).isEqualTo(Set(id))
    assertThat(mailboxId(server, "parent.after")).isEqualTo(id)
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

    val response = execute(request)

    val operationResponse = responseFor(response, "createUpdateAndDestroy")
    val id = (operationResponse \ "created" \ "x" \ "id").as[String]
    assertThat((operationResponse \ "updated").as[JsObject].keys).isEqualTo(Set(id))
    assertThat((operationResponse \ "destroyed").as[Seq[String]]).isEqualTo(Seq(id))
    assertThat((operationResponse \ "notDestroyed").toOption).isEqualTo(None)
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

    val response = execute(request)

    val replacementResponse = responseFor(response, "replaceAndUpdateFromResult")
    val newId = (replacementResponse \ "created" \ "x" \ "id").as[String]
    assertThat((replacementResponse \ "updated").as[JsObject].keys).isEqualTo(Set(newId))
    assertThat(mailboxId(server, "newParent.after")).isEqualTo(newId)
    assertThat(mailboxId(server, "old"))
      .isEqualTo((responseFor(response, "createOriginals") \ "created" \ "x" \ "id").as[String])
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

    val response = execute(request)

    val replacementResponse = responseFor(response, "replaceAndDestroyFromResult")
    val newId = (replacementResponse \ "created" \ "x" \ "id").as[String]
    assertThat((replacementResponse \ "destroyed").as[Seq[String]]).isEqualTo(Seq(newId))
    assertThat(mailboxId(server, "old"))
      .isEqualTo((responseFor(response, "createOriginal") \ "created" \ "x" \ "id").as[String])
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

    val response = execute(request)

    assertThat((responseFor(response, "invalidTarget") \ "type").as[String]).isEqualTo("invalidArguments")
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

    val response = execute(request)

    assertThat((responseFor(response, "invalidTarget") \ "type").as[String]).isEqualTo("invalidArguments")
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
      |        "u": {"name": "u", "parentId": "$parent"}
      |      },
      |      "update": {"#u": {"name": "renamed"}}
      |    }, "createAndUpdateSharedMailbox"]
      |  ]
      |}""".stripMargin

    val operationResponse = responseFor(execute(request), "createAndUpdateSharedMailbox")

    if (supportShares) {
      assertThat((operationResponse \ "created").as[JsObject].keys).isEqualTo(Set("u"))
      assertThat((operationResponse \ "notUpdated" \ "#u" \ "type").as[String]).isEqualTo("forbidden")
      assertThat(probe.listUserMailboxes(andreUsername.asString())).contains("shared.u")
      assertThat(probe.listUserMailboxes(andreUsername.asString())).doesNotContain("shared.renamed")
    } else {
      assertThat((operationResponse \ "notCreated").as[JsObject].keys).isEqualTo(Set("u"))
      assertThat((operationResponse \ "notUpdated" \ "#u" \ "type").as[String]).isEqualTo("notFound")
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
      |        "d": {"name": "d", "parentId": "$parent"}
      |      },
      |      "destroy": ["#d"]
      |    }, "createAndDestroySharedMailbox"]
      |  ]
      |}""".stripMargin

    val operationResponse = responseFor(execute(request), "createAndDestroySharedMailbox")

    if (supportShares) {
      assertThat((operationResponse \ "created").as[JsObject].keys).isEqualTo(Set("d"))
      assertThat((operationResponse \ "notDestroyed" \ "#d" \ "type").as[String]).isEqualTo("invalidArguments")
      assertThat(probe.listUserMailboxes(andreUsername.asString())).contains("shared.d")
    } else {
      assertThat((operationResponse \ "notCreated").as[JsObject].keys).isEqualTo(Set("d"))
      assertThat((operationResponse \ "notDestroyed" \ "#d" \ "type").as[String]).isEqualTo("notFound")
      assertThat(probe.listUserMailboxes(andreUsername.asString())).doesNotContain("shared.d")
    }
  }
}
