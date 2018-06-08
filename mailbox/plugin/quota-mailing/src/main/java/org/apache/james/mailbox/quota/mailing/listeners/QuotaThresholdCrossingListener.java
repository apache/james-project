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

import javax.inject.Inject;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.core.User;
import org.apache.james.eventsourcing.CommandHandler;
import org.apache.james.eventsourcing.EventSourcingSystem;
import org.apache.james.eventsourcing.Subscriber;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.quota.mailing.QuotaMailingListenerConfiguration;
import org.apache.james.mailbox.quota.mailing.commands.DetectThresholdCrossing;
import org.apache.james.mailbox.quota.mailing.commands.DetectThresholdCrossingHandler;
import org.apache.james.mailbox.quota.mailing.subscribers.QuotaThresholdMailer;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.MailetContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

public class QuotaThresholdCrossingListener implements MailboxListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuotaThresholdCrossingListener.class);

    private final EventSourcingSystem eventSourcingSystem;

    @Inject
    public QuotaThresholdCrossingListener(MailetContext mailetContext, UsersRepository usersRepository,
                                          FileSystem fileSystem, EventStore eventStore,
                                          HierarchicalConfiguration config) {
        this(mailetContext, usersRepository, fileSystem, eventStore, QuotaMailingListenerConfiguration.from(config));
    }

    public QuotaThresholdCrossingListener(MailetContext mailetContext, UsersRepository usersRepository,
                                          FileSystem fileSystem, EventStore eventStore,
                                          QuotaMailingListenerConfiguration config) {
        ImmutableSet<CommandHandler<?>> handlers = ImmutableSet.of(new DetectThresholdCrossingHandler(eventStore, config));
        ImmutableSet<Subscriber> subscribers = ImmutableSet.of(new QuotaThresholdMailer(mailetContext, usersRepository, fileSystem, config));
        eventSourcingSystem = new EventSourcingSystem(handlers, subscribers, eventStore);
    }

    @Override
    public ListenerType getType() {
        return ListenerType.ONCE;
    }

    @Override
    public void event(Event event) {
        try {
            if (event instanceof QuotaUsageUpdatedEvent) {
                handleEvent(getUser(event), (QuotaUsageUpdatedEvent) event);
            }
        } catch (Exception e) {
            LOGGER.error("Can not re-emmit quota threshold events", e);
        }
    }

    private void handleEvent(User user, QuotaUsageUpdatedEvent event) {
        eventSourcingSystem.dispatch(
            new DetectThresholdCrossing(user, event.getCountQuota(), event.getSizeQuota(), event.getInstant()));
    }

    private User getUser(Event event) {
        return User.fromUsername(
            event.getSession()
                .getUser()
                .getUserName());
    }

}
