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

import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.server.task.json.JsonTaskAdditionalInformationSerializer;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

class MessageIdReindexingTaskSerializationTest {

    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");

    private ReIndexerPerformer reIndexerPerformer;
    private MessageId.Factory messageIdFactory;
    private JsonTaskSerializer taskSerializer;
    private final String serializedMessageIdReIndexingTask = "{\"type\": \"messageId-reindexing\", \"messageId\": \"1\"}";
    private final String SERIALIZED_ADDITIONAL_INFORMATION = "{\"type\": \"messageId-reindexing\", \"messageId\": \"1\", \"timestamp\":\"2018-11-13T12:00:55Z\"}";

    private JsonTaskAdditionalInformationSerializer jsonAdditionalInformationSerializer;

    @BeforeEach
    void setUp() {
        messageIdFactory = new TestMessageId.Factory();
        jsonAdditionalInformationSerializer = JsonTaskAdditionalInformationSerializer.of(
            MessageIdReindexingTaskAdditionalInformationDTO.serializationModule(messageIdFactory));
        reIndexerPerformer = mock(ReIndexerPerformer.class);
        MessageIdReIndexingTask.Factory factory = new MessageIdReIndexingTask.Factory(reIndexerPerformer, messageIdFactory);
        taskSerializer = JsonTaskSerializer.of(MessageIdReindexingTaskDTO.module(factory));
    }

    @Test
    void messageIdReindexingShouldBeSerializable() throws JsonProcessingException {
        MessageId messageId = messageIdFactory.fromString("1");
        MessageIdReIndexingTask task = new MessageIdReIndexingTask(reIndexerPerformer, messageId);

        assertThatJson(taskSerializer.serialize(task))
            .isEqualTo(serializedMessageIdReIndexingTask);
    }

    @Test
    void messageIdReindexingShouldBeDeserializable() throws IOException {
        MessageId messageId = messageIdFactory.fromString("1");
        MessageIdReIndexingTask task = new MessageIdReIndexingTask(reIndexerPerformer, messageId);

        assertThat(taskSerializer.deserialize(serializedMessageIdReIndexingTask))
            .isEqualToComparingOnlyGivenFields(task, "messageId");
    }

    @Test
    void additionalInformationShouldBeSerializable() throws JsonProcessingException {
        MessageId messageId = messageIdFactory.fromString("1");
        MessageIdReIndexingTask.AdditionalInformation details = new MessageIdReIndexingTask.AdditionalInformation(messageId, TIMESTAMP);
        assertThatJson(jsonAdditionalInformationSerializer.serialize(details)).isEqualTo(SERIALIZED_ADDITIONAL_INFORMATION);
    }

    @Test
    void additonalInformationShouldBeDeserializable() throws IOException {
        MessageId messageId = messageIdFactory.fromString("1");
        MessageIdReIndexingTask.AdditionalInformation details = new MessageIdReIndexingTask.AdditionalInformation(messageId, TIMESTAMP);
        assertThat(jsonAdditionalInformationSerializer.deserialize(SERIALIZED_ADDITIONAL_INFORMATION))
            .isEqualToComparingFieldByField(details);
    }
}

