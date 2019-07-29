/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/

package org.apache.james.task.eventsourcing.cassandra;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.CassandraZonedDateTimeModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraTaskExecutionDetailsProjectionDAOTest {

    private static final TaskId TASK_ID = TaskId.fromString("2c7f4081-aa30-11e9-bf6c-2d3b9e84aafd");
    private static final TaskId TASK_ID_2 = TaskId.fromString("2c7f4081-aa30-11e9-bf6c-2d3b9e84aafe");

    private static final Optional<TaskExecutionDetails.AdditionalInformation> ADDITIONAL_INFORMATION = Optional.empty();
    private static final Optional<ZonedDateTime> SUBMIT_DATE = Optional.empty();
    private static final Optional<ZonedDateTime> STARTED_DATE = Optional.empty();
    private static final Optional<ZonedDateTime> COMPLETED_DATE = Optional.empty();
    private static final Optional<ZonedDateTime> CANCELLED_DATE = Optional.empty();
    private static final Optional<ZonedDateTime> FAILED_DATE = Optional.empty();

    private static final TaskExecutionDetails TASK_EXECUTION_DETAILS =  new TaskExecutionDetails(TASK_ID, "type", ADDITIONAL_INFORMATION,
        TaskManager.Status.COMPLETED, SUBMIT_DATE, STARTED_DATE, COMPLETED_DATE, CANCELLED_DATE, FAILED_DATE);
    private static final TaskExecutionDetails TASK_EXECUTION_DETAILS_2 =  new TaskExecutionDetails(TASK_ID_2, "type", ADDITIONAL_INFORMATION,
        TaskManager.Status.COMPLETED, STARTED_DATE, STARTED_DATE, COMPLETED_DATE, CANCELLED_DATE, FAILED_DATE);
    private static final TaskExecutionDetails TASK_EXECUTION_DETAILS_UPDATED =  new TaskExecutionDetails(TASK_ID, "type", ADDITIONAL_INFORMATION,
        TaskManager.Status.FAILED, STARTED_DATE, STARTED_DATE, COMPLETED_DATE, CANCELLED_DATE, FAILED_DATE);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
            CassandraModule.aggregateModules(CassandraSchemaVersionModule.MODULE, CassandraZonedDateTimeModule.MODULE, CassandraTaskExecutionDetailsProjectionModule.MODULE()));

    private CassandraTaskExecutionDetailsProjectionDAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new CassandraTaskExecutionDetailsProjectionDAO(cassandra.getConf(), cassandra.getTypesProvider());
    }

    @Test
    void readDetailsShouldBeAbleToRetrieveASavedRecord() {
        testee.saveDetails(TASK_EXECUTION_DETAILS).block();

        TaskExecutionDetails taskExecutionDetails = testee.readDetails(TASK_ID).block();
        assertThat(taskExecutionDetails).isEqualTo(TASK_EXECUTION_DETAILS);
    }

    @Test
    void saveDetailsShouldUpdateRecords() {
        testee.saveDetails(TASK_EXECUTION_DETAILS).block();

        testee.saveDetails(TASK_EXECUTION_DETAILS_UPDATED).block();

        TaskExecutionDetails taskExecutionDetails = testee.readDetails(TASK_ID).block();
        assertThat(taskExecutionDetails).isEqualTo(TASK_EXECUTION_DETAILS_UPDATED);
    }

    @Test
    void readDetailsShouldReturnEmptyWhenNone() {
        Optional<TaskExecutionDetails> taskExecutionDetails = testee.readDetails(TASK_ID).blockOptional();
        assertThat(taskExecutionDetails).isEmpty();
    }

    @Test
    void listDetailsShouldReturnEmptyWhenNone() {
        Stream<TaskExecutionDetails> taskExecutionDetails = testee.listDetails().toStream();
        assertThat(taskExecutionDetails).isEmpty();
    }

    @Test
    void listDetailsShouldReturnAllRecords() {
        testee.saveDetails(TASK_EXECUTION_DETAILS).block();
        testee.saveDetails(TASK_EXECUTION_DETAILS_2).block();

        Stream<TaskExecutionDetails> taskExecutionDetails = testee.listDetails().toStream();
        assertThat(taskExecutionDetails).containsOnly(TASK_EXECUTION_DETAILS, TASK_EXECUTION_DETAILS_2);
    }

    @Test
    void listDetailsShouldReturnLastUpdatedRecords() {
        testee.saveDetails(TASK_EXECUTION_DETAILS).block();
        testee.saveDetails(TASK_EXECUTION_DETAILS_UPDATED).block();

        Stream<TaskExecutionDetails> taskExecutionDetails = testee.listDetails().toStream();
        assertThat(taskExecutionDetails).containsOnly(TASK_EXECUTION_DETAILS_UPDATED);
    }
}