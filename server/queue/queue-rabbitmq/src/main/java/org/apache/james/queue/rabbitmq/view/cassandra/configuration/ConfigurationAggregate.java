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

import java.util.List;
import java.util.Optional;

import org.apache.james.eventsourcing.AggregateId;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.EventWithState;
import org.apache.james.eventsourcing.eventstore.History;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

class ConfigurationAggregate {

    private static class State {

        private static State initial() {
            return new State(Optional.empty());
        }

        private Optional<CassandraMailQueueViewConfiguration> maybeConfiguration;

        State(Optional<CassandraMailQueueViewConfiguration> maybeConfiguration) {
            Preconditions.checkNotNull(maybeConfiguration);
            this.maybeConfiguration = maybeConfiguration;
        }

        State set(CassandraMailQueueViewConfiguration configuration) {
            return new State(Optional.of(configuration));
        }

    }

    static ConfigurationAggregate load(AggregateId aggregateId, History history) {
        return new ConfigurationAggregate(aggregateId, history);
    }

    private static final List<EventWithState> EMPTY_EVENTS = ImmutableList.of();

    private final AggregateId aggregateId;
    private final History history;
    private State state;

    ConfigurationAggregate(AggregateId aggregateId, History history) {
        this.aggregateId = aggregateId;
        this.history = history;
        this.state = State.initial();

        history.getEventsJava().forEach(this::apply);
    }

    List<EventWithState> registerConfiguration(CassandraMailQueueViewConfiguration configuration) {
        boolean isSame = state.maybeConfiguration.map(configuration::equals).orElse(false);
        if (isSame) {
            return EMPTY_EVENTS;
        }

        state.maybeConfiguration.ifPresent(oldConfiguration -> oldConfiguration.validateConfigurationChange(configuration));

        return ImmutableList.of(EventWithState.noState(new ConfigurationChanged(
            aggregateId,
            history.getNextEventId(),
            configuration)));
    }

    Optional<CassandraMailQueueViewConfiguration> getCurrentConfiguration() {
        return state.maybeConfiguration;
    }

    private void apply(Event event) {
        if (event instanceof ConfigurationChanged) {
            ConfigurationChanged configurationChanged = (ConfigurationChanged) event;

            state = state.set(configurationChanged.getConfiguration());
        }
    }
}
