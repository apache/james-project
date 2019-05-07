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

package org.apache.james.linshare.client;

import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class Document {

    public static class DocumentId {
        private final UUID id;

        DocumentId(UUID id) {
            Preconditions.checkNotNull(id);
            this.id = id;
        }

        public UUID getId() {
            return id;
        }

        public String asString() {
            return id.toString();
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof DocumentId) {
                DocumentId that = (DocumentId) o;

                return Objects.equals(this.id, that.id);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("id", id)
                .toString();
        }
    }

    private final DocumentId id;
    private final String name;
    private final String description;
    private final long creationDate;
    private final long modificationDate;
    private final long expirationDate;
    private final boolean ciphered;
    private final String type;
    private final long size;
    private final String metaData;
    private final String sha256sum;
    private final boolean hasThumbnail;
    private final int shared;

    public Document(@JsonProperty("uuid") String uuid,
                    @JsonProperty("name") String name,
                    @JsonProperty("description") String description,
                    @JsonProperty("creationDate") long creationDate,
                    @JsonProperty("modificationDate") long modificationDate,
                    @JsonProperty("expirationDate") long expirationDate,
                    @JsonProperty("ciphered") boolean ciphered,
                    @JsonProperty("type") String type,
                    @JsonProperty("size") long size,
                    @JsonProperty("metaData") String metaData,
                    @JsonProperty("sha256sum") String sha256sum,
                    @JsonProperty("hasThumbnail") boolean hasThumbnail,
                    @JsonProperty("shared") int shared) {
        this.id = new DocumentId(UUID.fromString(uuid));
        this.name = name;
        this.description = description;
        this.creationDate = creationDate;
        this.modificationDate = modificationDate;
        this.expirationDate = expirationDate;
        this.ciphered = ciphered;
        this.type = type;
        this.size = size;
        this.metaData = metaData;
        this.sha256sum = sha256sum;
        this.hasThumbnail = hasThumbnail;
        this.shared = shared;
    }

    public DocumentId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public long getCreationDate() {
        return creationDate;
    }

    public long getModificationDate() {
        return modificationDate;
    }

    public long getExpirationDate() {
        return expirationDate;
    }

    public boolean isCiphered() {
        return ciphered;
    }

    public String getType() {
        return type;
    }

    public long getSize() {
        return size;
    }

    public String getMetaData() {
        return metaData;
    }

    public String getSha256sum() {
        return sha256sum;
    }

    public boolean isHasThumbnail() {
        return hasThumbnail;
    }

    public int getShared() {
        return shared;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Document) {
            Document document = (Document) o;

            return Objects.equals(this.creationDate, document.creationDate)
                && Objects.equals(this.modificationDate, document.modificationDate)
                && Objects.equals(this.expirationDate, document.expirationDate)
                && Objects.equals(this.ciphered, document.ciphered)
                && Objects.equals(this.size, document.size)
                && Objects.equals(this.hasThumbnail, document.hasThumbnail)
                && Objects.equals(this.shared, document.shared)
                && Objects.equals(this.id, document.id)
                && Objects.equals(this.name, document.name)
                && Objects.equals(this.description, document.description)
                && Objects.equals(this.type, document.type)
                && Objects.equals(this.metaData, document.metaData)
                && Objects.equals(this.sha256sum, document.sha256sum);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id, name, description, creationDate, modificationDate, expirationDate, ciphered, type, size, metaData, sha256sum, hasThumbnail, shared);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("id", id)
            .add("name", name)
            .add("description", description)
            .add("creationDate", creationDate)
            .add("modificationDate", modificationDate)
            .add("expirationDate", expirationDate)
            .add("ciphered", ciphered)
            .add("type", type)
            .add("size", size)
            .add("metaData", metaData)
            .add("sha256sum", sha256sum)
            .add("hasThumbnail", hasThumbnail)
            .add("shared", shared)
            .toString();
    }
}
