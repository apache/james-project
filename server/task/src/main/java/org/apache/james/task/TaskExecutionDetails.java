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

import java.time.ZonedDateTime;
import java.util.Optional;

public class TaskExecutionDetails {

    public interface AdditionalInformation {

    }

    public static TaskExecutionDetails from(Task task, TaskId id) {
        return new TaskExecutionDetails(
            id,
            task,
            TaskManager.Status.WAITING,
            Optional.of(ZonedDateTime.now()),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    }

    private final TaskId taskId;
    private final Task task;
    private final TaskManager.Status status;
    private final Optional<ZonedDateTime> submitDate;
    private final Optional<ZonedDateTime> startedDate;
    private final Optional<ZonedDateTime> completedDate;
    private final Optional<ZonedDateTime> canceledDate;
    private final Optional<ZonedDateTime> failedDate;

    public TaskExecutionDetails(TaskId taskId, Task task, TaskManager.Status status,
                                Optional<ZonedDateTime> submitDate, Optional<ZonedDateTime> startedDate,
                                Optional<ZonedDateTime> completedDate, Optional<ZonedDateTime> canceledDate,
                                Optional<ZonedDateTime> failedDate) {
        this.taskId = taskId;
        this.task = task;
        this.status = status;
        this.submitDate = submitDate;
        this.startedDate = startedDate;
        this.completedDate = completedDate;
        this.canceledDate = canceledDate;
        this.failedDate = failedDate;
    }

    public TaskId getTaskId() {
        return taskId;
    }

    public String getType() {
        return task.type();
    }

    public TaskManager.Status getStatus() {
        return status;
    }

    public Optional<AdditionalInformation> getAdditionalInformation() {
        return task.details();
    }

    public Optional<ZonedDateTime> getSubmitDate() {
        return submitDate;
    }

    public Optional<ZonedDateTime> getStartedDate() {
        return startedDate;
    }

    public Optional<ZonedDateTime> getCompletedDate() {
        return completedDate;
    }

    public Optional<ZonedDateTime> getCanceledDate() {
        return canceledDate;
    }

    public Optional<ZonedDateTime> getFailedDate() {
        return failedDate;
    }

    public TaskExecutionDetails start() {
        switch (status) {
            case WAITING:
                return new TaskExecutionDetails(
                    taskId,
                    task,
                    TaskManager.Status.IN_PROGRESS,
                    submitDate,
                    Optional.of(ZonedDateTime.now()),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty());
            default:
                return this;
        }
    }

    public TaskExecutionDetails completed() {
        switch (status) {
            case IN_PROGRESS:
            case CANCEL_REQUESTED:
            case WAITING:
                return new TaskExecutionDetails(
                    taskId,
                    task,
                    TaskManager.Status.COMPLETED,
                    submitDate,
                    startedDate,
                    Optional.of(ZonedDateTime.now()),
                    Optional.empty(),
                    Optional.empty());
            default:
                return this;
        }
    }

    public TaskExecutionDetails failed() {
        switch (status) {
            case IN_PROGRESS:
            case CANCEL_REQUESTED:
                return new TaskExecutionDetails(
                    taskId,
                    task,
                    TaskManager.Status.FAILED,
                    submitDate,
                    startedDate,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(ZonedDateTime.now()));
            default:
                return this;
        }
    }

    public TaskExecutionDetails cancelRequested() {
        switch (status) {
            case IN_PROGRESS:
            case WAITING:
                return new TaskExecutionDetails(
                    taskId,
                    task,
                    TaskManager.Status.CANCEL_REQUESTED,
                    submitDate,
                    startedDate,
                    Optional.empty(),
                    Optional.of(ZonedDateTime.now()),
                    Optional.empty());
            default:
                return this;
        }
    }

    public TaskExecutionDetails cancelEffectively() {
        switch (status) {
            case CANCEL_REQUESTED:
            case IN_PROGRESS:
            case WAITING:
                return new TaskExecutionDetails(
                    taskId,
                    task,
                    TaskManager.Status.CANCELLED,
                    submitDate,
                    startedDate,
                    Optional.empty(),
                    Optional.of(ZonedDateTime.now()),
                    Optional.empty());
            default:
                return this;
        }
    }
}
