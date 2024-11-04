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

package org.apache.james.mailbox.opensearch.json;

import java.util.Optional;

import org.apache.james.mailbox.store.search.mime.MimePart;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MimePartDto(@JsonProperty(JsonMessageConstants.Attachment.TEXT_CONTENT) Optional<String> bodyTextContent,
                          @JsonProperty(JsonMessageConstants.Attachment.MEDIA_TYPE) Optional<String> mediaType,
                          @JsonProperty(JsonMessageConstants.Attachment.SUBTYPE) Optional<String> subType,
                          @JsonProperty(JsonMessageConstants.Attachment.FILENAME) Optional<String> fileName,
                          @JsonProperty(JsonMessageConstants.Attachment.FILE_EXTENSION) Optional<String> fileExtension,
                          @JsonProperty(JsonMessageConstants.Attachment.CONTENT_DISPOSITION) Optional<String> contentDisposition) {
    public static MimePartDto from(MimePart mimePart) {
        return new MimePartDto(mimePart.getTextualBody(),
            mimePart.getMediaType(),
            mimePart.getSubType(),
            mimePart.getFileName(),
            mimePart.getFileExtension(),
            mimePart.getContentDisposition());
    }
}

