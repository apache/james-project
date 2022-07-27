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

import static org.apache.james.webadmin.service.EventDeadLettersRedeliverService.RunningOptions;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.apache.james.webadmin.service.EventDeadLettersRedeliveryTaskAdditionalInformationDTO.EventDeadLettersRedeliveryTaskAdditionalInformationForAll;

public class EventDeadLettersRedeliverAllTask implements Task {
    public static final TaskType TYPE = TaskType.of("event-dead-letters-redeliver-all");

    private final EventDeadLettersRedeliverService service;
    private final EventRetriever eventRetriever;
    private final AtomicLong successfulRedeliveriesCount;
    private final AtomicLong failedRedeliveriesCount;
    private final RunningOptions runningOptions;

    EventDeadLettersRedeliverAllTask(EventDeadLettersRedeliverService service, RunningOptions runningOptions) {
        this.service = service;
        this.eventRetriever = EventRetriever.allEvents();
        this.successfulRedeliveriesCount = new AtomicLong(0L);
        this.failedRedeliveriesCount = new AtomicLong(0L);
        this.runningOptions = runningOptions;
    }

    @Override
    public Result run() {
        return service.redeliverEvents(eventRetriever, runningOptions)
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
    public TaskType type() {
        return TYPE;
    }

    public RunningOptions getRunningOptions() {
        return runningOptions;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(createAdditionalInformation());
    }

    private EventDeadLettersRedeliveryTaskAdditionalInformation createAdditionalInformation() {
        return new EventDeadLettersRedeliveryTaskAdditionalInformationForAll(
            successfulRedeliveriesCount.get(),
            failedRedeliveriesCount.get(),
            Clock.systemUTC().instant(),
            getRunningOptions());
    }
}
