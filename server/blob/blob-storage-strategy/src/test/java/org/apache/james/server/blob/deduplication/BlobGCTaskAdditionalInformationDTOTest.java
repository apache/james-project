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

package org.apache.james.server.blob.deduplication;

import static org.apache.james.JsonSerializationVerifier.recursiveComparisonConfiguration;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.json.JsonGenericSerializer;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.Test;

class BlobGCTaskAdditionalInformationDTOTest {

    @Test
    void shouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(BlobGCTaskAdditionalInformationDTO.SERIALIZATION_MODULE)
            .bean(new BlobGCTask.AdditionalInformation(
                1,
                2,
                3,
                4,
                5,
                0.8,
                Instant.parse("2007-12-03T10:15:30.00Z"),
                100))
            .json(ClassLoaderUtils.getSystemResourceAsString("json/blobGC.additionalInformation.json"))
            .verify();
    }

    @Test
    void shouldDeserializeLegacyData() throws Exception {
        BlobGCTask.AdditionalInformation gcTask = JsonGenericSerializer
            .forModules(BlobGCTaskAdditionalInformationDTO.SERIALIZATION_MODULE)
            .withoutNestedType()
            .deserialize(ClassLoaderUtils.getSystemResourceAsString("json/blobGC-legacy.additionalInformation.json"));

        assertThat(gcTask)
            .usingRecursiveComparison(recursiveComparisonConfiguration)
            .isEqualTo(new BlobGCTask.AdditionalInformation(
                1,
                2,
                3,
                4,
                5,
                0.8,
                Instant.parse("2007-12-03T10:15:30.00Z"),
                1000));
    }
}
