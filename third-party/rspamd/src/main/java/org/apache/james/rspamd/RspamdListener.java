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

package org.apache.james.rspamd;


import java.nio.ByteBuffer;

import javax.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.SystemMailboxesProvider;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.events.MessageMoveEvent;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.event.SpamEventListener;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.rspamd.client.RspamdClientConfiguration;
import org.apache.james.rspamd.client.RspamdHttpClient;
import org.apache.james.util.FunctionalUtils;
import org.apache.james.util.ReactorUtils;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RspamdListener implements SpamEventListener, EventListener.ReactiveGroupEventListener {
    public static class RspamdListenerGroup extends Group {

    }

    public static class RspamdListenerConfiguration {
        public static final RspamdListenerConfiguration DEFAULT = new RspamdListenerConfiguration(true);

        public static RspamdListenerConfiguration from(HierarchicalConfiguration<ImmutableNode> configuration) {
            return new RspamdListenerConfiguration(configuration.getBoolean("reportAdded", true));
        }

        private final boolean reportAdded;

        public RspamdListenerConfiguration(boolean reportAdded) {
            this.reportAdded = reportAdded;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(RspamdListener.class);

    private static final int LIMIT = 1;
    private static final Group GROUP = new RspamdListenerGroup();

    private final RspamdHttpClient rspamdHttpClient;
    private final RspamdClientConfiguration configuration;
    private final MailboxManager mailboxManager;
    private final MailboxSessionMapperFactory mapperFactory;
    private final SystemMailboxesProvider systemMailboxesProvider;
    private final RspamdListenerConfiguration rspamdListenerConfiguration;

    public RspamdListener(RspamdHttpClient rspamdHttpClient, MailboxManager mailboxManager, MailboxSessionMapperFactory mapperFactory, 
                          SystemMailboxesProvider systemMailboxesProvider, RspamdClientConfiguration configuration, RspamdListenerConfiguration rspamdListenerConfiguration) {
        this.rspamdHttpClient = rspamdHttpClient;
        this.configuration = configuration;
        this.mailboxManager = mailboxManager;
        this.mapperFactory = mapperFactory;
        this.systemMailboxesProvider = systemMailboxesProvider;
        this.rspamdListenerConfiguration = rspamdListenerConfiguration;
    }

    @Inject
    public RspamdListener(RspamdHttpClient rspamdHttpClient, MailboxManager mailboxManager, MailboxSessionMapperFactory mapperFactory,
                          SystemMailboxesProvider systemMailboxesProvider, RspamdClientConfiguration configuration, HierarchicalConfiguration<ImmutableNode> rspamdListenerConfiguration) {
        this.rspamdHttpClient = rspamdHttpClient;
        this.configuration = configuration;
        this.mailboxManager = mailboxManager;
        this.mapperFactory = mapperFactory;
        this.systemMailboxesProvider = systemMailboxesProvider;
        this.rspamdListenerConfiguration = RspamdListenerConfiguration.from(rspamdListenerConfiguration);
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof MessageMoveEvent || event instanceof MailboxEvents.Added;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (event instanceof MessageMoveEvent) {
            return handleMessageMoved((MessageMoveEvent) event);
        } else if (rspamdListenerConfiguration.reportAdded && event instanceof MailboxEvents.Added) {
            return handleMessageAdded((MailboxEvents.Added) event);
        }
        return Mono.empty();
    }

    private Mono<Void> handleMessageAdded(MailboxEvents.Added addedEvent) {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(Username.of(getClass().getCanonicalName()));
        return isAppendedToInbox(addedEvent)
            .filter(FunctionalUtils.identityPredicate())
            .doOnNext(isHam -> LOGGER.debug("Ham event detected, EventId = {}", addedEvent.getEventId().getId()))
            .flatMap(any -> reportHamWhenAdded(addedEvent, mailboxSession))
            .then(Mono.fromRunnable(() -> mailboxManager.endProcessingRequest(mailboxSession)));
    }

    private Mono<Void> handleMessageMoved(MessageMoveEvent messageMoveEvent) {
        return handleMessageMoved(mailboxMessagePublisher(messageMoveEvent), messageMoveEvent);
    }

    private Mono<Void> reportHamWhenAdded(MailboxEvents.Added addedEvent, MailboxSession session) {
        return mapperFactory.getMailboxMapper(session)
            .findMailboxById(addedEvent.getMailboxId())
            .map(mailbox -> Pair.of(mailbox, mapperFactory.getMessageMapper(session)))
            .flatMapMany(pair -> Flux.fromIterable(MessageRange.toRanges(addedEvent.getUids()))
                .flatMap(range -> pair.getRight().findInMailboxReactive(pair.getLeft(), range, MessageMapper.FetchType.FULL, LIMIT)))
            .map(MailboxMessage::getFullContentReactive)
            .flatMap(content -> reportHam(content, addedEvent), ReactorUtils.DEFAULT_CONCURRENCY)
            .then();
    }

    private Flux<ByteBuffer> mailboxMessagePublisher(MessageMoveEvent messageMoveEvent) {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(Username.of(getClass().getCanonicalName()));
        return Mono.fromCallable(() -> mapperFactory.getMessageIdMapper(mailboxSession))
            .flatMapMany(messageIdMapper -> messageIdMapper.findReactive(messageMoveEvent.getMessageIds(), MessageMapper.FetchType.FULL))
            .flatMap(MailboxMessage::getFullContentReactive)
            .doFinally(any -> mailboxManager.endProcessingRequest(mailboxSession));
    }

    private Mono<Void> handleMessageMoved(Flux<ByteBuffer> mailboxMessagesPublisher, MessageMoveEvent messageMoveEvent) {
        Mono<Boolean> reportHamIfNotSpamDetected = isMessageMovedOutOfSpamMailbox(messageMoveEvent)
            .filter(FunctionalUtils.identityPredicate())
            .doOnNext(isHam -> LOGGER.debug("Ham event detected, EventId = {}", messageMoveEvent.getEventId().getId()));

        return isMessageMovedToSpamMailbox(messageMoveEvent)
            .flatMap(isSpam -> {
                if (isSpam) {
                    LOGGER.debug("Spam event detected, EventId = {}", messageMoveEvent.getEventId().getId());
                    return reportSpam(mailboxMessagesPublisher, messageMoveEvent)
                        .then();
                } else {
                    return reportHamIfNotSpamDetected
                        .flatMapMany(isHam -> reportHam(mailboxMessagesPublisher, messageMoveEvent))
                        .then();
                }
            });
    }

    private Mono<Void> reportHam(Publisher<ByteBuffer> content, Event messageMoveEvent) {
        if (configuration.usePerUserBayes()) {
            return rspamdHttpClient.reportAsHam(content, RspamdHttpClient.Options.forUser(messageMoveEvent.getUsername()));
        } else {
            return rspamdHttpClient.reportAsHam(content);
        }
    }

    private Mono<Void> reportSpam(Flux<ByteBuffer> mailboxMessagesPublisher, MessageMoveEvent messageMoveEvent) {
        if (configuration.usePerUserBayes()) {
            return rspamdHttpClient.reportAsSpam(mailboxMessagesPublisher, RspamdHttpClient.Options.forUser(messageMoveEvent.getUsername()));
        } else {
            return rspamdHttpClient.reportAsSpam(mailboxMessagesPublisher);
        }
    }

    @VisibleForTesting
    Mono<Boolean> isMessageMovedToSpamMailbox(MessageMoveEvent event) {
        return isMessageMovedToMailbox(event, Role.SPAM);
    }

    @VisibleForTesting
    Mono<Boolean> isMessageMovedOutOfSpamMailbox(MessageMoveEvent event) {
        return isMessageMovedOutToMailbox(event, Role.SPAM)
            .zipWith(isMessageMovedToMailbox(event, Role.TRASH))
            .map(tuple -> tuple.getT1() && !tuple.getT2());
    }

    @VisibleForTesting
    Mono<Boolean> isAppendedToInbox(MailboxEvents.Added addedEvent) {
        return Flux.from(systemMailboxesProvider.getMailboxByRole(Role.INBOX, addedEvent.getUsername()))
            .next()
            .map(MessageManager::getId)
            .map(mailboxId -> mailboxId.equals(addedEvent.getMailboxId()))
            .onErrorResume(e -> {
                LOGGER.warn("Could not resolve Inbox mailbox", e);
                return Mono.just(false);
            });
    }

    private Mono<Boolean> isMessageMovedToMailbox(MessageMoveEvent event, Role role) {
        return Flux.from(systemMailboxesProvider.getMailboxByRole(role, event.getUsername()))
            .next()
            .map(MessageManager::getId)
            .map(spamMailboxId -> event.getMessageMoves().addedMailboxIds().contains(spamMailboxId))
            .onErrorResume(e -> {
                LOGGER.warn("Could not resolve {} mailbox", role, e);
                return Mono.just(false);
            });
    }

    private Mono<Boolean> isMessageMovedOutToMailbox(MessageMoveEvent event, Role role) {
        return Flux.from(systemMailboxesProvider.getMailboxByRole(role, event.getUsername()))
            .next()
            .map(MessageManager::getId)
            .map(spamMailboxId -> event.getMessageMoves().removedMailboxIds().contains(spamMailboxId))
            .onErrorResume(e -> {
                LOGGER.warn("Could not resolve {} mailbox", role, e);
                return Mono.just(false);
            });
    }
}