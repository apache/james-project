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

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.TestId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SingleMessageReindexingTaskSerializationTest {
    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");

    private final TestId.Factory mailboxIdFactory = new TestId.Factory();
    private final String serializedMessageReindexingTask = "{\"type\": \"message-reindexing\", \"mailboxId\": \"1\", \"uid\": 10}";
    private final String serializedAdditionalInformation = "{\"type\": \"message-reindexing\", \"mailboxId\": \"1\", \"uid\": 10, \"timestamp\":\"2018-11-13T12:00:55Z\"}";
    private final TestId mailboxId = TestId.of(1L);
    private final MessageUid messageUid = MessageUid.of(10L);

    private SingleMessageReindexingTask.Factory factory;
    private ReIndexerPerformer reIndexerPerformer;

    @BeforeEach
    void setUp() {
        reIndexerPerformer = mock(ReIndexerPerformer.class);
        factory = new SingleMessageReindexingTask.Factory(reIndexerPerformer, mailboxIdFactory);
    }

    @Test
    void singleMessageReindexingShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(SingleMessageReindexingTaskDTO.module(factory))
            .bean(new SingleMessageReindexingTask(reIndexerPerformer, mailboxId, messageUid))
            .json(serializedMessageReindexingTask)
            .verify();
    }

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        SingleMessageReindexingTask.AdditionalInformation details = new SingleMessageReindexingTask.AdditionalInformation(mailboxId, messageUid, TIMESTAMP);
        JsonSerializationVerifier.dtoModule(SingleMessageReindexingTaskAdditionalInformationDTO.serializationModule(mailboxIdFactory))
            .bean(details)
            .json(serializedAdditionalInformation)
            .verify();
    }
}

