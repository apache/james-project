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
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.modules.MailboxProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}

trait MailboxSetCreationDependencyContract {
  def bobUsername: Username
  def bobAccountId: String

  private def execute(request: String): JsValue = {
    Json.parse(given()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
      .when().post()
      .then().statusCode(200)
      .extract().asString())
  }

  private def responseFor(response: JsValue, callId: String): JsValue =
    (response \ "methodResponses").as[Seq[JsArray]]
      .find(call => call(2).as[String] == callId)
      .getOrElse(throw new AssertionError(s"Missing response for call '$callId': $response"))(1)

  private def storedMailboxId(server: GuiceJamesServer, path: String): String =
    server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId("#private", bobUsername.asString(), path).serialize()

  @ParameterizedTest
  @ValueSource(strings = Array("parent,child,grandchild", "parent,grandchild,child", "child,parent,grandchild",
    "child,grandchild,parent", "grandchild,parent,child", "grandchild,child,parent"))
  def createShouldResolveSameCallHierarchyInAnyOrder(order: String, server: GuiceJamesServer): Unit = {
    val mailboxesByCreationId = Map(
      "parent" ->
        """
          |"parent": {"name": "parent", "parentId": null}""".stripMargin,
      "child" ->
        """
          |"child": {"name": "child", "parentId": "#parent"}""".stripMargin,
      "grandchild" ->
        """
          |"grandchild": {"name": "grandchild", "parentId": "#child"}""".stripMargin)
    val orderedCreates = order.split(",").map(mailboxesByCreationId).mkString(",\n")
    val request = s"""{
      |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [
      |    ["Mailbox/set", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "create": {$orderedCreates}
      |    }, "createHierarchy"],
      |    ["Mailbox/get", {
      |      "accountId": ${Json.toJson(bobAccountId)},
      |      "ids": ["#parent", "#child", "#grandchild"],
      |      "properties": ["id", "parentId"]
      |    }, "getHierarchy"]
      |  ]
      |}""".stripMargin

    val response = execute(request)

    val creationResponse = responseFor(response, "createHierarchy")
    val parentId = storedMailboxId(server, "parent")
    val childId = storedMailboxId(server, "parent.child")
    val grandchildId = storedMailboxId(server, "parent.child.grandchild")
    assertThat((creationResponse \ "notCreated").toOption.isEmpty).isTrue()
    assertThat((creationResponse \ "created" \ "parent" \ "id").as[String]).isEqualTo(parentId)
    assertThat((creationResponse \ "created" \ "child" \ "id").as[String]).isEqualTo(childId)
    assertThat((creationResponse \ "created" \ "grandchild" \ "id").as[String]).isEqualTo(grandchildId)

    val fetchedMailboxes = (responseFor(response, "getHierarchy") \ "list").as[Seq[JsObject]]
    val mailboxesById = fetchedMailboxes.map(mailbox => (mailbox \ "id").as[String] -> mailbox).toMap
    assertThat((mailboxesById(childId) \ "parentId").as[String]).isEqualTo(parentId)
    assertThat((mailboxesById(grandchildId) \ "parentId").as[String]).isEqualTo(childId)
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

    val creationResponse = responseFor(execute(request), "create")

    assertThat((creationResponse \ "notCreated" \ "child" \ "type").as[String]).isEqualTo("invalidProperties")
    assertThat((creationResponse \ "notCreated" \ "child" \ "properties").as[Seq[String]])
      .isEqualTo(Seq("parentId"))
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

    val creationResponse = responseFor(execute(request), "create")

    assertThat((creationResponse \ "notCreated" \ "self" \ "type").as[String]).isEqualTo("invalidProperties")
    assertThat((creationResponse \ "notCreated" \ "self" \ "properties").as[Seq[String]])
      .isEqualTo(Seq("parentId"))
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

    val creationResponse = responseFor(execute(request), "create")

    Seq("first", "second", "descendant").foreach(creationId => {
      val error = creationResponse \ "notCreated" \ creationId
      assertThat((error \ "type").as[String]).isEqualTo("invalidProperties")
      assertThat((error \ "properties").as[Seq[String]]).isEqualTo(Seq("parentId"))
    })
    assertThat((creationResponse \ "created").as[JsObject].keys).isEqualTo(Set("independent"))
    assertThat((creationResponse \ "created" \ "independent" \ "id").as[String])
      .isEqualTo(storedMailboxId(server, "independent"))
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

    val creationResponse = responseFor(execute(request), "create")

    assertThat((creationResponse \ "notCreated" \ "parent" \ "type").as[String]).isEqualTo("invalidArguments")
    assertThat((creationResponse \ "notCreated" \ "child" \ "type").as[String]).isEqualTo("invalidProperties")
    assertThat((creationResponse \ "notCreated" \ "child" \ "properties").as[Seq[String]])
      .isEqualTo(Seq("parentId"))
    assertThat((creationResponse \ "created").as[JsObject].keys).isEqualTo(Set("independent"))
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

    val creationResponse = responseFor(execute(request), "create")

    assertThat((creationResponse \ "notCreated").toOption.isEmpty).isTrue()
    assertThat((creationResponse \ "created").as[JsObject].keys)
      .isEqualTo(Set("root", "otherRoot", "sibling1", "sibling2"))
    assertThat((creationResponse \ "created" \ "sibling1" \ "id").as[String])
      .isEqualTo(storedMailboxId(server, "root.sibling1"))
    assertThat((creationResponse \ "created" \ "sibling2" \ "id").as[String])
      .isEqualTo(storedMailboxId(server, "root.sibling2"))
  }

  @Test
  def createShouldPreferTheSameCallMappingWhenACreationIdIsReused(server: GuiceJamesServer): Unit = {
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
      |    }, "replaceParentAndCreateChild"]
      |  ]
      |}""".stripMargin

    val response = execute(request)

    val replacementResponse = responseFor(response, "replaceParentAndCreateChild")

    assertThat((replacementResponse \ "notCreated").toOption.isEmpty).isTrue()
    assertThat((replacementResponse \ "created" \ "child" \ "id").as[String])
      .isEqualTo(storedMailboxId(server, "new.child"))
    assertThat((replacementResponse \ "created" \ "parent" \ "id").as[String])
      .isEqualTo(storedMailboxId(server, "new"))
  }

  @Test
  def createShouldNotUseAnOldMappingWhenTheSameCallParentFails(server: GuiceJamesServer): Unit = {
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

    val response = execute(request)

    val failedReplacement = responseFor(response, "failReplacement")

    assertThat((failedReplacement \ "notCreated" \ "parent" \ "type").as[String]).isEqualTo("invalidArguments")
    assertThat((failedReplacement \ "notCreated" \ "child" \ "type").as[String]).isEqualTo("invalidProperties")
    assertThat((failedReplacement \ "created").as[JsObject].keys).isEqualTo(Set("root"))

    val laterCreation = responseFor(response, "reuseOldParent")
    assertThat((laterCreation \ "created" \ "later" \ "id").as[String])
      .isEqualTo(storedMailboxId(server, "old.later"))
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

    val creationResponse = responseFor(execute(request), "create")

    assertThat((creationResponse \ "notCreated").toOption.isEmpty).isTrue()
    assertThat((creationResponse \ "created" \ "child" \ "id").as[String])
      .isEqualTo(storedMailboxId(server, "existing.parent.child"))
  }

  @Test
  def createShouldNotInferACycleFromACrossTypeReference(server: GuiceJamesServer): Unit = {
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

    val response = execute(request)

    val emailCreation = responseFor(response, "createEmail")

    assertThat((emailCreation \ "notCreated").toOption.isEmpty).isTrue()
    val createdEmailId = (emailCreation \ "created" \ "shared" \ "id").as[String]
    val fetchedEmail = (responseFor(response, "getEmail") \ "list")(0)
    assertThat((fetchedEmail \ "id").as[String]).isEqualTo(createdEmailId)
    assertThat((fetchedEmail \ "mailboxIds").as[JsObject])
      .isEqualTo(Json.obj(storedMailboxId(server, "mailbox") -> true))
  }
}
