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

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.james.backend.rabbitmq.RabbitMQExtension;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.CassandraZonedDateTimeModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStoreExtension;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStoreModule;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTOModule;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.server.task.json.dto.MemoryReferenceTaskStore;
import org.apache.james.server.task.json.dto.TestTaskDTOModules;
import org.apache.james.task.CompletedTask;
import org.apache.james.task.CountDownLatchExtension;
import org.apache.james.task.MemoryReferenceTask;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.task.TaskManagerContract;
import org.apache.james.task.eventsourcing.EventSourcingTaskManager;
import org.apache.james.task.eventsourcing.Hostname;
import org.apache.james.task.eventsourcing.TaskExecutionDetailsProjection;
import org.apache.james.task.eventsourcing.WorkQueueSupplier;
import org.apache.james.task.eventsourcing.cassandra.CassandraTaskExecutionDetailsProjection;
import org.apache.james.task.eventsourcing.cassandra.CassandraTaskExecutionDetailsProjectionDAO;
import org.apache.james.task.eventsourcing.cassandra.CassandraTaskExecutionDetailsProjectionModule;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.steveash.guavate.Guavate;

@ExtendWith(CountDownLatchExtension.class)
class DistributedTaskManagerTest implements TaskManagerContract {

    private static final JsonTaskSerializer TASK_SERIALIZER = new JsonTaskSerializer(
        TestTaskDTOModules.COMPLETED_TASK_MODULE,
        TestTaskDTOModules.FAILED_TASK_MODULE,
        TestTaskDTOModules.THROWING_TASK_MODULE,
        TestTaskDTOModules.MEMORY_REFERENCE_TASK_MODULE.apply(new MemoryReferenceTaskStore()));

    private static final Hostname HOSTNAME = new Hostname("foo");
    private static final Hostname HOSTNAME_2 = new Hostname("bar");
    private static final Set<EventDTOModule> MODULES = TasksSerializationModule.MODULES.apply(TASK_SERIALIZER).stream().collect(Guavate.toImmutableSet());

    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(
            CassandraSchemaVersionModule.MODULE,
            CassandraEventStoreModule.MODULE,
            CassandraZonedDateTimeModule.MODULE,
            CassandraTaskExecutionDetailsProjectionModule.MODULE()));

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ();

    @RegisterExtension
    static CassandraEventStoreExtension eventStoreExtension = new CassandraEventStoreExtension(cassandraCluster, MODULES);

    private final CassandraCluster cassandra = cassandraCluster.getCassandraCluster();
    private final CassandraTaskExecutionDetailsProjectionDAO cassandraTaskExecutionDetailsProjectionDAO = new CassandraTaskExecutionDetailsProjectionDAO(cassandra.getConf(), cassandra.getTypesProvider());
    private final TaskExecutionDetailsProjection executionDetailsProjection = new CassandraTaskExecutionDetailsProjection(cassandraTaskExecutionDetailsProjectionDAO);

    private WorkQueueSupplier workQueueSupplier;
    private EventStore eventStore;

    @BeforeEach
    void setUp(EventStore eventStore) {
        workQueueSupplier = new RabbitMQWorkQueueSupplier(rabbitMQExtension.getRabbitConnectionPool(), TASK_SERIALIZER);
        this.eventStore = eventStore;
    }

    public TaskManager taskManager() {
        return new EventSourcingTaskManager(workQueueSupplier, eventStore, executionDetailsProjection, HOSTNAME);
    }

    @Test
    void givenOneEventStoreTwoEventTaskManagersShareTheSameEvents() {
        TaskManager taskManager1 = taskManager();
        TaskManager taskManager2 = new EventSourcingTaskManager(workQueueSupplier, eventStore, executionDetailsProjection, HOSTNAME_2);

        TaskId taskId = taskManager1.submit(new CompletedTask());
        Awaitility.await()
            .atMost(Duration.FIVE_SECONDS)
            .pollInterval(100L, TimeUnit.MILLISECONDS)
            .until(() -> taskManager1.await(taskId).getStatus() == TaskManager.Status.COMPLETED);

        TaskExecutionDetails detailsFromTaskManager1 = taskManager1.getExecutionDetails(taskId);
        TaskExecutionDetails detailsFromTaskManager2 = taskManager2.getExecutionDetails(taskId);
        assertThat(detailsFromTaskManager1).isEqualTo(detailsFromTaskManager2);
    }

    @Test
    @Disabled("Cancelling is not supported yet")
    public void aWaitingTaskShouldBeCancelled(CountDownLatch countDownLatch) {
    }

    @Test
    @Disabled("Cancelling is not supported yet")
    public void getStatusShouldBeCancelledWhenCancelled(CountDownLatch countDownLatch) {
    }

    @Test
    void givenTwoTaskManagersAndTwoTasksOnlyOneTaskShouldRunAtTheSameTime() throws InterruptedException {
        CountDownLatch waitingForFirstTaskLatch = new CountDownLatch(1);

        TaskManager taskManager1 = taskManager();
        TaskManager taskManager2 = taskManager();

        taskManager1.submit(new MemoryReferenceTask(() -> {
            waitingForFirstTaskLatch.await();
            return Task.Result.COMPLETED;
        }));
        TaskId waitingTaskId = taskManager1.submit(new CompletedTask());

        awaitUntilTaskHasStatus(waitingTaskId, TaskManager.Status.WAITING, taskManager2);
        waitingForFirstTaskLatch.countDown();

        Awaitility.await()
            .atMost(Duration.ONE_SECOND)
            .pollInterval(100L, TimeUnit.MILLISECONDS)
            .until(() -> taskManager1.await(waitingTaskId).getStatus() == TaskManager.Status.COMPLETED);
    }

    @Test
        // FIXME it's currently dependent of the implementation of the sequential TaskManager with the exclusive RabbitMQ consumer
        // once we store the node where the event have been created/started/completed we should rewrite it with this information.
    void givenTwoTaskManagerATaskSubmittedOnOneCouldBeRunOnTheOther() throws InterruptedException {
        TaskManager taskManager1 = taskManager();
        Thread.sleep(100); // FIXME used to ensure that taskManager1 is the worker consuming from rabbit
        TaskManager taskManager2 = taskManager();

        TaskId taskId = taskManager2.submit(new CompletedTask());

        Awaitility.await()
            .atMost(Duration.ONE_SECOND)
            .pollInterval(100L, TimeUnit.MILLISECONDS)
            .until(() -> taskManager1.await(taskId).getStatus() == TaskManager.Status.COMPLETED);
    }
}
