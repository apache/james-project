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

import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.indexer.ReIndexingExecutionFailures;
import org.apache.james.mailbox.model.TestId;
import org.apache.mailbox.tools.indexer.ReprocessingContextInformationDTO.ReprocessingContextInformationForErrorRecoveryIndexationTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class ErrorRecoveryIndexationTaskSerializationTest {
    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");

    private final TestId.Factory mailboxIdFactory = new TestId.Factory();
    private final int successfullyReprocessedMailCount = 42;
    private final int failedReprocessedMailCount = 2;
    private final String serializedErrorRecoveryReindexingTask = "{\"type\": \"error-recovery-indexation\"," +
        " \"previousFailures\" : [{\"mailboxId\":\"1\",\"uids\":[10]},{\"mailboxId\":\"2\",\"uids\":[20]}]}";
    private final String serializedAdditionalInformation = "{\"type\": \"error-recovery-indexation\", \"successfullyReprocessedMailCount\":42,\"failedReprocessedMailCount\":2,\"failures\":[{\"mailboxId\":\"1\",\"uids\":[10]},{\"mailboxId\":\"2\",\"uids\":[20]}], \"timestamp\":\"2018-11-13T12:00:55Z\"}";
    private final TestId mailboxId = TestId.of(1L);
    private final MessageUid messageUid = MessageUid.of(10L);
    private final ReIndexingExecutionFailures.ReIndexingFailure indexingFailure = new ReIndexingExecutionFailures.ReIndexingFailure(mailboxId, messageUid);
    private final TestId mailboxId2 = TestId.of(2L);
    private final MessageUid messageUid2 = MessageUid.of(20L);
    private final ReIndexingExecutionFailures.ReIndexingFailure indexingFailure2 = new ReIndexingExecutionFailures.ReIndexingFailure(mailboxId2, messageUid2);
    private final List<ReIndexingExecutionFailures.ReIndexingFailure> failures = ImmutableList.of(indexingFailure, indexingFailure2);
    private final ReIndexingExecutionFailures executionFailures = new ReIndexingExecutionFailures(failures);

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
            .bean(new ErrorRecoveryIndexationTask(reIndexerPerformer, executionFailures))
            .json(serializedErrorRecoveryReindexingTask)
            .verify();
    }

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        ReprocessingContextInformationForErrorRecoveryIndexationTask details = new ReprocessingContextInformationForErrorRecoveryIndexationTask(successfullyReprocessedMailCount, failedReprocessedMailCount, executionFailures, TIMESTAMP);
        JsonSerializationVerifier.dtoModule(ReprocessingContextInformationForErrorRecoveryIndexationTask.serializationModule(mailboxIdFactory))
            .bean(details)
            .json(serializedAdditionalInformation)
            .verify();
    }
}

