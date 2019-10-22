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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.time.Instant;

import javax.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.server.task.json.JsonTaskAdditionalInformationSerializer;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.task.Task;
import org.apache.james.vault.dto.query.QueryTranslator;
import org.apache.james.vault.search.CriterionFactory;
import org.apache.james.vault.search.Query;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

class DeletedMessagesVaultExportTaskSerializationTest {

    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");

    private ExportService exportService;
    private final TestId.Factory mailboxIdFactory = new TestId.Factory();
    private final QueryTranslator queryTranslator = new QueryTranslator(mailboxIdFactory);

    private JsonTaskSerializer taskSerializer;

    private static final String username = "james";
    private static final Username USERNAME_EXPORT_FROM = Username.of(username);
    private static final Query QUERY = Query.of(CriterionFactory.hasAttachment(true));
    private static MailAddress exportTo;
    private static DeletedMessagesVaultExportTask.AdditionalInformation details;

    private static final String serializedDeleteMessagesVaultExportTask = "{\"type\":\"deleted-messages-export\"," +
        "\"userExportFrom\":\"james\"," +
        "\"exportQuery\":{\"combinator\":\"and\",\"criteria\":[{\"fieldName\":\"hasAttachment\",\"operator\":\"equals\",\"value\":\"true\"}]}," +
        "\"exportTo\":\"james@apache.org\"}\n";
    private static final String SERIALIZED_ADDITIONAL_INFORMATION_TASK = "{\"type\":\"deleted-messages-export\", \"exportTo\":\"james@apache.org\",\"userExportFrom\":\"james\",\"totalExportedMessages\":42, \"timestamp\":\"2018-11-13T12:00:55Z\"}";

    private static final JsonTaskAdditionalInformationSerializer JSON_TASK_ADDITIONAL_INFORMATION_SERIALIZER = JsonTaskAdditionalInformationSerializer.of(DeletedMessagesVaultExportTaskAdditionalInformationDTO.MODULE);

    @BeforeAll
    static void init() throws AddressException {
        exportTo = new MailAddress("james@apache.org");
        details = new DeletedMessagesVaultExportTask.AdditionalInformation(USERNAME_EXPORT_FROM, exportTo, 42, TIMESTAMP);
    }

    @BeforeEach
    void setUp() {
        exportService = mock(ExportService.class);
        DeletedMessagesVaultExportTaskDTO.Factory factory = new DeletedMessagesVaultExportTaskDTO.Factory(exportService, queryTranslator);
        taskSerializer = JsonTaskSerializer.of(DeletedMessagesVaultExportTaskDTO.module(factory));
    }

    @Test
    void deleteMessagesVaultExportTaskShouldBeSerializable() throws JsonProcessingException {
        DeletedMessagesVaultExportTask task = new DeletedMessagesVaultExportTask(exportService, USERNAME_EXPORT_FROM, QUERY, exportTo);

        assertThatJson(taskSerializer.serialize(task))
            .isEqualTo(serializedDeleteMessagesVaultExportTask);
    }

    @Test
    void deleteMessagesVaultExportTaskShouldBeDeserializable() throws IOException {
        DeletedMessagesVaultExportTask task = new DeletedMessagesVaultExportTask(exportService, USERNAME_EXPORT_FROM, QUERY, exportTo);

        Task deserializedTask = taskSerializer.deserialize(serializedDeleteMessagesVaultExportTask);
        assertThat(deserializedTask)
            .isEqualToComparingOnlyGivenFields(task, "userExportFrom", "exportTo");

        DeletedMessagesVaultExportTask deserializedExportTask = (DeletedMessagesVaultExportTask) deserializedTask;
        assertThat(queryTranslator.toDTO(deserializedExportTask.exportQuery)).isEqualTo(queryTranslator.toDTO(QUERY));
    }

    @Test
    void additionalInformationShouldBeSerializable() throws JsonProcessingException {
        assertThatJson(JSON_TASK_ADDITIONAL_INFORMATION_SERIALIZER.serialize(details)).isEqualTo(SERIALIZED_ADDITIONAL_INFORMATION_TASK);
    }

    @Test
    void additionalInformationShouldBeDeserializable() throws IOException {
        assertThat(JSON_TASK_ADDITIONAL_INFORMATION_SERIALIZER.deserialize(SERIALIZED_ADDITIONAL_INFORMATION_TASK))
            .isEqualToComparingFieldByField(details);
    }

    @Test
    void additionalInformationWithInvalidMailAddressShouldThrow() throws IOException {
        String invalidSerializedAdditionalInformationTask = "{\"type\":\"deleted-messages-export\",\"exportTo\":\"invalid\",\"userExportFrom\":\"james\",\"totalExportedMessages\":42}";;
        assertThatCode(() -> JSON_TASK_ADDITIONAL_INFORMATION_SERIALIZER.deserialize(invalidSerializedAdditionalInformationTask))
            .hasCauseInstanceOf(AddressException.class);
    }
}