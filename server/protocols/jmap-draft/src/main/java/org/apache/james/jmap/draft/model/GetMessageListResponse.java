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

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.jmap.methods.Method;
import org.apache.james.jmap.model.Number;
import org.apache.james.mailbox.model.MessageId;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class GetMessageListResponse implements Method.Response {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String accountId;
        private Filter filter;
        private final ImmutableList.Builder<String> sort;
        private boolean collapseThreads;
        private String state;
        private boolean canCalculateUpdates;
        private Optional<Number> position;
        private Optional<Number> total;
        private final ImmutableList.Builder<String> threadIds;
        private final ImmutableList.Builder<MessageId> messageIds;

        private Builder() {
            sort = ImmutableList.builder();
            threadIds = ImmutableList.builder();
            messageIds = ImmutableList.builder();
            position = Optional.empty();
            total = Optional.empty();
        }

        public Builder accountId(String accountId) {
            throw new NotImplementedException("not implemented");
        }

        public Builder filter(Filter filter) {
            this.filter = filter;
            return this;
        }

        public Builder sort(List<String> sort) {
            this.sort.addAll(sort);
            return this;
        }

        public Builder collapseThreads(boolean collapseThreads) {
            throw new NotImplementedException("not implemented");
        }

        public Builder state(String state) {
            throw new NotImplementedException("not implemented");
        }

        public Builder canCalculateUpdates(boolean canCalculateUpdates) {
            throw new NotImplementedException("not implemented");
        }

        public Builder position(int position) {
            throw new NotImplementedException("not implemented");
        }

        public Builder total(int total) {
            throw new NotImplementedException("not implemented");
        }

        public Builder threadIds(List<String> threadIds) {
            throw new NotImplementedException("not implemented");
        }

        public Builder messageId(MessageId messageId) {
            this.messageIds.add(messageId);
            return this;
        }

        public Builder messageIds(List<MessageId> messageIds) {
            this.messageIds.addAll(messageIds);
            return this;
        }

        public GetMessageListResponse build() {
            return new GetMessageListResponse(accountId, filter, sort.build(), collapseThreads, state,
                    canCalculateUpdates, position.orElse(Number.ZERO), total.orElse(Number.ZERO), threadIds.build(), messageIds.build());
        }
    }

    private final String accountId;
    private final Filter filter;
    private final List<String> sort;
    private final boolean collapseThreads;
    private final String state;
    private final boolean canCalculateUpdates;
    private final Number position;
    private final Number total;
    private final List<String> threadIds;
    private final List<MessageId> messageIds;

    @VisibleForTesting GetMessageListResponse(String accountId, Filter filter, List<String> sort, boolean collapseThreads, String state,
            boolean canCalculateUpdates, Number position, Number total, List<String> threadIds, List<MessageId> messageIds) {

        this.accountId = accountId;
        this.filter = filter;
        this.sort = sort;
        this.collapseThreads = collapseThreads;
        this.state = state;
        this.canCalculateUpdates = canCalculateUpdates;
        this.position = position;
        this.total = total;
        this.threadIds = threadIds;
        this.messageIds = messageIds;
    }

    public String getAccountId() {
        return accountId;
    }

    public Filter getFilter() {
        return filter;
    }

    public List<String> getSort() {
        return sort;
    }

    public boolean isCollapseThreads() {
        return collapseThreads;
    }

    public String getState() {
        return state;
    }

    public boolean isCanCalculateUpdates() {
        return canCalculateUpdates;
    }

    public Number getPosition() {
        return position;
    }

    public Number getTotal() {
        return total;
    }

    public List<String> getThreadIds() {
        return threadIds;
    }

    public List<MessageId> getMessageIds() {
        return messageIds;
    }
}
