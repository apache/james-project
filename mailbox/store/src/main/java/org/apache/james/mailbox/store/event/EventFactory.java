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

public class EventFactory {

    public MailboxListener.Added added(MailboxSession session, SortedMap<MessageUid, MessageMetaData> uids, Mailbox mailbox) {
        return added(session.getSessionId(), session.getUser().getCoreUser(), uids, mailbox);
    }

    public MailboxListener.Added added(MailboxSession.SessionId sessionId, User user, SortedMap<MessageUid, MessageMetaData> uids, Mailbox mailbox) {
        return new MailboxListener.Added(sessionId, user, new StoreMailboxPath(mailbox), mailbox.getMailboxId(), uids);
    }

    public MailboxListener.Expunged expunged(MailboxSession session,  Map<MessageUid, MessageMetaData> uids, Mailbox mailbox) {
        return expunged(session.getSessionId(), session.getUser().getCoreUser(), uids, mailbox);
    }

    public MailboxListener.Expunged expunged(MailboxSession.SessionId sessionId, User user, Map<MessageUid, MessageMetaData> uids, Mailbox mailbox) {
        return new MailboxListener.Expunged(sessionId, user, new StoreMailboxPath(mailbox), mailbox.getMailboxId(), uids);
    }

    public MailboxListener.FlagsUpdated flagsUpdated(MailboxSession session, List<MessageUid> uids, Mailbox mailbox, List<UpdatedFlags> uflags) {
        return flagsUpdated(session.getSessionId(), session.getUser().getCoreUser(), uids, mailbox, uflags);
    }

    public MailboxListener.FlagsUpdated flagsUpdated(MailboxSession.SessionId sessionId, User user, List<MessageUid> uids, Mailbox mailbox, List<UpdatedFlags> uflags) {
        return new MailboxListener.FlagsUpdated(sessionId, user, new StoreMailboxPath(mailbox), mailbox.getMailboxId(), uids, uflags);
    }

    public MailboxListener.MailboxRenamed mailboxRenamed(MailboxSession session, MailboxPath from, Mailbox to) {
        return mailboxRenamed(session.getSessionId(), session.getUser().getCoreUser(), from, to);
    }

    public MailboxListener.MailboxRenamed mailboxRenamed(MailboxSession.SessionId sessionId, User user, MailboxPath from, Mailbox to) {
        return new MailboxListener.MailboxRenamed(sessionId, user, from, to.getMailboxId(), new StoreMailboxPath(to));
    }

    public MailboxListener.MailboxDeletion mailboxDeleted(MailboxSession session, Mailbox mailbox, QuotaRoot quotaRoot,
                                                          QuotaCount deletedMessageCount, QuotaSize totalDeletedSize) {
        return mailboxDeleted(session.getSessionId(), session.getUser().getCoreUser(), mailbox, quotaRoot, deletedMessageCount, totalDeletedSize);
    }

    public MailboxListener.MailboxDeletion mailboxDeleted(MailboxSession.SessionId sessionId, User user, Mailbox mailbox, QuotaRoot quotaRoot,
                                                          QuotaCount deletedMessageCount, QuotaSize totalDeletedSize) {
        return new MailboxListener.MailboxDeletion(sessionId, user, new StoreMailboxPath(mailbox), quotaRoot, deletedMessageCount, totalDeletedSize, mailbox.getMailboxId());
    }

    public MailboxListener.MailboxAdded mailboxAdded(MailboxSession session, Mailbox mailbox) {
        return mailboxAdded(session.getSessionId(), session.getUser().getCoreUser(), mailbox);
    }

    public MailboxListener.MailboxAdded mailboxAdded(MailboxSession.SessionId sessionId, User user, Mailbox mailbox) {
        return new MailboxListener.MailboxAdded(sessionId, user, new StoreMailboxPath(mailbox), mailbox.getMailboxId());
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
