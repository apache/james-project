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

package org.apache.james.mailbox.store.event;

import java.util.Collection;
import java.util.Map;
import java.util.SortedMap;

import org.apache.james.core.User;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageMoveEvent;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageMoves;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.mail.model.Mailbox;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class EventFactory {
    public abstract static class MailboxEventBuilder<T extends MailboxEventBuilder> {
        protected MailboxPath path;
        protected MailboxId mailboxId;
        protected User user;
        protected MailboxSession.SessionId sessionId;

        abstract T backReference();

        public T mailbox(Mailbox mailbox) {
            path(mailbox.generateAssociatedPath());
            mailboxId(mailbox.getMailboxId());
            return backReference();
        }

        public T mailboxSession(MailboxSession mailboxSession) {
            user(mailboxSession.getUser());
            sessionId(mailboxSession.getSessionId());
            return backReference();
        }

        public T mailboxId(MailboxId mailboxId) {
            this.mailboxId = mailboxId;
            return backReference();
        }

        public T path(MailboxPath path) {
            this.path = path;
            return backReference();
        }

        public T user(User user) {
            this.user = user;
            return backReference();
        }

        public T sessionId(MailboxSession.SessionId sessionId) {
            this.sessionId = sessionId;
            return backReference();
        }

        void mailboxEventChecks() {
            Preconditions.checkState(user != null, "Field `user` is compulsory");
            Preconditions.checkState(mailboxId != null, "Field `mailboxId` is compulsory");
            Preconditions.checkState(path != null, "Field `path` is compulsory");
            Preconditions.checkState(sessionId != null, "Field `sessionId` is compulsory");
        }
    }

    public static class MailboxAddedBuilder extends MailboxEventBuilder<MailboxAddedBuilder> {
        @Override
        MailboxAddedBuilder backReference() {
            return this;
        }

        public MailboxListener.MailboxAdded build() {
            mailboxEventChecks();

            return new MailboxListener.MailboxAdded(sessionId, user, path, mailboxId);
        }
    }

    public static class FlagsUpdatedBuilder extends MailboxEventBuilder<FlagsUpdatedBuilder> {
        private final ImmutableList.Builder<UpdatedFlags> updatedFlags;

        public FlagsUpdatedBuilder() {
            updatedFlags = ImmutableList.builder();
        }

        public FlagsUpdatedBuilder updatedFags(Iterable<UpdatedFlags> updatedFlags) {
            this.updatedFlags.addAll(updatedFlags);
            return this;
        }

        public FlagsUpdatedBuilder updatedFags(UpdatedFlags updatedFlags) {
            this.updatedFlags.add(updatedFlags);
            return this;
        }

        @Override
        FlagsUpdatedBuilder backReference() {
            return this;
        }

        public MailboxListener.FlagsUpdated build() {
            mailboxEventChecks();

            ImmutableList<UpdatedFlags> updatedFlags = this.updatedFlags.build();

            return new MailboxListener.FlagsUpdated(sessionId, user, path, mailboxId, updatedFlags);
        }
    }

    public MailboxListener.Added added(MailboxSession session, SortedMap<MessageUid, MessageMetaData> uids, Mailbox mailbox) {
        return added(session.getSessionId(), session.getUser(), uids, mailbox);
    }

    public MailboxListener.Added added(MailboxSession.SessionId sessionId, User user, SortedMap<MessageUid, MessageMetaData> uids, Mailbox mailbox) {
        return new MailboxListener.Added(sessionId, user, mailbox.generateAssociatedPath(), mailbox.getMailboxId(), uids);
    }

    public MailboxListener.Expunged expunged(MailboxSession session,  Map<MessageUid, MessageMetaData> uids, Mailbox mailbox) {
        return expunged(session.getSessionId(), session.getUser(), uids, mailbox);
    }

    public MailboxListener.Expunged expunged(MailboxSession.SessionId sessionId, User user, Map<MessageUid, MessageMetaData> uids, Mailbox mailbox) {
        return new MailboxListener.Expunged(sessionId, user, mailbox.generateAssociatedPath(), mailbox.getMailboxId(), uids);
    }

    public FlagsUpdatedBuilder flagsUpdated() {
        return new FlagsUpdatedBuilder();
    }

    public MailboxListener.MailboxRenamed mailboxRenamed(MailboxSession session, MailboxPath from, Mailbox to) {
        return mailboxRenamed(session.getSessionId(), session.getUser(), from, to);
    }

    public MailboxListener.MailboxRenamed mailboxRenamed(MailboxSession.SessionId sessionId, User user, MailboxPath from, Mailbox to) {
        return new MailboxListener.MailboxRenamed(sessionId, user, from, to.getMailboxId(), to.generateAssociatedPath());
    }

    public MailboxListener.MailboxDeletion mailboxDeleted(MailboxSession session, Mailbox mailbox, QuotaRoot quotaRoot,
                                                          QuotaCount deletedMessageCount, QuotaSize totalDeletedSize) {
        return mailboxDeleted(session.getSessionId(), session.getUser(), mailbox, quotaRoot, deletedMessageCount, totalDeletedSize);
    }

    public MailboxListener.MailboxDeletion mailboxDeleted(MailboxSession.SessionId sessionId, User user, Mailbox mailbox, QuotaRoot quotaRoot,
                                                          QuotaCount deletedMessageCount, QuotaSize totalDeletedSize) {
        return new MailboxListener.MailboxDeletion(sessionId, user, mailbox.generateAssociatedPath(), quotaRoot, deletedMessageCount, totalDeletedSize, mailbox.getMailboxId());
    }

    public MailboxAddedBuilder mailboxAdded() {
        return new MailboxAddedBuilder();
    }

    public MailboxListener.MailboxACLUpdated aclUpdated(MailboxSession session, MailboxPath mailboxPath, ACLDiff aclDiff, MailboxId mailboxId) {
        return aclUpdated(session.getSessionId(), session.getUser(), mailboxPath, aclDiff, mailboxId);
    }

    public MailboxListener.MailboxACLUpdated aclUpdated(MailboxSession.SessionId sessionId, User user, MailboxPath mailboxPath, ACLDiff aclDiff, MailboxId mailboxId) {
        return new MailboxListener.MailboxACLUpdated(sessionId, user, mailboxPath, aclDiff, mailboxId);
    }

    public MessageMoveEvent moved(MailboxSession session, MessageMoves messageMoves, Collection<MessageId> messageIds) {
        return MessageMoveEvent.builder()
                .user(session.getUser())
                .messageMoves(messageMoves)
                .messageId(messageIds)
                .build();
    }
}
