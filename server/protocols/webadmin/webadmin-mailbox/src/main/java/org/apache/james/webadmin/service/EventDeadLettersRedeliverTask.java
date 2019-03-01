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

import static org.apache.james.webadmin.service.EventDeadLettersRedeliverService.RedeliverResult;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.Group;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;

public class EventDeadLettersRedeliverTask implements Task {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventDeadLettersRedeliverTask.class);
    public static final String TYPE = "eventDeadLettersRedeliverTask";

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final long successfulRedeliveriesCount;
        private final long failedRedeliveriesCount;

        AdditionalInformation(long successfulRedeliveriesCount, long failedRedeliveriesCount) {
            this.successfulRedeliveriesCount = successfulRedeliveriesCount;
            this.failedRedeliveriesCount = failedRedeliveriesCount;
        }

        public long getSuccessfulRedeliveriesCount() {
            return successfulRedeliveriesCount;
        }

        public long getFailedRedeliveriesCount() {
            return failedRedeliveriesCount;
        }
    }

    private final EventDeadLettersRedeliverService service;
    private final Supplier<Flux<Tuple2<Group, Event>>> groupsWithEvents;
    private final AtomicLong successfulRedeliveriesCount;
    private final AtomicLong failedRedeliveriesCount;

    EventDeadLettersRedeliverTask(EventDeadLettersRedeliverService service, Supplier<Flux<Tuple2<Group, Event>>> groupsWithEvents) {
        this.service = service;
        this.groupsWithEvents = groupsWithEvents;
        this.successfulRedeliveriesCount = new AtomicLong(0L);
        this.failedRedeliveriesCount = new AtomicLong(0L);
    }

    @Override
    public Result run() {
        return service.redeliverEvents(groupsWithEvents)
            .map(this::updateCounters)
            .reduce(Result.COMPLETED, Task::combine)
            .block();
    }

    private Result updateCounters(RedeliverResult redeliverResult) {
        switch (redeliverResult) {
            case REDELIVER_SUCCESS:
                successfulRedeliveriesCount.incrementAndGet();
                return Result.COMPLETED;
            case REDELIVER_FAIL:
            default:
                failedRedeliveriesCount.incrementAndGet();
                return Result.PARTIAL;
        }
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
            failedRedeliveriesCount.get());
    }
}
