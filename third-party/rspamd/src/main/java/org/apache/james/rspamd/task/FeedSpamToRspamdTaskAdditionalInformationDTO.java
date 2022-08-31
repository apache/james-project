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

public class FeedSpamToRspamdTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    public static final AdditionalInformationDTOModule<FeedSpamToRspamdTask.AdditionalInformation, FeedSpamToRspamdTaskAdditionalInformationDTO> SERIALIZATION_MODULE =
        DTOModule.forDomainObject(FeedSpamToRspamdTask.AdditionalInformation.class)
            .convertToDTO(FeedSpamToRspamdTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(FeedSpamToRspamdTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(FeedSpamToRspamdTaskAdditionalInformationDTO::toDto)
            .typeName(FeedSpamToRspamdTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private static FeedSpamToRspamdTask.AdditionalInformation toDomainObject(FeedSpamToRspamdTaskAdditionalInformationDTO dto) {
        return new FeedSpamToRspamdTask.AdditionalInformation(
            dto.timestamp,
            dto.spamMessageCount,
            dto.reportedSpamMessageCount,
            dto.errorCount,
            dto.runningOptions);
    }

    private static FeedSpamToRspamdTaskAdditionalInformationDTO toDto(FeedSpamToRspamdTask.AdditionalInformation domainObject, String type) {
        return new FeedSpamToRspamdTaskAdditionalInformationDTO(
            type,
            domainObject.timestamp(),
            domainObject.getSpamMessageCount(),
            domainObject.getReportedSpamMessageCount(),
            domainObject.getErrorCount(),
            domainObject.getRunningOptions());
    }

    private final String type;
    private final Instant timestamp;
    private final long spamMessageCount;
    private final long reportedSpamMessageCount;
    private final long errorCount;
    private final RunningOptions runningOptions;

    public FeedSpamToRspamdTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                        @JsonProperty("timestamp") Instant timestamp,
                                                        @JsonProperty("spamMessageCount") long spamMessageCount,
                                                        @JsonProperty("reportedSpamMessageCount") long reportedSpamMessageCount,
                                                        @JsonProperty("errorCount") long errorCount,
                                                        @JsonProperty("runningOptions") RunningOptions runningOptions) {
        this.type = type;
        this.timestamp = timestamp;
        this.spamMessageCount = spamMessageCount;
        this.reportedSpamMessageCount = reportedSpamMessageCount;
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

    public long getSpamMessageCount() {
        return spamMessageCount;
    }

    public long getReportedSpamMessageCount() {
        return reportedSpamMessageCount;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public RunningOptions getRunningOptions() {
        return runningOptions;
    }
}
