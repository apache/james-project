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

package org.apache.james.mailbox.cassandra.mail.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;

import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.server.task.json.JsonTaskSerializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import org.junit.jupiter.api.Test;

class MailboxMergingTaskTest {
    private static final CassandraId.Factory CASSANDRA_ID_FACTORY = new CassandraId.Factory();
    private static final String SERIALIZED = "{\"type\":\"mailboxMerging\",\"totalMessageCount\":0,\"oldMailboxId\":\"3b8e5f90-b94f-20f8-ce7b-3c4aad93b90c\",\"newMailboxId\":\"2c7f4081-aa30-11e9-bf6c-2d3b9e84aafd\"}";
    private static final MailboxMergingTaskRunner TASK_RUNNER = mock(MailboxMergingTaskRunner.class);
    private static final MailboxMergingTask TASK = new MailboxMergingTask(TASK_RUNNER, 0L, CASSANDRA_ID_FACTORY.fromString("3b8e5f90-b94f-20f8-ce7b-3c4aad93b90c"), CASSANDRA_ID_FACTORY.fromString("2c7f4081-aa30-11e9-bf6c-2d3b9e84aafd"));
    private static final JsonTaskSerializer TESTEE = new JsonTaskSerializer(MailboxMergingTaskDTO.MODULE.apply(TASK_RUNNER));

    @Test
    void taskShouldBeSerializable() throws JsonProcessingException {
        JsonAssertions.assertThatJson(TESTEE.serialize(TASK))
            .isEqualTo(SERIALIZED);
    }

    @Test
    void taskShouldBeDeserializable() throws IOException {
        assertThat(TESTEE.deserialize(SERIALIZED))
            .isEqualToComparingFieldByFieldRecursively(TASK);
    }
}