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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.json.JsonGenericSerializer;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.rabbitmq.RabbitMQMailQueue;
import org.apache.james.queue.rabbitmq.RabbitMQMailQueueFactory;
import org.junit.jupiter.api.Test;

class RepublishNotprocessedMailsTaskTest {
    private static final Instant OLDER_THAN = Instant.parse("2018-11-13T12:00:55Z");
    private static final Instant NOW = Instant.now();
    private static final long NB_REQUEUED_MAILS = 12;
    private static final String SERIALIZED = "{\"type\": \"republish-not-processed-mails\",\"mailQueue\":\"anyQueue\", \"olderThan\": \"" + OLDER_THAN + "\"}";
    private static final String SERIALIZED_TASK_ADDITIONAL_INFORMATION = "{\"type\": \"republish-not-processed-mails\",\"mailQueue\":\"anyQueue\", \"olderThan\": \"" + OLDER_THAN + "\" ,\"nbRequeuedMails\":12,\"timestamp\":\"" + NOW.toString() + "\"}";
    private static final MailQueueName QUEUE_NAME = MailQueueName.of("anyQueue");

    @Test
    void taskShouldBeSerializable() throws Exception {
        RabbitMQMailQueueFactory mockedQueueFactory = mock(RabbitMQMailQueueFactory.class);
        RabbitMQMailQueue mockedQueue = mock(RabbitMQMailQueue.class);

        when(mockedQueue.getName()).thenReturn(QUEUE_NAME);
        when(mockedQueueFactory.getQueue(QUEUE_NAME)).thenReturn(Optional.of(mockedQueue));

        RepublishNotprocessedMailsTask task = new RepublishNotprocessedMailsTask(mockedQueue, OLDER_THAN);

        JsonSerializationVerifier.dtoModule(RepublishNotProcessedMailsTaskDTO.module(mockedQueueFactory))
            .bean(task)
            .json(SERIALIZED)
            .verify();
    }

    @Test
    void taskShouldBeDeserializable() throws Exception {
        RabbitMQMailQueueFactory mockedQueueFactory = mock(RabbitMQMailQueueFactory.class);
        RabbitMQMailQueue mockedQueue = mock(RabbitMQMailQueue.class);

        when(mockedQueue.getName()).thenReturn(QUEUE_NAME);
        when(mockedQueueFactory.getQueue(QUEUE_NAME)).thenReturn(Optional.of(mockedQueue));

        RepublishNotprocessedMailsTask task = new RepublishNotprocessedMailsTask(mockedQueue, OLDER_THAN);
        JsonSerializationVerifier.dtoModule(RepublishNotProcessedMailsTaskDTO.module(mockedQueueFactory))
            .bean(task)
            .json(SERIALIZED)
            .verify();
    }

    @Test
    void taskDeserializationFromUnknownQueueNameShouldThrow() {
        RabbitMQMailQueueFactory mockedQueueFactory = mock(RabbitMQMailQueueFactory.class);
        RabbitMQMailQueue mockedQueue = mock(RabbitMQMailQueue.class);

        when(mockedQueue.getName()).thenReturn(QUEUE_NAME);
        when(mockedQueueFactory.getQueue(QUEUE_NAME)).thenReturn(Optional.empty());

        RepublishNotprocessedMailsTask task = new RepublishNotprocessedMailsTask(mockedQueue, OLDER_THAN);
        assertThatThrownBy(() -> JsonSerializationVerifier.dtoModule(RepublishNotProcessedMailsTaskDTO.module(mockedQueueFactory))
            .bean(task)
            .json(SERIALIZED)
            .verify())
            .isInstanceOf(RepublishNotProcessedMailsTaskDTO.UnknownMailQueueException.class);
    }

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        RepublishNotprocessedMailsTask.AdditionalInformation details = new RepublishNotprocessedMailsTask.AdditionalInformation(QUEUE_NAME, OLDER_THAN, NB_REQUEUED_MAILS, NOW);
        JsonSerializationVerifier.dtoModule(RepublishNotProcessedMailsTaskAdditionalInformationDTO.module())
            .bean(details)
            .json(SERIALIZED_TASK_ADDITIONAL_INFORMATION)
            .verify();
    }

    @Test
    void additionalInformationShouldBeDeserializable() throws Exception {
        RepublishNotprocessedMailsTask.AdditionalInformation details = new RepublishNotprocessedMailsTask.AdditionalInformation(QUEUE_NAME, OLDER_THAN, NB_REQUEUED_MAILS, NOW);
        RepublishNotprocessedMailsTask.AdditionalInformation deserialized = JsonGenericSerializer.forModules(RepublishNotProcessedMailsTaskAdditionalInformationDTO.module())
            .withoutNestedType()
            .deserialize(SERIALIZED_TASK_ADDITIONAL_INFORMATION);
        assertThat(deserialized).isEqualToComparingFieldByField(details);
    }
}
