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

import java.util.List;
import java.util.Optional;

import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link MessageSearchIndex} which needs to get registered as global {@link MailboxListener} and so get
 * notified about message changes. This will then allow to update the underlying index.
 * 
 *
 */
public abstract class ListeningMessageSearchIndex implements MessageSearchIndex, MailboxListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ListeningMessageSearchIndex.class);

    public static final int UNLIMITED = -1;
    private final MessageMapperFactory factory;

    public ListeningMessageSearchIndex(MessageMapperFactory factory) {
        this.factory = factory;
    }

    /**
     * Return the {@link MessageMapperFactory}
     * 
     * @return factory
     */
    protected MessageMapperFactory getFactory() {
        return factory;
    }
    
    
    /**
     * Process the {@link org.apache.james.mailbox.Event} and update the index if
     * something relevant is received
     */
    @Override
    public void event(Event event) {
        final MailboxSession session = event.getSession();

        try {
            if (event instanceof MessageEvent) {
                if (event instanceof EventFactory.AddedImpl) {
                    EventFactory.AddedImpl added = (EventFactory.AddedImpl) event;
                    final Mailbox mailbox = added.getMailbox();

                    for (final MessageUid next : (Iterable<MessageUid>) added.getUids()) {
                        Optional<MailboxMessage> mailboxMessage = retrieveMailboxMessage(session, added, mailbox, next);
                        if (mailboxMessage.isPresent()) {
                            addMessage(session, mailbox, mailboxMessage.get());
                        }
                    }
                } else if (event instanceof EventFactory.ExpungedImpl) {
                    EventFactory.ExpungedImpl expunged = (EventFactory.ExpungedImpl) event;
                    try {
                        delete(session, expunged.getMailbox(), expunged.getUids());
                    } catch (MailboxException e) {
                        LOGGER.error("Unable to deleted messages {} from index for mailbox {}", expunged.getUids(), expunged.getMailbox(), e);
                    }
                } else if (event instanceof EventFactory.FlagsUpdatedImpl) {
                    EventFactory.FlagsUpdatedImpl flagsUpdated = (EventFactory.FlagsUpdatedImpl) event;
                    final Mailbox mailbox = flagsUpdated.getMailbox();

                    try {
                        update(session, mailbox, flagsUpdated.getUpdatedFlags());
                    } catch (MailboxException e) {
                        LOGGER.error("Unable to update flags in index for mailbox {}", mailbox, e);
                    }
                }
            } else if (event instanceof EventFactory.MailboxDeletionImpl) {
                deleteAll(session, ((EventFactory.MailboxDeletionImpl) event).getMailbox());
            }
        } catch (MailboxException e) {
            LOGGER.error("Unable to update index", e);
        }
    }

    private Optional<MailboxMessage> retrieveMailboxMessage(MailboxSession session, EventFactory.AddedImpl added, Mailbox mailbox, MessageUid next) {
        Optional<MailboxMessage> firstChoice = Optional.ofNullable(added.getAvailableMessages().get(next));
        if (firstChoice.isPresent()) {
            return firstChoice;
        } else {
            try {
                return Optional.of(factory.getMessageMapper(session)
                    .findInMailbox(mailbox, MessageRange.one(next), FetchType.Full, UNLIMITED)
                    .next());
            } catch (Exception e) {
                LOGGER.error("Could not retrieve message {} in mailbox {}", next, mailbox.getMailboxId().serialize(), e);
                return Optional.empty();
            }
        }
    }

    private void addMessage(final MailboxSession session, final Mailbox mailbox, MailboxMessage message) {
        try {
            add(session, mailbox, message);
        } catch (MailboxException e) {
            LOGGER.error("Unable to index message {} for mailbox {}", message.getUid(), mailbox, e);
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
    public abstract void add(MailboxSession session, Mailbox mailbox, MailboxMessage message) throws MailboxException;

    /**
     * Delete the concerned UIDs for the given {@link Mailbox} from the index
     *
     * @param session The mailbox session performing the expunge
     * @param mailbox mailbox on which the expunge was performed
     * @param expungedUids UIDS to be deleted
     * @throws MailboxException
     */
    public abstract void delete(MailboxSession session, Mailbox mailbox, List<MessageUid> expungedUids) throws MailboxException;

    /**
     * Delete the messages contained in the given {@link Mailbox} from the index
     *
     * @param session The mailbox session performing the expunge
     * @param mailbox mailbox on which the expunge was performed
     * @throws MailboxException
     */
    public abstract void deleteAll(MailboxSession session, Mailbox mailbox) throws MailboxException;
    
    /**
     * Update the messages concerned by the updated flags list for the given {@link Mailbox}
     *
     * @param session session that performed the update
     * @param mailbox mailbox containing the updated messages
     * @param updatedFlagsList list of flags that were updated
     * @throws MailboxException
     */
    public abstract void update(MailboxSession session, Mailbox mailbox, List<UpdatedFlags> updatedFlagsList) throws MailboxException;
}
