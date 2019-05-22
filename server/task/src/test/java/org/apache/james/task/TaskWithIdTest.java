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

package org.apache.james.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class TaskWithIdTest {

    @Test
    public void twoTasksWithSameIdShouldBeEqual() {
        TaskId id = TaskId.generateTaskId();
        Task task1 = () -> Task.Result.COMPLETED;
        Task task2 = () -> Task.Result.COMPLETED;
        TaskWithId taskWithId1 = new TaskWithId(id, task1);
        TaskWithId taskWithId2 = new TaskWithId(id, task2);
        assertThat(taskWithId1).isEqualTo(taskWithId2);
    }

    @Test
    public void sameTaskWithDifferentIdShouldNotBeEqual() {
        TaskId id1 = TaskId.generateTaskId();
        TaskId id2 = TaskId.generateTaskId();
        Task task = () -> Task.Result.COMPLETED;
        TaskWithId taskWithId1 = new TaskWithId(id1, task);
        TaskWithId taskWithId2 = new TaskWithId(id2, task);
        assertThat(taskWithId1).isNotEqualTo(taskWithId2);
    }
}