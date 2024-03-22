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

package org.apache.james.mailbox.quota.mailing.listeners;

import jakarta.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.eventsourcing.Command;
import org.apache.james.eventsourcing.CommandHandler;
import org.apache.james.eventsourcing.EventSourcingSystem;
import org.apache.james.eventsourcing.Subscriber;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.events.MailboxEvents.QuotaUsageUpdatedEvent;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.quota.mailing.QuotaMailingListenerConfiguration;
import org.apache.james.mailbox.quota.mailing.commands.DetectThresholdCrossing;
import org.apache.james.mailbox.quota.mailing.commands.DetectThresholdCrossingHandler;
import org.apache.james.mailbox.quota.mailing.subscribers.QuotaThresholdMailer;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.MailetContext;
import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

public class QuotaThresholdCrossingListener implements EventListener.ReactiveGroupEventListener {
    public static class QuotaThresholdCrossingListenerGroup extends Group {

    }

    private static final Group GROUP = new QuotaThresholdCrossingListenerGroup();

    private final EventSourcingSystem eventSourcingSystem;
    private final QuotaRootResolver quotaRootResolver;

    @Inject
    public QuotaThresholdCrossingListener(MailetContext mailetContext, UsersRepository usersRepository,
                                          FileSystem fileSystem, EventStore eventStore,
                                          HierarchicalConfiguration<ImmutableNode> config, QuotaRootResolver quotaRootResolver) {
        this(mailetContext, usersRepository, fileSystem, eventStore, QuotaMailingListenerConfiguration.from(config), quotaRootResolver);
    }

    public QuotaThresholdCrossingListener(MailetContext mailetContext, UsersRepository usersRepository,
                                          FileSystem fileSystem, EventStore eventStore,
                                          QuotaMailingListenerConfiguration config,
                                          QuotaRootResolver quotaRootResolver) {
        this.quotaRootResolver = quotaRootResolver;
        ImmutableSet<CommandHandler<? extends Command>> handlers = ImmutableSet.of(new DetectThresholdCrossingHandler(eventStore, config));
        ImmutableSet<Subscriber> subscribers = ImmutableSet.of(new QuotaThresholdMailer(mailetContext, usersRepository, fileSystem, config));
        eventSourcingSystem = EventSourcingSystem.fromJava(handlers, subscribers, eventStore);
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof QuotaUsageUpdatedEvent;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (event instanceof QuotaUsageUpdatedEvent) {
            return handleEvent((QuotaUsageUpdatedEvent) event);
        }
        return Mono.empty();
    }

    private Mono<Void> handleEvent(QuotaUsageUpdatedEvent event) {
        return Mono.from(eventSourcingSystem.dispatch(
                new DetectThresholdCrossing(quotaRootResolver.associatedUsername(event.getQuotaRoot()), event.getCountQuota(), event.getSizeQuota(), event.getInstant())))
            .then();
    }
}
