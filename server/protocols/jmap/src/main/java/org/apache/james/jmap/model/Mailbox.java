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

package org.apache.james.jmap.model;

import java.util.Optional;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

@JsonDeserialize(builder = Mailbox.Builder.class)
public class Mailbox {

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private final static int MAX_SORT_ORDER = Double.valueOf(Math.pow(2, 31)).intValue();

        private String id;
        private String name;
        private String parentId;
        private Role role;
        private int sortOrder;
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

        private Builder() {
        }

        public Builder id(String id) {
            Preconditions.checkNotNull(id);
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            Preconditions.checkNotNull(name);
            this.name = name;
            return this;
        }

        public Builder parentId(String parentId) {
            this.parentId = parentId;
            return this;
        }

        public Builder role(Role role) {
            this.role = role;
            return this;
        }

        public Builder sortOrder(int sortOrder) {
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

        public Mailbox build() {
            Preconditions.checkState(!Strings.isNullOrEmpty(name), "'name' is mandatory");
            Preconditions.checkState(!Strings.isNullOrEmpty(id), "'id' is mandatory");
            Preconditions.checkState(sortOrder >= 0, "'sortOrder' must be positive");
            Preconditions.checkState(sortOrder < MAX_SORT_ORDER, "'sortOrder' must be lesser than " + MAX_SORT_ORDER);

            return new Mailbox(id, name, Optional.ofNullable(parentId), Optional.ofNullable(role), sortOrder, mustBeOnlyMailbox, mayReadItems, mayAddItems, mayRemoveItems, mayCreateChild, mayRename, mayDelete,
                    totalMessages, unreadMessages, totalThreads, unreadThreads);
        }
    }

    private final String id;
    private final String name;
    private final Optional<String> parentId;
    private final Optional<Role> role;
    private final int sortOrder;
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

    @VisibleForTesting Mailbox(String id, String name, Optional<String> parentId, Optional<Role> role, int sortOrder, boolean mustBeOnlyMailbox, 
            boolean mayReadItems, boolean mayAddItems, boolean mayRemoveItems, boolean mayCreateChild, boolean mayRename, boolean mayDelete,
            long totalMessages, long unreadMessages, long totalThreads, long unreadThreads) {

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
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Optional<String> getParentId() {
        return parentId;
    }

    public Optional<Role> getRole() {
        return role;
    }

    public int getSortOrder() {
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
}
