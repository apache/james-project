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
package org.apache.james.mailbox.store;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.apache.james.events.EventBus;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.RequestAware;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.transaction.Mapper;
import org.apache.james.mailbox.store.user.SubscriptionMapper;
import org.apache.james.mailbox.store.user.SubscriptionMapperFactory;
import org.apache.james.mailbox.store.user.model.Subscription;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

/**
 * Manages subscriptions for Users and Mailboxes.
 */
public class StoreSubscriptionManager implements SubscriptionManager {
    private static final int INITIAL_SIZE = 32;
    
    protected SubscriptionMapperFactory mapperFactory;
    private final MailboxSessionMapperFactory mailboxSessionMapperFactory;
    private final EventBus eventBus;

    @Inject
    public StoreSubscriptionManager(SubscriptionMapperFactory mapperFactory,
                                    MailboxSessionMapperFactory mailboxSessionMapperFactory,
                                    EventBus eventBus) {
        this.mapperFactory = mapperFactory;
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
        this.eventBus = eventBus;
    }

    @Override
    public void subscribe(MailboxSession session, MailboxPath mailbox) throws SubscriptionException {
        SubscriptionMapper mapper = mapperFactory.getSubscriptionMapper(session);
        try {
            mapper.execute(Mapper.toTransaction(() -> {
                Subscription newSubscription = new Subscription(session.getUser(), mailbox.asEscapedString());
                mapper.save(newSubscription);
            }));
        } catch (MailboxException e) {
            throw new SubscriptionException(e);
        }
        dispatchSubscribedEvent(session, mailbox).block();
    }

    @Override
    public Publisher<Void> subscribeReactive(MailboxPath mailbox, MailboxSession session) {
        try {
            SubscriptionMapper mapper = mapperFactory.getSubscriptionMapper(session);
            Subscription newSubscription = new Subscription(session.getUser(), mailbox.asEscapedString());
            return mapper.executeReactive(mapper.saveReactive(newSubscription))
                .then(dispatchSubscribedEvent(session, mailbox));
        } catch (SubscriptionException e) {
            return Mono.error(e);
        }
    }

    private Mono<Void> dispatchSubscribedEvent(MailboxSession session, MailboxPath mailboxPath) {
        return mailboxSessionMapperFactory.getMailboxMapper(session)
            .findMailboxByPath(mailboxPath)
            .flatMap(mailbox -> eventBus.dispatch(EventFactory.mailboxSubscribed()
                    .randomEventId()
                    .mailboxSession(session)
                    .mailbox(mailbox)
                    .build(),
                new MailboxIdRegistrationKey(mailbox.getMailboxId())));
    }

    @Override
    public Publisher<Void> unsubscribeReactive(MailboxPath mailbox, MailboxSession session) {
        try {
            SubscriptionMapper mapper = mapperFactory.getSubscriptionMapper(session);
            Subscription oldSubscription = new Subscription(session.getUser(), mailbox.asEscapedString());
            Optional<Subscription> legacyOldSubscription = Optional.of(new Subscription(session.getUser(), mailbox.getName()))
                .filter(any -> mailbox.belongsTo(session));
            return mapper.executeReactive(mapper.deleteReactive(oldSubscription))
                .then(legacyOldSubscription
                    .map(subscription -> mapper.executeReactive(mapper.deleteReactive(subscription)))
                    .orElse(Mono.empty()))
                .then(dispatchUnSubscribedEvent(session, mailbox));
        } catch (SubscriptionException e) {
            return Mono.error(e);
        }
    }

    @Override
    public Collection<MailboxPath> subscriptions(MailboxSession session) throws SubscriptionException {
        return mapperFactory.getSubscriptionMapper(session)
            .findSubscriptionsForUser(session.getUser())
            .stream()
            .map(Subscription::getMailbox)
            .map(s -> MailboxPath.parseEscaped(s).orElse(MailboxPath.forUser(session.getUser(), s)))
            .collect(Collectors.toCollection(() -> new HashSet<>(INITIAL_SIZE)));
    }

    @Override
    public Publisher<MailboxPath> subscriptionsReactive(MailboxSession session) throws SubscriptionException {
        return mapperFactory.getSubscriptionMapper(session)
            .findSubscriptionsForUserReactive(session.getUser())
            .map(Subscription::getMailbox)
            .map(s -> MailboxPath.parseEscaped(s).orElse(MailboxPath.forUser(session.getUser(), s)));
    }

    @Override
    public void unsubscribe(MailboxSession session, MailboxPath mailbox) throws SubscriptionException {
        SubscriptionMapper mapper = mapperFactory.getSubscriptionMapper(session);
        try {
            mapper.execute(Mapper.toTransaction(() -> mapper.delete(new Subscription(session.getUser(), mailbox.asEscapedString()))));
            if (mailbox.belongsTo(session)) {
                // Legacy purposes, remove subscriptions created prior to the MailboxPath migration. Noops for those created after.
                mapper.execute(Mapper.toTransaction(() -> mapper.delete(new Subscription(session.getUser(), mailbox.getName()))));
            }
        } catch (MailboxException e) {
            throw new SubscriptionException(e);
        }
        dispatchUnSubscribedEvent(session, mailbox).block();
    }

    private Mono<Void> dispatchUnSubscribedEvent(MailboxSession session, MailboxPath mailboxPath) {
        return mailboxSessionMapperFactory.getMailboxMapper(session)
            .findMailboxByPath(mailboxPath)
            .flatMap(mailbox -> eventBus.dispatch(EventFactory.mailboxUnSubscribed()
                    .randomEventId()
                    .mailboxSession(session)
                    .mailbox(mailbox)
                    .build(),
                new MailboxIdRegistrationKey(mailbox.getMailboxId())));
    }

    @Override
    public void endProcessingRequest(MailboxSession session) {
        if (mapperFactory instanceof RequestAware) {
            ((RequestAware)mapperFactory).endProcessingRequest(session);
        }
    }

    @Override
    public void startProcessingRequest(MailboxSession session) {
        // Do nothing        
    }
}
