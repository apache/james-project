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

package org.apache.james.jmap.model.mailbox;

import java.util.Objects;
import java.util.Optional;

import org.apache.james.jmap.methods.JmapResponseWriterImpl;
import org.apache.james.mailbox.model.MailboxId;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

@JsonDeserialize(builder = Mailbox.Builder.class)
@JsonFilter(JmapResponseWriterImpl.PROPERTIES_FILTER)
public class Mailbox {

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private MailboxId id;
        private String name;
        private Optional<MailboxId> parentId;
        private Optional<Role> role;
        private SortOrder sortOrder;
        private boolean mustBeOnlyMailbox;
        private boolean mayReadItems;
        private boolean mayAddItems;
        private boolean mayRemoveItems;
        private boolean mayCreateChild;
        private boolean mayRename;
        private boolean mayDelete;
        private long totalMessages;
        private long unreadMessages;
        private long totalThreads;
        private long unreadThreads;
        private Optional<Rights> sharedWith;

        private Builder() {
            parentId = Optional.empty();
            sharedWith = Optional.empty();
        }

        public Builder id(MailboxId id) {
            Preconditions.checkNotNull(id);
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            Preconditions.checkNotNull(name);
            this.name = name;
            return this;
        }

        public Builder parentId(MailboxId parentId) {
            this.parentId = Optional.ofNullable(parentId);
            return this;
        }

        public Builder role(Optional<Role> role) {
            this.role = role;
            return this;
        }

        public Builder sortOrder(SortOrder sortOrder) {
            this.sortOrder = sortOrder;
            return this;
        }

        public Builder mustBeOnlyMailbox(boolean mustBeOnlyMailbox) {
            this.mustBeOnlyMailbox = mustBeOnlyMailbox;
            return this;
        }

        public Builder mayReadItems(boolean mayReadItems) {
            this.mayReadItems = mayReadItems;
            return this;
        }

        public Builder mayAddItems(boolean mayAddItems) {
            this.mayAddItems = mayAddItems;
            return this;
        }

        public Builder mayRemoveItems(boolean mayRemoveItems) {
            this.mayRemoveItems = mayRemoveItems;
            return this;
        }

        public Builder mayCreateChild(boolean mayCreateChild) {
            this.mayCreateChild = mayCreateChild;
            return this;
        }

        public Builder mayRename(boolean mayRename) {
            this.mayRename = mayRename;
            return this;
        }

        public Builder mayDelete(boolean mayDelete) {
            this.mayDelete = mayDelete;
            return this;
        }

        public Builder totalMessages(long totalMessages) {
            this.totalMessages = totalMessages;
            return this;
        }

        public Builder unreadMessages(long unreadMessages) {
            this.unreadMessages = unreadMessages;
            return this;
        }

        public Builder totalThreads(long totalThreads) {
            this.totalThreads = totalThreads;
            return this;
        }

        public Builder unreadThreads(long unreadThreads) {
            this.unreadThreads = unreadThreads;
            return this;
        }

        public Builder sharedWith(Rights sharedWith) {
            this.sharedWith = Optional.of(sharedWith);
            return this;
        }

        public Mailbox build() {
            Preconditions.checkState(!Strings.isNullOrEmpty(name), "'name' is mandatory");
            Preconditions.checkState(id != null, "'id' is mandatory");

            return new Mailbox(id, name, parentId, role, sortOrder, mustBeOnlyMailbox, mayReadItems, mayAddItems, mayRemoveItems, mayCreateChild, mayRename, mayDelete,
                    totalMessages, unreadMessages, totalThreads, unreadThreads, sharedWith.orElse(Rights.EMPTY));
        }
    }

    private final MailboxId id;
    private final String name;
    private final Optional<MailboxId> parentId;
    private final Optional<Role> role;
    private final SortOrder sortOrder;
    private final boolean mustBeOnlyMailbox;
    private final boolean mayReadItems;
    private final boolean mayAddItems;
    private final boolean mayRemoveItems;
    private final boolean mayCreateChild;
    private final boolean mayRename;
    private final boolean mayDelete;
    private final long totalMessages;
    private final long unreadMessages;
    private final long totalThreads;
    private final long unreadThreads;
    private final Rights sharedWith;

    @VisibleForTesting Mailbox(MailboxId id, String name, Optional<MailboxId> parentId, Optional<Role> role, SortOrder sortOrder, boolean mustBeOnlyMailbox,
                               boolean mayReadItems, boolean mayAddItems, boolean mayRemoveItems, boolean mayCreateChild, boolean mayRename, boolean mayDelete,
                               long totalMessages, long unreadMessages, long totalThreads, long unreadThreads, Rights sharedWith) {

        this.id = id;
        this.name = name;
        this.parentId = parentId;
        this.role = role;
        this.sortOrder = sortOrder;
        this.mustBeOnlyMailbox = mustBeOnlyMailbox;
        this.mayReadItems = mayReadItems;
        this.mayAddItems = mayAddItems;
        this.mayRemoveItems = mayRemoveItems;
        this.mayCreateChild = mayCreateChild;
        this.mayRename = mayRename;
        this.mayDelete = mayDelete;
        this.totalMessages = totalMessages;
        this.unreadMessages = unreadMessages;
        this.totalThreads = totalThreads;
        this.unreadThreads = unreadThreads;
        this.sharedWith = sharedWith;
    }

    public MailboxId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Optional<MailboxId> getParentId() {
        return parentId;
    }

    public Optional<Role> getRole() {
        return role;
    }

    public SortOrder getSortOrder() {
        return sortOrder;
    }

    public boolean isMustBeOnlyMailbox() {
        return mustBeOnlyMailbox;
    }

    public boolean isMayReadItems() {
        return mayReadItems;
    }

    public boolean isMayAddItems() {
        return mayAddItems;
    }

    public boolean isMayRemoveItems() {
        return mayRemoveItems;
    }

    public boolean isMayCreateChild() {
        return mayCreateChild;
    }

    public boolean isMayRename() {
        return mayRename;
    }

    public boolean isMayDelete() {
        return mayDelete;
    }

    public long getTotalMessages() {
        return totalMessages;
    }

    public long getUnreadMessages() {
        return unreadMessages;
    }

    public long getTotalThreads() {
        return totalThreads;
    }

    public long getUnreadThreads() {
        return unreadThreads;
    }

    public Rights getSharedWith() {
        return sharedWith;
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof Mailbox) {
            Mailbox other = (Mailbox) obj;
            return Objects.equals(this.id, other.id)
                && Objects.equals(this.name, other.name)
                && Objects.equals(this.parentId, other.parentId)
                && Objects.equals(this.role, other.role)
                && Objects.equals(this.sortOrder, other.sortOrder)
                && Objects.equals(this.mustBeOnlyMailbox, other.mustBeOnlyMailbox)
                && Objects.equals(this.mayReadItems, other.mayReadItems)
                && Objects.equals(this.mayAddItems, other.mayAddItems)
                && Objects.equals(this.mayRemoveItems, other.mayRemoveItems)
                && Objects.equals(this.mayCreateChild, other.mayCreateChild)
                && Objects.equals(this.mayRename, other.mayRename)
                && Objects.equals(this.mayDelete, other.mayDelete)
                && Objects.equals(this.totalMessages, other.totalMessages)
                && Objects.equals(this.unreadMessages, other.unreadMessages)
                && Objects.equals(this.totalThreads, other.totalThreads)
                && Objects.equals(this.unreadThreads, other.unreadThreads)
                && Objects.equals(this.sharedWith, other.sharedWith);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id, name, parentId, role, sortOrder, mustBeOnlyMailbox, mayReadItems, mayAddItems, 
            mayRemoveItems, mayCreateChild, mayRename, mayDelete, totalMessages, unreadMessages, totalThreads,
            unreadThreads, sharedWith);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("id", id)
                .add("name", name)
                .add("sortOrder", sortOrder)
                .toString();
    }
}
