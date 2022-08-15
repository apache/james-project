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

package org.apache.james.rspamd.task;

import java.time.Instant;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FeedHamToRspamdTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    public static final AdditionalInformationDTOModule<FeedHamToRspamdTask.AdditionalInformation, FeedHamToRspamdTaskAdditionalInformationDTO> SERIALIZATION_MODULE =
        DTOModule.forDomainObject(FeedHamToRspamdTask.AdditionalInformation.class)
            .convertToDTO(FeedHamToRspamdTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(FeedHamToRspamdTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(FeedHamToRspamdTaskAdditionalInformationDTO::toDto)
            .typeName(FeedHamToRspamdTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private static FeedHamToRspamdTask.AdditionalInformation toDomainObject(FeedHamToRspamdTaskAdditionalInformationDTO dto) {
        return new FeedHamToRspamdTask.AdditionalInformation(
            dto.timestamp,
            dto.hamMessageCount,
            dto.reportedHamMessageCount,
            dto.errorCount,
            dto.runningOptions.getMessagesPerSecond(),
            dto.runningOptions.getPeriodInSecond(),
            dto.runningOptions.getSamplingProbability());
    }

    private static FeedHamToRspamdTaskAdditionalInformationDTO toDto(FeedHamToRspamdTask.AdditionalInformation domainObject, String type) {
        return new FeedHamToRspamdTaskAdditionalInformationDTO(
            type,
            domainObject.timestamp(),
            domainObject.getHamMessageCount(),
            domainObject.getReportedHamMessageCount(),
            domainObject.getErrorCount(),
            new FeedHamToRspamdTask.RunningOptions(domainObject.getPeriod(), domainObject.getMessagesPerSecond(), domainObject.getSamplingProbability()));
    }

    private final String type;
    private final Instant timestamp;
    private final long hamMessageCount;
    private final long reportedHamMessageCount;
    private final long errorCount;
    private final FeedHamToRspamdTask.RunningOptions runningOptions;

    public FeedHamToRspamdTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                       @JsonProperty("timestamp") Instant timestamp,
                                                       @JsonProperty("hamMessageCount") long hamMessageCount,
                                                       @JsonProperty("reportedHamMessageCount") long reportedHamMessageCount,
                                                       @JsonProperty("errorCount") long errorCount,
                                                       @JsonProperty("runningOptions") FeedHamToRspamdTask.RunningOptions runningOptions) {
        this.type = type;
        this.timestamp = timestamp;
        this.hamMessageCount = hamMessageCount;
        this.reportedHamMessageCount = reportedHamMessageCount;
        this.errorCount = errorCount;
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

    public long getHamMessageCount() {
        return hamMessageCount;
    }

    public long getReportedHamMessageCount() {
        return reportedHamMessageCount;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public FeedHamToRspamdTask.RunningOptions getRunningOptions() {
        return runningOptions;
    }
}
