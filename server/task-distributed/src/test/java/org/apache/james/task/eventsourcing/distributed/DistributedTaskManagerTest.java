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
import org.apache.james.server.task.json.dto.TestTaskDTOModules;
import org.apache.james.task.CompletedTask;
import org.apache.james.task.SerialTaskManagerWorker;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.task.TaskManagerWorker;
import org.apache.james.task.eventsourcing.EventSourcingTaskManager;
import org.apache.james.task.eventsourcing.Hostname;
import org.apache.james.task.eventsourcing.TaskExecutionDetailsProjection;
import org.apache.james.task.eventsourcing.WorkQueueSupplier;
import org.apache.james.task.eventsourcing.WorkerStatusListener;
import org.apache.james.task.eventsourcing.cassandra.CassandraTaskExecutionDetailsProjection;
import org.apache.james.task.eventsourcing.cassandra.CassandraTaskExecutionDetailsProjectionDAO;
import org.apache.james.task.eventsourcing.cassandra.CassandraTaskExecutionDetailsProjectionModule;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.steveash.guavate.Guavate;

class DistributedTaskManagerTest {
    private static final JsonTaskSerializer TASK_SERIALIZER = new JsonTaskSerializer(TestTaskDTOModules.COMPLETED_TASK_MODULE);

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

    @Test
    void givenOneEventStoreTwoEventTaskManagersShareTheSameEvents(EventStore eventStore) {
        CassandraCluster cassandra = cassandraCluster.getCassandraCluster();
        CassandraTaskExecutionDetailsProjectionDAO cassandraTaskExecutionDetailsProjectionDAO = new CassandraTaskExecutionDetailsProjectionDAO(cassandra.getConf(), cassandra.getTypesProvider());
        TaskExecutionDetailsProjection executionDetailsProjection = new CassandraTaskExecutionDetailsProjection(cassandraTaskExecutionDetailsProjectionDAO);

        WorkQueueSupplier workQueueSupplier = eventSourcingSystem -> {
            WorkerStatusListener listener = new WorkerStatusListener(eventSourcingSystem);
            TaskManagerWorker worker = new SerialTaskManagerWorker(listener);
            RabbitMQWorkQueue rabbitMQWorkQueue = new RabbitMQWorkQueue(worker, rabbitMQExtension.getRabbitConnectionPool(), TASK_SERIALIZER);
            rabbitMQWorkQueue.start();
            return rabbitMQWorkQueue;

        };
        TaskManager taskManager1 = new EventSourcingTaskManager(workQueueSupplier, eventStore, executionDetailsProjection, new Hostname("foo"));
        TaskManager taskManager2 = new EventSourcingTaskManager(workQueueSupplier, eventStore, executionDetailsProjection, new Hostname("bar"));

        TaskId taskId = taskManager1.submit(new CompletedTask());
        Awaitility.await()
            .atMost(Duration.FIVE_SECONDS)
            .pollInterval(100L, TimeUnit.MILLISECONDS)
            .until(() -> taskManager1.await(taskId).getStatus() == TaskManager.Status.COMPLETED);

        TaskExecutionDetails detailsFromTaskManager1 = taskManager1.getExecutionDetails(taskId);
        TaskExecutionDetails detailsFromTaskManager2 = taskManager2.getExecutionDetails(taskId);
        assertThat(detailsFromTaskManager1).isEqualTo(detailsFromTaskManager2);
    }
}
