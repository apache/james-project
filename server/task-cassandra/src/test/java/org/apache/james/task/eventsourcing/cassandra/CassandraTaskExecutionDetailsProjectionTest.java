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

import java.util.List;
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
import scala.collection.JavaConverters;
import scala.compat.java8.OptionConverters;

class CassandraTaskExecutionDetailsProjectionTest {

    private static final TaskId TASK_ID = TaskId.fromString("2c7f4081-aa30-11e9-bf6c-2d3b9e84aafd");
    private static final TaskId TASK_ID_2 = TaskId.fromString("2c7f4081-aa30-11e9-bf6c-2d3b9e84aafe");

    private static TaskExecutionDetails TASK_EXECUTION_DETAILS =  new TaskExecutionDetails(TASK_ID, "type", Optional.empty(),
            TaskManager.Status.COMPLETED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    private static TaskExecutionDetails TASK_EXECUTION_DETAILS_2 =  new TaskExecutionDetails(TASK_ID_2, "type", Optional.empty(),
            TaskManager.Status.COMPLETED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    private static TaskExecutionDetails TASK_EXECUTION_DETAILS_UPDATED =  new TaskExecutionDetails(TASK_ID, "type", Optional.empty(),
            TaskManager.Status.FAILED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
            CassandraModule.aggregateModules(CassandraSchemaVersionModule.MODULE, CassandraZonedDateTimeModule.MODULE, CassandraTaskExecutionDetailsProjectionModule.MODULE()));

    private CassandraTaskExecutionDetailsProjection testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        CassandraTaskExecutionDetailsProjectionDAO cassandraTaskExecutionDetailsProjectionDAO = new CassandraTaskExecutionDetailsProjectionDAO(cassandra.getConf(), cassandra.getTypesProvider());
        testee = new CassandraTaskExecutionDetailsProjection(cassandraTaskExecutionDetailsProjectionDAO);
    }

    @Test
    void loadShouldBeAbleToRetrieveASavedRecord() {
        testee.update(TASK_EXECUTION_DETAILS);

        Optional<TaskExecutionDetails> taskExecutionDetails = OptionConverters.toJava(testee.load(TASK_ID));
        assertThat(taskExecutionDetails).contains(TASK_EXECUTION_DETAILS);
    }

    @Test
    void updateShouldUpdateRecords() {
        testee.update(TASK_EXECUTION_DETAILS);

        testee.update(TASK_EXECUTION_DETAILS_UPDATED);

        Optional<TaskExecutionDetails> taskExecutionDetails = OptionConverters.toJava(testee.load(TASK_ID));
        assertThat(taskExecutionDetails).contains(TASK_EXECUTION_DETAILS_UPDATED);
    }

    @Test
    void loadShouldReturnEmptyWhenNone() {
        Optional<TaskExecutionDetails> taskExecutionDetails = OptionConverters.toJava(testee.load(TASK_ID));
        assertThat(taskExecutionDetails).isEmpty();
    }

    @Test
    void listShouldReturnEmptyWhenNone() {
        List<TaskExecutionDetails> taskExecutionDetails = JavaConverters.asJava(testee.list());
        assertThat(taskExecutionDetails).isEmpty();
    }

    @Test
    void listShouldReturnAllRecords() {
        testee.update(TASK_EXECUTION_DETAILS);
        testee.update(TASK_EXECUTION_DETAILS_2);

        List<TaskExecutionDetails> taskExecutionDetails = JavaConverters.asJava(testee.list());
        assertThat(taskExecutionDetails).containsOnly(TASK_EXECUTION_DETAILS, TASK_EXECUTION_DETAILS_2);
    }

    @Test
    void listDetailsShouldReturnLastUpdatedRecords() {
        testee.update(TASK_EXECUTION_DETAILS);
        testee.update(TASK_EXECUTION_DETAILS_UPDATED);

        List<TaskExecutionDetails> taskExecutionDetails = JavaConverters.asJava(testee.list());
        assertThat(taskExecutionDetails).containsOnly(TASK_EXECUTION_DETAILS_UPDATED);
    }
}
