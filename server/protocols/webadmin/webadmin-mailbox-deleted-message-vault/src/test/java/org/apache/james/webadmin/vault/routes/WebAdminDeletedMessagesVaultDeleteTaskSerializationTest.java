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

import java.time.Instant;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestMessageId;
import org.junit.jupiter.api.Test;

class WebAdminDeletedMessagesVaultDeleteTaskSerializationTest {
    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");

    private final Username username = Username.of("james");
    private final TestMessageId.Factory messageIdFactory = new TestMessageId.Factory();
    private final MessageId messageId = messageIdFactory.generate();
    private final String serializedAdditionalInformation = "{\"type\": \"deleted-messages-delete\", \"username\":\"james\", \"deleteMessageId\": \"" + messageId.serialize() + "\", \"timestamp\":\"2018-11-13T12:00:55Z\"}";

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(WebAdminDeletedMessagesVaultDeleteTaskAdditionalInformationDTO.module(messageIdFactory))
            .bean(new DeletedMessagesVaultDeleteTask.AdditionalInformation(username, messageId, TIMESTAMP))
            .json(serializedAdditionalInformation)
            .verify();
    }
}