/**
 * *************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/
package org.apache.james.task.eventsourcing.distributed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Duration.FIVE_HUNDRED_MILLISECONDS;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.server.task.json.TestTask;
import org.apache.james.server.task.json.dto.TestTaskDTOModules;
import org.apache.james.task.CompletedTask;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManagerWorker;
import org.apache.james.task.TaskWithId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class RabbitMQWorkQueueTest {
    private static final TaskId TASK_ID = TaskId.fromString("2c7f4081-aa30-11e9-bf6c-2d3b9e84aafd");
    private static final TaskId TASK_ID_2 = TaskId.fromString("3c7f4081-aa30-11e9-bf6c-2d3b9e84aafd");
    private static final Task TASK = new CompletedTask();
    private static final Task TASK2 = new CompletedTask();
    private static final TaskWithId TASK_WITH_ID = new TaskWithId(TASK_ID, TASK);
    private static final TaskWithId TASK_WITH_ID_2 = new TaskWithId(TASK_ID_2, TASK2);

    @RegisterExtension
    RabbitMQExtension rabbitMQExtension = RabbitMQExtension.defaultRabbitMQ()
        .restartPolicy(RabbitMQExtension.DockerRestartPolicy.PER_TEST);


    private RabbitMQWorkQueue testee;
    private ImmediateWorker worker;
    private JsonTaskSerializer serializer;

    private static class ImmediateWorker implements TaskManagerWorker {

        ConcurrentLinkedQueue<TaskWithId> tasks = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Task.Result> results = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<TaskId> failedTasks = new ConcurrentLinkedQueue<>();

        @Override
        public Mono<Task.Result> executeTask(TaskWithId taskWithId) {
            tasks.add(taskWithId);
            return Mono.fromCallable(() -> taskWithId.getTask().run())
                .doOnNext(result -> results.add(result))
                .subscribeOn(Schedulers.boundedElastic());
        }

        @Override
        public void cancelTask(TaskId taskId) {
        }

        @Override
        public void fail(TaskId taskId, Optional<TaskExecutionDetails.AdditionalInformation> additionalInformation,String errorMessage, Throwable reason) {
            failedTasks.add(taskId);
        }

        @Override
        public void close() throws IOException {
        }
    }

    @BeforeEach
    void setUp() {
        worker = spy(new ImmediateWorker());
        serializer = JsonTaskSerializer.of(TestTaskDTOModules.COMPLETED_TASK_MODULE);
        testee = new RabbitMQWorkQueue(worker, rabbitMQExtension.getRabbitChannelPool(), serializer);
        testee.start();
    }

    @AfterEach
    void tearDown() {
        testee.close();
    }

    @Test
    void workQueueShouldConsumeSubmittedTask() {
        testee.submit(TASK_WITH_ID);
        await().atMost(FIVE_HUNDRED_MILLISECONDS).until(() -> !worker.results.isEmpty());
        assertThat(worker.tasks).containsExactly(TASK_WITH_ID);
        assertThat(worker.results).containsExactly(Task.Result.COMPLETED);
    }

    @Test
    void workQueueShouldConsumeTwoSubmittedTasks() {
        testee.submit(TASK_WITH_ID);
        testee.submit(TASK_WITH_ID_2);
        await().atMost(FIVE_HUNDRED_MILLISECONDS).until(() -> worker.results.size() == 2);
        assertThat(worker.tasks).containsExactly(TASK_WITH_ID, TASK_WITH_ID_2);
        assertThat(worker.results).allSatisfy(result -> assertThat(result).isEqualTo(Task.Result.COMPLETED));
    }

    @Test
    void givenTwoWorkQueuesOnlyTheFirstOneIsConsumingTasks() {
        testee.submit(TASK_WITH_ID);

        ImmediateWorker otherTaskManagerWorker = new ImmediateWorker();
        try (RabbitMQWorkQueue otherWorkQueue = new RabbitMQWorkQueue(otherTaskManagerWorker, rabbitMQExtension.getRabbitChannelPool(), serializer)) {
            otherWorkQueue.start();

            IntStream.range(0, 9)
                .forEach(ignoredIndex -> testee.submit(TASK_WITH_ID_2));

            await().atMost(FIVE_HUNDRED_MILLISECONDS).until(() -> worker.results.size() == 10);
            assertThat(otherTaskManagerWorker.tasks).isEmpty();
        }
    }

    @Test
    void givenANonDeserializableTaskItShouldBeFlaggedAsFailedAndItDoesNotPreventFollowingTasks() throws InterruptedException {
        Task task = new TestTask(42);
        TaskId taskId = TaskId.fromString("4bf6d081-aa30-11e9-bf6c-2d3b9e84aafd");
        TaskWithId taskWithId = new TaskWithId(taskId, task);

        ImmediateWorker otherTaskManagerWorker = new ImmediateWorker();
        JsonTaskSerializer otherTaskSerializer = JsonTaskSerializer.of(TestTaskDTOModules.TEST_TYPE);
        try (RabbitMQWorkQueue otherWorkQueue = new RabbitMQWorkQueue(otherTaskManagerWorker, rabbitMQExtension.getRabbitChannelPool(), otherTaskSerializer)) {
            //wait to be sur that the first workqueue has subscribed as an exclusive consumer of the RabbitMQ queue.
            Thread.sleep(200);
            otherWorkQueue.start();

            otherWorkQueue.submit(taskWithId);

            await().atMost(FIVE_HUNDRED_MILLISECONDS).until(() -> worker.failedTasks.size() == 1);
            assertThat(worker.failedTasks).containsExactly(taskWithId.getId());

            testee.submit(TASK_WITH_ID);
            await().atMost(FIVE_HUNDRED_MILLISECONDS).until(() -> worker.results.size() == 1);
            assertThat(worker.tasks).containsExactly(TASK_WITH_ID);
        }
    }

    @Test
    void tasksShouldBeConsumedSequentially() {
        Task task1 = new CompletedTask();
        TaskId taskId1 = TaskId.fromString("1111d081-aa30-11e9-bf6c-2d3b9e84aafd");
        TaskWithId taskWithId1 = new TaskWithId(taskId1, task1);

        Task task2 = new CompletedTask();
        TaskId taskId2 = TaskId.fromString("2222d082-aa30-22e9-bf6c-2d3b9e84aafd");
        TaskWithId taskWithId2 = new TaskWithId(taskId2, task2);

        when(worker.executeTask(taskWithId1)).then(answer -> {
            TimeUnit.MINUTES.sleep(2);
            return Mono.just(Task.Result.COMPLETED);
        });

        testee.submit(taskWithId1);
        testee.submit(taskWithId2);

        verify(worker, timeout(100)).executeTask(taskWithId1);
        verifyNoMoreInteractions(worker);
    }
}
