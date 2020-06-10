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
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestMessageId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MessageIdReindexingTaskSerializationTest {
    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");

    private final String serializedMessageIdReIndexingTask = "{\"type\": \"messageId-reindexing\", \"messageId\": \"1\"}";
    private final String serializedAdditionalInformation = "{\"type\": \"messageId-reindexing\", \"messageId\": \"1\", \"timestamp\":\"2018-11-13T12:00:55Z\"}";

    private ReIndexerPerformer reIndexerPerformer;
    private MessageId.Factory messageIdFactory;
    private MessageIdReIndexingTask.Factory factory;

    @BeforeEach
    void setUp() {
        messageIdFactory = new TestMessageId.Factory();
        reIndexerPerformer = mock(ReIndexerPerformer.class);
        factory = new MessageIdReIndexingTask.Factory(reIndexerPerformer, messageIdFactory);
    }

    @Test
    void messageIdReindexingShouldBeSerializable() throws Exception {
        MessageId messageId = messageIdFactory.fromString("1");
        JsonSerializationVerifier.dtoModule(MessageIdReindexingTaskDTO.module(factory))
            .bean(new MessageIdReIndexingTask(reIndexerPerformer, messageId))
            .json(serializedMessageIdReIndexingTask)
            .verify();
    }

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        MessageId messageId = messageIdFactory.fromString("1");
        MessageIdReIndexingTask.AdditionalInformation details = new MessageIdReIndexingTask.AdditionalInformation(messageId, TIMESTAMP);

        JsonSerializationVerifier.dtoModule(MessageIdReindexingTaskAdditionalInformationDTO.module(messageIdFactory))
            .bean(details)
            .json(serializedAdditionalInformation)
            .verify();
    }
}

