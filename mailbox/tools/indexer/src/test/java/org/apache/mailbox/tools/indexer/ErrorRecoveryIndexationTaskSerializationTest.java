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

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.indexer.ReIndexingExecutionFailures;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.server.task.json.JsonTaskAdditionalInformationSerializer;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.task.Task;
import org.apache.mailbox.tools.indexer.ReprocessingContextInformationDTO.ReprocessingContextInformationForErrorRecoveryIndexationTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;

class ErrorRecoveryIndexationTaskSerializationTest {

    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");
    private final TestId.Factory mailboxIdFactory = new TestId.Factory();
    private ReIndexerPerformer reIndexerPerformer;
    private JsonTaskSerializer taskSerializer;
    private JsonTaskAdditionalInformationSerializer jsonAdditionalInformationSerializer;

    private final int successfullyReprocessedMailCount = 42;
    private final int failedReprocessedMailCount = 2;
    private  ReIndexingExecutionFailures reIndexingExecutionFailures;

    private final String serializedErrorRecoveryReindexingTask = "{\"type\": \"error-recovery-indexation\"," +
        " \"previousFailures\" : [{\"mailboxId\":\"1\",\"uids\":[10]},{\"mailboxId\":\"2\",\"uids\":[20]}]}";
    private final String SERIALIZED_ADDITIONAL_INFORMATION = "{\"type\": \"error-recovery-indexation\", \"successfullyReprocessedMailCount\":42,\"failedReprocessedMailCount\":2,\"failures\":[{\"mailboxId\":\"1\",\"uids\":[10]},{\"mailboxId\":\"2\",\"uids\":[20]}], \"timestamp\":\"2018-11-13T12:00:55Z\"}";

    private final TestId mailboxId = TestId.of(1L);
    private final MessageUid messageUid = MessageUid.of(10L);
    private final ReIndexingExecutionFailures.ReIndexingFailure indexingFailure = new ReIndexingExecutionFailures.ReIndexingFailure(mailboxId, messageUid);

    private final TestId mailboxId2 = TestId.of(2L);
    private final MessageUid messageUid2 = MessageUid.of(20L);

    private final ReIndexingExecutionFailures.ReIndexingFailure indexingFailure2 = new ReIndexingExecutionFailures.ReIndexingFailure(mailboxId2, messageUid2);
    private final List<ReIndexingExecutionFailures.ReIndexingFailure> failures = ImmutableList.of(indexingFailure, indexingFailure2);
    private final ReIndexingExecutionFailures executionFailures = new ReIndexingExecutionFailures(failures);

    @BeforeEach
    void setUp() {
        reIndexerPerformer = mock(ReIndexerPerformer.class);
        ErrorRecoveryIndexationTask.Factory factory = new ErrorRecoveryIndexationTask.Factory(reIndexerPerformer, mailboxIdFactory);
        taskSerializer = JsonTaskSerializer.of(ErrorRecoveryIndexationTaskDTO.module(factory));

        jsonAdditionalInformationSerializer = JsonTaskAdditionalInformationSerializer.of(
            ReprocessingContextInformationForErrorRecoveryIndexationTask.serializationModule(mailboxIdFactory));

        reIndexingExecutionFailures = new ReIndexingExecutionFailures(ImmutableList.of(
            new ReIndexingExecutionFailures.ReIndexingFailure(mailboxId, messageUid),
            new ReIndexingExecutionFailures.ReIndexingFailure(mailboxId2, messageUid2)));
    }

    @Test
    void errorRecoveryReindexingShouldBeSerializable() throws JsonProcessingException {
        ErrorRecoveryIndexationTask task = new ErrorRecoveryIndexationTask(reIndexerPerformer, executionFailures);

        assertThatJson(taskSerializer.serialize(task))
            .isEqualTo(serializedErrorRecoveryReindexingTask);
    }

    @Test
    void errorRecoveryReindexingShouldBeDeserializable() throws IOException {
        ErrorRecoveryIndexationTask task = new ErrorRecoveryIndexationTask(reIndexerPerformer, executionFailures);

        Task deserializedTask = taskSerializer.deserialize(serializedErrorRecoveryReindexingTask);
        assertThat(task instanceof ErrorRecoveryIndexationTask);
        assertThat(deserializedTask)
            .isEqualToComparingOnlyGivenFields(task, "reIndexerPerformer", "previousFailures");
    }

    @Test
    void additionalInformationShouldBeSerializable() throws JsonProcessingException {
        ReprocessingContextInformation details = new ReprocessingContextInformationForErrorRecoveryIndexationTask(successfullyReprocessedMailCount, failedReprocessedMailCount, reIndexingExecutionFailures, TIMESTAMP);
        assertThatJson(jsonAdditionalInformationSerializer.serialize(details)).isEqualTo(SERIALIZED_ADDITIONAL_INFORMATION);
    }

    @Test
    void additonalInformationShouldBeDeserializable() throws IOException {
        ReprocessingContextInformation details = new ReprocessingContextInformationForErrorRecoveryIndexationTask(successfullyReprocessedMailCount, failedReprocessedMailCount, reIndexingExecutionFailures, TIMESTAMP);
        assertThat(jsonAdditionalInformationSerializer.deserialize(SERIALIZED_ADDITIONAL_INFORMATION))
            .isEqualToComparingFieldByField(details);
    }

}

