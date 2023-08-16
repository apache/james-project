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

import javax.inject.Inject;

import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.model.search.PrefixedWildcard;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.util.ReactorUtils;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MailboxSubscriptionListener implements EventListener.ReactiveGroupEventListener {
    public static final class MailboxSubscriptionListenerGroup extends Group {

    }

    public static final Group GROUP = new MailboxSubscriptionListenerGroup();

    private final SubscriptionManager subscriptionManager;
    private final SessionProvider sessionProvider;
    private final MailboxSessionMapperFactory mailboxSessionMapperFactory;

    @Inject
    public MailboxSubscriptionListener(SubscriptionManager subscriptionManager, SessionProvider sessionProvider, MailboxSessionMapperFactory mailboxSessionMapperFactory) {
        this.subscriptionManager = subscriptionManager;
        this.sessionProvider = sessionProvider;
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof MailboxEvents.MailboxSubscribedEvent || event instanceof MailboxEvents.MailboxUnsubscribedEvent;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (event instanceof MailboxEvents.MailboxSubscribedEvent) {
            return propagateSubscriptionToParentMailboxes(event);
        }

        if (event instanceof MailboxEvents.MailboxUnsubscribedEvent) {
            return propagateUnsubscriptionToChildrenMailboxes(event);
        }

        return Mono.empty();
    }

    private Mono<Void> propagateUnsubscriptionToChildrenMailboxes(Event event) {
        MailboxEvents.MailboxUnsubscribedEvent mailboxUnsubscribedEvent = (MailboxEvents.MailboxUnsubscribedEvent) event;
        MailboxSession mailboxSession = sessionProvider.createSystemSession(event.getUsername());
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession);

        MailboxQuery.UserBound findSubMailboxesQuery = MailboxQuery.builder()
            .userAndNamespaceFrom(mailboxUnsubscribedEvent.getMailboxPath())
            .expression(new PrefixedWildcard(mailboxUnsubscribedEvent.getMailboxPath().getName() + mailboxSession.getPathDelimiter()))
            .build()
            .asUserBound();

        return mailboxMapper.findMailboxWithPathLike(findSubMailboxesQuery)
            .flatMap(subMailbox -> subscriptionManager.unsubscribeReactive(subMailbox.generateAssociatedPath(), mailboxSession), ReactorUtils.DEFAULT_CONCURRENCY)
            .then();
    }

    private Mono<Void> propagateSubscriptionToParentMailboxes(Event event) {
        MailboxEvents.MailboxSubscribedEvent mailboxSubscribedEvent = (MailboxEvents.MailboxSubscribedEvent) event;
        MailboxSession mailboxSession = sessionProvider.createSystemSession(event.getUsername());

        return Flux.fromIterable(mailboxSubscribedEvent.getMailboxPath().getParents(mailboxSession.getPathDelimiter()))
            .flatMap(parentMailbox -> subscriptionManager.subscribeReactive(parentMailbox, mailboxSession), ReactorUtils.DEFAULT_CONCURRENCY)
            .then();
    }
}
