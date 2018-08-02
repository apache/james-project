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

package org.apache.james.mailbox.store.json.event.dto;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.QuotaRoot;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class EventDataTransferObject {

    public static class Builder {

        private EventType type;
        private MailboxDataTransferObject mailbox;
        private MailboxSessionDataTransferObject session;
        private List<MessageUid> uids;
        private Map<MessageUid, MessageMetaDataDataTransferObject> metaData;
        private List<UpdatedFlagsDataTransferObject> updatedFlags;
        private MailboxPathDataTransferObject from;
        private Optional<QuotaRoot> quotaRoot;
        private Optional<QuotaCount> deletedMessageCount;
        private Optional<QuotaSize> totalDeletedSize;

        public Builder type(EventType type) {
            this.type = type;
            return this;
        }

        public Builder mailbox(MailboxDataTransferObject mailbox) {
            this.mailbox = mailbox;
            return this;
        }

        public Builder session(MailboxSessionDataTransferObject session) {
            this.session = session;
            return this;
        }

        public Builder from(MailboxPathDataTransferObject from) {
            this.from = from;
            return this;
        }

        public Builder uids(List<MessageUid> uids) {
            this.uids = uids;
            return this;
        }

        public Builder metaData(Map<MessageUid, MessageMetaDataDataTransferObject> metaData) {
            this.metaData = metaData;
            return this;
        }

        public Builder updatedFlags(List<UpdatedFlagsDataTransferObject> updatedFlagsList) {
            this.updatedFlags = updatedFlagsList;
            return this;
        }

        public Builder quotaRoot(Optional<QuotaRoot> quotaRoot) {
            this.quotaRoot = quotaRoot;
            return this;
        }

        public Builder deletedMessageCount(Optional<QuotaCount> deletedMessageCount) {
            this.deletedMessageCount = deletedMessageCount;
            return this;
        }

        public Builder totalDeletedSize(Optional<QuotaSize> totalDeletedSize) {
            this.totalDeletedSize = totalDeletedSize;
            return this;
        }

        public EventDataTransferObject build() {
            return new EventDataTransferObject(type, mailbox, session, uids, metaData, updatedFlags, from, quotaRoot, deletedMessageCount, totalDeletedSize);
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonProperty()
    private EventType type;
    @JsonProperty()
    private MailboxDataTransferObject mailbox;
    @JsonProperty()
    private MailboxSessionDataTransferObject session;
    @JsonProperty()
    private List<MessageUid> uids;
    @JsonProperty()
    private Map<MessageUid, MessageMetaDataDataTransferObject> metaData;
    @JsonProperty()
    private List<UpdatedFlagsDataTransferObject> updatedFlags;
    @JsonProperty()
    private MailboxPathDataTransferObject from;
    @JsonProperty()
    private Optional<QuotaRoot> quotaRoot;
    @JsonProperty()
    private Optional<QuotaCount> deletedMessageCount;
    @JsonProperty()
    private Optional<QuotaSize> totalDeletedSize;

    public EventDataTransferObject() {}

    public EventDataTransferObject(EventType type,
                                   MailboxDataTransferObject mailbox,
                                   MailboxSessionDataTransferObject session,
                                   List<MessageUid> uids,
                                   Map<MessageUid, MessageMetaDataDataTransferObject> metaData,
                                   List<UpdatedFlagsDataTransferObject> updatedFlags,
                                   MailboxPathDataTransferObject from, 
                                   Optional<QuotaRoot> quotaRoot, 
                                   Optional<QuotaCount> deletedMessageCount,
                                   Optional<QuotaSize> totalDeletedSize) {
        this.type = type;
        this.mailbox = mailbox;
        this.session = session;
        this.uids = uids;
        this.metaData = metaData;
        this.updatedFlags = updatedFlags;
        this.from = from;
        this.quotaRoot = quotaRoot;
        this.deletedMessageCount = deletedMessageCount;
        this.totalDeletedSize = totalDeletedSize;
    }

    @JsonIgnore
    public EventType getType() {
        return type;
    }

    @JsonIgnore
    public MailboxDataTransferObject getMailbox() {
        return mailbox;
    }

    @JsonIgnore
    public MailboxSessionDataTransferObject getSession() {
        return session;
    }

    @JsonIgnore
    public List<MessageUid> getUids() {
        return uids;
    }

    @JsonIgnore
    public Map<MessageUid, MessageMetaDataDataTransferObject> getMetaDataProxyMap() {
        return metaData;
    }

    @JsonIgnore
    public List<UpdatedFlagsDataTransferObject> getUpdatedFlags() {
        return updatedFlags;
    }

    @JsonIgnore
    public MailboxPathDataTransferObject getFrom() {
        return from;
    }

    @JsonIgnore
    public Optional<QuotaRoot> getQuotaRoot() {
        return quotaRoot;
    }

    @JsonIgnore
    public Optional<QuotaCount> getDeletedMessageCount() {
        return deletedMessageCount;
    }

    @JsonIgnore
    public Optional<QuotaSize> getTotalDeletedSize() {
        return totalDeletedSize;
    }
}