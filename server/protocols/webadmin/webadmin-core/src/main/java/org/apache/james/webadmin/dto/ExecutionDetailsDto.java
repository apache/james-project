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

import org.apache.james.json.DTOConverter;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.task.Hostname;
import org.apache.james.task.TaskExecutionDetails;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.steveash.guavate.Guavate;

public class ExecutionDetailsDto {
    public static List<ExecutionDetailsDto> from(DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationConverter,
                                                 List<TaskExecutionDetails> tasksDetails) {
        return tasksDetails.stream()
            .map(details -> ExecutionDetailsDto.from(additionalInformationConverter, details))
            .collect(Guavate.toImmutableList());
    }

    public static ExecutionDetailsDto from(DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationConverter,
                                           TaskExecutionDetails taskDetails) {

        return new ExecutionDetailsDto(taskDetails,
            taskDetails.getAdditionalInformation()
                .flatMap(additionalInformationConverter::toDTO));
    }

    private final TaskExecutionDetails executionDetails;
    private final Optional<AdditionalInformationDTO> additionalInformation;

    private ExecutionDetailsDto(TaskExecutionDetails executionDetails,
                                Optional<AdditionalInformationDTO> additionalInformation) {
        this.executionDetails = executionDetails;
        this.additionalInformation = additionalInformation;
    }

    public UUID getTaskId() {
        return executionDetails.getTaskId().getValue();
    }

    public String getType() {
        return executionDetails.getType().asString();
    }

    public String getStatus() {
        return executionDetails.getStatus().getValue();
    }

    public String getSubmittedFrom() {
        return executionDetails.getSubmittedNode().asString();
    }

    public Optional<String> getExecutedOn() {
        return executionDetails.getRanNode()
            .map(Hostname::asString);
    }

    public Optional<String> getCancelledFrom() {
        return executionDetails.getCancelRequestedNode()
            .map(Hostname::asString);
    }

    public Optional<AdditionalInformationDTO> getAdditionalInformation() {
        return additionalInformation;
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    public ZonedDateTime getSubmitDate() {
        return executionDetails.getSubmittedDate();
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
