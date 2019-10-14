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

import java.util.Optional;

import org.junit.jupiter.api.function.ThrowingSupplier;

/**
 * This class is used for unit testing.
 * It enable to wrap a lambda to run as a Task.
 * It should be used with `MemoryReferenceTaskStore` which keeps a reference to the Task object and index it with its id.
 * On deserialization, we can then ask the store to retrieve the Task object and pretend we deserialized it.
 */
public class MemoryReferenceTask implements Task {
    public static final TaskType TYPE = TaskType.of("memory-reference-task");
    private final ThrowingSupplier<Result> task;

    public MemoryReferenceTask(ThrowingSupplier<Result> task) {
        this.task = task;
    }

    @Override
    public Result run() throws InterruptedException {
        try {
            return task.get();
        } catch (InterruptedException e) {
            throw e;
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    @Override
    public TaskType type() {
        return TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.empty();
    }
}
