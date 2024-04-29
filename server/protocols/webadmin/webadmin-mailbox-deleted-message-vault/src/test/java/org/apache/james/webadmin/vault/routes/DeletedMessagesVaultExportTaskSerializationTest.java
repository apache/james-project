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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import java.time.Instant;

import jakarta.mail.internet.AddressException;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.server.task.json.JsonTaskAdditionalInformationSerializer;
import org.apache.james.vault.dto.query.QueryTranslator;
import org.apache.james.vault.search.CriterionFactory;
import org.apache.james.vault.search.Query;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeletedMessagesVaultExportTaskSerializationTest {
    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");
    private static final String USERNAME = "james";
    private static final Username USERNAME_EXPORT_FROM = Username.of(USERNAME);
    private static final Query QUERY = Query.of(CriterionFactory.hasAttachment(true));
    private static final String SERIALIZED_DELETE_MESSAGES_VAULT_EXPORT_TASK = "{\"type\":\"deleted-messages-export\"," +
        "\"userExportFrom\":\"james\"," +
        "\"exportQuery\":{\"combinator\":\"and\",\"criteria\":[{\"fieldName\":\"hasAttachment\",\"operator\":\"equals\",\"value\":\"true\"}]}," +
        "\"exportTo\":\"james@apache.org\"}\n";
    private static final String SERIALIZED_ADDITIONAL_INFORMATION_TASK = "{\"type\":\"deleted-messages-export\", \"exportTo\":\"james@apache.org\",\"userExportFrom\":\"james\",\"totalExportedMessages\":42, \"timestamp\":\"2018-11-13T12:00:55Z\"}";
    private static MailAddress exportTo;
    private static DeletedMessagesVaultExportTask.AdditionalInformation details;

    private final TestId.Factory mailboxIdFactory = new TestId.Factory();
    private final QueryTranslator queryTranslator = new QueryTranslator(mailboxIdFactory);
    private ExportService exportService;
    private DeletedMessagesVaultExportTaskDTO.Factory factory;
    private JsonSerializationVerifier.EqualityTester<DeletedMessagesVaultExportTask> equalityTester;

    @BeforeAll
    static void init() throws AddressException {
        exportTo = new MailAddress("james@apache.org");
        details = new DeletedMessagesVaultExportTask.AdditionalInformation(USERNAME_EXPORT_FROM, exportTo, 42, TIMESTAMP);
    }

    @BeforeEach
    void setUp() {
        exportService = mock(ExportService.class);
        factory = new DeletedMessagesVaultExportTaskDTO.Factory(exportService, queryTranslator);
        equalityTester = (a, b) -> {
            assertThat(a).isEqualToComparingOnlyGivenFields(b, "userExportFrom", "exportTo");
            assertThat(queryTranslator.toDTO(a.getExportQuery())).isEqualTo(queryTranslator.toDTO(b.getExportQuery()));
        };
    }

    @Test
    void deleteMessagesVaultExportTaskShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(DeletedMessagesVaultExportTaskDTO.module(factory))
            .equalityTester(equalityTester)
            .bean(new DeletedMessagesVaultExportTask(exportService, USERNAME_EXPORT_FROM, QUERY, exportTo))
            .json(SERIALIZED_DELETE_MESSAGES_VAULT_EXPORT_TASK)
            .verify();
    }

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(DeletedMessagesVaultExportTaskAdditionalInformationDTO.module())
            .bean(details)
            .json(SERIALIZED_ADDITIONAL_INFORMATION_TASK)
            .verify();
    }

    @Test
    void additionalInformationWithInvalidMailAddressShouldThrow() {
        String invalidSerializedAdditionalInformationTask = "{\"type\":\"deleted-messages-export\",\"exportTo\":\"invalid\",\"userExportFrom\":\"james\",\"totalExportedMessages\":42}";
        assertThatCode(() -> JsonTaskAdditionalInformationSerializer.of(DeletedMessagesVaultExportTaskAdditionalInformationDTO.module())
                .deserialize(invalidSerializedAdditionalInformationTask))
            .hasCauseInstanceOf(AddressException.class);
    }
}