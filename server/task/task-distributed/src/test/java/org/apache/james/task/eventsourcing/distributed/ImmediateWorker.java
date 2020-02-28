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
package org.apache.james.task.eventsourcing.distributed;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManagerWorker;
import org.apache.james.task.TaskWithId;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class ImmediateWorker implements TaskManagerWorker {

    ConcurrentLinkedQueue<TaskWithId> tasks = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Task.Result> results = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<TaskId> failedTasks = new ConcurrentLinkedQueue<>();

    @Override
    public Mono<Task.Result> executeTask(TaskWithId taskWithId) {
        tasks.add(taskWithId);
        return Mono.fromCallable(() -> taskWithId.getTask().run())
            .doOnNext(result -> results.add(result))
            .subscribeOn(Schedulers.elastic());
    }

    @Override
    public void cancelTask(TaskId taskId) {
    }

    @Override
    public void fail(TaskId taskId, Optional<TaskExecutionDetails.AdditionalInformation> additionalInformation, String errorMessage, Throwable reason) {
        failedTasks.add(taskId);
    }

    @Override
    public void close() throws IOException {
    }
}
