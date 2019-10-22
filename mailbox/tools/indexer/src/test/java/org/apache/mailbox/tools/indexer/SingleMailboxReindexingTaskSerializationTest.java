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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;

class SingleMailboxReindexingTaskSerializationTest {

    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");

    private ReIndexerPerformer reIndexerPerformer;
    private JsonTaskSerializer taskSerializer;
    private JsonTaskAdditionalInformationSerializer jsonAdditionalInformationSerializer;

    private final int successfullyReprocessedMailCount = 42;
    private final int failedReprocessedMailCount = 2;
    private  ReIndexingExecutionFailures reIndexingExecutionFailures;

    private final String serializedMailboxReindexingTask = "{\"type\": \"mailbox-reindexing\", \"mailboxId\": \"1\"}";

    private final String SERIALIZED_ADDITIONAL_INFORMATION = "{\"type\": \"mailbox-reindexing\", \"mailboxId\": \"1\", \"successfullyReprocessedMailCount\":42,\"failedReprocessedMailCount\":2,\"failures\":[{\"mailboxId\":\"1\",\"uids\":[10, 20]}], \"timestamp\":\"2018-11-13T12:00:55Z\"}";

    private final TestId mailboxId = TestId.of(1L);
    private final MessageUid messageUid = MessageUid.of(10L);
    private final MessageUid messageUid2 = MessageUid.of(20L);

    private final ReIndexingExecutionFailures.ReIndexingFailure indexingFailure = new ReIndexingExecutionFailures.ReIndexingFailure(mailboxId, messageUid);
    private final ReIndexingExecutionFailures.ReIndexingFailure indexingFailure2 = new ReIndexingExecutionFailures.ReIndexingFailure(mailboxId, messageUid2);
    private final List<ReIndexingExecutionFailures.ReIndexingFailure> failures = ImmutableList.of(indexingFailure, indexingFailure2);

    @BeforeEach
    void setUp() {
        reIndexerPerformer = mock(ReIndexerPerformer.class);
        SingleMailboxReindexingTask.Factory factory = new SingleMailboxReindexingTask.Factory(reIndexerPerformer, new TestId.Factory());
        taskSerializer = JsonTaskSerializer.of(SingleMailboxReindexingTaskDTO.module(factory));

        jsonAdditionalInformationSerializer = JsonTaskAdditionalInformationSerializer.of(
            SingleMailboxReindexingTaskAdditionalInformationDTO.serializationModule(new TestId.Factory()));

        reIndexingExecutionFailures = new ReIndexingExecutionFailures(ImmutableList.of(
            new ReIndexingExecutionFailures.ReIndexingFailure(mailboxId, messageUid),
            new ReIndexingExecutionFailures.ReIndexingFailure(mailboxId, messageUid2)));
    }

    @Test
    void singleMailboxReindexingShouldBeSerializable() throws JsonProcessingException {
        TestId mailboxId = TestId.of(1L);
        SingleMailboxReindexingTask task = new SingleMailboxReindexingTask(reIndexerPerformer, mailboxId);

        assertThatJson(taskSerializer.serialize(task))
            .isEqualTo(serializedMailboxReindexingTask);
    }

    @Test
    void singleMailboxReindexingShouldBeDeserializable() throws IOException {
        TestId mailboxId = TestId.of(1L);
        SingleMailboxReindexingTask task = new SingleMailboxReindexingTask(reIndexerPerformer, mailboxId);

        assertThat(taskSerializer.deserialize(serializedMailboxReindexingTask))
            .isEqualToComparingOnlyGivenFields(task, "reIndexerPerformer", "mailboxId");
    }

    @Test
    void additionalInformationShouldBeSerializable() throws JsonProcessingException {
        SingleMailboxReindexingTask.AdditionalInformation details = new SingleMailboxReindexingTask.AdditionalInformation(mailboxId, successfullyReprocessedMailCount, failedReprocessedMailCount, reIndexingExecutionFailures, TIMESTAMP);
        assertThatJson(jsonAdditionalInformationSerializer.serialize(details)).isEqualTo(SERIALIZED_ADDITIONAL_INFORMATION);
    }

    @Test
    void additonalInformationShouldBeDeserializable() throws IOException {
        SingleMailboxReindexingTask.AdditionalInformation details = new SingleMailboxReindexingTask.AdditionalInformation(mailboxId, successfullyReprocessedMailCount, failedReprocessedMailCount, reIndexingExecutionFailures, TIMESTAMP);
        assertThat(jsonAdditionalInformationSerializer.deserialize(SERIALIZED_ADDITIONAL_INFORMATION))
            .isEqualToComparingFieldByField(details);
    }
}

