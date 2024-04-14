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

package org.apache.james.events;

import jakarta.inject.Inject;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraEventDeadLetters implements EventDeadLetters {

    private final CassandraEventDeadLettersDAO cassandraEventDeadLettersDAO;
    private final CassandraEventDeadLettersGroupDAO cassandraEventDeadLettersGroupDAO;

    @Inject
    CassandraEventDeadLetters(CassandraEventDeadLettersDAO cassandraEventDeadLettersDAO,
                              CassandraEventDeadLettersGroupDAO cassandraEventDeadLettersGroupDAO) {
        this.cassandraEventDeadLettersDAO = cassandraEventDeadLettersDAO;
        this.cassandraEventDeadLettersGroupDAO = cassandraEventDeadLettersGroupDAO;
    }

    @Override
    public Mono<InsertionId> store(Group registeredGroup, Event failDeliveredEvent) {
        Preconditions.checkArgument(registeredGroup != null, REGISTERED_GROUP_CANNOT_BE_NULL);
        Preconditions.checkArgument(failDeliveredEvent != null, FAIL_DELIVERED_EVENT_CANNOT_BE_NULL);

        InsertionId insertionId = InsertionId.random();
        return cassandraEventDeadLettersDAO.store(registeredGroup, failDeliveredEvent, insertionId)
            .then(cassandraEventDeadLettersGroupDAO.storeGroup(registeredGroup))
            .thenReturn(insertionId);
    }

    @Override
    public Mono<Void> remove(Group registeredGroup, InsertionId failDeliveredInsertionId) {
        Preconditions.checkArgument(registeredGroup != null, REGISTERED_GROUP_CANNOT_BE_NULL);
        Preconditions.checkArgument(failDeliveredInsertionId != null, FAIL_DELIVERED_ID_INSERTION_CANNOT_BE_NULL);

        return cassandraEventDeadLettersDAO.removeEvent(registeredGroup, failDeliveredInsertionId);
    }

    @Override
    public Mono<Void> remove(Group registeredGroup) {
        Preconditions.checkArgument(registeredGroup != null, REGISTERED_GROUP_CANNOT_BE_NULL);

        return cassandraEventDeadLettersDAO.removeEvents(registeredGroup)
            .then(cassandraEventDeadLettersGroupDAO.deleteGroup(registeredGroup));
    }

    @Override
    public Mono<Event> failedEvent(Group registeredGroup, InsertionId failDeliveredInsertionId) {
        Preconditions.checkArgument(registeredGroup != null, REGISTERED_GROUP_CANNOT_BE_NULL);
        Preconditions.checkArgument(failDeliveredInsertionId != null, FAIL_DELIVERED_ID_INSERTION_CANNOT_BE_NULL);

        return cassandraEventDeadLettersDAO.retrieveFailedEvent(registeredGroup, failDeliveredInsertionId);
    }

    @Override
    public Flux<InsertionId> failedIds(Group registeredGroup) {
        Preconditions.checkArgument(registeredGroup != null, REGISTERED_GROUP_CANNOT_BE_NULL);

        return cassandraEventDeadLettersDAO.retrieveInsertionIdsWithGroup(registeredGroup);
    }

    @Override
    public Flux<Group> groupsWithFailedEvents() {
        return cassandraEventDeadLettersGroupDAO.retrieveAllGroups();
    }

    @Override
    public Mono<Boolean> containEvents() {
        return cassandraEventDeadLettersDAO.containEvents();
    }
}
