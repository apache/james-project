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

import org.apache.james.jmap.mail.{BlobId, BlobIds, ErrorField, FinalRecipientField, ForEmailIdField, IncludeOriginalMessageField, MDNDisposition, MDNNotFound, MDNNotParsable, MDNParseRequest, MDNParseResponse, MDNParsed, OriginalMessageIdField, OriginalRecipientField, ReportUAField, SubjectField, TextBodyField}
import play.api.libs.json._

object MDNParseSerializer {
  private implicit val blobIdReads: Reads[BlobId] = Json.valueReads[BlobId]
  private implicit val blobIdsWrites: Format[BlobIds] = Json.valueFormat[BlobIds]
  private implicit val mdnNotFoundWrites: Writes[MDNNotFound] = Json.valueWrites[MDNNotFound]
  private implicit val mdnNotParsableWrites: Writes[MDNNotParsable] = Json.valueWrites[MDNNotParsable]
  private implicit val mdnDispositionWrites: Writes[MDNDisposition] = Json.writes[MDNDisposition]
  private implicit val mdnForEmailIdFieldWrites: Writes[ForEmailIdField] = Json.valueWrites[ForEmailIdField]
  private implicit val mdnSubjectFieldWrites: Writes[SubjectField] = Json.valueWrites[SubjectField]
  private implicit val mdnTextBodyFieldWrites: Writes[TextBodyField] = Json.valueWrites[TextBodyField]
  private implicit val mdnReportUAFieldWrites: Writes[ReportUAField] = Json.valueWrites[ReportUAField]
  private implicit val mdnFinalRecipientFieldWrites: Writes[FinalRecipientField] = Json.valueWrites[FinalRecipientField]
  private implicit val mdnOriginalMessageIdFieldWrites: Writes[OriginalMessageIdField] = Json.valueWrites[OriginalMessageIdField]
  private implicit val mdnOriginalRecipientFieldFieldWrites: Writes[OriginalRecipientField] = Json.valueWrites[OriginalRecipientField]
  private implicit val mdnIncludeOriginalMessageFieldWrites: Writes[IncludeOriginalMessageField] = Json.valueWrites[IncludeOriginalMessageField]
  private implicit val mdnErrorFieldWrites: Writes[ErrorField] = Json.valueWrites[ErrorField]
  private implicit val mdnParsedWrites: Writes[MDNParsed] = Json.writes[MDNParsed]
  private implicit val parsedMapWrites: Writes[Map[BlobId, MDNParsed]] = mapWrites[BlobId, MDNParsed](s => s.value.value, mdnParsedWrites)

  private implicit val mdnParseRequestReads: Reads[MDNParseRequest] = Json.reads[MDNParseRequest]

  def deserializeMDNParseRequest(input: JsValue): JsResult[MDNParseRequest] = Json.fromJson[MDNParseRequest](input)

  private implicit val mdnParseResponseWrites: Writes[MDNParseResponse] = Json.writes[MDNParseResponse]

  def serialize(mdnParseResponse: MDNParseResponse): JsValue = {
    Json.toJson(mdnParseResponse)
  }
}
