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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.mailbox.events.EventDeadLetters;
import org.apache.james.mailbox.events.Group;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;

import com.fasterxml.jackson.annotation.JsonInclude;

public class EventDeadLettersRedeliverTask implements Task {
    public static final String TYPE = "eventDeadLettersRedeliverTask";

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final long successfulRedeliveriesCount;
        private final long failedRedeliveriesCount;
        private final Optional<Group> group;
        private final Optional<EventDeadLetters.InsertionId> insertionId;

        AdditionalInformation(long successfulRedeliveriesCount, long failedRedeliveriesCount,
                              Optional<Group> group, Optional<EventDeadLetters.InsertionId> insertionId) {
            this.successfulRedeliveriesCount = successfulRedeliveriesCount;
            this.failedRedeliveriesCount = failedRedeliveriesCount;
            this.group = group;
            this.insertionId = insertionId;
        }

        public long getSuccessfulRedeliveriesCount() {
            return successfulRedeliveriesCount;
        }

        public long getFailedRedeliveriesCount() {
            return failedRedeliveriesCount;
        }

        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        public Optional<String> getGroup() {
            return group.map(Group::asString);
        }

        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        public Optional<String> getInsertionId() {
            return insertionId.map(insertionId -> insertionId.getId().toString());
        }
    }

    private final EventDeadLettersRedeliverService service;
    private final EventRetriever eventRetriever;
    private final AtomicLong successfulRedeliveriesCount;
    private final AtomicLong failedRedeliveriesCount;

    EventDeadLettersRedeliverTask(EventDeadLettersRedeliverService service, EventRetriever eventRetriever) {
        this.service = service;
        this.eventRetriever = eventRetriever;
        this.successfulRedeliveriesCount = new AtomicLong(0L);
        this.failedRedeliveriesCount = new AtomicLong(0L);
    }

    @Override
    public Result run() {
        return service.redeliverEvents(eventRetriever)
            .map(this::updateCounters)
            .reduce(Result.COMPLETED, Task::combine)
            .block();
    }

    private Result updateCounters(Result result) {
        switch (result) {
            case COMPLETED:
                successfulRedeliveriesCount.incrementAndGet();
                break;
            case PARTIAL:
                failedRedeliveriesCount.incrementAndGet();
                break;
            default:
                throw new RuntimeException("Result case: " + result.toString() + " not recognized");
        }
        return result;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(createAdditionalInformation());
    }

    AdditionalInformation createAdditionalInformation() {
        return new AdditionalInformation(
            successfulRedeliveriesCount.get(),
            failedRedeliveriesCount.get(),
            eventRetriever.forGroup(),
            eventRetriever.forEvent());
    }
}
