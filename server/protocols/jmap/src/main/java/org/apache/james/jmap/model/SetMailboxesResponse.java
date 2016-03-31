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

import org.apache.james.jmap.methods.Method;
import org.apache.james.jmap.model.mailbox.Mailbox;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class SetMailboxesResponse implements Method.Response {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final ImmutableMap.Builder<MailboxCreationId, Mailbox> created;
        private final ImmutableList.Builder<String> updated;
        private final ImmutableList.Builder<String> destroyed;
        private final ImmutableMap.Builder<MailboxCreationId, SetError> notCreated;
        private final ImmutableMap.Builder<String, SetError> notUpdated;
        private final ImmutableMap.Builder<String, SetError> notDestroyed;

        private Builder() {
            created = ImmutableMap.builder();
            updated = ImmutableList.builder();
            destroyed = ImmutableList.builder();
            notCreated = ImmutableMap.builder();
            notUpdated = ImmutableMap.builder();
            notDestroyed = ImmutableMap.builder();
        }

        public Builder created(MailboxCreationId creationId, Mailbox mailbox) {
            created.put(creationId, mailbox);
            return this;
        }

        public Builder created(ImmutableMap<MailboxCreationId, Mailbox> created) {
            this.created.putAll(created);
            return this;
        }

        public Builder updated(String mailboxId) {
            updated.add(mailboxId);
            return this;
        }
        
        public Builder updated(List<String> mailboxIds) {
            updated.addAll(mailboxIds);
            return this;
        }
        
        public Builder destroyed(String mailboxId) {
            destroyed.add(mailboxId);
            return this;
        }
        
        public Builder destroyed(ImmutableList<String> destroyed) {
            this.destroyed.addAll(destroyed);
            return this;
        }
        
        public Builder notCreated(Map<MailboxCreationId, SetError> notCreated) {
            this.notCreated.putAll(notCreated);
            return this;
        }

        public Builder notCreated(MailboxCreationId mailboxCreationId, SetError setError) {
            this.notCreated.put(mailboxCreationId, setError);
            return this;
        }
        
        public Builder notUpdated(String mailboxId, SetError setError) {
            notUpdated.put(mailboxId, setError);
            return this;
        }
        
        public Builder notUpdated(Map<String, SetError> notUpdated) {
            this.notUpdated.putAll(notUpdated);
            return this;
        }

        public Builder notDestroyed(String mailboxId, SetError setError) {
            notDestroyed.put(mailboxId, setError);
            return this;
        }

        public Builder notDestroyed(ImmutableMap<String, SetError> notDestroyed) {
            this.notDestroyed.putAll(notDestroyed);
            return this;
        }

        public SetMailboxesResponse build() {
            return new SetMailboxesResponse(created.build(), updated.build(), destroyed.build(), notCreated.build(), notUpdated.build(), notDestroyed.build());
        }
    }

    private final ImmutableMap<MailboxCreationId, Mailbox> created;
    private final ImmutableList<String> updated;
    private final ImmutableList<String> destroyed;
    private final ImmutableMap<MailboxCreationId, SetError> notCreated;
    private final ImmutableMap<String, SetError> notUpdated;
    private final ImmutableMap<String, SetError> notDestroyed;

    @VisibleForTesting
    SetMailboxesResponse(ImmutableMap<MailboxCreationId, Mailbox> created, ImmutableList<String> updated, ImmutableList<String> destroyed,
            ImmutableMap<MailboxCreationId, SetError> notCreated, ImmutableMap<String, SetError> notUpdated, ImmutableMap<String, SetError> notDestroyed) {
        this.created = created;
        this.updated = updated;
        this.destroyed = destroyed;
        this.notCreated = notCreated;
        this.notUpdated = notUpdated;
        this.notDestroyed = notDestroyed;
    }

    public ImmutableMap<MailboxCreationId, Mailbox> getCreated() {
        return created;
    }

    public ImmutableList<String> getUpdated() {
        return updated;
    }

    public ImmutableList<String> getDestroyed() {
        return destroyed;
    }
    
    public Map<MailboxCreationId, SetError> getNotCreated() {
        return notCreated;
    }

    public ImmutableMap<String, SetError> getNotUpdated() {
        return notUpdated;
    }
    
    public ImmutableMap<String, SetError> getNotDestroyed() {
        return notDestroyed;
    }

    public SetMailboxesResponse.Builder mergeInto(SetMailboxesResponse.Builder responseBuilder) {
        return responseBuilder
            .created(getCreated())
            .updated(getUpdated())
            .destroyed(getDestroyed())
            .notCreated(getNotCreated())
            .notUpdated(getNotUpdated())
            .notDestroyed(getNotDestroyed());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(created, notCreated, destroyed, notDestroyed);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SetMailboxesResponse) {
            SetMailboxesResponse other = (SetMailboxesResponse) obj;
            return Objects.equal(this.created, other.created)
                && Objects.equal(this.updated, other.updated)
                && Objects.equal(this.destroyed, other.destroyed)
                && Objects.equal(this.notCreated, other.notCreated)
                && Objects.equal(this.notUpdated, other.notUpdated)
                && Objects.equal(this.notDestroyed, other.notDestroyed);
        }
        return false;
    }
}
