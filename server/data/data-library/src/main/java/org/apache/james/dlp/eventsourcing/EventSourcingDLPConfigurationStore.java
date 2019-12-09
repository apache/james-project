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

package org.apache.james.dlp.eventsourcing;

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.dlp.api.DLPConfigurationItem;
import org.apache.james.dlp.api.DLPConfigurationItem.Id;
import org.apache.james.dlp.api.DLPConfigurationStore;
import org.apache.james.dlp.api.DLPRules;
import org.apache.james.dlp.eventsourcing.aggregates.DLPAggregateId;
import org.apache.james.dlp.eventsourcing.aggregates.DLPDomainConfiguration;
import org.apache.james.dlp.eventsourcing.commands.ClearCommand;
import org.apache.james.dlp.eventsourcing.commands.ClearCommandHandler;
import org.apache.james.dlp.eventsourcing.commands.StoreCommand;
import org.apache.james.dlp.eventsourcing.commands.StoreCommandHandler;
import org.apache.james.eventsourcing.EventSourcingSystem;
import org.apache.james.eventsourcing.Subscriber;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.util.streams.Iterables;

import com.google.common.collect.ImmutableSet;

public class EventSourcingDLPConfigurationStore implements DLPConfigurationStore {

    private static final ImmutableSet<Subscriber> NO_SUBSCRIBER = ImmutableSet.of();

    private final EventSourcingSystem eventSourcingSystem;
    private final EventStore eventStore;

    @Inject
    public EventSourcingDLPConfigurationStore(EventStore eventStore) {
        this.eventSourcingSystem = EventSourcingSystem.fromJava(
            ImmutableSet.of(
                new ClearCommandHandler(eventStore),
                new StoreCommandHandler(eventStore)),
            NO_SUBSCRIBER,
            eventStore);
        this.eventStore = eventStore;
    }

    @Override
    public DLPRules list(Domain domain) {

        DLPAggregateId aggregateId = new DLPAggregateId(domain);

        return DLPDomainConfiguration.load(
                aggregateId,
                eventStore.getEventsOfAggregate(aggregateId))
            .retrieveRules();
    }

    @Override
    public void store(Domain domain, DLPRules rules) {
        eventSourcingSystem.dispatch(new StoreCommand(domain, rules));
    }

    @Override
    public void clear(Domain domain) {
        eventSourcingSystem.dispatch(new ClearCommand(domain));
    }

    @Override
    public Optional<DLPConfigurationItem> fetch(Domain domain, Id ruleId) {
        return Iterables.toStream(list(domain))
                .filter((DLPConfigurationItem item) -> item.getId().equals(ruleId))
                .findFirst();
    }

}
