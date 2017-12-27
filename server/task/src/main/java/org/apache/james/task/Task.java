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
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface Task {
    Logger LOGGER = LoggerFactory.getLogger(Task.class);

    interface Operation {
        void run();
    }

    enum Result {
        COMPLETED,
        PARTIAL;

        public Result onComplete(Operation... operation) {
            try {
                if (this == COMPLETED) {
                    run(operation);
                }
                return this;
            } catch (Exception e) {
                LOGGER.error("Error while executing operation", e);
                return PARTIAL;
            }
        }

        public Result onFailure(Operation... operation) {
            if (this == PARTIAL) {
                run(operation);
            }
            return this;
        }

        private void run(Operation... operation) {
            Arrays.stream(operation)
                .forEach(Operation::run);
        }
    }

    static Result combine(Result result1, Result result2) {
        if (result1 == Result.COMPLETED
            && result2 == Result.COMPLETED) {
            return Result.COMPLETED;
        }
        return Result.PARTIAL;
    }

    /**
     * Runs the migration
     *
     * @return Return true if fully migrated. Returns false otherwise.
     */
    Result run();


    default String type() {
        return UNKNOWN;
    }

    default Optional<Object> details() {
        return Optional.empty();
    }

    String TASK_ID = "taskId";
    String TASK_TYPE = "taskType";
    String TASK_DETAILS = "taskDetails";

    String UNKNOWN = "unknown";
}
