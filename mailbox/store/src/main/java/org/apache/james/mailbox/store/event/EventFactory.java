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

import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import org.apache.james.core.User;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageMoves;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.StoreMailboxPath;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class EventFactory {

    private final class AddedImpl extends MailboxListener.Added {
        private final Map<MessageUid, MessageMetaData> added;

        AddedImpl(MailboxSession.SessionId sessionId, User user, Mailbox mailbox, SortedMap<MessageUid, MessageMetaData> uids) {
            super(sessionId, user, new StoreMailboxPath(mailbox), mailbox.getMailboxId());
            this.added = ImmutableMap.copyOf(uids);
        }

        @Override
        public List<MessageUid> getUids() {
            return ImmutableList.copyOf(added.keySet());
        }

        @Override
        public MessageMetaData getMetaData(MessageUid uid) {
            return added.get(uid);
        }
    }

    private final class ExpungedImpl extends MailboxListener.Expunged {
        private final Map<MessageUid, MessageMetaData> uids;

        ExpungedImpl(MailboxSession.SessionId sessionId, User user, Mailbox mailbox, Map<MessageUid, MessageMetaData> uids) {
            super(sessionId, user,  new StoreMailboxPath(mailbox), mailbox.getMailboxId());
            this.uids = ImmutableMap.copyOf(uids);
        }

        @Override
        public List<MessageUid> getUids() {
            return ImmutableList.copyOf(uids.keySet());
        }

        @Override
        public MessageMetaData getMetaData(MessageUid uid) {
            return uids.get(uid);
        }
    }

    private final class FlagsUpdatedImpl extends MailboxListener.FlagsUpdated {
        private final List<MessageUid> uids;
        private final List<UpdatedFlags> uFlags;

        FlagsUpdatedImpl(MailboxSession.SessionId sessionId, User user, Mailbox mailbox, List<MessageUid> uids, List<UpdatedFlags> uFlags) {
            super(sessionId, user, new StoreMailboxPath(mailbox), mailbox.getMailboxId());
            this.uids = ImmutableList.copyOf(uids);
            this.uFlags = ImmutableList.copyOf(uFlags);
        }

        @Override
        public List<MessageUid> getUids() {
            return uids;
        }

        @Override
        public List<UpdatedFlags> getUpdatedFlags() {
            return uFlags;
        }
    }

    private final class MailboxDeletionImpl extends MailboxListener.MailboxDeletion {
        MailboxDeletionImpl(MailboxSession.SessionId sessionId, User user, Mailbox mailbox, QuotaRoot quotaRoot, QuotaCount deletedMessageCount, QuotaSize totalDeletedSize) {
            super(sessionId, user, new StoreMailboxPath(mailbox), quotaRoot, deletedMessageCount, totalDeletedSize, mailbox.getMailboxId());
        }
    }

    private final class MailboxAddedImpl extends MailboxListener.MailboxAdded {
        MailboxAddedImpl(MailboxSession.SessionId sessionId, User user, Mailbox mailbox) {
            super(sessionId, user,  new StoreMailboxPath(mailbox), mailbox.getMailboxId());
        }
    }

    private final class MailboxRenamedEventImpl extends MailboxListener.MailboxRenamed {
        private final MailboxPath newPath;

        MailboxRenamedEventImpl(MailboxSession.SessionId sessionId, User user, MailboxPath oldPath, Mailbox newMailbox) {
            super(sessionId, user, oldPath, newMailbox.getMailboxId());
            this.newPath = new StoreMailboxPath(newMailbox);
        }

        @Override
        public MailboxPath getNewPath() {
            return newPath;
        }
    }

    public MailboxListener.Added added(MailboxSession session, SortedMap<MessageUid, MessageMetaData> uids, Mailbox mailbox) {
        return added(session.getSessionId(), session.getUser().getCoreUser(), uids, mailbox);
    }

    public MailboxListener.Added added(MailboxSession.SessionId sessionId, User user, SortedMap<MessageUid, MessageMetaData> uids, Mailbox mailbox) {
        return new AddedImpl(sessionId, user, mailbox, uids);
    }

    public MailboxListener.Expunged expunged(MailboxSession session,  Map<MessageUid, MessageMetaData> uids, Mailbox mailbox) {
        return expunged(session.getSessionId(), session.getUser().getCoreUser(), uids, mailbox);
    }

    public MailboxListener.Expunged expunged(MailboxSession.SessionId sessionId, User user, Map<MessageUid, MessageMetaData> uids, Mailbox mailbox) {
        return new ExpungedImpl(sessionId, user, mailbox, uids);
    }

    public MailboxListener.FlagsUpdated flagsUpdated(MailboxSession session, List<MessageUid> uids, Mailbox mailbox, List<UpdatedFlags> uflags) {
        return flagsUpdated(session.getSessionId(), session.getUser().getCoreUser(), uids, mailbox, uflags);
    }

    public MailboxListener.FlagsUpdated flagsUpdated(MailboxSession.SessionId sessionId, User user, List<MessageUid> uids, Mailbox mailbox, List<UpdatedFlags> uflags) {
        return new FlagsUpdatedImpl(sessionId, user, mailbox, uids, uflags);
    }

    public MailboxListener.MailboxRenamed mailboxRenamed(MailboxSession session, MailboxPath from, Mailbox to) {
        return mailboxRenamed(session.getSessionId(), session.getUser().getCoreUser(), from, to);
    }

    public MailboxListener.MailboxRenamed mailboxRenamed(MailboxSession.SessionId sessionId, User user, MailboxPath from, Mailbox to) {
        return new MailboxRenamedEventImpl(sessionId, user, from, to);
    }

    public MailboxListener.MailboxDeletion mailboxDeleted(MailboxSession session, Mailbox mailbox, QuotaRoot quotaRoot,
                                                          QuotaCount deletedMessageCount, QuotaSize totalDeletedSize) {
        return mailboxDeleted(session.getSessionId(), session.getUser().getCoreUser(), mailbox, quotaRoot, deletedMessageCount, totalDeletedSize);
    }

    public MailboxListener.MailboxDeletion mailboxDeleted(MailboxSession.SessionId sessionId, User user, Mailbox mailbox, QuotaRoot quotaRoot,
                                                          QuotaCount deletedMessageCount, QuotaSize totalDeletedSize) {
        return new MailboxDeletionImpl(sessionId, user, mailbox, quotaRoot, deletedMessageCount, totalDeletedSize);
    }

    public MailboxListener.MailboxAdded mailboxAdded(MailboxSession session, Mailbox mailbox) {
        return mailboxAdded(session.getSessionId(), session.getUser().getCoreUser(), mailbox);
    }

    public MailboxListener.MailboxAdded mailboxAdded(MailboxSession.SessionId sessionId, User user, Mailbox mailbox) {
        return new MailboxAddedImpl(sessionId, user, mailbox);
    }

    public MailboxListener.MailboxACLUpdated aclUpdated(MailboxSession session, MailboxPath mailboxPath, ACLDiff aclDiff, MailboxId mailboxId) {
        return aclUpdated(session.getSessionId(), session.getUser().getCoreUser(), mailboxPath, aclDiff, mailboxId);
    }

    public MailboxListener.MailboxACLUpdated aclUpdated(MailboxSession.SessionId sessionId, User user, MailboxPath mailboxPath, ACLDiff aclDiff, MailboxId mailboxId) {
        return new MailboxListener.MailboxACLUpdated(sessionId, user, mailboxPath, aclDiff, mailboxId);
    }

    public MessageMoveEvent moved(MailboxSession session, MessageMoves messageMoves, Map<MessageUid, MailboxMessage> messages) {
        return MessageMoveEvent.builder()
                .user(session.getUser().getCoreUser())
                .messageMoves(messageMoves)
                .messages(messages)
                .build();
    }
}
