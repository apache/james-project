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

package org.apache.james.blob.mail;

import java.util.Map;
import java.util.Objects;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobPartsId;
import org.apache.james.blob.api.BlobType;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

public class MimeMessagePartsId implements BlobPartsId {
    @FunctionalInterface
    public interface RequireHeaderBlobId {
        RequireBodyBlobId headerBlobId(BlobId headerBlobId);
    }

    @FunctionalInterface
    public interface RequireBodyBlobId {
        Builder bodyBlobId(BlobId bodyBlobId);
    }

    public static class Builder {
        private final BlobId headerBlobId;
        private final BlobId bodyBlobId;

        private Builder(BlobId headerBlobId, BlobId bodyBlobId) {
            Preconditions.checkNotNull(headerBlobId, "'headerBlobId' should not be null");
            Preconditions.checkNotNull(bodyBlobId, "'bodyBlobId' should not be null");

            this.headerBlobId = headerBlobId;
            this.bodyBlobId = bodyBlobId;
        }

        public MimeMessagePartsId build() {
            return new MimeMessagePartsId(headerBlobId, bodyBlobId);
        }
    }

    public static RequireHeaderBlobId builder() {
        return headerBlobId -> bodyBlobId -> new Builder(headerBlobId, bodyBlobId);
    }

    public static class Factory implements BlobPartsId.Factory<MimeMessagePartsId> {
        @Override
        public MimeMessagePartsId generate(Map<BlobType, BlobId> map) {
            Preconditions.checkArgument(map.containsKey(HEADER_BLOB_TYPE), "Expecting 'mailHeader' blobId to be specified");
            Preconditions.checkArgument(map.containsKey(BODY_BLOB_TYPE), "Expecting 'mailBody' blobId to be specified");
            Preconditions.checkArgument(map.size() == 2, "blobId other than 'mailHeader' or 'mailBody' are not supported");

            return builder()
                .headerBlobId(map.get(HEADER_BLOB_TYPE))
                .bodyBlobId(map.get(BODY_BLOB_TYPE))
                .build();
        }
    }

    static final BlobType HEADER_BLOB_TYPE = new BlobType("mailHeader");
    static final BlobType BODY_BLOB_TYPE = new BlobType("mailBody");

    private final BlobId headerBlobId;
    private final BlobId bodyBlobId;

    private MimeMessagePartsId(BlobId headerBlobId, BlobId bodyBlobId) {
        this.headerBlobId = headerBlobId;
        this.bodyBlobId = bodyBlobId;
    }

    @Override
    public Map<BlobType, BlobId> asMap() {
        return ImmutableMap.of(
            HEADER_BLOB_TYPE, headerBlobId,
            BODY_BLOB_TYPE, bodyBlobId);
    }

    public BlobId getHeaderBlobId() {
        return headerBlobId;
    }

    public BlobId getBodyBlobId() {
        return bodyBlobId;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MimeMessagePartsId) {
            MimeMessagePartsId that = (MimeMessagePartsId) o;

            return Objects.equals(this.headerBlobId, that.headerBlobId)
                && Objects.equals(this.bodyBlobId, that.bodyBlobId);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(headerBlobId, bodyBlobId);
    }
}
