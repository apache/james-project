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

import java.util.Map;

import org.apache.james.jmap.methods.JmapRequest;
import org.apache.james.jmap.model.mailbox.MailboxRequest;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.collect.ImmutableMap;

@JsonDeserialize(builder = SetMailboxesRequest.Builder.class)
public class SetMailboxesRequest implements JmapRequest {

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private ImmutableMap.Builder<MailboxCreationId, MailboxRequest> create;

        private Builder() {
            create = ImmutableMap.builder();
        }

        public Builder create(Map<MailboxCreationId, MailboxRequest> requests) {
            create.putAll(requests);
            return this;
        }

        public Builder create(MailboxCreationId creationId, MailboxRequest mailbox) {
            create.put(creationId, mailbox);
            return this;
        }

        public SetMailboxesRequest build() {
            return new SetMailboxesRequest(create.build());
        }
    }

    private final ImmutableMap<MailboxCreationId, MailboxRequest> create;

    private SetMailboxesRequest(ImmutableMap<MailboxCreationId, MailboxRequest> create) {
        this.create = create;
    }

    public ImmutableMap<MailboxCreationId, MailboxRequest> getCreate() {
        return create;
    }
}
