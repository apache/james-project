/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/

package org.apache.james.task.eventsourcing.distributed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.james.eventsourcing.EventId;
import org.apache.james.eventsourcing.eventstore.cassandra.JsonEventSerializer;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTOModule;
import org.apache.james.json.DTOConverter;
import org.apache.james.server.task.json.JsonTaskAdditionalInformationSerializer;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.MemoryReferenceWithCounterTaskAdditionalInformationDTO;
import org.apache.james.server.task.json.dto.MemoryReferenceWithCounterTaskStore;
import org.apache.james.server.task.json.dto.TestTaskDTOModules;
import org.apache.james.task.CompletedTask;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryReferenceWithCounterTask;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskId;
import org.apache.james.task.eventsourcing.CancelRequested;
import org.apache.james.task.eventsourcing.Cancelled;
import org.apache.james.task.eventsourcing.Completed;
import org.apache.james.task.eventsourcing.Created;
import org.apache.james.task.eventsourcing.Failed;
import org.apache.james.task.eventsourcing.Started;
import org.apache.james.task.eventsourcing.TaskAggregateId;
import org.apache.james.task.eventsourcing.TaskEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.common.collect.ImmutableSet;
import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import scala.Option;

class TaskEventsSerializationTest {
    static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");
    static final DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> ADDITIONAL_INFORMATION_CONVERTER = DTOConverter.of(MemoryReferenceWithCounterTaskAdditionalInformationDTO.SERIALIZATION_MODULE);
    static final JsonTaskAdditionalInformationSerializer TASK_ADDITIONNAL_INFORMATION_SERIALIZER = new JsonTaskAdditionalInformationSerializer(MemoryReferenceWithCounterTaskAdditionalInformationDTO.SERIALIZATION_MODULE);
    static final TaskAggregateId AGGREGATE_ID = new TaskAggregateId(TaskId.fromString("2c7f4081-aa30-11e9-bf6c-2d3b9e84aafd"));
    static final EventId EVENT_ID = EventId.fromSerialized(42);
    static final Task TASK = new CompletedTask();
    static final Hostname HOSTNAME = new Hostname("foo");
    static final MemoryReferenceWithCounterTask.AdditionalInformation COUNTER_ADDITIONAL_INFORMATION = new MemoryReferenceWithCounterTask.AdditionalInformation(3, TIMESTAMP);

    private final Set<EventDTOModule<?, ?>> list = TasksSerializationModule.list(
        new JsonTaskSerializer(
            TestTaskDTOModules.COMPLETED_TASK_MODULE,
            TestTaskDTOModules.MEMORY_REFERENCE_WITH_COUNTER_TASK_MODULE.apply(new MemoryReferenceWithCounterTaskStore())),
        ADDITIONAL_INFORMATION_CONVERTER);

    JsonEventSerializer serializer = new JsonEventSerializer(new DTOConverter<>(list), list, ImmutableSet.of(MemoryReferenceWithCounterTaskAdditionalInformationDTO.SERIALIZATION_MODULE));

    @ParameterizedTest
    @MethodSource
    void taskShouldBeSerializable(TaskEvent event, String serializedJson) throws Exception {
        JsonAssertions.assertThatJson(serializer.serialize(event)).isEqualTo(serializedJson);
    }

    static Stream<Arguments> taskShouldBeSerializable() throws Exception {
        return validTasks();
    }

    @ParameterizedTest
    @MethodSource
    void taskShouldBeDeserializable(TaskEvent event, String serializedJson) throws Exception {
        assertThat(serializer.deserialize(serializedJson)).isEqualToComparingFieldByFieldRecursively(event);
    }

    static Stream<Arguments> taskShouldBeDeserializable() throws Exception {
        return validTasks();
    }

    static Stream<Arguments> validTasks() throws Exception {
        return Stream.of(
            Arguments.of(new Created(AGGREGATE_ID, EVENT_ID, TASK, HOSTNAME), "{\"task\":\"{\\\"type\\\":\\\"completed-task\\\"}\",\"type\":\"task-manager-created\",\"aggregate\":\"2c7f4081-aa30-11e9-bf6c-2d3b9e84aafd\",\"event\":42,\"hostname\":\"foo\"}\n"),
            Arguments.of(new Started(AGGREGATE_ID, EVENT_ID, HOSTNAME), "{\"aggregate\":\"2c7f4081-aa30-11e9-bf6c-2d3b9e84aafd\",\"event\":42,\"type\":\"task-manager-started\",\"hostname\":\"foo\"}"),
            Arguments.of(new CancelRequested(AGGREGATE_ID, EVENT_ID, HOSTNAME), "{\"type\":\"task-manager-cancel-requested\",\"aggregate\":\"2c7f4081-aa30-11e9-bf6c-2d3b9e84aafd\",\"event\":42,\"hostname\":\"foo\"}\n"),
            Arguments.of(new Completed(AGGREGATE_ID, EVENT_ID, Task.Result.COMPLETED, Option.empty()), "{\"result\":\"COMPLETED\",\"aggregate\":\"2c7f4081-aa30-11e9-bf6c-2d3b9e84aafd\",\"event\":42,\"type\":\"task-manager-completed\"}"),
            Arguments.of(new Completed(AGGREGATE_ID, EVENT_ID, Task.Result.PARTIAL, Option.empty()), "{\"result\":\"PARTIAL\",\"aggregate\":\"2c7f4081-aa30-11e9-bf6c-2d3b9e84aafd\",\"event\":42,\"type\":\"task-manager-completed\"}"),
            Arguments.of(new Failed(AGGREGATE_ID, EVENT_ID, Option.empty(), Option.empty(), Option.empty()), "{\"aggregate\":\"2c7f4081-aa30-11e9-bf6c-2d3b9e84aafd\",\"event\":42,\"type\":\"task-manager-failed\"}"),
            Arguments.of(new Failed(AGGREGATE_ID, EVENT_ID, Option.empty(), Option.apply("contextual message"), Option.apply("my exception")), "{\"aggregate\":\"2c7f4081-aa30-11e9-bf6c-2d3b9e84aafd\",\"event\":42,\"type\":\"task-manager-failed\", \"errorMessage\": \"contextual message\", \"exception\": \"my exception\"}"),
            Arguments.of(new Cancelled(AGGREGATE_ID, EVENT_ID, Option.empty()), "{\"aggregate\":\"2c7f4081-aa30-11e9-bf6c-2d3b9e84aafd\",\"event\":42,\"type\":\"task-manager-cancelled\"}"),
            Arguments.of(new Completed(AGGREGATE_ID, EVENT_ID, Task.Result.COMPLETED, Option.apply(COUNTER_ADDITIONAL_INFORMATION)), "{\"result\":\"COMPLETED\",\"aggregate\":\"2c7f4081-aa30-11e9-bf6c-2d3b9e84aafd\",\"event\":42,\"type\":\"task-manager-completed\",\"additionalInformation\":{\"type\":\"memory-reference-task-with-counter\",\"count\":3,\"timestamp\":\"2018-11-13T12:00:55Z\"}}"),
            Arguments.of(new Completed(AGGREGATE_ID, EVENT_ID, Task.Result.PARTIAL, Option.apply(COUNTER_ADDITIONAL_INFORMATION)), "{\"result\":\"PARTIAL\",\"aggregate\":\"2c7f4081-aa30-11e9-bf6c-2d3b9e84aafd\",\"event\":42,\"type\":\"task-manager-completed\",\"additionalInformation\":{\"type\":\"memory-reference-task-with-counter\",\"count\":3,\"timestamp\":\"2018-11-13T12:00:55Z\"}}"),
            Arguments.of(new Failed(AGGREGATE_ID, EVENT_ID, Option.apply(COUNTER_ADDITIONAL_INFORMATION), Option.empty(), Option.empty()), "{\"aggregate\":\"2c7f4081-aa30-11e9-bf6c-2d3b9e84aafd\",\"event\":42,\"type\":\"task-manager-failed\",\"additionalInformation\":{\"type\":\"memory-reference-task-with-counter\",\"count\":3,\"timestamp\":\"2018-11-13T12:00:55Z\"}}"),
            Arguments.of(new Cancelled(AGGREGATE_ID, EVENT_ID, Option.apply(COUNTER_ADDITIONAL_INFORMATION)), "{\"aggregate\":\"2c7f4081-aa30-11e9-bf6c-2d3b9e84aafd\",\"event\":42,\"type\":\"task-manager-cancelled\",\"additionalInformation\":{\"type\":\"memory-reference-task-with-counter\",\"count\":3,\"timestamp\":\"2018-11-13T12:00:55Z\"}}")
        );
    }

}