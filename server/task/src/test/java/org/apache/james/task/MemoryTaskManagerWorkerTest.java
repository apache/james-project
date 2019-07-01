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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class MemoryTaskManagerWorkerTest {

    private final MemoryTaskManagerWorker worker = new MemoryTaskManagerWorker();

    private final Task successfulTask = () -> Task.Result.COMPLETED;
    private final Task failedTask = () -> Task.Result.PARTIAL;
    private final Task throwingTask = () -> {
        throw new RuntimeException("Throwing Task");
    };

    @Test
    void aSuccessfullTaskShouldCompleteSuccessfully() {
        TaskWithId taskWithId = new TaskWithId(TaskId.generateTaskId(), this.successfulTask);

        TaskManagerWorker.Listener listener = mock(TaskManagerWorker.Listener.class);

        Mono<Task.Result> result = worker.executeTask(taskWithId, listener).cache();

        assertThat(result.block()).isEqualTo(Task.Result.COMPLETED);

        verify(listener, atLeastOnce()).completed();
    }

    @Test
    void aFailedTaskShouldCompleteWithFailedStatus() {
        TaskWithId taskWithId = new TaskWithId(TaskId.generateTaskId(), failedTask);
        TaskManagerWorker.Listener listener = mock(TaskManagerWorker.Listener.class);

        Mono<Task.Result> result = worker.executeTask(taskWithId, listener).cache();

        assertThat(result.block()).isEqualTo(Task.Result.PARTIAL);
        verify(listener, atLeastOnce()).failed();
    }

    @Test
    void aThrowingTaskShouldCompleteWithFailedStatus() {
        TaskWithId taskWithId = new TaskWithId(TaskId.generateTaskId(), throwingTask);
        TaskManagerWorker.Listener listener = mock(TaskManagerWorker.Listener.class);

        Mono<Task.Result> result = worker.executeTask(taskWithId, listener).cache();

        assertThat(result.block()).isEqualTo(Task.Result.PARTIAL);
        verify(listener, atLeastOnce()).failed(any(RuntimeException.class));
    }

    @Test
    void theWorkerShouldReportThatATaskIsInProgress() {
        TaskId id = TaskId.generateTaskId();
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch taskLaunched = new CountDownLatch(1);

        Task inProgressTask = () -> {
            taskLaunched.countDown();
            await(latch);
            return Task.Result.COMPLETED;
        };

        TaskWithId taskWithId = new TaskWithId(id, inProgressTask);

        TaskManagerWorker.Listener listener = mock(TaskManagerWorker.Listener.class);

        worker.executeTask(taskWithId, listener).cache();

        await(taskLaunched);
        verify(listener, atLeastOnce()).started();
        verifyNoMoreInteractions(listener);
        latch.countDown();
    }

    @Test
    void theWorkerShouldCancelAnInProgressTask() {
        TaskId id = TaskId.generateTaskId();
        AtomicInteger counter = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        Task inProgressTask = () -> {
            await(latch);
            counter.incrementAndGet();
            return Task.Result.COMPLETED;
        };

        TaskWithId taskWithId = new TaskWithId(id, inProgressTask);

        TaskManagerWorker.Listener listener = mock(TaskManagerWorker.Listener.class);

        worker.executeTask(taskWithId, listener).cache();

        worker.cancelTask(id, listener);

        verify(listener, atLeastOnce()).started();
        verify(listener, atLeastOnce()).cancelled();
        verifyNoMoreInteractions(listener);
    }


    private void await(CountDownLatch countDownLatch) {
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
