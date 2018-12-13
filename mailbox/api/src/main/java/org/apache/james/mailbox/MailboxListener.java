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

package org.apache.james.mailbox;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;

import org.apache.james.core.User;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.UpdatedFlags;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;


/**
 * Listens to <code>Mailbox</code> events.<br>
 * Note that listeners may be removed asynchronously.
 */
public interface MailboxListener {

    enum ListenerType {
        ONCE,
        EACH_NODE,
        MAILBOX
    }

    enum ExecutionMode {
        SYNCHRONOUS,
        ASYNCHRONOUS
    }

    ListenerType getType();

    default ExecutionMode getExecutionMode() {
        return ExecutionMode.SYNCHRONOUS;
    }

    /**
     * Informs this listener about the given event.
     *
     * @param event not null
     */
    void event(Event event);

    interface QuotaEvent extends Event {
        QuotaRoot getQuotaRoot();
    }

    class QuotaUsageUpdatedEvent implements QuotaEvent {
        private final User user;
        private final QuotaRoot quotaRoot;
        private final Quota<QuotaCount> countQuota;
        private final Quota<QuotaSize> sizeQuota;
        private final Instant instant;

        public QuotaUsageUpdatedEvent(User user, QuotaRoot quotaRoot, Quota<QuotaCount> countQuota, Quota<QuotaSize> sizeQuota, Instant instant) {
            this.user = user;
            this.quotaRoot = quotaRoot;
            this.countQuota = countQuota;
            this.sizeQuota = sizeQuota;
            this.instant = instant;
        }

        @Override
        public User getUser() {
            return user;
        }

        public Quota<QuotaCount> getCountQuota() {
            return countQuota;
        }

        public Quota<QuotaSize> getSizeQuota() {
            return sizeQuota;
        }

        @Override
        public QuotaRoot getQuotaRoot() {
            return quotaRoot;
        }

        public Instant getInstant() {
            return instant;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof QuotaUsageUpdatedEvent) {
                QuotaUsageUpdatedEvent that = (QuotaUsageUpdatedEvent) o;

                return Objects.equals(this.user, that.user)
                    && Objects.equals(this.quotaRoot, that.quotaRoot)
                    && Objects.equals(this.countQuota, that.countQuota)
                    && Objects.equals(this.sizeQuota, that.sizeQuota)
                    && Objects.equals(this.instant, that.instant);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(user, quotaRoot, countQuota, sizeQuota, instant);
        }

    }

    /**
     * A mailbox event.
     */
    abstract class MailboxEvent implements Event {
        private final MailboxPath path;
        private final MailboxId mailboxId;
        private final User user;
        private final MailboxSession.SessionId sessionId;

        public MailboxEvent(MailboxSession.SessionId sessionId, User user, MailboxPath path, MailboxId mailboxId) {
            this.user = user;
            this.path = path;
            this.mailboxId = mailboxId;
            this.sessionId = sessionId;
        }

        /**
         * Gets the {@link User} in which's context the {@link MailboxEvent}
         * happened
         *
         * @return user
         */
        @Override
        public User getUser() {
            return user;
        }

        /**
         * Gets the sessionId in which's context the {@link MailboxEvent}
         * happened
         *
         * @return sessionId
         */
        public MailboxSession.SessionId getSessionId() {
            return sessionId;
        }

        /**
         * Return the path of the Mailbox this event belongs to.
         *
         * @return path
         */
        public MailboxPath getMailboxPath() {
            return path;
        }

        /**
         * Return the id of the Mailbox this event belongs to.
         *
         * @return mailboxId
         */
        public MailboxId getMailboxId() {
            return mailboxId;
        }
    }

    /**
     * Indicates that mailbox has been deleted.
     */
    class MailboxDeletion extends MailboxEvent {
        private final QuotaRoot quotaRoot;
        private final QuotaCount deletedMessageCOunt;
        private final QuotaSize totalDeletedSize;

        public MailboxDeletion(MailboxSession.SessionId sessionId, User user, MailboxPath path, QuotaRoot quotaRoot, QuotaCount deletedMessageCOunt, QuotaSize totalDeletedSize,
                               MailboxId mailboxId) {
            super(sessionId, user, path, mailboxId);
            this.quotaRoot = quotaRoot;
            this.deletedMessageCOunt = deletedMessageCOunt;
            this.totalDeletedSize = totalDeletedSize;
        }

        public QuotaRoot getQuotaRoot() {
            return quotaRoot;
        }

        public QuotaCount getDeletedMessageCount() {
            return deletedMessageCOunt;
        }

        public QuotaSize getTotalDeletedSize() {
            return totalDeletedSize;
        }
    }

    /**
     * Indicates that a mailbox has been Added.
     */
    class MailboxAdded extends MailboxEvent {

        public MailboxAdded(MailboxSession.SessionId sessionId, User user, MailboxPath path, MailboxId mailboxId) {
            super(sessionId, user, path, mailboxId);
        }
    }

    /**
     * Indicates that a mailbox has been renamed.
     */
    class MailboxRenamed extends MailboxEvent {
        private final MailboxPath newPath;

        public MailboxRenamed(MailboxSession.SessionId sessionId, User user, MailboxPath path, MailboxId mailboxId, MailboxPath newPath) {
            super(sessionId, user, path, mailboxId);
            this.newPath = newPath;
        }

        /**
         * Gets the new name for this mailbox.
         *
         * @return name, not null
         */
        public MailboxPath getNewPath() {
            return newPath;
        }
    }


    /**
     * A mailbox event related to updated ACL
     */
    class MailboxACLUpdated extends MailboxEvent {
        private final ACLDiff aclDiff;

        public MailboxACLUpdated(MailboxSession.SessionId sessionId, User user, MailboxPath path, ACLDiff aclDiff, MailboxId mailboxId) {
            super(sessionId, user, path, mailboxId);
            this.aclDiff = aclDiff;
        }

        public ACLDiff getAclDiff() {
            return aclDiff;
        }

    }

    /**
     * A mailbox event related to a message.
     */
    abstract class MessageEvent extends MailboxEvent {

        public MessageEvent(MailboxSession.SessionId sessionId, User user, MailboxPath path, MailboxId mailboxId) {
            super(sessionId, user, path, mailboxId);
        }

        /**
         * Gets the message UIDs for the subject of this event.
         *
         * @return message uids
         */
        public abstract Collection<MessageUid> getUids();
    }

    abstract class MetaDataHoldingEvent extends MessageEvent {

        public MetaDataHoldingEvent(MailboxSession.SessionId sessionId, User user, MailboxPath path, MailboxId mailboxId) {
            super(sessionId, user, path, mailboxId);
        }

        /**
         * Return the flags which were set for the affected message
         *
         * @return flags
         */
        public abstract MessageMetaData getMetaData(MessageUid uid);

    }

    class Expunged extends MetaDataHoldingEvent {
        private final Map<MessageUid, MessageMetaData> uids;

        public Expunged(MailboxSession.SessionId sessionId, User user, MailboxPath path, MailboxId mailboxId, Map<MessageUid, MessageMetaData> uids) {
            super(sessionId, user, path, mailboxId);
            this.uids = ImmutableMap.copyOf(uids);
        }

        @Override
        public Collection<MessageUid> getUids() {
            return uids.keySet();
        }

        /**
         * Return the flags which were set for the added message
         *
         * @return flags
         */
        @Override
        public MessageMetaData getMetaData(MessageUid uid) {
            return uids.get(uid);
        }
    }

    /**
     * A mailbox event related to updated flags
     */
    class FlagsUpdated extends MessageEvent {
        private final List<MessageUid> uids;
        private final List<UpdatedFlags> updatedFlags;

        public FlagsUpdated(MailboxSession.SessionId sessionId, User user, MailboxPath path, MailboxId mailboxId, List<MessageUid> uids, List<UpdatedFlags> updatedFlags) {
            super(sessionId, user, path, mailboxId);
            this.uids = ImmutableList.copyOf(uids);
            this.updatedFlags = ImmutableList.copyOf(updatedFlags);
        }

        @Override
        public Collection<MessageUid> getUids() {
            return uids;
        }

        public List<UpdatedFlags> getUpdatedFlags() {
            return updatedFlags;
        }
    }

    /**
     * A mailbox event related to added message
     */
    class Added extends MetaDataHoldingEvent {
        private final Map<MessageUid, MessageMetaData> added;

        public Added(MailboxSession.SessionId sessionId, User user, MailboxPath path, MailboxId mailboxId, SortedMap<MessageUid, MessageMetaData> uids) {
            super(sessionId, user, path, mailboxId);
            this.added = ImmutableMap.copyOf(uids);
        }

        /**
         * Return the flags which were set for the added message
         *
         * @return flags
         */
        public MessageMetaData getMetaData(MessageUid uid) {
            return added.get(uid);
        }

        @Override
        public Collection<MessageUid> getUids() {
            return added.keySet();
        }
    }

}
