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

import java.util.Objects;

import com.google.common.base.MoreObjects;

public class TaskType {
    private final String typeName;

    public static TaskType of(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            throw new IllegalArgumentException("Task type should be defined");
        }
        return new TaskType(typeName);
    }

    private TaskType(String typeName) {
        this.typeName = typeName;
    }

    public String asString() {
        return typeName;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof TaskType) {
            TaskType taskId = (TaskType) o;

            return Objects.equals(this.typeName, taskId.typeName);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(typeName);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("typeName", typeName)
            .toString();
    }

}
