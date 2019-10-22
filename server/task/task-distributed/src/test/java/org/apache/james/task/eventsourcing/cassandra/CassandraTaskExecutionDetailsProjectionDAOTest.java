/**
 * *************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.task.eventsourcing.cassandra;

import static org.apache.james.task.TaskExecutionDetailsFixture.TASK_EXECUTION_DETAILS;
import static org.apache.james.task.TaskExecutionDetailsFixture.TASK_EXECUTION_DETAILS_2;
import static org.apache.james.task.TaskExecutionDetailsFixture.TASK_EXECUTION_DETAILS_UPDATED;
import static org.apache.james.task.TaskExecutionDetailsFixture.TASK_EXECUTION_DETAILS_WITH_ADDITIONAL_INFORMATION;
import static org.apache.james.task.TaskExecutionDetailsFixture.TASK_ID;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.CassandraZonedDateTimeModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.server.task.json.JsonTaskAdditionalInformationSerializer;
import org.apache.james.server.task.json.dto.MemoryReferenceWithCounterTaskAdditionalInformationDTO;
import org.apache.james.task.TaskExecutionDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraTaskExecutionDetailsProjectionDAOTest {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(CassandraSchemaVersionModule.MODULE, CassandraZonedDateTimeModule.MODULE, CassandraTaskExecutionDetailsProjectionModule.MODULE()));
    private static final JsonTaskAdditionalInformationSerializer JSON_TASK_ADDITIONAL_INFORMATION_SERIALIZER = JsonTaskAdditionalInformationSerializer.of(MemoryReferenceWithCounterTaskAdditionalInformationDTO.SERIALIZATION_MODULE);

    private CassandraTaskExecutionDetailsProjectionDAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new CassandraTaskExecutionDetailsProjectionDAO(cassandra.getConf(), cassandra.getTypesProvider(), JSON_TASK_ADDITIONAL_INFORMATION_SERIALIZER);
    }

    @Test
    void readDetailsShouldBeAbleToRetrieveASavedRecord() {
        testee.saveDetails(TASK_EXECUTION_DETAILS()).block();

        TaskExecutionDetails taskExecutionDetails = testee.readDetails(TASK_ID()).block();
        assertThat(taskExecutionDetails).isEqualTo(TASK_EXECUTION_DETAILS());
    }

    @Test
    void readDetailsShouldBeAbleToRetrieveASavedRecordWithAdditionalInformation() {
        testee.saveDetails(TASK_EXECUTION_DETAILS_WITH_ADDITIONAL_INFORMATION()).block();

        TaskExecutionDetails taskExecutionDetails = testee.readDetails(TASK_ID()).block();
        assertThat(taskExecutionDetails).isEqualTo(TASK_EXECUTION_DETAILS_WITH_ADDITIONAL_INFORMATION());
    }

    @Test
    void saveDetailsShouldUpdateRecords() {
        testee.saveDetails(TASK_EXECUTION_DETAILS()).block();

        testee.saveDetails(TASK_EXECUTION_DETAILS_UPDATED()).block();

        TaskExecutionDetails taskExecutionDetails = testee.readDetails(TASK_ID()).block();
        assertThat(taskExecutionDetails).isEqualTo(TASK_EXECUTION_DETAILS_UPDATED());
    }

    @Test
    void readDetailsShouldReturnEmptyWhenNone() {
        Optional<TaskExecutionDetails> taskExecutionDetails = testee.readDetails(TASK_ID()).blockOptional();
        assertThat(taskExecutionDetails).isEmpty();
    }

    @Test
    void listDetailsShouldReturnEmptyWhenNone() {
        Stream<TaskExecutionDetails> taskExecutionDetails = testee.listDetails().toStream();
        assertThat(taskExecutionDetails).isEmpty();
    }

    @Test
    void listDetailsShouldReturnAllRecords() {
        testee.saveDetails(TASK_EXECUTION_DETAILS()).block();
        testee.saveDetails(TASK_EXECUTION_DETAILS_2()).block();

        Stream<TaskExecutionDetails> taskExecutionDetails = testee.listDetails().toStream();
        assertThat(taskExecutionDetails).containsOnly(TASK_EXECUTION_DETAILS(), TASK_EXECUTION_DETAILS_2());
    }

    @Test
    void listDetailsShouldReturnLastUpdatedRecords() {
        testee.saveDetails(TASK_EXECUTION_DETAILS()).block();
        testee.saveDetails(TASK_EXECUTION_DETAILS_UPDATED()).block();

        Stream<TaskExecutionDetails> taskExecutionDetails = testee.listDetails().toStream();
        assertThat(taskExecutionDetails).containsOnly(TASK_EXECUTION_DETAILS_UPDATED());
    }
}