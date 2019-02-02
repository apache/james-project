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

import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.events.InVMEventBus;
import org.apache.james.mailbox.events.RegistrationKey;
import org.apache.james.mailbox.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.quota.mailing.QuotaMailingListenerConfiguration;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.server.core.JamesServerResourceLoader;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.mailet.MailetContext;

import com.google.common.collect.ImmutableSet;

class QuotaThresholdListenersTestSystem {
    private static final ImmutableSet<RegistrationKey> NO_KEYS = ImmutableSet.of();

    private final EventBus eventBus;

    QuotaThresholdListenersTestSystem(MailetContext mailetContext, EventStore eventStore, QuotaMailingListenerConfiguration configuration) throws MailboxException {
        eventBus = new InVMEventBus(new InVmEventDelivery(new NoopMetricFactory()));

        FileSystem fileSystem = new FileSystemImpl(new JamesServerResourceLoader("."));

        QuotaThresholdCrossingListener thresholdCrossingListener =
            new QuotaThresholdCrossingListener(mailetContext, MemoryUsersRepository.withVirtualHosting(), fileSystem, eventStore, configuration);

        eventBus.register(thresholdCrossingListener);
    }

    void event(Event event) {
        eventBus.dispatch(event, NO_KEYS).block();
    }
}
