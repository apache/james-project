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

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;

public class PopulateFilteringProjectionTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    public static AdditionalInformationDTOModule<PopulateFilteringProjectionTask.AdditionalInformation, PopulateFilteringProjectionTaskAdditionalInformationDTO> module() {
        return DTOModule.forDomainObject(PopulateFilteringProjectionTask.AdditionalInformation.class)
            .convertToDTO(PopulateFilteringProjectionTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(PopulateFilteringProjectionTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(PopulateFilteringProjectionTaskAdditionalInformationDTO::toDTO)
            .typeName(PopulateFilteringProjectionTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private static PopulateFilteringProjectionTask.AdditionalInformation toDomainObject(PopulateFilteringProjectionTaskAdditionalInformationDTO dto) {
        return new PopulateFilteringProjectionTask.AdditionalInformation(
            dto.getProcessedUserCount(),
            dto.getFailedUserCount(),
            dto.timestamp);
    }

    private static PopulateFilteringProjectionTaskAdditionalInformationDTO toDTO(PopulateFilteringProjectionTask.AdditionalInformation details, String type) {
        return new PopulateFilteringProjectionTaskAdditionalInformationDTO(
            type,
            details.timestamp(),
            details.getProcessedUserCount(),
            details.getFailedUserCount());
    }

    private final String type;
    private final Instant timestamp;
    private final long processedUserCount;
    private final long failedUserCount;

    @VisibleForTesting
    PopulateFilteringProjectionTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                            @JsonProperty("timestamp") Instant timestamp,
                                                            @JsonProperty("processedUserCount") long processedUserCount,
                                                            @JsonProperty("failedUserCount") long failedUserCount) {
        this.type = type;
        this.timestamp = timestamp;
        this.processedUserCount = processedUserCount;
        this.failedUserCount = failedUserCount;
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

    public long getFailedUserCount() {
        return failedUserCount;
    }
}
