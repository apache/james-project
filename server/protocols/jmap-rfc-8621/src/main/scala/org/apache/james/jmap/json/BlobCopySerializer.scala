/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *  http://www.apache.org/licenses/LICENSE-2.0                  *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.json

import org.apache.james.jmap.core.{BlobCopyRequest, BlobCopyResponse, SetError}
import org.apache.james.jmap.mail.{BlobId, BlobIds}
import play.api.libs.json.{JsObject, JsResult, Json, OWrites, Reads, Writes}

class BlobCopySerializer {
  private implicit val blobIdWrites: Writes[BlobId] = Json.valueWrites[BlobId]
  private implicit val blobIdsReads: Reads[BlobIds] = Json.valueReads[BlobIds]

  private implicit val copiedWrites: Writes[Map[BlobId, BlobId]] =
    mapWrites[BlobId, BlobId](_.value.value, blobIdWrites)
  private implicit val notCopiedWrites: Writes[Map[BlobId, SetError]] =
    mapWrites[BlobId, SetError](_.value.value, setErrorWrites)

  private implicit val blobCopyRequestReads: Reads[BlobCopyRequest] = Json.reads[BlobCopyRequest]
  private implicit val blobCopyResponseWrites: OWrites[BlobCopyResponse] = Json.writes[BlobCopyResponse]

  def deserializeBlobCopyRequest(input: JsObject): JsResult[BlobCopyRequest] = Json.fromJson[BlobCopyRequest](input)

  def serializeBlobCopyResponse(response: BlobCopyResponse): JsObject = Json.toJsObject(response)
}
