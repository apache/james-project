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

package org.apache.james.mailbox.events;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MemoryEventDeadLetters implements EventDeadLetters {

    private final Table<Group, InsertionId, Event> deadLetters;

    public MemoryEventDeadLetters() {
        this.deadLetters = HashBasedTable.create();
    }

    @Override
    public Mono<Void> store(Group registeredGroup, Event failDeliveredEvent, InsertionId insertionId) {
        Preconditions.checkArgument(registeredGroup != null, REGISTERED_GROUP_CANNOT_BE_NULL);
        Preconditions.checkArgument(failDeliveredEvent != null, FAIL_DELIVERED_EVENT_CANNOT_BE_NULL);
        Preconditions.checkArgument(insertionId != null, FAIL_DELIVERED_ID_INSERTION_CANNOT_BE_NULL);

        synchronized (deadLetters) {
            deadLetters.put(registeredGroup, insertionId, failDeliveredEvent);
            return Mono.empty();
        }
    }

    @Override
    public Mono<Void> remove(Group registeredGroup, InsertionId failDeliveredInsertionId) {
        Preconditions.checkArgument(registeredGroup != null, REGISTERED_GROUP_CANNOT_BE_NULL);
        Preconditions.checkArgument(failDeliveredInsertionId != null, FAIL_DELIVERED_ID_INSERTION_CANNOT_BE_NULL);

        synchronized (deadLetters) {
            deadLetters.remove(registeredGroup, failDeliveredInsertionId);
            return Mono.empty();
        }
    }

    @Override
    public Mono<Event> failedEvent(Group registeredGroup, InsertionId failDeliveredInsertionId) {
        Preconditions.checkArgument(registeredGroup != null, REGISTERED_GROUP_CANNOT_BE_NULL);
        Preconditions.checkArgument(failDeliveredInsertionId != null, FAIL_DELIVERED_ID_INSERTION_CANNOT_BE_NULL);

        synchronized (deadLetters) {
            return Mono.justOrEmpty(deadLetters.get(registeredGroup, failDeliveredInsertionId));
        }
    }

    @Override
    public Flux<InsertionId> failedIds(Group registeredGroup) {
        Preconditions.checkArgument(registeredGroup != null, REGISTERED_GROUP_CANNOT_BE_NULL);

        synchronized (deadLetters) {
            return Flux.fromIterable(ImmutableList.copyOf(deadLetters.row(registeredGroup).keySet()));
        }
    }

    @Override
    public Flux<Group> groupsWithFailedEvents() {
        synchronized (deadLetters) {
            return Flux.fromIterable(ImmutableList.copyOf(deadLetters.rowKeySet()));
        }
    }
}
