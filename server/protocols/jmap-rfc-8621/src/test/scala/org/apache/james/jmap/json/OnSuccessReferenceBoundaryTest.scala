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

import java.time.ZonedDateTime

import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{AccountId, Id, SetError, UTCDate, UuidState}
import org.apache.james.jmap.mail.{EmailSubmissionCreationId, EmailSubmissionCreationResponse, EmailSubmissionId, EmailSubmissionSetResponse, MDNSendCreateResponse, MDNSendCreationId, MDNSendResponse}
import org.apache.james.mailbox.model.TestMessageId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.BooleanAssert
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import play.api.libs.json.{JsError, JsObject, JsResult, JsValue, Json}

class OnSuccessReferenceBoundaryTest {
  private val submissionSerializer = new EmailSubmissionSetSerializer(new TestMessageId.Factory)
  private val mdnSerializer = new MDNSerializer(new TestMessageId.Factory)
  private val boundaries = Seq("submissionUpdate", "submissionDestroy", "mdnUpdate")

  @ParameterizedTest
  @ValueSource(ints = Array(254, 255))
  def onSuccessShouldAcceptReferencesUpToTheCreationIdLimit(suffixLength: Int): Unit = {
    val token = "#" + "c" * suffixLength

    boundaries.foreach(boundary => assertThat(parseToken(boundary, token).isSuccess).asInstanceOf[BooleanAssert].as(boundary).isTrue())
  }

  @Test
  def onSuccessShouldRejectReferencesOverTheCreationIdLimit(): Unit = {
    boundaries.foreach(boundary => assertThat(parseToken(boundary, "#" + "c" * 256)).as(boundary).isInstanceOf(classOf[JsError]))
  }

  @ParameterizedTest
  @ValueSource(strings = Array("##", "#created", "##created", "a#b"))
  def onSuccessShouldPreserveExistingLexicalAcceptance(token: String): Unit = {
    boundaries.foreach(boundary => assertThat(parseToken(boundary, token).isSuccess).asInstanceOf[BooleanAssert].as(boundary).isTrue())
  }

  @Test
  def onSuccessShouldExplainAnEmptyCreationIdAtTheFieldPath(): Unit = {
    boundaries.foreach { boundary =>
      val result = parseToken(boundary, "#")
      assertThat(result).as(boundary).isInstanceOf(classOf[JsError])
      val errors = result.asEither.left.toOption.get
      val property = if (boundary == "submissionDestroy") "onSuccessDestroyEmail" else "onSuccessUpdateEmail"

      val expectedPath = if (boundary == "submissionDestroy") s"/$property(0)" else s"/$property/#"
      assertThat(errors.map(_._1.toString).toList).as(boundary).isEqualTo(List(expectedPath))
      assertThat(errors.flatMap(_._2).map(_.message).exists(_.contains("'#' is not a valid creation reference: creation id is empty"))).asInstanceOf[BooleanAssert]
        .as(boundary).isTrue()
    }
  }

  @ParameterizedTest
  @ValueSource(strings = Array("", "bad id", "#bad id", "#a/b", "a/b", "#é"))
  def onSuccessShouldRejectMalformedTokens(token: String): Unit = {
    boundaries.foreach(boundary => assertThat(parseToken(boundary, token)).as(boundary).isInstanceOf(classOf[JsError]))
  }

  @Test
  def onSuccessShouldKeepTheOrdinaryIdLengthLimit(): Unit = {
    boundaries.foreach { boundary =>
      assertThat(parseToken(boundary, "i" * 255).isSuccess).asInstanceOf[BooleanAssert].as(boundary).isTrue()
      assertThat(parseToken(boundary, "i" * 256)).as(boundary).isInstanceOf(classOf[JsError])
    }
  }

  @ParameterizedTest
  @ValueSource(strings = Array("submissionUpdate", "submissionDestroy", "mdnUpdate"))
  def onSuccessShouldRejectTheWrongJsonType(boundary: String): Unit = {
    assertThat(parseField(boundary, Json.toJson(123))).isInstanceOf(classOf[JsError])
  }

  @Test
  def onSuccessDestroyShouldRejectNonStringElements(): Unit = {
    assertThat(parseField("submissionDestroy", Json.arr(123))).isInstanceOf(classOf[JsError])
  }

  @Test
  def submissionUpdateShouldPreserveAMaximumLengthReference(): Unit = {
    val token = "#" + "c" * 255
    val result = submissionSerializer.deserializeEmailSubmissionSetRequest(
      Json.obj("accountId" -> "account", "onSuccessUpdateEmail" -> Json.obj(token -> Json.obj("keywords/test" -> true))))

    assertThat(result.isSuccess).isTrue()
    assertThat(result.get.onSuccessUpdateEmail.get.keys.head.value).isEqualTo(token)
  }

  @Test
  def submissionDestroyShouldPreserveAMaximumLengthReference(): Unit = {
    val token = "#" + "c" * 255
    val result = submissionSerializer.deserializeEmailSubmissionSetRequest(
      Json.obj("accountId" -> "account", "onSuccessDestroyEmail" -> Json.arr(token)))

    assertThat(result.isSuccess).isTrue()
    assertThat(result.get.onSuccessDestroyEmail.get.head.value).isEqualTo(token)
  }

  @Test
  def mdnUpdateShouldPreserveAMaximumLengthReference(): Unit = {
    val token = "#" + "c" * 255
    val result = mdnSerializer.deserializeMDNSendRequest(mdnBase ++
      Json.obj("onSuccessUpdateEmail" -> Json.obj(token -> Json.obj("keywords/test" -> true))))

    assertThat(result.isSuccess).isTrue()
    assertThat(result.get.onSuccessUpdateEmail.get.keys.head.value).isEqualTo(token)
  }

  @ParameterizedTest
  @ValueSource(ints = Array(255, 256))
  def creationMapsShouldKeepTheOrdinaryIdLengthLimit(length: Int): Unit = {
    Seq("c" * length, "#" + "c" * (length - 1)).foreach { key =>
      val submission = submissionSerializer.deserializeEmailSubmissionSetRequest(Json.obj("accountId" -> "account", "create" -> Json.obj(key -> Json.obj())))
      val mdn = mdnSerializer.deserializeMDNSendRequest(mdnBase ++ Json.obj("send" -> Json.obj(key -> Json.obj())))

      assertThat(submission.isSuccess).isEqualTo(length == 255)
      assertThat(mdn.isSuccess).isEqualTo(length == 255)
      if (length == 255) {
        assertThat(submission.get.create.get.keys.head.id.value).isEqualTo(key)
        assertThat(mdn.get.send.keys.head.id.value).isEqualTo(key)
      }
    }
  }

  @Test
  def responseWritersShouldPreserveCreationKeys(): Unit = {
    val key = "c" * 255
    val id = Id.validate(key).toOption.get
    val error = SetError.notFound(SetErrorDescription("unknown email"))
    val submissionId = EmailSubmissionCreationId(id)
    val submission = submissionSerializer.serializeEmailSubmissionSetResponse(EmailSubmissionSetResponse(
      accountId = AccountId(Id.validate("account").toOption.get), newState = UuidState.INSTANCE,
      created = Some(Map(submissionId -> EmailSubmissionCreationResponse(EmailSubmissionId(Id.validate("submission").toOption.get),
        UTCDate(ZonedDateTime.parse("2026-11-03T00:00:00Z"))))),
      notCreated = Some(Map(submissionId -> error))))
    val mdnId = MDNSendCreationId(id)
    val mdn = mdnSerializer.serializeMDNSendResponse(MDNSendResponse(AccountId(Id.validate("account").toOption.get),
      Some(Map(mdnId -> MDNSendCreateResponse(None, None, None, None, None, None, None, None, None))), Some(Map(mdnId -> error))))

    Seq("created", "notCreated").foreach(property => assertThat((submission \ property).as[JsObject].keys.toSet).isEqualTo(Set(key)))
    Seq("sent", "notSent").foreach(property => assertThat((mdn \ property).as[JsObject].keys.toSet).isEqualTo(Set(key)))
  }

  private def parseToken(boundary: String, token: String): JsResult[_] =
    parseField(boundary, if (boundary == "submissionDestroy") Json.arr(token) else Json.obj(token -> Json.obj("keywords/test" -> true)))

  private def parseField(boundary: String, field: JsValue): JsResult[_] = boundary match {
    case "submissionUpdate" => submissionSerializer.deserializeEmailSubmissionSetRequest(Json.obj("accountId" -> "account", "onSuccessUpdateEmail" -> field))
    case "submissionDestroy" => submissionSerializer.deserializeEmailSubmissionSetRequest(Json.obj("accountId" -> "account", "onSuccessDestroyEmail" -> field))
    case "mdnUpdate" => mdnSerializer.deserializeMDNSendRequest(mdnBase ++ Json.obj("onSuccessUpdateEmail" -> field))
  }

  private def mdnBase: JsObject = Json.obj("accountId" -> "account", "identityId" -> "identity", "send" -> Json.obj())
}
