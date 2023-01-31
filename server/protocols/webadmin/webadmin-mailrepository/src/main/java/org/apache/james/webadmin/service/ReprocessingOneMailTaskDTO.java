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

import java.time.Clock;
import java.util.Optional;

import org.apache.james.json.DTOModule;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.util.streams.Limit;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReprocessingOneMailTaskDTO implements TaskDTO {

    public static final Optional<Integer> NO_MAX_RETRIES = Optional.empty();

    public static TaskDTOModule<ReprocessingOneMailTask, ReprocessingOneMailTaskDTO> module(Clock clock, ReprocessingService reprocessingService) {
        return DTOModule
            .forDomainObject(ReprocessingOneMailTask.class)
            .convertToDTO(ReprocessingOneMailTaskDTO.class)
            .toDomainObjectConverter(dto -> dto.fromDTO(reprocessingService, clock))
            .toDTOConverter(ReprocessingOneMailTaskDTO::toDTO)
            .typeName(ReprocessingOneMailTask.TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    public static ReprocessingOneMailTaskDTO toDTO(ReprocessingOneMailTask domainObject, String typeName) {
        try {
            return new ReprocessingOneMailTaskDTO(
                typeName,
                domainObject.getRepositoryPath().urlEncoded(),
                domainObject.getConfiguration().getMailQueueName().asString(),
                domainObject.getMailKey().asString(),
                domainObject.getConfiguration().getTargetProcessor(),
                Optional.of(domainObject.getConfiguration().isConsume()));
        } catch (Exception e) {
            throw new ReprocessingOneMailTask.UrlEncodingFailureSerializationException(domainObject.getRepositoryPath());
        }
    }

    private final String type;
    private final String repositoryPath;
    private final String targetQueue;
    private final String mailKey;
    private final Optional<String> targetProcessor;
    private final boolean consume;

    public ReprocessingOneMailTaskDTO(@JsonProperty("type") String type,
                                      @JsonProperty("repositoryPath") String repositoryPath,
                                      @JsonProperty("targetQueue") String targetQueue,
                                      @JsonProperty("mailKey") String mailKey,
                                      @JsonProperty("targetProcessor") Optional<String> targetProcessor,
                                      @JsonProperty("boolean") Optional<Boolean> consume) {
        this.type = type;
        this.repositoryPath = repositoryPath;
        this.mailKey = mailKey;
        this.targetQueue = targetQueue;
        this.targetProcessor = targetProcessor;
        this.consume = consume.orElse(true);
    }

    public ReprocessingOneMailTask fromDTO(ReprocessingService reprocessingService, Clock clock) {
        return new ReprocessingOneMailTask(
            reprocessingService,
            getMailRepositoryPath(),
            new ReprocessingService.Configuration(
                MailQueueName.of(targetQueue),
                targetProcessor,
                NO_MAX_RETRIES,
                consume,
                Limit.unlimited()),
            new MailKey(mailKey),
            clock);
    }

    private MailRepositoryPath getMailRepositoryPath() {
        try {
            return MailRepositoryPath.fromEncoded(repositoryPath);
        } catch (Exception e) {
            throw new ReprocessingOneMailTask.InvalidMailRepositoryPathDeserializationException(repositoryPath);
        }
    }

    @Override
    public String getType() {
        return type;
    }

    public String getRepositoryPath() {
        return repositoryPath;
    }

    public String getMailKey() {
        return mailKey;
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
}
