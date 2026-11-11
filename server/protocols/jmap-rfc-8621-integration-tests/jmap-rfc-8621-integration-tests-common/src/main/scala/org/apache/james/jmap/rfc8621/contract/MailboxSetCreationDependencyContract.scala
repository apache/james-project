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
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.modules.MailboxProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.{contains, containsInAnyOrder, equalTo, hasKey, not}
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import play.api.libs.json.Json

trait MailboxSetCreationDependencyContract {
  def bobUsername: Username
  def bobAccountId: String

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

  private def storedMailboxId(server: GuiceJamesServer, path: String): String =
    server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId("#private", bobUsername.asString(), path).serialize()

  @Test
  def createShouldResolveSameCallHierarchyWhenChildrenAppearFirst(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {
      |        "grandchild": {"name": "grandchild", "parentId": "#child"},
      |        "child": {"name": "child", "parentId": "#parent"},
      |        "parent": {"name": "parent", "parentId": null}
      |      }
      |    }, "createHierarchy"],
      |    ["Mailbox/get", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "ids": ["#parent", "#child", "#grandchild"],
      |      "properties": ["id", "parentId"]
      |    }, "getHierarchy"]
      |  ]
      |}""".stripMargin

    val response = execute(request)

    val creation = responseArgumentsPath("createHierarchy")
    val fetchedMailboxes = s"${responseArgumentsPath("getHierarchy")}.list"
    val parentId = storedMailboxId(server, "parent")
    val childId = storedMailboxId(server, "parent.child")
    val grandchildId = storedMailboxId(server, "parent.child.grandchild")

    response
      .body(creation, not(hasKey("notCreated")))
      .body(s"$creation.created.parent.id", equalTo(parentId))
      .body(s"$creation.created.child.id", equalTo(childId))
      .body(s"$creation.created.grandchild.id", equalTo(grandchildId))
      .body(s"$fetchedMailboxes.find { it.id == '$childId' }.parentId", equalTo(parentId))
      .body(s"$fetchedMailboxes.find { it.id == '$grandchildId' }.parentId", equalTo(childId))
  }

  @ParameterizedTest
  @ValueSource(ints = Array(7, 255))
  def createShouldRejectUnknownParentReferences(referenceLength: Int, server: GuiceJamesServer): Unit = {
    val unknownCreationId = "x" * referenceLength
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {
      |        "child": {"name": "child", "parentId": "#$unknownCreationId"}
      |      }
      |    }, "create"]
      |  ]
      |}""".stripMargin

    val childError = s"${responseArgumentsPath("create")}.notCreated.child"
    execute(request)
      .body(s"$childError.type", equalTo("invalidProperties"))
      .body(s"$childError.properties", contains("parentId"))
      .body(s"$childError.description", equalTo(s"parentId references unknown mailbox creation '#$unknownCreationId'"))

    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString()))
      .doesNotContain("child")
  }

  @Test
  def createShouldRejectSelfReferences(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {
      |        "self": {"name": "self", "parentId": "#self"}
      |      }
      |    }, "create"]
      |  ]
      |}""".stripMargin

    val selfError = s"${responseArgumentsPath("create")}.notCreated.self"
    execute(request)
      .body(s"$selfError.type", equalTo("invalidProperties"))
      .body(s"$selfError.properties", contains("parentId"))

    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString()))
      .doesNotContain("self")
  }

  @Test
  def createShouldRejectCyclesAndTheirDescendantsWhileCreatingIndependentMailboxes(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {
      |        "first": {"name": "first", "parentId": "#second"},
      |        "second": {"name": "second", "parentId": "#first"},
      |        "descendant": {"name": "descendant", "parentId": "#first"},
      |        "independent": {"name": "independent"}
      |      }
      |    }, "create"]
      |  ]
      |}""".stripMargin

    val creation = responseArgumentsPath("create")
    val response = execute(request)
      .body(s"$creation.created.keySet().toList()", contains("independent"))

    response.body(s"$creation.created.independent.id", equalTo(storedMailboxId(server, "independent")))
    Seq("first", "second", "descendant").foreach(creationId => {
      val error = s"$creation.notCreated.$creationId"
      response
        .body(s"$error.type", equalTo("invalidProperties"))
        .body(s"$error.properties", contains("parentId"))
        .body(s"$error.description", equalTo("parentId cannot be resolved because this mailbox creation depends on a cycle"))
    })

    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString()))
      .doesNotContain("first", "second", "descendant")
  }

  @Test
  def createShouldPreserveParentFailureAndRejectItsChild(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {
      |        "parent": {"name": "bad.name"},
      |        "child": {"name": "child", "parentId": "#parent"},
      |        "independent": {"name": "independent"}
      |      }
      |    }, "create"]
      |  ]
      |}""".stripMargin

    val creation = responseArgumentsPath("create")
    execute(request)
      .body(s"$creation.notCreated.parent.type", equalTo("invalidArguments"))
      .body(s"$creation.notCreated.child.type", equalTo("invalidProperties"))
      .body(s"$creation.notCreated.child.properties", contains("parentId"))
      .body(s"$creation.notCreated.child.description", equalTo("parentId references failed mailbox creation '#parent'"))
      .body(s"$creation.created.keySet().toList()", contains("independent"))

    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString()))
      .doesNotContain("bad.name", "child")
  }

  @Test
  def createShouldResolveSiblingsAndIndependentRoots(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {
      |        "sibling1": {"name": "sibling1", "parentId": "#root"},
      |        "sibling2": {"name": "sibling2", "parentId": "#root"},
      |        "root": {"name": "root"},
      |        "otherRoot": {"name": "otherRoot"}
      |      }
      |    }, "create"]
      |  ]
      |}""".stripMargin

    val creation = responseArgumentsPath("create")
    val response = execute(request)
    response
      .body(creation, not(hasKey("notCreated")))
      .body(s"$creation.created.keySet().toList()", containsInAnyOrder("root", "otherRoot", "sibling1", "sibling2"))
      .body(s"$creation.created.sibling1.id", equalTo(storedMailboxId(server, "root.sibling1")))
      .body(s"$creation.created.sibling2.id", equalTo(storedMailboxId(server, "root.sibling2")))
  }

  @Test
  def createShouldResolveAReusedCreationIdToTheMostRecentlyCreatedMailbox(server: GuiceJamesServer): Unit = {
    // RFC 8620 section 5.3 requires the latest successful mapping when a creation ID is reused.
    // The same section requires creating the new parent before resolving the child's reference.
    // https://www.rfc-editor.org/rfc/rfc8620.html#section-5.3
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {
      |        "parent": {"name": "old"}
      |      }
      |    }, "createOldParent"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {
      |        "child": {"name": "child", "parentId": "#parent"},
      |        "parent": {"name": "new"}
      |      }
      |    }, "reuseParentIdAndCreateChild"]
      |  ]
      |}""".stripMargin

    val oldParentCreation = responseArgumentsPath("createOldParent")
    val replacement = responseArgumentsPath("reuseParentIdAndCreateChild")
    val response = execute(request)
    response
      .body(s"$oldParentCreation.created.parent.id", equalTo(storedMailboxId(server, "old")))
      .body(replacement, not(hasKey("notCreated")))
      .body(s"$replacement.created.child.id", equalTo(storedMailboxId(server, "new.child")))
      .body(s"$replacement.created.parent.id", equalTo(storedMailboxId(server, "new")))
  }

  @Test
  def createShouldReuseTheLastSuccessfulMappingWhenTheSameCallParentFails(server: GuiceJamesServer): Unit = {
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {
      |        "parent": {"name": "old"}
      |      }
      |    }, "createOldParent"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {
      |        "child": {"name": "child", "parentId": "#parent"},
      |        "parent": {"name": "bad.name"},
      |        "root": {"name": "root"}
      |      }
      |    }, "failReplacement"],
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {
      |        "later": {"name": "later", "parentId": "#parent"}
      |      }
      |    }, "reuseOldParent"]
      |  ]
      |}""".stripMargin

    val replacement = responseArgumentsPath("failReplacement")
    val laterCreation = responseArgumentsPath("reuseOldParent")
    val response = execute(request)
    response
      .body(s"$replacement.notCreated.parent.type", equalTo("invalidArguments"))
      .body(s"$replacement.notCreated.keySet().toList()", contains("parent"))
      .body(s"$replacement.created.keySet().toList()", containsInAnyOrder("child", "root"))
      .body(s"$replacement.created.child.id", equalTo(storedMailboxId(server, "old.child")))
      .body(s"$laterCreation.created.later.id", equalTo(storedMailboxId(server, "old.later")))
  }

  @Test
  def createShouldResolveAMaximumLengthReferenceAndRealParentId(server: GuiceJamesServer): Unit = {
    val existingParentId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(bobUsername, "existing")).serialize()
    val creationId = "x" * 255
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {
      |        "child": {"name": "child", "parentId": "#$creationId"},
      |        "$creationId": {"name": "parent", "parentId": ${Json.toJson(existingParentId)}}
      |      }
      |    }, "create"]
      |  ]
      |}""".stripMargin

    val creation = responseArgumentsPath("create")
    val response = execute(request)
    response
      .body(creation, not(hasKey("notCreated")))
      .body(s"$creation.created.child.id", equalTo(storedMailboxId(server, "existing.parent.child")))
  }

  @Test
  def createShouldNotInferACycleFromACrossTypeReference(server: GuiceJamesServer): Unit = {
    // Creation IDs share one map across types (RFC 8620 section 5.3). The email's
    // mailboxIds references the earlier mailbox; only after creation does #shared identify the email.
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {
      |        "shared": {"name": "mailbox"}
      |      }
      |    }, "createMailbox"],
      |    ["Email/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {
      |        "shared": {"mailboxIds": {"#shared": true}, "subject": "cross-type reference"}
      |      }
      |    }, "createEmail"],
      |    ["Email/get", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "ids": ["#shared"],
      |      "properties": ["id", "mailboxIds"]
      |    }, "getEmail"]
      |  ]
      |}""".stripMargin

    val emailCreation = responseArgumentsPath("createEmail")
    val fetchedEmails = s"${responseArgumentsPath("getEmail")}.list"
    val response = execute(request)
      .body(emailCreation, not(hasKey("notCreated")))

    val createdEmailId = response.extract().path[String](s"$emailCreation.created.shared.id")
    val expectedMailboxIds = java.util.Map.of(storedMailboxId(server, "mailbox"), java.lang.Boolean.TRUE)
    response
      .body(s"$fetchedEmails.id", contains(createdEmailId))
      .body(s"$fetchedEmails[0].mailboxIds", equalTo(expectedMailboxIds))
  }
}
