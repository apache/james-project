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

public class FeedHamToRSpamDTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    public static final AdditionalInformationDTOModule<FeedHamToRSpamDTask.AdditionalInformation, FeedHamToRSpamDTaskAdditionalInformationDTO> SERIALIZATION_MODULE =
        DTOModule.forDomainObject(FeedHamToRSpamDTask.AdditionalInformation.class)
            .convertToDTO(FeedHamToRSpamDTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(FeedHamToRSpamDTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(FeedHamToRSpamDTaskAdditionalInformationDTO::toDto)
            .typeName(FeedHamToRSpamDTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private static FeedHamToRSpamDTask.AdditionalInformation toDomainObject(FeedHamToRSpamDTaskAdditionalInformationDTO dto) {
        return new FeedHamToRSpamDTask.AdditionalInformation(
            dto.timestamp,
            dto.hamMessageCount,
            dto.reportedHamMessageCount,
            dto.errorCount,
            dto.runningOptions.getMessagesPerSecond(),
            dto.runningOptions.getPeriodInSecond(),
            dto.runningOptions.getSamplingProbability());
    }

    private static FeedHamToRSpamDTaskAdditionalInformationDTO toDto(FeedHamToRSpamDTask.AdditionalInformation domainObject, String type) {
        return new FeedHamToRSpamDTaskAdditionalInformationDTO(
            type,
            domainObject.timestamp(),
            domainObject.getHamMessageCount(),
            domainObject.getReportedHamMessageCount(),
            domainObject.getErrorCount(),
            new FeedHamToRSpamDTask.RunningOptions(domainObject.getPeriod(), domainObject.getMessagesPerSecond(), domainObject.getSamplingProbability()));
    }

    private final String type;
    private final Instant timestamp;
    private final long hamMessageCount;
    private final long reportedHamMessageCount;
    private final long errorCount;
    private final FeedHamToRSpamDTask.RunningOptions runningOptions;

    public FeedHamToRSpamDTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                       @JsonProperty("timestamp") Instant timestamp,
                                                       @JsonProperty("hamMessageCount") long hamMessageCount,
                                                       @JsonProperty("reportedHamMessageCount") long reportedHamMessageCount,
                                                       @JsonProperty("errorCount") long errorCount,
                                                       @JsonProperty("runningOptions") FeedHamToRSpamDTask.RunningOptions runningOptions) {
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

    public FeedHamToRSpamDTask.RunningOptions getRunningOptions() {
        return runningOptions;
    }
}
