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

import java.util.Arrays;
import java.util.List;

public interface TaskManager {
    enum Status {
        WAITING("waiting"),
        IN_PROGRESS("inProgress"),
        COMPLETED("completed"),
        CANCELLED("canceled"),
        FAILED("failed");

        public static Status fromString(String value) {
            return Arrays.stream(values())
                .filter(status -> status.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    String.format("Unknown status value '%s'", value)));
        }

        private final String value;

        Status(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    TaskId submit(Task task);

    TaskExecutionDetails getExecutionDetails(TaskId id);

    List<TaskExecutionDetails> list();

    List<TaskExecutionDetails> list(Status status);

    void cancel(TaskId id);

    TaskExecutionDetails await(TaskId id);
}
