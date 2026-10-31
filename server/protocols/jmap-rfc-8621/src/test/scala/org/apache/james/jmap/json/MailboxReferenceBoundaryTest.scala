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

package org.apache.james.jmap.json

import eu.timepit.refined.auto._
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{AccountId, Id, Properties, SetError, UuidState}
import org.apache.james.jmap.mail.{MailboxGetResponse, MailboxSetResponse, NotFound, UnparsedMailboxId}
import org.apache.james.mailbox.model.TestId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import play.api.libs.json.{JsError, JsString, JsSuccess, Json}

class MailboxReferenceBoundaryTest {
  private val serializer = new MailboxSerializer(new TestId.Factory)
  private val reference = "#" + "c" * 255

  @Test
  def destroyShouldAcceptAMaximumLengthCreationReference(): Unit = {
    val result = serializer.deserializeMailboxSetRequest(Json.obj("accountId" -> "account", "destroy" -> Json.arr(reference)))

    assertThat(result.isSuccess).isTrue()
    assertThat(result.get.destroy.get.head.id.value).isEqualTo(reference)
  }

  @Test
  def updateShouldAcceptAMaximumLengthCreationReference(): Unit = {
    val result = serializer.deserializeMailboxSetRequest(Json.obj("accountId" -> "account", "update" -> Json.obj(reference -> Json.obj("name" -> "renamed"))))

    assertThat(result.isSuccess).isTrue()
    assertThat(result.get.update.get.keys.head.id.value).isEqualTo(reference)
  }

  @Test
  def parentIdShouldAcceptAMaximumLengthCreationReference(): Unit = {
    val result = serializer.mailboxCreationRequest.reads(Json.obj("name" -> "child", "parentId" -> reference))

    assertThat(result.isSuccess).isTrue()
    assertThat(result.get.parentId.get.id.value).isEqualTo(reference)
  }

  @Test
  def destroyShouldRejectABareHash(): Unit = {
    assertThat(serializer.deserializeMailboxSetRequest(Json.obj("accountId" -> "account", "destroy" -> Json.arr("#"))))
      .isInstanceOf(classOf[JsError])
  }

  @ParameterizedTest
  @ValueSource(strings = Array("", "#", "#bad id", "#a/b"))
  def setBoundariesShouldRejectMalformedReferences(token: String): Unit = {
    assertThat(serializer.deserializeMailboxSetRequest(Json.obj("accountId" -> "account", "destroy" -> Json.arr(token))))
      .isInstanceOf(classOf[JsError])
    assertThat(serializer.deserializeMailboxSetRequest(Json.obj("accountId" -> "account", "update" -> Json.obj(token -> Json.obj("name" -> "renamed")))))
      .isInstanceOf(classOf[JsError])
    assertThat(serializer.mailboxCreationRequest.reads(Json.obj("name" -> "child", "parentId" -> token)))
      .isInstanceOf(classOf[JsError])
  }

  @Test
  def setBoundariesShouldRejectOversizedTokens(): Unit = {
    Seq("i" * 256, "#" + "c" * 256).foreach(setBoundariesShouldRejectMalformedReferences)
  }

  @Test
  def valueMacrosShouldRoundTripAReferenceAsAJsonString(): Unit = {
    val token = UnparsedMailboxId.validate(reference).toOption.get
    val reads = Json.valueReads[UnparsedMailboxId]
    val writes = Json.valueWrites[UnparsedMailboxId]

    assertThat(writes.writes(token)).isEqualTo(JsString(reference))
    assertThat(reads.reads(writes.writes(token))).isEqualTo(JsSuccess(token))
  }

  @Test
  def shouldAdaptExistingIdConstructors(): Unit = {
    val id = Id.validate("mailbox-id").toOption.get

    assertThat(UnparsedMailboxId(id)).isEqualTo(UnparsedMailboxId.validate("mailbox-id").toOption.get)
    assertThat(UnparsedMailboxId("mailbox-id")).isEqualTo(UnparsedMailboxId(id))
  }

  @ParameterizedTest
  @ValueSource(strings = Array("#created", "##created", "a#b"))
  def adapterShouldPreserveCanonicalTokens(value: String): Unit = {
    val adapted = UnparsedMailboxId(Id.validate(value).toOption.get)
    val validated = UnparsedMailboxId.validate(value).toOption.get

    assertThat(adapted).isEqualTo(validated)
    assertThat(adapted.hashCode()).isEqualTo(validated.hashCode())
  }

  @Test
  def shouldAdaptALegacyBareHashId(): Unit = {
    val token = UnparsedMailboxId(Id.validate("#").toOption.get)

    assertThat(token.id.value).isEqualTo("#")
    assertThat(Json.valueWrites[UnparsedMailboxId].writes(token)).isEqualTo(JsString("#"))
  }

  @ParameterizedTest
  @ValueSource(booleans = Array(false, true))
  def getShouldAcceptABareHashAlongsideAValidId(hashFirst: Boolean): Unit = {
    val ids = if (hashFirst) Seq("#", "1") else Seq("1", "#")
    val result = serializer.deserializeMailboxGetRequest(Json.obj("accountId" -> "account", "ids" -> ids))

    assertThat(result.isSuccess).isTrue()
    assertThat(result.get.ids.get.value.map(_.id.value)).isEqualTo(ids.toList)
  }

  @Test
  def setErrorKeysShouldPreserveTheRequestedReference(): Unit = {
    val token = UnparsedMailboxId.validate(reference).toOption.get
    val error = SetError.notFound(SetErrorDescription("unknown mailbox"))
    val response = MailboxSetResponse(AccountId("account"), None, UuidState.INSTANCE,
      None, None, None, None, Some(Map(token -> error)), Some(Map(token -> error)))
    val json = serializer.serialize(response, Set.empty)

    assertThat((json \ "notUpdated").as[play.api.libs.json.JsObject].keys.toSet).isEqualTo(Set(reference))
    assertThat((json \ "notDestroyed").as[play.api.libs.json.JsObject].keys.toSet).isEqualTo(Set(reference))
  }

  @Test
  def notFoundShouldRemainAnArrayOfStrings(): Unit = {
    val token = UnparsedMailboxId.validate(reference).toOption.get
    val response = MailboxGetResponse(AccountId("account"), UuidState.INSTANCE, Nil, NotFound(Set(token)))
    val json = serializer.serialize(response, Properties("id"), Set.empty)

    assertThat((json \ "notFound").get).isEqualTo(Json.arr(reference))
  }

  @Test
  def getShouldKeepItsExistingIdLengthLimit(): Unit = {
    assertThat(serializer.deserializeMailboxGetRequest(Json.obj("accountId" -> "account", "ids" -> Json.arr(reference))))
      .isInstanceOf(classOf[JsError])
  }
  @Test
  def parentIdShouldPreserveTheExistingValidationErrorDescription(): Unit = {
    val result = serializer.mailboxCreationRequest.reads(Json.obj("name" -> "child", "parentId" -> ""))
    val errors = result.asEither.left.toOption.get

    assertThat(errors.head._2.head.message)
      .isEqualTo("mailboxId does not match Id constraints: " + Id.validate("").left.toOption.get.getMessage)
  }
}
