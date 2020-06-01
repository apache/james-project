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
package org.apache.james.webadmin.dto;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.apache.james.json.JsonGenericSerializer;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.indexer.ReIndexingExecutionFailures;
import org.apache.james.mailbox.model.TestId;
import org.apache.mailbox.tools.indexer.ReprocessingContextInformationDTO;
import org.apache.mailbox.tools.indexer.SingleMailboxReindexingTask;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.google.common.collect.ImmutableList;

class WebAdminSingleMailboxReprocessingDTOTest {
    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");

    private final String serializedAdditionalInformation = "{\"type\":\"mailbox-reindexing\",\"mailboxId\":\"1\",\"successfullyReprocessedMailCount\":42,\"failedReprocessedMailCount\":2,\"failures\":{\"1\":[{\"uid\":10}],\"2\":[{\"uid\":20}]},\"timestamp\":\"2018-11-13T12:00:55Z\"}";

    private final TestId mailboxId = TestId.of(1L);
    private final MessageUid messageUid = MessageUid.of(10L);
    private final ReIndexingExecutionFailures.ReIndexingFailure indexingFailure = new ReIndexingExecutionFailures.ReIndexingFailure(mailboxId, messageUid);
    private final TestId mailboxId2 = TestId.of(2L);
    private final MessageUid messageUid2 = MessageUid.of(20L);
    private final ReIndexingExecutionFailures.ReIndexingFailure indexingFailure2 = new ReIndexingExecutionFailures.ReIndexingFailure(mailboxId2, messageUid2);
    private final List<ReIndexingExecutionFailures.ReIndexingFailure> failures = ImmutableList.of(indexingFailure, indexingFailure2);
    private final ReIndexingExecutionFailures executionFailures = new ReIndexingExecutionFailures(failures);

    @Test
    void shouldSerializeAdditionalInformation() throws Exception {
        SingleMailboxReindexingTask.AdditionalInformation domainObject =
            new SingleMailboxReindexingTask.AdditionalInformation(
                mailboxId,
                42,
                2,
                executionFailures,
                TIMESTAMP,
                ReIndexer.RunningOptions.DEFAULT);

        String json =
            JsonGenericSerializer.forModules(WebAdminSingleMailboxReindexingTaskAdditionalInformationDTO
                .serializationModule(new TestId.Factory()))
                .withoutNestedType()
                .serialize(domainObject);

        assertThatJson(json)
            .isEqualTo(serializedAdditionalInformation);
    }

    @Test
    void deserializeShouldNotBeSupported() {
        assertThatThrownBy(() -> JsonGenericSerializer.forModules(WebAdminSingleMailboxReindexingTaskAdditionalInformationDTO
            .serializationModule(new TestId.Factory()))
            .withoutNestedType()
            .deserialize(serializedAdditionalInformation))
            .isInstanceOf(InvalidDefinitionException.class);
    }
}
