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

import jakarta.inject.Inject
import org.apache.james.jmap.core.{Id, SetError}
import org.apache.james.jmap.mail.{BlobId, BlobIds, ErrorField, ExtensionFieldName, ExtensionFieldValue, FinalRecipientField, ForEmailIdField, IncludeOriginalMessageField, MDNDisposition, MDNGatewayField, MDNNotFound, MDNNotParsable, MDNParseRequest, MDNParseResponse, MDNParsed, MDNSendCreateRequest, MDNSendCreateResponse, MDNSendCreationId, MDNSendRequest, MDNSendResponse, OriginalMessageIdField, OriginalRecipientField, ReportUAField, SubjectField, TextBodyField, UnparsedIdentityId}
import org.apache.james.mailbox.model.MessageId
import play.api.libs.json._

import scala.util.Try

class MDNSerializer @Inject()(messageIdFactory: MessageId.Factory) {

  private implicit val messageIdReads: Reads[MessageId] = {
    case JsString(serializedMessageId) => Try(JsSuccess(messageIdFactory.fromString(serializedMessageId)))
      .fold(_ => JsError("Invalid messageId"), messageId => messageId)
    case _ => JsError("Expecting messageId to be represented by a JsString")
  }
  private implicit val blobIdReads: Reads[BlobId] = Json.valueReads[BlobId]
  private implicit val blobIdsWrites: Format[BlobIds] = Json.valueFormat[BlobIds]
  private implicit val mdnNotFoundWrites: Writes[MDNNotFound] = Json.valueWrites[MDNNotFound]
  private implicit val mdnNotParsableWrites: Writes[MDNNotParsable] = Json.valueWrites[MDNNotParsable]
  private implicit val messageIdWrites: Writes[MessageId] = id => JsString(id.serialize())
  private implicit val forEmailIdFormat: Format[ForEmailIdField] = Json.valueFormat[ForEmailIdField]
  private implicit val subjectFieldFormat: Format[SubjectField] = Json.valueFormat[SubjectField]
  private implicit val textBodyFieldFormat: Format[TextBodyField] = Json.valueFormat[TextBodyField]
  private implicit val reportUAFieldFormat: Format[ReportUAField] = Json.valueFormat[ReportUAField]
  private implicit val finalRecipientFieldFormat: Format[FinalRecipientField] = Json.valueFormat[FinalRecipientField]
  private implicit val originalMessageIdFieldFormat: Format[OriginalMessageIdField] = Json.valueFormat[OriginalMessageIdField]
  private implicit val originalRecipientFieldFormat: Format[OriginalRecipientField] = Json.valueFormat[OriginalRecipientField]
  private implicit val includeOriginalMessageFieldFormat: Format[IncludeOriginalMessageField] = Json.valueFormat[IncludeOriginalMessageField]
  private implicit val mdnGatewayFieldFormat: Format[MDNGatewayField] = Json.valueFormat[MDNGatewayField]
  private implicit val mdnDispositionFormat: Format[MDNDisposition] = Json.format[MDNDisposition]
  private implicit val identityIdFormat: Format[UnparsedIdentityId] = Json.valueFormat[UnparsedIdentityId]
  private implicit val mdnErrorFieldReads: Reads[ErrorField] = Json.reads[ErrorField]
  private implicit val mdnErrorFieldWrites: Writes[ErrorField] = Json.valueWrites[ErrorField]
  private implicit val extensionFieldNameFormat: Format[ExtensionFieldName] = Json.valueFormat[ExtensionFieldName]
  private implicit val extensionFieldValueFormat: Format[ExtensionFieldValue] = Json.valueFormat[ExtensionFieldValue]
  private implicit val mdnParsedWrites: Writes[MDNParsed] = Json.writes[MDNParsed]
  private implicit val parsedMapWrites: Writes[Map[BlobId, MDNParsed]] = mapWrites[BlobId, MDNParsed](s => s.value.value, mdnParsedWrites)
  private implicit val mdnParseRequestReads: Reads[MDNParseRequest] = Json.reads[MDNParseRequest]

  private implicit val mapMDNSendIDAndMDNReads: Reads[Map[MDNSendCreationId, JsObject]] =
    Reads.mapReads[MDNSendCreationId, JsObject] {
      s => Id.validate(s).fold(e => JsError(e.getMessage), partId => JsSuccess(MDNSendCreationId(partId)))
    }

  private implicit val mapExtensionFieldRead: Reads[Map[ExtensionFieldName, ExtensionFieldValue]] =
    Reads.mapReads[ExtensionFieldName, ExtensionFieldValue] { s => JsSuccess(ExtensionFieldName(s)) }

  private implicit val mdnWrites: Writes[MDNSendCreateRequest] = Json.writes[MDNSendCreateRequest]
  private implicit val mdnObjectWrites: OWrites[MDNSendCreateRequest] = Json.writes[MDNSendCreateRequest]
  private implicit val mdnRequestReads: Reads[MDNSendCreateRequest] = Json.reads[MDNSendCreateRequest]
  private implicit val mdnSendRequestReads: Reads[MDNSendRequest] = Json.reads[MDNSendRequest]
  private implicit val setErrorWrites: Writes[SetError] = Json.writes[SetError]
  private implicit val mdnNotSentMapWrites: Writes[Map[MDNSendCreationId, SetError]] =
    mapWrites[MDNSendCreationId, SetError](mdnSendId => mdnSendId.id.value, setErrorWrites)
  private implicit val mdnResponseWrites: Writes[MDNSendCreateResponse] = Json.writes[MDNSendCreateResponse]
  private implicit val mdnResponseObjectWrites: OWrites[MDNSendCreateResponse] = Json.writes[MDNSendCreateResponse]
  private implicit val mdnSentMapWrites: Writes[Map[MDNSendCreationId, MDNSendCreateResponse]] =
    mapWrites[MDNSendCreationId, MDNSendCreateResponse](mdnSendId => mdnSendId.id.value, mdnResponseWrites)
  private implicit val mdnSendResponseWrites: Writes[MDNSendResponse] = Json.writes[MDNSendResponse]
  private implicit val mdnParseResponseWrites: Writes[MDNParseResponse] = Json.writes[MDNParseResponse]

  def deserializeMDNSendRequest(input: JsValue): JsResult[MDNSendRequest] = Json.fromJson[MDNSendRequest](input)

  def deserializeMDNSendCreateRequest(input: JsValue): JsResult[MDNSendCreateRequest] = Json.fromJson[MDNSendCreateRequest](input)

  def serializeMDNSendResponse(mdnSendResponse: MDNSendResponse): JsValue = Json.toJson(mdnSendResponse)

  def serializeMDNResponse(mdnResponse: MDNSendCreateResponse): JsValue = Json.toJson(mdnResponse)

  def deserializeMDNParseRequest(input: JsValue): JsResult[MDNParseRequest] = Json.fromJson[MDNParseRequest](input)

  def serializeMDNParseResponse(mdnParseResponse: MDNParseResponse): JsValue = Json.toJson(mdnParseResponse)
}
