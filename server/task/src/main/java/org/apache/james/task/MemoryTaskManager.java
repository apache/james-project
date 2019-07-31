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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.annotation.PreDestroy;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

public class MemoryTaskManager implements TaskManager {

    private static class DetailsUpdater implements TaskManagerWorker.Listener {

        private final Consumer<TaskExecutionDetailsUpdater> updater;

        DetailsUpdater(Consumer<TaskExecutionDetailsUpdater> updater) {
            this.updater = updater;
        }

        @Override
        public void started() {
            updater.accept(TaskExecutionDetails::started);
        }

        @Override
        public void completed(Task.Result result) {
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

    public MemoryTaskManager() {

        idToExecutionDetails = new ConcurrentHashMap<>();
        worker = new SerialTaskManagerWorker();
        workQueue = new MemoryWorkQueue(worker);
    }

    public TaskId submit(Task task) {
        TaskId taskId = TaskId.generateTaskId();
        TaskExecutionDetails executionDetails = TaskExecutionDetails.from(task, taskId);
        idToExecutionDetails.put(taskId, executionDetails);
        workQueue.submit(new TaskWithId(taskId, task), updater(taskId));
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
                updateDetails(id).accept(TaskExecutionDetails::cancelRequested);
                workQueue.cancel(id);
            }
        );
    }

    @Override
    public TaskExecutionDetails await(TaskId id) {
        if (Optional.ofNullable(idToExecutionDetails.get(id)).isPresent()) {
            return Flux.interval(NOW, AWAIT_POLLING_DURATION, Schedulers.elastic())
                .map(ignored -> getExecutionDetails(id))
                .filter(details -> details.getStatus() == Status.COMPLETED
                    || details.getStatus() == Status.FAILED
                    || details.getStatus() == Status.CANCELLED)
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
