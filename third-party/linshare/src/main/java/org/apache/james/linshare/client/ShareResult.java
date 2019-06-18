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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;

class ShareResult {

    private final String uuid;
    private final String name;
    private final long creationDate;
    private final long modificationDate;
    private final long expirationDate;
    private final int downloaded;
    private final String description;
    private final Document document;
    private final User recipient;

    @VisibleForTesting
    ShareResult(@JsonProperty("uuid") String uuid,
                @JsonProperty("name") String name,
                @JsonProperty("creationDate") long creationDate,
                @JsonProperty("modificationDate") long modificationDate,
                @JsonProperty("expirationDate") long expirationDate,
                @JsonProperty("downloaded") int downloaded,
                @JsonProperty("description") String description,
                @JsonProperty("document") Document document,
                @JsonProperty("recipient") User recipient) {
        this.uuid = uuid;
        this.name = name;
        this.creationDate = creationDate;
        this.modificationDate = modificationDate;
        this.expirationDate = expirationDate;
        this.downloaded = downloaded;
        this.description = description;
        this.document = document;
        this.recipient = recipient;
    }

    public String getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
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

    public int getDownloaded() {
        return downloaded;
    }

    public String getDescription() {
        return description;
    }

    public Document getDocument() {
        return document;
    }

    public User getRecipient() {
        return recipient;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ShareResult) {
            ShareResult that = (ShareResult) o;

            return Objects.equals(this.creationDate, that.creationDate)
                && Objects.equals(this.modificationDate, that.modificationDate)
                && Objects.equals(this.expirationDate, that.expirationDate)
                && Objects.equals(this.downloaded, that.downloaded)
                && Objects.equals(this.uuid, that.uuid)
                && Objects.equals(this.name, that.name)
                && Objects.equals(this.description, that.description)
                && Objects.equals(this.document, that.document)
                && Objects.equals(this.recipient, that.recipient);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(uuid, name, creationDate, modificationDate, expirationDate, downloaded, description, document, recipient);
    }
}
