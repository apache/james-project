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
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.server.task.json.JsonTaskAdditionalInformationSerializer;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.vault.DeletedMessageVault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

class DeletedMessagesVaultDeleteTaskSerializationTest {

    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");

    private DeletedMessageVault deletedMessageVault;
    private JsonTaskSerializer taskSerializer;
    private final Username username = Username.of("james");

    private final TestMessageId.Factory messageIdFactory = new TestMessageId.Factory();
    private final MessageId messageId = messageIdFactory.generate();

    private final String serializedDeleteMessagesVaultDeleteTask = "{\"type\": \"deleted-messages-delete\", \"userName\":\"james\", \"messageId\": \"" + messageId.serialize() + "\"}";
    private final String serializedAdditionalInformation = "{\"type\": \"deleted-messages-delete\", \"userName\":\"james\", \"messageId\": \"" + messageId.serialize() + "\", \"timestamp\":\"2018-11-13T12:00:55Z\"}";

    private JsonTaskAdditionalInformationSerializer jsonAdditionalInformationSerializer;

    @BeforeEach
    void setUp() {
        deletedMessageVault = mock(DeletedMessageVault.class);
        DeletedMessagesVaultDeleteTask.Factory factory = new DeletedMessagesVaultDeleteTask.Factory(deletedMessageVault, messageIdFactory);
        taskSerializer = JsonTaskSerializer.of(DeletedMessagesVaultDeleteTaskDTO.module(factory));
        jsonAdditionalInformationSerializer = JsonTaskAdditionalInformationSerializer.of(DeletedMessagesVaultDeleteTaskAdditionalInformationDTO.serializationModule(messageIdFactory));
    }

    @Test
    void deleteMessagesVaultDeleteTaskShouldBeSerializable() throws JsonProcessingException {
        DeletedMessagesVaultDeleteTask task = new DeletedMessagesVaultDeleteTask(deletedMessageVault, username, messageId);

        assertThatJson(taskSerializer.serialize(task))
            .isEqualTo(serializedDeleteMessagesVaultDeleteTask);
    }

    @Test
    void deleteMessagesVaultDeleteTaskShouldBeDeserializable() throws IOException {
        DeletedMessagesVaultDeleteTask task = new DeletedMessagesVaultDeleteTask(deletedMessageVault, username, messageId);

        assertThat(taskSerializer.deserialize(serializedDeleteMessagesVaultDeleteTask))
            .isEqualToComparingOnlyGivenFields(task, "username", "messageId");
    }


    @Test
    void additionalInformationShouldBeSerializable() throws JsonProcessingException {
        DeletedMessagesVaultDeleteTask.AdditionalInformation details = new DeletedMessagesVaultDeleteTask.AdditionalInformation(username, messageId, TIMESTAMP);
        assertThatJson(jsonAdditionalInformationSerializer.serialize(details)).isEqualTo(serializedAdditionalInformation);
    }

    @Test
    void additonalInformationShouldBeDeserializable() throws IOException {
        DeletedMessagesVaultDeleteTask.AdditionalInformation details = new DeletedMessagesVaultDeleteTask.AdditionalInformation(username, messageId, TIMESTAMP);
        assertThat(jsonAdditionalInformationSerializer.deserialize(serializedAdditionalInformation))
            .isEqualToComparingFieldByField(details);
    }
}