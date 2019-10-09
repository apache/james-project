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
import java.util.Optional;

import org.apache.james.mailbox.events.EventDeadLetters;
import org.apache.james.mailbox.events.Group;
import org.apache.james.server.task.json.JsonTaskAdditionalInformationsSerializer;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.javacrumbs.jsonunit.assertj.JsonAssertions;

class EventDeadLettersRedeliverTaskTest {
    private static final String SERIALIZED = "{\"type\":\"eventDeadLettersRedeliverTask\"}";
    private static final String SERIALIZED_TASK_ADDITIONAL_INFORMATION = "{\"type\":\"eventDeadLettersRedeliverTask\", \"successfulRedeliveriesCount\":10,\"failedRedeliveriesCount\":4,\"group\":\"org.apache.james.mailbox.events.GenericGroup-foo\",\"insertionId\":\"53db3dd9-80eb-476f-b25a-722ad364905a\"}";
    private static final String SERIALIZED_TASK_ADDITIONAL_INFORMATION_EMPTY_FIELDS = "{\"type\":\"eventDeadLettersRedeliverTask\", \"successfulRedeliveriesCount\":10,\"failedRedeliveriesCount\":4}";
    private static final EventDeadLettersRedeliverService SERVICE = mock(EventDeadLettersRedeliverService.class);
    private static final EventRetriever EVENT_RETRIEVER = mock(EventRetriever.class);
    private static final EventDeadLettersRedeliverTask TASK = new EventDeadLettersRedeliverTask(SERVICE, EVENT_RETRIEVER);
    private static final JsonTaskSerializer TESTEE = new JsonTaskSerializer(EventDeadLettersRedeliverTaskDTO.MODULE.apply(SERVICE, EVENT_RETRIEVER));

    private JsonTaskAdditionalInformationsSerializer jsonAdditionalInformationSerializer = new JsonTaskAdditionalInformationsSerializer(
        EventDeadLettersRedeliveryTaskAdditionalInformationDTO.MODULE);

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

    @Test
    void taskShouldBeSerializable() throws JsonProcessingException {
        JsonAssertions.assertThatJson(TESTEE.serialize(TASK))
            .isEqualTo(SERIALIZED);
    }

    @Test
    void taskShouldBeDeserializable() throws IOException {
        assertThat(TESTEE.deserialize(SERIALIZED))
            .isEqualToComparingFieldByFieldRecursively(TASK);
    }

    @Test
    void additionalInformationShouldBeSerializable() throws JsonProcessingException {
        EventDeadLettersRedeliverTask.AdditionalInformation details = new EventDeadLettersRedeliverTask.AdditionalInformation(SUCCESSFUL_REDELIVERY_COUNT,
            FAILED_REDELIVERY_COUNT,
            SOME_GROUP,
            SOME_INSERTION_ID);
        assertThatJson(jsonAdditionalInformationSerializer.serialize(details)).isEqualTo(SERIALIZED_TASK_ADDITIONAL_INFORMATION);
    }

    @Test
    void additionalInformationShouldBeDeserializable() throws IOException {
        EventDeadLettersRedeliverTask.AdditionalInformation details = new EventDeadLettersRedeliverTask.AdditionalInformation(SUCCESSFUL_REDELIVERY_COUNT,
            FAILED_REDELIVERY_COUNT,
            SOME_GROUP,
            SOME_INSERTION_ID);
        assertThat(jsonAdditionalInformationSerializer.deserialize(SERIALIZED_TASK_ADDITIONAL_INFORMATION))
            .isEqualToComparingFieldByField(details);
    }

    @Test
    void additionalInformationShouldBeSerializableWithEmptyFields() throws JsonProcessingException {
        EventDeadLettersRedeliverTask.AdditionalInformation details = new EventDeadLettersRedeliverTask.AdditionalInformation(SUCCESSFUL_REDELIVERY_COUNT,
            FAILED_REDELIVERY_COUNT,
            NO_GROUP,
            NO_INSERTION_ID);
        assertThatJson(jsonAdditionalInformationSerializer.serialize(details)).isEqualTo(SERIALIZED_TASK_ADDITIONAL_INFORMATION_EMPTY_FIELDS);
    }

    @Test
    void additionalInformationShouldBeDeserializableWithEmptyFields() throws IOException {
        EventDeadLettersRedeliverTask.AdditionalInformation details = new EventDeadLettersRedeliverTask.AdditionalInformation(SUCCESSFUL_REDELIVERY_COUNT,
            FAILED_REDELIVERY_COUNT,
            NO_GROUP,
            NO_INSERTION_ID);
        assertThat(jsonAdditionalInformationSerializer.deserialize(SERIALIZED_TASK_ADDITIONAL_INFORMATION_EMPTY_FIELDS))
            .isEqualToComparingFieldByField(details);
    }
}