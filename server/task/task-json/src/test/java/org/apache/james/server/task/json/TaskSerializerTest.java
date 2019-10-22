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

import org.apache.james.server.task.json.dto.TestTaskDTOModules;
import org.junit.jupiter.api.Test;

import net.javacrumbs.jsonunit.assertj.JsonAssertions;

class TaskSerializerTest {

    private static final String TASK_AS_STRING = "{" +
        "\"type\": \"test-task\"," +
        "\"parameter\": 1" +
        "}";

    @Test
    void shouldSerializeTaskWithItsType() throws Exception {
        JsonTaskSerializer testee = JsonTaskSerializer.of(TestTaskDTOModules.TEST_TYPE);
        long parameter = 1L;
        TestTask task = new TestTask(parameter);
        JsonAssertions.assertThatJson(testee.serialize(task)).isEqualTo(TASK_AS_STRING);
    }
}
