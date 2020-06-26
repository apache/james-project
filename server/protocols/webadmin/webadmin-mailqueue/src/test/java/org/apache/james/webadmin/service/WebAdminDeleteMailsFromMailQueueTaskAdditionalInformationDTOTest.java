/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/

package org.apache.james.webadmin.service;

import java.time.Instant;
import java.util.Optional;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.core.MailAddress;
import org.apache.james.queue.api.MailQueueName;
import org.junit.jupiter.api.Test;

class WebAdminDeleteMailsFromMailQueueTaskAdditionalInformationDTOTest {
    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");

    private static final MailQueueName queueName = MailQueueName.of("anyQueue");

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        DeleteMailsFromMailQueueTask.AdditionalInformation detailsSender = new DeleteMailsFromMailQueueTask.AdditionalInformation(queueName, 10L, 5L,
            Optional.of(new MailAddress("a@b.c")), Optional.empty(), Optional.empty(), TIMESTAMP);
        DeleteMailsFromMailQueueTask.AdditionalInformation detailsName = new DeleteMailsFromMailQueueTask.AdditionalInformation(queueName, 1L, 0L,
            Optional.empty(), Optional.of("name"), Optional.empty(), TIMESTAMP);
        DeleteMailsFromMailQueueTask.AdditionalInformation detailsRecipient = new DeleteMailsFromMailQueueTask.AdditionalInformation(queueName, 6L, 6L,
            Optional.empty(), Optional.empty(), Optional.of(new MailAddress("d@e.f")), TIMESTAMP);

        JsonSerializationVerifier.dtoModule(WebAdminDeleteMailsFromMailQueueTaskAdditionalInformationDTO.module())
            .testCase(detailsSender,
                "{\"type\": \"delete-mails-from-mail-queue\", \"mailQueueName\": \"anyQueue\", \"sender\": \"a@b.c\", \"initialCount\" : 10, \"remainingCount\":5, \"timestamp\": \"2018-11-13T12:00:55Z\"}")
            .testCase(detailsName,
                "{\"type\": \"delete-mails-from-mail-queue\", \"mailQueueName\": \"anyQueue\", \"name\": \"name\", \"initialCount\" : 1, \"remainingCount\":0, \"timestamp\": \"2018-11-13T12:00:55Z\"}")
            .testCase(detailsRecipient,
                "{\"type\": \"delete-mails-from-mail-queue\", \"mailQueueName\": \"anyQueue\", \"recipient\": \"d@e.f\", \"initialCount\" : 6, \"remainingCount\":6, \"timestamp\": \"2018-11-13T12:00:55Z\"}")
            .verify();
    }
}