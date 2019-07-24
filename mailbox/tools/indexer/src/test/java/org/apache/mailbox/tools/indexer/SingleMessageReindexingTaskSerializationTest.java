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

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

class SingleMessageReindexingTaskSerializationTest {

    private ReIndexerPerformer reIndexerPerformer;
    private JsonTaskSerializer taskSerializer;
    private final String serializedMessageReindexingTask = "{\"type\": \"messageReIndexing\", \"mailboxId\": \"1\", \"uid\": 10}";

    @BeforeEach
    void setUp() {
        reIndexerPerformer = mock(ReIndexerPerformer.class);
        SingleMessageReindexingTask.Factory factory = new SingleMessageReindexingTask.Factory(reIndexerPerformer, new TestId.Factory());
        taskSerializer = new JsonTaskSerializer(SingleMessageReindexingTask.MODULE.apply(factory));
    }

    @Test
    void singleMessageReindexingShouldBeSerializable() throws JsonProcessingException {
        TestId mailboxId = TestId.of(1L);
        MessageUid messageUid = MessageUid.of(10L);
        SingleMessageReindexingTask task = new SingleMessageReindexingTask(reIndexerPerformer, mailboxId, messageUid);

        assertThatJson(taskSerializer.serialize(task))
            .isEqualTo(serializedMessageReindexingTask);
    }

    @Test
    void singleMessageReindexingShouldBeDeserializable() throws IOException {
        TestId mailboxId = TestId.of(1L);
        MessageUid messageUid = MessageUid.of(10L);
        SingleMessageReindexingTask task = new SingleMessageReindexingTask(reIndexerPerformer, mailboxId, messageUid);

        assertThat(taskSerializer.deserialize(serializedMessageReindexingTask))
            .isEqualToComparingOnlyGivenFields(task, "reIndexerPerformer", "mailboxId", "uid");
    }
}

