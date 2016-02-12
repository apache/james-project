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
import java.util.function.BiFunction;

import com.google.common.base.Strings;
import org.apache.commons.lang.NotImplementedException;
import org.apache.james.jmap.methods.MessageWithId;
import org.apache.james.jmap.methods.Method;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

@JsonDeserialize(builder = SetMessagesResponse.Builder.class)
public class SetMessagesResponse implements Method.Response {

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        public static Builder accumulator(Builder accumulator, SetMessagesResponse response) {
            return response.mergeInto(accumulator);
        }

        public static Builder combiner(Builder firstBuilder, Builder secondBuilder) {
            return secondBuilder.build().mergeInto(firstBuilder);
        }

        private String accountId;
        private String oldState;
        private String newState;
        private ImmutableMap.Builder<String, Message> created;
        private ImmutableList.Builder<MessageId> updated;
        private ImmutableList.Builder<MessageId> destroyed;
        private ImmutableMap.Builder<MessageId, SetError> notCreated;
        private ImmutableMap.Builder<MessageId, SetError> notUpdated;
        private ImmutableMap.Builder<MessageId, SetError> notDestroyed;

        private Builder() {
            created = ImmutableMap.builder();
            updated = ImmutableList.builder();
            destroyed = ImmutableList.builder();
            notCreated = ImmutableMap.builder();
            notUpdated = ImmutableMap.builder();
            notDestroyed = ImmutableMap.builder();
        }

        public Builder accountId(String accountId) {
            throw new NotImplementedException();
        }

        public Builder oldState(String oldState) {
            throw new NotImplementedException();
        }

        public Builder newState(String newState) {
            throw new NotImplementedException();
        }

        public Builder created(Map<String, Message> created) {
            this.created.putAll(created);
            return this;
        }

        public Builder updated(List<MessageId> updated) {
            this.updated.addAll(updated);
            return this;
        }

        public Builder destroyed(MessageId destroyed) {
            this.destroyed.add(destroyed);
            return this;
        }

        public Builder destroyed(List<MessageId> destroyed) {
            this.destroyed.addAll(destroyed);
            return this;
        }

        public Builder notCreated(Map<MessageId, SetError> notCreated) {
            this.notCreated.putAll(notCreated);
            return this;
        }

        public Builder notUpdated(Map<MessageId, SetError> notUpdated) {
            this.notUpdated.putAll(notUpdated);
            return this;
        }

        public Builder notDestroyed(MessageId messageId, SetError notDestroyed) {
            this.notDestroyed.put(messageId, notDestroyed);
            return this;
        }

        public Builder notDestroyed(Map<MessageId, SetError> notDestroyed) {
            this.notDestroyed.putAll(notDestroyed);
            return this;
        }

        public SetMessagesResponse build() {
            return new SetMessagesResponse(accountId, oldState, newState, 
                    created.build(), updated.build(), destroyed.build(), notCreated.build(), notUpdated.build(), notDestroyed.build());
        }
    }

    private final String accountId;
    private final String oldState;
    private final String newState;
    private final Map<String, Message> created;
    private final List<MessageId> updated;
    private final List<MessageId> destroyed;
    private final Map<MessageId, SetError> notCreated;
    private final Map<MessageId, SetError> notUpdated;
    private final Map<MessageId, SetError> notDestroyed;

    @VisibleForTesting SetMessagesResponse(String accountId, String oldState, String newState, Map<String, Message> created, List<MessageId> updated, List<MessageId> destroyed,
            Map<MessageId, SetError> notCreated, Map<MessageId, SetError> notUpdated, Map<MessageId, SetError> notDestroyed) {
        this.accountId = accountId;
        this.oldState = oldState;
        this.newState = newState;
        this.created = created;
        this.updated = updated;
        this.destroyed = destroyed;
        this.notCreated = notCreated;
        this.notUpdated = notUpdated;
        this.notDestroyed = notDestroyed;
    }

    @JsonSerialize
    public String getAccountId() {
        return accountId;
    }

    @JsonSerialize
    public String getOldState() {
        return oldState;
    }

    @JsonSerialize
    public String getNewState() {
        return newState;
    }

    @JsonSerialize
    public Map<String, Message> getCreated() {
        return created;
    }

    @JsonSerialize
    public List<MessageId> getUpdated() {
        return updated;
    }

    @JsonSerialize
    public List<MessageId> getDestroyed() {
        return destroyed;
    }

    @JsonSerialize
    public Map<MessageId, SetError> getNotCreated() {
        return notCreated;
    }

    @JsonSerialize
    public Map<MessageId, SetError> getNotUpdated() {
        return notUpdated;
    }

    @JsonSerialize
    public Map<MessageId, SetError> getNotDestroyed() {
        return notDestroyed;
    }

    public SetMessagesResponse.Builder mergeInto(SetMessagesResponse.Builder responseBuilder) {
        responseBuilder.created(getCreated());
        responseBuilder.updated(getUpdated());
        responseBuilder.destroyed(getDestroyed());
        responseBuilder.notCreated(getNotCreated());
        responseBuilder.notUpdated(getNotUpdated());
        responseBuilder.notDestroyed(getNotDestroyed());
        if(! Strings.isNullOrEmpty(getAccountId())) {
            responseBuilder.accountId(getAccountId());
        }
        if(! Strings.isNullOrEmpty(getOldState())) {
            responseBuilder.accountId(getOldState());
        }
        if(! Strings.isNullOrEmpty(getNewState())) {
            responseBuilder.accountId(getAccountId());
        }
        return responseBuilder;
    }
}
