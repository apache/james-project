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
import org.apache.james.core.Username;
import org.apache.james.json.JsonGenericSerializer;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.Test;

class RecomputeUserFastViewProjectionItemsTaskAdditionalInformationDTOTest {
    private static final Instant INSTANT = Instant.parse("2007-12-03T10:15:30.00Z");
    private static final RecomputeUserFastViewProjectionItemsTask.AdditionalInformation DOMAIN_OBJECT = new RecomputeUserFastViewProjectionItemsTask.AdditionalInformation(
        RunningOptions.withMessageRatePerSecond(20),
        Username.of("bob"), 2, 3, INSTANT);

    @Test
    void shouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(RecomputeUserFastViewTaskAdditionalInformationDTO.module())
            .bean(DOMAIN_OBJECT)
            .json(ClassLoaderUtils.getSystemResourceAsString("json/recomputeUser.additionalInformation.json"))
            .verify();
    }


    @Test
    void shouldDeserializeLegacy() throws Exception {
        RecomputeUserFastViewProjectionItemsTask.AdditionalInformation legacyDetails = JsonGenericSerializer.forModules(RecomputeUserFastViewTaskAdditionalInformationDTO.module())
            .withoutNestedType()
            .deserialize(ClassLoaderUtils.getSystemResourceAsString("json/recomputeUser.additionalInformation.legacy.json"));

        RecomputeUserFastViewProjectionItemsTask.AdditionalInformation expected = new RecomputeUserFastViewProjectionItemsTask.AdditionalInformation(
            RunningOptions.DEFAULT,
            Username.of("bob"),
            2,
            3,
            INSTANT);

        assertThat(legacyDetails)
            .usingRecursiveComparison(recursiveComparisonConfiguration)
            .isEqualTo(expected);
    }
}