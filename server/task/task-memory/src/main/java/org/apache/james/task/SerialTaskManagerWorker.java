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

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;
import static org.apache.james.util.ReactorUtils.publishIfPresent;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import org.apache.james.util.MDCBuilder;
import org.apache.james.util.concurrent.NamedThreadFactory;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

public class SerialTaskManagerWorker implements TaskManagerWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(SerialTaskManagerWorker.class);
    public static final boolean MAY_INTERRUPT_IF_RUNNING = true;

    private final Scheduler taskExecutor;
    private final Scheduler asyncTaskExecutor;
    private final Listener listener;
    private final Map<TaskId, CompletableFuture<Task.Result>> runningTasks;
    private final Set<TaskId> cancelledTasks;
    private final Duration pollingInterval;

    public SerialTaskManagerWorker(Listener listener, Duration pollingInterval) {
        this.pollingInterval = pollingInterval;
        this.taskExecutor = Schedulers.fromExecutor(
            Executors.newSingleThreadExecutor(NamedThreadFactory.withName("task executor")));
        this.asyncTaskExecutor = Schedulers.fromExecutor(
            Executors.newCachedThreadPool(NamedThreadFactory.withName("async task executor")));
        this.listener = listener;
        this.cancelledTasks = Sets.newConcurrentHashSet();
        this.runningTasks = Maps.newConcurrentMap();
    }

    @Override
    public Mono<Task.Result> executeTask(TaskWithId taskWithId) {
        if (!cancelledTasks.remove(taskWithId.getId())) {
            Mono<Task.Result> taskMono = Mono.fromCallable(() -> runWithMdc(taskWithId, listener)).subscribeOn(schedulerForTask(taskWithId));
            CompletableFuture<Task.Result> future = taskMono.toFuture();
            runningTasks.put(taskWithId.getId(), future);

            Mono<Task.Result> pollingMono = Mono.using(
                () -> pollAdditionalInformation(taskWithId).subscribe(),
                ignored -> Mono.fromFuture(future)
                    .onErrorResume(exception -> Mono.from(handleExecutionError(taskWithId, listener, exception))
                            .thenReturn(Task.Result.PARTIAL)),
                Disposable::dispose)
                .doOnTerminate(() -> runningTasks.remove(taskWithId.getId()));

            if (taskWithId.getTask() instanceof AsyncSafeTask) {
                pollingMono.subscribe();
                return Mono.empty();
            } else {
                return pollingMono;
            }
        } else {
            return Mono.from(listener.cancelled(taskWithId.getId(), taskWithId.getTask().details()))
                .doOnTerminate(() -> runningTasks.remove(taskWithId.getId()))
                .then(Mono.empty());
        }
    }

    private Scheduler schedulerForTask(TaskWithId taskWithId) {
        if (taskWithId.getTask() instanceof AsyncSafeTask) {
            return asyncTaskExecutor;
        } else {
            return taskExecutor;
        }
    }

    private Publisher<Void> handleExecutionError(TaskWithId taskWithId, Listener listener, Throwable exception) {
        if (exception instanceof CancellationException) {
            return Mono.from(listener.cancelled(taskWithId.getId(), taskWithId.getTask().details()))
                .then(Mono.fromCallable(() -> cancelledTasks.remove(taskWithId.getId())))
                .then();
        } else {
            return listener.failed(taskWithId.getId(), taskWithId.getTask().details(), exception);
        }
    }

    private Flux<TaskExecutionDetails.AdditionalInformation> pollAdditionalInformation(TaskWithId taskWithId) {
        return Mono.fromCallable(() -> taskWithId.getTask().details())
            .delayElement(pollingInterval, Schedulers.elastic())
            .repeat()
            .handle(publishIfPresent())
            .flatMap(information -> Mono.from(listener.updated(taskWithId.getId(), information)).thenReturn(information), DEFAULT_CONCURRENCY);
    }


    private Task.Result runWithMdc(TaskWithId taskWithId, Listener listener) {
        return MDCBuilder.withMdc(
            MDCBuilder.create()
                .addToContext(Task.TASK_ID, taskWithId.getId().asString())
                .addToContext(Task.TASK_TYPE, taskWithId.getTask().type().asString())
                .addToContext(Task.TASK_DETAILS, taskWithId.getTask().details().toString()),
            () -> run(taskWithId, listener).block());
    }

    private Mono<Task.Result> run(TaskWithId taskWithId, Listener listener) {
        return Mono.from(listener.started(taskWithId.getId()))
            .then(runTask(taskWithId, listener))
            .onErrorResume(this::isCausedByInterruptedException, e -> cancelled(taskWithId, listener))
            .onErrorResume(Exception.class, e -> {
                LOGGER.error("Error while running task {}", taskWithId.getId(), e);
                return Mono.from(listener.failed(taskWithId.getId(), taskWithId.getTask().details(), e)).thenReturn(Task.Result.PARTIAL);
            });
    }

    private boolean isCausedByInterruptedException(Throwable e) {
        if (e instanceof InterruptedException) {
            return true;
        }
        return Stream.iterate(e, t -> t.getCause() != null, Throwable::getCause)
            .anyMatch(t -> t instanceof InterruptedException);
    }

    private Mono<Task.Result> cancelled(TaskWithId taskWithId, Listener listener) {
        TaskId id = taskWithId.getId();
        Optional<TaskExecutionDetails.AdditionalInformation> details = taskWithId.getTask().details();

        return Mono.from(listener.cancelled(id, details))
            .thenReturn(Task.Result.PARTIAL);
    }

    private Mono<Task.Result> runTask(TaskWithId taskWithId, Listener listener) {
        return Mono.from(taskWithId.getTask().runAsync())
            .doOnNext(result -> result
                .onComplete(any -> Mono.from(listener.completed(taskWithId.getId(), result, taskWithId.getTask().details())).block())
                .onFailure(() -> {
                    LOGGER.error("Task was partially performed. Check logs for more details. Taskid : " + taskWithId.getId());
                    Mono.from(listener.failed(taskWithId.getId(), taskWithId.getTask().details())).block();
                }));
    }

    @Override
    public void cancelTask(TaskId taskId) {
        cancelledTasks.add(taskId);
        Optional.ofNullable(runningTasks.get(taskId))
            .ifPresent(task -> task.cancel(MAY_INTERRUPT_IF_RUNNING));
    }

    @Override
    public Publisher<Void> fail(TaskId taskId, Optional<TaskExecutionDetails.AdditionalInformation> additionalInformation, String errorMessage, Throwable reason) {
        return listener.failed(taskId, additionalInformation, errorMessage, reason);
    }

    @Override
    public void close() {
        taskExecutor.dispose();
        asyncTaskExecutor.dispose();
    }
}
