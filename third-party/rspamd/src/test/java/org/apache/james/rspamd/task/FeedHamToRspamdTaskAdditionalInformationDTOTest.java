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

package org.apache.james.rspamd.task;

import static org.apache.james.rspamd.task.RunningOptions.DEFAULT_RSPAMD_TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.json.JsonGenericSerializer;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.Test;

class FeedHamToRspamdTaskAdditionalInformationDTOTest {
    @Test
    void shouldMatchJsonSerializationContractWhenEmptyPeriod() throws Exception {
        JsonSerializationVerifier.dtoModule(FeedHamToRspamdTaskAdditionalInformationDTO.SERIALIZATION_MODULE)
            .bean(new FeedHamToRspamdTask.AdditionalInformation(
                Instant.parse("2007-12-03T10:15:30.00Z"),
                4,
                2,
                1,
                RunningOptions.DEFAULT))
            .json(ClassLoaderUtils.getSystemResourceAsString("json/feedHamEmptyPeriod.additionalInformation.json"))
            .verify();
    }

    @Test
    void shouldMatchJsonSerializationContractWhenClassifiedAsHam() throws Exception {
        JsonSerializationVerifier.dtoModule(FeedHamToRspamdTaskAdditionalInformationDTO.SERIALIZATION_MODULE)
            .bean(new FeedHamToRspamdTask.AdditionalInformation(
                Instant.parse("2007-12-03T10:15:30.00Z"),
                4,
                2,
                1,
                new RunningOptions(
                    Optional.empty(),
                    RunningOptions.DEFAULT_MESSAGES_PER_SECOND,
                    RunningOptions.DEFAULT_SAMPLING_PROBABILITY,
                    Optional.of(false))))
            .json(ClassLoaderUtils.getSystemResourceAsString("json/feedHamClassifiedAsHam.additionalInformation.json"))
            .verify();
    }

    @Test
    void shouldMatchJsonSerializationContractWhenNonEmptyPeriod() throws Exception {
        JsonSerializationVerifier.dtoModule(FeedHamToRspamdTaskAdditionalInformationDTO.SERIALIZATION_MODULE)
            .bean(new FeedHamToRspamdTask.AdditionalInformation(
                Instant.parse("2007-12-03T10:15:30.00Z"),
                4,
                2,
                1,
                new RunningOptions(
                    Optional.of(3600L),
                    RunningOptions.DEFAULT_MESSAGES_PER_SECOND,
                    RunningOptions.DEFAULT_SAMPLING_PROBABILITY,
                    Optional.empty())))
            .json(ClassLoaderUtils.getSystemResourceAsString("json/feedHamNonEmptyPeriod.additionalInformation.json"))
            .verify();
    }

    @Test
    void shouldWellDeserializeWhenEmptyRspamdTimeoutField() throws Exception {
        assertThat(JsonGenericSerializer
            .forModules(FeedHamToRspamdTaskAdditionalInformationDTO.SERIALIZATION_MODULE)
            .withoutNestedType()
            .deserialize(ClassLoaderUtils.getSystemResourceAsString("json/feedHamEmptyRspamdTimeout.additionalInformation.json")))
            .isEqualTo(new FeedHamToRspamdTask.AdditionalInformation(
                Instant.parse("2007-12-03T10:15:30.00Z"),
                4,
                2,
                1,
                new RunningOptions(
                    Optional.of(3600L),
                    RunningOptions.DEFAULT_MESSAGES_PER_SECOND,
                    RunningOptions.DEFAULT_SAMPLING_PROBABILITY,
                    Optional.empty(),
                    DEFAULT_RSPAMD_TIMEOUT)));
    }

    @Test
    void shouldWellDeserializeWhenNonEmptyRspamdTimeoutField() throws Exception {
        assertThat(JsonGenericSerializer
            .forModules(FeedHamToRspamdTaskAdditionalInformationDTO.SERIALIZATION_MODULE)
            .withoutNestedType()
            .deserialize(ClassLoaderUtils.getSystemResourceAsString("json/feedHamNonEmptyRspamdTimeout.additionalInformation.json")))
            .isEqualTo(new FeedHamToRspamdTask.AdditionalInformation(
                Instant.parse("2007-12-03T10:15:30.00Z"),
                4,
                2,
                1,
                new RunningOptions(
                    Optional.of(3600L),
                    RunningOptions.DEFAULT_MESSAGES_PER_SECOND,
                    RunningOptions.DEFAULT_SAMPLING_PROBABILITY,
                    Optional.empty(),
                    Duration.ofSeconds(100))));
    }
}
