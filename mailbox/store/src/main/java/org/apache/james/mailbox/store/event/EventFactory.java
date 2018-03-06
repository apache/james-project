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

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageMoves;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.StoreMailboxPath;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class EventFactory {

    public interface MailboxAware {
        Mailbox getMailbox();
    }

    public final class AddedImpl extends MailboxListener.Added implements MailboxAware {
        private final Map<MessageUid, MessageMetaData> added;
        private final Map<MessageUid, MailboxMessage> availableMessages;
        private final Mailbox mailbox;

        public AddedImpl(MailboxSession session, Mailbox mailbox, SortedMap<MessageUid, MessageMetaData> uids, Map<MessageUid, MailboxMessage> availableMessages) {
            super(session, new StoreMailboxPath(mailbox));
            this.added = ImmutableMap.copyOf(uids);
            this.mailbox = mailbox;
            this.availableMessages = ImmutableMap.copyOf(availableMessages);
        }

        public List<MessageUid> getUids() {
            return ImmutableList.copyOf(added.keySet());
        }

        public MessageMetaData getMetaData(MessageUid uid) {
            return added.get(uid);
        }

        public Mailbox getMailbox() {
            return mailbox;
        }

        public Map<MessageUid, MailboxMessage> getAvailableMessages() {
            return availableMessages;
        }
    }

    public final class ExpungedImpl extends MailboxListener.Expunged implements MailboxAware {
        private final Map<MessageUid, MessageMetaData> uids;
        private final Mailbox mailbox;

        public ExpungedImpl(MailboxSession session, Mailbox mailbox,  Map<MessageUid, MessageMetaData> uids) {
            super(session,  new StoreMailboxPath(mailbox));
            this.uids = ImmutableMap.copyOf(uids);
            this.mailbox = mailbox;
        }

        public List<MessageUid> getUids() {
            return ImmutableList.copyOf(uids.keySet());
        }

        public MessageMetaData getMetaData(MessageUid uid) {
            return uids.get(uid);
        }

        public Mailbox getMailbox() {
            return mailbox;
        }
    }

    public final class FlagsUpdatedImpl extends MailboxListener.FlagsUpdated implements MailboxAware {
        private final List<MessageUid> uids;

        private final Mailbox mailbox;

        private final List<UpdatedFlags> uFlags;

        public FlagsUpdatedImpl(MailboxSession session, Mailbox mailbox, List<MessageUid> uids, List<UpdatedFlags> uFlags) {
            super(session, new StoreMailboxPath(mailbox));
            this.uids = ImmutableList.copyOf(uids);
            this.uFlags = ImmutableList.copyOf(uFlags);
            this.mailbox = mailbox;
        }

        public List<MessageUid> getUids() {
            return uids;
        }

        public List<UpdatedFlags> getUpdatedFlags() {
            return uFlags;
        }

        public Mailbox getMailbox() {
            return mailbox;
        }

    }

    public final class MailboxDeletionImpl extends MailboxListener.MailboxDeletion implements MailboxAware {
        private final Mailbox mailbox;

        public MailboxDeletionImpl(MailboxSession session, Mailbox mailbox) {
            super(session, new StoreMailboxPath(mailbox));
            this.mailbox = mailbox;
        }


        public Mailbox getMailbox() {
            return mailbox;
        }

    }

    public final class MailboxAddedImpl extends MailboxListener.MailboxAdded implements MailboxAware {

        private final Mailbox mailbox;

        public MailboxAddedImpl(MailboxSession session, Mailbox mailbox) {
            super(session,  new StoreMailboxPath(mailbox));
            this.mailbox = mailbox;
        }


        public Mailbox getMailbox() {
            return mailbox;
        }

    }

    public final class MailboxRenamedEventImpl extends MailboxListener.MailboxRenamed implements MailboxAware {

        private final MailboxPath newPath;
        private final Mailbox newMailbox;

        public MailboxRenamedEventImpl(MailboxSession session, MailboxPath oldPath, Mailbox newMailbox) {
            super(session, oldPath);
            this.newPath = new StoreMailboxPath(newMailbox);
            this.newMailbox = newMailbox;
        }

        public MailboxPath getNewPath() {
            return newPath;
        }

        @Override
        public Mailbox getMailbox() {
            return newMailbox;
        }
    }

    public MailboxListener.Added added(MailboxSession session, SortedMap<MessageUid, MessageMetaData> uids, Mailbox mailbox, Map<MessageUid, MailboxMessage> cachedMessages) {
        return new AddedImpl(session, mailbox, uids, cachedMessages);
    }

    public MailboxListener.Expunged expunged(MailboxSession session,  Map<MessageUid, MessageMetaData> uids, Mailbox mailbox) {
        return new ExpungedImpl(session, mailbox, uids);
    }

    public MailboxListener.FlagsUpdated flagsUpdated(MailboxSession session, List<MessageUid> uids, Mailbox mailbox, List<UpdatedFlags> uflags) {
        return new FlagsUpdatedImpl(session, mailbox, uids, uflags);
    }

    public MailboxListener.MailboxRenamed mailboxRenamed(MailboxSession session, MailboxPath from, Mailbox to) {
        return new MailboxRenamedEventImpl(session, from, to);
    }

    public MailboxListener.MailboxDeletion mailboxDeleted(MailboxSession session, Mailbox mailbox) {
        return new MailboxDeletionImpl(session, mailbox);
    }

    public MailboxListener.MailboxAdded mailboxAdded(MailboxSession session, Mailbox mailbox) {
        return new MailboxAddedImpl(session, mailbox);
    }

    public MailboxListener.MailboxACLUpdated aclUpdated(MailboxSession session, MailboxPath mailboxPath, ACLDiff aclDiff) {
        return new MailboxListener.MailboxACLUpdated(session, mailboxPath, aclDiff);
    }

    public MessageMoveEvent moved(MailboxSession session, MessageMoves messageMoves, Map<MessageUid, MailboxMessage> messages) {
        return MessageMoveEvent.builder()
                .session(session)
                .messageMoves(messageMoves)
                .messages(messages)
                .build();
    }
}
