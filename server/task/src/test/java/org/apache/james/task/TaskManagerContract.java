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
import static org.awaitility.Duration.FIVE_SECONDS;
import static org.awaitility.Duration.ONE_HUNDRED_MILLISECONDS;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.core.api.SoftAssertions;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public interface TaskManagerContract {

    Duration slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS;
    ConditionFactory calmlyAwait = Awaitility.with()
        .pollInterval(slowPacedPollInterval)
        .and()
        .with()
        .pollDelay(slowPacedPollInterval)
        .await();
    ConditionFactory awaitAtMostFiveSeconds = calmlyAwait.atMost(FIVE_SECONDS);

    TaskManager taskManager();

    @Test
    default void submitShouldReturnATaskId() {
        TaskId taskId = taskManager().submit(() -> Task.Result.COMPLETED);
        assertThat(taskId).isNotNull();
    }

    @Test
    default void getStatusShouldReturnUnknownWhenUnknownId() {
        TaskId unknownId = TaskId.generateTaskId();
        assertThatThrownBy(() -> taskManager().getExecutionDetails(unknownId))
            .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    default void getStatusShouldReturnWaitingWhenNotYetProcessed(CountDownLatch waitingForResultLatch) {
        TaskManager taskManager = taskManager();
        taskManager.submit(() -> {
            waitingForResultLatch.await();
            return Task.Result.COMPLETED;
        });

        TaskId taskId = taskManager.submit(() -> Task.Result.COMPLETED);

        assertThat(taskManager.getExecutionDetails(taskId).getStatus())
            .isEqualTo(TaskManager.Status.WAITING);
    }

    @Test
    default void taskCodeAfterCancelIsNotRun(CountDownLatch waitingForResultLatch) throws InterruptedException {
        TaskManager taskManager = taskManager();
        CountDownLatch waitForTaskToBeLaunched = new CountDownLatch(1);
        AtomicInteger count = new AtomicInteger(0);

        TaskId id = taskManager.submit(() -> {
            waitForTaskToBeLaunched.countDown();
            waitingForResultLatch.await();
            //We sleep to handover the CPU to the scheduler
            Thread.sleep(1);
            count.incrementAndGet();
            return Task.Result.COMPLETED;
        });

        waitForTaskToBeLaunched.await();
        taskManager.cancel(id);

        assertThat(count.get()).isEqualTo(0);
    }

    @Test
    default void completedTaskShouldNotBeCancelled() {
        TaskManager taskManager = taskManager();
        TaskId id = taskManager.submit(() -> Task.Result.COMPLETED);

        awaitUntilTaskHasStatus(id, TaskManager.Status.COMPLETED, taskManager);
        taskManager.cancel(id);

        try {
            awaitUntilTaskHasStatus(id, TaskManager.Status.CANCELLED, taskManager);
        } catch (Exception e) {
            //Should timeout
        }
        assertThat(taskManager.getExecutionDetails(id).getStatus()).isEqualTo(TaskManager.Status.COMPLETED);
    }

    @Test
    default void failedTaskShouldNotBeCancelled() {
        TaskManager taskManager = taskManager();
        TaskId id = taskManager.submit(() -> Task.Result.PARTIAL);

        awaitUntilTaskHasStatus(id, TaskManager.Status.FAILED, taskManager);
        taskManager.cancel(id);

        try {
            awaitUntilTaskHasStatus(id, TaskManager.Status.CANCELLED, taskManager);
        } catch (Exception e) {
            //Should timeout
        }
        assertThat(taskManager.getExecutionDetails(id).getStatus()).isEqualTo(TaskManager.Status.FAILED);
    }

    @Test
    default void getStatusShouldBeCancelledWhenCancelled(CountDownLatch countDownLatch) {
        TaskManager taskManager = taskManager();
        TaskId id = taskManager.submit(() -> {
            countDownLatch.await();
            return Task.Result.COMPLETED;
        });

        awaitUntilTaskHasStatus(id, TaskManager.Status.IN_PROGRESS, taskManager);
        taskManager.cancel(id);

        assertThat(taskManager.getExecutionDetails(id).getStatus())
            .isIn(TaskManager.Status.CANCELLED, TaskManager.Status.CANCEL_REQUESTED);

        countDownLatch.countDown();

        awaitUntilTaskHasStatus(id, TaskManager.Status.CANCELLED, taskManager);
        assertThat(taskManager.getExecutionDetails(id).getStatus())
            .isEqualTo(TaskManager.Status.CANCELLED);

    }

    @Test
    default void aWaitingTaskShouldBeCancelled(CountDownLatch countDownLatch) {
        TaskManager taskManager = taskManager();
        TaskId id = taskManager.submit(() -> {
            countDownLatch.await();
            return Task.Result.COMPLETED;
        });

        TaskId idTaskToCancel = taskManager.submit(() -> Task.Result.COMPLETED);

        taskManager.cancel(idTaskToCancel);

        awaitUntilTaskHasStatus(id, TaskManager.Status.IN_PROGRESS, taskManager);

        assertThat(taskManager.getExecutionDetails(idTaskToCancel).getStatus())
            .isIn(TaskManager.Status.CANCELLED, TaskManager.Status.CANCEL_REQUESTED);

        countDownLatch.countDown();

        awaitUntilTaskHasStatus(idTaskToCancel, TaskManager.Status.CANCELLED, taskManager);
        assertThat(taskManager.getExecutionDetails(idTaskToCancel).getStatus())
            .isEqualTo(TaskManager.Status.CANCELLED);
    }

    @Test
    default void cancelShouldBeIdempotent(CountDownLatch waitingForResultLatch) {
        TaskManager taskManager = taskManager();
        TaskId id = taskManager.submit(() -> {
            waitingForResultLatch.await();
            return Task.Result.COMPLETED;
        });
        awaitUntilTaskHasStatus(id, TaskManager.Status.IN_PROGRESS, taskManager);
        taskManager.cancel(id);
        assertThatCode(() -> taskManager.cancel(id))
            .doesNotThrowAnyException();
    }

    @Test
    default void getStatusShouldReturnInProgressWhenProcessingIsInProgress(CountDownLatch waitingForResultLatch) {
        TaskManager taskManager = taskManager();
        TaskId taskId = taskManager.submit(() -> {
            waitingForResultLatch.await();
            return Task.Result.COMPLETED;
        });
        awaitUntilTaskHasStatus(taskId, TaskManager.Status.IN_PROGRESS, taskManager);
        assertThat(taskManager.getExecutionDetails(taskId).getStatus())
            .isEqualTo(TaskManager.Status.IN_PROGRESS);
    }

    @Test
    default void getStatusShouldReturnCompletedWhenRunSuccessfully() {
        TaskManager taskManager = taskManager();
        TaskId taskId = taskManager.submit(
            () -> Task.Result.COMPLETED);

        awaitUntilTaskHasStatus(taskId, TaskManager.Status.COMPLETED, taskManager);
        assertThat(taskManager.getExecutionDetails(taskId).getStatus())
            .isEqualTo(TaskManager.Status.COMPLETED);
    }

    @Test
    default void getStatusShouldReturnFailedWhenRunPartially() {
        TaskManager taskManager = taskManager();
        TaskId taskId = taskManager.submit(
            () -> Task.Result.PARTIAL);

        awaitUntilTaskHasStatus(taskId, TaskManager.Status.FAILED, taskManager);

        assertThat(taskManager.getExecutionDetails(taskId).getStatus())
            .isEqualTo(TaskManager.Status.FAILED);
    }

    @Test
    default void listShouldReturnTaskStatus(CountDownLatch waitingForResultLatch) throws Exception {
        TaskManager taskManager = taskManager();
        SoftAssertions softly = new SoftAssertions();

        CountDownLatch latch1 = new CountDownLatch(1);

        TaskId failedId = taskManager.submit(
            () -> Task.Result.PARTIAL);
        TaskId successfulId = taskManager.submit(
            () -> Task.Result.COMPLETED);
        TaskId inProgressId = taskManager.submit(
            () -> {
                latch1.countDown();
                waitingForResultLatch.await();
                return Task.Result.COMPLETED;
            });
        TaskId waitingId = taskManager.submit(
            () -> Task.Result.COMPLETED);

        latch1.await();

        List<TaskExecutionDetails> list = taskManager.list();
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

    default TaskManager.Status entryWithId(List<TaskExecutionDetails> list, TaskId taskId) {
        return list.stream()
            .filter(e -> e.getTaskId().equals(taskId))
            .findFirst()
            .get()
            .getStatus();
    }

    @Test
    default void listShouldAllowToSeeWaitingTasks(CountDownLatch waitingForResultLatch) throws Exception {
        TaskManager taskManager = taskManager();
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);

        taskManager.submit(
            () -> Task.Result.PARTIAL);
        taskManager.submit(
            () -> Task.Result.COMPLETED);
        taskManager.submit(
            () -> {
                latch1.await();
                latch2.countDown();
                waitingForResultLatch.await();
                return Task.Result.COMPLETED;
            });
        TaskId waitingId = taskManager.submit(
            () -> {
                waitingForResultLatch.await();
                latch2.countDown();
                return Task.Result.COMPLETED;
            });

        latch1.countDown();
        latch2.await();

        assertThat(taskManager.list(TaskManager.Status.WAITING))
            .extracting(TaskExecutionDetails::getTaskId)
            .containsOnly(waitingId);
    }

    @Test
    default void listShouldAllowToSeeCompletedTasks(CountDownLatch waitingForResultLatch) throws Exception {
        TaskManager taskManager = taskManager();
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);

        taskManager.submit(
            () -> Task.Result.PARTIAL);
        TaskId successfulId = taskManager.submit(
            () -> Task.Result.COMPLETED);
        taskManager.submit(
            () -> {
                latch1.await();
                latch2.countDown();
                waitingForResultLatch.await();
                return Task.Result.COMPLETED;
            });
        taskManager.submit(
            () -> {
                waitingForResultLatch.await();
                latch2.countDown();
                return Task.Result.COMPLETED;
            });

        latch1.countDown();
        latch2.await();

        assertThat(taskManager.list(TaskManager.Status.COMPLETED))
            .extracting(TaskExecutionDetails::getTaskId)
            .containsOnly(successfulId);
    }

    @Test
    default void listShouldAllowToSeeFailedTasks(CountDownLatch waitingForResultLatch) throws Exception {
        TaskManager taskManager = taskManager();
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);

        TaskId failedId = taskManager.submit(
            () -> Task.Result.PARTIAL);
        taskManager.submit(
            () -> Task.Result.COMPLETED);
        taskManager.submit(
            () -> {
                latch1.await();
                latch2.countDown();
                waitingForResultLatch.await();
                return Task.Result.COMPLETED;
            });
        taskManager.submit(
            () -> {
                waitingForResultLatch.await();
                latch2.countDown();
                return Task.Result.COMPLETED;
            });

        latch1.countDown();
        latch2.await();

        assertThat(taskManager.list(TaskManager.Status.FAILED))
            .extracting(TaskExecutionDetails::getTaskId)
            .containsOnly(failedId);
    }

    @Test
    default void listShouldAllowToSeeInProgressfulTasks(CountDownLatch waitingForResultLatch) throws Exception {
        TaskManager taskManager = taskManager();
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);

        taskManager.submit(
            () -> Task.Result.PARTIAL);
        taskManager.submit(
            () -> Task.Result.COMPLETED);
        TaskId inProgressId = taskManager.submit(
            () -> {
                latch1.await();
                latch2.countDown();
                waitingForResultLatch.await();
                return Task.Result.COMPLETED;
            });
        taskManager.submit(
            () -> {
                waitingForResultLatch.await();
                latch2.countDown();
                return Task.Result.COMPLETED;
            });

        latch1.countDown();
        latch2.await();

        assertThat(taskManager.list(TaskManager.Status.IN_PROGRESS))
            .extracting(TaskExecutionDetails::getTaskId)
            .containsOnly(inProgressId);
    }

    @Test
    default void listShouldBeEmptyWhenNoTasks() {
        assertThat(taskManager().list()).isEmpty();
    }

    @Test
    default void listCancelledShouldBeEmptyWhenNoTasks() {
        assertThat(taskManager().list(TaskManager.Status.CANCELLED)).isEmpty();
    }

    @Test
    default void listCancelRequestedShouldBeEmptyWhenNoTasks() {
        assertThat(taskManager().list(TaskManager.Status.CANCEL_REQUESTED)).isEmpty();
    }

    @Test
    default void awaitShouldNotThrowWhenCompletedTask() {
        TaskManager taskManager = taskManager();
        TaskId taskId = taskManager.submit(
            () -> Task.Result.COMPLETED);
        taskManager.await(taskId);
        taskManager.await(taskId);
    }

    @Test
    default void awaitShouldAwaitWaitingTask() {
        TaskManager taskManager = taskManager();
        CountDownLatch latch = new CountDownLatch(1);
        taskManager.submit(
            () -> {
                latch.await();
                return Task.Result.COMPLETED;
            });
        latch.countDown();
        TaskId task2 = taskManager.submit(
            () -> Task.Result.COMPLETED);

        assertThat(taskManager.await(task2).getStatus()).isEqualTo(TaskManager.Status.COMPLETED);
    }

    @Test
    default void submittedTaskShouldExecuteSequentially() {
        TaskManager taskManager = taskManager();
        ConcurrentLinkedQueue<Integer> queue = new ConcurrentLinkedQueue<>();

        taskManager.submit(() -> {
            queue.add(1);
            Thread.sleep(50);
            queue.add(2);
            return Task.Result.COMPLETED;
        });
        taskManager.submit(() -> {
            queue.add(3);
            Thread.sleep(50);
            queue.add(4);
            return Task.Result.COMPLETED;
        });
        taskManager.submit(() -> {
            queue.add(5);
            Thread.sleep(50);
            queue.add(6);
            return Task.Result.COMPLETED;
        });

        awaitAtMostFiveSeconds.until(() -> queue.contains(6));

        assertThat(queue)
            .containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    default void awaitShouldReturnFailedWhenExceptionThrown() {
        TaskManager taskManager = taskManager();
        TaskId taskId = taskManager.submit(() -> {
            throw new RuntimeException();
        });
        awaitUntilTaskHasStatus(taskId, TaskManager.Status.FAILED, taskManager);
        assertThat(taskManager.getExecutionDetails(taskId).getStatus())
            .isEqualTo(TaskManager.Status.FAILED);
    }

    @Test
    default void getStatusShouldReturnFailedWhenExceptionThrown() {
        TaskManager taskManager = taskManager();
        TaskId taskId = taskManager.submit(() -> {
            throw new RuntimeException();
        });
        awaitUntilTaskHasStatus(taskId, TaskManager.Status.FAILED, taskManager);
        assertThat(taskManager.getExecutionDetails(taskId).getStatus())
            .isEqualTo(TaskManager.Status.FAILED);
    }

    default void awaitUntilTaskHasStatus(TaskId id, TaskManager.Status status, TaskManager taskManager) {
        awaitAtMostFiveSeconds.until(() -> taskManager.getExecutionDetails(id).getStatus(), Matchers.equalTo(status));
    }
}
