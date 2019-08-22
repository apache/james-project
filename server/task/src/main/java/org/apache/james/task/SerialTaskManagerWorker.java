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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.apache.james.util.MDCBuilder;
import org.apache.james.util.concurrent.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class SerialTaskManagerWorker implements TaskManagerWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(SerialTaskManagerWorker.class);
    private final ExecutorService taskExecutor;
    private final Listener listener;
    private final AtomicReference<Tuple2<TaskId, Future<?>>> runningTask;
    private final Semaphore semaphore;
    private final Set<TaskId> cancelledTasks;

    public SerialTaskManagerWorker(Listener listener) {
        this.taskExecutor = Executors.newSingleThreadExecutor(NamedThreadFactory.withName("task executor"));
        this.listener = listener;
        this.cancelledTasks = Sets.newConcurrentHashSet();
        this.runningTask = new AtomicReference<>();
        this.semaphore = new Semaphore(1);
    }

    @Override
    public Mono<Task.Result> executeTask(TaskWithId taskWithId) {
        return Mono
            .using(
                acquireSemaphore(taskWithId, listener),
                executeWithSemaphore(taskWithId, listener),
                Semaphore::release);

    }

    private Callable<Semaphore> acquireSemaphore(TaskWithId taskWithId, Listener listener) {
        return () -> {
                try {
                    semaphore.acquire();
                    return semaphore;
                } catch (InterruptedException e) {
                    listener.cancelled(taskWithId.getId());
                    throw e;
                }
            };
    }

    private Function<Semaphore, Mono<Task.Result>> executeWithSemaphore(TaskWithId taskWithId, Listener listener) {
        return semaphore -> {
            if (!cancelledTasks.remove(taskWithId.getId())) {
                CompletableFuture<Task.Result> future = CompletableFuture.supplyAsync(() -> runWithMdc(taskWithId, listener), taskExecutor);
                runningTask.set(Tuples.of(taskWithId.getId(), future));

                return Mono.fromFuture(future)
                        .doOnError(exception -> {
                            if (exception instanceof CancellationException) {
                                listener.cancelled(taskWithId.getId());
                            } else {
                                listener.failed(taskWithId.getId(), exception);
                            }
                        })
                        .onErrorReturn(Task.Result.PARTIAL);
            } else {
                listener.cancelled(taskWithId.getId());
                return Mono.empty();
            }
        };
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
        listener.started(taskWithId.getId());
        try {
            return taskWithId.getTask()
                .run()
                .onComplete(result -> listener.completed(taskWithId.getId(), result))
                .onFailure(() -> {
                    LOGGER.error("Task was partially performed. Check logs for more details. Taskid : " + taskWithId.getId());
                    listener.failed(taskWithId.getId());
                });
        } catch (InterruptedException e) {
            listener.cancelled(taskWithId.getId());
            return Task.Result.PARTIAL;
        } catch (Exception e) {
            LOGGER.error("Error while running task {}", taskWithId.getId(), e);
            listener.failed(taskWithId.getId(), e);
            return Task.Result.PARTIAL;
        }
    }

    @Override
    public void cancelTask(TaskId taskId) {
        cancelledTasks.add(taskId);
        Optional.ofNullable(runningTask.get())
            .filter(task -> task.getT1().equals(taskId))
            .ifPresent(task -> task.getT2().cancel(true));
    }

    @Override
    public void fail(TaskId taskId, Throwable reason) {
        listener.failed(taskId, reason);
    }

    @Override
    public void close() throws IOException {
        taskExecutor.shutdownNow();
    }
}
