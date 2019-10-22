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

package org.apache.james.webadmin.service;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.time.Instant;

import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.server.task.json.JsonTaskAdditionalInformationSerializer;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;
import net.javacrumbs.jsonunit.assertj.JsonAssertions;

class ClearMailRepositoryTaskTest {

    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");

    private static final String SERIALIZED = "{\"type\":\"clear-mail-repository\",\"mailRepositoryPath\":\"a\"}";
    private static final String SERIALIZED_TASK_ADDITIONAL_INFORMATION = "{\"type\":\"clear-mail-repository\", \"mailRepositoryPath\":\"a\", \"initialCount\": 0, \"remainingCount\": 10, \"timestamp\":\"2018-11-13T12:00:55Z\"}";
    private static final ClearMailRepositoryTask.Factory FACTORY = new ClearMailRepositoryTask.Factory(mock(MailRepositoryStore.class));
    private static final ImmutableList<MailRepository> MAIL_REPOSITORIES = ImmutableList.of();
    private static final MailRepositoryPath MAIL_REPOSITORY_PATH = MailRepositoryPath.from("a");
    private static final ClearMailRepositoryTask TASK = new ClearMailRepositoryTask(MAIL_REPOSITORIES, MAIL_REPOSITORY_PATH);
    private static final long INITIAL_COUNT = 0L;
    private static final long REMAINING_COUNT = 10L;
    private JsonTaskAdditionalInformationSerializer jsonAdditionalInformationSerializer = JsonTaskAdditionalInformationSerializer.of(
        ClearMailRepositoryTaskAdditionalInformationDTO.SERIALIZATION_MODULE);

    @Test
    void taskShouldBeSerializable() throws JsonProcessingException {
        JsonTaskSerializer testee = JsonTaskSerializer.of(ClearMailRepositoryTaskDTO.module(FACTORY));
        JsonAssertions.assertThatJson(testee.serialize(TASK))
            .isEqualTo(SERIALIZED);
    }

    @Test
    void taskShouldBeDeserializable() throws IOException {
        JsonTaskSerializer testee = JsonTaskSerializer.of(ClearMailRepositoryTaskDTO.module(FACTORY));

        assertThat(testee.deserialize(SERIALIZED))
            .isEqualToComparingFieldByFieldRecursively(TASK);
    }

    @Test
    void taskShouldThrowOnDeserializationUrlDecodingError() {
        JsonTaskSerializer testee = JsonTaskSerializer.of(ClearMailRepositoryTaskDTO.module(FACTORY));

        assertThatThrownBy(() -> testee.deserialize("{\"type\":\"clear-mail-repository\",\"mailRepositoryPath\":\"%\"}"))
            .isInstanceOf(ClearMailRepositoryTask.InvalidMailRepositoryPathDeserializationException.class);
    }

    @Test
    void additionalInformationShouldBeSerializable() throws JsonProcessingException {
        ClearMailRepositoryTask.AdditionalInformation details = new ClearMailRepositoryTask.AdditionalInformation(MAIL_REPOSITORY_PATH, INITIAL_COUNT, REMAINING_COUNT, TIMESTAMP);
        assertThatJson(jsonAdditionalInformationSerializer.serialize(details)).isEqualTo(SERIALIZED_TASK_ADDITIONAL_INFORMATION);
    }

    @Test
    void additionalInformationShouldBeDeserializable() throws IOException {
        ClearMailRepositoryTask.AdditionalInformation details = new ClearMailRepositoryTask.AdditionalInformation(MAIL_REPOSITORY_PATH, INITIAL_COUNT, REMAINING_COUNT, TIMESTAMP);
        assertThat(jsonAdditionalInformationSerializer.deserialize(SERIALIZED_TASK_ADDITIONAL_INFORMATION))
            .isEqualToComparingFieldByField(details);
    }
}