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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.CassandraZonedDateTimeModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.backends.rabbitmq.RabbitMQManagementAPI;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.eventsourcing.eventstore.JsonEventSerializer;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStoreExtension;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStoreModule;
import org.apache.james.eventsourcing.eventstore.dto.EventDTO;
import org.apache.james.eventsourcing.eventstore.dto.EventDTOModule;
import org.apache.james.json.DTOConverter;
import org.apache.james.server.task.json.JsonTaskAdditionalInformationSerializer;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.server.task.json.dto.MemoryReferenceTaskStore;
import org.apache.james.server.task.json.dto.MemoryReferenceWithCounterTaskAdditionalInformationDTO;
import org.apache.james.server.task.json.dto.MemoryReferenceWithCounterTaskStore;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.server.task.json.dto.TestTaskDTOModules;
import org.apache.james.task.CountDownLatchExtension;
import org.apache.james.task.Hostname;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskManagerContract;
import org.apache.james.task.eventsourcing.EventSourcingTaskManager;
import org.apache.james.task.eventsourcing.TaskExecutionDetailsProjection;
import org.apache.james.task.eventsourcing.cassandra.CassandraTaskExecutionDetailsProjection;
import org.apache.james.task.eventsourcing.cassandra.CassandraTaskExecutionDetailsProjectionDAO;
import org.apache.james.task.eventsourcing.cassandra.CassandraTaskExecutionDetailsProjectionModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

class DistributedTaskManagerWithQuorumQueueTest implements TaskManagerContract {
    private static final AdditionalInformationDTOModule<?, ?> ADDITIONAL_INFORMATION_MODULE = MemoryReferenceWithCounterTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    private static final JsonTaskAdditionalInformationSerializer JSON_TASK_ADDITIONAL_INFORMATION_SERIALIZER = JsonTaskAdditionalInformationSerializer.of(ADDITIONAL_INFORMATION_MODULE);
    private static final DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> TASK_ADDITIONAL_INFORMATION_DTO_CONVERTER = DTOConverter.of(ADDITIONAL_INFORMATION_MODULE);
    private static final Hostname HOSTNAME = new Hostname("foo");

    @RegisterExtension
    static final RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.WEAK);

    @RegisterExtension
    static final CassandraClusterExtension CASSANDRA_CLUSTER = new CassandraClusterExtension(
        CassandraModule.aggregateModules(
            CassandraSchemaVersionModule.MODULE,
            CassandraEventStoreModule.MODULE(),
            CassandraZonedDateTimeModule.MODULE,
            CassandraTaskExecutionDetailsProjectionModule.MODULE()));

    MemoryReferenceTaskStore memoryReferenceTaskStore = new MemoryReferenceTaskStore();
    MemoryReferenceWithCounterTaskStore memoryReferenceWithCounterTaskStore = new MemoryReferenceWithCounterTaskStore();

    ImmutableSet<TaskDTOModule<?, ?>> taskDTOModules =
        ImmutableSet.of(
            DistributedTaskManagerTest.CassandraExecutingTask.module(CASSANDRA_CLUSTER.getCassandraCluster().getConf()),
            TestTaskDTOModules.FAILS_DESERIALIZATION_TASK_MODULE,
            TestTaskDTOModules.COMPLETED_TASK_MODULE,
            TestTaskDTOModules.FAILED_TASK_MODULE,
            TestTaskDTOModules.THROWING_TASK_MODULE,
            TestTaskDTOModules.MEMORY_REFERENCE_TASK_MODULE.apply(memoryReferenceTaskStore),
            TestTaskDTOModules.MEMORY_REFERENCE_WITH_COUNTER_TASK_MODULE.apply(memoryReferenceWithCounterTaskStore));

    JsonTaskSerializer taskSerializer = new JsonTaskSerializer(taskDTOModules);

    DTOConverter<Task, TaskDTO> taskDTOConverter = new DTOConverter<>(taskDTOModules);

    Set<EventDTOModule<? extends Event, ? extends EventDTO>> eventDtoModule = TasksSerializationModule.list(taskSerializer, TASK_ADDITIONAL_INFORMATION_DTO_CONVERTER, taskDTOConverter);

    @RegisterExtension
    CassandraEventStoreExtension eventStoreExtension = new CassandraEventStoreExtension(CASSANDRA_CLUSTER,
        JsonEventSerializer.forModules(eventDtoModule)
            .withNestedTypeModules(
                Sets.union(
                    ImmutableSet.of(ADDITIONAL_INFORMATION_MODULE),
                    taskDTOModules
                )));

    @RegisterExtension
    CountDownLatchExtension countDownLatchExtension = new CountDownLatchExtension();

    DistributedTaskManagerTest.TrackedRabbitMQWorkQueueSupplier workQueueSupplier;
    EventStore eventStore;
    List<RabbitMQTerminationSubscriber> terminationSubscribers;
    TaskExecutionDetailsProjection executionDetailsProjection;
    JsonEventSerializer eventSerializer;

    @BeforeEach
    void setUp(EventStore eventStore) throws Exception {
        memoryReferenceTaskStore = new MemoryReferenceTaskStore();
        memoryReferenceWithCounterTaskStore = new MemoryReferenceWithCounterTaskStore();
        CassandraCluster cassandra = CASSANDRA_CLUSTER.getCassandraCluster();
        CassandraTaskExecutionDetailsProjectionDAO projectionDAO = new CassandraTaskExecutionDetailsProjectionDAO(cassandra.getConf(), cassandra.getTypesProvider(), JSON_TASK_ADDITIONAL_INFORMATION_SERIALIZER);
        this.executionDetailsProjection = new CassandraTaskExecutionDetailsProjection(projectionDAO);
        this.workQueueSupplier = new DistributedTaskManagerTest.TrackedRabbitMQWorkQueueSupplier(rabbitMQExtension.getSender(), rabbitMQExtension.getReceiverProvider(), taskSerializer);
        this.eventStore = eventStore;
        this.terminationSubscribers = new ArrayList<>();
        this.eventSerializer = JsonEventSerializer.forModules(eventDtoModule)
            .withNestedTypeModules(
                Sets.union(
                    ImmutableSet.of(ADDITIONAL_INFORMATION_MODULE),
                    taskDTOModules));
    }

    @AfterEach
    void tearDown() throws Exception {
        terminationSubscribers.forEach(RabbitMQTerminationSubscriber::close);
        workQueueSupplier.stopWorkQueues();
        RabbitMQManagementAPI managementAPI = rabbitMQExtension.managementAPI();
        managementAPI.listQueues()
            .forEach(queue -> managementAPI.deleteQueue("/", queue.getName()));
    }

    public EventSourcingTaskManager taskManager() throws Exception {
        return taskManager(HOSTNAME);
    }

    EventSourcingTaskManager taskManager(Hostname hostname) throws Exception {
        RabbitMQTerminationSubscriber terminationSubscriber = new RabbitMQTerminationSubscriber(TerminationQueueName.generate(), rabbitMQExtension.getSender(),
            rabbitMQExtension.getReceiverProvider(),
            eventSerializer, rabbitMQExtension.getRabbitMQ().withQuorumQueueConfiguration());
        terminationSubscribers.add(terminationSubscriber);
        terminationSubscriber.start();
        return new EventSourcingTaskManager(workQueueSupplier, eventStore, executionDetailsProjection, hostname, terminationSubscriber);
    }

}
