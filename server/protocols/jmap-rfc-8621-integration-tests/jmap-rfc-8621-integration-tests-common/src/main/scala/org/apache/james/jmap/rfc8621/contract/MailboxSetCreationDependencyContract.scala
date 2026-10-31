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

  private def setCall(create: JsObject, callId: String): JsValue = Json.arr("Mailbox/set",
    Json.obj("accountId" -> bobAccountId, "create" -> create), callId)

  private def execute(calls: JsValue*): JsValue = Json.parse(given()
    .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .body(Json.obj("using" -> Json.arr("urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"),
      "methodCalls" -> JsArray(calls)).toString())
    .when().post().then().statusCode(200).extract().asString())

  private def result(response: JsValue, index: Int = 0): JsValue =
    (response \ "methodResponses")(index)(1)

  private def mailboxId(server: GuiceJamesServer, path: String): String =
    server.getProbe(classOf[MailboxProbeImpl]).getMailboxId("#private", bobUsername.asString(), path).serialize()

  @ParameterizedTest
  @ValueSource(strings = Array("parent,child,grandchild", "parent,grandchild,child", "child,parent,grandchild",
    "child,grandchild,parent", "grandchild,parent,child", "grandchild,child,parent"))
  def createShouldResolveSameCallHierarchyInAnyOrder(order: String, server: GuiceJamesServer): Unit = {
    val entries = Map("parent" -> Json.obj("name" -> "parent", "parentId" -> JsNull),
      "child" -> Json.obj("name" -> "child", "parentId" -> "#parent"),
      "grandchild" -> Json.obj("name" -> "grandchild", "parentId" -> "#child"))
    val response = execute(setCall(JsObject(order.split(",").toSeq.map(id => id -> entries(id))), "create"),
      Json.arr("Mailbox/get", Json.obj("accountId" -> bobAccountId,
        "ids" -> Json.arr("#parent", "#child", "#grandchild"), "properties" -> Json.arr("id", "parentId")), "get"))

    assertThat((result(response) \ "notCreated").toOption.isEmpty).isTrue()
    val parent = mailboxId(server, "parent")
    val child = mailboxId(server, "parent.child")
    val grandchild = mailboxId(server, "parent.child.grandchild")
    assertThat((result(response) \ "created" \ "parent" \ "id").as[String]).isEqualTo(parent)
    assertThat((result(response) \ "created" \ "child" \ "id").as[String]).isEqualTo(child)
    assertThat((result(response) \ "created" \ "grandchild" \ "id").as[String]).isEqualTo(grandchild)
    val mailboxes = (result(response, 1) \ "list").as[Seq[JsObject]].map(obj => (obj \ "id").as[String] -> obj).toMap
    assertThat((mailboxes(child) \ "parentId").as[String]).isEqualTo(parent)
    assertThat((mailboxes(grandchild) \ "parentId").as[String]).isEqualTo(child)
  }

  @Test
  def createShouldIsolateInvalidDependencies(server: GuiceJamesServer): Unit = {
    val response = execute(setCall(Json.obj(
      "unknown" -> Json.obj("name" -> "unknown", "parentId" -> "#missing"),
      "unknown255" -> Json.obj("name" -> "unknown255", "parentId" -> ("#" + "x" * 255)),
      "self" -> Json.obj("name" -> "self", "parentId" -> "#self"),
      "a" -> Json.obj("name" -> "a", "parentId" -> "#b"),
      "b" -> Json.obj("name" -> "b", "parentId" -> "#a"),
      "descendant" -> Json.obj("name" -> "descendant", "parentId" -> "#a"),
      "invalid" -> Json.obj("name" -> "bad.name"),
      "failedChild" -> Json.obj("name" -> "failedChild", "parentId" -> "#invalid"),
      "root" -> Json.obj("name" -> "root"),
      "otherRoot" -> Json.obj("name" -> "otherRoot"),
      "sibling1" -> Json.obj("name" -> "sibling1", "parentId" -> "#root"),
      "sibling2" -> Json.obj("name" -> "sibling2", "parentId" -> "#root")), "create"))
    val errors = (result(response) \ "notCreated").as[JsObject]
    Seq("unknown", "unknown255", "self", "a", "b", "descendant", "failedChild").foreach(id => {
      assertThat((errors \ id \ "type").as[String]).isEqualTo("invalidProperties")
      assertThat((errors \ id \ "properties").as[Seq[String]] == Seq("parentId")).isTrue()
    })
    assertThat((errors \ "invalid" \ "type").as[String]).isEqualTo("invalidArguments")
    assertThat((result(response) \ "created").as[JsObject].keys == Set("root", "otherRoot", "sibling1", "sibling2")).isTrue()
    assertThat((result(response) \ "created" \ "sibling1" \ "id").as[String]).isEqualTo(mailboxId(server, "root.sibling1"))
    assertThat((result(response) \ "created" \ "sibling2" \ "id").as[String]).isEqualTo(mailboxId(server, "root.sibling2"))
    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString()))
      .doesNotContain("unknown", "unknown255", "self", "a", "b", "descendant", "failedChild")
  }

  @Test
  def createShouldPreferTheSameCallMappingWhenACreationIdIsReused(server: GuiceJamesServer): Unit = {
    val response = execute(setCall(Json.obj("parent" -> Json.obj("name" -> "old")), "old"),
      setCall(Json.obj("child" -> Json.obj("name" -> "child", "parentId" -> "#parent"),
        "parent" -> Json.obj("name" -> "new")), "new"))
    assertThat((result(response, 1) \ "notCreated").toOption.isEmpty).isTrue()
    assertThat((result(response, 1) \ "created" \ "child" \ "id").as[String]).isEqualTo(mailboxId(server, "new.child"))
    assertThat((result(response, 1) \ "created" \ "parent" \ "id").as[String]).isEqualTo(mailboxId(server, "new"))
  }

  @Test
  def createShouldNotUseAnOldMappingWhenTheSameCallParentFails(): Unit = {
    val response = execute(setCall(Json.obj("parent" -> Json.obj("name" -> "old")), "old"),
      setCall(Json.obj("child" -> Json.obj("name" -> "child", "parentId" -> "#parent"),
        "parent" -> Json.obj("name" -> "bad.name"), "root" -> Json.obj("name" -> "root")), "new"),
      setCall(Json.obj("later" -> Json.obj("name" -> "later", "parentId" -> "#parent")), "later"))
    assertThat((result(response, 1) \ "notCreated" \ "parent" \ "type").as[String]).isEqualTo("invalidArguments")
    assertThat((result(response, 1) \ "notCreated" \ "child" \ "type").as[String]).isEqualTo("invalidProperties")
    assertThat((result(response, 1) \ "created").as[JsObject].keys == Set("root")).isTrue()
    assertThat((result(response, 2) \ "created" \ "later").toOption.isDefined).isTrue()
  }

  @Test
  def createShouldResolveAMaximumLengthReferenceAndRealParentId(server: GuiceJamesServer): Unit = {
    val parentId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.forUser(bobUsername, "existing")).serialize()
    val creationId = "x" * 255
    val response = execute(setCall(Json.obj("child" -> Json.obj("name" -> "child", "parentId" -> ("#" + creationId)),
      creationId -> Json.obj("name" -> "parent", "parentId" -> parentId)), "create"))
    assertThat((result(response) \ "notCreated").toOption.isEmpty).isTrue()
    assertThat((result(response) \ "created" \ "child" \ "id").as[String]).isEqualTo(mailboxId(server, "existing.parent.child"))
  }
  @Test
  def createShouldNotInferACycleFromACrossTypeReference(server: GuiceJamesServer): Unit = {
    val response = execute(setCall(Json.obj("shared" -> Json.obj("name" -> "mailbox")), "mailbox"),
      Json.arr("Email/set", Json.obj("accountId" -> bobAccountId, "create" -> Json.obj(
        "shared" -> Json.obj("mailboxIds" -> Json.obj("#shared" -> true), "subject" -> "cross-type reference"))), "email"),
      Json.arr("Email/get", Json.obj("accountId" -> bobAccountId, "ids" -> Json.arr("#shared"),
        "properties" -> Json.arr("id", "mailboxIds")), "get"))
    assertThat((result(response, 1) \ "notCreated").toOption.isEmpty).isTrue()
    val emailId = (result(response, 1) \ "created" \ "shared" \ "id").as[String]
    val email = (result(response, 2) \ "list")(0)
    assertThat((email \ "id").as[String]).isEqualTo(emailId)
    assertThat((email \ "mailboxIds").as[JsObject]).isEqualTo(Json.obj(mailboxId(server, "mailbox") -> true))
  }

}
