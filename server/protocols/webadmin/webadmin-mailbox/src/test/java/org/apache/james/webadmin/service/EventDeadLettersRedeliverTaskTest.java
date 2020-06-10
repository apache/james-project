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

import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.Optional;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.mailbox.events.EventDeadLetters;
import org.apache.james.mailbox.events.GenericGroup;
import org.apache.james.mailbox.events.Group;
import org.apache.james.webadmin.service.EventDeadLettersRedeliveryTaskAdditionalInformationDTO.EventDeadLettersRedeliveryTaskAdditionalInformationForAll;
import org.apache.james.webadmin.service.EventDeadLettersRedeliveryTaskAdditionalInformationDTO.EventDeadLettersRedeliveryTaskAdditionalInformationForGroup;
import org.apache.james.webadmin.service.EventDeadLettersRedeliveryTaskAdditionalInformationDTO.EventDeadLettersRedeliveryTaskAdditionalInformationForOne;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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

    private static final long SUCCESSFUL_REDELIVERY_COUNT = 10L;
    private static final long FAILED_REDELIVERY_COUNT = 4L;
    private static Optional<Group> SOME_GROUP;

    private static final Optional<EventDeadLetters.InsertionId> SOME_INSERTION_ID = Optional.of(EventDeadLetters.InsertionId.of("53db3dd9-80eb-476f-b25a-722ad364905a"));

    @BeforeAll
    static void setUp() throws Exception {
        SOME_GROUP = Optional.of(Group.deserialize("org.apache.james.mailbox.events.GenericGroup-foo"));
    }

    @Test
    void redeliverAllTaskShouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(EventDeadLettersRedeliverAllTaskDTO.module(SERVICE))
            .bean(TASK_ALL)
            .json(SERIALIZED_ALL)
            .verify();
    }

    @Test
    void redeliverGroupTaskShouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(EventDeadLettersRedeliverGroupTaskDTO.module(SERVICE))
            .bean(TASK_GROUP)
            .json(SERIALIZED_GROUP)
            .verify();
    }

    @Test
    void redeliverOneTaskShouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(EventDeadLettersRedeliverOneTaskDTO.module(SERVICE))
            .bean(TASK_ONE)
            .json(SERIALIZED_ONE)
            .verify();
    }

    @Test
    void redeliverAllAdditionalInformationShouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(EventDeadLettersRedeliveryTaskAdditionalInformationForAll.module())
            .bean(new EventDeadLettersRedeliveryTaskAdditionalInformationForAll(SUCCESSFUL_REDELIVERY_COUNT, FAILED_REDELIVERY_COUNT, TIMESTAMP))
            .json(SERIALIZED_TASK_ADDITIONAL_INFORMATION_ALL)
            .verify();
    }

    @Test
    void redeliverGroupAdditionalInformationShouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(EventDeadLettersRedeliveryTaskAdditionalInformationForGroup.module())
            .bean(new EventDeadLettersRedeliveryTaskAdditionalInformationForGroup(SUCCESSFUL_REDELIVERY_COUNT, FAILED_REDELIVERY_COUNT, SOME_GROUP, TIMESTAMP))
            .json(SERIALIZED_TASK_ADDITIONAL_INFORMATION_GROUP)
            .verify();
    }

    @Test
    void redeliverOneAdditionalInformationShouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(EventDeadLettersRedeliveryTaskAdditionalInformationForOne.module())
            .bean(new EventDeadLettersRedeliveryTaskAdditionalInformationForOne(SUCCESSFUL_REDELIVERY_COUNT, FAILED_REDELIVERY_COUNT, SOME_GROUP, SOME_INSERTION_ID, TIMESTAMP))
            .json(SERIALIZED_TASK_ADDITIONAL_INFORMATION_ONE)
            .verify();
    }
}