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
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

@JsonDeserialize(builder = Attachment.Builder.class)
public class Attachment {

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private String blobId;
        private String type;
        private String name;
        private Long size;
        private String cid;
        private boolean isInline;
        private Long width;
        private Long height;

        public Builder blobId(String blobId) {
            this.blobId = blobId;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder size(long size) {
            this.size = size;
            return this;
        }

        public Builder cid(String cid) {
            this.cid = cid;
            return this;
        }

        public Builder isInline(boolean isInline) {
            this.isInline = isInline;
            return this;
        }

        public Builder width(long width) {
            this.width = width;
            return this;
        }

        public Builder height(long height) {
            this.height = height;
            return this;
        }

        public Attachment build() {
            Preconditions.checkState(!Strings.isNullOrEmpty(blobId), "'blobId' is mandatory");
            Preconditions.checkState(!Strings.isNullOrEmpty(type), "'type' is mandatory");
            Preconditions.checkState(!Strings.isNullOrEmpty(name), "'name' is mandatory");
            Preconditions.checkState(size != null, "'size' is mandatory");
            return new Attachment(blobId, type, name, size, Optional.ofNullable(cid), isInline, Optional.ofNullable(width), Optional.ofNullable(height));
        }
    }

    private final String blobId;
    private final String type;
    private final String name;
    private final long size;
    private final Optional<String> cid;
    private final boolean isInline;
    private final Optional<Long> width;
    private final Optional<Long> height;

    @VisibleForTesting Attachment(String blobId, String type, String name, long size, Optional<String> cid, boolean isInline, Optional<Long> width, Optional<Long> height) {
        this.blobId = blobId;
        this.type = type;
        this.name = name;
        this.size = size;
        this.cid = cid;
        this.isInline = isInline;
        this.width = width;
        this.height = height;
    }

    public String getBlobId() {
        return blobId;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public Optional<String> getCid() {
        return cid;
    }

    public boolean isInline() {
        return isInline;
    }

    public Optional<Long> getWidth() {
        return width;
    }

    public Optional<Long> getHeight() {
        return height;
    }
}
