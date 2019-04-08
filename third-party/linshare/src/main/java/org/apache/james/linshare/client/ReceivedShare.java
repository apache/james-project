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

class ReceivedShare {

    private final User sender;
    private final Document document;
    private final int downloaded;

    @VisibleForTesting
    ReceivedShare(@JsonProperty("sender") User sender,
                  @JsonProperty("downloaded") int downloaded,
                  @JsonProperty("uuid") String uuid,
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
        this.sender = sender;
        this.downloaded = downloaded;
        this.document = new Document(uuid, name, description, creationDate, modificationDate, expirationDate,
            ciphered, type, size, metaData, sha256sum, hasThumbnail, shared);
    }

    public User getSender() {
        return sender;
    }

    public Document getDocument() {
        return document;
    }

    public int getDownloaded() {
        return downloaded;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ReceivedShare) {
            ReceivedShare that = (ReceivedShare) o;

            return Objects.equals(this.sender, that.sender)
                && Objects.equals(this.document, that.document)
                && Objects.equals(this.downloaded, that.downloaded);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(sender, document, downloaded);
    }
}
