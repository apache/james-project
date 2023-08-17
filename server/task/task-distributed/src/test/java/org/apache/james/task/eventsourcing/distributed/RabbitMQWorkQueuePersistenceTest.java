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
package org.apache.james.task.eventsourcing.distributed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.mockito.Mockito.spy;

import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.server.task.json.dto.MemoryReferenceTaskStore;
import org.apache.james.server.task.json.dto.TestTaskDTOModules;
import org.apache.james.task.MemoryReferenceTask;
import org.apache.james.task.Task;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskWithId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class RabbitMQWorkQueuePersistenceTest {
    private static final TaskId TASK_ID = TaskId.fromString("2c7f4081-aa30-11e9-bf6c-2d3b9e84aafd");

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.defaultRabbitMQ()
        .restartPolicy(RabbitMQExtension.DockerRestartPolicy.PER_CLASS)
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.WEAK);

    private RabbitMQWorkQueue testee;
    private ImmediateWorker worker;
    private JsonTaskSerializer serializer;

    @BeforeEach
    void setUp() throws Exception {
        worker = spy(new ImmediateWorker());
        serializer = JsonTaskSerializer.of(TestTaskDTOModules.COMPLETED_TASK_MODULE, TestTaskDTOModules.MEMORY_REFERENCE_TASK_MODULE.apply(new MemoryReferenceTaskStore()));
        testee = new RabbitMQWorkQueue(worker, rabbitMQExtension.getSender(), rabbitMQExtension.getReceiverProvider(), serializer, RabbitMQWorkQueueConfiguration$.MODULE$.enabled(), CancelRequestQueueName.generate(), rabbitMQExtension.getRabbitMQ().getConfiguration());
        //declare the queue but do not start consuming from it
        testee.declareQueue();
    }

    @AfterEach
    void tearDown() {
        testee.close();
    }

    /**
     * submit on a workqueue which do not consume
     * restart rabbit
     * start a workqueue which consume messages
     * verify that the message is treated
     */
    @Test
    void submittedMessageShouldSurviveRabbitMQRestart() throws Exception {
        Task task = new MemoryReferenceTask(() -> Task.Result.COMPLETED);
        TaskWithId taskWithId = new TaskWithId(TASK_ID, task);

        testee.submit(taskWithId);

        //wait for submit to be effective
        Thread.sleep(500);
        testee.close();

        rabbitMQExtension.getRabbitMQ().restart();

        startNewConsumingWorkqueue();

        await().atMost(ONE_MINUTE).until(() -> !worker.results.isEmpty());

        assertThat(worker.tasks).containsExactly(taskWithId);
        assertThat(worker.results).containsExactly(Task.Result.COMPLETED);
    }

    private void startNewConsumingWorkqueue() throws Exception {
        worker = spy(new ImmediateWorker());
        testee = new RabbitMQWorkQueue(worker, rabbitMQExtension.getSender(), rabbitMQExtension.getReceiverProvider(), serializer, RabbitMQWorkQueueConfiguration$.MODULE$.enabled(), CancelRequestQueueName.generate(), rabbitMQExtension.getRabbitMQ().getConfiguration());
        testee.start();
    }
}
