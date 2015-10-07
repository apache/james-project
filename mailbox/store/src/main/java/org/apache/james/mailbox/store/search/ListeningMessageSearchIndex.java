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
package org.apache.james.mailbox.store.search;

import java.util.Iterator;
import java.util.List;

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.MailboxEventDispatcher.AddedImpl;
import org.apache.james.mailbox.store.MailboxEventDispatcher.ExpungedImpl;
import org.apache.james.mailbox.store.MailboxEventDispatcher.FlagsUpdatedImpl;
import org.apache.james.mailbox.store.MailboxEventDispatcher.MailboxDeletionImpl;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.Message;

/**
 * {@link MessageSearchIndex} which needs to get registered as global {@link MailboxListener} and so get
 * notified about message changes. This will then allow to update the underlying index.
 * 
 *
 * @param <Id>
 */
public abstract class ListeningMessageSearchIndex<Id extends MailboxId> implements MessageSearchIndex<Id>, MailboxListener {

    private MessageMapperFactory<Id> factory;

    public ListeningMessageSearchIndex(MessageMapperFactory<Id> factory) {
        this.factory = factory;
    }
    
    
    /**
     * Return the {@link MessageMapperFactory}
     * 
     * @return factory
     */
    protected MessageMapperFactory<Id> getFactory() {
        return factory;
    }
    
    
    /**
     * Process the {@link org.apache.james.mailbox.MailboxListener.Event} and update the index if
     * something relevant is received
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void event(Event event) {
        final MailboxSession session = event.getSession();

        try {
            if (event instanceof MessageEvent) {
                if (event instanceof AddedImpl) {
                    AddedImpl added = (AddedImpl) event;
                    final Mailbox<Id> mailbox = added.getMailbox();
                    Iterator<Long> uids = added.getUids().iterator();

                    while (uids.hasNext()) {
                        long next = uids.next();
                        Iterator<Message<Id>> messages = factory.getMessageMapper(session).findInMailbox(mailbox, MessageRange.one(next), FetchType.Full, -1);
                        while(messages.hasNext()) {
                            Message<Id> message = messages.next();
                            try {
                                add(session, mailbox, message);
                            } catch (MailboxException e) {
                                session.getLog().debug("Unable to index message " + message.getUid() + " for mailbox " + mailbox, e);
                            }
                        }

                    }
                } else if (event instanceof ExpungedImpl) {
                    ExpungedImpl expunged = (ExpungedImpl) event;
                    final Mailbox<Id> mailbox = expunged.getMailbox();
                    List<Long> uids = expunged.getUids();
                    List<MessageRange> ranges = MessageRange.toRanges(uids);
                    for (int i = 0; i < ranges.size(); i++) {
                        MessageRange range = ranges.get(i);
                        try {
                            delete(session, mailbox, range);
                        } catch (MailboxException e) {
                            session.getLog().debug("Unable to deleted range " + range.toString() + " from index for mailbox " + mailbox, e);
                        }
                    }
                } else if (event instanceof FlagsUpdatedImpl) {
                    FlagsUpdatedImpl flagsUpdated = (FlagsUpdatedImpl) event;
                    final Mailbox<Id> mailbox = flagsUpdated.getMailbox();

                    Iterator<UpdatedFlags> flags = flagsUpdated.getUpdatedFlags().iterator();
                    while(flags.hasNext()) {
                        UpdatedFlags uFlags = flags.next();
                        try {
                            update(session, mailbox, MessageRange.one(uFlags.getUid()), uFlags.getNewFlags(), uFlags.getModSeq());
                        } catch (MailboxException e) {
                            session.getLog().debug("Unable to update flags for message " + uFlags.getUid() + " in index for mailbox " + mailbox, e);
                        }
                    }
                }
            } else if (event instanceof MailboxDeletionImpl) {
                // delete all indexed messages for the mailbox
                delete(session, ((MailboxDeletionImpl) event).getMailbox(), MessageRange.all());
            }
        } catch (MailboxException e) {
            session.getLog().debug("Unable to update index", e);

        }
    }

    /**
     * Never closed
     */
    public boolean isClosed() {
        return false;
    }

    /**
     * Add the {@link Message} for the given {@link Mailbox} to the index
     * 
     * @param session
     * @param mailbox
     * @param message
     * @throws MailboxException
     */
    public abstract void add(MailboxSession session, Mailbox<Id> mailbox, Message<Id> message) throws MailboxException;

    /**
     * Delete the {@link MessageRange} for the given {@link Mailbox} from the index
     * 
     * @param session
     * @param mailbox
     * @param range
     * @throws MailboxException
     */
    public abstract void delete(MailboxSession session, Mailbox<Id> mailbox, MessageRange range) throws MailboxException;
    
    
    /**
     * Update the {@link MessageRange} for the given {@link Mailbox} with the new {@link Flags} in the index
     *  
     * @param session
     * @param mailbox
     * @param range
     * @param flags
     * @throws MailboxException
     */
    public abstract void update(MailboxSession session, Mailbox<Id> mailbox, MessageRange range, Flags flags, long modseq) throws MailboxException;
}
