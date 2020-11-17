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

package org.apache.james.webadmin.data.jmap;

import java.time.Instant;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RecomputeUserFastViewTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    public static AdditionalInformationDTOModule<RecomputeUserFastViewProjectionItemsTask.AdditionalInformation, RecomputeUserFastViewTaskAdditionalInformationDTO> module() {
        return DTOModule.forDomainObject(RecomputeUserFastViewProjectionItemsTask.AdditionalInformation.class)
            .convertToDTO(RecomputeUserFastViewTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(RecomputeUserFastViewTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(RecomputeUserFastViewTaskAdditionalInformationDTO::toDTO)
            .typeName(RecomputeUserFastViewProjectionItemsTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private static RecomputeUserFastViewTaskAdditionalInformationDTO toDTO(RecomputeUserFastViewProjectionItemsTask.AdditionalInformation details, String type) {
        return new RecomputeUserFastViewTaskAdditionalInformationDTO(
            type,
            details.timestamp(),
            details.getUsername(),
            details.getProcessedMessageCount(),
            details.getFailedMessageCount(),
            Optional.of(RunningOptionsDTO.asDTO(details.getRunningOptions())));
    }

    private static RecomputeUserFastViewProjectionItemsTask.AdditionalInformation toDomainObject(RecomputeUserFastViewTaskAdditionalInformationDTO dto) {
        return new RecomputeUserFastViewProjectionItemsTask.AdditionalInformation(
            dto.getRunningOptions()
                .map(RunningOptionsDTO::asDomainObject)
                .orElse(RunningOptions.DEFAULT),
            Username.of(dto.username),
            dto.getProcessedMessageCount(),
            dto.getFailedMessageCount(),
            dto.timestamp);
    }

    private final String type;
    private final Instant timestamp;
    private final String username;
    private final long processedMessageCount;
    private final long failedMessageCount;
    private final Optional<RunningOptionsDTO> runningOptions;

    private RecomputeUserFastViewTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                              @JsonProperty("timestamp") Instant timestamp,
                                                              @JsonProperty("username") String username,
                                                              @JsonProperty("processedMessageCount") long processedMessageCount,
                                                              @JsonProperty("failedMessageCount") long failedMessageCount,
                                                              @JsonProperty("runningOptions") Optional<RunningOptionsDTO> runningOptionsDTO) {
        this.type = type;
        this.timestamp = timestamp;
        this.username = username;
        this.processedMessageCount = processedMessageCount;
        this.failedMessageCount = failedMessageCount;
        this.runningOptions = runningOptionsDTO;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    public long getProcessedMessageCount() {
        return processedMessageCount;
    }

    public long getFailedMessageCount() {
        return failedMessageCount;
    }

    public Optional<RunningOptionsDTO> getRunningOptions() {
        return runningOptions;
    }

    public String getUsername() {
        return username;
    }
}
