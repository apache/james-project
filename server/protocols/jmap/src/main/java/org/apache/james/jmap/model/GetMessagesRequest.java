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

import java.util.Arrays;
import java.util.Optional;

import com.google.common.collect.ImmutableSet;
import org.apache.james.jmap.methods.JmapRequest;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.collect.ImmutableList;

@JsonDeserialize(builder = GetMessagesRequest.Builder.class)
public class GetMessagesRequest implements JmapRequest {

    public static Builder builder() {
        return new Builder();
    }
    
    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        
        private Optional<String> accountId;
        private final ImmutableList.Builder<MessageId> ids;
        private Optional<ImmutableSet<MessageProperty>> properties;

        private Builder() {
            accountId = Optional.empty();
            ids = ImmutableList.builder();
            properties = Optional.empty();
        }
        
        public Builder accountId(String accountId) {
            this.accountId = Optional.of(accountId);
            return this;
        }

        public Builder ids(MessageId... ids) {
            this.ids.addAll(Arrays.asList(ids));
            return this;
        }

        public Builder properties(MessageProperty... properties) {
            this.properties = Optional.of(ImmutableSet.copyOf(properties));
            return this;
        }
        
        public GetMessagesRequest build() {
            return new GetMessagesRequest(accountId, ids.build(), properties);
        }
    }

    private final Optional<String> accountId;
    private final ImmutableList<MessageId> ids;
    private final Optional<ImmutableSet<MessageProperty>> properties;

    public GetMessagesRequest(Optional<String> accountId, ImmutableList<MessageId> ids, Optional<ImmutableSet<MessageProperty>> properties) {
        this.accountId = accountId;
        this.ids = ids;
        this.properties = properties;
    }
    
    public Optional<String> getAccountId() {
        return accountId;
    }
    
    public ImmutableList<MessageId> getIds() {
        return ids;
    }
    
    public Optional<ImmutableSet<MessageProperty>> getProperties() {
        return properties;
    }
}
