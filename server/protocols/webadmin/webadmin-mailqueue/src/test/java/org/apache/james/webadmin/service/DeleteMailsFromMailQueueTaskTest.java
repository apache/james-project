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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.stream.Stream;

import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import org.apache.james.core.MailAddress;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.server.task.json.JsonTaskSerializer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.mail.internet.AddressException;

class DeleteMailsFromMailQueueTaskTest {
    @ParameterizedTest
    @MethodSource
    void taskShouldBeSerializable(Optional<MailAddress> sender, Optional<String> name, Optional<MailAddress> recipient, String serializedJson) throws Exception {
        MailQueueFactory<ManageableMailQueue> mailQueueFactory = mock(MailQueueFactory.class);
        ManageableMailQueue mockedQueue = mock(ManageableMailQueue.class);
        String queueName = "anyQueue";
        when(mockedQueue.getName()).thenReturn(queueName);
        when(mailQueueFactory.getQueue(anyString())).thenAnswer(arg -> Optional.of(mockedQueue));
        JsonTaskSerializer testee = new JsonTaskSerializer(DeleteMailsFromMailQueueTask.MODULE.apply(mailQueueFactory));

        ManageableMailQueue queue = mailQueueFactory.getQueue(queueName).get();
        DeleteMailsFromMailQueueTask task = new DeleteMailsFromMailQueueTask(queue, sender, name, recipient);
        JsonAssertions.assertThatJson(testee.serialize(task)).isEqualTo(serializedJson);
    }

    private static Stream<Arguments> taskShouldBeSerializable() throws Exception {
        return validTasks();
    }

    @ParameterizedTest
    @MethodSource
    void taskShouldBeDeserializable(Optional<MailAddress> sender, Optional<String> name, Optional<MailAddress> recipient, String serializedJson) throws Exception {
        MailQueueFactory<ManageableMailQueue> mailQueueFactory = mock(MailQueueFactory.class);
        ManageableMailQueue mockedQueue = mock(ManageableMailQueue.class);
        String queueName = "anyQueue";
        when(mockedQueue.getName()).thenReturn(queueName);
        when(mailQueueFactory.getQueue(anyString())).thenAnswer(arg -> Optional.of(mockedQueue));
        JsonTaskSerializer testee = new JsonTaskSerializer(DeleteMailsFromMailQueueTask.MODULE.apply(mailQueueFactory));

        ManageableMailQueue queue = mailQueueFactory.getQueue(queueName).get();
        DeleteMailsFromMailQueueTask task = new DeleteMailsFromMailQueueTask(queue, sender, name, recipient);
        assertThat(testee.deserialize(serializedJson)).isEqualToIgnoringGivenFields(task, "additionalInformation");
    }

    private static Stream<Arguments> taskShouldBeDeserializable() throws Exception {
        return validTasks();
    }

    private static Stream<Arguments> validTasks() throws Exception {
        return Stream.of(
            Arguments.of(Optional.of(new MailAddress("a@b.c")), Optional.empty(), Optional.empty(), "{\"type\": \"delete-mails-from-mail-queue\", \"queue\": \"anyQueue\", \"sender\": \"a@b.c\"}"),
            Arguments.of(Optional.empty(), Optional.of("name"), Optional.empty(), "{\"type\": \"delete-mails-from-mail-queue\", \"queue\": \"anyQueue\", \"name\": \"name\"}"),
            Arguments.of(Optional.empty(), Optional.empty(), Optional.of(new MailAddress("d@e.f")), "{\"type\": \"delete-mails-from-mail-queue\", \"queue\": \"anyQueue\", \"recipient\": \"d@e.f\"}")
        );
    }

    @Test
    void taskShouldThrowWhenDeserializeAnUnknownQueue() throws Exception {
        MailQueueFactory<ManageableMailQueue> mailQueueFactory = mock(MailQueueFactory.class);
        when(mailQueueFactory.getQueue(anyString())).thenReturn(Optional.empty());
        JsonTaskSerializer testee = new JsonTaskSerializer(DeleteMailsFromMailQueueTask.MODULE.apply(mailQueueFactory));

        String serializedJson = "{\"type\": \"delete-mails-from-mail-queue\", \"queue\": \"anyQueue\", \"sender\": \"a@b.c\"}";
        assertThatThrownBy(() -> testee.deserialize(serializedJson))
            .isInstanceOf(DeleteMailsFromMailQueueTask.UnknownSerializedQueue.class);
    }

    @Test
    void taskShouldThrowWhenDeserializeAMalformedMailAddress() throws Exception {
        MailQueueFactory<ManageableMailQueue> mailQueueFactory = mock(MailQueueFactory.class);
        ManageableMailQueue mockedQueue = mock(ManageableMailQueue.class);
        String queueName = "anyQueue";
        when(mockedQueue.getName()).thenReturn(queueName);
        when(mailQueueFactory.getQueue(anyString())).thenAnswer(arg -> Optional.of(mockedQueue));
        JsonTaskSerializer testee = new JsonTaskSerializer(DeleteMailsFromMailQueueTask.MODULE.apply(mailQueueFactory));

        String serializedJson = "{\"type\": \"delete-mails-from-mail-queue\", \"queue\": \"" + queueName + "\", \"sender\": \"a.b.c\"}";
        assertThatThrownBy(() -> testee.deserialize(serializedJson))
            .isInstanceOf(AddressException.class);
    }
}
