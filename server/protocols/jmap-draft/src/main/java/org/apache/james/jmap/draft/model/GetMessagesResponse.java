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

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.james.jmap.methods.Method;
import org.apache.james.jmap.model.message.view.MessageView;
import org.apache.james.mailbox.model.MessageId;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

@JsonDeserialize(builder = GetMessagesResponse.Builder.class)
public class GetMessagesResponse implements Method.Response {

    public static Builder builder() {
        return new Builder();
    }
    
    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private ImmutableSet<MessageView> messages;
        private Set<MessageId> expectedMessageIds;

        private Builder() {
            this.messages = ImmutableSet.of();
        }

        @JsonIgnore
        public Builder message(MessageView message) {
            this.messages = ImmutableSet.of(message);
            return this;
        }

        public Builder messages(Collection<? extends MessageView> messages) {
            this.messages = ImmutableSet.copyOf(messages);
            return this;
        }

        public Builder expectedMessageIds(List<MessageId> expectedMessageIds) {
            this.expectedMessageIds = ImmutableSet.copyOf(expectedMessageIds);
            return this;
        }
        
        public GetMessagesResponse build() {
            Preconditions.checkState(messages != null);
            return new GetMessagesResponse(messages, messagesNotFound());
        }
        

        private Set<MessageId> messagesNotFound() {
            if (expectedMessageIds.size() == messages.size()) {
                return ImmutableSet.of();
            }
            return Sets.difference(expectedMessageIds,
                messages.stream().map(MessageView::getId).collect(Collectors.toSet()));
        }
    }
    
    
    
    private final Set<MessageView> messages;
    private final Set<MessageId> messagesNotFound;

    private GetMessagesResponse(Set<MessageView> messages, Set<MessageId> messagesNotFound) {
        this.messages = messages;
        this.messagesNotFound = messagesNotFound;
    }

    @JsonSerialize
    public Set<MessageView> list() {
        return messages;
    }
    
    @JsonSerialize
    public Set<MessageId> notFound() {
        return messagesNotFound;
    }

}
