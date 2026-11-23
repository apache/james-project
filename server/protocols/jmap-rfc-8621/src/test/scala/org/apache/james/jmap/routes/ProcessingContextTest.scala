/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.routes

import eu.timepit.refined.auto._
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodCallId, MethodName}
import org.apache.james.jmap.core.{ClientId, Id, Invocation, JmapRfc8621Configuration, ResponseObject, ServerId}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.{CoreEchoMethod, InvocationWithContext, Method, SameCallCreationTargets}
import org.apache.james.mailbox.MailboxSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{Nested, Test}
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, spy, times, verify}
import play.api.libs.json.{JsArray, JsObject, JsPath, JsValue, Json}
import reactor.core.scala.publisher.SFlux

class ProcessingContextTest {
  @Test
  def resolveBackReferencesShouldStopAChainOfResultReferencesThatDoublesEachTime(): Unit = {
    var context = ProcessingContext(Map.empty, Map.empty)
      .recordInvocation(Invocation(MethodName("Core/echo"), Arguments(Json.obj("p" -> "x" * (1024 * 1024))), MethodCallId("c0")))

    val results = (1 to 5).map { index =>
      val previous = s"c${index - 1}"
      val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
        "#a" -> Json.obj("resultOf" -> previous, "name" -> "Core/echo", "path" -> ""),
        "#b" -> Json.obj("resultOf" -> previous, "name" -> "Core/echo", "path" -> ""))), MethodCallId(s"c$index"))
      val result = context.resolveBackReferences(invocation)
      result.foreach(resolved => context = context.recordInvocation(resolved))
      result
    }

    // 2, 4 then 8 MiB: the third doubling goes past the budget of the request
    assertThat(results.take(2).forall(_.isRight)).isTrue()
    assertThat(results(2).left.toOption.get).isInstanceOf(classOf[InvalidResultReferenceException])
  }

  @Test
  def resolveBackReferencesShouldResolveAnEmailQueryReferenceFromEmailGet(): Unit = {
    val context = ProcessingContext(Map.empty, Map.empty)
      .recordInvocation(Invocation(MethodName("Email/query"), Arguments(Json.obj("ids" -> Json.arr("1", "2"))), MethodCallId("c0")))
    val invocation = Invocation(MethodName("Email/get"), Arguments(Json.obj(
      "#ids" -> Json.obj("resultOf" -> "c0", "name" -> "Email/query", "path" -> "/ids"))), MethodCallId("c1"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj("ids" -> Json.arr("1", "2"))))))
  }

  @Test
  def resolveBackReferencesShouldPreserveAnEmptyArgumentObject(): Unit = {
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj()), MethodCallId("echo"))
    val context = ProcessingContext(Map.empty, Map.empty)

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation))
  }

  @Test
  def resolveBackReferencesShouldPreserveOrdinaryValuesAndNestedEmptyContainers(): Unit = {
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.parse(
      """{
        |  "string": "server-id",
        |  "number": 42,
        |  "boolean": true,
        |  "null": null,
        |  "array": [],
        |  "object": {}
        |}""".stripMargin).as[JsObject]), MethodCallId("echo"))
    val context = ProcessingContext(Map.empty, Map.empty)

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation))
  }

  @Test
  def resolveBackReferencesShouldResolveSearchSnippetEmailIdsAfterRecordingAnEmailCreation(): Unit = {
    val context = ProcessingContext(Map.empty, Map.empty)
      .recordCreatedId(ClientId("email"), ServerId("server-email"))
    val invocation = Invocation(MethodName("SearchSnippet/get"), Arguments(Json.obj(
      "accountId" -> "account",
      "emailIds" -> Json.arr("#email", "existing-email"))), MethodCallId("snippets"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "accountId" -> "account",
        "emailIds" -> Json.arr("server-email", "existing-email"))))))
  }

  @Test
  def resolveBackReferencesShouldResolveConventionalIdLocationsOfAnyMethod(): Unit = {
    val context = ProcessingContext(Map(ClientId("asset") -> ServerId("server-asset")), Map.empty)
    val invocation = Invocation(MethodName("Custom/method"), Arguments(Json.obj(
      "id" -> "#asset",
      "blobId" -> "#asset",
      "ids" -> Json.arr("#asset"),
      "emailIds" -> Json.arr("#asset"),
      "destroy" -> Json.arr("#asset"),
      "update" -> Json.obj("#asset" -> Json.obj("mailboxIds" -> Json.obj("#other" -> true))),
      "create" -> Json.obj("k" -> Json.obj("mailboxIds" -> Json.obj("#asset" -> true))))), MethodCallId("custom"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "id" -> "server-asset",
        "blobId" -> "server-asset",
        "ids" -> Json.arr("server-asset"),
        "emailIds" -> Json.arr("server-asset"),
        "destroy" -> Json.arr("server-asset"),
        "update" -> Json.obj("server-asset" -> Json.obj("mailboxIds" -> Json.obj("#other" -> true))),
        "create" -> Json.obj("k" -> Json.obj("mailboxIds" -> Json.obj("server-asset" -> true))))))))
  }

  @Test
  def resolveBackReferencesShouldNotResolveOutsideIdLocations(): Unit = {
    val context = ProcessingContext(Map(ClientId("asset") -> ServerId("server-asset")), Map.empty)
    val invocation = Invocation(MethodName("Custom/method"), Arguments(Json.obj(
      "subject" -> "#asset",
      "#asset" -> Json.obj("name" -> "#asset"),
      "messageId" -> Json.arr("#asset"),
      "keywords" -> Json.obj("#asset" -> true),
      "create" -> Json.obj("#asset" -> Json.obj("destroy" -> Json.arr("#asset"), "update" -> Json.obj("#asset" -> Json.obj()))))), MethodCallId("custom"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation))
  }

  @Test
  def resolveBackReferencesShouldResolveCreationReferencesInNestedArraysAndObjects(): Unit = {
    val context = ProcessingContext(Map(ClientId("email") -> ServerId("server-email")), Map.empty)
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      "filter" -> Json.obj("conditions" -> Json.arr(
        Json.obj("emailIds" -> Json.arr("#email")),
        Json.obj("conditions" -> Json.arr(Json.obj("emailId" -> "#email"))))))), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "filter" -> Json.obj("conditions" -> Json.arr(
          Json.obj("emailIds" -> Json.arr("server-email")),
          Json.obj("conditions" -> Json.arr(Json.obj("emailId" -> "server-email"))))))))))
  }

  @Test
  def resolveBackReferencesShouldResolveBothObjectKeysAndTheirNestedValues(): Unit = {
    val context = ProcessingContext(Map(ClientId("mailbox") -> ServerId("server-mailbox")), Map.empty)
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      "update" -> Json.obj("#mailbox" -> Json.obj("parentId" -> "#mailbox")))), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "update" -> Json.obj("server-mailbox" -> Json.obj("parentId" -> "server-mailbox")))))))
  }

  @Test
  def resolveBackReferencesShouldPreserveAnUnknownObjectKeyWhileResolvingItsValue(): Unit = {
    val context = ProcessingContext(Map(ClientId("email") -> ServerId("server-email")), Map.empty)
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      "update" -> Json.obj("#unknown" -> Json.obj("emailId" -> "#email")))), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "update" -> Json.obj("#unknown" -> Json.obj("emailId" -> "server-email")))))))
  }

  @Test
  def resolveBackReferencesShouldPreserveUnknownCreationReferences(): Unit = {
    val invocation = Invocation(MethodName("Mailbox/get"), Arguments(Json.obj(
      "ids" -> Json.arr("#unknown"))), MethodCallId("get"))
    val context = ProcessingContext(Map.empty, Map.empty)

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation))
  }

  @Test
  def resolveBackReferencesShouldPreserveABareHashWithoutFailingTheInvocation(): Unit = {
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      "id" -> "#", "update" -> Json.obj("#" -> Json.obj()))), MethodCallId("echo"))
    val context = ProcessingContext(Map.empty, Map.empty)

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation))
  }

  @Test
  def resolveBackReferencesShouldPreserveMalformedCreationReferences(): Unit = {
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      "id" -> "#with space", "update" -> Json.obj("#with space" -> Json.obj()))), MethodCallId("echo"))
    val context = ProcessingContext(Map.empty, Map.empty)

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation))
  }

  @Test
  def resolveBackReferencesShouldPreserveReferencesExceedingTheExistingIDLengthLimit(): Unit = {
    val creationId = "x" * 256
    assertThat(Id.validate(creationId).isLeft).isTrue()
    val reference = "#" + creationId
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      "id" -> reference, "update" -> Json.obj(reference -> Json.obj()))), MethodCallId("echo"))
    val context = ProcessingContext(Map.empty, Map.empty)

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation))
  }

  @Test
  def resolveBackReferencesShouldResolveAReferenceWhoseTotalLengthIs255Characters(): Unit = {
    val creationId = "x" * 254
    val context = ProcessingContext(Map(ClientId(Id.validate(creationId).toOption.get) -> ServerId("server-id")), Map.empty)
    val invocation = Invocation(MethodName("Mailbox/get"), Arguments(Json.obj(
      "ids" -> Json.arr("#" + creationId))), MethodCallId("get"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "ids" -> Json.arr("server-id"))))))
  }

  @Test
  def resolveBackReferencesShouldPreserveAnEmbeddedHashThatDoesNotStartTheValue(): Unit = {
    val context = ProcessingContext(Map(ClientId("email") -> ServerId("server-email")), Map.empty)
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      "id" -> "prefix#email")), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation))
  }

  @Test
  def resolveBackReferencesShouldSubstituteAMappedValueOnceEvenIfTheServerIDBeginsWithAHash(): Unit = {
    val context = ProcessingContext(Map(
      ClientId("first") -> ServerId("#second"),
      ClientId("second") -> ServerId("server-id")), Map.empty)
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      "id" -> "#first")), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "id" -> "#second")))))
  }

  @Test
  def recordCreatedIdShouldKeepThePreviousContextUnchanged(): Unit = {
    val original = ProcessingContext(Map.empty, Map.empty)
    val updated = original.recordCreatedId(ClientId("email"), ServerId("server-email"))
    val invocation = Invocation(MethodName("Email/get"), Arguments(Json.obj(
      "ids" -> Json.arr("#email"))), MethodCallId("get"))

    assertThat(original.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation))
    assertThat(updated.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "ids" -> Json.arr("server-email"))))))
  }

  @Test
  def recordCreatedIdShouldRetainExistingMappingsWhenAddingAnotherCreation(): Unit = {
    val context = ProcessingContext(Map(ClientId("first") -> ServerId("server-first")), Map.empty)
      .recordCreatedId(ClientId("second"), ServerId("server-second"))
    val invocation = Invocation(MethodName("Email/get"), Arguments(Json.obj(
      "ids" -> Json.arr("#first", "#second"))), MethodCallId("get"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "ids" -> Json.arr("server-first", "server-second"))))))
  }

  @Test
  def recordCreatedIdShouldRetainRecordedResponsesWhenAddingACreationID(): Unit = {
    val response = Invocation(MethodName("Email/get"), Arguments(Json.obj(
      "list" -> Json.arr(Json.obj("id" -> "previous-email")))), MethodCallId("get"))
    val context = ProcessingContext(Map.empty, Map.empty)
      .recordInvocation(response)
      .recordCreatedId(ClientId("email"), ServerId("new-email"))
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      "emailId" -> "#email",
      "#ids" -> Json.obj("resultOf" -> "get", "name" -> "Email/get", "path" -> "/list/*/id"))), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "emailId" -> "new-email",
        "ids" -> Json.arr("previous-email"))))))
  }

  @Test
  def recordCreatedIdShouldReplaceAMappingWhenACreationIDIsRecordedAgain(): Unit = {
    val context = ProcessingContext(Map(ClientId("email") -> ServerId("old-email")), Map.empty)
      .recordCreatedId(ClientId("email"), ServerId("new-email"))
    val invocation = Invocation(MethodName("Email/get"), Arguments(Json.obj(
      "ids" -> Json.arr("#email"))), MethodCallId("get"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "ids" -> Json.arr("new-email"))))))
  }

  @Test
  def recordInvocationShouldMakeARecordedResponseAvailableWithoutModifyingThePreviousContext(): Unit = {
    val response = Invocation(MethodName("Email/get"), Arguments(Json.obj(
      "list" -> Json.arr(Json.obj("id" -> "server-email")))), MethodCallId("get"))
    val invocation = Invocation(MethodName("Email/set"), Arguments(Json.obj(
      "#destroy" -> Json.obj("resultOf" -> "get", "name" -> "Email/get", "path" -> "/list/*/id"))), MethodCallId("set"))
    val original = ProcessingContext(Map.empty, Map.empty)
    val updated = original.recordInvocation(response)

    assertThat(original.resolveBackReferences(invocation).isLeft).isTrue()
    assertThat(updated.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "destroy" -> Json.arr("server-email"))))))
  }

  @Test
  def recordInvocationShouldRetainCreationMappingsWhileRecordingAnInvocation(): Unit = {
    val response = Invocation(MethodName("Email/get"), Arguments(Json.obj(
      "list" -> Json.arr())), MethodCallId("get"))
    val context = ProcessingContext(Map(ClientId("email") -> ServerId("server-email")), Map.empty)
      .recordInvocation(response)
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      "id" -> "#email",
      "#list" -> Json.obj("resultOf" -> "get", "name" -> "Email/get", "path" -> "/list"))), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "id" -> "server-email", "list" -> Json.arr())))))
  }

  @Test
  def recordInvocationShouldRetainEarlierInvocationsWhenRecordingAnotherResponse(): Unit = {
    val first = Invocation(MethodName("Email/get"), Arguments(Json.obj("state" -> "first-state")), MethodCallId("first"))
    val second = Invocation(MethodName("Mailbox/get"), Arguments(Json.obj("state" -> "second-state")), MethodCallId("second"))
    val context = ProcessingContext(Map.empty, Map.empty).recordInvocation(first).recordInvocation(second)
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      "#first" -> Json.obj("resultOf" -> "first", "name" -> "Email/get", "path" -> "/state"),
      "#second" -> Json.obj("resultOf" -> "second", "name" -> "Mailbox/get", "path" -> "/state"))), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "first" -> "first-state", "second" -> "second-state")))))
  }

  @Test
  def recordInvocationShouldUseTheLastResponseRecordedForAMethodCallID(): Unit = {
    val first = Invocation(MethodName("Email/get"), Arguments(Json.obj("state" -> "old-state")), MethodCallId("get"))
    val second = Invocation(MethodName("Email/get"), Arguments(Json.obj("state" -> "new-state")), MethodCallId("get"))
    val context = ProcessingContext(Map.empty, Map.empty).recordInvocation(first).recordInvocation(second)
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      "#state" -> Json.obj("resultOf" -> "get", "name" -> "Email/get", "path" -> "/state"))), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "state" -> "new-state")))))
  }

  @Test
  def recordInvocationShouldLeaveStringsInAReferencedResponseUntouched(): Unit = {
    val response = Invocation(MethodName("Email/get"), Arguments(Json.obj("state" -> "#email")), MethodCallId("get"))
    val context = ProcessingContext(Map(ClientId("email") -> ServerId("server-email")), Map.empty).recordInvocation(response)
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      "#state" -> Json.obj("resultOf" -> "get", "name" -> "Email/get", "path" -> "/state"))), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "state" -> "#email")))))
  }

  @Test
  def resolveBackReferencesShouldResolveA255CharacterCreationIDInAnObjectKeyAndNestedValues(): Unit = {
    val creationId = "c" * 255
    val context = ProcessingContext(Map(ClientId(Id.validate(creationId).toOption.get) -> ServerId("server-id")), Map.empty)
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      "update" -> Json.obj(("#" + creationId) -> Json.obj("ids" -> Json.arr("#" + creationId))))), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "update" -> Json.obj("server-id" -> Json.obj("ids" -> Json.arr("server-id"))))))))
  }

  @Test
  def resolveBackReferencesShouldPreserveAnUnknown255CharacterCreationIDInKeysAndValues(): Unit = {
    val reference = "#" + "u" * 255
    val context = ProcessingContext(Map(ClientId("known") -> ServerId("server-id")), Map.empty)
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      "update" -> Json.obj(reference -> Json.obj("ids" -> Json.arr(reference))))), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation)).isEqualTo(Right(invocation))
  }

  @Test
  def resolveBackReferencesShouldRemoveExactlyOneHashBeforeLookingUpACreationID(): Unit = {
    val context = ProcessingContext(Map(
      ClientId("created") -> ServerId("plain-id"),
      ClientId("#created") -> ServerId("hash-id")), Map.empty)
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      "update" -> Json.obj("##created" -> Json.obj("id" -> "##created")))), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "update" -> Json.obj("hash-id" -> Json.obj("id" -> "hash-id")))))))
  }

  @Test
  def resolveBackReferencesShouldPreferAResultReferenceOverA255CharacterCreationIDKey(): Unit = {
    val creationId = "c" * 255
    val response = Invocation(MethodName("Email/get"), Arguments(Json.obj("state" -> "result-state")), MethodCallId("get"))
    val context = ProcessingContext(Map(ClientId(Id.validate(creationId).toOption.get) -> ServerId("server-id")), Map.empty)
      .recordInvocation(response)
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      ("#" + creationId) -> Json.obj("resultOf" -> "get", "name" -> "Email/get", "path" -> "/state"))), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(creationId -> "result-state")))))
  }

  @Test
  def resolveBackReferencesShouldFallBackToA255CharacterCreationIDKeyForAMalformedResultReference(): Unit = {
    val creationId = "c" * 255
    val context = ProcessingContext(Map(ClientId(Id.validate(creationId).toOption.get) -> ServerId("server-id")), Map.empty)
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      "update" -> Json.obj(("#" + creationId) -> Json.obj("resultOf" -> 42, "parentId" -> ("#" + creationId))))), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "update" -> Json.obj("server-id" -> Json.obj("resultOf" -> 42, "parentId" -> "server-id")))))))
  }

  @Test
  def resolveBackReferencesShouldReadEscapedAndNumericObjectKeysFromARecordedResponse(): Unit = {
    val response = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      "a/b" -> Json.obj("01" -> Json.obj("a~b" -> "value"), "1" -> Json.obj("a~b" -> "wrong")))), MethodCallId("previous"))
    val context = ProcessingContext(Map.empty, Map.empty).recordInvocation(response)
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      "#value" -> Json.obj("resultOf" -> "previous", "name" -> "Core/echo", "path" -> "/a~1b/01/a~0b"))), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj("value" -> "value")))))
  }

  @Test
  def resolveBackReferencesShouldRejectANestedInvocationWhenAWildcardElementLacksTheDecodedKey(): Unit = {
    val response = Invocation(MethodName("Core/echo"), Arguments(Json.obj("list" -> Json.arr(
      Json.obj("a/b" -> Json.obj("0" -> "first")),
      Json.obj("a~1b" -> Json.obj("0" -> "encoded"))))), MethodCallId("previous"))
    val context = ProcessingContext(Map.empty, Map.empty).recordInvocation(response)
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      "nested" -> Json.obj("#values" -> Json.obj(
        "resultOf" -> "previous", "name" -> "Core/echo", "path" -> "/list/*/a~1b/0")))), MethodCallId("echo"))

    val result = context.resolveBackReferences(invocation)

    assertThat(result.isLeft).isTrue()
    assertThat(result.left.toOption.get.message).contains("Expected path a/b was missing")
  }

  @Nested
  class ArgumentCollisions {
    @Test
    def resolverShouldRejectNormalAndResultReferenceArguments(): Unit = {
      val previous = Invocation(MethodName("Core/echo"), Arguments(Json.obj("ids" -> Json.arr("x"))), MethodCallId("c0"))
      val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.parse(
        """{"ids":["y"],"#ids":{"resultOf":"c0","name":"Core/echo","path":"/ids"}}""").as[JsObject]), MethodCallId("c1"))

      val result = ProcessingContext(Map.empty, Map(MethodCallId("c0") -> previous)).resolveBackReferences(invocation)

      assertThat(result).isEqualTo(Left(DuplicateArgumentException("ids")))
    }

    @Test
    def resolverShouldRejectEntriesResolvingToTheSameKey(): Unit = {
      val invocation = Invocation(MethodName("Email/set"), Arguments(Json.parse(
        """{"update":{"M1":{"keywords/a":true},"#a":{"keywords/b":true}}}""").as[JsObject]), MethodCallId("set"))

      val result = ProcessingContext(Map(ClientId("a") -> ServerId("M1")), Map.empty)
        .resolveBackReferences(invocation)

      assertThat(result).isEqualTo(Left(AmbiguousArgumentException(JsPath \ "update", "M1")))
    }

    @ParameterizedTest
    @ValueSource(strings = Array(
      """{"ids":["y"],"#ids":{"resultOf":"c0","name":"Core/echo","path":"/ids"}}""",
      """{"#ids":{"resultOf":"c0","name":"Core/echo","path":"/ids"},"ids":["y"]}""",
      """{"ids":["x"],"#ids":{"resultOf":"c0","name":"Core/echo","path":"/ids"}}""",
      """{"ids":null,"#ids":{"resultOf":"c0","name":"Core/echo","path":"/ids"}}""",
      """{"ids":null,"#ids":null}""",
      """{"foo":1,"#foo":{"resultOf":"c0","name":"Core/echo","path":"/ids"}}""",
      """{"ids":["y"],"#ids":{"resultOf":"missing","name":"Core/echo","path":"/ids"}}""",
      """{"ids":["y"],"#ids":{"resultOf":"c0","name":"Core/echo","path":"/missing"}}""",
      """{"ids":["y"],"#ids":{"resultOf":"c0","path":"/ids"}}""",
      """{"#ids":1,"##ids":2}"""))
    def apiShouldRejectCollisionWithoutDispatchAndContinueUsingEarlierResults(arguments: String): Unit = {
      val echo = spy(new CoreEchoMethod)
      val configuration = JmapRfc8621Configuration("http://localhost", "ws://localhost")
      val api = new JMAPApi(Set[Method](echo), Set(JMAP_CORE), configuration)
      val request = ResponseSerializer.deserializeRequestObject(
        s"""{
          |  "using":["urn:ietf:params:jmap:core"],
          |  "methodCalls":[
          |    ["Core/echo",{"ids":["x"]},"c0"],
          |    ["Core/echo",$arguments,"c1"],
          |    ["Core/echo",{"#ids":{"resultOf":"c0","name":"Core/echo","path":"/ids"}},"c2"]
          |  ]
          |}""".stripMargin).get

      val response = ResponseSerializer.serialize(api.process(request, mock(classOf[MailboxSession])).block())
      val responses = (response \ "methodResponses").as[Seq[JsArray]]

      assertThat(responses.size).isEqualTo(3)
      assertThat(responses.head).isEqualTo(Json.arr("Core/echo", Json.obj("ids" -> Json.arr("x")), "c0"))
      assertThat(responses(1).value.head).isEqualTo(Json.toJson("error"))
      assertThat((responses(1).value(1) \ "type").as[String]).isEqualTo("invalidArguments")
      assertThat(responses(1).value(2)).isEqualTo(Json.toJson("c1"))
      assertThat(responses(2)).isEqualTo(Json.arr("Core/echo", Json.obj("ids" -> Json.arr("x")), "c2"))
      verify(echo, times(2)).process(any(), any(), any())
    }

    @ParameterizedTest
    @ValueSource(strings = Array(
      """{"ids":["x"],"label":"kept"}""",
      """{"#ids":null}""",
      """{"ids":1,"##ids":2}""",
      """{"payload":{"foo":1,"#foo":{"literal":true}}}"""))
    def resolverShouldPreserveNonCollidingArgumentsAndNestedLiteralObjects(arguments: String): Unit = {
      val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.parse(arguments).as[JsObject]), MethodCallId("c0"))

      assertThat(ProcessingContext(Map.empty, Map.empty).resolveBackReferences(invocation)).isEqualTo(Right(invocation))
    }

    @Test
    def resolverShouldResolveMultipleNonCollidingArgumentsAndPreserveReferencedObjects(): Unit = {
      val payload = Json.obj("foo" -> 1, "#foo" -> Json.obj("literal" -> true))
      val previous = Invocation(MethodName("Core/echo"), Arguments(Json.obj("payload" -> payload)), MethodCallId("c0"))
      val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.parse(
        """{"#value":{"resultOf":"c0","name":"Core/echo","path":"/payload"},"label":"kept"}""").as[JsObject]), MethodCallId("c1"))

      assertThat(ProcessingContext(Map.empty, Map(MethodCallId("c0") -> previous)).resolveBackReferences(invocation))
        .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj("value" -> payload, "label" -> "kept")))))
    }

    @ParameterizedTest
    @ValueSource(strings = Array(
      """{"resultOf":"missing","name":"Core/echo","path":"/ids"}""",
      """{"resultOf":"c0","name":"Mailbox/get","path":"/ids"}""",
      """{"resultOf":"c0","name":"Core/echo","path":"/missing"}"""))
    def apiShouldKeepInvalidResultReferenceForNonCollidingReferenceErrors(reference: String): Unit = {
      val echo = spy(new CoreEchoMethod)
      val configuration = JmapRfc8621Configuration("http://localhost", "ws://localhost")
      val api = new JMAPApi(Set[Method](echo), Set(JMAP_CORE), configuration)
      val request = ResponseSerializer.deserializeRequestObject(
        s"""{
           |  "using":["urn:ietf:params:jmap:core"],
           |  "methodCalls":[
           |    ["Core/echo",{"ids":["x"]},"c0"],
           |    ["Core/echo",{"#ids":$reference},"c1"]
           |  ]
           |}""".stripMargin).get

      val response = ResponseSerializer.serialize(api.process(request, mock(classOf[MailboxSession])).block())
      val error = (response \ "methodResponses").as[Seq[JsArray]].apply(1)

      assertThat(error.value.head).isEqualTo(Json.toJson("error"))
      assertThat((error.value(1) \ "type").as[String]).isEqualTo("invalidResultReference")
      assertThat(error.value(2)).isEqualTo(Json.toJson("c1"))
      verify(echo, times(1)).process(any(), any(), any())
    }
  }

  @Nested
  class CreatedIds {
    private val configuration = JmapRfc8621Configuration("http://localhost", "ws://localhost")

    private val createMethod = new Method {
      override val methodName: MethodName = MethodName("Test/create")
      override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE)

      override def process(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, session: MailboxSession): SFlux[InvocationWithContext] = {
        val createdContext = invocation.processingContext.recordCreatedId(ClientId("new"), ServerId("new-server"))
        val createdResponse = invocation.copy(processingContext = createdContext)
        val shouldFailImplicitCall = (invocation.invocation.arguments.value \ "failAfterCreate").asOpt[Boolean].contains(true)
        if (shouldFailImplicitCall) {
          SFlux.concat(SFlux.just(createdResponse), SFlux.error[InvocationWithContext](new RuntimeException("Implicit call failed")))
        } else {
          val implicitResponse = invocation.invocation.copy(methodName = MethodName("Test/implicit"))
          SFlux.just(createdResponse, createdResponse.copy(invocation = implicitResponse))
        }
      }
    }

    private val readMethod = new Method with SameCallCreationTargets {
      override val methodName: MethodName = MethodName("Test/read")
      override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE)

      override def process(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, session: MailboxSession): SFlux[InvocationWithContext] =
        SFlux.just(invocation)
    }

    private def process(requestJson: String): ResponseObject = {
      val request = ResponseSerializer.deserializeRequestObject(requestJson).get
      val api = new JMAPApi(Set(new CoreEchoMethod, createMethod, readMethod), Set(JMAP_CORE), configuration)
      api.process(request, mock(classOf[MailboxSession])).block()
    }

    private def argumentsOf(response: ResponseObject, callId: String, methodName: String): JsObject =
      response.methodResponses.find(invocation =>
        invocation.methodCallId.value.value == callId && invocation.methodName.value.value == methodName)
        .map(_.arguments.value)
        .getOrElse(throw new AssertionError(s"Missing response $methodName for call $callId"))

    private def createdIdsOf(response: ResponseObject): JsValue =
      (ResponseSerializer.serialize(response) \ "createdIds").get

    @Test
    def shouldReturnEmptyMapWhenRequested(): Unit = {
      val response = process(
        """{
          |  "using": ["urn:ietf:params:jmap:core"],
          |  "createdIds": {},
          |  "methodCalls": []
          |}""".stripMargin)

      assertThat(createdIdsOf(response)).isEqualTo(Json.obj())
    }

    @Test
    def shouldKeepLatestCreationAcrossMultipleResponsesAndAFinalError(): Unit = {
      val response = process(
        """{
          |  "using": ["urn:ietf:params:jmap:core"],
          |  "createdIds": {"old": "old-server", "new": "obsolete"},
          |  "methodCalls": [
          |    ["Test/create", {}, "create"],
          |    ["Test/read", {"id": "#new"}, "read"],
          |    ["Core/echo", {"#id": {"resultOf": "missing", "name": "Core/echo", "path": "/id"}}, "invalid"]
          |  ]
          |}""".stripMargin)

      assertThat(response.methodResponses.map(_.methodName.value.value))
        .isEqualTo(Seq("Test/create", "Test/implicit", "Test/read", "error"))
      assertThat(argumentsOf(response, "read", "Test/read")).isEqualTo(Json.obj("id" -> "new-server"))
      assertThat((argumentsOf(response, "invalid", "error") \ "type").as[String]).isEqualTo("invalidResultReference")
      assertThat(createdIdsOf(response)).isEqualTo(Json.obj("old" -> "old-server", "new" -> "new-server"))
    }

    @Test
    def shouldKeepCreationsWhenAnImplicitResponseFails(): Unit = {
      val response = process(
        """{
          |  "using": ["urn:ietf:params:jmap:core"],
          |  "createdIds": {"new": "obsolete"},
          |  "methodCalls": [
          |    ["Test/create", {"failAfterCreate": true}, "create"],
          |    ["Test/read", {"id": "#new"}, "read"]
          |  ]
          |}""".stripMargin)

      assertThat((argumentsOf(response, "create", "error") \ "type").as[String]).isEqualTo("serverFail")
      assertThat(argumentsOf(response, "read", "Test/read")).isEqualTo(Json.obj("id" -> "new-server"))
      assertThat(createdIdsOf(response)).isEqualTo(Json.obj("new" -> "new-server"))
    }

    @Test
    def shouldOmitMapWhenNotRequestedEvenAfterCreation(): Unit = {
      val response = process(
        """{
          |  "using": ["urn:ietf:params:jmap:core"],
          |  "methodCalls": [["Test/create", {}, "create"]]
          |}""".stripMargin)

      assertThat(ResponseSerializer.serialize(response).keys.contains("createdIds")).isFalse()
    }

    @Test
    def shouldDeferSameCallTargetsAfterExpandingResultReferences(): Unit = {
      val response = process(
        """{
          |  "using": ["urn:ietf:params:jmap:core"],
          |  "createdIds": {"x": "old-x", "y": "old-y"},
          |  "methodCalls": [
          |    ["Core/echo", {"create": {"x": {}}, "update": {"#x": {}}}, "echo"],
          |    ["Test/read", {
          |      "#create": {"resultOf": "echo", "name": "Core/echo", "path": "/create"},
          |      "#update": {"resultOf": "echo", "name": "Core/echo", "path": "/update"},
          |      "destroy": ["#x", "#y"]
          |    }, "set"]
          |  ]
          |}""".stripMargin)

      assertThat(argumentsOf(response, "set", "Test/read")).isEqualTo(Json.parse(
        """{
          |  "create": {"x": {}},
          |  "update": {"#x": {}},
          |  "destroy": ["#x", "old-y"]
          |}""".stripMargin))
    }

    @Test
    def shouldResolveCreationReferencesFetchedThroughAResultReference(): Unit = {
      val response = process(
        """{
          |  "using": ["urn:ietf:params:jmap:core"],
          |  "methodCalls": [
          |    ["Test/create", {}, "create"],
          |    ["Core/echo", {"ids": ["#new"]}, "echo"],
          |    ["Test/read", {"#ids": {"resultOf": "echo", "name": "Core/echo", "path": "/ids"}}, "read"]
          |  ]
          |}""".stripMargin)

      assertThat(argumentsOf(response, "echo", "Core/echo")).isEqualTo(Json.obj("ids" -> Json.arr("#new")))
      assertThat(argumentsOf(response, "read", "Test/read")).isEqualTo(Json.obj("ids" -> Json.arr("new-server")))
    }

    @Test
    def conventionalIdPathsShouldFollowTheNamingOfTheSpecificationsAtAnyDepth(): Unit = {
      val idPaths = IdPaths.CONVENTIONAL
      val emailCreate = JsPath \ "create" \ "k1" \ "email/create"

      assertThat(idPaths.isIdValue(JsPath \ "mailboxId")).isTrue()
      assertThat(idPaths.isIdValue(JsPath \ "upToId")).isTrue()
      assertThat(idPaths.isIdValue(JsPath \ "anchor")).isTrue()
      assertThat(idPaths.isIdValue(JsPath \ "create" \ "k1" \ "parentId")).isTrue()
      assertThat(idPaths.isIdValue((JsPath \ "send" \ "k1") \ "forEmailId")).isTrue()
      assertThat(idPaths.isIdValue((emailCreate \ "attachments")(0) \ "blobId")).isTrue()
      assertThat(idPaths.isIdValue((JsPath \ "create" \ "k1" \ "blobIds")(0))).isTrue()
      assertThat(idPaths.isIdValue(((JsPath \ "filter" \ "conditions")(0) \ "conditions")(1) \ "inMailbox")).isTrue()
      assertThat(idPaths.isIdValue((JsPath \ "filter" \ "inMailboxOtherThan")(0))).isTrue()
      assertThat(idPaths.isIdValue((JsPath \ "destroy")(0))).isTrue()
      assertThat(idPaths.hasIdKeys(JsPath \ "update")).isTrue()
      assertThat(idPaths.hasIdKeys(emailCreate \ "mailboxIds")).isTrue()
      assertThat(idPaths.hasIdKeys(JsPath \ "onSuccessUpdateEmail" \ "#k1" \ "mailboxIds")).isTrue()
      assertThat(idPaths.hasIdKeys(JsPath \ "create" \ "k1" \ "identityIds")).isTrue()
    }

    @Test
    def conventionalIdPathsShouldExcludeStringsNamedLikeIdsAndFreeFormContent(): Unit = {
      val idPaths = IdPaths.CONVENTIONAL

      assertThat(idPaths.isIdValue(JsPath \ "create" \ "k1" \ "subject")).isFalse()
      assertThat(idPaths.isIdValue((JsPath \ "create" \ "k1" \ "textBody")(0) \ "partId")).isFalse()
      assertThat(idPaths.isIdValue(JsPath \ "create" \ "k1" \ "deviceClientId")).isFalse()
      assertThat(idPaths.isIdValue(JsPath \ "send" \ "k1" \ "originalMessageId")).isFalse()
      assertThat(idPaths.isIdValue((JsPath \ "create" \ "k1" \ "messageId")(0))).isFalse()
      assertThat(idPaths.isIdValue(JsPath \ "create" \ "k1" \ "header:X-Correlation-Id:asText")).isFalse()
      assertThat(idPaths.isIdValue(JsPath \ "send" \ "k1" \ "extensionFields" \ "X-Original-Id")).isFalse()
      assertThat(idPaths.isIdValue((JsPath \ "create" \ "k1" \ "destroy")(0))).isFalse()
      assertThat(idPaths.isIdValue((JsPath \ "onSuccessDestroyEmail")(0))).isFalse()
      assertThat(idPaths.hasIdKeys(JsPath \ "onSuccessUpdateEmail")).isFalse()
      assertThat(idPaths.hasIdKeys(JsPath \ "create" \ "k1" \ "update")).isFalse()
      assertThat(idPaths.hasIdKeys(JsPath \ "create" \ "k1" \ "keywords")).isFalse()
      assertThat(idPaths.hasIdKeys(JsPath \ "create" \ "k1" \ "bodyValues")).isFalse()
    }
  }
}
