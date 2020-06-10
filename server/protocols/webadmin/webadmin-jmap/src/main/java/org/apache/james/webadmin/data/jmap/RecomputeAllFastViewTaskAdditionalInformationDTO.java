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

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.webadmin.data.jmap.MessageFastViewProjectionCorrector.RunningOptions;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;

public class RecomputeAllFastViewTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    public static AdditionalInformationDTOModule<RecomputeAllFastViewProjectionItemsTask.AdditionalInformation, RecomputeAllFastViewTaskAdditionalInformationDTO> module() {
        return DTOModule.forDomainObject(RecomputeAllFastViewProjectionItemsTask.AdditionalInformation.class)
            .convertToDTO(RecomputeAllFastViewTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(RecomputeAllFastViewTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(RecomputeAllFastViewTaskAdditionalInformationDTO::toDTO)
            .typeName(RecomputeAllFastViewProjectionItemsTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private static RecomputeAllFastViewProjectionItemsTask.AdditionalInformation toDomainObject(RecomputeAllFastViewTaskAdditionalInformationDTO dto) {
        return new RecomputeAllFastViewProjectionItemsTask.AdditionalInformation(
            dto.getRunningOptions()
                .map(RunningOptionsDTO::asDomainObject)
                .orElse(RunningOptions.DEFAULT),
            dto.getProcessedUserCount(),
            dto.getProcessedMessageCount(),
            dto.getFailedUserCount(),
            dto.getFailedMessageCount(),
            dto.timestamp);
    }

    private static RecomputeAllFastViewTaskAdditionalInformationDTO toDTO(RecomputeAllFastViewProjectionItemsTask.AdditionalInformation details, String type) {
        return new RecomputeAllFastViewTaskAdditionalInformationDTO(
            type,
            details.timestamp(),
            details.getProcessedUserCount(),
            details.getProcessedMessageCount(),
            details.getFailedUserCount(),
            details.getFailedMessageCount(),
            Optional.of(RunningOptionsDTO.asDTO(details.getRunningOptions())));
    }

    private final String type;
    private final Instant timestamp;
    private final long processedUserCount;
    private final long processedMessageCount;
    private final long failedUserCount;
    private final long failedMessageCount;
    private final Optional<RunningOptionsDTO> runningOptions;

    @VisibleForTesting
    RecomputeAllFastViewTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                     @JsonProperty("timestamp") Instant timestamp,
                                                     @JsonProperty("processedUserCount") long processedUserCount,
                                                     @JsonProperty("processedMessageCount") long processedMessageCount,
                                                     @JsonProperty("failedUserCount") long failedUserCount,
                                                     @JsonProperty("failedMessageCount") long failedMessageCount,
                                                     @JsonProperty("runningOptions") Optional<RunningOptionsDTO> runningOptions) {
        this.type = type;
        this.timestamp = timestamp;
        this.processedUserCount = processedUserCount;
        this.processedMessageCount = processedMessageCount;
        this.failedUserCount = failedUserCount;
        this.failedMessageCount = failedMessageCount;
        this.runningOptions = runningOptions;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    public long getProcessedUserCount() {
        return processedUserCount;
    }

    public long getProcessedMessageCount() {
        return processedMessageCount;
    }

    public long getFailedUserCount() {
        return failedUserCount;
    }

    public long getFailedMessageCount() {
        return failedMessageCount;
    }

    public Optional<RunningOptionsDTO> getRunningOptions() {
        return runningOptions;
    }
}
