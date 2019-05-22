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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javax.annotation.PreDestroy;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.WorkQueueProcessor;
import reactor.core.scheduler.Schedulers;

public class MemoryTaskManager implements TaskManager {
    public static final Duration AWAIT_POLLING_DURATION = Duration.ofMillis(500);
    public static final Duration NOW = Duration.ZERO;
    private final WorkQueueProcessor<TaskWithId> workQueue;
    private final TaskManagerWorker worker;
    private final ConcurrentHashMap<TaskId, TaskExecutionDetails> idToExecutionDetails;
    private final ConcurrentHashMap<TaskId, Mono<Task.Result>> tasksResult;
    private final ExecutorService taskExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService requestTaskExecutor = Executors.newSingleThreadExecutor();

    public MemoryTaskManager() {
        workQueue = WorkQueueProcessor.<TaskWithId>builder()
            .executor(taskExecutor)
            .requestTaskExecutor(requestTaskExecutor)
            .build();
        idToExecutionDetails = new ConcurrentHashMap<>();
        tasksResult = new ConcurrentHashMap<>();
        worker = new MemoryTaskManagerWorker();
        workQueue
            .subscribeOn(Schedulers.single())
            .filter(task -> !listIds(Status.CANCELLED).contains(task.getId()))
            .subscribe(this::treatTask);
    }

    private void treatTask(TaskWithId task) {
        Mono<Task.Result> result = worker.executeTask(task, updateDetails(task.getId()));
        tasksResult.put(task.getId(), result);
        try {
            result.block();
        } catch (CancellationException e) {
            // Do not throw CancellationException
        }
    }

    public TaskId submit(Task task) {
        TaskId taskId = TaskId.generateTaskId();
        TaskExecutionDetails executionDetails = TaskExecutionDetails.from(task, taskId);
        idToExecutionDetails.put(taskId, executionDetails);
        workQueue.onNext(new TaskWithId(taskId, task));
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

    public Set<TaskId> listIds(Status status) {
        return tasksFiltered(status).keySet();
    }

    public Map<TaskId, TaskExecutionDetails> tasksFiltered(Status status) {
        return idToExecutionDetails.entrySet()
            .stream()
            .filter(details -> details.getValue().getStatus().equals(status))
            .collect(Guavate.entriesToImmutableMap());
    }

    @Override
    public void cancel(TaskId id) {
        TaskExecutionDetails details = getExecutionDetails(id);
        if (details.getStatus().equals(Status.IN_PROGRESS) || details.getStatus().equals(Status.WAITING)) {
            worker.cancelTask(id, updateDetails(id));
        }
    }

    @Override
    public TaskExecutionDetails await(TaskId id) {
        if (Optional.ofNullable(getExecutionDetails(id)).isPresent()) {
            return Flux.interval(NOW, AWAIT_POLLING_DURATION, Schedulers.elastic())
                .filter(ignore -> tasksResult.get(id) != null)
                .map(ignore -> {
                    Optional.ofNullable(tasksResult.get(id))
                        .ifPresent(Throwing.<Mono<Task.Result>>consumer(Mono::block).orDoNothing());
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
        taskExecutor.shutdown();
        requestTaskExecutor.shutdown();
    }

    private Consumer<TaskExecutionDetailsUpdater> updateDetails(TaskId taskId) {
        return updater -> {
            TaskExecutionDetails newDetails = updater.update(idToExecutionDetails.get(taskId));
            idToExecutionDetails.replace(taskId, newDetails);
        };
    }
}
