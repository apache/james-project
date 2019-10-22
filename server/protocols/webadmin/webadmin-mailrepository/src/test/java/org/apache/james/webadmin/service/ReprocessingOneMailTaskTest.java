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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.server.task.json.JsonTaskAdditionalInformationSerializer;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.javacrumbs.jsonunit.assertj.JsonAssertions;

class ReprocessingOneMailTaskTest {

    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");
    private static final Clock CLOCK = Clock.fixed(TIMESTAMP, ZoneId.of("UTC"));
    private static final ReprocessingService REPROCESSING_SERVICE = mock(ReprocessingService.class);
    private static final String SERIALIZED_TASK_1 = "{\"type\":\"reprocessing-one\",\"repositoryPath\":\"a\",\"targetQueue\":\"queue\",\"mailKey\": \"myMail\",\"targetProcessor\":\"targetProcessor\"}";
    private static final String SERIALIZED_TASK_1_ADDITIONAL_INFORMATION = "{\"type\":\"reprocessing-one\", \"repositoryPath\":\"a\",\"targetQueue\":\"queue\",\"mailKey\": \"myMail\",\"targetProcessor\":\"targetProcessor\", \"timestamp\":\"2018-11-13T12:00:55Z\"}";
    private static final MailRepositoryPath REPOSITORY_PATH = MailRepositoryPath.from("a");
    private static final String TARGET_QUEUE = "queue";
    private static final MailKey MAIL_KEY = new MailKey("myMail");
    private static final Optional<String> TARGET_PROCESSOR = Optional.of("targetProcessor");
    private JsonTaskAdditionalInformationSerializer jsonAdditionalInformationSerializer = JsonTaskAdditionalInformationSerializer.of(ReprocessingOneMailTaskAdditionalInformationDTO.SERIALIZATION_MODULE);

    @ParameterizedTest
    @MethodSource
    void taskShouldBeSerializable(MailRepositoryPath repositoryPath,
                                  String targetQueue,
                                  MailKey mailKey,
                                  Optional<String> targetProcessor,
                                  String serialized) throws JsonProcessingException {
        JsonTaskSerializer testee = JsonTaskSerializer.of(ReprocessingOneMailTaskDTO.module(CLOCK, REPROCESSING_SERVICE));
        ReprocessingOneMailTask task = new ReprocessingOneMailTask(REPROCESSING_SERVICE, repositoryPath, targetQueue, mailKey, targetProcessor, CLOCK);
        JsonAssertions.assertThatJson(testee.serialize(task))
            .isEqualTo(serialized);
    }

    private static Stream<Arguments> taskShouldBeSerializable() {
        return allValidTasks();
    }

    @ParameterizedTest
    @MethodSource
    void taskShouldBeDeserializable(MailRepositoryPath repositoryPath,
                                    String targetQueue,
                                    MailKey mailKey,
                                    Optional<String> targetProcessor,
                                    String serialized) throws IOException {
        JsonTaskSerializer testee = JsonTaskSerializer.of(ReprocessingOneMailTaskDTO.module(CLOCK, REPROCESSING_SERVICE));
        ReprocessingOneMailTask task = new ReprocessingOneMailTask(REPROCESSING_SERVICE, repositoryPath, targetQueue, mailKey, targetProcessor, CLOCK);

        assertThat(testee.deserialize(serialized))
            .isEqualToComparingFieldByFieldRecursively(task);
    }

    private static Stream<Arguments> taskShouldBeDeserializable() {
        return allValidTasks();
    }

    private static Stream<Arguments> allValidTasks() {
        return Stream.of(
            Arguments.of(REPOSITORY_PATH, TARGET_QUEUE, MAIL_KEY, TARGET_PROCESSOR, SERIALIZED_TASK_1),
            Arguments.of(REPOSITORY_PATH, TARGET_QUEUE, new MailKey("myMail"), Optional.empty(), "{\"type\":\"reprocessing-one\",\"repositoryPath\":\"a\",\"targetQueue\":\"queue\",\"mailKey\": \"myMail\"}")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"type\":\"reprocessing-one\",\"repositoryPath\":\"%\",\"targetQueue\":\"queue\",\"mailKey\": \"myMail\",\"targetProcessor\":\"targetProcessor\"}", "{\"type\":\"reprocessing-one\",\"repositoryPath\":\"%\",\"targetQueue\":\"queue\",\"mailKey\": \"myMail\"}"})
    void taskShouldThrowOnDeserializationUrlDecodingError(String serialized) {
        JsonTaskSerializer testee = JsonTaskSerializer.of(ReprocessingOneMailTaskDTO.module(CLOCK, REPROCESSING_SERVICE));

        assertThatThrownBy(() -> testee.deserialize(serialized))
            .isInstanceOf(ReprocessingOneMailTask.InvalidMailRepositoryPathDeserializationException.class);
    }

    @Test
    void additionalInformationShouldBeSerializable() throws JsonProcessingException {
        ReprocessingOneMailTask.AdditionalInformation details = new ReprocessingOneMailTask.AdditionalInformation(REPOSITORY_PATH, TARGET_QUEUE, MAIL_KEY, TARGET_PROCESSOR, TIMESTAMP);
        assertThatJson(jsonAdditionalInformationSerializer.serialize(details)).isEqualTo(SERIALIZED_TASK_1_ADDITIONAL_INFORMATION);
    }

    @Test
    void additonalInformationShouldBeDeserializable() throws IOException {
        ReprocessingOneMailTask.AdditionalInformation details = new ReprocessingOneMailTask.AdditionalInformation(REPOSITORY_PATH, TARGET_QUEUE, MAIL_KEY, TARGET_PROCESSOR, TIMESTAMP);
        assertThat(jsonAdditionalInformationSerializer.deserialize(SERIALIZED_TASK_1_ADDITIONAL_INFORMATION))
            .isEqualToComparingFieldByField(details);
    }
}
