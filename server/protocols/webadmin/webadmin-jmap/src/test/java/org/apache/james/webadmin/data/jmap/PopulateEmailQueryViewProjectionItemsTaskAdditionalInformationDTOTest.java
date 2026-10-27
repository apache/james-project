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
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.Test;

class PopulateEmailQueryViewProjectionItemsTaskAdditionalInformationDTOTest {
    private static final Instant INSTANT = Instant.parse("2007-12-03T10:15:30.00Z");
    private static final PopulateEmailQueryViewTask.AdditionalInformation DOMAIN_OBJECT = new PopulateEmailQueryViewTask.AdditionalInformation(
        RunningOptions.withMessageRatePerSecond(20),
        1,
        2,
        3,
        4,
        INSTANT);
    private static final PopulateEmailQueryViewTask.LegacyAdditionalInformation LEGACY_DOMAIN_OBJECT = new PopulateEmailQueryViewTask.LegacyAdditionalInformation(
        RunningOptions.withMessageRatePerSecond(20),
        1,
        2,
        3,
        4,
        INSTANT);

    @Test
    void shouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(PopulateEmailQueryViewTaskAdditionalInformationDTOV2.module())
            .bean(DOMAIN_OBJECT)
            .json(ClassLoaderUtils.getSystemResourceAsString("json/populateAll.additionalInformation.json"))
            .verify();
    }

    @Test
    void legacyShouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(PopulateEmailQueryViewTaskAdditionalInformationDTO.module())
            .bean(LEGACY_DOMAIN_OBJECT)
            .json(ClassLoaderUtils.getSystemResourceAsString("json/populateAll.additionalInformation.legacy.json"))
            .verify();
    }

    @Test
    void bothFormatsShouldBeReadableWhenBothModulesAreRegistered() throws Exception {
        JsonGenericSerializer<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> serializer = JsonGenericSerializer
            .<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO>forModules(PopulateEmailQueryViewTaskAdditionalInformationDTOV2.module(), PopulateEmailQueryViewTaskAdditionalInformationDTO.module())
            .withoutNestedType();

        assertThat(serializer.deserialize(ClassLoaderUtils.getSystemResourceAsString("json/populateAll.additionalInformation.legacy.json")))
            .usingRecursiveComparison(recursiveComparisonConfiguration)
            .isEqualTo(LEGACY_DOMAIN_OBJECT);
        assertThat(serializer.deserialize(ClassLoaderUtils.getSystemResourceAsString("json/populateAll.additionalInformation.json")))
            .usingRecursiveComparison(recursiveComparisonConfiguration)
            .isEqualTo(DOMAIN_OBJECT);
    }
}
