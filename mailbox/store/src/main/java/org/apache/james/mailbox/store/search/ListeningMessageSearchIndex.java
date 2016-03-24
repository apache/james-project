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

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

/**
 * {@link MessageSearchIndex} which needs to get registered as global {@link MailboxListener} and so get
 * notified about message changes. This will then allow to update the underlying index.
 * 
 *
 * @param <Id>
 */
public abstract class ListeningMessageSearchIndex<Id extends MailboxId> implements MessageSearchIndex<Id>, MailboxListener {

    private final MessageMapperFactory<Id> factory;

    public ListeningMessageSearchIndex(MessageMapperFactory<Id> factory) {
        this.factory = factory;
    }

    @Override
    public ExecutionMode getExecutionMode() {
        return ExecutionMode.ASYNCHRONOUS;
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
                if (event instanceof EventFactory.AddedImpl) {
                    EventFactory.AddedImpl added = (EventFactory.AddedImpl) event;
                    final Mailbox<Id> mailbox = added.getMailbox();

                    for (Long next : (Iterable<Long>) added.getUids()) {
                        Iterator<MailboxMessage<Id>> messages = factory.getMessageMapper(session).findInMailbox(mailbox, MessageRange.one(next), FetchType.Full, -1);
                        while (messages.hasNext()) {
                            MailboxMessage<Id> message = messages.next();
                            try {
                                add(session, mailbox, message);
                            } catch (MailboxException e) {
                                session.getLog().debug("Unable to index message " + message.getUid() + " for mailbox " + mailbox, e);
                            }
                        }

                    }
                } else if (event instanceof EventFactory.ExpungedImpl) {
                    EventFactory.ExpungedImpl expunged = (EventFactory.ExpungedImpl) event;
                    final Mailbox<Id> mailbox = expunged.getMailbox();
                    List<Long> uids = expunged.getUids();
                    List<MessageRange> ranges = MessageRange.toRanges(uids);
                    for (MessageRange range : ranges) {
                        try {
                            delete(session, mailbox, range);
                        } catch (MailboxException e) {
                            session.getLog().debug("Unable to deleted range " + range.toString() + " from index for mailbox " + mailbox, e);
                        }
                    }
                } else if (event instanceof EventFactory.FlagsUpdatedImpl) {
                    EventFactory.FlagsUpdatedImpl flagsUpdated = (EventFactory.FlagsUpdatedImpl) event;
                    final Mailbox<Id> mailbox = flagsUpdated.getMailbox();

                    try {
                        update(session, mailbox, flagsUpdated.getUpdatedFlags());
                    } catch (MailboxException e) {
                        session.getLog().debug("Unable to update flags in index for mailbox " + mailbox, e);
                    }
                }
            } else if (event instanceof EventFactory.MailboxDeletionImpl) {
                // delete all indexed messages for the mailbox
                delete(session, ((EventFactory.MailboxDeletionImpl) event).getMailbox(), MessageRange.all());
            }
        } catch (MailboxException e) {
            session.getLog().debug("Unable to update index", e);

        }
    }

    /**
     * Add the {@link MailboxMessage} for the given {@link Mailbox} to the index
     *
     * @param session The mailbox session performing the message addition
     * @param mailbox mailbox on which the message addition was performed
     * @param message The added message
     * @throws MailboxException
     */
    public abstract void add(MailboxSession session, Mailbox<Id> mailbox, MailboxMessage<Id> message) throws MailboxException;

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
     * Update the messages concerned by the updated flags list for the given {@link Mailbox}
     *
     * @param session session that performed the update
     * @param mailbox mailbox containing the updated messages
     * @param updatedFlagsList list of flags that were updated
     * @throws MailboxException
     */
    public abstract void update(MailboxSession session, Mailbox<Id> mailbox, List<UpdatedFlags> updatedFlagsList) throws MailboxException;
}
