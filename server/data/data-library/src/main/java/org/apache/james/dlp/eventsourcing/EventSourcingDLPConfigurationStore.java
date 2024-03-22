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

import jakarta.inject.Inject;

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
import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

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
    public Publisher<DLPRules> list(Domain domain) {

        DLPAggregateId aggregateId = new DLPAggregateId(domain);

        return Mono.from(eventStore.getEventsOfAggregate(aggregateId))
            .map(history -> DLPDomainConfiguration.load(aggregateId, history).retrieveRules());
    }

    @Override
    public void store(Domain domain, DLPRules rules) {
        Mono.from(eventSourcingSystem.dispatch(new StoreCommand(domain, rules))).block();
    }

    @Override
    public void clear(Domain domain) {
        Mono.from(eventSourcingSystem.dispatch(new ClearCommand(domain))).block();
    }

    @Override
    public Optional<DLPConfigurationItem> fetch(Domain domain, Id ruleId) {
        return Mono.from(list(domain))
                .flatMapIterable(DLPRules::getItems)
                .toStream()
                .filter((DLPConfigurationItem item) -> item.getId().equals(ruleId))
                .findFirst();
    }

}
