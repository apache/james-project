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

package org.apache.james.task;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.annotation.PreDestroy;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class MemoryTaskManager implements TaskManager {

    private static class DetailsUpdater implements TaskManagerWorker.Listener {

        private final Consumer<TaskExecutionDetailsUpdater> updater;

        DetailsUpdater(Consumer<TaskExecutionDetailsUpdater> updater) {
            this.updater = updater;
        }

        @Override
        public void started() {
            updater.accept(TaskExecutionDetails::start);
        }

        @Override
        public void completed() {
            updater.accept(TaskExecutionDetails::completed);
        }

        @Override
        public void failed(Throwable t) {
            failed();
        }

        @Override
        public void failed() {
            updater.accept(TaskExecutionDetails::failed);
        }

        @Override
        public void cancelled() {
            updater.accept(TaskExecutionDetails::cancelEffectively);
        }
    }

    public static final Duration AWAIT_POLLING_DURATION = Duration.ofMillis(500);
    public static final Duration NOW = Duration.ZERO;
    private final WorkQueue workQueue;
    private final TaskManagerWorker worker;
    private final ConcurrentHashMap<TaskId, TaskExecutionDetails> idToExecutionDetails;
    private final ConcurrentHashMap<TaskId, Mono<Task.Result>> tasksResult;

    public MemoryTaskManager() {

        idToExecutionDetails = new ConcurrentHashMap<>();
        tasksResult = new ConcurrentHashMap<>();
        worker = new MemoryTaskManagerWorker();
        workQueue = WorkQueue.builder()
            .worker(this::treatTask)
            .listener(this::listenToWorkQueueEvents);
    }

    private void listenToWorkQueueEvents(WorkQueue.Event event) {
        switch (event.status) {
            case CANCELLED:
                updateDetails(event.id).accept(TaskExecutionDetails::cancelEffectively);
                break;
            case STARTED:
                break;
        }
    }

    private void treatTask(TaskWithId task) {
        DetailsUpdater detailsUpdater = updater(task.getId());
        Mono<Task.Result> result = worker.executeTask(task, detailsUpdater);
        tasksResult.put(task.getId(), result);
        try {
            BiConsumer<Throwable, Object> ignoreException = (t, o) -> { };
            result
                .onErrorContinue(InterruptedException.class, ignoreException)
                .block();
        } catch (CancellationException e) {
            // Do not throw CancellationException
        }
    }

    public TaskId submit(Task task) {
        TaskId taskId = TaskId.generateTaskId();
        TaskExecutionDetails executionDetails = TaskExecutionDetails.from(task, taskId);
        idToExecutionDetails.put(taskId, executionDetails);
        workQueue.submit(new TaskWithId(taskId, task));
        return taskId;
    }

    @Override
    public TaskExecutionDetails getExecutionDetails(TaskId id) {
        return Optional.ofNullable(idToExecutionDetails.get(id))
            .orElseThrow(TaskNotFoundException::new);
    }

    @Override
    public List<TaskExecutionDetails> list() {
        return ImmutableList.copyOf(idToExecutionDetails.values());
    }

    @Override
    public List<TaskExecutionDetails> list(Status status) {
        return ImmutableList.copyOf(tasksFiltered(status).values());
    }

    public Map<TaskId, TaskExecutionDetails> tasksFiltered(Status status) {
        return idToExecutionDetails.entrySet()
            .stream()
            .filter(details -> details.getValue().getStatus().equals(status))
            .collect(Guavate.entriesToImmutableMap());
    }

    @Override
    public void cancel(TaskId id) {
        Optional.ofNullable(idToExecutionDetails.get(id)).ifPresent(details -> {
                if (details.getStatus().equals(Status.WAITING)) {
                    updateDetails(id).accept(TaskExecutionDetails::cancelRequested);
                }
                workQueue.cancel(id);
                worker.cancelTask(id, updater(id));
            }
        );
    }

    @Override
    public TaskExecutionDetails await(TaskId id) {
        if (Optional.ofNullable(idToExecutionDetails.get(id)).isPresent()) {
            return Flux.interval(NOW, AWAIT_POLLING_DURATION, Schedulers.elastic())
                .filter(ignore -> tasksResult.get(id) != null)
                .map(ignore -> {
                    Optional.ofNullable(tasksResult.get(id))
                        .ifPresent(mono -> {
                            try {
                                mono.block();
                            } catch (CancellationException e) {
                                // ignore
                            }
                        });
                    return getExecutionDetails(id);
                })
                .take(1)
                .blockFirst();
        } else {
            return null;
        }
    }

    @PreDestroy
    public void stop() {
        try {
            workQueue.close();
        } catch (IOException ignored) {
            //avoid noise when closing the workqueue
        }
    }

    private DetailsUpdater updater(TaskId id) {
        return new DetailsUpdater(updateDetails(id));
    }

    private Consumer<TaskExecutionDetailsUpdater> updateDetails(TaskId taskId) {
        return updater -> {
            TaskExecutionDetails currentDetails = idToExecutionDetails.get(taskId);
            TaskExecutionDetails newDetails = updater.update(currentDetails);
            idToExecutionDetails.replace(taskId, newDetails);
        };
    }
}
