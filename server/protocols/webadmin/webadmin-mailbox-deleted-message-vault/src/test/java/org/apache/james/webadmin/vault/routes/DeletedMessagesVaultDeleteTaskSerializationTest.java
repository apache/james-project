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

import static org.mockito.Mockito.mock;

import java.time.Instant;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.vault.DeletedMessageVault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeletedMessagesVaultDeleteTaskSerializationTest {
    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");

    private final Username username = Username.of("james");
    private final TestMessageId.Factory messageIdFactory = new TestMessageId.Factory();
    private final MessageId messageId = messageIdFactory.generate();
    private final String serializedDeleteMessagesVaultDeleteTask = "{\"type\": \"deleted-messages-delete\", \"userName\":\"james\", \"messageId\": \"" + messageId.serialize() + "\"}";
    private final String serializedAdditionalInformation = "{\"type\": \"deleted-messages-delete\", \"userName\":\"james\", \"messageId\": \"" + messageId.serialize() + "\", \"timestamp\":\"2018-11-13T12:00:55Z\"}";

    private DeletedMessagesVaultDeleteTask.Factory factory;
    private DeletedMessageVault deletedMessageVault;

    @BeforeEach
    void setUp() {
        deletedMessageVault = mock(DeletedMessageVault.class);
        factory = new DeletedMessagesVaultDeleteTask.Factory(deletedMessageVault, messageIdFactory);
    }

    @Test
    void deleteMessagesVaultDeleteTaskShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(DeletedMessagesVaultDeleteTaskDTO.module(factory))
            .bean(new DeletedMessagesVaultDeleteTask(deletedMessageVault, username, messageId))
            .json(serializedDeleteMessagesVaultDeleteTask)
            .verify();
    }

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(DeletedMessagesVaultDeleteTaskAdditionalInformationDTO.module(messageIdFactory))
            .bean(new DeletedMessagesVaultDeleteTask.AdditionalInformation(username, messageId, TIMESTAMP))
            .json(serializedAdditionalInformation)
            .verify();
    }
}