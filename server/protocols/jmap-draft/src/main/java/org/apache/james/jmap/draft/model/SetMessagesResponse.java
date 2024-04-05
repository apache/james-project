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
import java.util.Map;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.jmap.methods.Method;
import org.apache.james.jmap.model.message.view.MessageFullView;
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
        private final ImmutableMap.Builder<CreationMessageId, MessageFullView> created;
        private final ImmutableMap.Builder<CreationMessageId, MessageId> mdnSent;
        private final ImmutableList.Builder<MessageId> updated;
        private final ImmutableList.Builder<MessageId> destroyed;
        private final ImmutableMap.Builder<CreationMessageId, SetError> notCreated;
        private final ImmutableMap.Builder<CreationMessageId, SetError> mdnNotSent;
        private final ImmutableMap.Builder<MessageId, SetError> notUpdated;
        private final ImmutableMap.Builder<MessageId, SetError> notDestroyed;

        private Builder() {
            created = ImmutableMap.builder();
            mdnSent = ImmutableMap.builder();
            updated = ImmutableList.builder();
            destroyed = ImmutableList.builder();
            notCreated = ImmutableMap.builder();
            mdnNotSent = ImmutableMap.builder();
            notUpdated = ImmutableMap.builder();
            notDestroyed = ImmutableMap.builder();
        }

        public Builder accountId(String accountId) {
            throw new NotImplementedException("not implemented");
        }

        public Builder oldState(String oldState) {
            throw new NotImplementedException("not implemented");
        }

        public Builder newState(String newState) {
            throw new NotImplementedException("not implemented");
        }

        public Builder created(CreationMessageId creationMessageId, MessageFullView message) {
            this.created.put(creationMessageId, message);
            return this;
        }

        public Builder created(Map<CreationMessageId, MessageFullView> created) {
            this.created.putAll(created);
            return this;
        }

        public Builder mdnSent(CreationMessageId creationMessageId, MessageId messageId) {
            this.mdnSent.put(creationMessageId, messageId);
            return this;
        }

        public Builder mdnSent(ImmutableMap<CreationMessageId, MessageId> sent) {
            this.mdnSent.putAll(sent);
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

        public Builder mdnNotSent(Map<CreationMessageId, SetError> notCreated) {
            this.mdnNotSent.putAll(notCreated);
            return this;
        }

        public Builder mdnNotSent(CreationMessageId creationMessageId, SetError error) {
            this.mdnNotSent.put(creationMessageId, error);
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

        public Builder notUpdated(MessageId messageId, SetError error) {
            this.notUpdated.put(messageId, error);
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
                created.build(), mdnSent.build(), updated.build(), destroyed.build(),
                notCreated.build(), mdnNotSent.build(), notUpdated.build(), notDestroyed.build());
        }
    }

    private final String accountId;
    private final String oldState;
    private final String newState;
    private final ImmutableMap<CreationMessageId, MessageFullView> created;
    private final ImmutableMap<CreationMessageId, MessageId> mdnSent;
    private final ImmutableList<MessageId> updated;
    private final ImmutableList<MessageId> destroyed;
    private final ImmutableMap<CreationMessageId, SetError> notCreated;
    private final ImmutableMap<CreationMessageId, SetError> mdnNotSent;
    private final ImmutableMap<MessageId, SetError> notUpdated;
    private final ImmutableMap<MessageId, SetError> notDestroyed;

    @VisibleForTesting SetMessagesResponse(String accountId, String oldState, String newState, ImmutableMap<CreationMessageId, MessageFullView> created, ImmutableMap<CreationMessageId, MessageId> mdnSent, ImmutableList<MessageId> updated, ImmutableList<MessageId> destroyed,
                                           ImmutableMap<CreationMessageId, SetError> notCreated, ImmutableMap<CreationMessageId, SetError> mdnNotSent, ImmutableMap<MessageId, SetError> notUpdated, ImmutableMap<MessageId, SetError> notDestroyed) {
        this.accountId = accountId;
        this.oldState = oldState;
        this.newState = newState;
        this.created = created;
        this.mdnSent = mdnSent;
        this.updated = updated;
        this.destroyed = destroyed;
        this.notCreated = notCreated;
        this.mdnNotSent = mdnNotSent;
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

    public ImmutableMap<CreationMessageId, MessageFullView> getCreated() {
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

    @JsonProperty("MDNSent")
    public ImmutableMap<CreationMessageId, MessageId> getMDNSent() {
        return mdnSent;
    }

    @JsonProperty("MDNNotSent")
    public ImmutableMap<CreationMessageId, SetError> getMDNNotSent() {
        return mdnNotSent;
    }

    public SetMessagesResponse.Builder mergeInto(SetMessagesResponse.Builder responseBuilder) {
        responseBuilder.created(getCreated());
        responseBuilder.updated(getUpdated());
        responseBuilder.destroyed(getDestroyed());
        responseBuilder.notCreated(getNotCreated());
        responseBuilder.notUpdated(getNotUpdated());
        responseBuilder.notDestroyed(getNotDestroyed());
        responseBuilder.mdnNotSent(getMDNNotSent());
        responseBuilder.mdnSent(getMDNSent());
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
