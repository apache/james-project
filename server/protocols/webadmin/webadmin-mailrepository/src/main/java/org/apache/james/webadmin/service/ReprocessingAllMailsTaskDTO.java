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
                domainObject.getTargetQueue().asString(),
                domainObject.getTargetProcessor()
            );
        } catch (Exception e) {
            throw new ReprocessingAllMailsTask.UrlEncodingFailureSerializationException(domainObject.getRepositoryPath());
        }
    }

    private final String type;
    private final long repositorySize;
    private final String repositoryPath;
    private final String targetQueue;
    private final Optional<String> targetProcessor;

    public ReprocessingAllMailsTaskDTO(@JsonProperty("type") String type,
                                       @JsonProperty("repositorySize") long repositorySize,
                                       @JsonProperty("repositoryPath") String repositoryPath,
                                       @JsonProperty("targetQueue") String targetQueue,
                                       @JsonProperty("targetProcessor") Optional<String> targetProcessor) {
        this.type = type;
        this.repositorySize = repositorySize;
        this.repositoryPath = repositoryPath;
        this.targetQueue = targetQueue;
        this.targetProcessor = targetProcessor;
    }

    private ReprocessingAllMailsTask fromDTO(ReprocessingService reprocessingService) {
        try {
            return new ReprocessingAllMailsTask(
                reprocessingService,
                repositorySize,
                MailRepositoryPath.fromEncoded(repositoryPath),
                MailQueueName.of(targetQueue),
                targetProcessor
            );
        } catch (Exception e) {
            throw new ReprocessingAllMailsTask.InvalidMailRepositoryPathDeserializationException(repositoryPath);
        }
    }

    @Override
    public String getType() {
        return type;
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

    public Optional<String> getTargetProcessor() {
        return targetProcessor;
    }
}
