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

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.jmap.model.mailbox.Rights;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

@JsonDeserialize(builder = MailboxUpdateRequest.Builder.class)
public class MailboxUpdateRequest {

    public static Builder builder() throws MailboxException {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private Optional<String> name;
        private Optional<MailboxId> parentId;
        private Optional<Role> role;
        private Optional<SortOrder> sortOrder;
        private Optional<Rights> sharedWith;

        private Builder() {
            name = Optional.empty();
            role = Optional.empty();
            sortOrder = Optional.empty();
            parentId = Optional.empty();
            sharedWith = Optional.empty();
        }

        public Builder name(String name) throws MailboxException {
            Preconditions.checkNotNull(name);
            Preconditions.checkArgument(!name.isEmpty());
            this.name = Optional.of(name);
            return this;
        }

        public Builder parentId(MailboxId parentId) {
            if (parentId == null) {
                this.parentId = null;
            } else {
                this.parentId = Optional.of(parentId);
            }
            return this;
        }

        public Builder role(Role role) {
            Preconditions.checkNotNull(role);
            throw new NotImplementedException("not implemented");
        }

        public Builder sortOrder(SortOrder sortOrder) {
            Preconditions.checkNotNull(sortOrder);
            throw new NotImplementedException("not implemented");
        }

        public Builder sharedWith(Rights rights) {
            Preconditions.checkNotNull(rights);
            this.sharedWith = Optional.of(rights);
            return this;
        }

        public MailboxUpdateRequest build() {
            return new MailboxUpdateRequest(name, parentId, role, sortOrder, sharedWith);
        }
    }

    private final Optional<String> name;
    private final Optional<MailboxId> parentId;
    private final Optional<Role> role;
    private final Optional<SortOrder> sortOrder;
    private final Optional<Rights> sharedWith;

    @VisibleForTesting
    MailboxUpdateRequest(Optional<String> name, Optional<MailboxId> parentId, Optional<Role> role, Optional<SortOrder> sortOrder, Optional<Rights> sharedWith) {

        this.name = name;
        this.parentId = parentId;
        this.role = role;
        this.sortOrder = sortOrder;
        this.sharedWith = sharedWith;
    }

    public Optional<String> getName() {
        return name;
    }

    public Optional<MailboxId> getParentId() {
        return parentId;
    }

    public Optional<Role> getRole() {
        return role;
    }

    public Optional<SortOrder> getSortOrder() {
        return sortOrder;
    }

    public Optional<Rights> getSharedWith() {
        return sharedWith;
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof MailboxUpdateRequest) {
            MailboxUpdateRequest other = (MailboxUpdateRequest) obj;
            return Objects.equals(this.name, other.name)
                && Objects.equals(this.parentId, other.parentId)
                && Objects.equals(this.role, other.role)
                && Objects.equals(this.sortOrder, other.sortOrder)
                && Objects.equals(this.sharedWith, other.sharedWith);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(name, parentId, role, sortOrder, sharedWith);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
            .add("name", name)
            .add("parentId", parentId)
            .add("role", role)
            .add("sortOrder", sortOrder)
            .add("sharedWith", sharedWith)
            .toString();
    }
}
