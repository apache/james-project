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

import java.time.Instant;
import java.util.Optional;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.core.Username;
import org.apache.james.user.api.UsernameChangeTaskStep;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

class UsernameChangeTaskSerializationTest {
    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");
    private static final Username OLD_USERNAME = Username.of("user");
    private static final Username NEW_USERNAME = Username.of("geraldine");
    private static final UsernameChangeTaskStep.StepName STEP_A = new UsernameChangeTaskStep.StepName("A");
    private static final UsernameChangeTaskStep.StepName STEP_B = new UsernameChangeTaskStep.StepName("B");
    private static final UsernameChangeTaskStep.StepName STEP_C = new UsernameChangeTaskStep.StepName("C");
    private static final UsernameChangeTaskStep.StepName STEP_D = new UsernameChangeTaskStep.StepName("D");
    private static final UsernameChangeTaskStep A = asStep(STEP_A);
    private static final UsernameChangeTaskStep B = asStep(STEP_B);
    private static final UsernameChangeTaskStep C = asStep(STEP_C);
    private static final UsernameChangeTaskStep D = asStep(STEP_D);

    private static UsernameChangeTaskStep asStep(UsernameChangeTaskStep.StepName name) {
        return new UsernameChangeTaskStep() {
            @Override
            public StepName name() {
                return name;
            }

            @Override
            public int priority() {
                return 0;
            }

            @Override
            public Publisher<Void> changeUsername(Username oldUsername, Username newUsername) {
                return Mono.empty();
            }
        };
    }

    private static final String SERIALIZED_TASK = "{\"newUser\":\"geraldine\",\"oldUser\":\"user\",\"type\":\"UsernameChangeTask\"}";
    private static final String SERIALIZED_TASK_WITH_FROM_STEP = "{\"newUser\":\"geraldine\",\"fromStep\":\"B\",\"oldUser\":\"user\",\"type\":\"UsernameChangeTask\"}";
    private static final String SERIALIZED_ADDITIONAL_INFORMATION = "{" +
        "  \"newUser\":\"geraldine\"," +
        "  \"oldUser\":\"user\"," +
        "  \"status\":{" +
        "    \"A\":\"DONE\"," +
        "    \"B\":\"WAITING\"," +
        "    \"C\":\"FAILED\"," +
        "    \"D\":\"ABORTED\"}," +
        "  \"timestamp\":\"2018-11-13T12:00:55Z\"," +
        "  \"type\":\"UsernameChangeTask\"" +
        "}";
    private static final String SERIALIZED_ADDITIONAL_INFORMATION_WITH_STEP_NAME = "{" +
        "  \"newUser\":\"geraldine\"," +
        "  \"oldUser\":\"user\"," +
        "  \"fromStep\":\"B\"," +
        "  \"status\":{" +
        "    \"A\":\"DONE\"," +
        "    \"B\":\"WAITING\"," +
        "    \"C\":\"FAILED\"," +
        "    \"D\":\"ABORTED\"}," +
        "  \"timestamp\":\"2018-11-13T12:00:55Z\"," +
        "  \"type\":\"UsernameChangeTask\"" +
        "}";

    private static final UsernameChangeService SERVICE = new UsernameChangeService(ImmutableSet.of(A, B, C, D));

    @Test
    void taskShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(UsernameChangeTaskDTO.module(SERVICE))
            .bean(new UsernameChangeTask(SERVICE, OLD_USERNAME, NEW_USERNAME, Optional.empty()))
            .json(SERIALIZED_TASK)
            .verify();
    }

    @Test
    void taskShouldBeSerializableWithFromStep() throws Exception {
        JsonSerializationVerifier.dtoModule(UsernameChangeTaskDTO.module(SERVICE))
            .bean(new UsernameChangeTask(SERVICE, OLD_USERNAME, NEW_USERNAME, Optional.of(STEP_B)))
            .json(SERIALIZED_TASK_WITH_FROM_STEP)
            .verify();
    }

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(UsernameChangeTaskAdditionalInformationDTO.module())
            .bean(new UsernameChangeTask.AdditionalInformation(
                TIMESTAMP,
                OLD_USERNAME,
                NEW_USERNAME,
                ImmutableMap.of(STEP_A, UsernameChangeService.StepState.DONE,
                    STEP_B, UsernameChangeService.StepState.WAITING,
                    STEP_C, UsernameChangeService.StepState.FAILED,
                    STEP_D, UsernameChangeService.StepState.ABORTED), Optional.empty()))
            .json(SERIALIZED_ADDITIONAL_INFORMATION)
            .verify();
    }

    @Test
    void additionalInformationShouldBeSerializableWithStepName() throws Exception {
        JsonSerializationVerifier.dtoModule(UsernameChangeTaskAdditionalInformationDTO.module())
            .bean(new UsernameChangeTask.AdditionalInformation(
                TIMESTAMP,
                OLD_USERNAME,
                NEW_USERNAME,
                ImmutableMap.of(STEP_A, UsernameChangeService.StepState.DONE,
                    STEP_B, UsernameChangeService.StepState.WAITING,
                    STEP_C, UsernameChangeService.StepState.FAILED,
                    STEP_D, UsernameChangeService.StepState.ABORTED), Optional.of(STEP_B)))
            .json(SERIALIZED_ADDITIONAL_INFORMATION_WITH_STEP_NAME)
            .verify();
    }
}