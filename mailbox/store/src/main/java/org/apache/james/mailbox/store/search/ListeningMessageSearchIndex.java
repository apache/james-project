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

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.SessionProvider;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

/**
 * {@link MessageSearchIndex} which needs to get registered as global {@link MailboxListener} and so get
 * notified about message changes. This will then allow to update the underlying index.
 * 
 *
 */
public abstract class ListeningMessageSearchIndex implements MessageSearchIndex, MailboxListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ListeningMessageSearchIndex.class);

    private static final int UNLIMITED = -1;
    private final MailboxSessionMapperFactory factory;
    private final SessionProvider sessionProvider;
    private static final ImmutableList<Class<? extends Event>> INTERESTING_EVENTS = ImmutableList.of(Added.class, Expunged.class, FlagsUpdated.class, MailboxDeletion.class);

    public ListeningMessageSearchIndex(MailboxSessionMapperFactory factory, SessionProvider sessionProvider) {
        this.factory = factory;
        this.sessionProvider = sessionProvider;
    }

    /**
     * Process the {@link org.apache.james.mailbox.Event} and update the index if
     * something relevant is received
     */
    @Override
    public void event(Event event) {
        try {
            if (event instanceof MailboxEvent) {
                handleMailboxEvent(event,
                    sessionProvider.createSystemSession(event.getUser().asString()),
                    (MailboxEvent) event);
            }
        } catch (MailboxException e) {
            LOGGER.error("Unable to update index for event {}", event, e);
        }
    }

    private void handleMailboxEvent(Event event, MailboxSession session, MailboxEvent mailboxEvent) throws MailboxException {
        if (INTERESTING_EVENTS.contains(event.getClass())) {
            Mailbox mailbox = factory.getMailboxMapper(session).findMailboxById(mailboxEvent.getMailboxId());

            if (event instanceof Added) {
                handleAdded(session, mailbox, (Added) event);
            } else if (event instanceof Expunged) {
                handleExpunged(session, mailbox, (Expunged) event);
            } else if (event instanceof FlagsUpdated) {
                handleFlagsUpdated(session, mailbox, (FlagsUpdated) event);
            } else if (event instanceof MailboxDeletion) {
                deleteAll(session, mailbox);
            }
        }
    }

    private void handleFlagsUpdated(MailboxSession session, Mailbox mailbox, FlagsUpdated flagsUpdated) {
        try {
            update(session, mailbox, flagsUpdated.getUpdatedFlags());
        } catch (MailboxException e) {
            LOGGER.error("Unable to update flags in index for mailbox {}", mailbox, e);
        }
    }

    private void handleExpunged(MailboxSession session, Mailbox mailbox, Expunged expunged) {
        try {
            delete(session, mailbox, expunged.getUids());
        } catch (MailboxException e) {
            LOGGER.error("Unable to deleted messages {} from index for mailbox {}", expunged.getUids(), mailbox, e);
        }
    }

    private void handleAdded(MailboxSession session, Mailbox mailbox, Added added) {
        MessageRange.toRanges(added.getUids())
            .stream()
            .flatMap(range -> retrieveMailboxMessages(session, mailbox, range))
            .forEach(mailboxMessage -> addMessage(session, mailbox, mailboxMessage));
    }

    private Stream<MailboxMessage> retrieveMailboxMessages(MailboxSession session, Mailbox mailbox, MessageRange range) {
        try {
            return Stream.of(factory.getMessageMapper(session)
                .findInMailbox(mailbox, range, FetchType.Full, UNLIMITED)
                .next());
        } catch (Exception e) {
            LOGGER.error("Could not retrieve message {} in mailbox {}", range.toString(), mailbox.getMailboxId().serialize(), e);
            return Stream.empty();
        }
    }

    private void addMessage(MailboxSession session, Mailbox mailbox, MailboxMessage message) {
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
    public abstract void delete(MailboxSession session, Mailbox mailbox, Collection<MessageUid> expungedUids) throws MailboxException;

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
