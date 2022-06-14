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

import java.io.Closeable;
import java.util.Optional;

import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

public interface TaskManagerWorker extends Closeable {

    interface Listener {
        Publisher<Void> started(TaskId taskId);

        Publisher<Void> completed(TaskId taskId, Task.Result result, Publisher<Optional<TaskExecutionDetails.AdditionalInformation>> additionalInformationPublisher);

        Publisher<Void> failed(TaskId taskId, Publisher<Optional<TaskExecutionDetails.AdditionalInformation>> additionalInformationPublisher, Optional<String> errorMessage, Optional<Throwable> t);

        default Publisher<Void> failed(TaskId taskId, Publisher<Optional<TaskExecutionDetails.AdditionalInformation>> additionalInformationPublisher, Throwable t) {
            return failed(taskId, additionalInformationPublisher, Optional.empty(), Optional.of(t));
        }

        default Publisher<Void> failed(TaskId taskId, Publisher<Optional<TaskExecutionDetails.AdditionalInformation>> additionalInformationPublisher) {
            return failed(taskId, additionalInformationPublisher, Optional.empty(), Optional.empty());
        }

        Publisher<Void> cancelled(TaskId taskId, Publisher<Optional<TaskExecutionDetails.AdditionalInformation>> additionalInformationPublisher);

        Publisher<Void> updated(TaskId taskId, Publisher<TaskExecutionDetails.AdditionalInformation> additionalInformationPublisher);
    }

    Mono<Task.Result> executeTask(TaskWithId taskWithId);

    void cancelTask(TaskId taskId);

    Publisher<Void> fail(TaskId taskId, Publisher<Optional<TaskExecutionDetails.AdditionalInformation>> additionalInformationPublisher, String errorMessage, Throwable reason);

}
