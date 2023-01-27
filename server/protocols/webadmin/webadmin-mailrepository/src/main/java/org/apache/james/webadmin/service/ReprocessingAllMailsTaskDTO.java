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

import java.util.Optional;

import org.apache.james.json.DTOModule;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.util.streams.Limit;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReprocessingAllMailsTaskDTO implements TaskDTO {

    public static TaskDTOModule<ReprocessingAllMailsTask, ReprocessingAllMailsTaskDTO> module(ReprocessingService reprocessingService) {
        return DTOModule
            .forDomainObject(ReprocessingAllMailsTask.class)
            .convertToDTO(ReprocessingAllMailsTaskDTO.class)
            .toDomainObjectConverter(dto -> dto.fromDTO(reprocessingService))
            .toDTOConverter(ReprocessingAllMailsTaskDTO::toDTO)
            .typeName(ReprocessingAllMailsTask.TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    private static ReprocessingAllMailsTaskDTO toDTO(ReprocessingAllMailsTask domainObject, String typeName) {
        try {
            return new ReprocessingAllMailsTaskDTO(
                typeName,
                domainObject.getRepositorySize(),
                domainObject.getRepositoryPath().urlEncoded(),
                domainObject.getConfiguration().getMailQueueName().asString(),
                Optional.of(domainObject.getConfiguration().isConsume()),
                domainObject.getConfiguration().getTargetProcessor(),
                domainObject.getConfiguration().getLimit().getLimit(),
                domainObject.getConfiguration().getMaxRetries());
        } catch (Exception e) {
            throw new ReprocessingAllMailsTask.UrlEncodingFailureSerializationException(domainObject.getRepositoryPath());
        }
    }

    private final String type;
    private final long repositorySize;
    private final String repositoryPath;
    private final String targetQueue;
    private final boolean consume;
    private final Optional<String> targetProcessor;
    private final Optional<Integer> limit;
    private final Optional<Integer> maxRetries;

    public ReprocessingAllMailsTaskDTO(@JsonProperty("type") String type,
                                       @JsonProperty("repositorySize") long repositorySize,
                                       @JsonProperty("repositoryPath") String repositoryPath,
                                       @JsonProperty("targetQueue") String targetQueue,
                                       @JsonProperty("consume") Optional<Boolean> consume,
                                       @JsonProperty("targetProcessor") Optional<String> targetProcessor,
                                       @JsonProperty("limit") Optional<Integer> limit,
                                       @JsonProperty("maxRetries") Optional<Integer> maxRetries) {
        this.type = type;
        this.repositorySize = repositorySize;
        this.repositoryPath = repositoryPath;
        this.targetQueue = targetQueue;
        this.consume = consume.orElse(true);
        this.targetProcessor = targetProcessor;
        this.limit = limit;
        this.maxRetries = maxRetries;
    }

    private ReprocessingAllMailsTask fromDTO(ReprocessingService reprocessingService) {
        try {
            return new ReprocessingAllMailsTask(
                reprocessingService,
                repositorySize,
                MailRepositoryPath.fromEncoded(repositoryPath),
                new ReprocessingService.Configuration(
                    MailQueueName.of(targetQueue),
                    targetProcessor,
                    maxRetries,
                    consume,
                    Limit.from(limit)));
        } catch (Exception e) {
            throw new ReprocessingAllMailsTask.InvalidMailRepositoryPathDeserializationException(repositoryPath);
        }
    }

    @Override
    public String getType() {
        return type;
    }

    public Optional<Integer> getMaxRetries() {
        return maxRetries;
    }

    public long getRepositorySize() {
        return repositorySize;
    }

    public String getRepositoryPath() {
        return repositoryPath;
    }

    public String getTargetQueue() {
        return targetQueue;
    }

    public boolean isConsume() {
        return consume;
    }

    public Optional<String> getTargetProcessor() {
        return targetProcessor;
    }

    public Optional<Integer> getLimit() {
        return limit;
    }
}
