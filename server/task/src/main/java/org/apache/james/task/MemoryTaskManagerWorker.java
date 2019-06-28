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

import org.apache.james.util.FunctionalUtils;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MemoryTaskManagerWorker implements TaskManagerWorker {

    private static final boolean INTERRUPT_IF_RUNNING = true;
    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryTaskManagerWorker.class);
    private static final Duration CHECK_CANCELED_PERIOD = Duration.ofMillis(100);
    private static final int FIRST = 1;

    private final ConcurrentHashMap<TaskId, CompletableFuture<Task.Result>> idToFuture = new ConcurrentHashMap<>();

    @Override
    public Mono<Task.Result> executeTask(TaskWithId taskWithId, Listener listener) {
        CompletableFuture<Task.Result> futureResult = CompletableFuture.supplyAsync(() -> runWithMdc(taskWithId, listener));

        idToFuture.put(taskWithId.getId(), futureResult);

        return Mono.fromFuture(futureResult)
            .doOnError(res -> {
                if (!(res instanceof CancellationException)) {
                    listener.failed(res);
                    LOGGER.error("Task was partially performed. Check logs for more details", res);
                }
            })
            .doOnTerminate(() -> idToFuture.remove(taskWithId.getId()));
    }

    private Task.Result runWithMdc(TaskWithId taskWithId, Listener listener) {
        return MDCBuilder.withMdc(
            MDCBuilder.create()
                .addContext(Task.TASK_ID, taskWithId.getId())
                .addContext(Task.TASK_TYPE, taskWithId.getTask().type())
                .addContext(Task.TASK_DETAILS, taskWithId.getTask().details()),
            () -> run(taskWithId, listener));
    }


    private Task.Result run(TaskWithId taskWithId, Listener listener) {
        listener.started();
        try {
            return taskWithId.getTask()
                .run()
                .onComplete(listener::completed)
                .onFailure(() -> {
                    LOGGER.error("Task was partially performed. Check logs for more details. Taskid : " + taskWithId.getId());
                    listener.failed();
                });
        } catch (Exception e) {
            LOGGER.error("Error while running task {}", taskWithId.getId(), e);
            listener.failed(e);
            return Task.Result.PARTIAL;
        }
    }

    @Override
    public void cancelTask(TaskId id, Listener listener) {
        Optional.ofNullable(idToFuture.remove(id))
            .ifPresent(future -> {
                requestCancellation(future);
                waitUntilFutureIsCancelled(future).blockFirst();
                listener.cancelled();
            });
    }

    private void requestCancellation(CompletableFuture<Task.Result> future) {
        future.cancel(INTERRUPT_IF_RUNNING);
    }

    private Flux<Boolean> waitUntilFutureIsCancelled(CompletableFuture<Task.Result> future) {
        return Flux.interval(CHECK_CANCELED_PERIOD)
            .map(ignore -> future.isCancelled())
            .filter(FunctionalUtils.identityPredicate())
            .take(FIRST);
    }
}
