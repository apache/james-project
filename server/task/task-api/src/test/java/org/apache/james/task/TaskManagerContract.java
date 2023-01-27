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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.core.api.SoftAssertions;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public interface TaskManagerContract {
    java.time.Duration UPDATE_INFORMATION_POLLING_INTERVAL = java.time.Duration.ofSeconds(1);
    Duration slowPacedPollInterval = Duration.ofMillis(100);
    ConditionFactory calmlyAwait = Awaitility.with()
        .pollInterval(slowPacedPollInterval)
        .and()
        .with()
        .pollDelay(slowPacedPollInterval)
        .await();
    ConditionFactory awaitAtMostTwoSeconds = calmlyAwait.atMost(Duration.ofSeconds(2));
    java.time.Duration TIMEOUT = java.time.Duration.ofMinutes(15);

    TaskManager taskManager() throws Exception;

    @Test
    default void submitShouldReturnATaskId() throws Exception {
        TaskId taskId = taskManager().submit(new CompletedTask());
        assertThat(taskId).isNotNull();
    }

    @Test
    default void getStatusShouldReturnUnknownWhenUnknownId() {
        TaskId unknownId = TaskId.generateTaskId();
        assertThatThrownBy(() -> taskManager().getExecutionDetails(unknownId))
            .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    default void getStatusShouldReturnWaitingWhenNotYetProcessed(CountDownLatch waitingForResultLatch) throws Exception {
        TaskManager taskManager = taskManager();
        taskManager.submit(new MemoryReferenceTask(() -> {
            waitingForResultLatch.await();
            return Task.Result.COMPLETED;
        }));

        TaskId taskId = taskManager.submit(new CompletedTask());

        assertThat(taskManager.getExecutionDetails(taskId).getStatus())
            .isEqualTo(TaskManager.Status.WAITING);
    }

    @Test
    default void taskCodeAfterCancelIsNotRun(CountDownLatch waitingForResultLatch) throws Exception {
        TaskManager taskManager = taskManager();
        CountDownLatch waitForTaskToBeLaunched = new CountDownLatch(1);
        AtomicInteger count = new AtomicInteger(0);

        TaskId id = taskManager.submit(new MemoryReferenceTask(() -> {
            waitForTaskToBeLaunched.countDown();
            waitingForResultLatch.await();
            //We sleep to handover the CPU to the scheduler
            Thread.sleep(1);
            count.incrementAndGet();
            return Task.Result.COMPLETED;
        }));

        waitForTaskToBeLaunched.await();
        taskManager.cancel(id);

        assertThat(count.get()).isEqualTo(0);
    }

    @Test
    default void completedTaskShouldNotBeCancelled() throws Exception {
        TaskManager taskManager = taskManager();
        TaskId id = taskManager.submit(new CompletedTask());

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
    default void failedTaskShouldNotBeCancelled() throws Exception {
        TaskManager taskManager = taskManager();
        TaskId id = taskManager.submit(new FailedTask());

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
    default void getStatusShouldBeCancelledWhenCancelled(CountDownLatch countDownLatch) throws Exception {
        TaskManager taskManager = taskManager();
        TaskId id = taskManager.submit(new MemoryReferenceTask(() -> {
            countDownLatch.await();
            return Task.Result.COMPLETED;
        }));

        awaitUntilTaskHasStatus(id, TaskManager.Status.IN_PROGRESS, taskManager);
        taskManager.cancel(id);

        awaitAtMostTwoSeconds.untilAsserted(() ->
            assertThat(taskManager.getExecutionDetails(id).getStatus())
                .isIn(TaskManager.Status.CANCELLED, TaskManager.Status.CANCEL_REQUESTED));

        countDownLatch.countDown();

        awaitUntilTaskHasStatus(id, TaskManager.Status.CANCELLED, taskManager);
        assertThat(taskManager.getExecutionDetails(id).getStatus())
            .isEqualTo(TaskManager.Status.CANCELLED);
    }

    @Test
    default void aWaitingTaskShouldBeCancelled(CountDownLatch countDownLatch) throws Exception {
        TaskManager taskManager = taskManager();
        TaskId id = taskManager.submit(new MemoryReferenceTask(() -> {
            countDownLatch.await();
            return Task.Result.COMPLETED;
        }));

        TaskId idTaskToCancel = taskManager.submit(new CompletedTask());

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
    default void cancelShouldBeIdempotent(CountDownLatch waitingForResultLatch) throws Exception {
        TaskManager taskManager = taskManager();
        TaskId id = taskManager.submit(new MemoryReferenceTask(() -> {
            waitingForResultLatch.await();
            return Task.Result.COMPLETED;
        }));
        awaitUntilTaskHasStatus(id, TaskManager.Status.IN_PROGRESS, taskManager);
        taskManager.cancel(id);
        assertThatCode(() -> taskManager.cancel(id))
            .doesNotThrowAnyException();
    }

    @Test
    default void getStatusShouldReturnInProgressWhenProcessingIsInProgress(CountDownLatch waitingForResultLatch) throws Exception {
        TaskManager taskManager = taskManager();
        TaskId taskId = taskManager.submit(new MemoryReferenceTask(() -> {
            waitingForResultLatch.await();
            return Task.Result.COMPLETED;
        }));
        awaitUntilTaskHasStatus(taskId, TaskManager.Status.IN_PROGRESS, taskManager);
        assertThat(taskManager.getExecutionDetails(taskId).getStatus())
            .isEqualTo(TaskManager.Status.IN_PROGRESS);
    }

    @Test
    default void getStatusShouldReturnCompletedWhenRunSuccessfully() throws Exception {
        TaskManager taskManager = taskManager();
        TaskId taskId = taskManager.submit(
            new CompletedTask());

        awaitUntilTaskHasStatus(taskId, TaskManager.Status.COMPLETED, taskManager);
        assertThat(taskManager.getExecutionDetails(taskId).getStatus())
            .isEqualTo(TaskManager.Status.COMPLETED);
    }

    @Test
    default void additionalInformationShouldBeUpdatedWhenRunSuccessfully() throws Exception {
        TaskManager taskManager = taskManager();
        TaskId taskId = taskManager.submit(new MemoryReferenceWithCounterTask(counter -> {
            counter.incrementAndGet();
            return Task.Result.COMPLETED;
        }));

        awaitUntilTaskHasStatus(taskId, TaskManager.Status.COMPLETED, taskManager);
        MemoryReferenceWithCounterTask.AdditionalInformation additionalInformation = (MemoryReferenceWithCounterTask.AdditionalInformation) taskManager
            .getExecutionDetails(taskId)
            .getAdditionalInformation()
            .get();

        assertThat(additionalInformation.getCount())
            .isEqualTo(1);
    }

    @Test
    default void additionalInformationShouldBeUpdatedWhenFailed() throws Exception {
        TaskManager taskManager = taskManager();
        TaskId taskId = taskManager.submit(new MemoryReferenceWithCounterTask(counter -> {
            counter.incrementAndGet();
            throw new RuntimeException();
        }));

        awaitUntilTaskHasStatus(taskId, TaskManager.Status.FAILED, taskManager);
        MemoryReferenceWithCounterTask.AdditionalInformation additionalInformation = (MemoryReferenceWithCounterTask.AdditionalInformation) taskManager
            .getExecutionDetails(taskId)
            .getAdditionalInformation()
            .get();

        assertThat(additionalInformation.getCount())
            .isEqualTo(1);
    }

    @Test
    default void additionalInformationShouldBeUpdatedWhenCancelled(CountDownLatch countDownLatch) throws Exception {
        TaskManager taskManager = taskManager();
        TaskId id = taskManager.submit(new MemoryReferenceWithCounterTask((counter) -> {
            counter.incrementAndGet();
            countDownLatch.await();
            return Task.Result.COMPLETED;
        }));

        awaitUntilTaskHasStatus(id, TaskManager.Status.IN_PROGRESS, taskManager);
        taskManager.cancel(id);
        awaitUntilTaskHasStatus(id, TaskManager.Status.CANCELLED, taskManager);

        assertThat(taskManager.getExecutionDetails(id).getStatus())
            .isEqualTo(TaskManager.Status.CANCELLED);

        MemoryReferenceWithCounterTask.AdditionalInformation additionalInformation = (MemoryReferenceWithCounterTask.AdditionalInformation) taskManager
            .getExecutionDetails(id)
            .getAdditionalInformation()
            .get();

        assertThat(additionalInformation.getCount())
            .isEqualTo(1);
    }

    @Test
    default void additionalInformationShouldBeUpdatedDuringExecution(CountDownLatch countDownLatch) throws Exception {
        TaskManager taskManager = taskManager();
        TaskId id = taskManager.submit(new MemoryReferenceWithCounterTask((counter) -> {
            counter.incrementAndGet();
            countDownLatch.await();
            return Task.Result.COMPLETED;
        }));

        awaitUntilTaskHasStatus(id, TaskManager.Status.IN_PROGRESS, taskManager);

        calmlyAwait.atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            assertThat(getAdditionalInformation(taskManager, id).getCount()).isEqualTo(1L));
    }

    @Test
    default void additionalInformationShouldBeAvailableOnAnyTaskManagerDuringExecution(CountDownLatch countDownLatch) throws Exception {
        TaskManager taskManager = taskManager();
        TaskManager otherTaskManager = taskManager();
        TaskId id = taskManager.submit(new MemoryReferenceWithCounterTask((counter) -> {
            counter.incrementAndGet();
            countDownLatch.await();
            return Task.Result.COMPLETED;
        }));

        awaitUntilTaskHasStatus(id, TaskManager.Status.IN_PROGRESS, taskManager);

        calmlyAwait.atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            assertThat(getAdditionalInformation(taskManager, id).getCount()).isEqualTo(1L));
        assertThat(getAdditionalInformation(otherTaskManager, id).getCount()).isEqualTo(1L);
    }

    default MemoryReferenceWithCounterTask.AdditionalInformation getAdditionalInformation(TaskManager taskManager, TaskId id) {
        return (MemoryReferenceWithCounterTask.AdditionalInformation) taskManager
            .getExecutionDetails(id)
            .getAdditionalInformation()
            .get();
    }

    @Test
    default void getStatusShouldReturnFailedWhenRunPartially() throws Exception {
        TaskManager taskManager = taskManager();
        TaskId taskId = taskManager.submit(
            new FailedTask());

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
            new FailedTask());
        TaskId successfulId = taskManager.submit(
            new CompletedTask());
        TaskId inProgressId = taskManager.submit(new MemoryReferenceTask(
            () -> {
                latch1.countDown();
                waitingForResultLatch.await();
                return Task.Result.COMPLETED;
            }));
        TaskId waitingId = taskManager.submit(new CompletedTask());

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
            new FailedTask());
        taskManager.submit(
            new CompletedTask());
        taskManager.submit(new MemoryReferenceTask(
            () -> {
                latch1.await();
                latch2.countDown();
                waitingForResultLatch.await();
                return Task.Result.COMPLETED;
            }));
        TaskId waitingId = taskManager.submit(new MemoryReferenceTask(
            () -> {
                waitingForResultLatch.await();
                latch2.countDown();
                return Task.Result.COMPLETED;
            }));

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
            new FailedTask());
        TaskId successfulId = taskManager.submit(
            new CompletedTask());
        taskManager.submit(new MemoryReferenceTask(
            () -> {
                latch1.await();
                latch2.countDown();
                waitingForResultLatch.await();
                return Task.Result.COMPLETED;
            }));
        taskManager.submit(new MemoryReferenceTask(
            () -> {
                waitingForResultLatch.await();
                latch2.countDown();
                return Task.Result.COMPLETED;
            }));

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
            new FailedTask());
        taskManager.submit(
            new CompletedTask());
        taskManager.submit(new MemoryReferenceTask(
            () -> {
                latch1.await();
                latch2.countDown();
                waitingForResultLatch.await();
                return Task.Result.COMPLETED;
            }));
        taskManager.submit(new MemoryReferenceTask(
            () -> {
                waitingForResultLatch.await();
                latch2.countDown();
                return Task.Result.COMPLETED;
            }));

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
            new FailedTask());
        taskManager.submit(
            new CompletedTask());
        TaskId inProgressId = taskManager.submit(new MemoryReferenceTask(
            () -> {
                latch1.await();
                latch2.countDown();
                waitingForResultLatch.await();
                return Task.Result.COMPLETED;
            }));
        taskManager.submit(new MemoryReferenceTask(
            () -> {
                waitingForResultLatch.await();
                latch2.countDown();
                return Task.Result.COMPLETED;
            }));

        latch1.countDown();
        latch2.await();

        assertThat(taskManager.list(TaskManager.Status.IN_PROGRESS))
            .extracting(TaskExecutionDetails::getTaskId)
            .containsOnly(inProgressId);
    }

    @Test
    default void listShouldBeEmptyWhenNoTasks() throws Exception {
        assertThat(taskManager().list()).isEmpty();
    }

    @Test
    default void listCancelledShouldBeEmptyWhenNoTasks() throws Exception {
        assertThat(taskManager().list(TaskManager.Status.CANCELLED)).isEmpty();
    }

    @Test
    default void listCancelRequestedShouldBeEmptyWhenNoTasks() throws Exception {
        assertThat(taskManager().list(TaskManager.Status.CANCEL_REQUESTED)).isEmpty();
    }

    @Test
    default void awaitShouldNotThrowWhenCompletedTask() throws Exception {
        TaskManager taskManager = taskManager();
        TaskId taskId = taskManager.submit(new CompletedTask());
        taskManager.await(taskId, TIMEOUT);
        taskManager.await(taskId, TIMEOUT);
    }

    @Test
    default void awaitShouldAwaitWaitingTask() throws Exception {
        TaskManager taskManager = taskManager();
        CountDownLatch latch = new CountDownLatch(1);
        taskManager.submit(new MemoryReferenceTask(
            () -> {
                latch.await();
                return Task.Result.COMPLETED;
            }));
        latch.countDown();
        TaskId task2 = taskManager.submit(new CompletedTask());

        assertThat(taskManager.await(task2, TIMEOUT).getStatus()).isEqualTo(TaskManager.Status.COMPLETED);
    }

    @Test
    default void awaitANonExistingTaskShouldReturnAnUnknownAwaitedTaskExecutionDetailsAndThrow() {
        assertThatCode(() -> taskManager().await(TaskId.generateTaskId(), TIMEOUT))
            .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    default void awaitWithATooShortTimeoutShouldReturnATimeoutAwaitedTaskExecutionDetailsAndThrow() throws Exception {
        TaskManager taskManager = taskManager();
        TaskId taskId = taskManager.submit(new MemoryReferenceTask(
            () -> {
                Thread.sleep(1000);
                return Task.Result.COMPLETED;
            }));

        assertThatCode(() -> taskManager.await(taskId, java.time.Duration.ofMillis(10)))
            .isInstanceOf(TaskManager.ReachedTimeoutException.class);
    }

    @Test
    default void submittedTaskShouldExecuteSequentially() throws Exception {
        TaskManager taskManager = taskManager();
        ConcurrentLinkedQueue<Integer> queue = new ConcurrentLinkedQueue<>();

        taskManager.submit(new MemoryReferenceTask(() -> {
            queue.add(1);
            Thread.sleep(50);
            queue.add(2);
            return Task.Result.COMPLETED;
        }));
        taskManager.submit(new MemoryReferenceTask(() -> {
            queue.add(3);
            Thread.sleep(50);
            queue.add(4);
            return Task.Result.COMPLETED;
        }));
        taskManager.submit(new MemoryReferenceTask(() -> {
            queue.add(5);
            Thread.sleep(50);
            queue.add(6);
            return Task.Result.COMPLETED;
        }));

        awaitAtMostTwoSeconds.until(() -> queue.contains(6));

        assertThat(queue)
            .containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    default void awaitShouldReturnFailedWhenExceptionThrown() throws Exception {
        TaskManager taskManager = taskManager();
        TaskId taskId = taskManager.submit(new ThrowingTask());
        awaitUntilTaskHasStatus(taskId, TaskManager.Status.FAILED, taskManager);
        assertThat(taskManager.getExecutionDetails(taskId).getStatus())
            .isEqualTo(TaskManager.Status.FAILED);
    }

    @Test
    default void getStatusShouldReturnFailedWhenExceptionThrown() throws Exception {
        TaskManager taskManager = taskManager();
        TaskId taskId = taskManager.submit(new ThrowingTask());
        awaitUntilTaskHasStatus(taskId, TaskManager.Status.FAILED, taskManager);
        assertThat(taskManager.getExecutionDetails(taskId).getStatus())
            .isEqualTo(TaskManager.Status.FAILED);
    }

    default void awaitUntilTaskHasStatus(TaskId id, TaskManager.Status status, TaskManager taskManager) {
        awaitAtMostTwoSeconds.until(() -> taskManager.getExecutionDetails(id).getStatus(), Matchers.equalTo(status));
    }
}
