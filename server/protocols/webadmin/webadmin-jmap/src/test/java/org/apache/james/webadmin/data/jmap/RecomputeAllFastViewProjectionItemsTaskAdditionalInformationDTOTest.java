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

package org.apache.james.webadmin.data.jmap;

import static org.apache.james.JsonSerializationVerifier.recursiveComparisonConfiguration;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.json.JsonGenericSerializer;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.Test;

class RecomputeAllFastViewProjectionItemsTaskAdditionalInformationDTOTest {
    private static final Instant INSTANT = Instant.parse("2007-12-03T10:15:30.00Z");
    private static final RecomputeAllFastViewProjectionItemsTask.AdditionalInformation DOMAIN_OBJECT = new RecomputeAllFastViewProjectionItemsTask.AdditionalInformation(
        RunningOptions.withMessageRatePerSecond(20),
        1,
        2,
        3,
        4,
        INSTANT);

    @Test
    void shouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(RecomputeAllFastViewTaskAdditionalInformationDTO.module())
            .bean(DOMAIN_OBJECT)
            .json(ClassLoaderUtils.getSystemResourceAsString("json/recomputeAll.additionalInformation.json"))
            .verify();
    }

    @Test
    void shouldDeserializeLegacy() throws Exception {
        RecomputeAllFastViewProjectionItemsTask.AdditionalInformation legacyDetails = JsonGenericSerializer.forModules(RecomputeAllFastViewTaskAdditionalInformationDTO.module())
            .withoutNestedType()
            .deserialize(ClassLoaderUtils.getSystemResourceAsString("json/recomputeAll.additionalInformation.legacy.json"));

        RecomputeAllFastViewProjectionItemsTask.AdditionalInformation expected = new RecomputeAllFastViewProjectionItemsTask.AdditionalInformation(
            RunningOptions.DEFAULT,
            1,
            2,
            3,
            4,
            INSTANT);

        assertThat(legacyDetails)
            .usingRecursiveComparison(recursiveComparisonConfiguration)
            .isEqualTo(expected);
    }
}