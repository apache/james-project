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
package org.apache.james.jmap.draft.model;

import java.util.Objects;
import java.util.Optional;

import org.apache.james.jmap.api.model.UploadId;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.MessageId;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class BlobId {
    public static final String UPLOAD_PREFIX = "upload-";

    public static BlobId of(UploadId uploadId) {
        return BlobId.of(UPLOAD_PREFIX + uploadId.asString());
    }

    public static BlobId of(MessageId messageId) {
        return BlobId.of(messageId.serialize());
    }

    public static BlobId of(AttachmentId attachmentId) {
        return BlobId.of(attachmentId.getId());
    }

    public static BlobId of(String rawValue) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(rawValue), "'rawValue' is mandatory");
        return new BlobId(rawValue);
    }

    private final String rawValue;
    
    private BlobId(String rawValue) {
        this.rawValue = rawValue;
    }
    
    @JsonValue
    public String getRawValue() {
        return rawValue;
    }

    public Optional<UploadId> asUploadId() {
        if (rawValue.startsWith(UPLOAD_PREFIX)) {
            UploadId uploadId = UploadId.from(rawValue.substring(UPLOAD_PREFIX.length()));
            return Optional.of(uploadId);
        }
        return Optional.empty();
    }
    
    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof BlobId) {
            BlobId other = (BlobId) obj;
            return Objects.equals(this.rawValue, other.rawValue);
        }
        return false;
    }
    
    @Override
    public final int hashCode() {
        return Objects.hashCode(this.rawValue);
    }

    @Override
    public String toString() {
        return "BlobId{" +
            "rawValue='" + rawValue + '\'' +
            '}';
    }
}
