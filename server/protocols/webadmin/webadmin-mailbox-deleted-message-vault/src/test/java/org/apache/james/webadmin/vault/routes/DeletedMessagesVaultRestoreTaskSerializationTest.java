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
import static org.mockito.Mockito.mock;

import java.time.Instant;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.vault.dto.query.QueryTranslator;
import org.apache.james.vault.search.CriterionFactory;
import org.apache.james.vault.search.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeletedMessagesVaultRestoreTaskSerializationTest {
    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");
    private static final String USERNAME = "james";
    private static final Username USERNAME_TO_RESTORE = Username.of(USERNAME);
    private static final Query QUERY = Query.of(CriterionFactory.hasAttachment(true));
    private static final DeletedMessagesVaultRestoreTask.AdditionalInformation DETAILS = new DeletedMessagesVaultRestoreTask.AdditionalInformation(USERNAME_TO_RESTORE,42, 10, TIMESTAMP);
    private static final String SERIALIZED_DELETE_MESSAGES_VAULT_RESTORE_TASK = "{\"type\":\"deleted-messages-restore\"," +
        "\"userToRestore\":\"james\"," +
        "\"query\":{\"combinator\":\"and\",\"criteria\":[{\"fieldName\":\"hasAttachment\",\"operator\":\"equals\",\"value\":\"true\"}]}" +
        "}";
    private static final String SERIALIZED_ADDITIONAL_INFORMATION_TASK = "{\"type\":\"deleted-messages-restore\", \"user\":\"james\",\"successfulRestoreCount\":42,\"errorRestoreCount\":10, \"timestamp\":\"2018-11-13T12:00:55Z\"}";

    private final TestId.Factory mailboxIdFactory = new TestId.Factory();
    private final QueryTranslator queryTranslator = new QueryTranslator(mailboxIdFactory);
    private RestoreService exportService;
    private JsonSerializationVerifier.EqualityTester<DeletedMessagesVaultRestoreTask> equalityTester;
    private DeletedMessagesVaultRestoreTaskDTO.Factory factory;

    @BeforeEach
    void setUp() {
        exportService = mock(RestoreService.class);
        factory = new DeletedMessagesVaultRestoreTaskDTO.Factory(exportService, queryTranslator);
        equalityTester = (a, b) -> {
            assertThat(a).isEqualToComparingOnlyGivenFields(b, "userToRestore");
            assertThat(queryTranslator.toDTO(a.query)).isEqualTo(queryTranslator.toDTO(b.query));
        };
    }

    @Test
    void deleteMessagesVaultRestoreTaskShouldBeSerializable() throws Exception {
        DeletedMessagesVaultRestoreTask task = new DeletedMessagesVaultRestoreTask(exportService, USERNAME_TO_RESTORE, QUERY);
        JsonSerializationVerifier.dtoModule(DeletedMessagesVaultRestoreTaskDTO.module(factory))
            .equalityTester(equalityTester)
            .bean(task)
            .json(SERIALIZED_DELETE_MESSAGES_VAULT_RESTORE_TASK)
            .verify();
    }

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(DeletedMessagesVaultRestoreTaskAdditionalInformationDTO.module())
            .bean(DETAILS)
            .json(SERIALIZED_ADDITIONAL_INFORMATION_TASK)
            .verify();
    }
}