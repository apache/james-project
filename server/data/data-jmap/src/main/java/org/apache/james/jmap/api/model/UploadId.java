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

package org.apache.james.jmap.api.model;

import java.util.UUID;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class UploadId {
    public static UploadId random() {
        return new UploadId(UUID.randomUUID());
    }

    public static UploadId from(String id) {
        Preconditions.checkNotNull(id);
        Preconditions.checkArgument(!id.isEmpty());
        return new UploadId(UUID.fromString(id));
    }

    public static UploadId from(UUID id) {
        Preconditions.checkNotNull(id);

        return new UploadId(id);
    }

    private final UUID id;

    public UploadId(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public String asString() {
        return id.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof UploadId) {
            UploadId other = (UploadId) obj;
            return Objects.equal(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
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
