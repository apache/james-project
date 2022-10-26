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
import static org.awaitility.Durations.TWO_MINUTES;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.apache.james.util.MDCBuilder;
import org.apache.james.util.MDCStructuredLogger;
import org.apache.james.util.ReactorUtils;
import org.apache.james.util.concurrent.NamedThreadFactory;
import org.awaitility.Awaitility;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class SerialTaskManagerWorker implements TaskManagerWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(SerialTaskManagerWorker.class);
    public static final boolean MAY_INTERRUPT_IF_RUNNING = true;

    private final Scheduler taskExecutor;
    private final Listener listener;
    private final AtomicReference<Tuple2<TaskId, CompletableFuture>> runningTask;
    private final Set<TaskId> cancelledTasks;
    private final Duration pollingInterval;

    public SerialTaskManagerWorker(Listener listener, Duration pollingInterval) {
        this.pollingInterval = pollingInterval;
        this.taskExecutor = Schedulers.fromExecutor(
            Executors.newSingleThreadExecutor(NamedThreadFactory.withName("task executor")));
        this.listener = listener;
        this.cancelledTasks = Sets.newConcurrentHashSet();
        this.runningTask = new AtomicReference<>();
    }

    @Override
    public Mono<Task.Result> executeTask(TaskWithId taskWithId) {
        if (!cancelledTasks.remove(taskWithId.getId())) {
            Mono<Task.Result> taskMono = runWithMdc(taskWithId, listener).subscribeOn(taskExecutor);
            CompletableFuture<Task.Result> future = taskMono.toFuture();
            runningTask.set(Tuples.of(taskWithId.getId(), future));

            return Mono.using(
                () -> pollAdditionalInformation(taskWithId).subscribe(),
                ignored -> Mono.fromFuture(future)
                    .onErrorResume(exception -> Mono.from(handleExecutionError(taskWithId, listener, exception))
                        .thenReturn(Task.Result.PARTIAL)),
                Disposable::dispose);
        } else {
            return Mono.from(listener.cancelled(taskWithId.getId(), taskWithId.getTask().detailsReactive()))
                .then(Mono.empty());
        }
    }

    private Publisher<Void> handleExecutionError(TaskWithId taskWithId, Listener listener, Throwable exception) {
        if (exception instanceof CancellationException) {
            return Mono.from(listener.cancelled(taskWithId.getId(), taskWithId.getTask().detailsReactive()))
                .then(Mono.fromCallable(() -> cancelledTasks.remove(taskWithId.getId())))
                .then();
        } else {
            return listener.failed(taskWithId.getId(), taskWithId.getTask().detailsReactive(), exception);
        }
    }

    private Flux<TaskExecutionDetails.AdditionalInformation> pollAdditionalInformation(TaskWithId taskWithId) {
        return Mono.from(taskWithId.getTask().detailsReactive())
            .delayElement(pollingInterval, Schedulers.parallel())
            .repeat()
            .handle(publishIfPresent())
            .flatMap(information -> Mono.from(listener.updated(taskWithId.getId(), Mono.just(information))).thenReturn(information), DEFAULT_CONCURRENCY);
    }


    private Mono<Task.Result> runWithMdc(TaskWithId taskWithId, Listener listener) {
        return run(taskWithId, listener)
            .contextWrite(ReactorUtils.context("task",
                MDCBuilder.create()
                    .addToContext(Task.TASK_ID, taskWithId.getId().asString())
                    .addToContext(Task.TASK_TYPE, taskWithId.getTask().type().asString())));
    }

    private Mono<Task.Result> run(TaskWithId taskWithId, Listener listener) {
        return Mono.from(listener.started(taskWithId.getId()))
            .then(runTask(taskWithId, listener))
            .onErrorResume(this::isCausedByInterruptedException, e -> cancelled(taskWithId, listener))
            .onErrorResume(Exception.class, e -> {
                MDCStructuredLogger.forLogger(LOGGER)
                    .field("taskId", taskWithId.getId().asString())
                    .field("taskType", taskWithId.getTask().type().asString())
                    .log(logger -> logger.error("Error while running task {}", taskWithId.getId(), e));

                return Mono.from(listener.failed(taskWithId.getId(), taskWithId.getTask().detailsReactive(), e))
                    .thenReturn(Task.Result.PARTIAL);
            });
    }

    private boolean isCausedByInterruptedException(Throwable e) {
        if (e instanceof InterruptedException) {
            return true;
        }
        return Stream.iterate(e, t -> t.getCause() != null, Throwable::getCause)
            .anyMatch(InterruptedException.class::isInstance);
    }

    private Mono<Task.Result> cancelled(TaskWithId taskWithId, Listener listener) {
        TaskId id = taskWithId.getId();
        return Mono.from(listener.cancelled(id, taskWithId.getTask().detailsReactive()))
            .thenReturn(Task.Result.PARTIAL);
    }

    private Mono<Task.Result> runTask(TaskWithId taskWithId, Listener listener) {
        return Mono.fromCallable(() -> taskWithId.getTask().run())
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
            .doOnNext(result -> result
                .onComplete(any -> Mono.from(listener.completed(taskWithId.getId(), result, taskWithId.getTask().detailsReactive()))
                    .subscribe())
                .onFailure(() -> {
                    MDCStructuredLogger.forLogger(LOGGER)
                        .field("taskId", taskWithId.getId().asString())
                        .field("taskType", taskWithId.getTask().type().asString())
                        .log(logger -> logger.error("Task was partially performed. Check logs for more details. Taskid : {}", taskWithId.getId()));

                    Mono.from(listener.failed(taskWithId.getId(), taskWithId.getTask().detailsReactive()))
                        .subscribe();
                }));
    }

    @Override
    public void cancelTask(TaskId taskId) {
        cancelledTasks.add(taskId);
        Optional.ofNullable(runningTask.get())
            .filter(task -> task.getT1().equals(taskId))
            .ifPresent(task -> task.getT2().cancel(MAY_INTERRUPT_IF_RUNNING));
    }

    @Override
    public Publisher<Void> fail(TaskId taskId, Publisher<Optional<TaskExecutionDetails.AdditionalInformation>> additionalInformationPublisher, String errorMessage, Throwable reason) {
        return listener.failed(taskId, additionalInformationPublisher, Optional.ofNullable(errorMessage), Optional.ofNullable(reason));
    }

    @Override
    public void close() {
        Optional.ofNullable(runningTask.get())
            .ifPresent(task -> {
                if (!task.getT2().isDone() && !task.getT2().isCancelled()) {
                    cancelTask(task.getT1());
                    Awaitility
                        .waitAtMost(TWO_MINUTES)
                        .pollDelay(Duration.ofMillis(500))
                        .until(() -> !cancelledTasks.contains(task.getT1()));
                }

            });
        taskExecutor.dispose();
    }
}
