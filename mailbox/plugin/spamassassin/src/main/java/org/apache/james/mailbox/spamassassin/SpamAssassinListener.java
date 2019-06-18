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
package org.apache.james.mailbox.spamassassin;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.SystemMailboxesProvider;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.Group;
import org.apache.james.mailbox.events.MessageMoveEvent;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.event.SpamEventListener;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.util.streams.Iterators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class SpamAssassinListener implements SpamEventListener {
    public static class SpamAssassinListenerGroup extends Group {}

    private static final Logger LOGGER = LoggerFactory.getLogger(SpamAssassinListener.class);
    private static final int LIMIT = 1;
    private static final Group GROUP = new SpamAssassinListenerGroup();

    private final SpamAssassin spamAssassin;
    private final SystemMailboxesProvider systemMailboxesProvider;
    private final MailboxManager mailboxManager;
    private final MailboxSessionMapperFactory mapperFactory;
    private final ExecutionMode executionMode;

    @Inject
    public SpamAssassinListener(SpamAssassin spamAssassin, SystemMailboxesProvider systemMailboxesProvider, MailboxManager mailboxManager, MailboxSessionMapperFactory mapperFactory, ExecutionMode executionMode) {
        this.spamAssassin = spamAssassin;
        this.systemMailboxesProvider = systemMailboxesProvider;
        this.mailboxManager = mailboxManager;
        this.mapperFactory = mapperFactory;
        this.executionMode = executionMode;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof MessageMoveEvent || event instanceof Added;
    }

    @Override
    public void event(Event event) throws MailboxException {
        if (event instanceof MessageMoveEvent) {
            MailboxSession session = mailboxManager.createSystemSession(getClass().getCanonicalName());
            handleMessageMove(event, session, (MessageMoveEvent) event);
        }
        if (event instanceof Added) {
            MailboxSession session = mailboxManager.createSystemSession(getClass().getCanonicalName());
            handleAdded(event, session, (Added) event);
        }
    }

    private void handleAdded(Event event, MailboxSession session, Added addedEvent) throws MailboxException {
        if (isAppendedToInbox(addedEvent)) {
            Mailbox mailbox = mapperFactory.getMailboxMapper(session).findMailboxById(addedEvent.getMailboxId());
            MessageMapper messageMapper = mapperFactory.getMessageMapper(session);

            List<InputStream> contents = MessageRange.toRanges(addedEvent.getUids())
                .stream()
                .flatMap(range -> retrieveMessages(messageMapper, mailbox, range))
                .map(Throwing.function(MailboxMessage::getFullContent))
                .collect(Guavate.toImmutableList());
            spamAssassin.learnHam(contents, event.getUser());
        }
    }

    private void handleMessageMove(Event event, MailboxSession session, MessageMoveEvent messageMoveEvent) throws MailboxException {
        if (isMessageMovedToSpamMailbox(messageMoveEvent)) {
            LOGGER.debug("Spam event detected");
            ImmutableList<InputStream> messages = retrieveMessages(messageMoveEvent, session);
            spamAssassin.learnSpam(messages, event.getUser());
        }
        if (isMessageMovedOutOfSpamMailbox(messageMoveEvent)) {
            ImmutableList<InputStream> messages = retrieveMessages(messageMoveEvent, session);
            spamAssassin.learnHam(messages, event.getUser());
        }
    }

    private Stream<MailboxMessage> retrieveMessages(MessageMapper messageMapper, Mailbox mailbox, MessageRange range) {
        try {
            return Iterators.toStream(messageMapper.findInMailbox(mailbox, range, MessageMapper.FetchType.Full, LIMIT));
        } catch (MailboxException e) {
            LOGGER.warn("Can not retrieve message {} {}", mailbox.getMailboxId(), range.toString(), e);
            return Stream.empty();
        }
    }

    private boolean isAppendedToInbox(Added addedEvent) {
        try {
            return systemMailboxesProvider.findMailbox(Role.INBOX, addedEvent.getUser())
                .getId().equals(addedEvent.getMailboxId());
        } catch (MailboxException e) {
            LOGGER.warn("Could not resolve Inbox mailbox", e);
            return false;
        }
    }

    private ImmutableList<InputStream> retrieveMessages(MessageMoveEvent messageMoveEvent, MailboxSession session) throws MailboxException {
        return mapperFactory.getMessageIdMapper(session)
            .find(messageMoveEvent.getMessageIds(), MessageMapper.FetchType.Full)
            .stream()
            .map(Throwing.function(Message::getFullContent))
            .collect(Guavate.toImmutableList());
    }

    @VisibleForTesting
    boolean isMessageMovedToSpamMailbox(MessageMoveEvent event) {
        try {
            MailboxId spamMailboxId = systemMailboxesProvider.findMailbox(Role.SPAM, event.getUser()).getId();

            return event.getMessageMoves().addedMailboxIds().contains(spamMailboxId);
        } catch (MailboxException e) {
            LOGGER.warn("Could not resolve Spam mailbox", e);
            return false;
        }
    }

    @VisibleForTesting
    boolean isMessageMovedOutOfSpamMailbox(MessageMoveEvent event) {
        try {
            MailboxId spamMailboxId = systemMailboxesProvider.findMailbox(Role.SPAM, event.getUser()).getId();
            MailboxId trashMailboxId = systemMailboxesProvider.findMailbox(Role.TRASH, event.getUser()).getId();

            return event.getMessageMoves().removedMailboxIds().contains(spamMailboxId)
                && !event.getMessageMoves().addedMailboxIds().contains(trashMailboxId);
        } catch (MailboxException e) {
            LOGGER.warn("Could not resolve Spam mailbox", e);
            return false;
        }
    }
}
