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

package org.apache.james.webadmin.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.james.task.TaskExecutionDetails;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.steveash.guavate.Guavate;

public class ExecutionDetailsDto {
    public static List<ExecutionDetailsDto> from(List<TaskExecutionDetails> tasksDetails) {
        return tasksDetails.stream()
            .map(ExecutionDetailsDto::new)
            .collect(Guavate.toImmutableList());
    }

    public static ExecutionDetailsDto from(TaskExecutionDetails taskDetails) {
        return new ExecutionDetailsDto(taskDetails);
    }

    private final TaskExecutionDetails executionDetails;

    private ExecutionDetailsDto(TaskExecutionDetails executionDetails) {
        this.executionDetails = executionDetails;
    }

    public UUID getTaskId() {
        return executionDetails.getTaskId().getValue();
    }

    public String getType() {
        return executionDetails.getType();
    }

    public String getStatus() {
        return executionDetails.getStatus().getValue();
    }

    public Optional<TaskExecutionDetails.AdditionalInformation> getAdditionalInformation() {
        return executionDetails.getAdditionalInformation();
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    public Optional<ZonedDateTime> getSubmitDate() {
        return executionDetails.getSubmitDate();
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    public Optional<ZonedDateTime> getStartedDate() {
        return executionDetails.getStartedDate();
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    public Optional<ZonedDateTime> getCompletedDate() {
        return executionDetails.getCompletedDate();
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    public Optional<ZonedDateTime> getCanceledDate() {
        return executionDetails.getCanceledDate();
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    public Optional<ZonedDateTime> getFailedDate() {
        return executionDetails.getFailedDate();
    }
}
