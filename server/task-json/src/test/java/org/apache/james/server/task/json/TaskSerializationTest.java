/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/

package org.apache.james.server.task.json;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.CompletedTaskDTO;
import org.apache.james.server.task.json.dto.FailedTaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.server.task.json.dto.ThrowingTaskDTO;
import org.apache.james.task.CompletedTask;
import org.apache.james.task.FailedTask;
import org.apache.james.task.Task;
import org.apache.james.task.ThrowingTask;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

class TaskSerializationTest {

    private static final String SERIALIZED_FAILED_TASK = "{\"type\": \"failed-task\"}";
    private TaskDTOModule<FailedTask, FailedTaskDTO> failedTaskModule = DTOModule
        .forDomainObject(FailedTask.class)
        .convertToDTO(FailedTaskDTO.class)
        .toDomainObjectConverter(dto -> new FailedTask())
        .toDTOConverter((task, typeName) -> new FailedTaskDTO(typeName))
        .typeName("failed-task")
        .withFactory(TaskDTOModule::new);

    private static final String SERIALIZED_COMPLETED_TASK = "{\"type\": \"completed-task\"}";
    private TaskDTOModule<CompletedTask, CompletedTaskDTO> completedTaskModule = DTOModule
        .forDomainObject(CompletedTask.class)
        .convertToDTO(CompletedTaskDTO.class)
        .toDomainObjectConverter(dto -> new CompletedTask())
        .toDTOConverter((task, typeName) -> new CompletedTaskDTO(typeName))
        .typeName("completed-task")
        .withFactory(TaskDTOModule::new);

    private static final String SERIALIZED_THROWING_TASK = "{\"type\": \"throwing-task\"}";
    private TaskDTOModule<ThrowingTask, ThrowingTaskDTO> throwingTaskModule = DTOModule
        .forDomainObject(ThrowingTask.class)
        .convertToDTO(ThrowingTaskDTO.class)
        .toDomainObjectConverter(dto -> new ThrowingTask())
        .toDTOConverter((task, typeName) -> new ThrowingTaskDTO(typeName))
        .typeName("throwing-task")
        .withFactory(TaskDTOModule::new);

    @Test
    void failedTaskShouldSerialize() throws JsonProcessingException {
        FailedTask failedTask = new FailedTask();

        String actual = new JsonTaskSerializer(failedTaskModule).serialize(failedTask);
        assertThatJson(actual).isEqualTo(SERIALIZED_FAILED_TASK);
    }

    @Test
    void failedTaskShouldDeserialize() throws IOException {
        Task task = new JsonTaskSerializer(failedTaskModule).deserialize(SERIALIZED_FAILED_TASK);
        assertThat(task).isInstanceOf(FailedTask.class);
    }

    @Test
    void completedTaskShouldSerialize() throws JsonProcessingException {
        CompletedTask completedTask = new CompletedTask();

        String actual = new JsonTaskSerializer(completedTaskModule).serialize(completedTask);
        assertThatJson(actual).isEqualTo(SERIALIZED_COMPLETED_TASK);
    }

    @Test
    void completedTaskShouldDeserialize() throws IOException {
        Task task = new JsonTaskSerializer(completedTaskModule).deserialize(SERIALIZED_COMPLETED_TASK);
        assertThat(task).isInstanceOf(CompletedTask.class);
    }

    @Test
    void throwingTaskShouldSerialize() throws JsonProcessingException {
        ThrowingTask throwingTask = new ThrowingTask();

        String actual = new JsonTaskSerializer(throwingTaskModule).serialize(throwingTask);
        assertThatJson(actual).isEqualTo(SERIALIZED_THROWING_TASK);
    }

    @Test
    void throwingTaskShouldDeserialize() throws IOException {
        Task task = new JsonTaskSerializer(throwingTaskModule).deserialize(SERIALIZED_THROWING_TASK);
        assertThat(task).isInstanceOf(ThrowingTask.class);
    }
}
