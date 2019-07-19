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

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.server.task.json.JsonTaskSerializer;

import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReprocessingOneMailTaskTest {
    private static final ReprocessingService REPROCESSING_SERVICE = mock(ReprocessingService.class);

    @ParameterizedTest
    @MethodSource
    void taskShouldBeSerializable(MailRepositoryPath repositoryPath,
                                  String targetQueue,
                                  MailKey mailKey,
                                  Optional<String> targetProcessor,
                                  String serialized) throws JsonProcessingException {
        JsonTaskSerializer testee = new JsonTaskSerializer(ReprocessingOneMailTask.MODULE.apply(REPROCESSING_SERVICE));
        ReprocessingOneMailTask task = new ReprocessingOneMailTask(REPROCESSING_SERVICE, repositoryPath, targetQueue, mailKey, targetProcessor);
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
        JsonTaskSerializer testee = new JsonTaskSerializer(ReprocessingOneMailTask.MODULE.apply(REPROCESSING_SERVICE));
        ReprocessingOneMailTask task = new ReprocessingOneMailTask(REPROCESSING_SERVICE, repositoryPath, targetQueue, mailKey, targetProcessor);

        assertThat(testee.deserialize(serialized))
            .isEqualToComparingFieldByFieldRecursively(task);
    }

    private static Stream<Arguments> taskShouldBeDeserializable() {
        return allValidTasks();
    }

    private static Stream<Arguments> allValidTasks() {
        return Stream.of(
            Arguments.of(MailRepositoryPath.from("a"), "queue", new MailKey("myMail"), Optional.of("targetProcessor"), "{\"type\":\"reprocessingOneTask\",\"repositoryPath\":\"a\",\"targetQueue\":\"queue\",\"mailKey\": \"myMail\",\"targetProcessor\":\"targetProcessor\"}"),
            Arguments.of(MailRepositoryPath.from("a"), "queue", new MailKey("myMail"), Optional.empty(), "{\"type\":\"reprocessingOneTask\",\"repositoryPath\":\"a\",\"targetQueue\":\"queue\",\"mailKey\": \"myMail\"}")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"type\":\"reprocessingOneTask\",\"repositoryPath\":\"%\",\"targetQueue\":\"queue\",\"mailKey\": \"myMail\",\"targetProcessor\":\"targetProcessor\"}", "{\"type\":\"reprocessingOneTask\",\"repositoryPath\":\"%\",\"targetQueue\":\"queue\",\"mailKey\": \"myMail\"}"})
    void taskShouldThrowOnDeserializationUrlDecodingError(String serialized) {
        JsonTaskSerializer testee = new JsonTaskSerializer(ReprocessingOneMailTask.MODULE.apply(REPROCESSING_SERVICE));

        assertThatThrownBy(() -> testee.deserialize(serialized))
                .isInstanceOf(ReprocessingOneMailTask.InvalidMailRepositoryPathDeserializationException.class);
    }
}
