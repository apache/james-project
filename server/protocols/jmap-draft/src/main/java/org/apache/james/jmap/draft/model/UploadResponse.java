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

import java.time.ZonedDateTime;
import java.util.Optional;

import org.apache.james.jmap.model.Number;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

@JsonDeserialize(builder = UploadResponse.Builder.class)
public class UploadResponse {

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private String accountId;
        private String blobId;
        private String type;
        private Number size;
        private ZonedDateTime expires;

        public Builder accountId(String accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder blobId(String blobId) {
            this.blobId = blobId;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder size(long size) {
            this.size = Number.BOUND_SANITIZING_FACTORY.from(size);
            return this;
        }

        public Builder expires(ZonedDateTime expires) {
            this.expires = expires;
            return this;
        }

        public UploadResponse build() {
            Preconditions.checkState(!Strings.isNullOrEmpty(blobId), "'blobId' is mandatory");
            Preconditions.checkState(!Strings.isNullOrEmpty(type), "'type' is mandatory");
            Preconditions.checkState(size != null, "'size' is mandatory");
            return new UploadResponse(Optional.ofNullable(accountId), blobId, type, size, Optional.ofNullable(expires));
        }
    }

    private final Optional<String> accountId;
    private final String blobId;
    private final String type;
    private final Number size;
    private final Optional<ZonedDateTime> expires;

    @VisibleForTesting UploadResponse(Optional<String> accountId, String blobId, String type, Number size, Optional<ZonedDateTime> expires) {
        this.accountId = accountId;
        this.blobId = blobId;
        this.type = type;
        this.size = size;
        this.expires = expires;
    }

    public Optional<String> getAccountId() {
        return accountId;
    }

    public String getBlobId() {
        return blobId;
    }

    public String getType() {
        return type;
    }

    public Number getSize() {
        return size;
    }

    public Optional<ZonedDateTime> getExpires() {
        return expires;
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof UploadResponse) {
            UploadResponse other = (UploadResponse) obj;
            return Objects.equal(accountId, other.accountId)
                && Objects.equal(blobId, other.blobId)
                && Objects.equal(type, other.type)
                && Objects.equal(size, other.size)
                && Objects.equal(expires, other.expires);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(accountId, blobId, type, size, expires);
    }

    @Override
    public String toString() {
        return MoreObjects
                .toStringHelper(this)
                .add("accountId", accountId)
                .add("blobId", blobId)
                .add("type", type)
                .add("size", size)
                .add("expires", expires)
                .toString();
    }
}
