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
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.james.util.FunctionalUtils;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MemoryTaskManagerWorker implements TaskManagerWorker {
    private static final boolean INTERRUPT_IF_RUNNING = true;
    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryTaskManagerWorker.class);
    public static final Duration CHECK_CANCELED_PERIOD = Duration.ofMillis(100);
    public static final int FIRST = 1;
    private final ConcurrentHashMap<TaskId, CompletableFuture<Task.Result>> idToFuture = new ConcurrentHashMap<>();

    @Override
    public Mono<Task.Result> executeTask(TaskWithId taskWithId, Consumer<TaskExecutionDetailsUpdater> updateDetails) {
        CompletableFuture<Task.Result> futureResult = CompletableFuture.supplyAsync(() -> runWithMdc(taskWithId, updateDetails));

        idToFuture.put(taskWithId.getId(), futureResult);

        Mono<Task.Result> result = Mono.<Task.Result>fromFuture(futureResult)
            .doOnError(res -> {
                if (!(res instanceof CancellationException)) {
                    failed(updateDetails,
                        (logger, details) -> logger.error("Task was partially performed. Check logs for more details"));
                }
            })
            .doOnTerminate(() -> idToFuture.remove(taskWithId.getId()));

        return result;
    }

    private Task.Result runWithMdc(TaskWithId taskWithId, Consumer<TaskExecutionDetailsUpdater> updateDetails) {
        return MDCBuilder.withMdc(
            MDCBuilder.create()
                .addContext(Task.TASK_ID, taskWithId.getId())
                .addContext(Task.TASK_TYPE, taskWithId.getTask().type())
                .addContext(Task.TASK_DETAILS, taskWithId.getTask().details()),
            () -> run(taskWithId, updateDetails));
    }


    private Task.Result run(TaskWithId taskWithId, Consumer<TaskExecutionDetailsUpdater> updateDetails) {
        updateDetails.accept(TaskExecutionDetails::start);
        try {
            return taskWithId.getTask()
                .run()
                .onComplete(() -> success(updateDetails))
                .onFailure(() -> failed(updateDetails, (logger, details) -> logger.error("Task was partially performed. Check logs for more details" + details.getTaskId())));
        } catch (Exception e) {
            failed(updateDetails,
                (logger, executionDetails) -> logger.error("Error while running task", executionDetails, e));
            return Task.Result.PARTIAL;
        }
    }

    @Override
    public void cancelTask(TaskId id, Consumer<TaskExecutionDetailsUpdater> updateDetails) {
        Optional.ofNullable(idToFuture.remove(id))
            .ifPresent(future -> {
                requestCancellation(updateDetails, future);
                waitUntilFutureIsCancelled(future)
                    .subscribe(cancellationSuccessful -> effectivelyCancelled(updateDetails));
            });
    }

    private void requestCancellation(Consumer<TaskExecutionDetailsUpdater> updateDetails, CompletableFuture<Task.Result> future) {
        updateDetails.accept(details -> {
            if (details.getStatus().equals(TaskManager.Status.WAITING) || details.getStatus().equals(TaskManager.Status.IN_PROGRESS)) {
                return details.cancelRequested();
            }
            return details;
        });
        future.cancel(INTERRUPT_IF_RUNNING);
    }

    private Flux<Boolean> waitUntilFutureIsCancelled(CompletableFuture<Task.Result> future) {
        return Flux.interval(CHECK_CANCELED_PERIOD)
            .map(ignore -> future.isCancelled())
            .filter(FunctionalUtils.identityPredicate())
            .take(FIRST);
    }

    private void effectivelyCancelled(Consumer<TaskExecutionDetailsUpdater> updateDetails) {
        updateDetails.accept(details -> {
            if (canBeCancelledEffectively(details)) {
                return details.cancelEffectively();
            }
            return details;
        });
    }

    private boolean canBeCancelledEffectively(TaskExecutionDetails details) {
        return !details.getStatus().isFinished();
    }

    private void success(Consumer<TaskExecutionDetailsUpdater> updateDetails) {
        updateDetails.accept(currentDetails -> {
            if (!wasCancelled(currentDetails)) {
                LOGGER.info("Task success");
                return currentDetails.completed();
            }
            return currentDetails;
        });
    }

    private void failed(Consumer<TaskExecutionDetailsUpdater> updateDetails, BiConsumer<Logger, TaskExecutionDetails> logOperation) {
        updateDetails.accept(currentDetails -> {
            if (!wasCancelled(currentDetails)) {
                logOperation.accept(LOGGER, currentDetails);
                return currentDetails.failed();
            }
            return currentDetails;
        });
    }

    private boolean wasCancelled(TaskExecutionDetails details) {
        return details.getStatus() == TaskManager.Status.CANCELLED;
    }

}
