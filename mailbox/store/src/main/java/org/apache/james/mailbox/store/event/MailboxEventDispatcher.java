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

import javax.inject.Inject;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageMoves;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.SimpleMessageMetaData;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;

/**
 * Helper class to dispatch {@link org.apache.james.mailbox.Event}'s to registerend MailboxListener
 */
public class MailboxEventDispatcher {

    @VisibleForTesting
    public static MailboxEventDispatcher ofListener(MailboxListener mailboxListener) {
        return new MailboxEventDispatcher(mailboxListener, new EventFactory());
    }

    private final MailboxListener listener;
    private final EventFactory eventFactory;

    @Inject
    public MailboxEventDispatcher(DelegatingMailboxListener delegatingMailboxListener) {
        this(delegatingMailboxListener, new EventFactory());
    }

    private MailboxEventDispatcher(MailboxListener listener, EventFactory eventFactory) {
        this.listener = listener;
        this.eventFactory = eventFactory;
    }

    /**
     * Should get called when a new message was added to a Mailbox. All
     * registered MailboxListener will get triggered then
     *
     * @param session The mailbox session
     * @param uids Sorted map with uids and message meta data
     * @param mailbox The mailbox
     */
    public void added(MailboxSession session, SortedMap<MessageUid, MessageMetaData> uids, Mailbox mailbox, Map<MessageUid, MailboxMessage> cachedMessages) {
        listener.event(eventFactory.added(session, uids, mailbox, cachedMessages));
    }

    public void added(MailboxSession session, Mailbox mailbox, MailboxMessage mailboxMessage) {
        SimpleMessageMetaData messageMetaData = new SimpleMessageMetaData(mailboxMessage);
        SortedMap<MessageUid, MessageMetaData> metaDataMap = ImmutableSortedMap.<MessageUid, MessageMetaData>naturalOrder()
                .put(messageMetaData.getUid(), messageMetaData)
                .build();
        added(session, metaDataMap, mailbox, ImmutableMap.of(mailboxMessage.getUid(), mailboxMessage));
    }

    public void added(MailboxSession session, MessageMetaData messageMetaData, Mailbox mailbox) {
        SortedMap<MessageUid, MessageMetaData> metaDataMap = ImmutableSortedMap.<MessageUid, MessageMetaData>naturalOrder()
            .put(messageMetaData.getUid(), messageMetaData)
            .build();
        added(session, metaDataMap, mailbox, ImmutableMap.<MessageUid, MailboxMessage>of());
    }

    /**
     * Should get called when a message was expunged from a Mailbox. All
     * registered MailboxListener will get triggered then
     *
     * @param session The mailbox session
     * @param uids Sorted map with uids and message meta data
     * @param mailbox The mailbox
     */
    public void expunged(MailboxSession session,  Map<MessageUid, MessageMetaData> uids, Mailbox mailbox) {
        if (!uids.isEmpty()) {
            listener.event(eventFactory.expunged(session, uids, mailbox));
        }
    }

    public void expunged(MailboxSession session,  MessageMetaData messageMetaData, Mailbox mailbox) {
        Map<MessageUid, MessageMetaData> metaDataMap = ImmutableMap.<MessageUid, MessageMetaData>builder()
            .put(messageMetaData.getUid(), messageMetaData)
            .build();
        expunged(session, metaDataMap, mailbox);
    }

    /**
     * Should get called when the message flags were update in a Mailbox. All
     * registered MailboxListener will get triggered then
     */
    public void flagsUpdated(MailboxSession session, List<MessageUid> uids, Mailbox mailbox, List<UpdatedFlags> uflags) {
        if (!uids.isEmpty()) {
            listener.event(eventFactory.flagsUpdated(session, uids, mailbox, uflags));
        }
    }

    public void flagsUpdated(MailboxSession session, MessageUid uid, Mailbox mailbox, UpdatedFlags uflags) {
        flagsUpdated(session, ImmutableList.of(uid), mailbox, ImmutableList.of(uflags));
    }

    /**
     * Should get called when a Mailbox was renamed. All registered
     * MailboxListener will get triggered then
     */
    public void mailboxRenamed(MailboxSession session, MailboxPath from, Mailbox to) {
        listener.event(eventFactory.mailboxRenamed(session, from, to));
    }

    /**
     * Should get called when a Mailbox was deleted. All registered
     * MailboxListener will get triggered then
     */
    public void mailboxDeleted(MailboxSession session, Mailbox mailbox) {
        listener.event(eventFactory.mailboxDeleted(session, mailbox));
    }

    /**
     * Should get called when a Mailbox was added. All registered
     * MailboxListener will get triggered then
     */
    public void mailboxAdded(MailboxSession session, Mailbox mailbox) {
        listener.event(eventFactory.mailboxAdded(session, mailbox));
    }

    public void aclUpdated(MailboxSession session, MailboxPath mailboxPath, ACLDiff aclDiff) {
        listener.event(eventFactory.aclUpdated(session, mailboxPath, aclDiff));
    }

    public void moved(MailboxSession session, MessageMoves messageMoves, Map<MessageUid, MailboxMessage> messages) {
        listener.event(eventFactory.moved(session, messageMoves, messages));
    }
}
