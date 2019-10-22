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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import org.apache.james.server.task.json.dto.TestTaskDTOModules;
import org.apache.james.task.Task;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskDeserializerTest {

    private static final String TASK_AS_STRING = "{" +
        "\"type\": \"test-task\"," +
        "\"parameter\": 1" +
        "}";

    private static final String UNREGISTERED_TASK_AS_STRING = "{" +
        "\"type\": \"unknown\"," +
        "\"parameter\": 1" +
        "}";

    private static final String TWO_TYPES_TASK_AS_STRING = "{" +
        "\"type\": \"test-task\"," +
        "\"type\": \"unknown\"," +
        "\"parameter\": 1" +
        "}";

    private static final String MISSING_TASK_AS_STRING = "{" +
        "\"parameter\": 1" +
        "}";

    private JsonTaskSerializer testee;

    @BeforeEach
    void setUp() {
        testee = JsonTaskSerializer.of(TestTaskDTOModules.TEST_TYPE);
    }

    @Test
    void shouldDeserializeTaskWithRegisteredType() throws IOException {
        Task task = testee.deserialize(TASK_AS_STRING);
        Assertions.assertThat(task).isInstanceOf(TestTask.class);
        TestTask testTask = (TestTask) task;
        Assertions.assertThat(testTask.getParameter()).isEqualTo(1L);
    }

    @Test
    void shouldThrowWhenNotRegisteredType() {
        assertThatThrownBy(() -> testee.deserialize(UNREGISTERED_TASK_AS_STRING))
            .isInstanceOf(JsonTaskSerializer.UnknownTaskException.class);
    }

    @Test
    void shouldThrowWhenMissingType() {
        assertThatThrownBy(() -> testee.deserialize(MISSING_TASK_AS_STRING))
            .isInstanceOf(JsonTaskSerializer.InvalidTaskException.class);
    }

    @Test
    void shouldThrowWhenDuplicateType() {
        assertThatThrownBy(() -> testee.deserialize(TWO_TYPES_TASK_AS_STRING))
            .isInstanceOf(JsonTaskSerializer.InvalidTaskException.class);
    }

}
