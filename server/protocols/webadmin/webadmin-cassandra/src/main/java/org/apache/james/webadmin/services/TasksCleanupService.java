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
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.task.eventsourcing.TaskAggregateId;
import org.apache.james.task.eventsourcing.TaskExecutionDetailsProjection;
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

    private final TaskExecutionDetailsProjection taskExecutionDetailsProjection;
    private final EventStore eventStore;

    @Inject
    public TasksCleanupService(TaskExecutionDetailsProjection taskExecutionDetailsProjection,
                               EventStore eventStore) {
        this.eventStore = eventStore;
        this.taskExecutionDetailsProjection = taskExecutionDetailsProjection;
    }

    public Mono<Task.Result> removeBeforeDate(Instant beforeDate, Context context) {
        return removeTask(beforeDate)
            .doOnNext(pair -> doOnNext(pair, context))
            .map(Pair::getValue)
            .reduce(Task.Result.COMPLETED, Task::combine)
            .onErrorResume(e -> {
                LOGGER.error("Error listing tasks execution detail", e);
                return Mono.just(Task.Result.PARTIAL);
            });
    }

    private Flux<Pair<TaskId, Task.Result>> removeTask(Instant beforeDate) {
        return Flux.from(taskExecutionDetailsProjection.listDetailsByBeforeDate(beforeDate))
            .filterWhen(oldTaskDetail -> canBeRemoved(oldTaskDetail, beforeDate))
            .flatMap(oldTaskDetail -> Mono.from(eventStore.remove(new TaskAggregateId(oldTaskDetail.getTaskId())))
                .then(Mono.from(taskExecutionDetailsProjection.remove(oldTaskDetail)))
                .then(Mono.just(Pair.of(oldTaskDetail.getTaskId(), Task.Result.COMPLETED)))
                .onErrorResume(error -> {
                    LOGGER.error("Error while cleanup task {}", oldTaskDetail.getTaskId().asString(), error);
                    return Mono.just(Pair.of(oldTaskDetail.getTaskId(), Task.Result.PARTIAL));
                }));
    }

    private Mono<Boolean> canBeRemoved(TaskExecutionDetails taskDetail, Instant beforeDate) {
        if (!isUnfinished(taskDetail)) {
            return Mono.just(true);
        }
        if (isStale(taskDetail, beforeDate)) {
            LOGGER.warn("Removing task {} of type {}, left in {} status with no activity since {}: " +
                    "the node running it died before completing it.",
                taskDetail.getTaskId().asString(), taskDetail.getType().asString(),
                taskDetail.getStatus().getValue(), lastActivity(taskDetail));
            return Mono.just(true);
        }
        // Still showing signs of life, yet a task without any event can not be running either: its history
        // was lost. As cancelling it has no effect, removing it is the only way to get rid of such an entry.
        return Mono.from(eventStore.getEventsOfAggregate(new TaskAggregateId(taskDetail.getTaskId())))
            .map(history -> history.getEventsJava().isEmpty());
    }

    private boolean isUnfinished(TaskExecutionDetails taskDetail) {
        return taskDetail.getStatus().equals(TaskManager.Status.WAITING)
            || taskDetail.getStatus().equals(TaskManager.Status.IN_PROGRESS);
    }

    /**
     * A task being executed keeps refreshing its execution details. One left untouched since the cleanup
     * horizon lost the node running it, and would otherwise stay in the listing forever: such an entry can
     * neither be cancelled nor deleted through any other route.
     */
    private boolean isStale(TaskExecutionDetails taskDetail, Instant beforeDate) {
        return lastActivity(taskDetail).isBefore(beforeDate);
    }

    private Instant lastActivity(TaskExecutionDetails taskDetail) {
        Instant lastKnownDate = taskDetail.getStartedDate()
            .map(ZonedDateTime::toInstant)
            .orElseGet(() -> taskDetail.getSubmittedDate().toInstant());

        return taskDetail.getAdditionalInformation()
            .map(TaskExecutionDetails.AdditionalInformation::timestamp)
            .filter(lastKnownDate::isBefore)
            .orElse(lastKnownDate);
    }

    private static void doOnNext(Pair<TaskId, Task.Result> next, Context context) {
        context.incrementProcessedTaskCount();
        if (Task.Result.COMPLETED.equals(next.getValue())) {
            context.incrementRemovedTasksCount();
        }
    }

}
