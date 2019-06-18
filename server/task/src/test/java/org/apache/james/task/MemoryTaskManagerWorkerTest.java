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
import static org.awaitility.Duration.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Duration.ONE_SECOND;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

public class MemoryTaskManagerWorkerTest {

    private final MemoryTaskManagerWorker worker = new MemoryTaskManagerWorker();

    private final Duration slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS;
    private final ConditionFactory calmlyAwait = Awaitility.with()
        .pollInterval(slowPacedPollInterval)
        .and()
        .with()
        .pollDelay(slowPacedPollInterval)
        .await();
    private final ConditionFactory awaitAtMostOneSecond = calmlyAwait.atMost(ONE_SECOND);

    private final Task successfulTask = () -> Task.Result.COMPLETED;
    private final Task failedTask = () -> Task.Result.PARTIAL;
    private final Task throwingTask = () -> {
        throw new RuntimeException("Throwing Task");
    };

    @Test
    public void aSuccessfullTaskShouldCompleteSuccessfully() {
        assertThatTaskSucceeded(successfulTask);
    }

    @Test
    public void aFailedTaskShouldCompleteWithFailedStatus() {
        assertThatTaskFailed(failedTask);
    }

    @Test
    public void aThrowingTaskShouldCompleteWithFailedStatus() {
        assertThatTaskFailed(throwingTask);
    }

    @Test
    public void theWorkerShouldReportThatATaskIsInProgress() {
        TaskId id = TaskId.generateTaskId();
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch taskLaunched = new CountDownLatch(1);

        Task inProgressTask = () -> {
            taskLaunched.countDown();
            await(latch);
            return Task.Result.COMPLETED;
        };

        TaskWithId taskWithId = new TaskWithId(id, inProgressTask);
        TaskExecutionDetails executionDetails = TaskExecutionDetails.from(inProgressTask, id);
        ConcurrentHashMap<TaskId, TaskExecutionDetails> idToDetails = new ConcurrentHashMap<>();
        idToDetails.put(id, executionDetails);

        worker.executeTask(taskWithId, updateDetails(idToDetails, id)).cache();
        await(taskLaunched);
        assertThat(idToDetails.get(id).getStatus()).isEqualTo(TaskManager.Status.IN_PROGRESS);
        latch.countDown();
    }

    @Test
    public void theWorkerShouldNotRunATaskRequestedForCancellation() {
        TaskId id = TaskId.generateTaskId();
        AtomicInteger counter = new AtomicInteger(0);

        Task task = () -> {
            counter.incrementAndGet();
            return Task.Result.COMPLETED;
        };

        TaskWithId taskWithId = new TaskWithId(id, task);
        TaskExecutionDetails executionDetails = TaskExecutionDetails.from(task, id);
        ConcurrentHashMap<TaskId, TaskExecutionDetails> idToDetails = new ConcurrentHashMap<>();

        idToDetails.put(id, executionDetails.cancelRequested());

        worker.executeTask(taskWithId, updateDetails(idToDetails, id)).subscribe();

        assertThat(counter.get()).isEqualTo(0);
        assertThat(idToDetails.get(id).getStatus()).isEqualTo(TaskManager.Status.CANCEL_REQUESTED);
    }

    @Test
    public void theWorkerShouldNotRunACancelledTask() {
        TaskId id = TaskId.generateTaskId();
        AtomicInteger counter = new AtomicInteger(0);

        Task task = () -> {
            counter.incrementAndGet();
            return Task.Result.COMPLETED;
        };

        TaskWithId taskWithId = new TaskWithId(id, task);
        TaskExecutionDetails executionDetails = TaskExecutionDetails.from(task, id);
        ConcurrentHashMap<TaskId, TaskExecutionDetails> idToDetails = new ConcurrentHashMap<>();

        idToDetails.put(id, executionDetails.cancelEffectively());

        worker.executeTask(taskWithId, updateDetails(idToDetails, id)).subscribe();

        assertThat(counter.get()).isEqualTo(0);
        assertThat(idToDetails.get(id).getStatus()).isEqualTo(TaskManager.Status.CANCELLED);
    }

    @Test
    public void theWorkerShouldCancelAnInProgressTask() {
        TaskId id = TaskId.generateTaskId();
        AtomicInteger counter = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        Task inProgressTask = () -> {
            await(latch);
            counter.incrementAndGet();
            return Task.Result.COMPLETED;
        };

        TaskWithId taskWithId = new TaskWithId(id, inProgressTask);
        TaskExecutionDetails executionDetails = TaskExecutionDetails.from(inProgressTask, id);
        ConcurrentHashMap<TaskId, TaskExecutionDetails> idToDetails = new ConcurrentHashMap<>();
        idToDetails.put(id, executionDetails);

        worker.executeTask(taskWithId, updateDetails(idToDetails, id)).cache();

        worker.cancelTask(id, updateDetails(idToDetails, id));
        assertThat(idToDetails.get(id).getStatus()).isIn(TaskManager.Status.CANCELLED, TaskManager.Status.CANCEL_REQUESTED);

        assertThat(counter.get()).isEqualTo(0);

        awaitUntilTaskHasStatus(idToDetails, id, TaskManager.Status.CANCELLED);
        assertThat(idToDetails.get(id).getStatus()).isEqualTo(TaskManager.Status.CANCELLED);
    }

    private void assertThatTaskSucceeded(Task task) {
        assertTaskExecutionResultAndStatus(task, Task.Result.COMPLETED, TaskManager.Status.COMPLETED);
    }

    private void assertThatTaskFailed(Task task) {
        assertTaskExecutionResultAndStatus(task, Task.Result.PARTIAL, TaskManager.Status.FAILED);
    }

    private void assertTaskExecutionResultAndStatus(Task task, Task.Result expectedResult, TaskManager.Status expectedStatus) {
        TaskId id = TaskId.generateTaskId();
        TaskWithId taskWithId = new TaskWithId(id, task);
        TaskExecutionDetails executionDetails = TaskExecutionDetails.from(task, id);
        ConcurrentHashMap<TaskId, TaskExecutionDetails> idToDetails = new ConcurrentHashMap<>();
        idToDetails.put(id, executionDetails);

        Mono<Task.Result> result = worker.executeTask(taskWithId, updateDetails(idToDetails, id)).cache();

        assertThat(result.block()).isEqualTo(expectedResult);
        assertThat(idToDetails.get(id).getStatus()).isEqualTo(expectedStatus);
    }

    private Consumer<TaskExecutionDetailsUpdater> updateDetails(ConcurrentHashMap<TaskId, TaskExecutionDetails> idToExecutionDetails, TaskId taskId) {
        return updater -> {
            TaskExecutionDetails newDetails = updater.update(idToExecutionDetails.get(taskId));
            idToExecutionDetails.put(taskId, newDetails);
        };
    }

    private void awaitUntilTaskHasStatus(ConcurrentHashMap<TaskId, TaskExecutionDetails> idToExecutionDetails, TaskId id, TaskManager.Status status) {
        awaitAtMostOneSecond.until(() -> idToExecutionDetails.get(id).getStatus().equals(status));
    }

    private void await(CountDownLatch countDownLatch) {
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
