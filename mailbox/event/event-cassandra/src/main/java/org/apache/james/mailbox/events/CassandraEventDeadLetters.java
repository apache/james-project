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

import javax.inject.Inject;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraEventDeadLetters implements EventDeadLetters {

    private final CassandraEventDeadLettersDAO cassandraEventDeadLettersDAO;

    @Inject
    public CassandraEventDeadLetters(CassandraEventDeadLettersDAO cassandraEventDeadLettersDAO) {
        this.cassandraEventDeadLettersDAO = cassandraEventDeadLettersDAO;
    }

    @Override
    public Mono<Void> store(Group registeredGroup, Event failDeliveredEvent) {
        Preconditions.checkArgument(registeredGroup != null, REGISTERED_GROUP_CANNOT_BE_NULL);
        Preconditions.checkArgument(failDeliveredEvent != null, FAIL_DELIVERED_EVENT_CANNOT_BE_NULL);

        return cassandraEventDeadLettersDAO.store(registeredGroup, failDeliveredEvent);
    }

    @Override
    public Mono<Void> remove(Group registeredGroup, Event.EventId failDeliveredEventId) {
        Preconditions.checkArgument(registeredGroup != null, REGISTERED_GROUP_CANNOT_BE_NULL);
        Preconditions.checkArgument(failDeliveredEventId != null, FAIL_DELIVERED_ID_EVENT_CANNOT_BE_NULL);

        return cassandraEventDeadLettersDAO.removeEvent(registeredGroup, failDeliveredEventId);
    }

    @Override
    public Mono<Event> failedEvent(Group registeredGroup, Event.EventId failDeliveredEventId) {
        Preconditions.checkArgument(registeredGroup != null, REGISTERED_GROUP_CANNOT_BE_NULL);
        Preconditions.checkArgument(failDeliveredEventId != null, FAIL_DELIVERED_ID_EVENT_CANNOT_BE_NULL);

        return cassandraEventDeadLettersDAO.retrieveFailedEvent(registeredGroup, failDeliveredEventId);
    }

    @Override
    public Flux<Event.EventId> failedEventIds(Group registeredGroup) {
        Preconditions.checkArgument(registeredGroup != null, REGISTERED_GROUP_CANNOT_BE_NULL);

        return cassandraEventDeadLettersDAO.retrieveEventIdsWithGroup(registeredGroup);
    }

    @Override
    public Flux<Group> groupsWithFailedEvents() {
        return cassandraEventDeadLettersDAO.retrieveAllGroups();
    }
}
