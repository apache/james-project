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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.events.EventDeadLetters;
import org.apache.james.events.Group;
import org.apache.james.mailbox.events.GenericGroup;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.util.streams.Limit;
import org.apache.james.webadmin.service.EventDeadLettersRedeliverService.RunningOptions;
import org.apache.james.webadmin.service.EventDeadLettersRedeliveryTaskAdditionalInformationDTO.EventDeadLettersRedeliveryTaskAdditionalInformationForAll;
import org.apache.james.webadmin.service.EventDeadLettersRedeliveryTaskAdditionalInformationDTO.EventDeadLettersRedeliveryTaskAdditionalInformationForGroup;
import org.apache.james.webadmin.service.EventDeadLettersRedeliveryTaskAdditionalInformationDTO.EventDeadLettersRedeliveryTaskAdditionalInformationForOne;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EventDeadLettersRedeliverTaskTest {
    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");
    private static final EventDeadLettersRedeliverService SERVICE = mock(EventDeadLettersRedeliverService.class);
    private static final Set<Group> NON_CRITICAL_GROUPS = Set.of();
    private static final EventDeadLettersRedeliverAllTask TASK_ALL = new EventDeadLettersRedeliverAllTask(SERVICE, EventDeadLettersRedeliverService.RunningOptions.DEFAULT, NON_CRITICAL_GROUPS);
    private static final EventDeadLettersRedeliverGroupTask TASK_GROUP = new EventDeadLettersRedeliverGroupTask(SERVICE, new GenericGroup("abc"), EventDeadLettersRedeliverService.RunningOptions.DEFAULT);
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
        JsonSerializationVerifier.dtoModule(EventDeadLettersRedeliverAllTaskDTO.module(SERVICE, NON_CRITICAL_GROUPS))
            .bean(TASK_ALL)
            .json("{" +
                "    \"type\": \"event-dead-letters-redeliver-all\"," +
                "    \"runningOptions\": {}" +
                "}")
            .verify();

        EventDeadLettersRedeliverAllTask taskAllWithLimit = new EventDeadLettersRedeliverAllTask(SERVICE, new RunningOptions(Limit.limit(10)), NON_CRITICAL_GROUPS);

        JsonSerializationVerifier.dtoModule(EventDeadLettersRedeliverAllTaskDTO.module(SERVICE, NON_CRITICAL_GROUPS))
            .bean(taskAllWithLimit)
            .json("{\"type\":\"event-dead-letters-redeliver-all\", \"runningOptions\":{\"limit\": 10}}")
            .verify();
    }

    @Test
    void redeliverAllTaskShouldDeserializationSuccess() throws Exception {
        JsonTaskSerializer serializer = JsonTaskSerializer.of(EventDeadLettersRedeliverAllTaskDTO.module(SERVICE, NON_CRITICAL_GROUPS));

        assertThat(serializer.deserialize("{\"type\":\"event-dead-letters-redeliver-all\", \"runningOptions\":{\"limit\": 10}}"))
            .usingRecursiveComparison()
            .isEqualTo(new EventDeadLettersRedeliverAllTask(SERVICE, new RunningOptions(Limit.limit(10)), NON_CRITICAL_GROUPS));

        assertThat(serializer.deserialize("{\"type\":\"event-dead-letters-redeliver-all\", \"runningOptions\":{}}"))
            .usingRecursiveComparison()
            .isEqualTo(new EventDeadLettersRedeliverAllTask(SERVICE, new RunningOptions(Limit.unlimited()), NON_CRITICAL_GROUPS));

        assertThat(serializer.deserialize("{\"type\":\"event-dead-letters-redeliver-all\"}"))
            .usingRecursiveComparison()
            .isEqualTo(new EventDeadLettersRedeliverAllTask(SERVICE, new RunningOptions(Limit.unlimited()), NON_CRITICAL_GROUPS));
    }

    @Test
    void redeliverGroupTaskShouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(EventDeadLettersRedeliverGroupTaskDTO.module(SERVICE))
            .bean(TASK_GROUP)
            .json("{" +
                "    \"type\": \"event-dead-letters-redeliver-group\"," +
                "    \"group\": \"org.apache.james.mailbox.events.GenericGroup-abc\"," +
                "    \"runningOptions\": {}" +
                "}")
            .verify();

        EventDeadLettersRedeliverGroupTask taskGroupWithLimit = new EventDeadLettersRedeliverGroupTask(SERVICE, new GenericGroup("abc"), new RunningOptions(Limit.limit(10)));

        JsonSerializationVerifier.dtoModule(EventDeadLettersRedeliverGroupTaskDTO.module(SERVICE))
            .bean(taskGroupWithLimit)
            .json("{" +
                "    \"type\": \"event-dead-letters-redeliver-group\"," +
                "    \"group\": \"org.apache.james.mailbox.events.GenericGroup-abc\"," +
                "    \"runningOptions\": {" +
                "        \"limit\": 10" +
                "    }" +
                "}")
            .verify();
    }

    @Test
    void redeliverGroupTaskShouldDeserializationSuccess() throws Exception {
        JsonTaskSerializer serializer = JsonTaskSerializer.of(EventDeadLettersRedeliverGroupTaskDTO.module(SERVICE));

        assertThat(serializer.deserialize("{" +
            "    \"type\": \"event-dead-letters-redeliver-group\"," +
            "    \"group\": \"org.apache.james.mailbox.events.GenericGroup-abc\"," +
            "    \"runningOptions\": {" +
            "        \"limit\": 10" +
            "    }" +
            "}"))
            .usingRecursiveComparison()
            .isEqualTo(new EventDeadLettersRedeliverGroupTask(SERVICE, new GenericGroup("abc"), new RunningOptions(Limit.limit(10))));

        assertThat(serializer.deserialize("{" +
            "    \"type\": \"event-dead-letters-redeliver-group\"," +
            "    \"group\": \"org.apache.james.mailbox.events.GenericGroup-abc\"," +
            "    \"runningOptions\": {}" +
            "}"))
            .usingRecursiveComparison()
            .isEqualTo(new EventDeadLettersRedeliverGroupTask(SERVICE, new GenericGroup("abc"), new RunningOptions(Limit.unlimited())));


        assertThat(serializer.deserialize("{" +
            "    \"type\": \"event-dead-letters-redeliver-group\"," +
            "    \"group\": \"org.apache.james.mailbox.events.GenericGroup-abc\"" +
            "}"))
            .usingRecursiveComparison()
            .isEqualTo(new EventDeadLettersRedeliverGroupTask(SERVICE, new GenericGroup("abc"), new RunningOptions(Limit.unlimited())));
    }

    @Test
    void redeliverOneTaskShouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(EventDeadLettersRedeliverOneTaskDTO.module(SERVICE))
            .bean(TASK_ONE)
            .json("{" +
                "    \"type\": \"event-dead-letters-redeliver-one\"," +
                "    \"group\": \"org.apache.james.mailbox.events.GenericGroup-abc\"," +
                "    \"insertionId\": \"fcbc3c92-e9a0-4ece-94ed-6e6b45045258\"" +
                "}")
            .verify();
    }

    @Test
    void redeliverAllAdditionalInformationShouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(EventDeadLettersRedeliveryTaskAdditionalInformationForAll.module())
            .bean(new EventDeadLettersRedeliveryTaskAdditionalInformationForAll(SUCCESSFUL_REDELIVERY_COUNT, FAILED_REDELIVERY_COUNT, TIMESTAMP, RunningOptions.DEFAULT))
            .json("{" +
                "    \"type\": \"event-dead-letters-redeliver-all\"," +
                "    \"successfulRedeliveriesCount\": 10," +
                "    \"failedRedeliveriesCount\": 4," +
                "    \"timestamp\": \"2018-11-13T12:00:55Z\"," +
                "    \"runningOptions\":{}" +
                "}")
            .verify();

        JsonSerializationVerifier.dtoModule(EventDeadLettersRedeliveryTaskAdditionalInformationForAll.module())
            .bean(new EventDeadLettersRedeliveryTaskAdditionalInformationForAll(SUCCESSFUL_REDELIVERY_COUNT, FAILED_REDELIVERY_COUNT, TIMESTAMP, new RunningOptions(Limit.limit(10))))
            .json("{" +
                "    \"type\": \"event-dead-letters-redeliver-all\"," +
                "    \"successfulRedeliveriesCount\": 10," +
                "    \"failedRedeliveriesCount\": 4," +
                "    \"timestamp\": \"2018-11-13T12:00:55Z\"," +
                "    \"runningOptions\":{ \"limit\": 10}" +
                "}")
            .verify();
    }

    @Test
    void redeliverGroupAdditionalInformationShouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(EventDeadLettersRedeliveryTaskAdditionalInformationForGroup.module())
            .bean(new EventDeadLettersRedeliveryTaskAdditionalInformationForGroup(SUCCESSFUL_REDELIVERY_COUNT, FAILED_REDELIVERY_COUNT, SOME_GROUP, TIMESTAMP, RunningOptions.DEFAULT))
            .json("{" +
                "    \"type\": \"event-dead-letters-redeliver-group\"," +
                "    \"successfulRedeliveriesCount\": 10," +
                "    \"failedRedeliveriesCount\": 4," +
                "    \"group\": \"org.apache.james.mailbox.events.GenericGroup-foo\"," +
                "    \"timestamp\": \"2018-11-13T12:00:55Z\"," +
                "    \"runningOptions\": {}" +
                "}")
            .verify();

        JsonSerializationVerifier.dtoModule(EventDeadLettersRedeliveryTaskAdditionalInformationForGroup.module())
            .bean(new EventDeadLettersRedeliveryTaskAdditionalInformationForGroup(SUCCESSFUL_REDELIVERY_COUNT, FAILED_REDELIVERY_COUNT, SOME_GROUP, TIMESTAMP, new RunningOptions(Limit.limit(10))))
            .json("{" +
                "    \"type\": \"event-dead-letters-redeliver-group\"," +
                "    \"successfulRedeliveriesCount\": 10," +
                "    \"failedRedeliveriesCount\": 4," +
                "    \"group\": \"org.apache.james.mailbox.events.GenericGroup-foo\"," +
                "    \"timestamp\": \"2018-11-13T12:00:55Z\"," +
                "    \"runningOptions\": {\"limit\": 10}" +
                "}")
            .verify();
    }

    @Test
    void redeliverOneAdditionalInformationShouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(EventDeadLettersRedeliveryTaskAdditionalInformationForOne.module())
            .bean(new EventDeadLettersRedeliveryTaskAdditionalInformationForOne(SUCCESSFUL_REDELIVERY_COUNT, FAILED_REDELIVERY_COUNT, SOME_GROUP, SOME_INSERTION_ID, TIMESTAMP))
            .json("{" +
                "    \"type\": \"event-dead-letters-redeliver-one\"," +
                "    \"successfulRedeliveriesCount\": 10," +
                "    \"failedRedeliveriesCount\": 4," +
                "    \"group\": \"org.apache.james.mailbox.events.GenericGroup-foo\"," +
                "    \"insertionId\": \"53db3dd9-80eb-476f-b25a-722ad364905a\"," +
                "    \"timestamp\": \"2018-11-13T12:00:55Z\"" +
                "}")
            .verify();
    }
}