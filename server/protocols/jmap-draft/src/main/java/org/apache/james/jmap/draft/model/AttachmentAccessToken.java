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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;

public class AttachmentAccessToken implements SignedExpiringToken {

    public static final char SEPARATOR = '_';

    public static Builder builder() {
        return new Builder();
    }

    public static AttachmentAccessToken from(String serializedAttachmentAccessToken, String blobId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serializedAttachmentAccessToken), "'AttachmentAccessToken' is mandatory");
        List<String> split = Splitter.on(SEPARATOR).splitToList(serializedAttachmentAccessToken);
        Preconditions.checkArgument(split.size() >= 3, "Wrong 'AttachmentAccessToken'");

        String username = Joiner.on(SEPARATOR)
            .join(split.stream()
                .limit(split.size() - 2)
                .collect(Guavate.toImmutableList()));

        String defaultValue = null;
        return builder()
                .blobId(blobId)
                .username(username)
                .expirationDate(ZonedDateTime.parse(Iterables.get(split, split.size() - 2, defaultValue)))
                .signature(Iterables.get(split, split.size() - 1, defaultValue))
                .build();
    }

    public static class Builder {
        private String username;
        private String blobId;
        private ZonedDateTime expirationDate;
        private String signature;

        private Builder() {

        }
        
        public Builder blobId(String blobId) {
            this.blobId = blobId;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder expirationDate(ZonedDateTime expirationDate) {
            this.expirationDate = expirationDate;
            return this;
        }

        public Builder signature(String signature) {
            this.signature = signature.trim();
            return this;
        }

        public AttachmentAccessToken build() {
            Preconditions.checkNotNull(username);
            Preconditions.checkNotNull(blobId);
            Preconditions.checkArgument(! blobId.isEmpty());
            Preconditions.checkNotNull(expirationDate);
            Preconditions.checkNotNull(signature);
            return new AttachmentAccessToken(username, blobId, expirationDate, signature);
        }
    }
    
    private final String username;
    private final String blobId;
    private final ZonedDateTime expirationDate;
    private final String signature;

    @VisibleForTesting
    AttachmentAccessToken(String username, String blobId, ZonedDateTime expirationDate, String signature) {
        this.username = username;
        this.blobId = blobId;
        this.expirationDate = expirationDate;
        this.signature = signature;
    }

    public String getBlobId() {
        return blobId;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public ZonedDateTime getExpirationDate() {
        return expirationDate;
    }

    @Override
    public String getSignature() {
        return signature;
    }

    public String serialize() {
        return getPayload()
            + SEPARATOR
            + signature;
    }
    
    @Override
    public String getPayload() {
        return username
            + SEPARATOR
            + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(expirationDate);
    }
    
    @Override
    public String getSignedContent() {
        return blobId
            + SEPARATOR
            + getPayload();
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof AttachmentAccessToken) {
            AttachmentAccessToken attachmentAccessToken = (AttachmentAccessToken) other;
            return Objects.equals(username, attachmentAccessToken.username)
                    && Objects.equals(blobId, attachmentAccessToken.blobId)
                    && Objects.equals(expirationDate, attachmentAccessToken.expirationDate)
                    && Objects.equals(signature, attachmentAccessToken.signature);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, blobId, expirationDate, signature);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("username", username)
                .add("blobId", blobId)
                .add("expirationDate", expirationDate)
                .add("signature", signature)
                .toString();
    }
}
