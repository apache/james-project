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

package org.apache.james.mailbox.cassandra.mail.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.json.JsonGenericSerializer;
import org.apache.james.mailbox.cassandra.mail.task.SolveMessageInconsistenciesService.RunningOptions;
import org.apache.james.mailbox.cassandra.mail.task.SolveMessageInconsistenciesTask.Details;
import org.apache.james.util.ClassLoaderUtils;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

public class SolveMessageInconsistenciesTaskSerializationTest {

    private static final SolveMessageInconsistenciesService SERVICE = mock(SolveMessageInconsistenciesService.class);
    private static final SolveMessageInconsistenciesTask TASK = new SolveMessageInconsistenciesTask(SERVICE, new RunningOptions(2));

    private static final Instant INSTANT = Instant.parse("2007-12-03T10:15:30.00Z");
    private static final String MAILBOX_ID = "551f0580-82fb-11ea-970e-f9c83d4cf8c2";
    private static final String MESSAGE_ID_1 = "d2bee791-7e63-11ea-883c-95b84008f979";
    private static final String MESSAGE_ID_2 = "d2bee792-7e63-11ea-883c-95b84008f979";
    private static final String MESSAGE_ID_3 = "ffffffff-7e63-11ea-883c-95b84008f979";
    private static final Long MESSAGE_UID_1 = 1L;
    private static final Long MESSAGE_UID_2 = 2L;
    private static final Long MESSAGE_UID_3 = 3L;

    private static final MessageInconsistenciesEntry MESSAGE_1 = MessageInconsistenciesEntry.builder()
        .mailboxId(MAILBOX_ID)
        .messageId(MESSAGE_ID_1)
        .messageUid(MESSAGE_UID_1);
    private static final MessageInconsistenciesEntry MESSAGE_2 = MessageInconsistenciesEntry.builder()
        .mailboxId(MAILBOX_ID)
        .messageId(MESSAGE_ID_2)
        .messageUid(MESSAGE_UID_2);
    private static final MessageInconsistenciesEntry MESSAGE_3 = MessageInconsistenciesEntry.builder()
        .mailboxId(MAILBOX_ID)
        .messageId(MESSAGE_ID_3)
        .messageUid(MESSAGE_UID_3);

    private static final Details DETAILS = new SolveMessageInconsistenciesTask.Details(INSTANT, 2, 1, 1, 0, 1, new SolveMessageInconsistenciesService.RunningOptions(2), ImmutableList.of(MESSAGE_1, MESSAGE_2), ImmutableList.of(MESSAGE_3));
    private RecursiveComparisonConfiguration recursiveComparisonConfiguration;

    @BeforeEach
    void setUp() {
        recursiveComparisonConfiguration = new RecursiveComparisonConfiguration();
        recursiveComparisonConfiguration.registerComparatorForType(Comparator.comparingInt(AtomicInteger::get), AtomicInteger.class);
        recursiveComparisonConfiguration.registerComparatorForType(Comparator.comparingLong(AtomicLong::get), AtomicLong.class);
        recursiveComparisonConfiguration.registerEqualsForType((o, o2) -> o.get() == o2.get(), AtomicInteger.class);
        recursiveComparisonConfiguration.registerEqualsForType((o, o2) -> o.get() == o2.get(), AtomicLong.class);
        recursiveComparisonConfiguration.registerEqualsForType((o, o2) -> o.get() == o2.get(), AtomicBoolean.class);
    }

    @Test
    void taskShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(SolveMessageInconsistenciesTaskDTO.module(SERVICE))
            .bean(TASK)
            .json(ClassLoaderUtils.getSystemResourceAsString("json/solveMessageInconsistencies.task.json"))
            .verify();
    }

    @Test
    void legacyTaskShouldBeDeserializable() throws Exception {
        SolveMessageInconsistenciesService service = mock(SolveMessageInconsistenciesService.class);

        SolveMessageInconsistenciesTask legacyTask = JsonGenericSerializer.forModules(SolveMessageInconsistenciesTaskDTO.module(service))
            .withoutNestedType()
            .deserialize(ClassLoaderUtils.getSystemResourceAsString("json/solveMessageInconsistencies.task.legacy.json"));

        SolveMessageInconsistenciesTask expected = new SolveMessageInconsistenciesTask(service, RunningOptions.DEFAULT);


        assertThat(legacyTask)
            .usingRecursiveComparison(recursiveComparisonConfiguration)
            .isEqualTo(expected);
    }

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(SolveMessageInconsistenciesTaskAdditionalInformationDTO.module())
            .bean(DETAILS)
            .json(ClassLoaderUtils.getSystemResourceAsString("json/solveMessageInconsistencies.additionalInformation.json"))
            .verify();
    }

    @Test
    void legacyAdditionalInformationShouldBeDeserializable() throws Exception {
        SolveMessageInconsistenciesTask.Details legacyDetails = JsonGenericSerializer.forModules(SolveMessageInconsistenciesTaskAdditionalInformationDTO.module())
            .withoutNestedType()
            .deserialize(ClassLoaderUtils.getSystemResourceAsString("json/solveMessageInconsistencies.additionalInformation.legacy.json"));

        SolveMessageInconsistenciesTask.Details expected = new SolveMessageInconsistenciesTask.Details(
            INSTANT,
            2,
            1,
            1,
            0,
            1,
            RunningOptions.DEFAULT,
            ImmutableList.of(MESSAGE_1, MESSAGE_2),
            ImmutableList.of(MESSAGE_3)
            );

        assertThat(legacyDetails)
            .usingRecursiveComparison(recursiveComparisonConfiguration)
            .isEqualTo(expected);
    }
}
