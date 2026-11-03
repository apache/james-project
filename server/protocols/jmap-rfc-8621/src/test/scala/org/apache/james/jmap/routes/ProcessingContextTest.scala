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
import org.apache.james.jmap.core.CapabilityIdentifier.JMAP_CORE
import org.apache.james.jmap.core.Invocation.{Arguments, MethodCallId, MethodName}
import org.apache.james.jmap.core.{ClientId, Id, Invocation, JmapRfc8621Configuration, ServerId}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.{CoreEchoMethod, Method}
import org.apache.james.mailbox.MailboxSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{Nested, Test}
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, spy, times, verify}
import play.api.libs.json.{JsArray, JsObject, Json}

class ProcessingContextTest {
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
  def resolveBackReferencesShouldResolveStringsRegardlessOfMethodAndPropertyNames(): Unit = {
    val context = ProcessingContext(Map(ClientId("asset") -> ServerId("server-asset")), Map.empty)
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      "arbitrary" -> "#asset")), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "arbitrary" -> "server-asset")))))
  }

  @Test
  def resolveBackReferencesShouldResolveCreationReferencesInNestedArraysAndObjects(): Unit = {
    val context = ProcessingContext(Map(ClientId("email") -> ServerId("server-email")), Map.empty)
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      "nested" -> Json.arr(Json.arr("#email"), Json.obj("value" -> "#email")))), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "nested" -> Json.arr(Json.arr("server-email"), Json.obj("value" -> "server-email")))))))
  }

  @Test
  def resolveBackReferencesShouldResolveBothObjectKeysAndTheirNestedValues(): Unit = {
    val context = ProcessingContext(Map(ClientId("mailbox") -> ServerId("server-mailbox")), Map.empty)
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      "#mailbox" -> Json.obj("value" -> "#mailbox"))), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "server-mailbox" -> Json.obj("value" -> "server-mailbox"))))))
  }

  @Test
  def resolveBackReferencesShouldPreserveAnUnknownObjectKeyWhileResolvingItsValue(): Unit = {
    val context = ProcessingContext(Map(ClientId("email") -> ServerId("server-email")), Map.empty)
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      "#unknown" -> "#email")), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "#unknown" -> "server-email")))))
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
      "value" -> "#", "#" -> true)), MethodCallId("echo"))
    val context = ProcessingContext(Map.empty, Map.empty)

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation))
  }

  @Test
  def resolveBackReferencesShouldPreserveMalformedCreationReferences(): Unit = {
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      "value" -> "#with space", "#with space" -> true)), MethodCallId("echo"))
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
      "value" -> reference, reference -> true)), MethodCallId("echo"))
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
      "value" -> "prefix#email")), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation))
  }

  @Test
  def resolveBackReferencesShouldSubstituteAMappedValueOnceEvenIfTheServerIDBeginsWithAHash(): Unit = {
    val context = ProcessingContext(Map(
      ClientId("first") -> ServerId("#second"),
      ClientId("second") -> ServerId("server-id")), Map.empty)
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      "value" -> "#first")), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "value" -> "#second")))))
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
      "created" -> "#email",
      "#ids" -> Json.obj("resultOf" -> "get", "name" -> "Email/get", "path" -> "/list/*/id"))), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "created" -> "new-email",
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
      "value" -> "#email",
      "#list" -> Json.obj("resultOf" -> "get", "name" -> "Email/get", "path" -> "/list"))), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "value" -> "server-email", "list" -> Json.arr())))))
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
      ("#" + creationId) -> Json.obj("nested" -> Json.arr("#" + creationId)))), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "server-id" -> Json.obj("nested" -> Json.arr("server-id")))))))
  }

  @Test
  def resolveBackReferencesShouldPreserveAnUnknown255CharacterCreationIDInKeysAndValues(): Unit = {
    val reference = "#" + "u" * 255
    val context = ProcessingContext(Map(ClientId("known") -> ServerId("server-id")), Map.empty)
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      reference -> Json.arr(reference))), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation)).isEqualTo(Right(invocation))
  }

  @Test
  def resolveBackReferencesShouldRemoveExactlyOneHashBeforeLookingUpACreationID(): Unit = {
    val context = ProcessingContext(Map(
      ClientId("created") -> ServerId("plain-id"),
      ClientId("#created") -> ServerId("hash-id")), Map.empty)
    val invocation = Invocation(MethodName("Core/echo"), Arguments(Json.obj(
      "##created" -> "##created")), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj("hash-id" -> "hash-id")))))
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
      ("#" + creationId) -> Json.obj("resultOf" -> 42, "nested" -> ("#" + creationId)))), MethodCallId("echo"))

    assertThat(context.resolveBackReferences(invocation))
      .isEqualTo(Right(invocation.copy(arguments = Arguments(Json.obj(
        "server-id" -> Json.obj("resultOf" -> 42, "nested" -> "server-id"))))))
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
}
