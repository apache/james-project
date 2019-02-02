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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.consumers.ConsumerChainer;

public class MemoryTaskManagerTest {

    private MemoryTaskManager memoryTaskManager;

    @Rule
    public JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Before
    public void setUp() {
        memoryTaskManager = new MemoryTaskManager();
    }

    @After
    public void tearDown() {
        memoryTaskManager.stop();
    }

    @Test
    public void getStatusShouldReturnUnknownWhenUnknownId() {
        TaskId unknownId = TaskId.generateTaskId();
        assertThatThrownBy(() -> memoryTaskManager.getExecutionDetails(unknownId))
            .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    public void getStatusShouldReturnWaitingWhenNotYetProcessed() {
        CountDownLatch task1Latch = new CountDownLatch(1);

        memoryTaskManager.submit(() -> {
            await(task1Latch);
            return Task.Result.COMPLETED;
        });

        TaskId taskId = memoryTaskManager.submit(() -> Task.Result.COMPLETED);

        assertThat(memoryTaskManager.getExecutionDetails(taskId).getStatus())
            .isEqualTo(TaskManager.Status.WAITING);
    }

    @Test
    public void taskCodeAfterCancelIsNotRun() {
        CountDownLatch task1Latch = new CountDownLatch(1);
        AtomicInteger count = new AtomicInteger(0);

        TaskId id = memoryTaskManager.submit(() -> {
            await(task1Latch);
            count.incrementAndGet();
            return Task.Result.COMPLETED;
        });

        memoryTaskManager.cancel(id);
        task1Latch.countDown();

        assertThat(count.get()).isEqualTo(0);
    }

    @Test
    public void getStatusShouldReturnCancelledWhenCancelled() throws Exception {
        CountDownLatch task1Latch = new CountDownLatch(1);
        CountDownLatch ensureStartedLatch = new CountDownLatch(1);
        CountDownLatch ensureFinishedLatch = new CountDownLatch(1);

        TaskId id = memoryTaskManager.submit(() -> {
            ensureStartedLatch.countDown();
            await(task1Latch);
            return Task.Result.COMPLETED;
        },
            any -> ensureFinishedLatch.countDown());

        ensureStartedLatch.await();
        memoryTaskManager.cancel(id);
        ensureFinishedLatch.await();

        assertThat(memoryTaskManager.getExecutionDetails(id).getStatus())
            .isEqualTo(TaskManager.Status.CANCELLED);
    }

    @Test
    public void cancelShouldBeIdempotent() {
        CountDownLatch task1Latch = new CountDownLatch(1);

        TaskId id = memoryTaskManager.submit(() -> {
            await(task1Latch);
            return Task.Result.COMPLETED;
        });

        memoryTaskManager.cancel(id);
        assertThatCode(() -> memoryTaskManager.cancel(id))
            .doesNotThrowAnyException();
    }

    @Test
    public void getStatusShouldReturnInProgressWhenProcessingIsInProgress() throws Exception {
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);

        TaskId taskId = memoryTaskManager.submit(() -> {
            latch2.countDown();
            await(latch1);
            return Task.Result.COMPLETED;
        });
        latch2.await();

        assertThat(memoryTaskManager.getExecutionDetails(taskId).getStatus())
            .isEqualTo(TaskManager.Status.IN_PROGRESS);
    }

    @Test
    public void getStatusShouldReturnCompletedWhenRunSuccessfully() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        TaskId taskId = memoryTaskManager.submit(
            () -> Task.Result.COMPLETED,
            countDownCallback(latch));

        latch.await();

        assertThat(memoryTaskManager.getExecutionDetails(taskId).getStatus())
            .isEqualTo(TaskManager.Status.COMPLETED);
    }

    @Test
    public void getStatusShouldReturnFailedWhenRunPartially() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        TaskId taskId = memoryTaskManager.submit(
            () -> Task.Result.PARTIAL,
            countDownCallback(latch));

        latch.await();

        assertThat(memoryTaskManager.getExecutionDetails(taskId).getStatus())
            .isEqualTo(TaskManager.Status.FAILED);
    }

    private ConsumerChainer<TaskId> countDownCallback(CountDownLatch latch) {
        return Throwing.consumer(id -> latch.countDown());
    }

    @Test
    public void listShouldReturnTaskSatus() throws Exception {
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        CountDownLatch latch3 = new CountDownLatch(1);

        TaskId failedId = memoryTaskManager.submit(
            () -> Task.Result.PARTIAL);
        TaskId successfulId = memoryTaskManager.submit(
            () -> Task.Result.COMPLETED);
        TaskId inProgressId = memoryTaskManager.submit(
            () -> {
                await(latch1);
                latch2.countDown();
                await(latch3);
                return Task.Result.COMPLETED;
            });
        TaskId waitingId = memoryTaskManager.submit(
            () -> {
                await(latch3);
                latch2.countDown();
                return Task.Result.COMPLETED;
            });

        latch1.countDown();
        latch2.await();

        List<TaskExecutionDetails> list = memoryTaskManager.list();
        softly.assertThat(list).hasSize(4);
        softly.assertThat(entryWithId(list, failedId))
            .isEqualTo(TaskManager.Status.FAILED);
        softly.assertThat(entryWithId(list, waitingId))
            .isEqualTo(TaskManager.Status.WAITING);
        softly.assertThat(entryWithId(list, successfulId))
            .isEqualTo(TaskManager.Status.COMPLETED);
        softly.assertThat(entryWithId(list, inProgressId))
            .isEqualTo(TaskManager.Status.IN_PROGRESS);
    }

    private TaskManager.Status entryWithId(List<TaskExecutionDetails> list, TaskId taskId) {
        return list.stream()
            .filter(e -> e.getTaskId().equals(taskId))
            .findFirst().get()
            .getStatus();
    }

    @Test
    public void listShouldAllowToSeeWaitingTasks() throws Exception {
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        CountDownLatch latch3 = new CountDownLatch(1);

        memoryTaskManager.submit(
            () -> Task.Result.PARTIAL);
        memoryTaskManager.submit(
            () -> Task.Result.COMPLETED);
        memoryTaskManager.submit(
            () -> {
                await(latch1);
                latch2.countDown();
                await(latch3);
                return Task.Result.COMPLETED;
            });
        TaskId waitingId = memoryTaskManager.submit(
            () -> {
                await(latch3);
                latch2.countDown();
                return Task.Result.COMPLETED;
            });

        latch1.countDown();
        latch2.await();

        assertThat(memoryTaskManager.list(TaskManager.Status.WAITING))
            .extracting(TaskExecutionDetails::getTaskId)
            .containsOnly(waitingId);
    }

    @Test
    public void listShouldAllowToSeeInProgressTasks() throws Exception {
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        CountDownLatch latch3 = new CountDownLatch(1);

        memoryTaskManager.submit(
            () -> Task.Result.PARTIAL);
        TaskId successfulId = memoryTaskManager.submit(
            () -> Task.Result.COMPLETED);
        memoryTaskManager.submit(
            () -> {
                await(latch1);
                latch2.countDown();
                await(latch3);
                return Task.Result.COMPLETED;
            });
        memoryTaskManager.submit(
            () -> {
                await(latch3);
                latch2.countDown();
                return Task.Result.COMPLETED;
            });

        latch1.countDown();
        latch2.await();

        assertThat(memoryTaskManager.list(TaskManager.Status.COMPLETED))
            .extracting(TaskExecutionDetails::getTaskId)
            .containsOnly(successfulId);
    }

    @Test
    public void listShouldAllowToSeeFailedTasks() throws Exception {
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        CountDownLatch latch3 = new CountDownLatch(1);

        TaskId failedId = memoryTaskManager.submit(
            () -> Task.Result.PARTIAL);
        memoryTaskManager.submit(
            () -> Task.Result.COMPLETED);
        memoryTaskManager.submit(
            () -> {
                await(latch1);
                latch2.countDown();
                await(latch3);
                return Task.Result.COMPLETED;
            });
        memoryTaskManager.submit(
            () -> {
                await(latch3);
                latch2.countDown();
                return Task.Result.COMPLETED;
            });

        latch1.countDown();
        latch2.await();

        assertThat(memoryTaskManager.list(TaskManager.Status.FAILED))
            .extracting(TaskExecutionDetails::getTaskId)
            .containsOnly(failedId);
    }

    @Test
    public void listShouldAllowToSeeSuccessfulTasks() throws Exception {
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        CountDownLatch latch3 = new CountDownLatch(1);

        memoryTaskManager.submit(
            () -> Task.Result.PARTIAL);
        memoryTaskManager.submit(
            () -> Task.Result.COMPLETED);
        TaskId inProgressId = memoryTaskManager.submit(
            () -> {
                await(latch1);
                latch2.countDown();
                await(latch3);
                return Task.Result.COMPLETED;
            });
        memoryTaskManager.submit(
            () -> {
                await(latch3);
                latch2.countDown();
                return Task.Result.COMPLETED;
            });

        latch1.countDown();
        latch2.await();

        assertThat(memoryTaskManager.list(TaskManager.Status.IN_PROGRESS))
            .extracting(TaskExecutionDetails::getTaskId)
            .containsOnly(inProgressId);
    }

    @Test
    public void listShouldBeEmptyWhenNoTasks() throws Exception {
        assertThat(memoryTaskManager.list()).isEmpty();
    }

    @Test
    public void listCancelledShouldBeEmptyWhenNoTasks() throws Exception {
        assertThat(memoryTaskManager.list(TaskManager.Status.CANCELLED)).isEmpty();
    }

    @Test
    public void awaitShouldNotThrowWhenCompletedTask() throws Exception {
        TaskId taskId = memoryTaskManager.submit(
            () -> Task.Result.COMPLETED);
        memoryTaskManager.await(taskId);
        memoryTaskManager.await(taskId);
    }

    @Test
    public void submittedTaskShouldExecuteSequentially() {
        ConcurrentLinkedQueue<Integer> queue = new ConcurrentLinkedQueue<>();

        TaskId id1 = memoryTaskManager.submit(() -> {
            queue.add(1);
            sleep(500);
            queue.add(2);
            return Task.Result.COMPLETED;
        });
        TaskId id2 = memoryTaskManager.submit(() -> {
            queue.add(3);
            sleep(500);
            queue.add(4);
            return Task.Result.COMPLETED;
        });
        memoryTaskManager.await(id1);
        memoryTaskManager.await(id2);

        assertThat(queue)
            .containsExactly(1, 2, 3, 4);
    }

    @Test
    public void awaitShouldReturnFailedWhenExceptionThrown() {
        TaskId taskId = memoryTaskManager.submit(() -> {
            throw new RuntimeException();
        });

        TaskExecutionDetails executionDetails = memoryTaskManager.await(taskId);

        assertThat(executionDetails.getStatus())
            .isEqualTo(TaskManager.Status.FAILED);
    }

    @Test
    public void getStatusShouldReturnFailedWhenExceptionThrown() {
        TaskId taskId = memoryTaskManager.submit(() -> {
            throw new RuntimeException();
        });

        memoryTaskManager.await(taskId);

        assertThat(memoryTaskManager.getExecutionDetails(taskId).getStatus())
            .isEqualTo(TaskManager.Status.FAILED);
    }

    public void sleep(int durationInMs) {
        try {
            Thread.sleep(durationInMs);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void await(CountDownLatch countDownLatch) {
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}