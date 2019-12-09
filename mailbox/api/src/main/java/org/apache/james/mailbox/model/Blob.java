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

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.apache.james.mailbox.exception.BlobNotFoundException;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class Blob {

    @FunctionalInterface
    public interface InputStreamSupplier {
        /**
         * @return the content of this blob as an inputStream.
         *
         * The caller is responsible of closing it.
         */
        InputStream load() throws IOException, BlobNotFoundException;
    }

    public static class Builder {
        private BlobId blobId;
        private InputStreamSupplier payload;
        private String contentType;
        private Long size;

        private Builder() {
        }

        public Builder id(BlobId id) {
            this.blobId = id;
            return this;
        }

        public Builder payload(InputStreamSupplier payload) {
            this.payload = payload;
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder size(long size) {
            this.size = size;
            return this;
        }

        public Blob build() {
            Preconditions.checkState(blobId != null, "id can not be empty");
            Preconditions.checkState(payload != null, "payload can not be empty");
            Preconditions.checkState(contentType != null, "contentType can not be empty");
            Preconditions.checkState(size != null, "size can not be empty");

            return new Blob(blobId, payload, contentType, size);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final BlobId blobId;
    private final InputStreamSupplier payload;
    private final String contentType;
    private final long size;

    @VisibleForTesting
    Blob(BlobId blobId, InputStreamSupplier payload, String contentType, long size) {
        this.blobId = blobId;
        this.payload = payload;
        this.contentType = contentType;
        this.size = size;
    }

    public BlobId getBlobId() {
        return blobId;
    }

    public InputStream getStream() throws IOException {
        return payload.load();
    }

    public long getSize() {
        return size;
    }

    public String getContentType() {
        return contentType;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Blob) {
            Blob blob = (Blob) o;

            return Objects.equals(this.blobId, blob.blobId)
                && Objects.equals(this.contentType, blob.contentType);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(blobId, contentType);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("blobId", blobId)
            .add("contentType", contentType)
            .toString();
    }
}
