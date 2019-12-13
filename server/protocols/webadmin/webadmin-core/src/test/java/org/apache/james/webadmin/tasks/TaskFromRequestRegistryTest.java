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

package org.apache.james.webadmin.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.task.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import spark.Request;

class TaskFromRequestRegistryTest {
    static final Task TASK_1 = mock(Task.class);
    static final Task TASK_2 = mock(Task.class);
    static final TaskRegistrationKey KEY_1 = TaskRegistrationKey.of("task1");
    static final TaskRegistrationKey KEY_2 = TaskRegistrationKey.of("task2");

    Request request;
    TaskFromRequestRegistry taskFromRequestRegistry;
    TaskFromRequestRegistry singleTaskFromRequestRegistry;

    @BeforeEach
    void setUp() {
        request = mock(Request.class);
        taskFromRequestRegistry = TaskFromRequestRegistry.builder()
            .register(KEY_1, any -> TASK_1)
            .register(KEY_2, any -> TASK_2)
            .build();
        singleTaskFromRequestRegistry = TaskFromRequestRegistry.of(KEY_1, any -> TASK_1);
    }

    @Test
    void buildShouldThrowWhenNoTasks() {
        assertThatThrownBy(() -> TaskFromRequestRegistry.builder().build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void generateShouldThrowFormattedMessageWhenNoTaskParamAndSeveralOptions() {
        assertThatThrownBy(() -> taskFromRequestRegistry.fromRequest(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("'action' query parameter is compulsory. Supported values are [task1, task2]");
    }

    @Test
    void generateShouldThrowFormattedMessageWhenNoTaskParam() {
        assertThatThrownBy(() -> singleTaskFromRequestRegistry.fromRequest(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("'action' query parameter is compulsory. Supported values are [task1]");
    }

    @Test
    void generateShouldThrowWhenCustomParameterValueIsInvalid() {
        TaskFromRequestRegistry taskFromRequestRegistry = TaskFromRequestRegistry.builder()
            .parameterName("custom")
            .register(KEY_1, any -> TASK_1)
            .build();

        when(request.queryParams("custom")).thenReturn("unknown");

        assertThatThrownBy(() -> taskFromRequestRegistry.fromRequest(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid value supplied for query parameter 'custom': unknown. Supported values are [task1]");
    }

    @Test
    void generateShouldThrowWhenCustomParameterNotSpecified() {
        TaskFromRequestRegistry taskFromRequestRegistry = TaskFromRequestRegistry.builder()
            .parameterName("custom")
            .register(KEY_1, any -> TASK_1)
            .build();

        when(request.queryParams("action")).thenReturn("unknown");

        assertThatThrownBy(() -> taskFromRequestRegistry.fromRequest(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("'custom' query parameter is compulsory. Supported values are [task1]");
    }

    @Test
    void generateShouldThrowFormattedMessageWhenUnknownTaskParamAndSeveralOptions() {
        when(request.queryParams("action")).thenReturn("unknown");

        assertThatThrownBy(() -> taskFromRequestRegistry.fromRequest(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid value supplied for query parameter 'action': unknown. Supported values are [task1, task2]");
    }

    @Test
    void generateShouldThrowFormattedMessageWhenUnknownTaskParam() {
        when(request.queryParams("action")).thenReturn("unknown");

        assertThatThrownBy(() -> singleTaskFromRequestRegistry.fromRequest(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid value supplied for query parameter 'action': unknown. Supported values are [task1]");
    }

    @Test
    void generateShouldThrowWhenEmptyTaskParam() {
        when(request.queryParams("action")).thenReturn("");

        assertThatThrownBy(() -> singleTaskFromRequestRegistry.fromRequest(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("'action' query parameter cannot be empty or blank. Supported values are [task1]");
    }

    @Test
    void generateShouldThrowWhenBlankTaskParam() {
        when(request.queryParams("action")).thenReturn(" ");

        assertThatThrownBy(() -> singleTaskFromRequestRegistry.fromRequest(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("'action' query parameter cannot be empty or blank. Supported values are [task1]");
    }

    @Test
    void generateShouldCreateCorrespondingTask() throws Exception {
        when(request.queryParams("action")).thenReturn("task1");

        assertThat(singleTaskFromRequestRegistry.fromRequest(request))
            .isSameAs(TASK_1);
    }

    @Test
    void generateShouldHandleCustomTaskParameter() throws Exception {
        TaskFromRequestRegistry taskFromRequestRegistry = TaskFromRequestRegistry.builder()
            .parameterName("custom")
            .register(KEY_1, any -> TASK_1)
            .build();

        when(request.queryParams("custom")).thenReturn("task1");

        assertThat(taskFromRequestRegistry.fromRequest(request))
            .isSameAs(TASK_1);
    }
}