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
package org.apache.james.backends.cassandra.migration;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.time.Instant;

import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.server.task.json.JsonTaskAdditionalInformationSerializer;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

class MigrationTaskSerializationTest {

    private static final int SCHEMA_VERSION = 12;
    private static final String SERIALIZED_TASK = "{\"type\": \"cassandra-migration\", \"targetVersion\": 12}";
    private static final String SERIALIZED_ADDITIONAL_INFORMATION = "{\"type\": \"cassandra-migration\", \"targetVersion\": 12, \"timestamp\": \"2018-11-13T12:00:55Z\"}";
    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");

    private final CassandraSchemaVersionDAO cassandraSchemaVersionDAO = mock(CassandraSchemaVersionDAO.class);
    private final CassandraSchemaTransitions transitions = mock(CassandraSchemaTransitions.class);
    private final MigrationTask.Factory factory = target -> new MigrationTask(cassandraSchemaVersionDAO, transitions, target);
    private final JsonTaskSerializer taskSerializer = JsonTaskSerializer.of(MigrationTaskDTO.module(factory));
    private JsonTaskAdditionalInformationSerializer jsonAdditionalInformationSerializer = JsonTaskAdditionalInformationSerializer.of(MigrationTaskAdditionalInformationDTO.serializationModule());

    @Test
    void taskShouldBeSerializable() throws JsonProcessingException {
        MigrationTask task = factory.create(new SchemaVersion(SCHEMA_VERSION));
        assertThatJson(taskSerializer.serialize(task)).isEqualTo(SERIALIZED_TASK);
    }

    @Test
    void taskShouldBeDeserializable() throws IOException {
        MigrationTask task = factory.create(new SchemaVersion(SCHEMA_VERSION));
        assertThat(taskSerializer.deserialize(SERIALIZED_TASK))
            .isEqualToComparingFieldByField(task);
    }

    @Test
    void additionalInformationShouldBeSerializable() throws JsonProcessingException {
        MigrationTask.AdditionalInformation details = new MigrationTask.AdditionalInformation(new SchemaVersion(SCHEMA_VERSION), TIMESTAMP);
        assertThatJson(jsonAdditionalInformationSerializer.serialize(details)).isEqualTo(SERIALIZED_ADDITIONAL_INFORMATION);
    }

    @Test
    void additionalInformationShouldBeDeserializable() throws IOException {
        MigrationTask.AdditionalInformation details = new MigrationTask.AdditionalInformation(new SchemaVersion(SCHEMA_VERSION), TIMESTAMP);
        assertThat(jsonAdditionalInformationSerializer.deserialize(SERIALIZED_ADDITIONAL_INFORMATION))
            .isEqualToComparingFieldByField(details);
    }
}