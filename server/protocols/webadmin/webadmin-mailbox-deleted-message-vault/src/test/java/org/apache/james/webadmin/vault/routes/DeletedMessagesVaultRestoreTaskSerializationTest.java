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
package org.apache.james.webadmin.vault.routes;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.time.Instant;

import org.apache.james.core.Username;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.server.task.json.JsonTaskAdditionalInformationSerializer;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.task.Task;
import org.apache.james.vault.dto.query.QueryTranslator;
import org.apache.james.vault.search.CriterionFactory;
import org.apache.james.vault.search.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

class DeletedMessagesVaultRestoreTaskSerializationTest {

    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");

    private RestoreService exportService;
    private final TestId.Factory mailboxIdFactory = new TestId.Factory();
    private final QueryTranslator queryTranslator = new QueryTranslator(mailboxIdFactory);

    private JsonTaskSerializer taskSerializer;

    private static final String USERNAME = "james";
    private static final Username USERNAME_TO_RESTORE = Username.of(USERNAME);
    private static final Query QUERY = Query.of(CriterionFactory.hasAttachment(true));
    private static final DeletedMessagesVaultRestoreTask.AdditionalInformation DETAILS = new DeletedMessagesVaultRestoreTask.AdditionalInformation(USERNAME_TO_RESTORE,42, 10, TIMESTAMP);

    private static final String SERIALIZED_DELETE_MESSAGES_VAULT_RESTORE_TASK = "{\"type\":\"deleted-messages-restore\"," +
        "\"userToRestore\":\"james\"," +
        "\"query\":{\"combinator\":\"and\",\"criteria\":[{\"fieldName\":\"hasAttachment\",\"operator\":\"equals\",\"value\":\"true\"}]}" +
        "}";
    private static final String SERIALIZED_ADDITIONAL_INFORMATION_TASK = "{\"type\":\"deleted-messages-restore\", \"user\":\"james\",\"successfulRestoreCount\":42,\"errorRestoreCount\":10, \"timestamp\":\"2018-11-13T12:00:55Z\"}";

    private static final JsonTaskAdditionalInformationSerializer JSON_TASK_ADDITIONAL_INFORMATION_SERIALIZER = JsonTaskAdditionalInformationSerializer.of(DeletedMessagesVaultRestoreTaskAdditionalInformationDTO.MODULE);

    @BeforeEach
    void setUp() {
        exportService = mock(RestoreService.class);
        DeletedMessagesVaultRestoreTaskDTO.Factory factory = new DeletedMessagesVaultRestoreTaskDTO.Factory(exportService, queryTranslator);
        taskSerializer = JsonTaskSerializer.of(DeletedMessagesVaultRestoreTaskDTO.module(factory));
    }

    @Test
    void deleteMessagesVaultRestoreTaskShouldBeSerializable() throws JsonProcessingException {
        DeletedMessagesVaultRestoreTask task = new DeletedMessagesVaultRestoreTask(exportService, USERNAME_TO_RESTORE, QUERY);

        assertThatJson(taskSerializer.serialize(task))
            .isEqualTo(SERIALIZED_DELETE_MESSAGES_VAULT_RESTORE_TASK);
    }

    @Test
    void deleteMessagesVaultRestoreTaskShouldBeDeserializable() throws IOException {
        DeletedMessagesVaultRestoreTask task = new DeletedMessagesVaultRestoreTask(exportService, USERNAME_TO_RESTORE, QUERY);

        Task deserializedTask = taskSerializer.deserialize(SERIALIZED_DELETE_MESSAGES_VAULT_RESTORE_TASK);
        assertThat(deserializedTask)
            .isEqualToComparingOnlyGivenFields(task, "userToRestore");

        DeletedMessagesVaultRestoreTask deserializedRestoreTask = (DeletedMessagesVaultRestoreTask) deserializedTask;
        assertThat(queryTranslator.toDTO(deserializedRestoreTask.query)).isEqualTo(queryTranslator.toDTO(QUERY));
    }

    @Test
    void additionalInformationShouldBeSerializable() throws JsonProcessingException {
        assertThatJson(JSON_TASK_ADDITIONAL_INFORMATION_SERIALIZER.serialize(DETAILS)).isEqualTo(SERIALIZED_ADDITIONAL_INFORMATION_TASK);
    }

    @Test
    void additonalInformationShouldBeDeserializable() throws IOException {
        assertThat(JSON_TASK_ADDITIONAL_INFORMATION_SERIALIZER.deserialize(SERIALIZED_ADDITIONAL_INFORMATION_TASK))
            .isEqualToComparingFieldByField(DETAILS);
    }
}