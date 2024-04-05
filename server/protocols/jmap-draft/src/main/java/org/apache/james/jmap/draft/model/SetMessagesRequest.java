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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.apache.james.jmap.draft.exceptions.JmapFieldNotSupportedException;
import org.apache.james.jmap.draft.methods.UpdateMessagePatchConverter;
import org.apache.james.jmap.draft.methods.ValueWithId.CreationMessageEntry;
import org.apache.james.jmap.draft.methods.ValueWithId.MDNCreationEntry;
import org.apache.james.jmap.methods.JmapRequest;
import org.apache.james.mailbox.model.MessageId;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

@JsonDeserialize(builder = SetMessagesRequest.Builder.class)
public class SetMessagesRequest implements JmapRequest {
    private static final String ISSUER = "SetMessagesRequest";

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private String accountId;
        private String ifInState;
        private HashMap<CreationMessageId, CreationMessage> create;
        private HashMap<CreationMessageId, JmapMDN> sendMDN;
        private ImmutableMap.Builder<MessageId, Function<UpdateMessagePatchConverter, UpdateMessagePatch>> updatesProvider;

        private ImmutableList.Builder<MessageId> destroy;

        private Builder() {
            create = new HashMap<>();
            sendMDN = new HashMap<>();
            updatesProvider = ImmutableMap.builder();
            destroy = ImmutableList.builder();
        }

        public Builder accountId(String accountId) {
            if (accountId != null) {
                throw new JmapFieldNotSupportedException(ISSUER, "accountId");
            }
            return this;
        }

        public Builder ifInState(String ifInState) {
            if (ifInState != null) {
                throw new JmapFieldNotSupportedException(ISSUER, "ifInState");
            }
            return this;
        }

        public Builder create(CreationMessageId creationMessageId, CreationMessage creation) {
            this.create.put(creationMessageId, creation);
            return this;
        }

        public Builder create(Map<CreationMessageId, CreationMessage> creations) {
            this.create.putAll(creations);
            return this;
        }

        public Builder sendMDN(CreationMessageId creationMessageId, JmapMDN mdn) {
            this.sendMDN.put(creationMessageId, mdn);
            return this;
        }

        public Builder sendMDN(Map<CreationMessageId, JmapMDN> mdns) {
            this.sendMDN.putAll(mdns);
            return this;
        }

        public Builder update(Map<MessageId, ObjectNode> updates) {
            this.updatesProvider.putAll(Maps.transformValues(updates, json -> converter -> converter.fromJsonNode(json)));
            return this;
        }

        public Builder destroy(List<MessageId> destroy) {
            this.destroy.addAll(destroy);
            return this;
        }

        public SetMessagesRequest build() {
            return new SetMessagesRequest(Optional.ofNullable(accountId), Optional.ofNullable(ifInState), 
                    messageCreations(), mdnSendings(), updatesProvider.build(), destroy.build());
        }

        private ImmutableList<CreationMessageEntry> messageCreations() {
            return create.entrySet().stream()
                    .map(entry -> new CreationMessageEntry(entry.getKey(), entry.getValue()))
                    .collect(ImmutableList.toImmutableList());
        }

        private ImmutableList<MDNCreationEntry> mdnSendings() {
            return sendMDN.entrySet().stream()
                    .map(entry -> new MDNCreationEntry(entry.getKey(), entry.getValue()))
                    .collect(ImmutableList.toImmutableList());
        }
    }

    private final Optional<String> accountId;
    private final Optional<String> ifInState;
    private final List<CreationMessageEntry> create;
    private final List<MDNCreationEntry> sendMDN;
    private final Map<MessageId, Function<UpdateMessagePatchConverter, UpdateMessagePatch>> update;
    private final List<MessageId> destroy;

    @VisibleForTesting SetMessagesRequest(Optional<String> accountId, Optional<String> ifInState,
                                          List<CreationMessageEntry> create, List<MDNCreationEntry> sendMDN,
                                          Map<MessageId, Function<UpdateMessagePatchConverter, UpdateMessagePatch>> update,
                                          List<MessageId> destroy) {
        this.accountId = accountId;
        this.ifInState = ifInState;
        this.create = create;
        this.sendMDN = sendMDN;
        this.update = update;
        this.destroy = destroy;
    }

    public Optional<String> getAccountId() {
        return accountId;
    }

    public Optional<String> getIfInState() {
        return ifInState;
    }

    public List<CreationMessageEntry> getCreate() {
        return create;
    }

    public List<MDNCreationEntry> getSendMDN() {
        return sendMDN;
    }

    public Map<MessageId, UpdateMessagePatch> buildUpdatePatches(UpdateMessagePatchConverter converter) {
        return Maps.transformValues(update, func -> func.apply(converter));
    }

    public boolean hasUpdates() {
        return !update.isEmpty();
    }

    public List<MessageId> getDestroy() {
        return destroy;
    }

}
