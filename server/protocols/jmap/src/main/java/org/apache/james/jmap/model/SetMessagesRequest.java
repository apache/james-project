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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.jmap.methods.JmapRequest;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

@JsonDeserialize(builder = SetMessagesRequest.Builder.class)
public class SetMessagesRequest implements JmapRequest {

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private String accountId;
        private String ifInState;
        private ImmutableList.Builder<Message> create;
        private ImmutableMap.Builder<MessageId, UpdateMessagePatch> update;
        private ImmutableList.Builder<MessageId> destroy;

        private Builder() {
            create = ImmutableList.builder();
            update = ImmutableMap.builder();
            destroy = ImmutableList.builder();
        }

        public Builder accountId(String accountId) {
            if (accountId != null) {
                throw new NotImplementedException();
            }
            return this;
        }

        public Builder ifInState(String ifInState) {
            if (ifInState != null) {
                throw new NotImplementedException();
            }
            return this;
        }

        public Builder create(List<Message> create) {
            if (create != null && !create.isEmpty()) {
                throw new NotImplementedException();
            }
            return this;
        }

        public Builder update(Map<MessageId, UpdateMessagePatch> updates) {
            this.update.putAll(updates);
            return this;
        }

        public Builder destroy(List<MessageId> destroy) {
            this.destroy.addAll(destroy);
            return this;
        }

        public SetMessagesRequest build() {
            return new SetMessagesRequest(Optional.ofNullable(accountId), Optional.ofNullable(ifInState), create.build(), update.build(), destroy.build());
        }
    }

    private final Optional<String> accountId;
    private final Optional<String> ifInState;
    private final List<Message> create;
    private final Map<MessageId, UpdateMessagePatch> update;
    private final List<MessageId> destroy;

    @VisibleForTesting SetMessagesRequest(Optional<String> accountId, Optional<String> ifInState, List<Message> create, Map<MessageId, UpdateMessagePatch> update, List<MessageId> destroy) {
        this.accountId = accountId;
        this.ifInState = ifInState;
        this.create = create;
        this.update = update;
        this.destroy = destroy;
    }

    public Optional<String> getAccountId() {
        return accountId;
    }

    public Optional<String> getIfInState() {
        return ifInState;
    }

    public List<Message> getCreate() {
        return create;
    }

    public Map<MessageId, UpdateMessagePatch> getUpdate() {
        return update;
    }

    public List<MessageId> getDestroy() {
        return destroy;
    }
}
