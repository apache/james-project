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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class Attachment {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private AttachmentId attachmentId;
        private byte[] bytes;
        private String type;

        public Builder attachmentId(AttachmentId attachmentId) {
            Preconditions.checkArgument(attachmentId != null);
            this.attachmentId = attachmentId;
            return this;
        }

        public Builder bytes(byte[] bytes) {
            Preconditions.checkArgument(bytes != null);
            this.bytes = bytes;
            return this;
        }

        public Builder type(String type) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(type));
            this.type = type;
            return this;
        }

        public Attachment build() {
            Preconditions.checkState(bytes != null, "'bytes' is mandatory");
            Preconditions.checkState(type != null, "'type' is mandatory");
            AttachmentId builtAttachmentId = attachmentId();
            Preconditions.checkState(builtAttachmentId != null, "'attachmentId' is mandatory");
            return new Attachment(bytes, builtAttachmentId, type, size());
        }

        private AttachmentId attachmentId() {
            if (attachmentId != null) {
                return attachmentId;
            }
            return AttachmentId.random();
        }

        private long size() {
            return bytes.length;
        }
    }

    private final byte[] bytes;
    private final AttachmentId attachmentId;
    private final String type;
    private final long size;

    private Attachment(byte[] bytes, AttachmentId attachmentId, String type, long size) {
        this.bytes = bytes;
        this.attachmentId = attachmentId;
        this.type = type;
        this.size = size;
    }

    public AttachmentId getAttachmentId() {
        return attachmentId;
    }

    public String getType() {
        return type;
    }

    public long getSize() {
        return size;
    }

    public InputStream getStream() throws IOException {
        return new ByteArrayInputStream(bytes);
    }

    /**
     * Be careful the returned array is not a copy of the attachment byte array.
     * Mutating it will mutate the attachment!
     * @return the attachment content
     */
    public byte[] getBytes() {
        return bytes;
    }

    public Blob toBlob() {
        return Blob.builder()
            .id(BlobId.fromBytes(bytes))
            .payload(bytes)
            .contentType(type)
            .build();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Attachment) {
            Attachment other = (Attachment) obj;
            return Objects.equal(attachmentId, other.attachmentId)
                && Arrays.equals(bytes, other.bytes)
                && Objects.equal(type, other.type)
                && Objects.equal(size, other.size);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(attachmentId, bytes, type, size);
    }

    @Override
    public String toString() {
        return MoreObjects
                .toStringHelper(this)
                .add("attachmentId", attachmentId)
                .add("bytes", bytes)
                .add("type", type)
                .add("size", size)
                .toString();
    }
}
