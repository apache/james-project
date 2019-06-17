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
import static org.awaitility.Duration.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Duration.ONE_SECOND;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.core.api.SoftAssertions;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemoryTaskManagerTest {

    private MemoryTaskManager memoryTaskManager;

    @BeforeEach
    void setUp() {
        memoryTaskManager = new MemoryTaskManager();
    }

    @AfterEach
    void tearDown() {
        memoryTaskManager.stop();
    }

    private final Duration slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS;
    private final ConditionFactory calmlyAwait = Awaitility.with()
        .pollInterval(slowPacedPollInterval)
        .and()
        .with()
        .pollDelay(slowPacedPollInterval)
        .await();
    private final ConditionFactory awaitAtMostOneSecond = calmlyAwait.atMost(ONE_SECOND);

    @Test
    void getStatusShouldReturnUnknownWhenUnknownId() {
        TaskId unknownId = TaskId.generateTaskId();
        assertThatThrownBy(() -> memoryTaskManager.getExecutionDetails(unknownId))
            .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void getStatusShouldReturnWaitingWhenNotYetProcessed() {
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
    void taskCodeAfterCancelIsNotRun() {
        CountDownLatch waitForTaskToBeLaunched = new CountDownLatch(1);
        CountDownLatch task1Latch = new CountDownLatch(1);
        AtomicInteger count = new AtomicInteger(0);

        TaskId id = memoryTaskManager.submit(() -> {
            waitForTaskToBeLaunched.countDown();
            await(task1Latch);
            count.incrementAndGet();
            return Task.Result.COMPLETED;
        });

        await(waitForTaskToBeLaunched);
        memoryTaskManager.cancel(id);
        task1Latch.countDown();

        assertThat(count.get()).isEqualTo(0);
    }

    @Test
    void completedTaskShouldNotBeCancelled() {
        TaskId id = memoryTaskManager.submit(() -> Task.Result.COMPLETED);

        awaitUntilTaskHasStatus(id, TaskManager.Status.COMPLETED);
        memoryTaskManager.cancel(id);

       try {
           awaitUntilTaskHasStatus(id, TaskManager.Status.CANCELLED);
       } catch (Exception e) {
           //Should timeout
       }
        assertThat(memoryTaskManager.getExecutionDetails(id).getStatus()).isEqualTo(TaskManager.Status.COMPLETED);
    }

    @Test
    void failedTaskShouldNotBeCancelled() {
        TaskId id = memoryTaskManager.submit(() -> Task.Result.PARTIAL);

        awaitUntilTaskHasStatus(id, TaskManager.Status.FAILED);
        memoryTaskManager.cancel(id);

       try {
           awaitUntilTaskHasStatus(id, TaskManager.Status.CANCELLED);
       } catch (Exception e) {
           //Should timeout
       }
        assertThat(memoryTaskManager.getExecutionDetails(id).getStatus()).isEqualTo(TaskManager.Status.FAILED);
    }

    @Test
    void getStatusShouldBeCancelledWhenCancelled() {
        TaskId id = memoryTaskManager.submit(() -> {
            sleep(500);
            return Task.Result.COMPLETED;
        });

        awaitUntilTaskHasStatus(id, TaskManager.Status.IN_PROGRESS);
        memoryTaskManager.cancel(id);

        assertThat(memoryTaskManager.getExecutionDetails(id).getStatus())
            .isIn(TaskManager.Status.CANCELLED, TaskManager.Status.CANCEL_REQUESTED);

        awaitUntilTaskHasStatus(id, TaskManager.Status.CANCELLED);
        assertThat(memoryTaskManager.getExecutionDetails(id).getStatus())
            .isEqualTo(TaskManager.Status.CANCELLED);

    }

    @Test
    void aWaitingTaskShouldBeCancelled() {
        TaskId id = memoryTaskManager.submit(() -> {
            sleep(500);
            return Task.Result.COMPLETED;
        });

        TaskId idTaskToCancel = memoryTaskManager.submit(() -> Task.Result.COMPLETED);

        memoryTaskManager.cancel(idTaskToCancel);

        awaitUntilTaskHasStatus(id, TaskManager.Status.IN_PROGRESS);


        assertThat(memoryTaskManager.getExecutionDetails(idTaskToCancel).getStatus())
            .isIn(TaskManager.Status.CANCELLED, TaskManager.Status.CANCEL_REQUESTED);

        awaitUntilTaskHasStatus(idTaskToCancel, TaskManager.Status.CANCELLED);
        assertThat(memoryTaskManager.getExecutionDetails(idTaskToCancel).getStatus())
            .isEqualTo(TaskManager.Status.CANCELLED);

    }

    @Test
    void cancelShouldBeIdempotent() {
        CountDownLatch task1Latch = new CountDownLatch(1);

        TaskId id = memoryTaskManager.submit(() -> {
            await(task1Latch);
            return Task.Result.COMPLETED;
        });
        awaitUntilTaskHasStatus(id, TaskManager.Status.IN_PROGRESS);
        memoryTaskManager.cancel(id);
        assertThatCode(() -> memoryTaskManager.cancel(id))
            .doesNotThrowAnyException();
    }

    @Test
    void getStatusShouldReturnInProgressWhenProcessingIsInProgress() {
        CountDownLatch latch1 = new CountDownLatch(1);

        TaskId taskId = memoryTaskManager.submit(() -> {
            await(latch1);
            return Task.Result.COMPLETED;
        });
        awaitUntilTaskHasStatus(taskId, TaskManager.Status.IN_PROGRESS);
        assertThat(memoryTaskManager.getExecutionDetails(taskId).getStatus())
            .isEqualTo(TaskManager.Status.IN_PROGRESS);
        latch1.countDown();
    }

    @Test
    void getStatusShouldReturnCompletedWhenRunSuccessfully() {

        TaskId taskId = memoryTaskManager.submit(
            () -> Task.Result.COMPLETED);

        awaitUntilTaskHasStatus(taskId, TaskManager.Status.COMPLETED);
        assertThat(memoryTaskManager.getExecutionDetails(taskId).getStatus())
            .isEqualTo(TaskManager.Status.COMPLETED);
    }

    @Test
    void getStatusShouldReturnFailedWhenRunPartially() {

        TaskId taskId = memoryTaskManager.submit(
            () -> Task.Result.PARTIAL);

        awaitUntilTaskHasStatus(taskId, TaskManager.Status.FAILED);

        assertThat(memoryTaskManager.getExecutionDetails(taskId).getStatus())
            .isEqualTo(TaskManager.Status.FAILED);
    }

    @Test
    void listShouldReturnTaskStatus() throws Exception {
        SoftAssertions softly = new SoftAssertions();

        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);

        TaskId failedId = memoryTaskManager.submit(
            () -> Task.Result.PARTIAL);
        TaskId successfulId = memoryTaskManager.submit(
            () -> Task.Result.COMPLETED);
        TaskId inProgressId = memoryTaskManager.submit(
            () -> {
                latch1.countDown();
                await(latch2);
                return Task.Result.COMPLETED;
            });
        TaskId waitingId = memoryTaskManager.submit(
            () -> Task.Result.COMPLETED);

        latch1.await();

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
        latch2.countDown();
    }

    private TaskManager.Status entryWithId(List<TaskExecutionDetails> list, TaskId taskId) {
        return list.stream()
            .filter(e -> e.getTaskId().equals(taskId))
            .findFirst().get()
            .getStatus();
    }

    @Test
    void listShouldAllowToSeeWaitingTasks() throws Exception {
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
        latch3.countDown();
    }

    @Test
    void listShouldAllowToSeeInProgressTasks() throws Exception {
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
        latch3.countDown();
    }

    @Test
    void listShouldAllowToSeeFailedTasks() throws Exception {
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
        latch3.countDown();
    }

    @Test
    void listShouldAllowToSeeSuccessfulTasks() throws Exception {
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
        latch3.countDown();
    }

    @Test
    void listShouldBeEmptyWhenNoTasks() {
        assertThat(memoryTaskManager.list()).isEmpty();
    }

    @Test
    void listCancelledShouldBeEmptyWhenNoTasks() {
        assertThat(memoryTaskManager.list(TaskManager.Status.CANCELLED)).isEmpty();
    }

    @Test
    void listCancelRequestedShouldBeEmptyWhenNoTasks() {
        assertThat(memoryTaskManager.list(TaskManager.Status.CANCEL_REQUESTED)).isEmpty();
    }

    @Test
    void awaitShouldNotThrowWhenCompletedTask() {
        TaskId taskId = memoryTaskManager.submit(
            () -> Task.Result.COMPLETED);
        memoryTaskManager.await(taskId);
        memoryTaskManager.await(taskId);
    }

    @Test
    void awaitShouldAwaitWaitingTask() {
        CountDownLatch latch = new CountDownLatch(1);
        memoryTaskManager.submit(
            () -> {
                await(latch);
                return Task.Result.COMPLETED;
            });
        latch.countDown();
        TaskId task2 = memoryTaskManager.submit(
            () -> Task.Result.COMPLETED);

        assertThat(memoryTaskManager.await(task2).getStatus()).isEqualTo(TaskManager.Status.COMPLETED);
    }

    @Test
    void submittedTaskShouldExecuteSequentially() {
        ConcurrentLinkedQueue<Integer> queue = new ConcurrentLinkedQueue<>();

        memoryTaskManager.submit(() -> {
            queue.add(1);
            sleep(50);
            queue.add(2);
            return Task.Result.COMPLETED;
        });
        memoryTaskManager.submit(() -> {
            queue.add(3);
            sleep(50);
            queue.add(4);
            return Task.Result.COMPLETED;
        });
        memoryTaskManager.submit(() -> {
            queue.add(5);
            sleep(50);
            queue.add(6);
            return Task.Result.COMPLETED;
        });

        awaitAtMostOneSecond.until(() -> queue.contains(6));

        assertThat(queue)
            .containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    void awaitShouldReturnFailedWhenExceptionThrown() {
        TaskId taskId = memoryTaskManager.submit(() -> {
            throw new RuntimeException();
        });
        awaitUntilTaskHasStatus(taskId, TaskManager.Status.FAILED);
        assertThat(memoryTaskManager.getExecutionDetails(taskId).getStatus())
            .isEqualTo(TaskManager.Status.FAILED);
    }

    @Test
    void getStatusShouldReturnFailedWhenExceptionThrown() {
        TaskId taskId = memoryTaskManager.submit(() -> {
            throw new RuntimeException();
        });
        awaitUntilTaskHasStatus(taskId, TaskManager.Status.FAILED);
        assertThat(memoryTaskManager.getExecutionDetails(taskId).getStatus())
            .isEqualTo(TaskManager.Status.FAILED);
    }

    private void sleep(int durationInMs) {
        try {
            Thread.sleep(durationInMs);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void await(CountDownLatch countDownLatch) {
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void awaitUntilTaskHasStatus(TaskId id, TaskManager.Status status) {
        awaitAtMostOneSecond.until(() -> memoryTaskManager.getExecutionDetails(id).getStatus().equals(status));
    }
}