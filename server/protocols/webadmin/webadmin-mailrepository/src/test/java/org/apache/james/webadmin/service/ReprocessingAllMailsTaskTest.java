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

import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.server.task.json.JsonTaskAdditionalInformationSerializer;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.util.streams.Limit;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReprocessingAllMailsTaskTest {
    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");
    private static final ReprocessingService REPROCESSING_SERVICE = mock(ReprocessingService.class);
    private static final long REPOSITORY_SIZE = 5L;
    private static final MailRepositoryPath REPOSITORY_PATH = MailRepositoryPath.from("a");
    private static final MailQueueName TARGET_QUEUE = MailQueueName.of("queue");
    private static final Optional<String> SOME_TARGET_PROCESSOR = Optional.of("targetProcessor");
    private static final Optional<Integer> NO_MAX_RETRIES = Optional.empty();
    private static final Optional<String> NO_PROCESSOR = Optional.empty();
    private static final long REMAINING_COUNT = 3L;
    private static final boolean CONSUME = true;

    private static final String SERIALIZED_TASK_WITH_TARGET_PROCESSOR = "{\"type\":\"reprocessing-all\",\"repositorySize\":5,\"repositoryPath\":\"a\",\"targetQueue\":\"queue\",\"targetProcessor\":\"targetProcessor\",\"consume\":true}";
    private static final String SERIALIZED_TASK_WITHOUT_TARGET_PROCESSOR = "{\"type\":\"reprocessing-all\",\"repositorySize\":5,\"repositoryPath\":\"a\",\"targetQueue\":\"queue\",\"consume\":false}";
    private static final String SERIALIZED_TASK_ADDITIONAL_INFORMATION_WITH_TARGET_PROCESSOR = "{\"type\":\"reprocessing-all\", \"repositoryPath\":\"a\",\"targetQueue\":\"queue\",\"targetProcessor\":\"targetProcessor\",\"initialCount\":5,\"remainingCount\":3, \"timestamp\":\"2018-11-13T12:00:55Z\",\"consume\":true}";
    private static final String SERIALIZED_TASK_ADDITIONAL_INFORMATION_WITHOUT_TARGET_PROCESSOR = "{\"type\":\"reprocessing-all\", \"repositoryPath\":\"a\",\"targetQueue\":\"queue\", \"initialCount\":5,\"remainingCount\":3, \"timestamp\":\"2018-11-13T12:00:55Z\",\"consume\":false}";

    private static final String OLD_SERIALIZED_TASK = "{\"type\":\"reprocessing-all\",\"repositorySize\":5,\"repositoryPath\":\"a\",\"targetQueue\":\"queue\",\"targetProcessor\":\"targetProcessor\"}";
    private static final String OLD_SERIALIZED_TASK_ADDITIONAL_INFORMATION = "{\"type\":\"reprocessing-all\", \"repositoryPath\":\"a\",\"targetQueue\":\"queue\",\"targetProcessor\":\"targetProcessor\",\"initialCount\":5,\"remainingCount\":3, \"timestamp\":\"2018-11-13T12:00:55Z\"}";

    private RecursiveComparisonConfiguration recursiveComparisonConfiguration;

    @BeforeEach
    void setUp() {
        recursiveComparisonConfiguration = new RecursiveComparisonConfiguration();
        recursiveComparisonConfiguration.registerComparatorForType(Comparator.comparingInt(AtomicInteger::get), AtomicInteger.class);
        recursiveComparisonConfiguration.registerComparatorForType(Comparator.comparingLong(AtomicLong::get), AtomicLong.class);
        recursiveComparisonConfiguration.registerEqualsForType((o, o2) -> o.get() == o2.get(), AtomicInteger.class);
        recursiveComparisonConfiguration.registerEqualsForType((o, o2) -> o.get() == o2.get(), AtomicLong.class);
        recursiveComparisonConfiguration.registerEqualsForType((o, o2) -> o.get() == o2.get(), AtomicBoolean.class);
    }

    @Test
    void taskShouldBeSerializable() throws Exception {
        ReprocessingAllMailsTask taskWithTargetProcessor = new ReprocessingAllMailsTask(REPROCESSING_SERVICE, REPOSITORY_SIZE, REPOSITORY_PATH, new ReprocessingService.Configuration(TARGET_QUEUE, SOME_TARGET_PROCESSOR, NO_MAX_RETRIES, CONSUME, Limit.unlimited()));
        ReprocessingAllMailsTask taskWithoutTargetProcessor = new ReprocessingAllMailsTask(REPROCESSING_SERVICE, REPOSITORY_SIZE, REPOSITORY_PATH, new ReprocessingService.Configuration(TARGET_QUEUE, NO_PROCESSOR, NO_MAX_RETRIES, !CONSUME, Limit.unlimited()));

        ReprocessingAllMailsTask taskWithLimit = new ReprocessingAllMailsTask(REPROCESSING_SERVICE, REPOSITORY_SIZE, REPOSITORY_PATH, new ReprocessingService.Configuration(TARGET_QUEUE, SOME_TARGET_PROCESSOR, NO_MAX_RETRIES, CONSUME, Limit.limit(10)));

        JsonSerializationVerifier.dtoModule(ReprocessingAllMailsTaskDTO.module(REPROCESSING_SERVICE))
            .testCase(taskWithTargetProcessor, SERIALIZED_TASK_WITH_TARGET_PROCESSOR)
            .testCase(taskWithoutTargetProcessor, SERIALIZED_TASK_WITHOUT_TARGET_PROCESSOR)
            .testCase(taskWithLimit,"{\"type\":\"reprocessing-all\",\"repositorySize\":5,\"repositoryPath\":\"a\",\"targetQueue\":\"queue\",\"targetProcessor\":\"targetProcessor\",\"consume\":true, \"limit\":10}" )
            .verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"type\":\"reprocessing-all\",\"repositorySize\":5,\"repositoryPath\":\"%\",\"targetQueue\":\"queue\",\"targetProcessor\":\"targetProcessor\"}",
        "{\"type\":\"reprocessing-all\",\"repositorySize\":5,\"repositoryPath\":\"%\",\"targetQueue\":\"queue\"}"})
    void taskShouldThrowOnDeserializationUrlDecodingError(String serialized) {
        JsonTaskSerializer testee = JsonTaskSerializer.of(ReprocessingAllMailsTaskDTO.module(REPROCESSING_SERVICE));

        assertThatThrownBy(() -> testee.deserialize(serialized))
            .isInstanceOf(ReprocessingAllMailsTask.InvalidMailRepositoryPathDeserializationException.class);
    }

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        ReprocessingAllMailsTask.AdditionalInformation details = new ReprocessingAllMailsTask.AdditionalInformation(REPOSITORY_PATH,
            new ReprocessingService.Configuration(TARGET_QUEUE, SOME_TARGET_PROCESSOR, NO_MAX_RETRIES, CONSUME, Limit.unlimited()),
            REPOSITORY_SIZE, REMAINING_COUNT, TIMESTAMP);
        ReprocessingAllMailsTask.AdditionalInformation detailsWithoutProcessor = new ReprocessingAllMailsTask.AdditionalInformation(REPOSITORY_PATH,
            new ReprocessingService.Configuration(TARGET_QUEUE, NO_PROCESSOR, NO_MAX_RETRIES, !CONSUME, Limit.unlimited()),
            REPOSITORY_SIZE, REMAINING_COUNT, TIMESTAMP);

        ReprocessingAllMailsTask.AdditionalInformation detailWithLimit = new ReprocessingAllMailsTask.AdditionalInformation(REPOSITORY_PATH,
            new ReprocessingService.Configuration(TARGET_QUEUE, SOME_TARGET_PROCESSOR, NO_MAX_RETRIES, CONSUME, Limit.limit(10)),
            REPOSITORY_SIZE, REMAINING_COUNT, TIMESTAMP);

        JsonSerializationVerifier.dtoModule(ReprocessingAllMailsTaskAdditionalInformationDTO.module())
            .testCase(details, SERIALIZED_TASK_ADDITIONAL_INFORMATION_WITH_TARGET_PROCESSOR)
            .testCase(detailsWithoutProcessor, SERIALIZED_TASK_ADDITIONAL_INFORMATION_WITHOUT_TARGET_PROCESSOR)
            .testCase(detailWithLimit, "{\"type\":\"reprocessing-all\", \"repositoryPath\":\"a\",\"targetQueue\":\"queue\",\"targetProcessor\":\"targetProcessor\",\"initialCount\":5,\"remainingCount\":3, \"timestamp\":\"2018-11-13T12:00:55Z\",\"consume\":true, \"limit\": 10}")
            .verify();
    }

    @Test
    void additionalInformationShouldBeSerializableWithMaxRetries() throws Exception {
        ReprocessingAllMailsTask.AdditionalInformation details = new ReprocessingAllMailsTask.AdditionalInformation(REPOSITORY_PATH,
            new ReprocessingService.Configuration(TARGET_QUEUE, SOME_TARGET_PROCESSOR, Optional.of(6), CONSUME, Limit.limit(10)),
            REPOSITORY_SIZE, REMAINING_COUNT, TIMESTAMP);

        JsonSerializationVerifier.dtoModule(ReprocessingAllMailsTaskAdditionalInformationDTO.module())
            .testCase(details, "{\"type\":\"reprocessing-all\", \"maxRetries\":6, \"repositoryPath\":\"a\",\"targetQueue\":\"queue\",\"targetProcessor\":\"targetProcessor\",\"initialCount\":5,\"remainingCount\":3, \"timestamp\":\"2018-11-13T12:00:55Z\",\"consume\":true, \"limit\": 10}")
            .verify();
    }

    @Test
    void shouldDeserializePreviousTaskFormat() throws Exception {
        ReprocessingAllMailsTask taskWithTargetProcessor = new ReprocessingAllMailsTask(REPROCESSING_SERVICE, REPOSITORY_SIZE, REPOSITORY_PATH, new ReprocessingService.Configuration(TARGET_QUEUE, SOME_TARGET_PROCESSOR, NO_MAX_RETRIES, CONSUME, Limit.unlimited()));
        JsonTaskSerializer testee = JsonTaskSerializer.of(ReprocessingAllMailsTaskDTO.module(REPROCESSING_SERVICE));

        assertThat(testee.deserialize(OLD_SERIALIZED_TASK))
            .usingRecursiveComparison(recursiveComparisonConfiguration)
            .isEqualTo(taskWithTargetProcessor);

        String serializedTaskWithLimit = "{\"type\":\"reprocessing-all\",\"repositorySize\":5,\"repositoryPath\":\"a\",\"targetQueue\":\"queue\",\"targetProcessor\":\"targetProcessor\", \"limit\":10}";
        assertThat(testee.deserialize(serializedTaskWithLimit))
            .usingRecursiveComparison(recursiveComparisonConfiguration)
            .isEqualTo(new ReprocessingAllMailsTask(REPROCESSING_SERVICE, REPOSITORY_SIZE, REPOSITORY_PATH, new ReprocessingService.Configuration(TARGET_QUEUE, SOME_TARGET_PROCESSOR, NO_MAX_RETRIES, CONSUME, Limit.limit(10))));
    }

    @Test
    void shouldDeserializeMaxRetries() throws Exception {
        JsonTaskSerializer testee = JsonTaskSerializer.of(ReprocessingAllMailsTaskDTO.module(REPROCESSING_SERVICE));

        String serializedTaskWithLimit = "{\"type\":\"reprocessing-all\", \"maxRetries\":6,\"repositorySize\":5,\"repositoryPath\":\"a\",\"targetQueue\":\"queue\",\"targetProcessor\":\"targetProcessor\", \"limit\":10}";
        assertThat(testee.deserialize(serializedTaskWithLimit))
            .usingRecursiveComparison(recursiveComparisonConfiguration)
            .isEqualTo(new ReprocessingAllMailsTask(REPROCESSING_SERVICE, REPOSITORY_SIZE, REPOSITORY_PATH, new ReprocessingService.Configuration(TARGET_QUEUE, SOME_TARGET_PROCESSOR, Optional.of(6), CONSUME, Limit.limit(10))));
    }

    @Test
    void shouldDeserializePreviousAdditionalInformationFormat() throws Exception {
        ReprocessingAllMailsTask.AdditionalInformation details = new ReprocessingAllMailsTask.AdditionalInformation(REPOSITORY_PATH,
            new ReprocessingService.Configuration(TARGET_QUEUE, SOME_TARGET_PROCESSOR, NO_MAX_RETRIES, CONSUME, Limit.unlimited()),
            REPOSITORY_SIZE, REMAINING_COUNT, TIMESTAMP);
        JsonTaskAdditionalInformationSerializer testee = JsonTaskAdditionalInformationSerializer.of(ReprocessingAllMailsTaskAdditionalInformationDTO.module());

        assertThat(testee.deserialize(OLD_SERIALIZED_TASK_ADDITIONAL_INFORMATION))
            .usingRecursiveComparison(recursiveComparisonConfiguration)
            .isEqualTo(details);

        String serializedTaskAdditionalInformation = "{\"type\":\"reprocessing-all\", \"repositoryPath\":\"a\",\"targetQueue\":\"queue\",\"targetProcessor\":\"targetProcessor\",\"initialCount\":5,\"remainingCount\":3, \"timestamp\":\"2018-11-13T12:00:55Z\", \"limit\": 10}";

        assertThat(testee.deserialize(serializedTaskAdditionalInformation))
            .usingRecursiveComparison(recursiveComparisonConfiguration)
            .isEqualTo(new ReprocessingAllMailsTask.AdditionalInformation(REPOSITORY_PATH,
                new ReprocessingService.Configuration(TARGET_QUEUE, SOME_TARGET_PROCESSOR, NO_MAX_RETRIES, CONSUME, Limit.limit(10)),
                REPOSITORY_SIZE, REMAINING_COUNT, TIMESTAMP));
    }
}