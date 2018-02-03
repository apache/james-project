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

package org.apache.james.jmap.model;

import java.util.Optional;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

@JsonDeserialize(builder = Attachment.Builder.class)
public class Attachment {

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private BlobId blobId;
        private String type;
        private String name;
        private Number size;
        private String cid;
        private boolean isInline;
        private Number width;
        private Number height;

        public Builder blobId(BlobId blobId) {
            this.blobId = blobId;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        @JsonDeserialize
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder name(Optional<String> name) {
            this.name = name.orElse(null);
            return this;
        }

        public Builder size(long size) {
            this.size = Number.DEFAULT_FACTORY.from(size)
                .orElseThrow(() -> new IllegalArgumentException(Number.VALIDATION_MESSAGE));
            return this;
        }

        @JsonDeserialize
        public Builder cid(String cid) {
            this.cid = cid;
            return this;
        }

        public Builder cid(Optional<String> cid) {
            this.cid = cid.orElse(null);
            return this;
        }

        public Builder isInline(boolean isInline) {
            this.isInline = isInline;
            return this;
        }

        public Builder width(long width) {
            this.width = Number.DEFAULT_FACTORY.from(width)
                .orElseThrow(() -> new IllegalArgumentException(Number.VALIDATION_MESSAGE));
            return this;
        }

        public Builder height(long height) {
            this.height = Number.DEFAULT_FACTORY.from(height)
                .orElseThrow(() -> new IllegalArgumentException(Number.VALIDATION_MESSAGE));
            return this;
        }

        public Attachment build() {
            Preconditions.checkState(blobId != null, "'blobId' is mandatory");
            Preconditions.checkState(!Strings.isNullOrEmpty(type), "'type' is mandatory");
            Preconditions.checkState(size != null, "'size' is mandatory");
            return new Attachment(blobId, type, Optional.ofNullable(name), size, Optional.ofNullable(cid), isInline, Optional.ofNullable(width), Optional.ofNullable(height));
        }
    }

    private final BlobId blobId;
    private final String type;
    private final Optional<String> name;
    private final Number size;
    private final Optional<String> cid;
    private final boolean isInline;
    private final Optional<Number> width;
    private final Optional<Number> height;

    @VisibleForTesting Attachment(BlobId blobId, String type, Optional<String> name, Number size, Optional<String> cid, boolean isInline, Optional<Number> width, Optional<Number> height) {
        this.blobId = blobId;
        this.type = type;
        this.name = name;
        this.size = size;
        this.cid = cid;
        this.isInline = isInline;
        this.width = width;
        this.height = height;
    }

    public BlobId getBlobId() {
        return blobId;
    }

    public String getType() {
        return type;
    }

    public Optional<String> getName() {
        return name;
    }

    public Number getSize() {
        return size;
    }

    public Optional<String> getCid() {
        return cid;
    }

    public boolean isIsInline() {
        return isInline;
    }

    public Boolean isInlinedWithCid() {
        return isInline && cid.isPresent();
    }

    public Optional<Number> getWidth() {
        return width;
    }

    public Optional<Number> getHeight() {
        return height;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Attachment) {
            Attachment other = (Attachment) obj;
            return Objects.equal(blobId, other.blobId)
                && Objects.equal(type, other.type)
                && Objects.equal(name, other.name)
                && Objects.equal(size, other.size)
                && Objects.equal(cid, other.cid)
                && Objects.equal(isInline, other.isInline)
                && Objects.equal(width, other.width)
                && Objects.equal(height, other.height);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(blobId, type, name, size, cid, isInline, width, height);
    }

    @Override
    public String toString() {
        return MoreObjects
                .toStringHelper(this)
                .add("blobId", blobId)
                .add("type", type)
                .add("name", name)
                .add("size", size)
                .add("cid", cid)
                .add("isInline", isInline)
                .add("width", width)
                .add("height", height)
                .toString();
    }
}
