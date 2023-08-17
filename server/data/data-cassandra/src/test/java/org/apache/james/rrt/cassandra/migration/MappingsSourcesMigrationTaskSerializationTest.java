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

package org.apache.james.rrt.cassandra.migration;

import static org.mockito.Mockito.mock;

import java.time.Instant;

import org.apache.james.JsonSerializationVerifier;
import org.junit.jupiter.api.Test;

class MappingsSourcesMigrationTaskSerializationTest {
    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");
    private static final MappingsSourcesMigration MIGRATION = mock(MappingsSourcesMigration.class);
    private static final MappingsSourcesMigration.MappingsSourcesMigrationTask TASK = new MappingsSourcesMigration.MappingsSourcesMigrationTask(MIGRATION);
    private static final String SERIALIZED_TASK = "{\"type\": \"mappings-sources-migration\"}";
    private static final MappingsSourcesMigration.AdditionalInformation DETAILS = new MappingsSourcesMigration.AdditionalInformation(42L, 10, TIMESTAMP);
    private static final String SERIALIZED_ADDITIONAL_INFORMATION = "{\"type\": \"mappings-sources-migration\", \"successfulMappingsCount\":42,\"errorMappingsCount\":10,\"timestamp\":\"2018-11-13T12:00:55Z\"}";

    @Test
    void taskShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(MappingsSourcesMigrationTaskDTO.MODULE.apply(MIGRATION))
            .bean(TASK)
            .json(SERIALIZED_TASK)
            .verify();
    }

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(MappingsSourcesMigrationTaskAdditionalInformationDTO.module(MappingsSourcesMigration.TYPE))
            .bean(DETAILS)
            .json(SERIALIZED_ADDITIONAL_INFORMATION)
            .verify();
    }
}