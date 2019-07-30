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

package org.apache.james.task.eventsourcing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;

import org.junit.jupiter.api.Test;
import scala.collection.JavaConverters;
import scala.compat.java8.OptionConverters;

public interface TaskExecutionDetailsProjectionContract {

    TaskId TASK_ID = TaskId.fromString("2c7f4081-aa30-11e9-bf6c-2d3b9e84aafd");
    TaskId TASK_ID_2 = TaskId.fromString("2c7f4081-aa30-11e9-bf6c-2d3b9e84aafe");

    Optional<TaskExecutionDetails.AdditionalInformation> ADDITIONAL_INFORMATION = Optional.empty();
    Optional<ZonedDateTime> SUBMIT_DATE = Optional.empty();
    Optional<ZonedDateTime> STARTED_DATE = Optional.empty();
    Optional<ZonedDateTime> COMPLETED_DATE = Optional.empty();
    Optional<ZonedDateTime> CANCELLED_DATE = Optional.empty();
    Optional<ZonedDateTime> FAILED_DATE = Optional.empty();

    TaskExecutionDetails TASK_EXECUTION_DETAILS =  new TaskExecutionDetails(TASK_ID, "type", ADDITIONAL_INFORMATION,
        TaskManager.Status.COMPLETED, SUBMIT_DATE, STARTED_DATE, COMPLETED_DATE, CANCELLED_DATE, FAILED_DATE);
    TaskExecutionDetails TASK_EXECUTION_DETAILS_2 =  new TaskExecutionDetails(TASK_ID_2, "type", ADDITIONAL_INFORMATION,
        TaskManager.Status.COMPLETED, STARTED_DATE, STARTED_DATE, COMPLETED_DATE, CANCELLED_DATE, FAILED_DATE);
    TaskExecutionDetails TASK_EXECUTION_DETAILS_UPDATED =  new TaskExecutionDetails(TASK_ID, "type", ADDITIONAL_INFORMATION,
        TaskManager.Status.FAILED, STARTED_DATE, STARTED_DATE, COMPLETED_DATE, CANCELLED_DATE, FAILED_DATE);

    TaskExecutionDetailsProjection testee();

    @Test
    default void loadShouldBeAbleToRetrieveASavedRecord() {
        TaskExecutionDetailsProjection testee = testee();
        testee.update(TASK_EXECUTION_DETAILS);

        Optional<TaskExecutionDetails> taskExecutionDetails = OptionConverters.toJava(testee.load(TASK_ID));
        assertThat(taskExecutionDetails).contains(TASK_EXECUTION_DETAILS);
    }

    @Test
    default void updateShouldUpdateRecords() {
        TaskExecutionDetailsProjection testee = testee();
        testee.update(TASK_EXECUTION_DETAILS);

        testee.update(TASK_EXECUTION_DETAILS_UPDATED);

        Optional<TaskExecutionDetails> taskExecutionDetails = OptionConverters.toJava(testee.load(TASK_ID));
        assertThat(taskExecutionDetails).contains(TASK_EXECUTION_DETAILS_UPDATED);
    }

    @Test
    default void loadShouldReturnEmptyWhenNone() {
        TaskExecutionDetailsProjection testee = testee();
        Optional<TaskExecutionDetails> taskExecutionDetails = OptionConverters.toJava(testee.load(TASK_ID));
        assertThat(taskExecutionDetails).isEmpty();
    }

    @Test
    default void listShouldReturnEmptyWhenNone() {
        TaskExecutionDetailsProjection testee = testee();
        List<TaskExecutionDetails> taskExecutionDetails = JavaConverters.seqAsJavaList(testee.list());
        assertThat(taskExecutionDetails).isEmpty();
    }

    @Test
    default void listShouldReturnAllRecords() {
        TaskExecutionDetailsProjection testee = testee();
        testee.update(TASK_EXECUTION_DETAILS);
        testee.update(TASK_EXECUTION_DETAILS_2);

        List<TaskExecutionDetails> taskExecutionDetails = JavaConverters.seqAsJavaList(testee.list());
        assertThat(taskExecutionDetails).containsOnly(TASK_EXECUTION_DETAILS, TASK_EXECUTION_DETAILS_2);
    }

    @Test
    default void listDetailsShouldReturnLastUpdatedRecords() {
        TaskExecutionDetailsProjection testee = testee();
        testee.update(TASK_EXECUTION_DETAILS);
        testee.update(TASK_EXECUTION_DETAILS_UPDATED);

        List<TaskExecutionDetails> taskExecutionDetails = JavaConverters.seqAsJavaList(testee.list());
        assertThat(taskExecutionDetails).containsOnly(TASK_EXECUTION_DETAILS_UPDATED);
    }
}
