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

import eu.timepit.refined
import eu.timepit.refined.refineV
import javax.inject.Inject
import org.apache.james.core.MailAddress
import org.apache.james.jmap.core.Id.IdConstraint
import org.apache.james.jmap.core.{SetError, UuidState}
import org.apache.james.jmap.mail.{DestroyIds, EmailSubmissionAddress, EmailSubmissionCreationId, EmailSubmissionCreationRequest, EmailSubmissionCreationResponse, EmailSubmissionId, EmailSubmissionSetRequest, EmailSubmissionSetResponse, Envelope, ParameterName, ParameterValue, UnparsedMessageId}
import org.apache.james.mailbox.model.MessageId
import play.api.libs.json.{JsError, JsNull, JsObject, JsResult, JsString, JsSuccess, JsValue, Json, Reads, Writes}

import scala.util.Try

class EmailSubmissionSetSerializer @Inject()(messageIdFactory: MessageId.Factory) {
  private implicit val creationIdFormat: Reads[EmailSubmissionCreationId] = Json.valueFormat[EmailSubmissionCreationId]
  private implicit val mapCreationRequestByEmailSubmissionCreationId: Reads[Map[EmailSubmissionCreationId, JsObject]] =
    Reads.mapReads[EmailSubmissionCreationId, JsObject] {string => refineV[IdConstraint](string)
      .fold(e => JsError(s"email submission creationId needs to match id contraints: $e"),
        id => JsSuccess(EmailSubmissionCreationId(id))) }

  private implicit val messageIdReads: Reads[MessageId] = {
    case JsString(serializedMessageId) => Try(JsSuccess(messageIdFactory.fromString(serializedMessageId)))
      .fold(e => JsError(s"Invalid messageId: ${e.getMessage}"), messageId => messageId)
    case _ => JsError("Expecting messageId to be represented by a JsString")
  }
  private implicit val notCreatedWrites: Writes[Map[EmailSubmissionCreationId, SetError]] = mapWrites[EmailSubmissionCreationId, SetError](_.id.value, setErrorWrites)

  private implicit val mailAddressReads: Reads[MailAddress] = {
    case JsString(value) => Try(JsSuccess(new MailAddress(value)))
      .fold(e => JsError(s"Invalid mailAddress: ${e.getMessage}"), mailAddress => mailAddress)
    case _ => JsError("Expecting mailAddress to be represented by a JsString")
  }

  private implicit val emailUpdatesMapReads: Reads[Map[UnparsedMessageId, JsObject]] =
    Reads.mapReads[UnparsedMessageId, JsObject] {string => refineV[IdConstraint](string)
      .fold(e => JsError(s"messageId needs to match id contraints: $e"),
        id => JsSuccess(UnparsedMessageId(id))) }
  private implicit val unparsedMessageIdReads: Reads[UnparsedMessageId] = {
    case JsString(string) => refined.refineV[IdConstraint](string)
      .fold(
        e => JsError(s"messageId does not match Id constraints: $e"),
        id => JsSuccess(UnparsedMessageId(id)))
    case _ => JsError("messageId needs to be represented by a JsString")
  }
  private implicit val unparsedMessageIdWrites: Writes[UnparsedMessageId] = Json.valueWrites[UnparsedMessageId]
  private implicit val destroyIdsReads: Reads[DestroyIds] = Json.valueFormat[DestroyIds]

  private implicit val emailSubmissionSetRequestReads: Reads[EmailSubmissionSetRequest] = Json.reads[EmailSubmissionSetRequest]

  private implicit val emailSubmissionIdWrites: Writes[EmailSubmissionId] = Json.valueWrites[EmailSubmissionId]

  private implicit val parameterNameReads: Reads[ParameterName] = Json.valueReads[ParameterName]
  private implicit val parameterValueReads: Reads[ParameterValue] = Json.valueReads[ParameterValue]
  private implicit val parameterValueOptionReads: Reads[Option[ParameterValue]] = {
    case JsString(value) => JsSuccess(Some(ParameterValue(value)))
    case JsNull => JsSuccess(None)
    case _ => JsError("JsonPath objects are represented by JsonString")
  }

  private implicit val parametersReads: Reads[Map[ParameterName, Option[ParameterValue]]] =
    Reads.mapReads[ParameterName, Option[ParameterValue]](k => JsSuccess(ParameterName(k)))(parameterValueOptionReads)

  private implicit val emailSubmissionAddressReads: Reads[EmailSubmissionAddress] = {
    Json.reads[EmailSubmissionAddress]
  }
  private implicit val envelopeReads: Reads[Envelope] = Json.reads[Envelope]

  implicit val emailSubmissionCreationRequestReads: Reads[EmailSubmissionCreationRequest] = Json.reads[EmailSubmissionCreationRequest]

  private implicit val emailSubmissionCreationResponseWrites: Writes[EmailSubmissionCreationResponse] = Json.writes[EmailSubmissionCreationResponse]

  private implicit val stateWrites: Writes[UuidState] = Json.valueWrites[UuidState]
  private implicit def emailSubmissionSetResponseWrites(implicit emailSubmissionCreationResponseWrites: Writes[EmailSubmissionCreationResponse]): Writes[EmailSubmissionSetResponse] = Json.writes[EmailSubmissionSetResponse]

  private implicit def emailSubmissionMapCreationResponseWrites(implicit emailSubmissionSetCreationResponseWrites: Writes[EmailSubmissionCreationResponse]): Writes[Map[EmailSubmissionCreationId, EmailSubmissionCreationResponse]] =
    mapWrites[EmailSubmissionCreationId, EmailSubmissionCreationResponse](_.id.value, emailSubmissionSetCreationResponseWrites)

  def deserializeEmailSubmissionSetRequest(input: JsValue): JsResult[EmailSubmissionSetRequest] = Json.fromJson[EmailSubmissionSetRequest](input)

  def serializeEmailSubmissionSetResponse(response: EmailSubmissionSetResponse): JsValue = Json.toJson(response)
}
