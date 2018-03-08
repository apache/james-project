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

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.jmap.methods.Method;
import org.apache.james.mailbox.model.MessageId;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class SetMessagesResponse implements Method.Response {

    public static Builder builder() {
        return new Builder();
    }

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
        private final ImmutableMap.Builder<CreationMessageId, Message> created;
        private final ImmutableList.Builder<MessageId> updated;
        private final ImmutableList.Builder<MessageId> destroyed;
        private final ImmutableMap.Builder<CreationMessageId, SetError> notCreated;
        private final ImmutableMap.Builder<CreationMessageId, SetError> MDNNotSent;
        private final ImmutableMap.Builder<MessageId, SetError> notUpdated;
        private final ImmutableMap.Builder<MessageId, SetError> notDestroyed;

        private Builder() {
            created = ImmutableMap.builder();
            updated = ImmutableList.builder();
            destroyed = ImmutableList.builder();
            notCreated = ImmutableMap.builder();
            MDNNotSent = ImmutableMap.builder();
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

        public Builder created(CreationMessageId creationMessageId, Message message) {
            this.created.put(creationMessageId, message);
            return this;
        }

        public Builder created(Map<CreationMessageId, Message> created) {
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

        public Builder notCreated(Map<CreationMessageId, SetError> notCreated) {
            this.notCreated.putAll(notCreated);
            return this;
        }

        public Builder MDNNotSent(Map<CreationMessageId, SetError> notCreated) {
            this.MDNNotSent.putAll(notCreated);
            return this;
        }

        public Builder MDNNotSent(CreationMessageId creationMessageId, SetError error) {
            this.MDNNotSent.put(creationMessageId, error);
            return this;
        }
        
        public Builder notCreated(CreationMessageId id, SetError error) {
            this.notCreated.put(id, error);
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

        public Builder mergeWith(Builder otherBuilder) {
            return otherBuilder.build().mergeInto(this);
        }

        public SetMessagesResponse build() {
            return new SetMessagesResponse(accountId, oldState, newState, 
                created.build(), updated.build(), destroyed.build(),
                notCreated.build(), MDNNotSent.build(), notUpdated.build(), notDestroyed.build());
        }
    }

    private final String accountId;
    private final String oldState;
    private final String newState;
    private final ImmutableMap<CreationMessageId, Message> created;
    private final ImmutableList<MessageId> updated;
    private final ImmutableList<MessageId> destroyed;
    private final ImmutableMap<CreationMessageId, SetError> notCreated;
    private final ImmutableMap<CreationMessageId, SetError> MDNNotSent;
    private final ImmutableMap<MessageId, SetError> notUpdated;
    private final ImmutableMap<MessageId, SetError> notDestroyed;

    @VisibleForTesting SetMessagesResponse(String accountId, String oldState, String newState, ImmutableMap<CreationMessageId, Message> created, ImmutableList<MessageId> updated, ImmutableList<MessageId> destroyed,
                                           ImmutableMap<CreationMessageId, SetError> notCreated, ImmutableMap<CreationMessageId, SetError> mdnNotSent, ImmutableMap<MessageId, SetError> notUpdated, ImmutableMap<MessageId, SetError> notDestroyed) {
        this.accountId = accountId;
        this.oldState = oldState;
        this.newState = newState;
        this.created = created;
        this.updated = updated;
        this.destroyed = destroyed;
        this.notCreated = notCreated;
        this.MDNNotSent = mdnNotSent;
        this.notUpdated = notUpdated;
        this.notDestroyed = notDestroyed;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getOldState() {
        return oldState;
    }

    public String getNewState() {
        return newState;
    }

    public ImmutableMap<CreationMessageId, Message> getCreated() {
        return created;
    }

    public ImmutableList<MessageId> getUpdated() {
        return updated;
    }

    public ImmutableList<MessageId> getDestroyed() {
        return destroyed;
    }

    public ImmutableMap<CreationMessageId, SetError> getNotCreated() {
        return notCreated;
    }

    public ImmutableMap<MessageId, SetError> getNotUpdated() {
        return notUpdated;
    }

    public ImmutableMap<MessageId, SetError> getNotDestroyed() {
        return notDestroyed;
    }

    @JsonProperty("MDNNotSent")
    public ImmutableMap<CreationMessageId, SetError> getMDNNotSent() {
        return MDNNotSent;
    }

    public SetMessagesResponse.Builder mergeInto(SetMessagesResponse.Builder responseBuilder) {
        responseBuilder.created(getCreated());
        responseBuilder.updated(getUpdated());
        responseBuilder.destroyed(getDestroyed());
        responseBuilder.notCreated(getNotCreated());
        responseBuilder.notUpdated(getNotUpdated());
        responseBuilder.notDestroyed(getNotDestroyed());
        responseBuilder.MDNNotSent(getMDNNotSent());
        if (! Strings.isNullOrEmpty(getAccountId())) {
            responseBuilder.accountId(getAccountId());
        }
        if (! Strings.isNullOrEmpty(getOldState())) {
            responseBuilder.accountId(getOldState());
        }
        if (! Strings.isNullOrEmpty(getNewState())) {
            responseBuilder.accountId(getAccountId());
        }
        return responseBuilder;
    }
}
