/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.method

import org.apache.james.jmap.core.{ClientId, Id, ServerId}
import org.apache.james.jmap.json.MailboxSerializer
import org.apache.james.jmap.mail.{InvalidUpdateException, MailboxPatchObject, MailboxSetRequest}
import org.apache.james.jmap.routes.ProcessingContext
import org.apache.james.mailbox.inmemory.InMemoryId
import org.apache.james.mailbox.model.TestId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsNull, JsObject, Json}

class MailboxSetReferenceResolverTest {
  private val mailboxIdFactory = new InMemoryId.Factory
  private val serializer = new MailboxSerializer(new TestId.Factory)
  private val context = ProcessingContext(Map(
    ClientId(Id.validate("mailboxAlias").toOption.get) -> ServerId(Id.validate("1").toOption.get),
    ClientId(Id.validate("child").toOption.get) -> ServerId(Id.validate("3").toOption.get),
    ClientId(Id.validate("parent").toOption.get) -> ServerId(Id.validate("2").toOption.get)), Map.empty)

  private def request(arguments: JsObject): MailboxSetRequest =
    serializer.deserializeMailboxSetRequest(Json.obj("accountId" -> "account") ++ arguments).get

  @Test
  def shouldReturnOneFailureForAConflictingResolvedTarget(): Unit = {
    val input = request(Json.obj("update" -> Json.obj("#mailboxAlias" -> Json.obj("name" -> "alias"),
      "1" -> Json.obj("isSubscribed" -> false), "3" -> Json.obj("name" -> "success"))))
    val results = MailboxSetReferenceResolver.updates(input, context, mailboxIdFactory)
    assertThat(results.size).isEqualTo(2)
    val error = results.find(_._1.id.value == "1").get._2.left.toOption.get
    assertThat(error).isInstanceOf(classOf[IllegalArgumentException])
    assertThat(error.getMessage).isEqualTo("Multiple update keys resolve to the same mailbox: '#mailboxAlias', '1'")
    assertThat(results.find(_._1.id.value == "3").get._2.isRight).isTrue()
  }

  @Test
  def shouldUseTheSuppliedContextForTheTargetAndParent(): Unit = {
    val input = request(Json.obj("update" -> Json.obj("#child" -> Json.obj("parentId" -> "#parent"))))
    val updatedContext = context.recordCreatedId(ClientId(Id.validate("child").toOption.get), ServerId(Id.validate("4").toOption.get))
      .recordCreatedId(ClientId(Id.validate("parent").toOption.get), ServerId(Id.validate("5").toOption.get))
    val (errorKey, resolved) = MailboxSetReferenceResolver.updates(input, updatedContext, mailboxIdFactory).head
    assertThat(errorKey.id.value).isEqualTo("4")
    assertThat(resolved.toOption.get._1.serialize()).isEqualTo("4")
    assertThat(resolved.toOption.get._2.value("parentId")).isEqualTo(Json.toJson("5"))
  }

  @Test
  def shouldResolveOnlyParentIdAndPreserveNullAndLiterals(): Unit = {
    val patch = MailboxPatchObject(Map("name" -> Json.toJson("#mailboxAlias"), "parentId" -> JsNull,
      "sharedWith/#mailboxAlias" -> Json.toJson("#mailboxAlias")))
    assertThat(MailboxSetReferenceResolver.patch(patch, context)).isEqualTo(Right(patch))
  }

  @Test
  def shouldDescribeAnUnknownMaximumLengthUpdateTarget(): Unit = {
    val reference = "#" + "u" * 255
    val input = request(Json.obj("update" -> Json.obj(reference -> Json.obj("name" -> "missing"))))
    val update = MailboxSetReferenceResolver.updates(input, context, mailboxIdFactory).head
    assertThat(update._1.id.value).isEqualTo(reference)
    assertThat(update._2.left.toOption.get).isInstanceOf(classOf[IllegalArgumentException])
    assertThat(update._2.left.toOption.get.getMessage)
      .isEqualTo(s"Unable to resolve mailbox reference '$reference': no successful creation is recorded for creation ID '${reference.substring(1)}'")
  }

  @Test
  def shouldDescribeAnUnknownMaximumLengthDestroyTarget(): Unit = {
    val reference = "#" + "u" * 255
    val input = request(Json.obj("destroy" -> Json.arr(reference)))
    val deletion = MailboxSetReferenceResolver.deletions(input, context, mailboxIdFactory).head
    assertThat(deletion._1.id.value).isEqualTo(reference)
    assertThat(deletion._2.left.toOption.get).isInstanceOf(classOf[IllegalArgumentException])
    assertThat(deletion._2.left.toOption.get.getMessage)
      .isEqualTo(s"Unable to resolve mailbox reference '$reference': no successful creation is recorded for creation ID '${reference.substring(1)}'")
  }

  @Test
  def shouldRejectUnknownOrMalformedParentBeforeApplyingThePatch(): Unit = {
    Seq("#missing", "#", "#bad/reference", "#" + "x" * 256).foreach(parent => {
      val result = MailboxSetReferenceResolver.patch(MailboxPatchObject(Map("parentId" -> Json.toJson(parent))), context)
      assertThat(result.left.toOption.get).isInstanceOf(classOf[InvalidUpdateException])
    })
  }

  @Test
  def shouldDeduplicateDestroyAliasesByResolvedString(): Unit = {
    val input = request(Json.obj("destroy" -> Json.arr("#mailboxAlias", "1", "01", "#mailboxAlias", "#missing")))
    val results = MailboxSetReferenceResolver.deletions(input, context, mailboxIdFactory)
    assertThat(results.map(_._1.id.value)).isEqualTo(Seq("1", "01", "#missing"))
    assertThat(results.filter(_._2.isRight).map(_._2.toOption.get.serialize())).isEqualTo(Seq("1", "1"))
    assertThat(results.last._2.left.toOption.get).isInstanceOf(classOf[MailboxSetReferenceResolver.UnresolvedMailboxReferenceException])
  }

  @Test
  def shouldUseTheResolvedKeyWhenBackendParsingFails(): Unit = {
    val invalidContext = context.recordCreatedId(ClientId(Id.validate("mailboxAlias").toOption.get),
      ServerId(Id.validate("not-a-number").toOption.get))
    val input = request(Json.obj("update" -> Json.obj("#mailboxAlias" -> Json.obj("name" -> "alias")),
      "destroy" -> Json.arr("#mailboxAlias", "not-a-number")))

    val (updateKey, update) = MailboxSetReferenceResolver.updates(input, invalidContext, mailboxIdFactory).head
    assertThat(updateKey.id.value).isEqualTo("not-a-number")
    assertThat(update.left.toOption.get).isInstanceOf(classOf[NumberFormatException])

    val deletions = MailboxSetReferenceResolver.deletions(input, invalidContext, mailboxIdFactory)
    assertThat(deletions.map(_._1.id.value)).isEqualTo(Seq("not-a-number"))
    assertThat(deletions.head._2.left.toOption.get).isInstanceOf(classOf[NumberFormatException])
  }

  @Test
  def shouldValidateKnownConflictsBeforeParsingMailboxIds(): Unit = {
    val input = request(Json.obj("update" -> Json.obj("#mailboxAlias" -> Json.obj("name" -> "alias"),
      "1" -> Json.obj("isSubscribed" -> false), "01" -> Json.obj("name" -> "leadingZero"))))

    val error = MailboxSetReferenceResolver.validateUpdateTargets(input, context).left.toOption.get
    assertThat(error.getMessage).isEqualTo("Multiple update keys resolve to the same mailbox: '#mailboxAlias', '1'")
  }

  @Test
  def shouldDeferValidationWhenTheMappingCanBeReplaced(): Unit = {
    val input = request(Json.obj("create" -> Json.obj("mailboxAlias" -> Json.obj("name" -> "replacement")),
      "update" -> Json.obj("#mailboxAlias" -> Json.obj("name" -> "alias"), "1" -> Json.obj("name" -> "literal"))))

    assertThat(MailboxSetReferenceResolver.validateUpdateTargets(input, context)).isEqualTo(Right(()))
  }

  @Test
  def shouldAcceptDistinctLiteralStringsThatParseToTheSameMailbox(): Unit = {
    val input = request(Json.obj("update" -> Json.obj("1" -> Json.obj("name" -> "renamed"),
      "01" -> Json.obj("isSubscribed" -> false))))

    assertThat(MailboxSetReferenceResolver.validateUpdateTargets(input, context)).isEqualTo(Right(()))
    val results = MailboxSetReferenceResolver.updates(input, context, mailboxIdFactory)
    assertThat(results.map(_._1.id.value).toSet).isEqualTo(Set("1", "01"))
    assertThat(results.forall(_._2.isRight)).isTrue()
  }
}
