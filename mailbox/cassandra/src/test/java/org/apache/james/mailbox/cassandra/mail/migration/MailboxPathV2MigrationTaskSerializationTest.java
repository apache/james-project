/**
 * *************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.mailbox.cassandra.mail.migration;

import static org.mockito.Mockito.mock;

import java.time.Instant;

import org.apache.james.JsonSerializationVerifier;
import org.junit.jupiter.api.Test;

class MailboxPathV2MigrationTaskSerializationTest {
    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");
    private static final MailboxPathV2Migration MIGRATION = mock(MailboxPathV2Migration.class);
    private static final MailboxPathV2Migration.MailboxPathV2MigrationTask TASK = new MailboxPathV2Migration.MailboxPathV2MigrationTask(MIGRATION);
    private static final String SERIALIZED_TASK = "{\"type\": \"cassandra-mailbox-path-v2-migration\"}";
    private static final MailboxPathV2Migration.AdditionalInformation DETAILS = new MailboxPathV2Migration.AdditionalInformation(42L, 10, TIMESTAMP);
    private static final String SERIALIZED_ADDITIONAL_INFORMATION = "{\"type\": \"cassandra-mailbox-path-v2-migration\", \"remainingCount\":42,\"initialCount\":10, \"timestamp\":\"2018-11-13T12:00:55Z\"}";

    @Test
    void taskShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(MailboxPathV2MigrationTaskDTO.MODULE.apply(MIGRATION))
            .bean(TASK)
            .json(SERIALIZED_TASK)
            .verify();
    }

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(MailboxPathV2MigrationTaskAdditionalInformationDTO.MODULE)
            .bean(DETAILS)
            .json(SERIALIZED_ADDITIONAL_INFORMATION)
            .verify();
    }
}
