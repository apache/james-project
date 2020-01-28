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

package org.apache.james.task.eventsourcing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Duration.ONE_HUNDRED_MILLISECONDS;

import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.eventsourcing.eventstore.memory.InMemoryEventStore;
import org.apache.james.task.CountDownLatchExtension;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryReferenceTask;
import org.apache.james.task.MemoryWorkQueue;
import org.apache.james.task.SerialTaskManagerWorker;
import org.apache.james.task.Task;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.task.TaskManagerContract;
import org.apache.james.task.TaskManagerWorker;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(CountDownLatchExtension.class)
class EventSourcingTaskManagerTest implements TaskManagerContract {
    ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();


    private static final Hostname HOSTNAME = new Hostname("foo");
    private EventSourcingTaskManager taskManager;
    private EventStore eventStore;

    @BeforeEach
    void setUp() {
        eventStore = new InMemoryEventStore();
        TaskExecutionDetailsProjection executionDetailsProjection = new MemoryTaskExecutionDetailsProjection();
        WorkQueueSupplier workQueueSupplier = eventSourcingSystem -> {
            WorkerStatusListener listener = new WorkerStatusListener(eventSourcingSystem);
            TaskManagerWorker worker = new SerialTaskManagerWorker(listener, UPDATE_INFORMATION_POLLING_INTERVAL);
            return new MemoryWorkQueue(worker);
        };
        taskManager = new EventSourcingTaskManager(workQueueSupplier, eventStore, executionDetailsProjection, HOSTNAME, new MemoryTerminationSubscriber());
    }

    @AfterEach
    void tearDown() {
        taskManager.close();
    }

    @Override
    public TaskManager taskManager() {
        return taskManager;
    }

    @Test
    void createdTaskShouldKeepOriginHostname() {
        TaskId taskId = taskManager.submit(new MemoryReferenceTask(() -> Task.Result.COMPLETED));
        TaskAggregateId aggregateId = new TaskAggregateId(taskId);
        assertThat(eventStore.getEventsOfAggregate(aggregateId).getEventsJava())
                .filteredOn(event -> event instanceof Created)
                .extracting("hostname")
                .containsOnly(HOSTNAME);
    }

    @Test
    void startedTaskShouldKeepOriginHostname() {
        TaskId taskId = taskManager.submit(new MemoryReferenceTask(() -> Task.Result.COMPLETED));
        TaskAggregateId aggregateId = new TaskAggregateId(taskId);

        CALMLY_AWAIT.untilAsserted(() ->
            assertThat(eventStore.getEventsOfAggregate(aggregateId).getEventsJava())
                .filteredOn(event -> event instanceof Started)
                .extracting("hostname")
                .containsOnly(HOSTNAME));
    }

    @Test
    void cancelRequestedTaskShouldKeepOriginHostname() {
        TaskId taskId = taskManager.submit(new MemoryReferenceTask(() -> {
            Thread.sleep(100);
            return Task.Result.COMPLETED;
        }));
        taskManager.cancel(taskId);

        TaskAggregateId aggregateId = new TaskAggregateId(taskId);
        CALMLY_AWAIT.untilAsserted(() ->
            assertThat(eventStore.getEventsOfAggregate(aggregateId).getEventsJava())
                .filteredOn(event -> event instanceof CancelRequested)
                .extracting("hostname")
                .containsOnly(HOSTNAME));
    }
}
