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

import static org.apache.james.rspamd.task.FeedSpamToRSpamDTask.RunningOptions.DEFAULT_MESSAGES_PER_SECOND;
import static org.apache.james.rspamd.task.FeedSpamToRSpamDTask.RunningOptions.DEFAULT_PERIOD;
import static org.apache.james.rspamd.task.FeedSpamToRSpamDTask.RunningOptions.DEFAULT_SAMPLING_PROBABILITY;

import java.time.Instant;
import java.util.Optional;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.Test;

class FeedSpamToRSpamDTaskAdditionalInformationDTOTest {
    @Test
    void shouldMatchJsonSerializationContractWhenEmptyPeriod() throws Exception {
        JsonSerializationVerifier.dtoModule(FeedSpamToRSpamDTaskAdditionalInformationDTO.SERIALIZATION_MODULE)
            .bean(new FeedSpamToRSpamDTask.AdditionalInformation(
                Instant.parse("2007-12-03T10:15:30.00Z"),
                4,
                2,
                1,
                DEFAULT_MESSAGES_PER_SECOND,
                DEFAULT_PERIOD,
                DEFAULT_SAMPLING_PROBABILITY))
            .json(ClassLoaderUtils.getSystemResourceAsString("json/feedSpamEmptyPeriod.additionalInformation.json"))
            .verify();
    }

    @Test
    void shouldMatchJsonSerializationContractWhenNonEmptyPeriod() throws Exception {
        JsonSerializationVerifier.dtoModule(FeedSpamToRSpamDTaskAdditionalInformationDTO.SERIALIZATION_MODULE)
            .bean(new FeedSpamToRSpamDTask.AdditionalInformation(
                Instant.parse("2007-12-03T10:15:30.00Z"),
                4,
                2,
                1,
                DEFAULT_MESSAGES_PER_SECOND,
                Optional.of(3600L),
                DEFAULT_SAMPLING_PROBABILITY))
            .json(ClassLoaderUtils.getSystemResourceAsString("json/feedSpamNonEmptyPeriod.additionalInformation.json"))
            .verify();
    }
}
