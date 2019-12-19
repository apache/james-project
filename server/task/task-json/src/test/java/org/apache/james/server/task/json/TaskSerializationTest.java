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
import static org.apache.james.server.task.json.dto.TestTaskDTOModules.COMPLETED_TASK_MODULE;
import static org.apache.james.server.task.json.dto.TestTaskDTOModules.FAILED_TASK_MODULE;
import static org.apache.james.server.task.json.dto.TestTaskDTOModules.MEMORY_REFERENCE_TASK_MODULE;
import static org.apache.james.server.task.json.dto.TestTaskDTOModules.THROWING_TASK_MODULE;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.stream.Stream;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.server.task.json.dto.MemoryReferenceTaskStore;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.CompletedTask;
import org.apache.james.task.FailedTask;
import org.apache.james.task.MemoryReferenceTask;
import org.apache.james.task.Task;
import org.apache.james.task.ThrowingTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.core.JsonProcessingException;

class TaskSerializationTest {

    private static final String SERIALIZED_COMPLETED_TASK = "{\"type\": \"completed-task\"}";
    private static final String SERIALIZED_FAILED_TASK = "{\"type\": \"failed-task\"}";
    private static final String SERIALIZED_MEMORY_REFERENCE_TASK = "{\"type\": \"memory-reference-task\", \"reference\": 0}";
    private static final String SERIALIZED_THROWING_TASK = "{\"type\": \"throwing-task\"}";

    @ParameterizedTest
    @MethodSource
    void taskShouldBeSerializable(Task task, TaskDTOModule<Task, ? extends TaskDTOModule> module, String expectedJson) throws Exception {
        JsonSerializationVerifier.dtoModule(module)
            .bean(task)
            .json(expectedJson)
            .verify();
    }

    private static Stream<Arguments> taskShouldBeSerializable() {
        return Stream.of(
            Arguments.of(new CompletedTask(), COMPLETED_TASK_MODULE, SERIALIZED_COMPLETED_TASK),
            Arguments.of(new FailedTask(), FAILED_TASK_MODULE, SERIALIZED_FAILED_TASK),
            Arguments.of(new ThrowingTask(), THROWING_TASK_MODULE, SERIALIZED_THROWING_TASK));
    }

    @Test
    void memoryReferenceTaskShouldSerialize() throws JsonProcessingException {
        MemoryReferenceTask memoryReferenceTask = new MemoryReferenceTask(() -> Task.Result.COMPLETED);

        String actual = JsonTaskSerializer.of(MEMORY_REFERENCE_TASK_MODULE.apply(new MemoryReferenceTaskStore())).serialize(memoryReferenceTask);
        assertThatJson(actual).isEqualTo(SERIALIZED_MEMORY_REFERENCE_TASK);
    }

    @Test
    void memoryReferenceTaskShouldDeserialize() throws IOException {
        MemoryReferenceTaskStore memoryReferenceTaskStore = new MemoryReferenceTaskStore();
        MemoryReferenceTask memoryReferenceTask = new MemoryReferenceTask(() -> Task.Result.COMPLETED);
        memoryReferenceTaskStore.add(memoryReferenceTask);

        Task task = JsonTaskSerializer.of(MEMORY_REFERENCE_TASK_MODULE.apply(memoryReferenceTaskStore)).deserialize(SERIALIZED_MEMORY_REFERENCE_TASK);
        assertThat(task).isInstanceOf(MemoryReferenceTask.class);
    }
}
