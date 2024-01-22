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

import static org.apache.james.JsonSerializationVerifier.recursiveComparisonConfiguration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Instant;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.json.JsonGenericSerializer;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.indexer.ReIndexer.RunningOptions;
import org.apache.james.mailbox.indexer.ReIndexingExecutionFailures;
import org.apache.james.mailbox.model.TestId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class SingleMailboxReindexingTaskSerializationTest {
    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");

    private final int successfullyReprocessedMailCount = 42;
    private final int failedReprocessedMailCount = 2;

    private final String serializedMailboxReindexingTask = "{\"type\": \"mailbox-reindexing\", \"mailboxId\": \"1\", \"runningOptions\":{\"messagesPerSecond\":50, \"mode\":\"REBUILD_ALL\"}}";
    private final String legacySerializedMailboxReindexingTask = "{\"type\": \"mailbox-reindexing\", \"mailboxId\": \"1\"}";
    private final String serializedAdditionalInformation = "{\"type\": \"mailbox-reindexing\", \"mailboxId\": \"1\", \"successfullyReprocessedMailCount\":42,\"failedReprocessedMailCount\":2,\"messageFailures\":[{\"mailboxId\":\"1\",\"uids\":[10, 20]}], \"mailboxFailures\": [\"2\"],\"timestamp\":\"2018-11-13T12:00:55Z\", \"runningOptions\":{\"messagesPerSecond\":50, \"mode\":\"FIX_OUTDATED\"}}";
    private final String legacySerializedAdditionalInformation = "{\"type\": \"mailbox-reindexing\", \"mailboxId\": \"1\", \"successfullyReprocessedMailCount\":42,\"failedReprocessedMailCount\":2,\"failures\":[{\"mailboxId\":\"1\",\"uids\":[10, 20]}], \"timestamp\":\"2018-11-13T12:00:55Z\"}";

    private final TestId mailboxId = TestId.of(1L);
    private final TestId mailboxId2 = TestId.of(2L);
    private final MessageUid messageUid = MessageUid.of(10L);
    private final MessageUid messageUid2 = MessageUid.of(20L);

    private SingleMailboxReindexingTask.Factory factory;
    private ReIndexingExecutionFailures reIndexingExecutionFailures;
    private ReIndexerPerformer reIndexerPerformer;

    @BeforeEach
    void setUp() {
        reIndexerPerformer = mock(ReIndexerPerformer.class);
        factory = new SingleMailboxReindexingTask.Factory(reIndexerPerformer, new TestId.Factory());
        reIndexingExecutionFailures = new ReIndexingExecutionFailures(ImmutableList.of(
            new ReIndexingExecutionFailures.ReIndexingFailure(mailboxId, messageUid),
            new ReIndexingExecutionFailures.ReIndexingFailure(mailboxId, messageUid2)),
        ImmutableList.of(mailboxId2));
    }

    @Test
    void singleMailboxReindexingShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(SingleMailboxReindexingTaskDTO.module(factory))
            .bean(new SingleMailboxReindexingTask(reIndexerPerformer, mailboxId, RunningOptions.DEFAULT))
            .json(serializedMailboxReindexingTask)
            .verify();
    }

    @Test
    void legacyTaskshouldBeDeserializable() throws Exception {
        SingleMailboxReindexingTask legacyTask = JsonGenericSerializer.forModules(SingleMailboxReindexingTaskDTO.module(factory))
            .withoutNestedType()
            .deserialize(legacySerializedMailboxReindexingTask);

        SingleMailboxReindexingTask expected = new SingleMailboxReindexingTask(reIndexerPerformer, mailboxId, RunningOptions.DEFAULT);

        assertThat(legacyTask)
            .usingRecursiveComparison(recursiveComparisonConfiguration)
            .isEqualTo(expected);
    }

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        RunningOptions runningOptions = RunningOptions.builder()
            .mode(RunningOptions.Mode.FIX_OUTDATED)
            .build();
        SingleMailboxReindexingTask.AdditionalInformation details = new SingleMailboxReindexingTask.AdditionalInformation(mailboxId, successfullyReprocessedMailCount, failedReprocessedMailCount, reIndexingExecutionFailures, TIMESTAMP, runningOptions);

        JsonSerializationVerifier.dtoModule(SingleMailboxReindexingTaskAdditionalInformationDTO.module(new TestId.Factory()))
            .bean(details)
            .json(serializedAdditionalInformation)
            .verify();
    }

    @Test
    void legacyAdditionalInformationShouldBeDeserializable() throws Exception {
        SingleMailboxReindexingTask.AdditionalInformation legacyAdditionalInformation = JsonGenericSerializer.forModules(SingleMailboxReindexingTaskAdditionalInformationDTO.module(new TestId.Factory()))
            .withoutNestedType()
            .deserialize(legacySerializedAdditionalInformation);

        SingleMailboxReindexingTask.AdditionalInformation expected = new SingleMailboxReindexingTask.AdditionalInformation(
            mailboxId,
            42,
            2,
            new ReIndexingExecutionFailures(ImmutableList.of(
                new ReIndexingExecutionFailures.ReIndexingFailure(mailboxId, messageUid),
                new ReIndexingExecutionFailures.ReIndexingFailure(mailboxId, messageUid2)),
                ImmutableList.of()),
            TIMESTAMP,
            RunningOptions.DEFAULT
        );

        assertThat(legacyAdditionalInformation)
            .usingRecursiveComparison(recursiveComparisonConfiguration)
            .isEqualTo(expected);
    }
}

