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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.server.task.json.JsonTaskAdditionalInformationSerializer;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.util.streams.Limit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReprocessingOneMailTaskTest {
    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");
    private static final Clock CLOCK = Clock.fixed(TIMESTAMP, ZoneId.of("UTC"));
    private static final ReprocessingService REPROCESSING_SERVICE = mock(ReprocessingService.class);
    private static final String SERIALIZED_TASK_1 = "{\"type\":\"reprocessing-one\",\"repositoryPath\":\"a\",\"targetQueue\":\"queue\",\"mailKey\": \"myMail\",\"targetProcessor\":\"targetProcessor\", \"consume\":true}";
    private static final String SERIALIZED_TASK_1_ADDITIONAL_INFORMATION = "{\"type\":\"reprocessing-one\", \"repositoryPath\":\"a\",\"targetQueue\":\"queue\",\"mailKey\": \"myMail\",\"targetProcessor\":\"targetProcessor\", \"timestamp\":\"2018-11-13T12:00:55Z\", \"consume\":true}";
    private static final String SERIALIZED_TASK_OLD = "{\"type\":\"reprocessing-one\",\"repositoryPath\":\"a\",\"targetQueue\":\"queue\",\"mailKey\": \"myMail\",\"targetProcessor\":\"targetProcessor\"}";
    private static final String SERIALIZED_TASK_OLD_ADDITIONAL_INFORMATION = "{\"type\":\"reprocessing-one\", \"repositoryPath\":\"a\",\"targetQueue\":\"queue\",\"mailKey\": \"myMail\",\"targetProcessor\":\"targetProcessor\", \"timestamp\":\"2018-11-13T12:00:55Z\"}";
    private static final MailRepositoryPath REPOSITORY_PATH = MailRepositoryPath.from("a");
    private static final MailQueueName TARGET_QUEUE = MailQueueName.of("queue");
    private static final MailKey MAIL_KEY = new MailKey("myMail");
    private static final Optional<String> TARGET_PROCESSOR = Optional.of("targetProcessor");
    public static final boolean CONSUME = true;
    private static final Optional<Integer> NO_MAX_RETRIES = Optional.empty();
    private static final Optional<String> NO_PROCESSOR = Optional.empty();

    @Test
    void taskShouldBeSerializable() throws Exception {
        ReprocessingOneMailTask taskWithTargetProcessor = new ReprocessingOneMailTask(REPROCESSING_SERVICE, REPOSITORY_PATH, new ReprocessingService.Configuration(TARGET_QUEUE, TARGET_PROCESSOR, NO_MAX_RETRIES, CONSUME, Limit.unlimited()), MAIL_KEY, CLOCK);
        ReprocessingOneMailTask taskWithoutTargetProcessor = new ReprocessingOneMailTask(REPROCESSING_SERVICE, REPOSITORY_PATH, new ReprocessingService.Configuration(TARGET_QUEUE, NO_PROCESSOR, NO_MAX_RETRIES, !CONSUME, Limit.unlimited()), MAIL_KEY, CLOCK);

        JsonSerializationVerifier.dtoModule(ReprocessingOneMailTaskDTO.module(CLOCK, REPROCESSING_SERVICE))
            .testCase(taskWithTargetProcessor, SERIALIZED_TASK_1)
            .testCase(taskWithoutTargetProcessor,
                "{\"type\":\"reprocessing-one\",\"repositoryPath\":\"a\",\"targetQueue\":\"queue\",\"mailKey\": \"myMail\", \"consume\":false}")
            .verify();
    }

    @Test
    void taskShouldBeDeserializable() throws Exception {
        JsonTaskSerializer serializer = JsonTaskSerializer.of(ReprocessingOneMailTaskDTO.module(CLOCK, REPROCESSING_SERVICE));

        assertThat(serializer.deserialize("{\"type\":\"reprocessing-one\",\"repositoryPath\":\"a\",\"targetQueue\":\"queue\",\"mailKey\": \"myMail\", \"consume\":false}"))
            .usingRecursiveComparison()
            .isEqualTo(new ReprocessingOneMailTask(REPROCESSING_SERVICE, REPOSITORY_PATH, new ReprocessingService.Configuration(TARGET_QUEUE, NO_PROCESSOR, NO_MAX_RETRIES, !CONSUME, Limit.unlimited()), MAIL_KEY, CLOCK));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"type\":\"reprocessing-one\",\"repositoryPath\":\"%\",\"targetQueue\":\"queue\",\"mailKey\": \"myMail\",\"targetProcessor\":\"targetProcessor\"}",
        "{\"type\":\"reprocessing-one\",\"repositoryPath\":\"%\",\"targetQueue\":\"queue\",\"mailKey\": \"myMail\"}"})
    void taskShouldThrowOnDeserializationUrlDecodingError(String serialized) {
        JsonTaskSerializer testee = JsonTaskSerializer.of(ReprocessingOneMailTaskDTO.module(CLOCK, REPROCESSING_SERVICE));

        assertThatThrownBy(() -> testee.deserialize(serialized))
            .isInstanceOf(ReprocessingOneMailTask.InvalidMailRepositoryPathDeserializationException.class);
    }

    @Test
    void additionalInformationShouldBeSerializable() throws IOException {
        ReprocessingOneMailTask.AdditionalInformation details = new ReprocessingOneMailTask.AdditionalInformation(REPOSITORY_PATH,
            new ReprocessingService.Configuration(TARGET_QUEUE, TARGET_PROCESSOR, Optional.empty(), true, Limit.unlimited()), MAIL_KEY, TIMESTAMP);
        JsonSerializationVerifier.dtoModule(ReprocessingOneMailTaskAdditionalInformationDTO.module())
            .bean(details)
            .json(SERIALIZED_TASK_1_ADDITIONAL_INFORMATION)
            .verify();
    }


    @Test
    void shouldDeserializePreviousTaskFormat() throws Exception {
        ReprocessingOneMailTask taskWithTargetProcessor = new ReprocessingOneMailTask(REPROCESSING_SERVICE, REPOSITORY_PATH, new ReprocessingService.Configuration(TARGET_QUEUE, TARGET_PROCESSOR, NO_MAX_RETRIES, CONSUME, Limit.unlimited()), MAIL_KEY, CLOCK);
        JsonTaskSerializer testee = JsonTaskSerializer.of(ReprocessingOneMailTaskDTO.module(CLOCK, REPROCESSING_SERVICE));

        assertThat(testee.deserialize(SERIALIZED_TASK_OLD))
            .isEqualToComparingFieldByFieldRecursively(taskWithTargetProcessor);
    }

    @Test
    void shouldDeserializePreviousAdditionalInformationFormat() throws Exception {
        ReprocessingOneMailTask.AdditionalInformation details = new ReprocessingOneMailTask.AdditionalInformation(REPOSITORY_PATH,
            new ReprocessingService.Configuration(TARGET_QUEUE, TARGET_PROCESSOR, Optional.empty(), true, Limit.unlimited()), MAIL_KEY, TIMESTAMP);
        JsonTaskAdditionalInformationSerializer testee = JsonTaskAdditionalInformationSerializer.of(ReprocessingOneMailTaskAdditionalInformationDTO.module());

        assertThat(testee.deserialize(SERIALIZED_TASK_OLD_ADDITIONAL_INFORMATION))
            .isEqualToComparingFieldByFieldRecursively(details);
    }
}
