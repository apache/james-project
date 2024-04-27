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

import static com.rabbitmq.client.MessageProperties.PERSISTENT_TEXT_PLAIN;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.backends.cassandra.Scenario.Builder.executeNormally;
import static org.apache.james.backends.cassandra.Scenario.Builder.fail;
import static org.apache.james.task.TaskManager.Status.CANCELLED;
import static org.apache.james.task.eventsourcing.distributed.RabbitMQWorkQueue.EXCHANGE_NAME;
import static org.apache.james.task.eventsourcing.distributed.RabbitMQWorkQueue.ROUTING_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.awaitility.Durations.ONE_SECOND;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.Scenario;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.CassandraZonedDateTimeModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.backends.rabbitmq.RabbitMQManagementAPI;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.EventSourcingSystem;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.eventsourcing.eventstore.JsonEventSerializer;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStoreExtension;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStoreModule;
import org.apache.james.eventsourcing.eventstore.dto.EventDTO;
import org.apache.james.eventsourcing.eventstore.dto.EventDTOModule;
import org.apache.james.json.DTOConverter;
import org.apache.james.json.DTOModule;
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
import org.apache.james.task.CompletedTask;
import org.apache.james.task.CountDownLatchExtension;
import org.apache.james.task.FailedTask;
import org.apache.james.task.FailsDeserializationTask;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryReferenceTask;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.task.TaskManagerContract;
import org.apache.james.task.TaskType;
import org.apache.james.task.TaskWithId;
import org.apache.james.task.WorkQueue;
import org.apache.james.task.eventsourcing.EventSourcingTaskManager;
import org.apache.james.task.eventsourcing.TaskExecutionDetailsProjection;
import org.apache.james.task.eventsourcing.WorkQueueSupplier;
import org.apache.james.task.eventsourcing.cassandra.CassandraTaskExecutionDetailsProjection;
import org.apache.james.task.eventsourcing.cassandra.CassandraTaskExecutionDetailsProjectionDAO;
import org.apache.james.task.eventsourcing.cassandra.CassandraTaskExecutionDetailsProjectionModule;
import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.CqlSession;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.rabbitmq.client.AMQP;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.Sender;

class DistributedTaskManagerTest implements TaskManagerContract {

    private static final byte[] BAD_PAYLOAD = "BAD_PAYLOAD!".getBytes(UTF_8);

    static class TrackedRabbitMQWorkQueueSupplier implements WorkQueueSupplier {
        private final List<RabbitMQWorkQueue> workQueues;
        private final RabbitMQWorkQueueSupplier supplier;

        TrackedRabbitMQWorkQueueSupplier(Sender sender, ReceiverProvider receiverProvider, JsonTaskSerializer taskSerializer) throws Exception {
            workQueues = new ArrayList<>();
            supplier = new RabbitMQWorkQueueSupplier(sender, receiverProvider, taskSerializer, CancelRequestQueueName.generate(), RabbitMQWorkQueueConfiguration$.MODULE$.enabled(), rabbitMQExtension.getRabbitMQ().getConfiguration());
        }

        @Override
        public WorkQueue apply(EventSourcingSystem eventSourcingSystem) {
            RabbitMQWorkQueue workQueue = supplier.apply(eventSourcingSystem, UPDATE_INFORMATION_POLLING_INTERVAL);
            workQueue.start();
            workQueues.add(workQueue);
            return workQueue;
        }

        void stopWorkQueues() {
            workQueues.forEach(RabbitMQWorkQueue::close);
            workQueues.clear();
        }
    }

    public static final AdditionalInformationDTOModule<?, ?> ADDITIONAL_INFORMATION_MODULE = MemoryReferenceWithCounterTaskAdditionalInformationDTO.SERIALIZATION_MODULE;

    static final JsonTaskAdditionalInformationSerializer JSON_TASK_ADDITIONAL_INFORMATION_SERIALIZER = JsonTaskAdditionalInformationSerializer.of(ADDITIONAL_INFORMATION_MODULE);
    static final DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> TASK_ADDITIONAL_INFORMATION_DTO_CONVERTER = DTOConverter.of(ADDITIONAL_INFORMATION_MODULE);
    static final Hostname HOSTNAME = new Hostname("foo");
    static final Hostname HOSTNAME_2 = new Hostname("bar");
    static final TaskId TASK_ID = TaskId.fromString("2c7f4081-aa30-11e9-bf6c-2d3b9e84aafd");
    static final Task TASK = new CompletedTask();
    static final TaskWithId TASK_WITH_ID = new TaskWithId(TASK_ID, TASK);

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
            CassandraExecutingTask.module(CASSANDRA_CLUSTER.getCassandraCluster().getConf()),
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

    TrackedRabbitMQWorkQueueSupplier workQueueSupplier;
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
        this.workQueueSupplier = new TrackedRabbitMQWorkQueueSupplier(rabbitMQExtension.getSender(), rabbitMQExtension.getReceiverProvider(), taskSerializer);
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
            eventSerializer, rabbitMQExtension.getRabbitMQ().getConfiguration());
        terminationSubscribers.add(terminationSubscriber);
        terminationSubscriber.start();
        return new EventSourcingTaskManager(workQueueSupplier, eventStore, executionDetailsProjection, hostname, terminationSubscriber);
    }

    @Test
    void badPayloadShouldNotAffectTaskManagerOnCancelTask() throws Exception {
        TaskManager taskManager = taskManager(HOSTNAME);
        CountDownLatch latch = new CountDownLatch(1);
        TaskId id = taskManager.submit(new MemoryReferenceTask(() -> {
            latch.await();
            return Task.Result.COMPLETED;
        }));

        rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(EXCHANGE_NAME, ROUTING_KEY, BAD_PAYLOAD)))
            .block();

        taskManager.cancel(id);
        Awaitility.await()
            .until(() -> taskManager.getExecutionDetails(id).getStatus().equals(CANCELLED));
        latch.countDown();
        taskManager.await(id, TIMEOUT);
        assertThat(taskManager.getExecutionDetails(id).getStatus())
            .isEqualTo(CANCELLED);
    }

    @Test
    void badPayloadsShouldNotAffectTaskManagerOnCancelTask() throws Exception {
        TaskManager taskManager = taskManager(HOSTNAME);
        CountDownLatch latch = new CountDownLatch(1);
        TaskId id = taskManager.submit(new MemoryReferenceTask(() -> {
            latch.await();
            return Task.Result.COMPLETED;
        }));

        IntStream.range(0, 100)
            .forEach(i -> rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(EXCHANGE_NAME, ROUTING_KEY, BAD_PAYLOAD)))
            .block());

        taskManager.cancel(id);
        Awaitility.await()
            .until(() -> taskManager.getExecutionDetails(id).getStatus().equals(CANCELLED));
        latch.countDown();
        taskManager.await(id, TIMEOUT);
        assertThat(taskManager.getExecutionDetails(id).getStatus())
            .isEqualTo(CANCELLED);
    }

    @Test
    void badPayloadShouldNotAffectTaskManagerOnCompleteTask() throws Exception {
        TaskManager taskManager = taskManager(HOSTNAME);
        CountDownLatch latch = new CountDownLatch(1);
        TaskId id = taskManager.submit(new MemoryReferenceTask(() -> {
            latch.await();
            return Task.Result.COMPLETED;
        }));

        rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(EXCHANGE_NAME, ROUTING_KEY, BAD_PAYLOAD)))
            .block();

        latch.countDown();
        taskManager.await(id, TIMEOUT);
        assertThat(taskManager.getExecutionDetails(id).getStatus())
            .isEqualTo(TaskManager.Status.COMPLETED);
    }

    @Test
    void badPayloadsShouldNotAffectTaskManagerOnCompleteTask() throws Exception {
        TaskManager taskManager = taskManager(HOSTNAME);
        CountDownLatch latch = new CountDownLatch(1);
        TaskId id = taskManager.submit(new MemoryReferenceTask(() -> {
            latch.await();
            return Task.Result.COMPLETED;
        }));

        IntStream.range(0, 100)
            .forEach(i -> rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(EXCHANGE_NAME, ROUTING_KEY, BAD_PAYLOAD)))
            .block());

        latch.countDown();
        taskManager.await(id, TIMEOUT);
        assertThat(taskManager.getExecutionDetails(id).getStatus())
            .isEqualTo(TaskManager.Status.COMPLETED);
    }

    @Test
    void givenOneEventStoreTwoEventTaskManagersShareTheSameEvents() throws Exception {
        try (EventSourcingTaskManager taskManager1 = taskManager();
             EventSourcingTaskManager taskManager2 = taskManager(HOSTNAME_2)) {
            TaskId taskId = taskManager1.submit(new CompletedTask());
            Awaitility.await()
                .atMost(FIVE_SECONDS)
                .pollInterval(100L, TimeUnit.MILLISECONDS)
                .until(() -> taskManager1.await(taskId, TIMEOUT).getStatus() == TaskManager.Status.COMPLETED);

            TaskExecutionDetails detailsFromTaskManager1 = taskManager1.getExecutionDetails(taskId);
            TaskExecutionDetails detailsFromTaskManager2 = taskManager2.getExecutionDetails(taskId);
            assertThat(detailsFromTaskManager1).isEqualTo(detailsFromTaskManager2);
        }
    }

    @Test
    void givenTwoTaskManagersAndTwoTaskOnlyOneTaskShouldRunAtTheSameTime() throws Exception {
        CountDownLatch waitingForFirstTaskLatch = new CountDownLatch(1);

        try (EventSourcingTaskManager taskManager1 = taskManager();
             EventSourcingTaskManager taskManager2 = taskManager(HOSTNAME_2)) {

            taskManager1.submit(new MemoryReferenceTask(() -> {
                waitingForFirstTaskLatch.await();
                return Task.Result.COMPLETED;
            }));
            TaskId waitingTaskId = taskManager1.submit(new CompletedTask());

            awaitUntilTaskHasStatus(waitingTaskId, TaskManager.Status.WAITING, taskManager2);
            waitingForFirstTaskLatch.countDown();

            Awaitility.await()
                .atMost(ONE_SECOND)
                .pollInterval(100L, TimeUnit.MILLISECONDS)
                .until(() -> taskManager1.await(waitingTaskId, TIMEOUT).getStatus() == TaskManager.Status.COMPLETED);
        }
    }

    @Test
    void givenTwoTaskManagerATaskSubmittedOnOneCouldBeRunOnTheOther() throws Exception {
        try (EventSourcingTaskManager taskManager1 = taskManager()) {
            Thread.sleep(100);
            try (EventSourcingTaskManager taskManager2 = taskManager(HOSTNAME_2)) {

                TaskId taskId = taskManager2.submit(new CompletedTask());

                Awaitility.await()
                    .atMost(ONE_SECOND)
                    .pollInterval(100L, TimeUnit.MILLISECONDS)
                    .until(() -> taskManager1.await(taskId, TIMEOUT).getStatus() == TaskManager.Status.COMPLETED);

                TaskExecutionDetails executionDetails = taskManager2.getExecutionDetails(taskId);
                assertThat(executionDetails.getSubmittedNode()).isEqualTo(HOSTNAME_2);
                assertThat(executionDetails.getRanNode()).contains(HOSTNAME);
            }
        }
    }

    @Test
    void givenTwoTaskManagerATaskRunningOnOneShouldBeCancellableFromTheOtherOne(CountDownLatch countDownLatch) throws Exception {
        TaskManager taskManager1 = taskManager(HOSTNAME);
        TaskManager taskManager2 = taskManager(HOSTNAME_2);
        TaskId id = taskManager1.submit(new MemoryReferenceTask(() -> {
            countDownLatch.await();
            return Task.Result.COMPLETED;
        }));

        awaitUntilTaskHasStatus(id, TaskManager.Status.IN_PROGRESS, taskManager1);
        Hostname runningNode = taskManager1.getExecutionDetails(id).getRanNode().get();

        Pair<Hostname, TaskManager> remoteTaskManager = getOtherTaskManager(runningNode, Pair.of(HOSTNAME, taskManager1), Pair.of(HOSTNAME_2, taskManager2));
        remoteTaskManager.getValue().cancel(id);

        awaitAtMostTwoSeconds.untilAsserted(() ->
            assertThat(taskManager1.getExecutionDetails(id).getStatus())
                .isIn(CANCELLED, TaskManager.Status.CANCEL_REQUESTED));

        countDownLatch.countDown();

        awaitUntilTaskHasStatus(id, CANCELLED, taskManager1);
        assertThat(taskManager1.getExecutionDetails(id).getStatus())
            .isEqualTo(CANCELLED);

        assertThat(taskManager1.getExecutionDetails(id).getCancelRequestedNode())
            .contains(remoteTaskManager.getKey());
    }

    @Test
    void givenTwoTaskManagersATaskRunningOnOneShouldBeWaitableFromTheOtherOne() throws Exception {
        TaskManager taskManager1 = taskManager(HOSTNAME);
        TaskManager taskManager2 = taskManager(HOSTNAME_2);
        CountDownLatch latch = new CountDownLatch(1);
        TaskId id = taskManager1.submit(new MemoryReferenceTask(() -> {
            latch.await();
            return Task.Result.COMPLETED;
        }));

        awaitUntilTaskHasStatus(id, TaskManager.Status.IN_PROGRESS, taskManager1);
        Hostname runningNode = taskManager1.getExecutionDetails(id).getRanNode().get();

        TaskManager remoteTaskManager = getOtherTaskManager(runningNode, Pair.of(HOSTNAME, taskManager1), Pair.of(HOSTNAME_2, taskManager2)).getValue();

        latch.countDown();
        remoteTaskManager.await(id, TIMEOUT);
        assertThat(taskManager1.getExecutionDetails(id).getStatus())
            .isEqualTo(TaskManager.Status.COMPLETED);
    }

    private Pair<Hostname, TaskManager> getOtherTaskManager(Hostname node, Pair<Hostname, TaskManager> taskManager1, Pair<Hostname, TaskManager> taskManager2) {
        if (node.equals(taskManager1.getKey())) {
            return taskManager2;
        } else {
            return taskManager1;
        }
    }

    @Test
    void givenTwoTaskManagerAndATaskRanPerTaskManagerListingThemOnEachShouldShowBothTasks() throws Exception {
        try (EventSourcingTaskManager taskManager1 = taskManager();
             EventSourcingTaskManager taskManager2 = taskManager(HOSTNAME_2)) {

            TaskId taskId1 = taskManager1.submit(new CompletedTask());
            TaskId taskId2 = taskManager2.submit(new CompletedTask());

            Awaitility.await()
                .atMost(ONE_SECOND)
                .pollInterval(100L, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    List<TaskExecutionDetails> listOnTaskManager1 = taskManager1.list();
                    List<TaskExecutionDetails> listOnTaskManager2 = taskManager2.list();

                    assertThat(listOnTaskManager1)
                        .hasSize(2)
                        .isEqualTo(listOnTaskManager2)
                        .allSatisfy(taskExecutionDetails -> assertThat(taskExecutionDetails.getStatus()).isEqualTo(TaskManager.Status.COMPLETED))
                        .extracting(TaskExecutionDetails::getTaskId)
                        .containsExactlyInAnyOrder(taskId1, taskId2);
                });
        }
    }

    @Test
    void givenTwoTaskManagerIfTheFirstOneIsDownTheSecondOneShouldBeAbleToRunTheRemainingTasks(CountDownLatch countDownLatch) throws Exception {
        try (EventSourcingTaskManager taskManager1 = taskManager();
             EventSourcingTaskManager taskManager2 = taskManager(HOSTNAME_2)) {
            ImmutableBiMap<EventSourcingTaskManager, Hostname> hostNameByTaskManager = ImmutableBiMap.of(taskManager1, HOSTNAME, taskManager2, HOSTNAME_2);
            TaskId firstTask = taskManager1.submit(new MemoryReferenceTask(() -> {
                countDownLatch.await();
                return Task.Result.COMPLETED;
            }));

            awaitUntilTaskHasStatus(firstTask, TaskManager.Status.IN_PROGRESS, taskManager1);

            Hostname nodeRunningFirstTask = taskManager1.getExecutionDetails(firstTask).getRanNode().get();
            Hostname otherNode = getOtherNode(hostNameByTaskManager, nodeRunningFirstTask);
            EventSourcingTaskManager taskManagerRunningFirstTask = hostNameByTaskManager.inverse().get(nodeRunningFirstTask);
            EventSourcingTaskManager otherTaskManager = hostNameByTaskManager.inverse().get(otherNode);

            taskManagerRunningFirstTask.close();
            TaskId taskToExecuteAfterFirstNodeIsDown = taskManagerRunningFirstTask.submit(new CompletedTask());

            awaitAtMostTwoSeconds.untilAsserted(() ->
                assertThat(otherTaskManager.getExecutionDetails(taskToExecuteAfterFirstNodeIsDown).getStatus())
                    .isEqualTo(TaskManager.Status.COMPLETED));
            TaskExecutionDetails detailsSecondTask = otherTaskManager.getExecutionDetails(taskToExecuteAfterFirstNodeIsDown);
            assertThat(detailsSecondTask.getRanNode()).contains(otherNode);
        }
    }

    @Test
    void shouldNotCrashWhenBadMessage() throws Exception {
        TaskManager taskManager = taskManager(HOSTNAME);

        taskManager.submit(new FailsDeserializationTask());

        TaskId id = taskManager.submit(TASK);

        awaitUntilTaskHasStatus(id, TaskManager.Status.COMPLETED, taskManager);
    }

    @Test
    void shouldNotCrashWhenBadMessages() throws Exception {
        TaskManager taskManager = taskManager(HOSTNAME);

        IntStream.range(0, 100).forEach(i -> taskManager.submit(new FailsDeserializationTask()));

        TaskId id = taskManager.submit(TASK);

        awaitUntilTaskHasStatus(id, TaskManager.Status.COMPLETED, taskManager);
    }

    @Test
    void shouldNotCrashWhenInvalidHeader() throws Exception {
        TaskManager taskManager = taskManager(HOSTNAME);

        AMQP.BasicProperties badProperties = new AMQP.BasicProperties.Builder()
            .deliveryMode(PERSISTENT_TEXT_PLAIN.getDeliveryMode())
            .priority(PERSISTENT_TEXT_PLAIN.getPriority())
            .contentType(PERSISTENT_TEXT_PLAIN.getContentType())
            .headers(ImmutableMap.of("abc", TASK_WITH_ID.getId().asString()))
            .build();

        rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(EXCHANGE_NAME,
                ROUTING_KEY, badProperties, taskSerializer.serialize(TASK_WITH_ID.getTask()).getBytes(StandardCharsets.UTF_8))))
            .block();

        TaskId taskId = taskManager.submit(TASK);

        await().atMost(FIVE_SECONDS).until(() -> taskManager.list(TaskManager.Status.COMPLETED).size() == 1);

        assertThat(taskManager.getExecutionDetails(taskId).getStatus())
            .isEqualTo(TaskManager.Status.COMPLETED);
    }

    @Test
    void shouldNotCrashWhenInvalidTaskId() throws Exception {
        TaskManager taskManager = taskManager(HOSTNAME);

        AMQP.BasicProperties badProperties = new AMQP.BasicProperties.Builder()
            .deliveryMode(PERSISTENT_TEXT_PLAIN.getDeliveryMode())
            .priority(PERSISTENT_TEXT_PLAIN.getPriority())
            .contentType(PERSISTENT_TEXT_PLAIN.getContentType())
            .headers(ImmutableMap.of("taskId", "BAD_ID"))
            .build();

        rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(EXCHANGE_NAME,
                ROUTING_KEY, badProperties, taskSerializer.serialize(TASK_WITH_ID.getTask()).getBytes(StandardCharsets.UTF_8))))
            .block();

        TaskId taskId = taskManager.submit(TASK);

        await().atMost(FIVE_SECONDS).until(() -> taskManager.list(TaskManager.Status.COMPLETED).size() == 1);

        assertThat(taskManager.getExecutionDetails(taskId).getStatus())
            .isEqualTo(TaskManager.Status.COMPLETED);
    }

    @Test
    void shouldNotCrashWhenErrorHandlingFails(CassandraCluster cassandra) throws Exception {
        TaskManager taskManager = taskManager(HOSTNAME);

        cassandra.getConf().registerScenario(Scenario.combine(
            executeNormally()
                .times(2) // submit + inProgress
                .whenQueryStartsWith("INSERT INTO eventstore"),
            executeNormally()
                .times(2) // submit + inProgress
                .whenQueryStartsWith("INSERT INTO taskexecutiondetailsprojection"),
            fail()
                .forever()
                .whenQueryStartsWith("INSERT INTO eventstore"),
            fail()
                .forever()
                .whenQueryStartsWith("INSERT INTO taskexecutiondetailsprojection")));
        taskManager.submit(new FailedTask());

        Thread.sleep(1000);

        cassandra.getConf().registerScenario(Scenario.NOTHING);

        TaskId id2 = taskManager.submit(new CompletedTask());

        awaitUntilTaskHasStatus(id2, TaskManager.Status.COMPLETED, taskManager);
    }

    @Test
    void cassandraTasksShouldSucceed(CassandraCluster cassandra) throws Exception {
        TaskManager taskManager = taskManager(HOSTNAME);

        TaskId taskId = taskManager.submit(new CassandraExecutingTask(cassandra.getConf(), false));

        TaskExecutionDetails await = taskManager.await(taskId, Duration.ofSeconds(30));

        assertThat(await.getStatus()).isEqualTo(TaskManager.Status.COMPLETED);
    }

    @Test
    void cassandraTasksShouldBeCancealable(CassandraCluster cassandra) throws Exception {
        TaskManager taskManager = taskManager(HOSTNAME);

        TaskId taskId = taskManager.submit(new CassandraExecutingTask(cassandra.getConf(), true));

        taskManager.cancel(taskId);

        awaitAtMostTwoSeconds.untilAsserted(() ->
            assertThat(taskManager.getExecutionDetails(taskId).getStatus())
                .isIn(CANCELLED, TaskManager.Status.CANCEL_REQUESTED));
    }

    @Test
    void inProgressTaskShouldBeCanceledWhenCloseTaskManager() throws Exception {
        try (EventSourcingTaskManager taskManager = taskManager()) {
            CountDownLatch latch = new CountDownLatch(1);
            TaskId taskId = taskManager.submit(new MemoryReferenceTask(() -> {
                latch.await();
                return Task.Result.COMPLETED;
            }));

            awaitAtMostTwoSeconds.until(() -> taskManager.getExecutionDetails(taskId).getStatus(), Matchers.equalTo(TaskManager.Status.IN_PROGRESS));

            taskManager.close();

            assertThat(taskManager(HOSTNAME_2).getExecutionDetails(taskId).getStatus()).isEqualTo(CANCELLED);
        }
    }

    static class CassandraExecutingTask implements Task {
        public static class CassandraExecutingTaskDTO implements TaskDTO {
            private final String type;
            private final boolean pause;

            public CassandraExecutingTaskDTO(@JsonProperty("type") String type, @JsonProperty("pause") boolean pause) {
                this.type = type;
                this.pause = pause;
            }

            public boolean isPause() {
                return pause;
            }

            @Override
            public String getType() {
                return type;
            }
        }

        public static TaskDTOModule<CassandraExecutingTask, CassandraExecutingTaskDTO> module(CqlSession session) {
            return DTOModule
                .forDomainObject(CassandraExecutingTask.class)
                .convertToDTO(CassandraExecutingTaskDTO.class)
                .toDomainObjectConverter(dto -> new CassandraExecutingTask(session, dto.isPause()))
                .toDTOConverter((task, typeName) -> new CassandraExecutingTaskDTO(typeName, task.pause))
                .typeName("CassandraExecutingTask")
                .withFactory(TaskDTOModule::new);
        }

        private final CqlSession session;
        private final boolean pause;

        CassandraExecutingTask(CqlSession session, boolean pause) {
            this.session = session;
            this.pause = pause;

            // Some task requires cassandra query execution upon their creation
            Mono.from(session.executeReactive("SELECT dateof(now()) FROM system.local ;"))
                .block();
        }

        @Override
        public Result run() throws InterruptedException {
            // Task often execute Cassandra logic
            Mono.from(session.executeReactive("SELECT dateof(now()) FROM system.local ;"))
                .block();

            if (pause) {
                Thread.sleep(120000);
            }

            return Result.COMPLETED;
        }

        @Override
        public TaskType type() {
            return TaskType.of("CassandraExecutingTask");
        }

        @Override
        public Optional<TaskExecutionDetails.AdditionalInformation> details() {
            // Some task requires cassandra query execution upon detail generation
            Mono.from(session.executeReactive("SELECT dateof(now()) FROM system.local ;"))
                .block();

            return Optional.empty();
        }
    }

    private Hostname getOtherNode(ImmutableBiMap<EventSourcingTaskManager, Hostname> hostNameByTaskManager, Hostname node) {
        return hostNameByTaskManager
            .values()
            .stream()
            .filter(hostname -> !hostname.equals(node))
            .findFirst()
            .get();
    }
}
