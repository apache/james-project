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
package org.apache.james.webadmin.dto;

import java.time.Instant;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.backends.cassandra.migration.MigrationTask;
import org.apache.james.backends.cassandra.migration.MigrationTaskAdditionalInformationDTO;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.junit.jupiter.api.Test;

class WebAdminMigrationTaskSerializationTest {
    private static final String SERIALIZED_ADDITIONAL_INFORMATION = "{\"type\": \"cassandra-migration\", \"toVersion\": 12, \"timestamp\": \"2018-11-13T12:00:55Z\"}";
    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(WebAdminMigrationTaskAdditionalInformationDTO.serializationModule())
            .bean(new MigrationTask.AdditionalInformation(new SchemaVersion(12), TIMESTAMP))
            .json(SERIALIZED_ADDITIONAL_INFORMATION)
            .verify();
    }
}