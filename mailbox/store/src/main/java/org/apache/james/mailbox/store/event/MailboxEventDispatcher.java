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
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.mail.model.Mailbox;

/**
 * Helper class to dispatch {@link org.apache.james.mailbox.MailboxListener.Event}'s to registerend MailboxListener
 */
public class MailboxEventDispatcher {

    private final MailboxListener listener;
    private final EventFactory eventFactory;

    public MailboxEventDispatcher(MailboxListener listener) {
        this.listener = listener;
        this.eventFactory = new EventFactory();
    }

    /**
     * Should get called when a new message was added to a Mailbox. All
     * registered MailboxListener will get triggered then
     *
     * @param session The mailbox session
     * @param uids Sorted map with uids and message meta data
     * @param mailbox The mailbox
     */
    public void added(MailboxSession session, SortedMap<MessageUid, MessageMetaData> uids, Mailbox mailbox) {
        listener.event(eventFactory.added(session, uids, mailbox));
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
        listener.event(eventFactory.expunged(session, uids, mailbox));
    }

    /**
     * Should get called when the message flags were update in a Mailbox. All
     * registered MailboxListener will get triggered then
     */
    public void flagsUpdated(MailboxSession session, List<MessageUid> uids, Mailbox mailbox, List<UpdatedFlags> uflags) {
        listener.event(eventFactory.flagsUpdated(session, uids, mailbox, uflags));
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

}
