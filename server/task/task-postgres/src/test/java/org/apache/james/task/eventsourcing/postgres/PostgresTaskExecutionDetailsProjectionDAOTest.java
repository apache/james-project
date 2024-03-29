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

package org.apache.james.task.eventsourcing.postgres;

import static org.apache.james.task.TaskExecutionDetailsFixture.TASK_EXECUTION_DETAILS;
import static org.apache.james.task.TaskExecutionDetailsFixture.TASK_EXECUTION_DETAILS_2;
import static org.apache.james.task.TaskExecutionDetailsFixture.TASK_EXECUTION_DETAILS_UPDATED;
import static org.apache.james.task.TaskExecutionDetailsFixture.TASK_EXECUTION_DETAILS_WITH_ADDITIONAL_INFORMATION;
import static org.apache.james.task.TaskExecutionDetailsFixture.TASK_ID;
import static org.apache.james.task.TaskExecutionDetailsFixture.TASK_ID_2;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.server.task.json.JsonTaskAdditionalInformationSerializer;
import org.apache.james.server.task.json.dto.MemoryReferenceWithCounterTaskAdditionalInformationDTO;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskExecutionDetailsFixture;
import org.apache.james.task.TaskManager;
import org.apache.james.task.TaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Flux;

class PostgresTaskExecutionDetailsProjectionDAOTest {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresTaskExecutionDetailsProjectionModule.MODULE());

    private static final JsonTaskAdditionalInformationSerializer JSON_TASK_ADDITIONAL_INFORMATION_SERIALIZER = JsonTaskAdditionalInformationSerializer.of(MemoryReferenceWithCounterTaskAdditionalInformationDTO.SERIALIZATION_MODULE);

    private PostgresTaskExecutionDetailsProjectionDAO testee;

    @BeforeEach
    void setUp() {
        testee = new PostgresTaskExecutionDetailsProjectionDAO(postgresExtension.getPostgresExecutor(), JSON_TASK_ADDITIONAL_INFORMATION_SERIALIZER);
    }

    @Test
    void readDetailsShouldBeAbleToRetrieveASavedRecord() {
        testee.saveDetails(TASK_EXECUTION_DETAILS()).block();

        TaskExecutionDetails taskExecutionDetails = testee.readDetails(TASK_ID()).block();

        assertThat(taskExecutionDetails)
            .usingRecursiveComparison()
            .ignoringFields("submittedDate")
            .isEqualTo(TASK_EXECUTION_DETAILS());
    }

    @Test
    void readDetailsShouldBeAbleToRetrieveASavedRecordWithAdditionalInformation() {
        testee.saveDetails(TASK_EXECUTION_DETAILS_WITH_ADDITIONAL_INFORMATION()).block();

        TaskExecutionDetails taskExecutionDetails = testee.readDetails(TASK_ID()).block();

        assertThat(taskExecutionDetails)
            .usingRecursiveComparison()
            .ignoringFields("submittedDate")
            .isEqualTo(TASK_EXECUTION_DETAILS_WITH_ADDITIONAL_INFORMATION());

        assertThat(taskExecutionDetails.getSubmittedDate().isEqual(TASK_EXECUTION_DETAILS_WITH_ADDITIONAL_INFORMATION().getSubmittedDate()))
            .isTrue();
    }

    @Test
    void saveDetailsShouldUpdateRecords() {
        testee.saveDetails(TASK_EXECUTION_DETAILS()).block();

        testee.saveDetails(TASK_EXECUTION_DETAILS_UPDATED()).block();

        TaskExecutionDetails taskExecutionDetails = testee.readDetails(TASK_ID()).block();

        assertThat(taskExecutionDetails)
            .usingRecursiveComparison()
            .ignoringFields("submittedDate")
            .isEqualTo(TASK_EXECUTION_DETAILS_UPDATED());

        assertThat(taskExecutionDetails.getSubmittedDate().isEqual(TASK_EXECUTION_DETAILS_UPDATED().getSubmittedDate()))
            .isTrue();
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

        assertThat(taskExecutionDetails)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("submittedDate")
            .containsOnly(TASK_EXECUTION_DETAILS(), TASK_EXECUTION_DETAILS_2());
    }

    @Test
    void listDetailsShouldReturnLastUpdatedRecords() {
        testee.saveDetails(TASK_EXECUTION_DETAILS()).block();
        testee.saveDetails(TASK_EXECUTION_DETAILS_UPDATED()).block();

        Stream<TaskExecutionDetails> taskExecutionDetails = testee.listDetails().toStream();
        assertThat(taskExecutionDetails)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("submittedDate")
            .containsOnly(TASK_EXECUTION_DETAILS_UPDATED());
    }

    @Test
    void listBeforeDateShouldReturnCorrectEntry() {
        TaskExecutionDetails taskExecutionDetails1 = new TaskExecutionDetails(TASK_ID(),
            TaskType.of("type"),
            TaskManager.Status.COMPLETED,
            ZonedDateTime.ofInstant(Instant.parse("2000-01-01T00:00:00Z"), ZoneId.systemDefault()),
            TaskExecutionDetailsFixture.SUBMITTED_NODE(),
            Optional::empty,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

        TaskExecutionDetails taskExecutionDetails2 = new TaskExecutionDetails(TASK_ID_2(),
            TaskType.of("type"),
            TaskManager.Status.COMPLETED,
            ZonedDateTime.ofInstant(Instant.parse("2000-01-20T00:00:00Z"), ZoneId.systemDefault()),
            TaskExecutionDetailsFixture.SUBMITTED_NODE(),
            Optional::empty,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

        testee.saveDetails(taskExecutionDetails1).block();
        testee.saveDetails(taskExecutionDetails2).block();

        assertThat(Flux.from(testee.listDetailsByBeforeDate(Instant.parse("2000-01-15T12:00:55Z"))).collectList().block())
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("submittedDate")
            .containsOnly(taskExecutionDetails1);
    }

    @Test
    void removeShouldDeleteAssignEntry() {
        TaskExecutionDetails taskExecutionDetails1 = new TaskExecutionDetails(TASK_ID(),
            TaskType.of("type"),
            TaskManager.Status.COMPLETED,
            ZonedDateTime.ofInstant(Instant.parse("2000-01-01T00:00:00Z"), ZoneId.systemDefault()),
            TaskExecutionDetailsFixture.SUBMITTED_NODE(),
            Optional::empty,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

        testee.saveDetails(taskExecutionDetails1).block();

        assertThat(testee.listDetails().collectList().block())
            .hasSize(1);

        testee.remove(taskExecutionDetails1).block();

        assertThat(testee.listDetails().collectList().block())
            .isEmpty();
    }
}