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

package org.apache.james.webadmin.tasks;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.apache.james.webadmin.services.TasksCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TasksCleanupTask implements Task {
    public static final Logger LOGGER = LoggerFactory.getLogger(TasksCleanupTask.class);
    public static final TaskType TASK_TYPE = TaskType.of("tasks-cleanup");

    public static class Details implements TaskExecutionDetails.AdditionalInformation {
        private final Instant instant;
        private final long removedTasksCount;
        private final long processedTaskCount;
        private final Instant olderThan;

        public Details(Instant instant, long removedTasksCount, long processedTaskCount, Instant olderThan) {
            this.instant = instant;
            this.removedTasksCount = removedTasksCount;
            this.processedTaskCount = processedTaskCount;
            this.olderThan = olderThan;
        }

        @Override
        public Instant timestamp() {
            return instant;
        }

        public long getRemovedTasksCount() {
            return removedTasksCount;
        }

        public long getProcessedTaskCount() {
            return processedTaskCount;
        }

        public Instant getOlderThan() {
            return olderThan;
        }
    }

    private final TasksCleanupService service;
    private final Instant beforeDate;
    private final TasksCleanupService.Context context;

    public TasksCleanupTask(TasksCleanupService service, Instant beforeDate) {
        this.service = service;
        this.beforeDate = beforeDate;
        this.context = new TasksCleanupService.Context();
    }

    @Override
    public Result run() throws InterruptedException {
        return service.removeBeforeDate(beforeDate, context)
            .block();
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        TasksCleanupService.Context.Snapshot snapshot = context.snapshot();
        return Optional.of(new Details(Clock.systemUTC().instant(),
            snapshot.getRemovedTasksCount(), snapshot.getProcessedTaskCount(), beforeDate));
    }

    public Instant getBeforeDate() {
        return beforeDate;
    }
}
