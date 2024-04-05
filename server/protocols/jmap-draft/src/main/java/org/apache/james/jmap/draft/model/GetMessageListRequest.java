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
import org.apache.james.jmap.methods.JmapRequest;
import org.apache.james.jmap.model.Number;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

@JsonDeserialize(builder = GetMessageListRequest.Builder.class)
public class GetMessageListRequest implements JmapRequest {

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private String accountId;
        private Filter filter;
        private final ImmutableList.Builder<String> sort;
        private Boolean collapseThreads;
        private Optional<Number> position;
        private String anchor;
        private Number anchorOffset;
        private Number limit;
        private Boolean fetchThreads;
        private Boolean fetchMessages;
        private final ImmutableList.Builder<String> fetchMessageProperties;
        private Boolean fetchSearchSnippets;

        private Builder() {
            position = Optional.empty();
            sort = ImmutableList.builder();
            fetchMessageProperties = ImmutableList.builder();
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
            this.collapseThreads = collapseThreads;
            return this;
        }

        public Builder position(long position) {
            this.position = Optional.of(
                Number.DEFAULT_FACTORY.from(position)
                    .orElseThrow(() -> new IllegalArgumentException(Number.VALIDATION_MESSAGE)));
            return this;
        }

        public Builder anchor(String anchor) {
            throw new NotImplementedException("not implemented");
        }

        public Builder anchorOffset(int anchorOffset) {
            throw new NotImplementedException("not implemented");
        }

        public Builder limit(long limit) {
            this.limit = Number.DEFAULT_FACTORY.from(limit)
                .orElseThrow(() -> new IllegalArgumentException(Number.VALIDATION_MESSAGE));
            return this;
        }

        public Builder fetchThreads(boolean fetchThreads) {
            throw new NotImplementedException("not implemented");
        }

        public Builder fetchMessages(boolean fetchMessages) {
            this.fetchMessages = fetchMessages;
            return this;
        }

        public Builder fetchMessageProperties(List<String> fetchMessageProperties) {
            this.fetchMessageProperties.addAll(fetchMessageProperties);
            return this;
        }

        public Builder fetchSearchSnippets(boolean fetchSearchSnippets) {
            throw new NotImplementedException("not implemented");
        }

        public GetMessageListRequest build() {
            return new GetMessageListRequest(Optional.ofNullable(accountId), Optional.ofNullable(filter), sort.build(), Optional.ofNullable(collapseThreads),
                    position, Optional.ofNullable(anchor), Optional.ofNullable(anchorOffset), Optional.ofNullable(limit), Optional.ofNullable(fetchThreads),
                    Optional.ofNullable(fetchMessages), fetchMessageProperties.build(), Optional.ofNullable(fetchSearchSnippets));
        }
    }

    private final Optional<String> accountId;
    private final Optional<Filter> filter;
    private final List<String> sort;
    private final Optional<Boolean> collapseThreads;
    private final Optional<Number> position;
    private final Optional<String> anchor;
    private final Optional<Number> anchorOffset;
    private final Optional<Number> limit;
    private final Optional<Boolean> fetchThreads;
    private final Optional<Boolean> fetchMessages;
    private final List<String> fetchMessageProperties;
    private final Optional<Boolean> fetchSearchSnippets;

    @VisibleForTesting GetMessageListRequest(Optional<String> accountId, Optional<Filter> filter, List<String> sort, Optional<Boolean> collapseThreads,
            Optional<Number> position, Optional<String> anchor, Optional<Number> anchorOffset, Optional<Number> limit, Optional<Boolean> fetchThreads,
            Optional<Boolean> fetchMessages, List<String> fetchMessageProperties, Optional<Boolean> fetchSearchSnippets) {

        this.accountId = accountId;
        this.filter = filter;
        this.sort = sort;
        this.collapseThreads = collapseThreads;
        this.position = position;
        this.anchor = anchor;
        this.anchorOffset = anchorOffset;
        this.limit = limit;
        this.fetchThreads = fetchThreads;
        this.fetchMessages = fetchMessages;
        this.fetchMessageProperties = fetchMessageProperties;
        this.fetchSearchSnippets = fetchSearchSnippets;
    }

    public Optional<String> getAccountId() {
        return accountId;
    }

    public Optional<Filter> getFilter() {
        return filter;
    }

    public List<String> getSort() {
        return sort;
    }

    public Optional<Boolean> isCollapseThreads() {
        return collapseThreads;
    }

    public Optional<Number> getPosition() {
        return position;
    }

    public Optional<String> getAnchor() {
        return anchor;
    }

    public Optional<Number> getAnchorOffset() {
        return anchorOffset;
    }

    public Optional<Number> getLimit() {
        return limit;
    }

    public Optional<Boolean> isFetchThreads() {
        return fetchThreads;
    }

    public Optional<Boolean> isFetchMessages() {
        return fetchMessages;
    }

    public List<String> getFetchMessageProperties() {
        return fetchMessageProperties;
    }

    public Optional<Boolean> isFetchSearchSnippets() {
        return fetchSearchSnippets;
    }
}
