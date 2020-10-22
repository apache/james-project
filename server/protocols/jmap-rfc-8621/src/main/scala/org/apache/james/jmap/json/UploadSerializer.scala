package org.apache.james.jmap.json

import org.apache.james.jmap.mail.BlobId
import org.apache.james.jmap.routes.UploadResponse
import org.apache.james.mailbox.model.ContentType
import play.api.libs.json.{JsString, JsValue, Json, Writes}

class UploadSerializer {

  private implicit val blobIdWrites: Writes[BlobId] = Json.valueWrites[BlobId]
  private implicit val contentTypeWrites: Writes[ContentType] = contentType => JsString(contentType.asString())
  private implicit val uploadResponseWrites: Writes[UploadResponse] = Json.writes[UploadResponse]

  def serialize(uploadResponse: UploadResponse): JsValue = Json.toJson(uploadResponse)(uploadResponseWrites)
}
