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

package org.apache.james.webadmin.services;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.task.Task;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class TasksCleanupService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TasksCleanupService.class);

    public static class Context {

        public static class Snapshot {
            private final long removedTasksCount;
            private final long processedTaskCount;

            public Snapshot(long removedTasksCount, long processedTaskCount) {
                this.removedTasksCount = removedTasksCount;
                this.processedTaskCount = processedTaskCount;
            }

            public long getRemovedTasksCount() {
                return removedTasksCount;
            }

            public long getProcessedTaskCount() {
                return processedTaskCount;
            }
        }

        private final AtomicLong removedTasksCount;
        private final AtomicLong processedTaskCount;

        public Context() {
            removedTasksCount = new AtomicLong();
            processedTaskCount = new AtomicLong();
        }

        void incrementRemovedTasksCount() {
            removedTasksCount.incrementAndGet();
        }

        void incrementProcessedTaskCount() {
            processedTaskCount.incrementAndGet();
        }

        void incrementRemovedTasksCount(int count) {
            removedTasksCount.set(removedTasksCount.get() + count);
        }

        public Snapshot snapshot() {
            return new Snapshot(removedTasksCount.get(), processedTaskCount.get());
        }
    }

    private final TaskManager taskManager;

    @Inject
    public TasksCleanupService(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public Mono<Task.Result> removeBeforeDate(Instant beforeDate, Context context) {
        return Flux.from(taskManager.remove(beforeDate))
            .doOnNext(pair -> doOnNext(pair, context))
            .map(Pair::getValue)
            .reduce(Task.Result.COMPLETED, Task::combine)
            .onErrorResume(e -> {
                LOGGER.error("Error listing tasks execution detail", e);
                return Mono.just(Task.Result.PARTIAL);
            });
    }

    private static void doOnNext(Pair<TaskId, Task.Result> next, Context context) {
        context.incrementProcessedTaskCount();
        if (Task.Result.COMPLETED.equals(next.getValue())) {
            context.incrementRemovedTasksCount();
        }
    }

}
