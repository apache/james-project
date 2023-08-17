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

package org.apache.james.task.eventsourcing;

import static org.apache.james.task.TaskExecutionDetailsFixture.TASK_EXECUTION_DETAILS;
import static org.apache.james.task.TaskExecutionDetailsFixture.TASK_EXECUTION_DETAILS_2;
import static org.apache.james.task.TaskExecutionDetailsFixture.TASK_EXECUTION_DETAILS_UPDATED;
import static org.apache.james.task.TaskExecutionDetailsFixture.TASK_EXECUTION_DETAILS_WITH_ADDITIONAL_INFORMATION;
import static org.apache.james.task.TaskExecutionDetailsFixture.TASK_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static scala.jdk.javaapi.CollectionConverters.asJava;

import java.util.List;
import java.util.Optional;

import org.apache.james.task.TaskExecutionDetails;
import org.junit.jupiter.api.Test;

import scala.compat.java8.OptionConverters;

public interface TaskExecutionDetailsProjectionContract {

    TaskExecutionDetailsProjection testee();

    @Test
    default void loadShouldBeAbleToRetrieveASavedRecord() {
        TaskExecutionDetailsProjection testee = testee();
        testee.update(TASK_EXECUTION_DETAILS());

        Optional<TaskExecutionDetails> taskExecutionDetails = OptionConverters.toJava(testee.load(TASK_ID()));
        assertThat(taskExecutionDetails).contains(TASK_EXECUTION_DETAILS());
    }

    @Test
    default void readDetailsShouldBeAbleToRetrieveASavedRecordWithAdditionalInformation() {
        TaskExecutionDetailsProjection testee = testee();
        testee.update(TASK_EXECUTION_DETAILS_WITH_ADDITIONAL_INFORMATION());

        Optional<TaskExecutionDetails> taskExecutionDetails = OptionConverters.toJava(testee.load(TASK_ID()));
        assertThat(taskExecutionDetails).contains(TASK_EXECUTION_DETAILS_WITH_ADDITIONAL_INFORMATION());
    }

    @Test
    default void updateShouldUpdateRecords() {
        TaskExecutionDetailsProjection testee = testee();
        testee.update(TASK_EXECUTION_DETAILS());

        testee.update(TASK_EXECUTION_DETAILS_UPDATED());

        Optional<TaskExecutionDetails> taskExecutionDetails = OptionConverters.toJava(testee.load(TASK_ID()));
        assertThat(taskExecutionDetails).contains(TASK_EXECUTION_DETAILS_UPDATED());
    }

    @Test
    default void loadShouldReturnEmptyWhenNone() {
        TaskExecutionDetailsProjection testee = testee();
        Optional<TaskExecutionDetails> taskExecutionDetails = OptionConverters.toJava(testee.load(TASK_ID()));
        assertThat(taskExecutionDetails).isEmpty();
    }

    @Test
    default void listShouldReturnEmptyWhenNone() {
        TaskExecutionDetailsProjection testee = testee();
        List<TaskExecutionDetails> taskExecutionDetails = asJava(testee.list());
        assertThat(taskExecutionDetails).isEmpty();
    }

    @Test
    default void listShouldReturnAllRecords() {
        TaskExecutionDetailsProjection testee = testee();
        testee.update(TASK_EXECUTION_DETAILS());
        testee.update(TASK_EXECUTION_DETAILS_2());

        List<TaskExecutionDetails> taskExecutionDetails = asJava(testee.list());
        assertThat(taskExecutionDetails).containsOnly(TASK_EXECUTION_DETAILS(), TASK_EXECUTION_DETAILS_2());
    }

    @Test
    default void listDetailsShouldReturnLastUpdatedRecords() {
        TaskExecutionDetailsProjection testee = testee();
        testee.update(TASK_EXECUTION_DETAILS());
        testee.update(TASK_EXECUTION_DETAILS_UPDATED());

        List<TaskExecutionDetails> taskExecutionDetails = asJava(testee.list());
        assertThat(taskExecutionDetails).containsOnly(TASK_EXECUTION_DETAILS_UPDATED());
    }
}
