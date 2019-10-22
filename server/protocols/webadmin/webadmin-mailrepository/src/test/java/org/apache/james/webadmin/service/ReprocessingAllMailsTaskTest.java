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
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.server.task.json.JsonTaskAdditionalInformationSerializer;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.core.JsonProcessingException;

class ReprocessingAllMailsTaskTest {

    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");
    private static final ReprocessingService REPROCESSING_SERVICE = mock(ReprocessingService.class);
    private JsonTaskAdditionalInformationSerializer jsonAdditionalInformationSerializer = JsonTaskAdditionalInformationSerializer.of(ReprocessingAllMailsTaskAdditionalInformationDTO.SERIALIZATION_MODULE);
    private static final long REPOSITORY_SIZE = 5L;
    private static final MailRepositoryPath REPOSITORY_PATH = MailRepositoryPath.from("a");
    private static final String TARGET_QUEUE = "queue";
    private static final MailKey MAIL_KEY = new MailKey("myMail");
    private static final Optional<String> SOME_TARGET_PROCESSOR = Optional.of("targetProcessor");
    private static final long REMAINING_COUNT = 3L;
    private static final String SERIALIZED_TASK_WITH_TARGET_PROCESSOR = "{\"type\":\"reprocessing-all\",\"repositorySize\":5,\"repositoryPath\":\"a\",\"targetQueue\":\"queue\",\"targetProcessor\":\"targetProcessor\"}";
    private static final String SERIALIZED_TASK_WITHOUT_TARGET_PROCESSOR = "{\"type\":\"reprocessing-all\",\"repositorySize\":5,\"repositoryPath\":\"a\",\"targetQueue\":\"queue\"}";
    private static final String SERIALIZED_TASK_ADDITIONAL_INFORMATION_WITH_TARGET_PROCESSOR = "{\"type\":\"reprocessing-all\", \"repositoryPath\":\"a\",\"targetQueue\":\"queue\",\"targetProcessor\":\"targetProcessor\",\"initialCount\":5,\"remainingCount\":3, \"timestamp\":\"2018-11-13T12:00:55Z\"}";
    private static final String SERIALIZED_TASK_ADDITIONAL_INFORMATION_WITHOUT_TARGET_PROCESSOR = "{\"type\":\"reprocessing-all\", \"repositoryPath\":\"a\",\"targetQueue\":\"queue\", \"initialCount\":5,\"remainingCount\":3, \"timestamp\":\"2018-11-13T12:00:55Z\"}";

    @ParameterizedTest
    @MethodSource
    void taskShouldBeSerializable(long repositorySize,
                                  MailRepositoryPath repositoryPath,
                                  String targetQueue,
                                  Optional<String> targetProcessor,
                                  String serialized) throws JsonProcessingException {
        JsonTaskSerializer testee = JsonTaskSerializer.of(ReprocessingAllMailsTaskDTO.module(REPROCESSING_SERVICE));
        ReprocessingAllMailsTask task = new ReprocessingAllMailsTask(REPROCESSING_SERVICE, repositorySize, repositoryPath, targetQueue, targetProcessor);
        assertThatJson(testee.serialize(task))
            .isEqualTo(serialized);
    }

    private static Stream<Arguments> taskShouldBeSerializable() {
        return allValidTasks();
    }

    @ParameterizedTest
    @MethodSource
    void taskShouldBeDeserializable(long repositorySize,
                                    MailRepositoryPath repositoryPath,
                                    String targetQueue,
                                    Optional<String> targetProcessor,
                                    String serialized) throws IOException {
        JsonTaskSerializer testee = JsonTaskSerializer.of(ReprocessingAllMailsTaskDTO.module(REPROCESSING_SERVICE));
        ReprocessingAllMailsTask task = new ReprocessingAllMailsTask(REPROCESSING_SERVICE, repositorySize, repositoryPath, targetQueue, targetProcessor);

        assertThat(testee.deserialize(serialized))
            .isEqualToComparingFieldByFieldRecursively(task);
    }

    private static Stream<Arguments> taskShouldBeDeserializable() {
        return allValidTasks();
    }

    private static Stream<Arguments> allValidTasks() {
        return Stream.of(
            Arguments.of(REPOSITORY_SIZE, REPOSITORY_PATH, TARGET_QUEUE, SOME_TARGET_PROCESSOR, SERIALIZED_TASK_WITH_TARGET_PROCESSOR),
            Arguments.of(REPOSITORY_SIZE, REPOSITORY_PATH, TARGET_QUEUE, Optional.empty(), SERIALIZED_TASK_WITHOUT_TARGET_PROCESSOR)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"type\":\"reprocessing-all\",\"repositorySize\":5,\"repositoryPath\":\"%\",\"targetQueue\":\"queue\",\"targetProcessor\":\"targetProcessor\"}", "{\"type\":\"reprocessing-all\",\"repositorySize\":5,\"repositoryPath\":\"%\",\"targetQueue\":\"queue\"}"})
    void taskShouldThrowOnDeserializationUrlDecodingError(String serialized) {
        JsonTaskSerializer testee = JsonTaskSerializer.of(ReprocessingAllMailsTaskDTO.module(REPROCESSING_SERVICE));

        assertThatThrownBy(() -> testee.deserialize(serialized))
            .isInstanceOf(ReprocessingAllMailsTask.InvalidMailRepositoryPathDeserializationException.class);
    }

    private static Stream<Arguments> allAdditionalInformation() {
        return Stream.of(
            Arguments.of(REPOSITORY_PATH, TARGET_QUEUE, SOME_TARGET_PROCESSOR, REPOSITORY_SIZE, REMAINING_COUNT, SERIALIZED_TASK_ADDITIONAL_INFORMATION_WITH_TARGET_PROCESSOR),
            Arguments.of(REPOSITORY_PATH, TARGET_QUEUE, Optional.empty(), REPOSITORY_SIZE, REMAINING_COUNT, SERIALIZED_TASK_ADDITIONAL_INFORMATION_WITHOUT_TARGET_PROCESSOR)
        );
    }


    @ParameterizedTest
    @MethodSource
    void additionalInformationShouldBeSerializable(MailRepositoryPath repositoryPath,
                                                   String targetQueue,
                                                   Optional<String> targetProcessor,
                                                   long repositorySize,
                                                   long remainingCount,
                                                   String serialized) throws JsonProcessingException {
        ReprocessingAllMailsTask.AdditionalInformation details = new ReprocessingAllMailsTask.AdditionalInformation(repositoryPath, targetQueue, targetProcessor, repositorySize, remainingCount, TIMESTAMP);
        assertThatJson(jsonAdditionalInformationSerializer.serialize(details)).isEqualTo(serialized);
    }

    private static Stream<Arguments> additionalInformationShouldBeSerializable() {
        return allAdditionalInformation();
    }

    @ParameterizedTest
    @MethodSource
    void additionalInformationShouldBeDeserializable(MailRepositoryPath repositoryPath,
                                                     String targetQueue,
                                                     Optional<String> targetProcessor,
                                                     long repositorySize,
                                                     long remainingCount,
                                                     String serialized) throws IOException {
        ReprocessingAllMailsTask.AdditionalInformation details = new ReprocessingAllMailsTask.AdditionalInformation(repositoryPath, targetQueue, targetProcessor, repositorySize, remainingCount, TIMESTAMP);
        assertThat(jsonAdditionalInformationSerializer.deserialize(serialized))
            .isEqualToComparingFieldByField(details);
    }

    private static Stream<Arguments> additionalInformationShouldBeDeserializable() {
        return allAdditionalInformation();
    }
}