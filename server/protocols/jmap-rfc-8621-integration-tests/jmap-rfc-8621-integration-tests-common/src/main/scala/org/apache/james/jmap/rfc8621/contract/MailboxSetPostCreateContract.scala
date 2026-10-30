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

  private def set(arguments: JsObject): JsValue =
    Json.arr("Mailbox/set", Json.obj("accountId" -> bobAccountId) ++ arguments, "set")

  private def execute(calls: JsValue*): JsValue = Json.parse(given()
    .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .body(Json.obj("using" -> Json.arr("urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"),
      "methodCalls" -> JsArray(calls)).toString())
    .when().post().then().statusCode(200).extract().asString())

  private def result(response: JsValue, index: Int = 0): JsValue = (response \ "methodResponses")(index)(1)

  private def mailboxId(server: GuiceJamesServer, path: String): String =
    server.getProbe(classOf[MailboxProbeImpl]).getMailboxId("#private", bobUsername.asString(), path).serialize()

  @ParameterizedTest
  @ValueSource(ints = Array(1, 255))
  def postCreateShouldUpdateAndDestroyNewMailboxes(length: Int, server: GuiceJamesServer): Unit = {
    val updateId = "u" * length
    val destroyId = "d" * length
    val response = result(execute(set(Json.obj(
      "create" -> Json.obj(updateId -> Json.obj("name" -> "before"), destroyId -> Json.obj("name" -> "deleted")),
      "update" -> Json.obj(("#" + updateId) -> Json.obj("name" -> "after")),
      "destroy" -> Json.arr("#" + destroyId)))))
    val updatedId = (response \ "created" \ updateId \ "id").as[String]
    val destroyedId = (response \ "created" \ destroyId \ "id").as[String]
    assertThat((response \ "updated").as[JsObject].keys == Set(updatedId)).isTrue()
    assertThat((response \ "destroyed").as[Seq[String]] == Seq(destroyedId)).isTrue()
    assertThat(mailboxId(server, "after")).isEqualTo(updatedId)
    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString()))
      .doesNotContain("before", "deleted")
  }

  @Test
  def postCreateShouldResolveParentAndRealTarget(server: GuiceJamesServer): Unit = {
    val initial = result(execute(set(Json.obj("create" -> Json.obj("child" -> Json.obj("name" -> "child"))))))
    val child = (initial \ "created" \ "child" \ "id").as[String]
    val parent = "p" * 255
    val response = result(execute(set(Json.obj("create" -> Json.obj(parent -> Json.obj("name" -> "parent")),
      "update" -> Json.obj(child -> Json.obj("parentId" -> ("#" + parent), "name" -> "renamed"))))))
    assertThat((response \ "notUpdated").toOption.isEmpty).isTrue()
    assertThat(mailboxId(server, "parent.renamed")).isEqualTo(child)
  }

  @Test
  def postCreateShouldUseTheLatestSuccessfulMapping(server: GuiceJamesServer): Unit = {
    val response = execute(
      set(Json.obj("create" -> Json.obj("x" -> Json.obj("name" -> "old"), "p" -> Json.obj("name" -> "oldParent")))),
      set(Json.obj("create" -> Json.obj("x" -> Json.obj("name" -> "new"), "p" -> Json.obj("name" -> "newParent")),
        "update" -> Json.obj("#x" -> Json.obj("name" -> "renamed", "parentId" -> "#p")))),
      Json.arr("Mailbox/get", Json.obj("accountId" -> bobAccountId, "ids" -> Json.arr("#x"),
        "properties" -> Json.arr("id", "parentId")), "get"),
      set(Json.obj("destroy" -> Json.arr("#x"))))
    val newId = (result(response, 1) \ "created" \ "x" \ "id").as[String]
    assertThat((result(response, 1) \ "updated").as[JsObject].keys == Set(newId)).isTrue()
    assertThat(((result(response, 2) \ "list")(0) \ "parentId").as[String])
      .isEqualTo((result(response, 1) \ "created" \ "p" \ "id").as[String])
    assertThat((result(response, 3) \ "destroyed").as[Seq[String]] == Seq(newId)).isTrue()
    assertThat(mailboxId(server, "old")).isEqualTo((result(response) \ "created" \ "x" \ "id").as[String])
    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString()))
      .doesNotContain("new", "newParent.renamed", "oldParent.renamed")
  }

  @Test
  def postCreateShouldDestroyTheLatestReusedMapping(server: GuiceJamesServer): Unit = {
    val response = execute(set(Json.obj("create" -> Json.obj("x" -> Json.obj("name" -> "old")))),
      set(Json.obj("create" -> Json.obj("x" -> Json.obj("name" -> "new")), "destroy" -> Json.arr("#x"))))
    val newId = (result(response, 1) \ "created" \ "x" \ "id").as[String]
    assertThat((result(response, 1) \ "destroyed").as[Seq[String]] == Seq(newId)).isTrue()
    assertThat(mailboxId(server, "old")).isEqualTo((result(response) \ "created" \ "x" \ "id").as[String])
    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString())).doesNotContain("new")
  }

  @Test
  def postCreateShouldKeepThePreviousSuccessfulMappingWhenReplacementFails(server: GuiceJamesServer): Unit = {
    val response = execute(set(Json.obj("create" -> Json.obj("x" -> Json.obj("name" -> "old")))),
      set(Json.obj("create" -> Json.obj("x" -> Json.obj("name" -> "bad.name")),
        "update" -> Json.obj("#x" -> Json.obj("name" -> "renamed")))))
    val oldId = (result(response) \ "created" \ "x" \ "id").as[String]
    assertThat((result(response, 1) \ "notCreated" \ "x" \ "type").as[String]).isEqualTo("invalidArguments")
    assertThat((result(response, 1) \ "updated").as[JsObject].keys == Set(oldId)).isTrue()
    assertThat(mailboxId(server, "renamed")).isEqualTo(oldId)
  }

  @Test
  def postCreateShouldIsolateMissingTargetsAndInvalidParentWithoutPartialUpdate(server: GuiceJamesServer): Unit = {
    val unknown = "#" + "m" * 255
    val response = result(execute(set(Json.obj(
      "create" -> Json.obj("bad" -> Json.obj("name" -> "bad.name"), "good" -> Json.obj("name" -> "good"),
        "other" -> Json.obj("name" -> "other")),
      "update" -> Json.obj("#bad" -> Json.obj("name" -> "badUpdate"), unknown -> Json.obj("name" -> "missing"),
        "#good" -> Json.obj("name" -> "partial", "parentId" -> unknown, "isSubscribed" -> false),
        "#other" -> Json.obj("name" -> "independent")),
      "destroy" -> Json.arr("#bad", unknown)))))
    Seq("#bad", unknown).foreach(id => {
      assertThat((response \ "notUpdated" \ id \ "type").as[String]).isEqualTo("notFound")
      assertThat((response \ "notDestroyed" \ id \ "type").as[String]).isEqualTo("notFound")
    })
    assertThat((response \ "notUpdated" \ "#good" \ "type").as[String]).isEqualTo("invalidArguments")
    assertThat((response \ "notUpdated" \ "#good" \ "properties").as[Seq[String]] == Seq("parentId")).isTrue()
    val goodId = mailboxId(server, "good")
    val get = result(execute(Json.arr("Mailbox/get", Json.obj("accountId" -> bobAccountId,
      "ids" -> Json.arr(goodId), "properties" -> Json.arr("id", "name", "isSubscribed")), "get")))
    assertThat(((get \ "list")(0) \ "isSubscribed").as[Boolean]).isTrue()
    assertThat(mailboxId(server, "independent")).isEqualTo((response \ "created" \ "other" \ "id").as[String])
  }

  @Test
  def postCreateShouldKeepDistinctTargetsWhenACreationIdIsReused(server: GuiceJamesServer): Unit = {
    val initial = result(execute(set(Json.obj("create" -> Json.obj("x" -> Json.obj("name" -> "original"))))))
    val id = (initial \ "created" \ "x" \ "id").as[String]
    val response = execute(Json.arr("Mailbox/get", Json.obj("accountId" -> bobAccountId, "ids" -> Json.arr(id)), "get"),
      set(Json.obj("create" -> Json.obj("x" -> Json.obj("name" -> "unused")),
        "update" -> Json.obj("#x" -> Json.obj("name" -> "alias"), id -> Json.obj("name" -> "real")))))
    // The reused creation id refers to a different mailbox, so both operations must survive.
    assertThat((result(response, 1) \ "updated").as[JsObject].keys.size).isEqualTo(2)
    assertThat(mailboxId(server, "real")).isEqualTo(id)
  }

  @Test
  def postCreateShouldResolveCreationReferencesReturnedByAResultReference(server: GuiceJamesServer): Unit = {
    val response = execute(
      Json.arr("Core/echo", Json.obj("patches" -> Json.obj("#x" -> Json.obj("parentId" -> "#p", "name" -> "after"))), "echo"),
      set(Json.obj("create" -> Json.obj("x" -> Json.obj("name" -> "before"), "p" -> Json.obj("name" -> "parent")),
        "#update" -> Json.obj("resultOf" -> "echo", "name" -> "Core/echo", "path" -> "/patches"))))
    val id = (result(response, 1) \ "created" \ "x" \ "id").as[String]
    assertThat((result(response, 1) \ "updated").as[JsObject].keys == Set(id)).isTrue()
    assertThat(mailboxId(server, "parent.after")).isEqualTo(id)
  }

  @Test
  def postCreateShouldUpdateThenDestroyTheSameTarget(server: GuiceJamesServer): Unit = {
    val response = result(execute(set(Json.obj("create" -> Json.obj("x" -> Json.obj("name" -> "before")),
      "update" -> Json.obj("#x" -> Json.obj("name" -> "after")), "destroy" -> Json.arr("#x", "#x")))))
    val id = (response \ "created" \ "x" \ "id").as[String]
    assertThat((response \ "updated").as[JsObject].keys == Set(id)).isTrue()
    assertThat((response \ "destroyed").as[Seq[String]] == Seq(id)).isTrue()
    assertThat((response \ "notDestroyed").toOption.isEmpty).isTrue()
    assertThat(server.getProbe(classOf[MailboxProbeImpl]).listUserMailboxes(bobUsername.asString())).doesNotContain("before", "after")
  }
  @ParameterizedTest
  @ValueSource(booleans = Array(false, true))
  def postCreateShouldKeepSharedMailboxChecks(supportShares: Boolean, server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[MailboxProbeImpl])
    val parentPath = MailboxPath.forUser(andreUsername, "shared")
    val parent = probe.createMailbox(parentPath).serialize()
    server.getProbe(classOf[ACLProbeImpl]).replaceRights(parentPath, bobUsername.asString(),
      new MailboxACL.Rfc4314Rights(Right.Lookup, Right.CreateMailbox))
    val capabilities = Seq("urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail") ++
      (if (supportShares) Seq("urn:apache:james:params:jmap:mail:shares") else Seq.empty)
    val response = result(Json.parse(given()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(Json.obj("using" -> capabilities, "methodCalls" -> Json.arr(set(Json.obj(
        "create" -> Json.obj("u" -> Json.obj("name" -> "u", "parentId" -> parent),
          "d" -> Json.obj("name" -> "d", "parentId" -> parent)),
        "update" -> Json.obj("#u" -> Json.obj("name" -> "renamed")), "destroy" -> Json.arr("#d"))))).toString())
      .when().post().then().statusCode(200).extract().asString()))
    if (supportShares) {
      assertThat((response \ "created").as[JsObject].keys == Set("u", "d")).isTrue()
      assertThat((response \ "notUpdated" \ "#u" \ "type").as[String]).isEqualTo("forbidden")
      assertThat((response \ "notDestroyed" \ "#d" \ "type").as[String]).isEqualTo("invalidArguments")
      assertThat(probe.listUserMailboxes(andreUsername.asString())).contains("shared.u", "shared.d")
      assertThat(probe.listUserMailboxes(andreUsername.asString())).doesNotContain("shared.renamed")
    } else {
      assertThat((response \ "notCreated").as[JsObject].keys == Set("u", "d")).isTrue()
      assertThat((response \ "notUpdated" \ "#u" \ "type").as[String]).isEqualTo("notFound")
      assertThat((response \ "notDestroyed" \ "#d" \ "type").as[String]).isEqualTo("notFound")
      assertThat(probe.listUserMailboxes(andreUsername.asString())).doesNotContain("shared.u", "shared.d")
    }
  }
}
