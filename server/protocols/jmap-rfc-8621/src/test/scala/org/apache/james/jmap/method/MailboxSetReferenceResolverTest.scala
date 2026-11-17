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
import org.apache.james.jmap.method.MailboxSetReferenceResolver.{ResolvedUpdate, TargetResolution, UnresolvedMailboxReferenceException}
import org.apache.james.jmap.routes.ProcessingContext
import org.apache.james.mailbox.inmemory.InMemoryId
import org.apache.james.mailbox.model.TestId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsNull, JsObject, JsString, Json}

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

    results.sortBy(_.errorKey.id.value) match {
      case Seq(TargetResolution(conflictKey, Left(conflict)), independentUpdate) =>
        assertSoftly(softly => {
          softly.assertThat(conflictKey.id.value).isEqualTo("1")
          softly.assertThat(conflict).isExactlyInstanceOf(classOf[IllegalArgumentException])
            .hasMessage("Multiple update keys resolve to the same mailbox: '#mailboxAlias', '1'")
          softly.assertThat(independentUpdate.errorKey.id.value).isEqualTo("3")
          softly.assertThat(independentUpdate.resolution)
            .isEqualTo(Right(ResolvedUpdate(InMemoryId.of(3), MailboxPatchObject(Map("name" -> JsString("success"))))))
        })
      case other => throw new AssertionError(s"Expected one conflicting and one independent update, got $other")
    }
  }

  @Test
  def shouldUseTheSuppliedContextForTheTargetAndParent(): Unit = {
    val input = request(Json.obj("update" -> Json.obj("#child" -> Json.obj("parentId" -> "#parent"))))
    val updatedContext = context.recordCreatedId(ClientId(Id.validate("child").toOption.get), ServerId(Id.validate("4").toOption.get))
      .recordCreatedId(ClientId(Id.validate("parent").toOption.get), ServerId(Id.validate("5").toOption.get))

    val results = MailboxSetReferenceResolver.updates(input, updatedContext, mailboxIdFactory)

    results match {
      case Seq(TargetResolution(errorKey, Right(update))) =>
        assertSoftly(softly => {
          softly.assertThat(errorKey.id.value).isEqualTo("4")
          softly.assertThat(update.mailboxId).isEqualTo(InMemoryId.of(4))
          softly.assertThat(update.patch.value).isEqualTo(Map("parentId" -> JsString("5")))
        })
      case other => throw new AssertionError(s"Expected one resolved update, got $other")
    }
  }

  @Test
  def shouldResolveOnlyParentIdAndPreserveNullAndLiterals(): Unit = {
    val patch = MailboxPatchObject(Map("name" -> Json.toJson("#mailboxAlias"), "parentId" -> JsNull,
      "sharedWith/#mailboxAlias" -> Json.toJson("#mailboxAlias")))

    val resolved = MailboxSetReferenceResolver.patch(patch, context)

    assertThat(resolved).isEqualTo(Right(patch))
  }

  @Test
  def shouldRejectUnknownOrMalformedParentBeforeApplyingThePatch(): Unit = {
    Seq("#missing", "#", "#bad/reference", "#" + "x" * 256).foreach(parent => {
      val patch = MailboxPatchObject(Map("parentId" -> Json.toJson(parent)))

      val result = MailboxSetReferenceResolver.patch(patch, context)

      result match {
        case Left(error) => assertThat(error).as(parent).isInstanceOf(classOf[InvalidUpdateException])
        case Right(resolved) => throw new AssertionError(s"Expected invalid parent '$parent' to fail, got $resolved")
      }
    })
  }

  @Test
  def shouldDeduplicateDestroyAliasesByResolvedString(): Unit = {
    val input = request(Json.obj("destroy" -> Json.arr("#mailboxAlias", "1", "01", "#mailboxAlias", "#missing")))

    val results = MailboxSetReferenceResolver.deletions(input, context, mailboxIdFactory)

    results match {
      case Seq(first, leadingZero, TargetResolution(missingKey, Left(missing))) =>
        assertSoftly(softly => {
          softly.assertThat(first.errorKey.id.value).isEqualTo("1")
          softly.assertThat(leadingZero.errorKey.id.value).isEqualTo("01")
          softly.assertThat(first.resolution).isEqualTo(Right(InMemoryId.of(1)))
          softly.assertThat(leadingZero.resolution).isEqualTo(Right(InMemoryId.of(1)))
          softly.assertThat(missingKey.id.value).isEqualTo("#missing")
          softly.assertThat(missing).isInstanceOf(classOf[UnresolvedMailboxReferenceException])
        })
      case other => throw new AssertionError(s"Expected two resolved destroys and one unresolved destroy, got $other")
    }
  }

  @Test
  def shouldUseTheResolvedKeyWhenBackendParsingFails(): Unit = {
    val invalidContext = context.recordCreatedId(ClientId(Id.validate("mailboxAlias").toOption.get),
      ServerId(Id.validate("not-a-number").toOption.get))
    val input = request(Json.obj("update" -> Json.obj("#mailboxAlias" -> Json.obj("name" -> "alias")),
      "destroy" -> Json.arr("#mailboxAlias", "not-a-number")))

    val updates = MailboxSetReferenceResolver.updates(input, invalidContext, mailboxIdFactory)
    val deletions = MailboxSetReferenceResolver.deletions(input, invalidContext, mailboxIdFactory)

    (updates, deletions) match {
      case (Seq(TargetResolution(updateKey, Left(updateError))), Seq(TargetResolution(destroyKey, Left(destroyError)))) =>
        assertSoftly(softly => {
          softly.assertThat(updateKey.id.value).isEqualTo("not-a-number")
          softly.assertThat(updateError).isInstanceOf(classOf[NumberFormatException])
          softly.assertThat(destroyKey.id.value).isEqualTo("not-a-number")
          softly.assertThat(destroyError).isInstanceOf(classOf[NumberFormatException])
        })
      case other => throw new AssertionError(s"Expected one parse failure for each operation, got $other")
    }
  }

  @Test
  def shouldValidateKnownConflictsBeforeParsingMailboxIds(): Unit = {
    val input = request(Json.obj("update" -> Json.obj("#mailboxAlias" -> Json.obj("name" -> "alias"),
      "1" -> Json.obj("isSubscribed" -> false), "01" -> Json.obj("name" -> "leadingZero"))))

    val validation = MailboxSetReferenceResolver.validateUpdateTargets(input, context)

    validation match {
      case Left(error) =>
        assertThat(error).hasMessage("Multiple update keys resolve to the same mailbox: '#mailboxAlias', '1'")
      case Right(_) => throw new AssertionError("Expected the two aliases resolving to '1' to conflict")
    }
  }

  @Test
  def shouldDeferValidationWhenTheMappingCanBeReplaced(): Unit = {
    val input = request(Json.obj("create" -> Json.obj("mailboxAlias" -> Json.obj("name" -> "replacement")),
      "update" -> Json.obj("#mailboxAlias" -> Json.obj("name" -> "alias"), "1" -> Json.obj("name" -> "literal"))))

    val validation = MailboxSetReferenceResolver.validateUpdateTargets(input, context)

    assertThat(validation).isEqualTo(Right(()))
  }

  @Test
  def shouldAcceptDistinctLiteralStringsThatParseToTheSameMailbox(): Unit = {
    val input = request(Json.obj("update" -> Json.obj("1" -> Json.obj("name" -> "renamed"),
      "01" -> Json.obj("isSubscribed" -> false))))

    val validation = MailboxSetReferenceResolver.validateUpdateTargets(input, context)
    val results = MailboxSetReferenceResolver.updates(input, context, mailboxIdFactory)

    assertSoftly(softly => {
      softly.assertThat(validation).isEqualTo(Right(()))
      softly.assertThat(results.map(result => result.errorKey.id.value -> result.resolution).toMap).isEqualTo(Map(
        "1" -> Right(ResolvedUpdate(InMemoryId.of(1), MailboxPatchObject(Map("name" -> JsString("renamed"))))),
        "01" -> Right(ResolvedUpdate(InMemoryId.of(1), MailboxPatchObject(Map("isSubscribed" -> Json.toJson(false)))))))
      softly.assertThat(results.size).isEqualTo(2)
    })
  }
}
