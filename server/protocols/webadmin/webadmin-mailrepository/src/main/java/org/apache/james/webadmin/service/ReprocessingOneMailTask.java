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
import java.util.function.Function;
import javax.mail.MessagingException;

import org.apache.james.json.DTOModule;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReprocessingOneMailTask implements Task {

    public static final TaskType TYPE = TaskType.of("reprocessingOneTask");

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final MailRepositoryPath repositoryPath;
        private final String targetQueue;
        private final MailKey mailKey;
        private final Optional<String> targetProcessor;

        public AdditionalInformation(MailRepositoryPath repositoryPath, String targetQueue, MailKey mailKey, Optional<String> targetProcessor) {
            this.repositoryPath = repositoryPath;
            this.targetQueue = targetQueue;
            this.mailKey = mailKey;
            this.targetProcessor = targetProcessor;
        }

        public String getMailKey() {
            return mailKey.asString();
        }

        public String getTargetQueue() {
            return targetQueue;
        }

        public Optional<String> getTargetProcessor() {
            return targetProcessor;
        }

        public String getRepositoryPath() {
            return repositoryPath.asString();
        }
    }

    public static class UrlEncodingFailureSerializationException extends RuntimeException {

        public UrlEncodingFailureSerializationException(MailRepositoryPath mailRepositoryPath) {
            super("Unable to serialize: '" + mailRepositoryPath.asString() + "' can not be url encoded");
        }
    }

    public static class InvalidMailRepositoryPathDeserializationException extends RuntimeException {

        public InvalidMailRepositoryPathDeserializationException(String mailRepositoryPath) {
            super("Unable to deserialize: '" + mailRepositoryPath + "' can not be url decoded");
        }
    }

    private static class ReprocessingOneMailTaskDTO implements TaskDTO {

        public static ReprocessingOneMailTaskDTO toDTO(ReprocessingOneMailTask domainObject, String typeName) {
            try {
                return new ReprocessingOneMailTaskDTO(
                    typeName,
                    domainObject.repositoryPath.urlEncoded(),
                    domainObject.targetQueue,
                    domainObject.mailKey.asString(),
                    domainObject.targetProcessor
                );
            } catch (Exception e) {
                throw new UrlEncodingFailureSerializationException(domainObject.repositoryPath);
            }
        }

        private final String type;
        private final String repositoryPath;
        private final String targetQueue;
        private final String mailKey;
        private final Optional<String> targetProcessor;

        public ReprocessingOneMailTaskDTO(@JsonProperty("type") String type,
                                          @JsonProperty("repositoryPath") String repositoryPath,
                                          @JsonProperty("targetQueue") String targetQueue,
                                          @JsonProperty("mailKey") String mailKey,
                                          @JsonProperty("targetProcessor") Optional<String> targetProcessor) {
            this.type = type;
            this.repositoryPath = repositoryPath;
            this.mailKey = mailKey;
            this.targetQueue = targetQueue;
            this.targetProcessor = targetProcessor;
        }

        public ReprocessingOneMailTask fromDTO(ReprocessingService reprocessingService) {
            return new ReprocessingOneMailTask(
                reprocessingService,
                getMailRepositoryPath(),
                targetQueue,
                new MailKey(mailKey),
                targetProcessor
            );
        }

        private MailRepositoryPath getMailRepositoryPath() {
            try {
                return MailRepositoryPath.fromEncoded(repositoryPath);
            } catch (Exception e) {
                throw new InvalidMailRepositoryPathDeserializationException(repositoryPath);
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

        public Optional<String> getTargetProcessor() {
            return targetProcessor;
        }
    }

    public static final Function<ReprocessingService, TaskDTOModule<ReprocessingOneMailTask, ReprocessingOneMailTaskDTO>> MODULE = (reprocessingService) ->
        DTOModule
            .forDomainObject(ReprocessingOneMailTask.class)
            .convertToDTO(ReprocessingOneMailTaskDTO.class)
            .toDomainObjectConverter(dto -> dto.fromDTO(reprocessingService))
            .toDTOConverter(ReprocessingOneMailTaskDTO::toDTO)
            .typeName(TYPE.asString())
            .withFactory(TaskDTOModule::new);

    private final ReprocessingService reprocessingService;
    private final MailRepositoryPath repositoryPath;
    private final String targetQueue;
    private final MailKey mailKey;
    private final Optional<String> targetProcessor;
    private final AdditionalInformation additionalInformation;

    public ReprocessingOneMailTask(ReprocessingService reprocessingService,
                                   MailRepositoryPath repositoryPath, String targetQueue, MailKey mailKey, Optional<String> targetProcessor) {
        this.reprocessingService = reprocessingService;
        this.repositoryPath = repositoryPath;
        this.targetQueue = targetQueue;
        this.mailKey = mailKey;
        this.targetProcessor = targetProcessor;
        this.additionalInformation = new AdditionalInformation(repositoryPath, targetQueue, mailKey, targetProcessor);
    }

    @Override
    public Result run() {
        try {
            reprocessingService.reprocess(repositoryPath, mailKey, targetProcessor, targetQueue);
            return Result.COMPLETED;
        } catch (MessagingException | MailRepositoryStore.MailRepositoryStoreException e) {
            LOGGER.error("Encountered error while reprocessing repository", e);
            return Result.PARTIAL;
        }
    }

    @Override
    public TaskType type() {
        return TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(additionalInformation);
    }

}
