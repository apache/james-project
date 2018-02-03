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
package org.apache.james.mailbox.model;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.field.ContentTypeFieldImpl;
import org.apache.james.mime4j.stream.RawField;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.primitives.Bytes;

public class AttachmentId {

    private static final String DEFAULT_MIME_TYPE = "application/octet-stream";

    public static AttachmentId forPayloadAndType(byte[] payload, String contentType) {
        Preconditions.checkNotNull(payload);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(contentType));

        return new AttachmentId(computeRawId(payload, contentType));
    }

    private static String computeRawId(final byte[] payload, final String contentType) {
        return DigestUtils.sha256Hex(
            Bytes.concat(
                asMimeType(contentType).getBytes(StandardCharsets.UTF_8),
                DigestUtils.sha256Hex(payload).getBytes(StandardCharsets.UTF_8)));
    }

    @VisibleForTesting static String asMimeType(String contentType) {
        return Optional.ofNullable(ContentTypeFieldImpl.PARSER
                    .parse(new RawField("ContentType", contentType), DecodeMonitor.SILENT)
                    .getMimeType())
                .orElse(DEFAULT_MIME_TYPE);
    }

    public static AttachmentId from(BlobId blobId) {
        return new AttachmentId(blobId.asString());
    }

    public static AttachmentId from(String id) {
        Preconditions.checkNotNull(id);
        Preconditions.checkArgument(!id.isEmpty());
        return new AttachmentId(id);
    }

    private final String id;

    private AttachmentId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public UUID asUUID() {
        return UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof AttachmentId) {
            AttachmentId other = (AttachmentId) obj;
            return Objects.equal(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return MoreObjects
                .toStringHelper(this)
                .add("id", id)
                .toString();
    }
}
