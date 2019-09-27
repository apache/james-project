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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class SerialTaskManagerWorkerTest {
    private TaskManagerWorker.Listener listener;
    private SerialTaskManagerWorker worker;

    private final Task successfulTask = new CompletedTask();
    private final Task failedTask = new FailedTask();
    private final Task throwingTask = new ThrowingTask();

    @BeforeEach
    void beforeEach() {
        listener = mock(TaskManagerWorker.Listener.class);
        worker = new SerialTaskManagerWorker(listener);
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

        verify(listener, atLeastOnce()).completed(taskWithId.getId(), Task.Result.COMPLETED);
    }

    @Test
    void aFailedTaskShouldCompleteWithFailedStatus() {
        TaskWithId taskWithId = new TaskWithId(TaskId.generateTaskId(), failedTask);

        Mono<Task.Result> result = worker.executeTask(taskWithId);

        assertThat(result.block()).isEqualTo(Task.Result.PARTIAL);
        verify(listener, atLeastOnce()).failed(taskWithId.getId());
    }

    @Test
    void aThrowingTaskShouldCompleteWithFailedStatus() {
        TaskWithId taskWithId = new TaskWithId(TaskId.generateTaskId(), throwingTask);

        Mono<Task.Result> result = worker.executeTask(taskWithId);

        assertThat(result.block()).isEqualTo(Task.Result.PARTIAL);
        verify(listener, atLeastOnce()).failed(eq(taskWithId.getId()), any(RuntimeException.class));
    }

    @Test
    void theWorkerShouldReportThatATaskIsInProgress() throws InterruptedException {
        TaskId id = TaskId.generateTaskId();
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch taskLaunched = new CountDownLatch(1);

        Task inProgressTask = () -> {
            taskLaunched.countDown();
            await(latch);
            return Task.Result.COMPLETED;
        };

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

        Task inProgressTask = () -> {
            await(latch);
            counter.incrementAndGet();
            return Task.Result.COMPLETED;
        };

        TaskWithId taskWithId = new TaskWithId(id, inProgressTask);

        Mono<Task.Result> resultMono = worker.executeTask(taskWithId).cache();
        resultMono.subscribe();

        Awaitility.waitAtMost(org.awaitility.Duration.TEN_SECONDS)
            .untilAsserted(() -> verify(listener, atLeastOnce()).started(id));

        worker.cancelTask(id);

        resultMono.block(Duration.ofSeconds(10));

        verify(listener, atLeastOnce()).cancelled(id);
        verifyNoMoreInteractions(listener);
    }


    private void await(CountDownLatch countDownLatch) throws InterruptedException {
        countDownLatch.await();
    }
}
