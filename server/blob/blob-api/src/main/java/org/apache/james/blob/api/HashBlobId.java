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

package org.apache.james.blob.api;

import java.io.IOException;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSource;

public class HashBlobId implements BlobId {
    private static final String HASH_BLOB_ID_ENCODING_TYPE_PROPERTY = "james.blob.id.hash.encoding";
    private static final BaseEncoding HASH_BLOB_ID_ENCODING_DEFAULT = BaseEncoding.base64Url();

    public static class Factory implements BlobId.Factory {
        private final BaseEncoding baseEncoding;

        public Factory() {
            this.baseEncoding = Optional.ofNullable(System.getProperty(HASH_BLOB_ID_ENCODING_TYPE_PROPERTY))
                .map(Factory::baseEncodingFrom)
                .orElse(HASH_BLOB_ID_ENCODING_DEFAULT);
        }

        @Override
        public HashBlobId forPayload(byte[] payload) {
            Preconditions.checkArgument(payload != null);
            return base64(Hashing.sha256().hashBytes(payload));
        }

        @Override
        public BlobId forPayload(ByteSource payload) {
            try {
                return base64(payload.hash(Hashing.sha256()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private HashBlobId base64(HashCode hashCode) {
            byte[] bytes = hashCode.asBytes();
            return new HashBlobId(baseEncoding.encode(bytes));
        }

        @Override
        public HashBlobId from(String id) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(id));
            return new HashBlobId(id);
        }

        private static BaseEncoding baseEncodingFrom(String encodingType) {
            switch (encodingType) {
                case "base16":
                case "hex":
                    return BaseEncoding.base16();
                case "base64":
                    return BaseEncoding.base64();
                case "base64Url":
                    return BaseEncoding.base64Url();
                case "base32":
                    return BaseEncoding.base32();
                case "base32Hex":
                    return BaseEncoding.base32Hex();
                default:
                    throw new IllegalArgumentException("Unknown encoding type: " + encodingType);
            }
        }
    }

    private final String id;

    @VisibleForTesting
    HashBlobId(String id) {
        this.id = id;
    }

    @Override
    public String asString() {
        return id;
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof HashBlobId) {
            HashBlobId other = (HashBlobId) obj;
            return Objects.equal(id, other.id);
        }
        return false;
    }

    @Override
    public final int hashCode() {
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
