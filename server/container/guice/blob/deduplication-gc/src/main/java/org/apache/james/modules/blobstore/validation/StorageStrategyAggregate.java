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

package org.apache.james.modules.blobstore.validation;

import static org.apache.james.server.blob.deduplication.StorageStrategy.DEDUPLICATION;
import static org.apache.james.server.blob.deduplication.StorageStrategy.PASSTHROUGH;

import java.util.List;
import java.util.Optional;

import org.apache.james.eventsourcing.AggregateId;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.EventWithState;
import org.apache.james.eventsourcing.eventstore.History;
import org.apache.james.server.blob.deduplication.StorageStrategy;

import com.google.common.collect.ImmutableList;

public class StorageStrategyAggregate {
    static class State {
        static State initial() {
            return new State(Optional.empty());
        }

        static State forStorageStrategy(StorageStrategy storageStrategy) {
            return new State(Optional.of(storageStrategy));
        }

        private final Optional<StorageStrategy> storageStrategy;

        State(Optional<StorageStrategy> storageStrategy) {
            this.storageStrategy = storageStrategy;
        }

        public Optional<StorageStrategy> getStorageStrategy() {
            return storageStrategy;
        }

        public boolean holds(StorageStrategy storageStrategy) {
            return this.storageStrategy.filter(storageStrategy::equals).isPresent();
        }

        public boolean isAssignable(StorageStrategy storageStrategy) {
            if (holds(DEDUPLICATION) && storageStrategy.equals(PASSTHROUGH)) {
                return false;
            }
            return true;
        }
    }

    public static StorageStrategyAggregate load(AggregateId aggregateId, History history) {
        return new StorageStrategyAggregate(aggregateId, history);
    }

    private final AggregateId aggregateId;
    private final History history;
    private State state;

    public StorageStrategyAggregate(AggregateId aggregateId, History history) {
        this.aggregateId = aggregateId;
        this.history = history;

        this.state = State.initial();
        history.getEventsJava()
            .forEach(this::apply);
    }

    public List<EventWithState> registerStorageStrategy(RegisterStorageStrategy command) {
        if (state.holds(command.getStorageStrategy())) {
            return ImmutableList.of();
        }

        if (!state.isAssignable(command.getStorageStrategy())) {
            throw new IllegalStateException(
                String.format("Cannot use %s as a BlobStoreStorageStrategy when current BlobStoreStorageStrategy is %s",
                    command.getStorageStrategy(),
                    state.getStorageStrategy()));
        }

        return ImmutableList.of(EventWithState.noState(
            new StorageStrategyChanged(history.getNextEventId(), aggregateId, command.getStorageStrategy())));
    }

    private void apply(Event event) {
        if (event instanceof StorageStrategyChanged) {
            StorageStrategyChanged changed = (StorageStrategyChanged) event;

            this.state = State.forStorageStrategy(changed.getStorageStrategy());
        }
    }
}
