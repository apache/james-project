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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class SerialTaskManagerWorkerTest {
    private  static final Duration UPDATE_INFORMATION_POLLING_DURATION = Duration.ofSeconds(1);

    private TaskManagerWorker.Listener listener;
    private SerialTaskManagerWorker worker;

    private final Task successfulTask = new CompletedTask();
    private final Task failedTask = new FailedTask();
    private final Task throwingTask = new ThrowingTask();

    @BeforeEach
    void beforeEach() {
        listener = mock(TaskManagerWorker.Listener.class);
        when(listener.started(any())).thenReturn(Mono.empty());
        when(listener.cancelled(any(), any())).thenReturn(Mono.empty());
        when(listener.completed(any(), any(), any())).thenReturn(Mono.empty());
        when(listener.updated(any(), any())).thenReturn(Mono.empty());
        when(listener.failed(any(), any())).thenReturn(Mono.empty());
        when(listener.failed(any(), any(), any())).thenReturn(Mono.empty());
        when(listener.failed(any(), any(), any(), any())).thenReturn(Mono.empty());
        worker = new SerialTaskManagerWorker(listener, UPDATE_INFORMATION_POLLING_DURATION);
    }

    @AfterEach
    void tearDown() throws IOException {
        worker.close();
    }

    @Test
    void aSuccessfullTaskShouldCompleteSuccessfully() {
        TaskWithId taskWithId = new TaskWithId(TaskId.generateTaskId(), this.successfulTask);

        Mono<Task.Result> result = worker.executeTask(taskWithId);

        assertThat(result.block()).isEqualTo(Task.Result.COMPLETED);

        verify(listener, atLeastOnce()).completed(taskWithId.getId(), Task.Result.COMPLETED, Optional.empty());
    }

    @Test
    void aRunningTaskShouldProvideInformationUpdatesDuringExecution() throws InterruptedException {
        TaskWithId taskWithId = new TaskWithId(TaskId.generateTaskId(), new MemoryReferenceWithCounterTask((counter) ->
            Mono.fromCallable(counter::incrementAndGet)
                .delayElement(Duration.ofSeconds(2))
                .repeat(10)
                .then(Mono.just(Task.Result.COMPLETED))
                .block()));

        worker.executeTask(taskWithId).subscribe();

        TimeUnit.SECONDS.sleep(2);

        verify(listener, atLeastOnce()).updated(eq(taskWithId.getId()), notNull());
    }

    @Test
    void aRunningTaskShouldHaveAFiniteNumberOfInformation() throws InterruptedException {
        TaskWithId taskWithId = new TaskWithId(TaskId.generateTaskId(), new MemoryReferenceWithCounterTask((counter) ->
            Mono.fromCallable(counter::incrementAndGet)
                .delayElement(Duration.ofSeconds(1))
                .repeat(3)
                .then(Mono.just(Task.Result.COMPLETED))
                .block()));

        worker.executeTask(taskWithId).block();

        verify(listener, atMost(4)).updated(eq(taskWithId.getId()), notNull());
    }

    @Test
    void aRunningTaskShouldEmitAtMostOneInformationPerSecond() {
        TaskWithId taskWithId = new TaskWithId(TaskId.generateTaskId(), new MemoryReferenceWithCounterTask((counter) ->
            Mono.fromCallable(counter::incrementAndGet)
                .delayElement(Duration.ofMillis(10))
                .repeat(200)
                .then(Mono.just(Task.Result.COMPLETED))
                .block()));

        worker.executeTask(taskWithId).block();

        verify(listener, atMost(3)).updated(eq(taskWithId.getId()), notNull());
    }

    @Test
    void aFailedTaskShouldCompleteWithFailedStatus() {
        TaskWithId taskWithId = new TaskWithId(TaskId.generateTaskId(), failedTask);

        Mono<Task.Result> result = worker.executeTask(taskWithId);

        assertThat(result.block()).isEqualTo(Task.Result.PARTIAL);
        verify(listener, atLeastOnce()).failed(taskWithId.getId(), Optional.empty());
    }

    @Test
    void aThrowingTaskShouldCompleteWithFailedStatus() {
        TaskWithId taskWithId = new TaskWithId(TaskId.generateTaskId(), throwingTask);

        Mono<Task.Result> result = worker.executeTask(taskWithId);

        assertThat(result.block()).isEqualTo(Task.Result.PARTIAL);
        verify(listener, atLeastOnce()).failed(eq(taskWithId.getId()), eq(Optional.empty()), any(RuntimeException.class));
    }

    @Test
    void theWorkerShouldReportThatATaskIsInProgress() throws InterruptedException {
        TaskId id = TaskId.generateTaskId();
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch taskLaunched = new CountDownLatch(1);

        Task inProgressTask = new MemoryReferenceTask(() -> {
            taskLaunched.countDown();
            await(latch);
            return Task.Result.COMPLETED;
        });

        TaskWithId taskWithId = new TaskWithId(id, inProgressTask);

        worker.executeTask(taskWithId).subscribe();

        await(taskLaunched);
        verify(listener, atLeastOnce()).started(id);
        verifyNoMoreInteractions(listener);
        latch.countDown();
    }

    @Test
    void theWorkerShouldCancelAnInProgressTask() throws InterruptedException {
        TaskId id = TaskId.generateTaskId();
        AtomicInteger counter = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        Task inProgressTask = new MemoryReferenceTask(() -> {
            await(latch);
            counter.incrementAndGet();
            return Task.Result.COMPLETED;
        });

        TaskWithId taskWithId = new TaskWithId(id, inProgressTask);

        Mono<Task.Result> resultMono = worker.executeTask(taskWithId).cache();
        resultMono.subscribe();

        Awaitility.waitAtMost(org.awaitility.Duration.TEN_SECONDS)
            .untilAsserted(() -> verify(listener, atLeastOnce()).started(id));

        worker.cancelTask(id);

        resultMono.block(Duration.ofSeconds(10));

        verify(listener, atLeastOnce()).cancelled(id, Optional.empty());
        verifyNoMoreInteractions(listener);
    }


    private void await(CountDownLatch countDownLatch) throws InterruptedException {
        countDownLatch.await();
    }
}
