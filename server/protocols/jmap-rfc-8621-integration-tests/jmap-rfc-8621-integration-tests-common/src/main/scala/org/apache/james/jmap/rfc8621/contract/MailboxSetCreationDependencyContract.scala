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
import play.api.libs.json.{JsArray, JsNull, JsObject, JsValue, Json}

trait MailboxSetCreationDependencyContract {
  def bobUsername: Username
  def bobAccountId: String

  private def mailboxSetCall(create: JsObject, callId: String): JsValue =
    Json.arr("Mailbox/set", Json.obj("accountId" -> bobAccountId, "create" -> create), callId)

  private def execute(calls: JsValue*): JsValue = {
    val request = Json.obj(
      "using" -> Json.arr("urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"),
      "methodCalls" -> JsArray(calls))

    Json.parse(given()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request.toString())
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
      "parent" -> Json.obj("name" -> "parent", "parentId" -> JsNull),
      "child" -> Json.obj("name" -> "child", "parentId" -> "#parent"),
      "grandchild" -> Json.obj("name" -> "grandchild", "parentId" -> "#child"))
    val orderedCreates = JsObject(order.split(",").toSeq.map(id => id -> mailboxesByCreationId(id)))
    val getHierarchy = Json.arr("Mailbox/get", Json.obj(
      "accountId" -> bobAccountId,
      "ids" -> Json.arr("#parent", "#child", "#grandchild"),
      "properties" -> Json.arr("id", "parentId")), "getHierarchy")

    val response = execute(mailboxSetCall(orderedCreates, "createHierarchy"), getHierarchy)

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
    val creates = Json.obj("child" -> Json.obj("name" -> "child", "parentId" -> ("#" + "x" * referenceLength)))

    val creationResponse = responseFor(execute(mailboxSetCall(creates, "create")), "create")

    assertThat((creationResponse \ "notCreated" \ "child" \ "type").as[String]).isEqualTo("invalidProperties")
    assertThat((creationResponse \ "notCreated" \ "child" \ "properties").as[Seq[String]])
      .isEqualTo(Seq("parentId"))
    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString()))
      .doesNotContain("child")
  }

  @Test
  def createShouldRejectSelfReferences(server: GuiceJamesServer): Unit = {
    val creates = Json.obj("self" -> Json.obj("name" -> "self", "parentId" -> "#self"))

    val creationResponse = responseFor(execute(mailboxSetCall(creates, "create")), "create")

    assertThat((creationResponse \ "notCreated" \ "self" \ "type").as[String]).isEqualTo("invalidProperties")
    assertThat((creationResponse \ "notCreated" \ "self" \ "properties").as[Seq[String]])
      .isEqualTo(Seq("parentId"))
    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString()))
      .doesNotContain("self")
  }

  @Test
  def createShouldRejectCyclesAndTheirDescendantsWhileCreatingIndependentMailboxes(server: GuiceJamesServer): Unit = {
    val creates = Json.obj(
      "first" -> Json.obj("name" -> "first", "parentId" -> "#second"),
      "second" -> Json.obj("name" -> "second", "parentId" -> "#first"),
      "descendant" -> Json.obj("name" -> "descendant", "parentId" -> "#first"),
      "independent" -> Json.obj("name" -> "independent"))

    val creationResponse = responseFor(execute(mailboxSetCall(creates, "create")), "create")

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
    val creates = Json.obj(
      "parent" -> Json.obj("name" -> "bad.name"),
      "child" -> Json.obj("name" -> "child", "parentId" -> "#parent"),
      "independent" -> Json.obj("name" -> "independent"))

    val creationResponse = responseFor(execute(mailboxSetCall(creates, "create")), "create")

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
    val creates = Json.obj(
      "sibling1" -> Json.obj("name" -> "sibling1", "parentId" -> "#root"),
      "sibling2" -> Json.obj("name" -> "sibling2", "parentId" -> "#root"),
      "root" -> Json.obj("name" -> "root"),
      "otherRoot" -> Json.obj("name" -> "otherRoot"))

    val creationResponse = responseFor(execute(mailboxSetCall(creates, "create")), "create")

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
    val createOldParent = mailboxSetCall(Json.obj(
      "parent" -> Json.obj("name" -> "old")), "createOldParent")
    val replaceParentAndCreateChild = mailboxSetCall(Json.obj(
      "child" -> Json.obj("name" -> "child", "parentId" -> "#parent"),
      "parent" -> Json.obj("name" -> "new")), "replaceParentAndCreateChild")

    val response = execute(createOldParent, replaceParentAndCreateChild)

    val replacementResponse = responseFor(response, "replaceParentAndCreateChild")
    assertThat((replacementResponse \ "notCreated").toOption.isEmpty).isTrue()
    assertThat((replacementResponse \ "created" \ "child" \ "id").as[String])
      .isEqualTo(storedMailboxId(server, "new.child"))
    assertThat((replacementResponse \ "created" \ "parent" \ "id").as[String])
      .isEqualTo(storedMailboxId(server, "new"))
  }

  @Test
  def createShouldNotUseAnOldMappingWhenTheSameCallParentFails(server: GuiceJamesServer): Unit = {
    val createOldParent = mailboxSetCall(Json.obj(
      "parent" -> Json.obj("name" -> "old")), "createOldParent")
    val failReplacement = mailboxSetCall(Json.obj(
      "child" -> Json.obj("name" -> "child", "parentId" -> "#parent"),
      "parent" -> Json.obj("name" -> "bad.name"),
      "root" -> Json.obj("name" -> "root")), "failReplacement")
    val reuseOldParentInLaterCall = mailboxSetCall(Json.obj(
      "later" -> Json.obj("name" -> "later", "parentId" -> "#parent")), "reuseOldParent")

    val response = execute(createOldParent, failReplacement, reuseOldParentInLaterCall)

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
    val creates = Json.obj(
      "child" -> Json.obj("name" -> "child", "parentId" -> ("#" + creationId)),
      creationId -> Json.obj("name" -> "parent", "parentId" -> existingParentId))

    val creationResponse = responseFor(execute(mailboxSetCall(creates, "create")), "create")

    assertThat((creationResponse \ "notCreated").toOption.isEmpty).isTrue()
    assertThat((creationResponse \ "created" \ "child" \ "id").as[String])
      .isEqualTo(storedMailboxId(server, "existing.parent.child"))
  }

  @Test
  def createShouldNotInferACycleFromACrossTypeReference(server: GuiceJamesServer): Unit = {
    val createMailbox = mailboxSetCall(Json.obj(
      "shared" -> Json.obj("name" -> "mailbox")), "createMailbox")
    val createEmailWithSameCreationId = Json.arr("Email/set", Json.obj(
      "accountId" -> bobAccountId,
      "create" -> Json.obj("shared" -> Json.obj(
        "mailboxIds" -> Json.obj("#shared" -> true),
        "subject" -> "cross-type reference"))), "createEmail")
    val getEmail = Json.arr("Email/get", Json.obj(
      "accountId" -> bobAccountId,
      "ids" -> Json.arr("#shared"),
      "properties" -> Json.arr("id", "mailboxIds")), "getEmail")

    val response = execute(createMailbox, createEmailWithSameCreationId, getEmail)

    val emailCreation = responseFor(response, "createEmail")
    assertThat((emailCreation \ "notCreated").toOption.isEmpty).isTrue()
    val createdEmailId = (emailCreation \ "created" \ "shared" \ "id").as[String]
    val fetchedEmail = (responseFor(response, "getEmail") \ "list")(0)
    assertThat((fetchedEmail \ "id").as[String]).isEqualTo(createdEmailId)
    assertThat((fetchedEmail \ "mailboxIds").as[JsObject])
      .isEqualTo(Json.obj(storedMailboxId(server, "mailbox") -> true))
  }
}
