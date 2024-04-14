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

package org.apache.james.webadmin.service;

import jakarta.inject.Inject;

import org.apache.james.mailbox.indexer.IndexingDetailInformation;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.task.TaskNotFoundException;
import org.apache.james.task.TaskType;

public class PreviousReIndexingService {
    public static class TaskNotYetFinishedException extends RuntimeException {
        TaskNotYetFinishedException(TaskManager.Status currentStatus) {
            super("Task is not yet finished. Current status is: " + currentStatus);
        }
    }

    public static class NotAnIndexingRetriableTask extends RuntimeException {
        NotAnIndexingRetriableTask(TaskType type) {
            super("'" + type.asString() + "' is not a valid type of task for retrying a failed indexing");
        }
    }

    private final TaskManager taskManager;

    @Inject
    public PreviousReIndexingService(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public IndexingDetailInformation retrieveIndexingExecutionDetails(TaskId taskId) throws NotAnIndexingRetriableTask, TaskNotFoundException, TaskNotYetFinishedException {
        TaskExecutionDetails executionDetails = taskManager.getExecutionDetails(taskId);
        if (!executionDetails.getStatus().isFinished()) {
            throw new TaskNotYetFinishedException(executionDetails.getStatus());
        }
        return executionDetails.getAdditionalInformation()
            .filter(IndexingDetailInformation.class::isInstance)
            .map(IndexingDetailInformation.class::cast)
            .orElseThrow(() -> new NotAnIndexingRetriableTask(executionDetails.getType()));
    }
}
