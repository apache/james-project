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

import java.util.UUID;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

public class UuidBackedAttachmentId implements AttachmentId {
    public static UuidBackedAttachmentId random() {
        return new UuidBackedAttachmentId(UUID.randomUUID());
    }

    public static UuidBackedAttachmentId from(String id) {
        return new UuidBackedAttachmentId(UUID.fromString(id));
    }

    public static UuidBackedAttachmentId from(UUID id) {
        return new UuidBackedAttachmentId(id);
    }

    private final UUID id;

    private UuidBackedAttachmentId(UUID id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id.toString();
    }

    @Override
    public UUID asUUID() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof UuidBackedAttachmentId) {
            UuidBackedAttachmentId other = (UuidBackedAttachmentId) obj;
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
