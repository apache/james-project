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

import eu.timepit.refined.auto._
import org.apache.james.jmap.core.{ClientId, ServerId}
import org.apache.james.jmap.json.MailboxSerializer
import org.apache.james.jmap.mail.{InvalidUpdateException, MailboxPatchObject, MailboxSetRequest}
import org.apache.james.jmap.routes.ProcessingContext
import org.apache.james.mailbox.exception.MailboxNotFoundException
import org.apache.james.mailbox.inmemory.InMemoryId
import org.apache.james.mailbox.model.TestId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsNull, JsObject, Json}

class MailboxSetReferenceResolverTest {
  private val mailboxIdFactory = new InMemoryId.Factory
  private val serializer = new MailboxSerializer(new TestId.Factory)
  private val context = ProcessingContext(Map(ClientId("x") -> ServerId("1"), ClientId("p") -> ServerId("2")), Map.empty)

  private def request(arguments: JsObject): MailboxSetRequest =
    serializer.deserializeMailboxSetRequest(Json.obj("accountId" -> "account") ++ arguments).get

  @Test
  def shouldRejectEveryCollidingUpdateBeforeAnyPatchCanBeLost(): Unit = {
    val input = request(Json.obj("update" -> Json.obj("#x" -> Json.obj("name" -> "alias"),
      "1" -> Json.obj("isSubscribed" -> false), "3" -> Json.obj("name" -> "success"))))
    val results = MailboxSetReferenceResolver.updates(input, context, mailboxIdFactory)
    assertThat(results.size).isEqualTo(3)
    Seq("#x", "1").foreach(key => {
      val error = results.find(_._1.id.value == key).get._2.left.toOption.get
      assertThat(error).isInstanceOf(classOf[IllegalArgumentException])
      assertThat(error.getMessage).isEqualTo("Multiple update keys resolve to the same mailbox")
    })
    assertThat(results.find(_._1.id.value == "3").get._2.isRight).isTrue()
  }

  @Test
  def shouldUseTheSuppliedContextAndKeepTheOriginalErrorKey(): Unit = {
    val input = request(Json.obj("update" -> Json.obj("#x" -> Json.obj("parentId" -> "#p"))))
    val updatedContext = context.recordCreatedId(ClientId("x"), ServerId("4"))
      .recordCreatedId(ClientId("p"), ServerId("5"))
    val (original, resolved) = MailboxSetReferenceResolver.updates(input, updatedContext, mailboxIdFactory).head
    assertThat(original.id.value).isEqualTo("#x")
    assertThat(resolved.toOption.get._1.serialize()).isEqualTo("4")
    assertThat(resolved.toOption.get._2.value("parentId")).isEqualTo(Json.toJson("5"))
  }

  @Test
  def shouldResolveOnlyParentIdAndPreserveNullAndLiterals(): Unit = {
    val patch = MailboxPatchObject(Map("name" -> Json.toJson("#x"), "parentId" -> JsNull,
      "sharedWith/#x" -> Json.toJson("#x")))
    assertThat(MailboxSetReferenceResolver.patch(patch, context)).isEqualTo(Right(patch))
  }

  @Test
  def shouldReturnNotFoundForAnUnknownMaximumLengthTarget(): Unit = {
    val reference = "#" + "u" * 255
    val input = request(Json.obj("update" -> Json.obj(reference -> Json.obj("name" -> "missing")),
      "destroy" -> Json.arr(reference)))
    val update = MailboxSetReferenceResolver.updates(input, context, mailboxIdFactory).head
    assertThat(update._1.id.value).isEqualTo(reference)
    assertThat(update._2.left.toOption.get).isInstanceOf(classOf[MailboxNotFoundException])
    val deletion = MailboxSetReferenceResolver.deletions(input, context, mailboxIdFactory).head
    assertThat(deletion._1.head.id.value).isEqualTo(reference)
    assertThat(deletion._2.left.toOption.get).isInstanceOf(classOf[MailboxNotFoundException])
  }

  @Test
  def shouldRejectUnknownOrMalformedParentBeforeApplyingThePatch(): Unit = {
    Seq("#missing", "#", "#bad/reference", "#" + "x" * 256).foreach(parent => {
      val result = MailboxSetReferenceResolver.patch(MailboxPatchObject(Map("parentId" -> Json.toJson(parent))), context)
      assertThat(result.left.toOption.get).isInstanceOf(classOf[InvalidUpdateException])
    })
  }

  @Test
  def shouldDeduplicateDestroyAliasesAndRetainAllOriginalsForErrors(): Unit = {
    val input = request(Json.obj("destroy" -> Json.arr("#x", "1", "#x", "#missing")))
    val results = MailboxSetReferenceResolver.deletions(input, context, mailboxIdFactory)
    assertThat(results.size).isEqualTo(2)
    val aliases = results.find(_._2.isRight).get
    assertThat(aliases._1.map(_.id.value).toSet == Set("#x", "1")).isTrue()
    assertThat(aliases._2.toOption.get.serialize()).isEqualTo("1")
    assertThat(results.find(_._2.isLeft).get._1.head.id.value).isEqualTo("#missing")
  }
  @Test
  def shouldRejectUpdatesWhoseDifferentTokensParseToTheSameMailbox(): Unit = {
    val numericContext = ProcessingContext(Map(ClientId("x") -> ServerId("1")), Map.empty)
    val input = request(Json.obj("update" -> Json.obj("#x" -> Json.obj("name" -> "alias"),
      "01" -> Json.obj("isSubscribed" -> false), "2" -> Json.obj("name" -> "independent"))))
    val results = MailboxSetReferenceResolver.updates(input, numericContext, mailboxIdFactory)
    Seq("#x", "01").foreach(token => {
      assertThat(results.find(_._1.id.value == token).get._2.isLeft).isTrue()
    })
    assertThat(results.find(_._1.id.value == "2").get._2.isRight).isTrue()
  }

  @Test
  def shouldDeduplicateDestroyTokensThatParseToTheSameMailbox(): Unit = {
    val numericContext = ProcessingContext(Map(ClientId("x") -> ServerId("1")), Map.empty)
    val input = request(Json.obj("destroy" -> Json.arr("#x", "01", "1", "2")))
    val results = MailboxSetReferenceResolver.deletions(input, numericContext, mailboxIdFactory)
    assertThat(results.size).isEqualTo(2)
    val originals = results.map(_._1.map(_.id.value).toSet)
    assertThat(originals.contains(Set("#x", "01", "1"))).isTrue()
    assertThat(originals.contains(Set("2"))).isTrue()
  }
  @Test
  def shouldRetainOriginalTokensForFactoryParseFailures(): Unit = {
    val invalidContext = ProcessingContext(Map(ClientId("x") -> ServerId("not-a-number")), Map.empty)
    val input = request(Json.obj("update" -> Json.obj("#x" -> Json.obj("name" -> "alias"),
      "not-a-number" -> Json.obj("name" -> "literal")), "destroy" -> Json.arr("#x", "not-a-number")))
    val updates = MailboxSetReferenceResolver.updates(input, invalidContext, mailboxIdFactory)
    assertThat(updates.map(_._1.id.value).toSet == Set("#x", "not-a-number")).isTrue()
    updates.foreach(result => assertThat(result._2.left.toOption.get).isInstanceOf(classOf[NumberFormatException]))
    val deletions = MailboxSetReferenceResolver.deletions(input, invalidContext, mailboxIdFactory)
    assertThat(deletions.flatMap(_._1.map(_.id.value)).toSet == Set("#x", "not-a-number")).isTrue()
    deletions.foreach(result => assertThat(result._2.left.toOption.get).isInstanceOf(classOf[NumberFormatException]))
  }
}
