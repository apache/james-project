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

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.github.fge.lambdas.functions.ThrowingFunction;
import com.google.common.base.MoreObjects;

/**
 * This class is used for unit testing.
 * It enables to wrap a lambda to run as a Task.
 *
 * It should be used with `MemoryReferenceTaskStore` which keeps a reference to the Task object and index it with its id.
 * On deserialization, we can then ask the store to retrieve the Task object and pretend we deserialized it.
 *
 * This task is very similar to `MemoryReferenceTask` but it accepts an `AtomicLong` as parameter
 * which will be used for its additional information.
 *
 * This task enables to unit test the update/serialization of the additional information when using the task manager.
 */
public class MemoryReferenceWithCounterTask implements Task {
    public static final TaskType TYPE = TaskType.of("memory-reference-task-with-counter");

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final long count;
        private final Instant timestamp;

        public AdditionalInformation(long count, Instant timestamp) {
            this.count = count;
            this.timestamp = timestamp;
        }

        public long getCount() {
            return count;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }

        @Override
        public boolean equals(Object that) {
            if (that instanceof MemoryReferenceWithCounterTask.AdditionalInformation) {
                return Objects.equals(this.count, ((AdditionalInformation) that).getCount()) &&
                    Objects.equals(this.timestamp, ((AdditionalInformation) that).timestamp);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.count, this.timestamp);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("count", count)
                .add("timestamp", timestamp)
                .toString();
        }
    }

    private final ThrowingFunction<AtomicLong, Result> task;
    private final AtomicLong counter = new AtomicLong(0);

    public MemoryReferenceWithCounterTask(ThrowingFunction<AtomicLong, Result> task) {
        this.task = task;
    }

    @Override
    public Result run() {
        try {
            return task.apply(counter);
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
        return Optional.of(new MemoryReferenceWithCounterTask.AdditionalInformation(counter.get(), Clock.systemUTC().instant()));
    }
}
