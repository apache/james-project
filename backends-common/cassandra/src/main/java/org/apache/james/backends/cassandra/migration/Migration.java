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

package org.apache.james.backends.cassandra.migration;

import org.apache.james.task.Task;
import org.apache.james.task.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface Migration {
    Logger LOGGER = LoggerFactory.getLogger(Migration.class);

    void apply() throws InterruptedException;

    default Task asTask() {
        return new Task() {
            @Override
            public Result run() throws InterruptedException {
                return runTask();
            }

            @Override
            public TaskType type() {
                return TaskType.of("migration-sub-task");
            }
        };
    }

    default Task.Result runTask() throws InterruptedException {
        try {
            this.apply();
            return Task.Result.COMPLETED;
        } catch (RuntimeException e) {
            LOGGER.warn("Error running migration", e);
            return Task.Result.PARTIAL;
        }
    }

}
