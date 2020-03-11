/****************************************************************
O * Licensed to the Apache Software Foundation (ASF) under one   *
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

package org.apache.james.jmap.draft.model.mailbox;

import java.util.Objects;
import java.util.Optional;

import org.apache.james.jmap.draft.exceptions.JmapFieldNotSupportedException;
import org.apache.james.jmap.draft.model.MailboxCreationId;
import org.apache.james.mailbox.Role;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

@JsonDeserialize(builder = MailboxCreateRequest.Builder.class)
public class MailboxCreateRequest {
    private static final String ISSUER = "MailboxCreateRequest";

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private Optional<String> id;
        private String name;
        private Optional<MailboxCreationId> parentId;
        private Optional<Role> role;
        private Optional<SortOrder> sortOrder;

        private Builder() {
            id = Optional.empty();
            role = Optional.empty();
            sortOrder = Optional.empty();
            parentId = Optional.empty();
        }

        public Builder id(String id) {
            Preconditions.checkNotNull(id);
            this.id = Optional.of(id);
            return this;
        }

        public Builder name(String name) {
            Preconditions.checkNotNull(name);
            this.name = name;
            return this;
        }

        public Builder parentId(MailboxCreationId parentId) {
            Preconditions.checkNotNull(parentId);
            this.parentId = Optional.of(parentId);
            return this;
        }

        public Builder role(Role role) {
            Preconditions.checkNotNull(role);
            throw new JmapFieldNotSupportedException(ISSUER, "role");
        }

        public Builder sortOrder(SortOrder sortOrder) {
            Preconditions.checkNotNull(sortOrder);
            throw new JmapFieldNotSupportedException(ISSUER, "sortOrder");
        }


        public MailboxCreateRequest build() {
            Preconditions.checkState(!Strings.isNullOrEmpty(name), "'name' is mandatory");
            return new MailboxCreateRequest(id, name, parentId, role, sortOrder);
        }
    }

    private final Optional<String> id;
    private final String name;
    private final Optional<MailboxCreationId> parentId;
    private final Optional<Role> role;
    private final Optional<SortOrder> sortOrder;

    @VisibleForTesting
    MailboxCreateRequest(Optional<String> id, String name, Optional<MailboxCreationId> parentId, Optional<Role> role, Optional<SortOrder> sortOrder) {

        this.id = id;
        this.name = name;
        this.parentId = parentId;
        this.role = role;
        this.sortOrder = sortOrder;
    }

    public Optional<String> getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Optional<MailboxCreationId> getParentId() {
        return parentId;
    }

    public Optional<Role> getRole() {
        return role;
    }

    public Optional<SortOrder> getSortOrder() {
        return sortOrder;
    }


    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof MailboxCreateRequest) {
            MailboxCreateRequest other = (MailboxCreateRequest) obj;
            return Objects.equals(this.id, other.id)
                && Objects.equals(this.name, other.name)
                && Objects.equals(this.parentId, other.parentId)
                && Objects.equals(this.role, other.role)
                && Objects.equals(this.sortOrder, other.sortOrder);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id, name, parentId, role, sortOrder);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("id", id)
                .add("name", name)
                .add("parentId", parentId)
                .add("role", role)
                .add("sortOrder", sortOrder)
                .toString();
    }
}
