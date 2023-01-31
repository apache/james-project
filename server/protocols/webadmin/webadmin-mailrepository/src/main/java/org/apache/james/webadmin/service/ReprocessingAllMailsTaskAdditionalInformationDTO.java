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
package org.apache.james.webadmin.service;

import java.time.Instant;
import java.util.Optional;

import org.apache.james.json.DTOModule;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.util.streams.Limit;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReprocessingAllMailsTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    public static AdditionalInformationDTOModule<ReprocessingAllMailsTask.AdditionalInformation, ReprocessingAllMailsTaskAdditionalInformationDTO> module() {
        return DTOModule.forDomainObject(ReprocessingAllMailsTask.AdditionalInformation.class)
            .convertToDTO(ReprocessingAllMailsTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(dto -> new ReprocessingAllMailsTask.AdditionalInformation(
                MailRepositoryPath.from(dto.repositoryPath),
                new ReprocessingService.Configuration(
                    MailQueueName.of(dto.getTargetQueue()),
                    dto.getTargetProcessor(),
                    dto.getMaxRetries(),
                    dto.isConsume(),
                    Limit.from(dto.getLimit())),
                dto.initialCount,
                dto.remainingCount,
                dto.timestamp))
            .toDTOConverter((details, type) -> new ReprocessingAllMailsTaskAdditionalInformationDTO(
                type,
                details.getRepositoryPath(),
                details.getConfiguration().getMailQueueName().asString(),
                details.getConfiguration().getTargetProcessor(),
                Optional.of(details.getConfiguration().isConsume()),
                details.getInitialCount(),
                details.getRemainingCount(),
                details.timestamp(),
                details.getConfiguration().getLimit().getLimit(),
                details.getConfiguration().getMaxRetries()))
            .typeName(ReprocessingAllMailsTask.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private final String type;
    private final String repositoryPath;
    private final String targetQueue;
    private final Optional<String> targetProcessor;
    private final boolean consume;
    private final long initialCount;
    private final long remainingCount;
    private final Instant timestamp;
    private final Optional<Integer> limit;
    private final Optional<Integer> maxRetries;

    public ReprocessingAllMailsTaskAdditionalInformationDTO(
        @JsonProperty("type") String type,
        @JsonProperty("repositoryPath") String repositoryPath,
        @JsonProperty("targetQueue") String targetQueue,
        @JsonProperty("targetProcessor") Optional<String> targetProcessor,
        @JsonProperty("consume") Optional<Boolean> consume,
        @JsonProperty("initialCount") long initialCount,
        @JsonProperty("remainingCount") long remainingCount,
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("limit") Optional<Integer> limit,
        @JsonProperty("maxRetries") Optional<Integer> maxRetries) {
        this.type = type;
        this.repositoryPath = repositoryPath;
        this.targetQueue = targetQueue;
        this.targetProcessor = targetProcessor;
        this.initialCount = initialCount;
        this.remainingCount = remainingCount;
        this.timestamp = timestamp;
        this.consume = consume.orElse(true);
        this.limit = limit;
        this.maxRetries = maxRetries;
    }

    public boolean isConsume() {
        return consume;
    }

    @Override
    public String getType() {
        return type;
    }

    public Optional<Integer> getMaxRetries() {
        return maxRetries;
    }

    public long getInitialCount() {
        return initialCount;
    }

    public long getRemainingCount() {
        return remainingCount;
    }

    public String getRepositoryPath() {
        return repositoryPath;
    }

    public String getTargetQueue() {
        return targetQueue;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Optional<String> getTargetProcessor() {
        return targetProcessor;
    }

    public Optional<Integer> getLimit() {
        return limit;
    }
}
