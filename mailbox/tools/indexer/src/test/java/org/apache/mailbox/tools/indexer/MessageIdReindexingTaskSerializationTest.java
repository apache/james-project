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

import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

class MessageIdReindexingTaskSerializationTest {

    private ReIndexerPerformer reIndexerPerformer;
    private MessageId.Factory messageIdFactory;
    private JsonTaskSerializer taskSerializer;
    private final String serializedMessageIdReIndexingTask = "{\"type\": \"MessageIdReIndexingTask\", \"messageId\": \"1\"}";

    @BeforeEach
    void setUp() {
        messageIdFactory = new TestMessageId.Factory();
        reIndexerPerformer = mock(ReIndexerPerformer.class);
        MessageIdReIndexingTask.Factory factory = new MessageIdReIndexingTask.Factory(reIndexerPerformer, messageIdFactory);
        taskSerializer = new JsonTaskSerializer(MessageIdReindexingTaskDTO.MODULE.apply(factory));
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
}

