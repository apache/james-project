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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.server.task.json.TestTask;
import org.apache.james.server.task.json.dto.TestTaskDTOModules;
import org.apache.james.task.CompletedTask;
import org.apache.james.task.Task;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManagerWorker;
import org.apache.james.task.TaskWithId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.verification.Timeout;

import reactor.core.publisher.Mono;

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
    private TaskManagerWorker taskManagerWorker;
    private JsonTaskSerializer taskSerializer;

    @BeforeEach
    void setUp() {
        taskManagerWorker = mock(TaskManagerWorker.class);
        when(taskManagerWorker.executeTask(TASK_WITH_ID)).thenReturn(Mono.just(Task.Result.COMPLETED));
        when(taskManagerWorker.executeTask(TASK_WITH_ID_2)).thenReturn(Mono.just(Task.Result.COMPLETED));
        taskSerializer = new JsonTaskSerializer(TestTaskDTOModules.COMPLETED_TASK_MODULE);
        testee = new RabbitMQWorkQueue(taskManagerWorker, rabbitMQExtension.getRabbitConnectionPool(), taskSerializer);
        testee.start();
    }

    @AfterEach
    void tearDown() {
        testee.close();
    }

    @Test
    void workerShouldConsumeSubmittedTask() {
        testee.submit(TASK_WITH_ID);

        ArgumentCaptor<TaskWithId> taskWithIdCaptor = ArgumentCaptor.forClass(TaskWithId.class);
        verify(taskManagerWorker, timeout(1000)).executeTask(taskWithIdCaptor.capture());

        TaskWithId actualTaskWithId = taskWithIdCaptor.getValue();
        assertThat(actualTaskWithId.getId()).isEqualTo(TASK_ID);
        assertThat(actualTaskWithId.getTask().type()).isEqualTo(TASK.type());
    }

    @Test
    void workerShouldConsumeTwoSubmittedTask() {
        testee.submit(TASK_WITH_ID);
        testee.submit(TASK_WITH_ID_2);

        ArgumentCaptor<TaskWithId> taskWithIdCaptor = ArgumentCaptor.forClass(TaskWithId.class);
        verify(taskManagerWorker, new Timeout(1000, times(2))).executeTask(taskWithIdCaptor.capture());

        TaskWithId actualTaskWithId = taskWithIdCaptor.getAllValues().get(0);
        assertThat(actualTaskWithId.getId()).isEqualTo(TASK_ID);
        assertThat(actualTaskWithId.getTask().type()).isEqualTo(TASK.type());

        TaskWithId actualSecondTaskWithId = taskWithIdCaptor.getAllValues().get(1);
        assertThat(actualSecondTaskWithId.getId()).isEqualTo(TASK_ID_2);
        assertThat(actualSecondTaskWithId.getTask().type()).isEqualTo(TASK2.type());
    }

    @Test
    void givenTwoWorkQueuesOnlyTheFirstOneIsConsumingTasks() {
        testee.submit(TASK_WITH_ID);

        TaskManagerWorker otherTaskManagerWorker = mock(TaskManagerWorker.class);
        RabbitMQWorkQueue otherWorkQueue = new RabbitMQWorkQueue(otherTaskManagerWorker, rabbitMQExtension.getRabbitConnectionPool(), taskSerializer);
        otherWorkQueue.start();

        IntStream.range(0, 9)
            .forEach(ignoredIndex -> testee.submit(TASK_WITH_ID_2));

        verify(taskManagerWorker, new Timeout(1000, times(10))).executeTask(any());

        verify(otherTaskManagerWorker, new Timeout(1000, times(0))).executeTask(any());
    }

    @Test
    void givenANonDeserializableTaskItShouldBeFlaggedAsFailedAndItDoesNotPreventFollowingTasks() throws InterruptedException {
        Task task = new TestTask(42);
        TaskId taskId = TaskId.fromString("4bf6d081-aa30-11e9-bf6c-2d3b9e84aafd");
        TaskWithId taskWithId = new TaskWithId(taskId, task);

        TaskManagerWorker otherTaskManagerWorker = mock(TaskManagerWorker.class);
        JsonTaskSerializer otherTaskSerializer = new JsonTaskSerializer(TestTaskDTOModules.TEST_TYPE);
        RabbitMQWorkQueue otherWorkQueue = new RabbitMQWorkQueue(otherTaskManagerWorker, rabbitMQExtension.getRabbitConnectionPool(), otherTaskSerializer);
        //wait to be sur that the first workqueue has subscribed as an exclusive consumer of the RabbitMQ queue.
        Thread.sleep(200);
        otherWorkQueue.start();

        otherWorkQueue.submit(taskWithId);

        verify(taskManagerWorker, new Timeout(100, times(0))).executeTask(any());
        verify(taskManagerWorker, timeout(100)).fail(eq(taskId), eq(Optional.empty()), any(), any());

        testee.submit(TASK_WITH_ID);
        verify(taskManagerWorker, timeout(100)).executeTask(any());
    }

    @Test
    void tasksShouldBeConsumedSequentially() {
        Task task1 = new CompletedTask();
        TaskId taskId1 = TaskId.fromString("1111d081-aa30-11e9-bf6c-2d3b9e84aafd");
        TaskWithId taskWithId1 = new TaskWithId(taskId1, task1);

        Task task2 = new CompletedTask();
        TaskId taskId2 = TaskId.fromString("2222d082-aa30-22e9-bf6c-2d3b9e84aafd");
        TaskWithId taskWithId2 = new TaskWithId(taskId2, task2);

        when(taskManagerWorker.executeTask(taskWithId1)).then(answer -> {
            TimeUnit.MINUTES.sleep(2);
            return Mono.just(Task.Result.COMPLETED);
        });

        testee.submit(taskWithId1);
        testee.submit(taskWithId2);

        verify(taskManagerWorker, timeout(100)).executeTask(taskWithId1);
        verifyNoMoreInteractions(taskManagerWorker);
    }
}
