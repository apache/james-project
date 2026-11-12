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
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.ConfigurationWhen.{path, paths, `then`}
import net.javacrumbs.jsonunit.core.Option.{IGNORING_ARRAY_ORDER, IGNORING_EXTRA_FIELDS}
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.rfc8621.contract.Fixture.ACCEPT_RFC8621_VERSION_HEADER
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.modules.MailboxProbeImpl
import org.assertj.core.api.Assertions.assertThat
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

  private def creationResultsPath(callId: String): String =
    s"${responseArgumentsPath(callId)}.subMap(['created', 'notCreated'])"

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

    val response = execute(request).extract()
    val creation = response.path[java.util.Map[String, Object]](creationResultsPath("createHierarchy"))
    val fetchedMailboxes = response.path[java.util.List[Object]](s"${responseArgumentsPath("getHierarchy")}.list")
    val parentId = storedMailboxId(server, "parent")
    val childId = storedMailboxId(server, "parent.child")
    val grandchildId = storedMailboxId(server, "parent.child.grandchild")

    assertThatJson(creation)
      .when(paths("created.parent", "created.child", "created.grandchild"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {
        |    "parent": {"id": ${Json.toJson(parentId)}},
        |    "child": {"id": ${Json.toJson(childId)}},
        |    "grandchild": {"id": ${Json.toJson(grandchildId)}}
        |  }
        |}""".stripMargin)

    assertThatJson(fetchedMailboxes)
      .withOptions(IGNORING_ARRAY_ORDER)
      .isEqualTo(s"""[
        |  {"id": ${Json.toJson(parentId)}, "parentId": null},
        |  {"id": ${Json.toJson(childId)}, "parentId": ${Json.toJson(parentId)}},
        |  {"id": ${Json.toJson(grandchildId)}, "parentId": ${Json.toJson(childId)}}
        |]""".stripMargin)
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

    val childError = execute(request).extract()
      .path[java.util.Map[String, Object]](s"${responseArgumentsPath("create")}.notCreated.child")

    assertThatJson(childError)
      .isEqualTo(s"""{
        |  "type": "invalidProperties",
        |  "properties": ["parentId"],
        |  "description": "parentId references unknown mailbox creation '#$unknownCreationId'"
        |}""".stripMargin)

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

    val selfError = execute(request).extract()
      .path[java.util.Map[String, Object]](s"${responseArgumentsPath("create")}.notCreated.self")

    assertThatJson(selfError)
      .isEqualTo("""{
        |  "type": "invalidProperties",
        |  "properties": ["parentId"],
        |  "description": "parentId cannot be resolved because this mailbox creation depends on a cycle"
        |}""".stripMargin)

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

    val creation = execute(request).extract()
      .path[java.util.Map[String, Object]](creationResultsPath("create"))
    val independentId = storedMailboxId(server, "independent")

    assertThatJson(creation)
      .when(path("created.independent"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {"independent": {"id": ${Json.toJson(independentId)}}},
        |  "notCreated": {
        |    "first": {
        |      "type": "invalidProperties",
        |      "properties": ["parentId"],
        |      "description": "parentId cannot be resolved because this mailbox creation depends on a cycle"
        |    },
        |    "second": {
        |      "type": "invalidProperties",
        |      "properties": ["parentId"],
        |      "description": "parentId cannot be resolved because this mailbox creation depends on a cycle"
        |    },
        |    "descendant": {
        |      "type": "invalidProperties",
        |      "properties": ["parentId"],
        |      "description": "parentId cannot be resolved because this mailbox creation depends on a cycle"
        |    }
        |  }
        |}""".stripMargin)

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

    val creationResults = execute(request).extract()
      .path[java.util.Map[String, Object]](creationResultsPath("create"))

    assertThatJson(creationResults)
      .when(path("notCreated.parent"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(
        """{
          |  "created": {
          |    "independent": "${json-unit.ignore}"
          |  },
          |  "notCreated": {
          |    "parent": {"type": "invalidArguments"},
          |    "child": {
          |      "type": "invalidProperties",
          |      "properties": ["parentId"],
          |      "description": "parentId references failed mailbox creation '#parent'"
          |    }
          |  }
          |}""".stripMargin)

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

    val creation = execute(request).extract()
      .path[java.util.Map[String, Object]](creationResultsPath("create"))
    val sibling1Id = storedMailboxId(server, "root.sibling1")
    val sibling2Id = storedMailboxId(server, "root.sibling2")

    assertThatJson(creation)
      .when(paths("created.sibling1", "created.sibling2"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {
        |    "root": "$${json-unit.ignore}",
        |    "otherRoot": "$${json-unit.ignore}",
        |    "sibling1": {"id": ${Json.toJson(sibling1Id)}},
        |    "sibling2": {"id": ${Json.toJson(sibling2Id)}}
        |  }
        |}""".stripMargin)
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

    val response = execute(request).extract()
    val oldParentCreation = response.path[java.util.Map[String, Object]](creationResultsPath("createOldParent"))
    val replacement = response.path[java.util.Map[String, Object]](creationResultsPath("reuseParentIdAndCreateChild"))
    val oldParentId = storedMailboxId(server, "old")
    val newParentId = storedMailboxId(server, "new")
    val childId = storedMailboxId(server, "new.child")

    assertThatJson(oldParentCreation)
      .when(path("created.parent"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {"parent": {"id": ${Json.toJson(oldParentId)}}}
        |}""".stripMargin)

    assertThatJson(replacement)
      .when(paths("created.parent", "created.child"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {
        |    "parent": {"id": ${Json.toJson(newParentId)}},
        |    "child": {"id": ${Json.toJson(childId)}}
        |  }
        |}""".stripMargin)
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

    val response = execute(request).extract()
    val replacement = response.path[java.util.Map[String, Object]](creationResultsPath("failReplacement"))
    val laterCreation = response.path[java.util.Map[String, Object]](creationResultsPath("reuseOldParent"))
    val childId = storedMailboxId(server, "old.child")
    val laterId = storedMailboxId(server, "old.later")

    assertThatJson(replacement)
      .when(paths("created.child", "notCreated.parent"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {
        |    "child": {"id": ${Json.toJson(childId)}},
        |    "root": "$${json-unit.ignore}"
        |  },
        |  "notCreated": {
        |    "parent": {"type": "invalidArguments"}
        |  }
        |}""".stripMargin)

    assertThatJson(laterCreation)
      .when(path("created.later"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {"later": {"id": ${Json.toJson(laterId)}}}
        |}""".stripMargin)
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

    val creation = execute(request).extract()
      .path[java.util.Map[String, Object]](creationResultsPath("create"))
    val childId = storedMailboxId(server, "existing.parent.child")

    assertThatJson(creation)
      .when(path("created.child"), `then`(IGNORING_EXTRA_FIELDS))
      .isEqualTo(s"""{
        |  "created": {
        |    "child": {"id": ${Json.toJson(childId)}},
        |    "$creationId": "$${json-unit.ignore}"
        |  }
        |}""".stripMargin)
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

    val response = execute(request).extract()
    val emailCreation = response.path[java.util.Map[String, Object]](responseArgumentsPath("createEmail"))
    val createdEmailId = response.path[String](s"${responseArgumentsPath("createEmail")}.created.shared.id")
    val fetchedEmails = response.path[java.util.List[Object]](s"${responseArgumentsPath("getEmail")}.list")
    val mailboxId = storedMailboxId(server, "mailbox")

    assertThatJson(emailCreation).node("notCreated").isAbsent()
    assertThatJson(fetchedEmails)
      .isEqualTo(s"""[
        |  {
        |    "id": ${Json.toJson(createdEmailId)},
        |    "mailboxIds": {${Json.toJson(mailboxId)}: true}
        |  }
        |]""".stripMargin)
  }
}
