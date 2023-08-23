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

package org.apache.james.jmap.api.model

import java.io.InputStream
import java.time.Instant

import org.apache.james.blob.api.BlobId
import org.apache.james.jmap.api.model.Size.Size
import org.apache.james.mailbox.model.ContentType

object Upload {

  def from(metaData: UploadMetaData, content: () => InputStream): Upload =
    Upload(uploadId = metaData.uploadId,
      size = metaData.size,
      contentType = metaData.contentType,
      content = content,
      uploadDate = metaData.uploadDate)
}

case class Upload(uploadId: UploadId,
                  size: Size,
                  contentType: ContentType,
                  uploadDate: Instant,
                  content: () => InputStream) {
  def sizeAsLong(): java.lang.Long = size.value
}

case class UploadNotFoundException(uploadId: UploadId) extends RuntimeException(s"Upload not found $uploadId")

object UploadMetaData {
  def from(uploadId: UploadId, contentType: ContentType, size: Long, blobId: BlobId, uploadDate: Instant): UploadMetaData =
    UploadMetaData(uploadId = uploadId,
      contentType = contentType,
      size = Size.sanitizeSize(size),
      blobId = blobId,
      uploadDate = uploadDate)
}

case class UploadMetaData(uploadId: UploadId,
                          contentType: ContentType,
                          size: Size,
                          blobId: BlobId,
                          uploadDate: Instant) {
  def sizeAsLong(): java.lang.Long = size.value
}

