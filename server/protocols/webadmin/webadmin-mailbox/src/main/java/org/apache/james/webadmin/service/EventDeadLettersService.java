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

package org.apache.james.webadmin.service;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.apache.james.events.Event;
import org.apache.james.events.EventDeadLetters;
import org.apache.james.events.Group;
import org.apache.james.task.Task;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

public class EventDeadLettersService {
    private final EventDeadLettersRedeliverService redeliverService;
    private final EventDeadLetters deadLetters;

    @Inject
    @VisibleForTesting
    public EventDeadLettersService(EventDeadLettersRedeliverService redeliverService, EventDeadLetters deadLetters) {
        this.redeliverService = redeliverService;
        this.deadLetters = deadLetters;
    }

    public List<String> listGroupsAsStrings() {
        return deadLetters.groupsWithFailedEvents()
            .map(Group::asString)
            .collect(ImmutableList.toImmutableList())
            .block();
    }

    public List<String> listGroupsInsertionIdsAsStrings(Group group) {
        return deadLetters.failedIds(group)
            .map(EventDeadLetters.InsertionId::getId)
            .map(UUID::toString)
            .collect(ImmutableList.toImmutableList())
            .block();
    }

    public Mono<Event> getEvent(Group group, EventDeadLetters.InsertionId insertionId) {
        return deadLetters.failedEvent(group, insertionId);
    }

    public void deleteEvent(Group group, EventDeadLetters.InsertionId insertionId) {
        deadLetters.remove(group, insertionId).block();
    }

    public void deleteEvents(Group group) {
        deadLetters.remove(group).block();
    }

    public Task redeliverAllEvents(EventDeadLettersRedeliverService.RunningOptions runningOptions) {
        return new EventDeadLettersRedeliverAllTask(redeliverService, runningOptions);
    }

    public Task redeliverGroupEvents(Group group, EventDeadLettersRedeliverService.RunningOptions runningOptions) {
        return new EventDeadLettersRedeliverGroupTask(redeliverService, group, runningOptions);
    }

    public Task redeliverSingleEvent(Group group, EventDeadLetters.InsertionId insertionId) {
        return new EventDeadLettersRedeliverOneTask(redeliverService, group, insertionId);
    }
}
