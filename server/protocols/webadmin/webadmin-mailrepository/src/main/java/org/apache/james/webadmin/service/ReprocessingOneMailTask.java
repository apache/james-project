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
import java.time.Instant;
import java.util.Optional;

import javax.mail.MessagingException;

import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

public class ReprocessingOneMailTask implements Task {

    public static final TaskType TYPE = TaskType.of("reprocessing-one");

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final MailRepositoryPath repositoryPath;
        private final String targetQueue;
        private final MailKey mailKey;
        private final Optional<String> targetProcessor;
        private final Instant timestamp;

        public AdditionalInformation(MailRepositoryPath repositoryPath, MailQueueName targetQueue, MailKey mailKey, Optional<String> targetProcessor, Instant timestamp) {
            this.repositoryPath = repositoryPath;
            this.targetQueue = targetQueue.asString();
            this.mailKey = mailKey;
            this.targetProcessor = targetProcessor;
            this.timestamp = timestamp;
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

        @Override
        public Instant timestamp() {
            return timestamp;
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

    private final ReprocessingService reprocessingService;
    private final MailRepositoryPath repositoryPath;
    private final MailQueueName targetQueue;
    private final MailKey mailKey;
    private final Optional<String> targetProcessor;
    private final AdditionalInformation additionalInformation;

    public ReprocessingOneMailTask(ReprocessingService reprocessingService,
                                   MailRepositoryPath repositoryPath,
                                   MailQueueName targetQueue,
                                   MailKey mailKey,
                                   Optional<String> targetProcessor,
                                   Clock clock) {
        this.reprocessingService = reprocessingService;
        this.repositoryPath = repositoryPath;
        this.targetQueue = targetQueue;
        this.mailKey = mailKey;
        this.targetProcessor = targetProcessor;
        this.additionalInformation = new AdditionalInformation(repositoryPath, targetQueue, mailKey, targetProcessor, clock.instant());
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

    MailRepositoryPath getRepositoryPath() {
        return repositoryPath;
    }

    MailQueueName getTargetQueue() {
        return targetQueue;
    }

    MailKey getMailKey() {
        return mailKey;
    }

    Optional<String> getTargetProcessor() {
        return targetProcessor;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(additionalInformation);
    }

}
