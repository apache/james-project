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

package org.apache.james.queue.rabbitmq.view.cassandra.configuration;

import static org.apache.james.util.ReactorUtils.publishIfPresent;

import jakarta.inject.Inject;

import org.apache.james.eventsourcing.AggregateId;
import org.apache.james.eventsourcing.EventSourcingSystem;
import org.apache.james.eventsourcing.Subscriber;
import org.apache.james.eventsourcing.eventstore.EventStore;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

public class EventsourcingConfigurationManagement {
    static final String CONFIGURATION_AGGREGATE_KEY = "CassandraMailQueueViewConfiguration";
    static final AggregateId CONFIGURATION_AGGREGATE_ID = () -> CONFIGURATION_AGGREGATE_KEY;

    private static final ImmutableSet<Subscriber> NO_SUBSCRIBER = ImmutableSet.of();

    private final EventStore eventStore;
    private final EventSourcingSystem eventSourcingSystem;

    @Inject
    public EventsourcingConfigurationManagement(EventStore eventStore) {
        this.eventSourcingSystem = EventSourcingSystem.fromJava(
            ImmutableSet.of(new RegisterConfigurationCommandHandler(eventStore)),
            NO_SUBSCRIBER,
            eventStore);
        this.eventStore = eventStore;
    }

    @VisibleForTesting
    Mono<CassandraMailQueueViewConfiguration> load() {
        return Mono.from(eventStore.getEventsOfAggregate(CONFIGURATION_AGGREGATE_ID))
            .map(history -> ConfigurationAggregate
                .load(CONFIGURATION_AGGREGATE_ID, history)
                .getCurrentConfiguration())
            .handle(publishIfPresent());
    }

    public void registerConfiguration(CassandraMailQueueViewConfiguration newConfiguration) {
        Preconditions.checkNotNull(newConfiguration);

        Mono.from(eventSourcingSystem.dispatch(new RegisterConfigurationCommand(newConfiguration, CONFIGURATION_AGGREGATE_ID))).block();
    }
}
