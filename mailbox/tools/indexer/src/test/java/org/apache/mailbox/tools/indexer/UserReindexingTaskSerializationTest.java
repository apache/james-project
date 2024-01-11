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
package org.apache.mailbox.tools.indexer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.core.Username;
import org.apache.james.json.JsonGenericSerializer;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.indexer.ReIndexer.RunningOptions;
import org.apache.james.mailbox.indexer.ReIndexingExecutionFailures;
import org.apache.james.mailbox.model.TestId;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class UserReindexingTaskSerializationTest {
    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");
    private static final Username USERNAME = Username.of("foo@apache.org");

    private final int successfullyReprocessedMailCount = 42;
    private final int failedReprocessedMailCount = 2;
    private final String serializedUserReindexingTask = "{\"type\": \"user-reindexing\", \"username\": \"foo@apache.org\", \"runningOptions\":{\"messagesPerSecond\":50, \"mode\":\"REBUILD_ALL\"}}";
    private final String legacySerializedUserReindexingTask = "{\"type\": \"user-reindexing\", \"username\": \"foo@apache.org\"}";
    private final String serializedAdditionalInformation = "{\"type\": \"user-reindexing\", \"user\": \"foo@apache.org\", \"successfullyReprocessedMailCount\":42,\"failedReprocessedMailCount\":2,\"messageFailures\":[{\"mailboxId\":\"1\",\"uids\":[10]},{\"mailboxId\":\"2\",\"uids\":[20]}],\"mailboxFailures\":[\"3\"], \"timestamp\":\"2018-11-13T12:00:55Z\", \"runningOptions\":{\"messagesPerSecond\":50, \"mode\":\"FIX_OUTDATED\"}}";
    private final String legacySerializedAdditionalInformation = "{\"type\": \"user-reindexing\", \"user\": \"foo@apache.org\", \"successfullyReprocessedMailCount\":42,\"failedReprocessedMailCount\":2,\"failures\":[{\"mailboxId\":\"1\",\"uids\":[10]},{\"mailboxId\":\"2\",\"uids\":[20]}], \"timestamp\":\"2018-11-13T12:00:55Z\"}";
    private final TestId mailboxId = TestId.of(1L);
    private final MessageUid messageUid = MessageUid.of(10L);
    private final TestId mailboxId2 = TestId.of(2L);
    private final MessageUid messageUid2 = MessageUid.of(20L);
    private final TestId mailboxId3 = TestId.of(3L);

    private ReIndexerPerformer reIndexerPerformer;
    private UserReindexingTask.Factory factory;
    private ReIndexingExecutionFailures reIndexingExecutionFailures;
    private RecursiveComparisonConfiguration recursiveComparisonConfiguration;

    @BeforeEach
    void setUp() {
        reIndexerPerformer = mock(ReIndexerPerformer.class);
        factory = new UserReindexingTask.Factory(reIndexerPerformer);

        reIndexingExecutionFailures = new ReIndexingExecutionFailures(ImmutableList.of(
                new ReIndexingExecutionFailures.ReIndexingFailure(mailboxId, messageUid),
                new ReIndexingExecutionFailures.ReIndexingFailure(mailboxId2, messageUid2)),
            ImmutableList.of(mailboxId3));

        recursiveComparisonConfiguration = new RecursiveComparisonConfiguration();
        recursiveComparisonConfiguration.registerComparatorForType(Comparator.comparingInt(AtomicInteger::get), AtomicInteger.class);
        recursiveComparisonConfiguration.registerComparatorForType(Comparator.comparingLong(AtomicLong::get), AtomicLong.class);
        recursiveComparisonConfiguration.registerEqualsForType((o, o2) -> o.get() == o2.get(), AtomicInteger.class);
        recursiveComparisonConfiguration.registerEqualsForType((o, o2) -> o.get() == o2.get(), AtomicLong.class);
        recursiveComparisonConfiguration.registerEqualsForType((o, o2) -> o.get() == o2.get(), AtomicBoolean.class);
    }

    @Test
    void userReindexingShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(UserReindexingTaskDTO.module(factory))
            .bean(new UserReindexingTask(reIndexerPerformer, USERNAME, RunningOptions.DEFAULT))
            .json(serializedUserReindexingTask)
            .verify();
    }

    @Test
    void legacyTaskshouldBeDeserializable() throws Exception {
        UserReindexingTask legacyTask = JsonGenericSerializer.forModules(UserReindexingTaskDTO.module(factory))
            .withoutNestedType()
            .deserialize(legacySerializedUserReindexingTask);

        UserReindexingTask expected = new UserReindexingTask(reIndexerPerformer, USERNAME, RunningOptions.DEFAULT);
        assertThat(legacyTask)
            .usingRecursiveComparison()
            .isEqualTo(expected);
    }

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        RunningOptions runningOptions = RunningOptions.builder()
            .mode(RunningOptions.Mode.FIX_OUTDATED)
            .build();
        UserReindexingTask.AdditionalInformation details = new UserReindexingTask.AdditionalInformation(USERNAME, successfullyReprocessedMailCount, failedReprocessedMailCount, reIndexingExecutionFailures, TIMESTAMP, runningOptions);
        JsonSerializationVerifier.dtoModule(UserReindexingTaskAdditionalInformationDTO.module(new TestId.Factory()))
            .bean(details)
            .json(serializedAdditionalInformation)
            .verify();
    }

    @Test
    void legacyAdditionalInformationShouldBeDeserializable() throws Exception {
        UserReindexingTask.AdditionalInformation legacyAdditionalInformation = JsonGenericSerializer.forModules(UserReindexingTaskAdditionalInformationDTO.module(new TestId.Factory()))
            .withoutNestedType()
            .deserialize(legacySerializedAdditionalInformation);

        UserReindexingTask.AdditionalInformation expected = new UserReindexingTask.AdditionalInformation(
            USERNAME,
            42,
            2,
            new ReIndexingExecutionFailures(ImmutableList.of(
                    new ReIndexingExecutionFailures.ReIndexingFailure(mailboxId, messageUid),
                    new ReIndexingExecutionFailures.ReIndexingFailure(mailboxId2, messageUid2)),
                ImmutableList.of()),
            TIMESTAMP,
            RunningOptions.DEFAULT
        );

        assertThat(legacyAdditionalInformation)
            .usingRecursiveComparison(recursiveComparisonConfiguration)
            .isEqualTo(expected);
    }
}

