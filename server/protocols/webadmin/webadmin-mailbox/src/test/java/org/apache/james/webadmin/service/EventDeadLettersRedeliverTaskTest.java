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

package org.apache.james.webadmin.service;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.mailbox.events.EventDeadLetters;
import org.apache.james.mailbox.events.GenericGroup;
import org.apache.james.mailbox.events.Group;
import org.apache.james.server.task.json.JsonTaskAdditionalInformationSerializer;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.task.Task;
import org.apache.james.webadmin.service.EventDeadLettersRedeliveryTaskAdditionalInformationDTO.EventDeadLettersRedeliveryTaskAdditionalInformationForAll;
import org.apache.james.webadmin.service.EventDeadLettersRedeliveryTaskAdditionalInformationDTO.EventDeadLettersRedeliveryTaskAdditionalInformationForGroup;
import org.apache.james.webadmin.service.EventDeadLettersRedeliveryTaskAdditionalInformationDTO.EventDeadLettersRedeliveryTaskAdditionalInformationForOne;

import org.junit.jupiter.api.BeforeAll;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EventDeadLettersRedeliverTaskTest {
    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");
    private static final String SERIALIZED_ALL = "{\"type\":\"event-dead-letters-redeliver-all\"}";
    private static final String SERIALIZED_GROUP = "{\"type\":\"event-dead-letters-redeliver-group\",\"group\":\"org.apache.james.mailbox.events.GenericGroup-abc\"}";
    private static final String SERIALIZED_ONE = "{\"type\":\"event-dead-letters-redeliver-one\",\"group\":\"org.apache.james.mailbox.events.GenericGroup-abc\",\"insertionId\":\"fcbc3c92-e9a0-4ece-94ed-6e6b45045258\"}";
    private static final String SERIALIZED_TASK_ADDITIONAL_INFORMATION_ALL = "{\"type\":\"event-dead-letters-redeliver-all\",\"successfulRedeliveriesCount\":10,\"failedRedeliveriesCount\":4, \"timestamp\":\"2018-11-13T12:00:55Z\"}";
    private static final String SERIALIZED_TASK_ADDITIONAL_INFORMATION_GROUP = "{\"type\":\"event-dead-letters-redeliver-group\",\"successfulRedeliveriesCount\":10,\"failedRedeliveriesCount\":4,\"group\":\"org.apache.james.mailbox.events.GenericGroup-foo\", \"timestamp\":\"2018-11-13T12:00:55Z\"}";
    private static final String SERIALIZED_TASK_ADDITIONAL_INFORMATION_ONE = "{\"type\":\"event-dead-letters-redeliver-one\",\"successfulRedeliveriesCount\":10,\"failedRedeliveriesCount\":4,\"group\":\"org.apache.james.mailbox.events.GenericGroup-foo\",\"insertionId\":\"53db3dd9-80eb-476f-b25a-722ad364905a\", \"timestamp\":\"2018-11-13T12:00:55Z\"}";
    private static final EventDeadLettersRedeliverService SERVICE = mock(EventDeadLettersRedeliverService.class);
    private static final EventDeadLettersRedeliverAllTask TASK_ALL = new EventDeadLettersRedeliverAllTask(SERVICE);
    private static final EventDeadLettersRedeliverGroupTask TASK_GROUP = new EventDeadLettersRedeliverGroupTask(SERVICE, new GenericGroup("abc"));
    private static final EventDeadLettersRedeliverOneTask TASK_ONE = new EventDeadLettersRedeliverOneTask(SERVICE, new GenericGroup("abc"), EventDeadLetters.InsertionId.of("fcbc3c92-e9a0-4ece-94ed-6e6b45045258"));
    private static final JsonTaskSerializer TESTEE = JsonTaskSerializer.of(
        EventDeadLettersRedeliverAllTaskDTO.module(SERVICE),
        EventDeadLettersRedeliverGroupTaskDTO.module(SERVICE),
        EventDeadLettersRedeliverOneTaskDTO.module(SERVICE));

    private JsonTaskAdditionalInformationSerializer jsonAdditionalInformationSerializer = JsonTaskAdditionalInformationSerializer.of(
        EventDeadLettersRedeliveryTaskAdditionalInformationForAll.MODULE,
        EventDeadLettersRedeliveryTaskAdditionalInformationForGroup.MODULE,
        EventDeadLettersRedeliveryTaskAdditionalInformationForOne.MODULE);

    private static final long SUCCESSFUL_REDELIVERY_COUNT = 10L;
    private static final long FAILED_REDELIVERY_COUNT = 4L;
    private static Optional<Group> SOME_GROUP;

    private static final Optional<EventDeadLetters.InsertionId> SOME_INSERTION_ID = Optional.of(EventDeadLetters.InsertionId.of("53db3dd9-80eb-476f-b25a-722ad364905a"));
    private static final Optional<Group> NO_GROUP = Optional.empty();
    private static final Optional<EventDeadLetters.InsertionId> NO_INSERTION_ID = Optional.empty();

    @BeforeAll
    static void setUp() throws Exception {
        SOME_GROUP = Optional.of(Group.deserialize("org.apache.james.mailbox.events.GenericGroup-foo"));
    }

    static Stream<Arguments> tasks() {
        return Stream.of(
            Arguments.of(SERIALIZED_ALL, TASK_ALL),
            Arguments.of(SERIALIZED_GROUP, TASK_GROUP),
            Arguments.of(SERIALIZED_ONE, TASK_ONE)
        );
    }

    @ParameterizedTest
    @MethodSource("tasks")
    void taskShouldBeSerializable(String serializedTask, Task task) throws JsonProcessingException {
        JsonAssertions.assertThatJson(TESTEE.serialize(task))
            .isEqualTo(serializedTask);
    }

    @ParameterizedTest
    @MethodSource("tasks")
    void taskShouldBeDeserializable(String serializedTask, Task task) throws IOException {
        assertThat(TESTEE.deserialize(serializedTask))
            .isEqualToComparingFieldByFieldRecursively(task);
    }

    static Stream<Arguments> additionalInformation() {
        return Stream.of(
            Arguments.of(
                new EventDeadLettersRedeliveryTaskAdditionalInformationForAll(SUCCESSFUL_REDELIVERY_COUNT, FAILED_REDELIVERY_COUNT, TIMESTAMP),
                SERIALIZED_TASK_ADDITIONAL_INFORMATION_ALL,
                "eventDeadLettersRedeliverAllTask"),
            Arguments.of(
                new EventDeadLettersRedeliveryTaskAdditionalInformationForGroup(SUCCESSFUL_REDELIVERY_COUNT, FAILED_REDELIVERY_COUNT, SOME_GROUP, TIMESTAMP),
                SERIALIZED_TASK_ADDITIONAL_INFORMATION_GROUP,
                "eventDeadLettersRedeliverGroupTask"),
            Arguments.of(
                new EventDeadLettersRedeliveryTaskAdditionalInformationForOne(SUCCESSFUL_REDELIVERY_COUNT, FAILED_REDELIVERY_COUNT, SOME_GROUP, SOME_INSERTION_ID, TIMESTAMP),
                SERIALIZED_TASK_ADDITIONAL_INFORMATION_ONE,
                "eventDeadLettersRedeliverOneTask")
        );
    }

    @ParameterizedTest
    @MethodSource("additionalInformation")
    void additionalInformationShouldBeSerializable(EventDeadLettersRedeliveryTaskAdditionalInformation details, String serializedTaskAdditionalInformation) throws JsonProcessingException {
        assertThatJson(jsonAdditionalInformationSerializer.serialize(details)).isEqualTo(serializedTaskAdditionalInformation);
    }

    @ParameterizedTest
    @MethodSource("additionalInformation")
    void additionalInformationShouldBeDeserializable(EventDeadLettersRedeliveryTaskAdditionalInformation details, String serializedTaskAdditionalInformation, String type) throws IOException {
        assertThat(jsonAdditionalInformationSerializer.deserialize(serializedTaskAdditionalInformation))
            .isEqualToComparingFieldByField(details);
    }
}