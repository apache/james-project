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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import jakarta.mail.internet.AddressException;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.core.MailAddress;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.server.task.json.JsonTaskAdditionalInformationSerializer;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeleteMailsFromMailQueueTaskTest {
    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");

    private MailQueueFactory<ManageableMailQueue> mailQueueFactory;
    private ManageableMailQueue mockedQueue;
    private static final MailQueueName queueName = MailQueueName.of("anyQueue");

    @BeforeEach
    void setUp() {
        mailQueueFactory = mock(MailQueueFactory.class);
        mockedQueue = mock(ManageableMailQueue.class);
        when(mockedQueue.getName()).thenReturn(queueName);
        when(mailQueueFactory.getQueue(any(MailQueueName.class))).thenAnswer(arg -> Optional.of(mockedQueue));
    }

    @Test
    void taskShouldBeSerializable() throws Exception {
        DeleteMailsFromMailQueueTask.MailQueueFactory factory = name -> this.mailQueueFactory.getQueue(name).orElseThrow(RuntimeException::new);
        DeleteMailsFromMailQueueTask taskSender = new DeleteMailsFromMailQueueTask(queueName, factory, Optional.of(new MailAddress("a@b.c")), Optional.empty(), Optional.empty());
        DeleteMailsFromMailQueueTask taskName = new DeleteMailsFromMailQueueTask(queueName, factory, Optional.empty(), Optional.of("name"), Optional.empty());
        DeleteMailsFromMailQueueTask taskRecipient = new DeleteMailsFromMailQueueTask(queueName, factory, Optional.empty(), Optional.empty(), Optional.of(new MailAddress("d@e.f")));

        JsonSerializationVerifier.dtoModule(DeleteMailsFromMailQueueTaskDTO.module(this.mailQueueFactory))
            .testCase(taskSender, "{\"type\": \"delete-mails-from-mail-queue\", \"queue\": \"anyQueue\", \"sender\": \"a@b.c\"}")
            .testCase(taskName, "{\"type\": \"delete-mails-from-mail-queue\", \"queue\": \"anyQueue\", \"name\": \"name\"}")
            .testCase(taskRecipient, "{\"type\": \"delete-mails-from-mail-queue\", \"queue\": \"anyQueue\", \"recipient\": \"d@e.f\"}")
            .equalityTester((a, b) -> {
                assertThat(a.getQueueName()).isEqualTo(b.getQueueName());
                assertThat(a.getMaybeName()).isEqualTo(b.getMaybeName());
                assertThat(a.getMaybeSender()).isEqualTo(b.getMaybeSender());
                assertThat(a.getMaybeRecipient()).isEqualTo(b.getMaybeRecipient());
            })
            .verify();
    }

    @Test
    void taskShouldThrowWhenRunOnAnUnknownQueue() {
        MailQueueFactory<ManageableMailQueue> mailQueueFactory = mock(MailQueueFactory.class);
        when(mailQueueFactory.getQueue(any(MailQueueName.class))).thenReturn(Optional.empty());
        JsonTaskSerializer testee = JsonTaskSerializer.of(DeleteMailsFromMailQueueTaskDTO.module(mailQueueFactory));

        String serializedJson = "{\"type\": \"delete-mails-from-mail-queue\", \"queue\": \"anyQueue\", \"sender\": \"a@b.c\"}";
        assertThatThrownBy(() -> testee.deserialize(serializedJson).run())
            .isInstanceOf(DeleteMailsFromMailQueueTask.UnknownSerializedQueue.class);
    }

    @Test
    void taskShouldThrowWhenDeserializeAMalformedMailAddress() {
        JsonTaskSerializer testee = JsonTaskSerializer.of(DeleteMailsFromMailQueueTaskDTO.module(mailQueueFactory));

        String serializedJson = "{\"type\": \"delete-mails-from-mail-queue\", \"queue\": \"" + queueName + "\", \"sender\": \"a.b.c\"}";
        assertThatThrownBy(() -> testee.deserialize(serializedJson))
            .isInstanceOf(AddressException.class);
    }

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        ManageableMailQueue queue = mailQueueFactory.getQueue(queueName).get();
        DeleteMailsFromMailQueueTask.AdditionalInformation detailsSender = new DeleteMailsFromMailQueueTask.AdditionalInformation(queue.getName(), 10L, 5L,
            Optional.of(new MailAddress("a@b.c")), Optional.empty(), Optional.empty(), TIMESTAMP);
        DeleteMailsFromMailQueueTask.AdditionalInformation detailsName = new DeleteMailsFromMailQueueTask.AdditionalInformation(queue.getName(), 1L, 0L,
            Optional.empty(), Optional.of("name"), Optional.empty(), TIMESTAMP);
        DeleteMailsFromMailQueueTask.AdditionalInformation detailsRecipient = new DeleteMailsFromMailQueueTask.AdditionalInformation(queue.getName(), 6L, 6L,
            Optional.empty(), Optional.empty(), Optional.of(new MailAddress("d@e.f")), TIMESTAMP);

        JsonSerializationVerifier.dtoModule(DeleteMailsFromMailQueueTaskAdditionalInformationDTO.module())
            .testCase(detailsSender,
                "{\"type\": \"delete-mails-from-mail-queue\", \"queue\": \"anyQueue\", \"sender\": \"a@b.c\", \"initialCount\" : 10, \"remainingCount\":5, \"timestamp\": \"2018-11-13T12:00:55Z\"}")
            .testCase(detailsName,
                "{\"type\": \"delete-mails-from-mail-queue\", \"queue\": \"anyQueue\", \"name\": \"name\", \"initialCount\" : 1, \"remainingCount\":0, \"timestamp\": \"2018-11-13T12:00:55Z\"}")
            .testCase(detailsRecipient,
                "{\"type\": \"delete-mails-from-mail-queue\", \"queue\": \"anyQueue\", \"recipient\": \"d@e.f\", \"initialCount\" : 6, \"remainingCount\":6, \"timestamp\": \"2018-11-13T12:00:55Z\"}")
            .verify();
    }

    @Test
    void additionalInformationShouldThrowWhenDeserializeAMalformedMailAddress() {
        String serializedJson = "{\"type\": \"delete-mails-from-mail-queue\", \"queue\": \"anyQueue\", \"sender\": \"a.b.c\", \"initialCount\" : 10, \"remainingCount\":5}";
        assertThatThrownBy(() -> JsonTaskAdditionalInformationSerializer.of(DeleteMailsFromMailQueueTaskAdditionalInformationDTO.module())
                .deserialize(serializedJson))
            .isInstanceOf(AddressException.class);
    }

}
