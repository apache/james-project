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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;

// The task used to walk users: details stored by previous versions carry user counters
@JsonIgnoreProperties({"processedUserCount", "failedUserCount"})
public class PopulateEmailQueryViewTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    public static AdditionalInformationDTOModule<PopulateEmailQueryViewTask.AdditionalInformation, PopulateEmailQueryViewTaskAdditionalInformationDTO> module() {
        return DTOModule.forDomainObject(PopulateEmailQueryViewTask.AdditionalInformation.class)
            .convertToDTO(PopulateEmailQueryViewTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(PopulateEmailQueryViewTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(PopulateEmailQueryViewTaskAdditionalInformationDTO::toDTO)
            .typeName(PopulateEmailQueryViewTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private static PopulateEmailQueryViewTask.AdditionalInformation toDomainObject(PopulateEmailQueryViewTaskAdditionalInformationDTO dto) {
        return new PopulateEmailQueryViewTask.AdditionalInformation(
            dto.getRunningOptions()
                .map(RunningOptionsDTO::asDomainObject)
                .orElse(RunningOptions.DEFAULT),
            dto.getProcessedMailboxCount(),
            dto.getProcessedMessageCount(),
            dto.getFailedMailboxCount(),
            dto.getFailedMessageCount(),
            dto.timestamp);
    }

    private static PopulateEmailQueryViewTaskAdditionalInformationDTO toDTO(PopulateEmailQueryViewTask.AdditionalInformation details, String type) {
        return new PopulateEmailQueryViewTaskAdditionalInformationDTO(
            type,
            details.timestamp(),
            details.getProcessedMailboxCount(),
            details.getProcessedMessageCount(),
            details.getFailedMailboxCount(),
            details.getFailedMessageCount(),
            Optional.of(RunningOptionsDTO.asDTO(details.getRunningOptions())));
    }

    private final String type;
    private final Instant timestamp;
    private final long processedMailboxCount;
    private final long processedMessageCount;
    private final long failedMailboxCount;
    private final long failedMessageCount;
    private final Optional<RunningOptionsDTO> runningOptions;

    @VisibleForTesting
    PopulateEmailQueryViewTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                       @JsonProperty("timestamp") Instant timestamp,
                                                       @JsonProperty("processedMailboxCount") long processedMailboxCount,
                                                       @JsonProperty("processedMessageCount") long processedMessageCount,
                                                       @JsonProperty("failedMailboxCount") long failedMailboxCount,
                                                       @JsonProperty("failedMessageCount") long failedMessageCount,
                                                       @JsonProperty("runningOptions") Optional<RunningOptionsDTO> runningOptions) {
        this.type = type;
        this.timestamp = timestamp;
        this.processedMailboxCount = processedMailboxCount;
        this.processedMessageCount = processedMessageCount;
        this.failedMailboxCount = failedMailboxCount;
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

    public long getProcessedMailboxCount() {
        return processedMailboxCount;
    }

    public long getProcessedMessageCount() {
        return processedMessageCount;
    }

    public long getFailedMailboxCount() {
        return failedMailboxCount;
    }

    public long getFailedMessageCount() {
        return failedMessageCount;
    }

    public Optional<RunningOptionsDTO> getRunningOptions() {
        return runningOptions;
    }
}
