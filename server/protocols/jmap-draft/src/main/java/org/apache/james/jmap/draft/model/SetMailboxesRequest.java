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
import org.apache.james.jmap.draft.model.mailbox.MailboxCreateRequest;
import org.apache.james.jmap.draft.model.mailbox.MailboxUpdateRequest;
import org.apache.james.jmap.methods.JmapRequest;
import org.apache.james.mailbox.model.MailboxId;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

@JsonDeserialize(builder = SetMailboxesRequest.Builder.class)
public class SetMailboxesRequest implements JmapRequest {

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private final ImmutableMap.Builder<MailboxCreationId, MailboxCreateRequest> create;
        private final ImmutableMap.Builder<MailboxId, MailboxUpdateRequest> update;
        private final ImmutableList.Builder<MailboxId> destroy;

        private Builder() {
            create = ImmutableMap.builder();
            update = ImmutableMap.builder();
            destroy = ImmutableList.builder();
        }

        public Builder create(Map<MailboxCreationId, MailboxCreateRequest> requests) {
            create.putAll(requests);
            return this;
        }

        public Builder create(MailboxCreationId creationId, MailboxCreateRequest mailbox) {
            create.put(creationId, mailbox);
            return this;
        }
        
        public Builder accountId(String accountId) {
            throw new NotImplementedException("not implemented");
        }
        
        public Builder ifInState(String state) {
            throw new NotImplementedException("not implemented");
        }
        
        public Builder update(MailboxId mailboxId, MailboxUpdateRequest mailboxUpdateRequest) {
            update.put(mailboxId, mailboxUpdateRequest);
            return this;
        }
        
        public Builder update(Map<MailboxId, MailboxUpdateRequest> updates) {
            update.putAll(updates);
            return this;
        }
        
        public Builder destroy(List<MailboxId> deletions) {
            destroy.addAll(deletions);
            return this;
        }

        public SetMailboxesRequest build() {
            return new SetMailboxesRequest(create.build(), update.build(), destroy.build());
        }
    }

    private final ImmutableMap<MailboxCreationId, MailboxCreateRequest> create;
    private final ImmutableMap<MailboxId, MailboxUpdateRequest> update;
    private final ImmutableList<MailboxId> destroy;

    @VisibleForTesting
    SetMailboxesRequest(ImmutableMap<MailboxCreationId, MailboxCreateRequest> create, ImmutableMap<MailboxId,MailboxUpdateRequest> update, ImmutableList<MailboxId> destroy) {
        this.create = create;
        this.update = update;
        this.destroy = destroy;
    }

    public ImmutableMap<MailboxCreationId, MailboxCreateRequest> getCreate() {
        return create;
    }

    public ImmutableMap<MailboxId,MailboxUpdateRequest> getUpdate() {
        return update;
    }

    public ImmutableList<MailboxId> getDestroy() {
        return destroy;
    }
}
