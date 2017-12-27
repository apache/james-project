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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import javax.annotation.PreDestroy;

import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class MemoryTaskManager implements TaskManager {
    private static final boolean INTERRUPT_IF_RUNNING = true;
    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryTaskManager.class);

    private final ConcurrentHashMap<TaskId, TaskExecutionDetails> idToExecutionDetails;
    private final ConcurrentHashMap<TaskId, Future> idToFuture;
    private final ExecutorService executor;

    public MemoryTaskManager() {
        idToExecutionDetails = new ConcurrentHashMap<>();
        idToFuture = new ConcurrentHashMap<>();
        executor = Executors.newSingleThreadExecutor();
    }

    @Override
    public TaskId submit(Task task) {
        return submit(task, id -> {});
    }

    @VisibleForTesting
    TaskId submit(Task task, Consumer<TaskId> callback) {
        TaskId taskId = TaskId.generateTaskId();
        TaskExecutionDetails executionDetails = TaskExecutionDetails.from(task, taskId);

        idToExecutionDetails.put(taskId, executionDetails);
        idToFuture.put(taskId,
            executor.submit(() -> runWithMdc(executionDetails, task, callback)));
        return taskId;
    }

    private void runWithMdc(TaskExecutionDetails executionDetails, Task task, Consumer<TaskId> callback) {
        MDCBuilder.withMdc(
            MDCBuilder.create()
                .addContext(Task.TASK_ID, executionDetails.getTaskId())
                .addContext(Task.TASK_TYPE, executionDetails.getType())
                .addContext(Task.TASK_DETAILS, executionDetails.getAdditionalInformation()),
            () -> run(executionDetails, task, callback));
    }

    private void run(TaskExecutionDetails executionDetails, Task task, Consumer<TaskId> callback) {
        TaskExecutionDetails started = executionDetails.start();
        idToExecutionDetails.put(started.getTaskId(), started);
        try {
            task.run()
                .onComplete(() -> success(started))
                .onFailure(() -> failed(started,
                    logger -> logger.info("Task was partially performed. Check logs for more details")));
        } catch (Exception e) {
            failed(started,
                logger -> logger.error("Error while running task", executionDetails, e));
        } finally {
            idToFuture.remove(executionDetails.getTaskId());
            callback.accept(executionDetails.getTaskId());
        }
    }

    private void success(TaskExecutionDetails started) {
        if (!wasCancelled(started.getTaskId())) {
            idToExecutionDetails.put(started.getTaskId(), started.completed());
            LOGGER.info("Task success");
        }
    }

    private void failed(TaskExecutionDetails started, Consumer<Logger> logOperation) {
        if (!wasCancelled(started.getTaskId())) {
            idToExecutionDetails.put(started.getTaskId(), started.failed());
            logOperation.accept(LOGGER);
        }
    }

    private boolean wasCancelled(TaskId taskId) {
        return idToExecutionDetails.get(taskId).getStatus() == Status.CANCELLED;
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
        return idToExecutionDetails.values()
            .stream()
            .filter(details -> details.getStatus().equals(status))
            .collect(Guavate.toImmutableList());
    }

    @Override
    public void cancel(TaskId id) {
        Optional.ofNullable(idToFuture.get(id))
            .ifPresent(future -> {
                TaskExecutionDetails executionDetails = idToExecutionDetails.get(id);
                idToExecutionDetails.put(id, executionDetails.cancel());
                future.cancel(INTERRUPT_IF_RUNNING);
                idToFuture.remove(id);
            });
    }

    @Override
    public TaskExecutionDetails await(TaskId id) {
        Optional.ofNullable(idToFuture.get(id))
            .ifPresent(Throwing.consumer(Future::get));
        return getExecutionDetails(id);
    }

    @PreDestroy
    public void stop() {
        executor.shutdownNow();
    }
}
