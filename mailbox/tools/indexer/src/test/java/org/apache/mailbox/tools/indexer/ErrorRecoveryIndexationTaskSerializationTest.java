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
import java.util.List;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.json.JsonGenericSerializer;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.indexer.ReIndexer.RunningOptions;
import org.apache.james.mailbox.indexer.ReIndexingExecutionFailures;
import org.apache.james.mailbox.model.TestId;
import org.apache.mailbox.tools.indexer.ReIndexingContextInformationDTO.ReIndexingContextInformationForErrorRecoveryIndexationTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class ErrorRecoveryIndexationTaskSerializationTest {
    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");

    private final TestId.Factory mailboxIdFactory = new TestId.Factory();
    private final int successfullyReprocessedMailCount = 42;
    private final int failedReprocessedMailCount = 2;
    private final String serializedErrorRecoveryReindexingTask = "{\"type\": \"error-recovery-indexation\"," +
        " \"previousMessageFailures\" : [{\"mailboxId\":\"1\",\"uids\":[10]},{\"mailboxId\":\"2\",\"uids\":[20]}], \"previousMailboxFailures\": [\"3\"], \"runningOptions\":{\"messagesPerSecond\":50, \"mode\":\"REBUILD_ALL\"}}";
    private final String legacySerializedErrorRecoveryReindexingTask = "{\"type\": \"error-recovery-indexation\"," +
        " \"previousFailures\" : [{\"mailboxId\":\"1\",\"uids\":[10]},{\"mailboxId\":\"2\",\"uids\":[20]}]}";
    private final String serializedAdditionalInformation = "{\"type\": \"error-recovery-indexation\", \"successfullyReprocessedMailCount\":42,\"failedReprocessedMailCount\":2,\"messageFailures\":[{\"mailboxId\":\"1\",\"uids\":[10]},{\"mailboxId\":\"2\",\"uids\":[20]}], \"mailboxFailures\":[\"3\"], \"timestamp\":\"2018-11-13T12:00:55Z\", \"runningOptions\":{\"messagesPerSecond\":50, \"mode\":\"REBUILD_ALL\"}}";
    private final String serializedAdditionalInformationWithCorrectMode = "{\"type\": \"error-recovery-indexation\", \"successfullyReprocessedMailCount\":42,\"failedReprocessedMailCount\":2,\"messageFailures\":[{\"mailboxId\":\"1\",\"uids\":[10]},{\"mailboxId\":\"2\",\"uids\":[20]}], \"mailboxFailures\":[\"3\"], \"timestamp\":\"2018-11-13T12:00:55Z\", \"runningOptions\":{\"messagesPerSecond\":50, \"mode\":\"FIX_OUTDATED\"}}";
    private final String legacySerializedAdditionalInformation = "{\"type\": \"error-recovery-indexation\", \"successfullyReprocessedMailCount\":42,\"failedReprocessedMailCount\":2,\"failures\":[{\"mailboxId\":\"1\",\"uids\":[10]},{\"mailboxId\":\"2\",\"uids\":[20]}], \"timestamp\":\"2018-11-13T12:00:55Z\"}";
    private final TestId mailboxId = TestId.of(1L);
    private final MessageUid messageUid = MessageUid.of(10L);
    private final ReIndexingExecutionFailures.ReIndexingFailure indexingFailure = new ReIndexingExecutionFailures.ReIndexingFailure(mailboxId, messageUid);
    private final TestId mailboxId2 = TestId.of(2L);
    private final MessageUid messageUid2 = MessageUid.of(20L);
    private final TestId mailboxId3 = TestId.of(3L);
    private final ReIndexingExecutionFailures.ReIndexingFailure indexingFailure2 = new ReIndexingExecutionFailures.ReIndexingFailure(mailboxId2, messageUid2);
    private final List<ReIndexingExecutionFailures.ReIndexingFailure> failures = ImmutableList.of(indexingFailure, indexingFailure2);
    private final ReIndexingExecutionFailures executionFailures = new ReIndexingExecutionFailures(failures, ImmutableList.of(mailboxId3));

    private ErrorRecoveryIndexationTask.Factory factory;
    private ReIndexerPerformer reIndexerPerformer;

    @BeforeEach
    void setUp() {
        reIndexerPerformer = mock(ReIndexerPerformer.class);
        factory = new ErrorRecoveryIndexationTask.Factory(reIndexerPerformer, mailboxIdFactory);
    }

    @Test
    void errorRecoveryReindexingShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(ErrorRecoveryIndexationTaskDTO.module(factory))
            .bean(new ErrorRecoveryIndexationTask(reIndexerPerformer, executionFailures, RunningOptions.DEFAULT))
            .json(serializedErrorRecoveryReindexingTask)
            .verify();
    }

    @Test
    void legacyTaskShouldBeDeserializable() throws Exception {
        ErrorRecoveryIndexationTask legacyTask = JsonGenericSerializer.forModules(ErrorRecoveryIndexationTaskDTO.module(factory))
            .withoutNestedType()
            .deserialize(legacySerializedErrorRecoveryReindexingTask);

        ErrorRecoveryIndexationTask expected = new ErrorRecoveryIndexationTask(reIndexerPerformer, new ReIndexingExecutionFailures(failures, ImmutableList.of()), RunningOptions.DEFAULT);

        assertThat(legacyTask)
            .usingRecursiveComparison(recursiveComparisonConfiguration)
            .isEqualTo(expected);
    }

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        ReIndexingContextInformationForErrorRecoveryIndexationTask details = new ReIndexingContextInformationForErrorRecoveryIndexationTask(successfullyReprocessedMailCount, failedReprocessedMailCount, executionFailures, TIMESTAMP, RunningOptions.DEFAULT);
        JsonSerializationVerifier.dtoModule(ReIndexingContextInformationForErrorRecoveryIndexationTask.module(mailboxIdFactory))
            .bean(details)
            .json(serializedAdditionalInformation)
            .verify();
    }

    @Test
    void additionalInformationWithCorrectModeShouldBeSerializable() throws Exception {
        RunningOptions runningOptions = RunningOptions.builder()
            .mode(RunningOptions.Mode.FIX_OUTDATED)
            .build();
        ReIndexingContextInformationForErrorRecoveryIndexationTask details = new ReIndexingContextInformationForErrorRecoveryIndexationTask(successfullyReprocessedMailCount, failedReprocessedMailCount, executionFailures, TIMESTAMP, runningOptions);
        JsonSerializationVerifier.dtoModule(ReIndexingContextInformationForErrorRecoveryIndexationTask.module(mailboxIdFactory))
            .bean(details)
            .json(serializedAdditionalInformationWithCorrectMode)
            .verify();
    }

    @Test
    void legacyAdditionalInformationShouldBeDeserializable() throws Exception {
        ReIndexingContextInformationForErrorRecoveryIndexationTask legacyAdditionalInformation = JsonGenericSerializer.forModules(ReIndexingContextInformationForErrorRecoveryIndexationTask.module(new TestId.Factory()))
            .withoutNestedType()
            .deserialize(legacySerializedAdditionalInformation);

        ReIndexingContextInformationDTO.ReIndexingContextInformationForFullReindexingTask expected = new ReIndexingContextInformationDTO.ReIndexingContextInformationForFullReindexingTask(
            42,
            2,
            new ReIndexingExecutionFailures(failures, ImmutableList.of()),
            TIMESTAMP,
            RunningOptions.DEFAULT
        );

        assertThat(legacyAdditionalInformation)
            .usingRecursiveComparison(recursiveComparisonConfiguration)
            .isEqualTo(expected);
    }
}

