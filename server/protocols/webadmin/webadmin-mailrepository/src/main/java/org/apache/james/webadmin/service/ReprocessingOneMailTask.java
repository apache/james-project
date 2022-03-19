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

import jakarta.mail.MessagingException;

import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

public class ReprocessingOneMailTask implements Task {

    public static final TaskType TYPE = TaskType.of("reprocessing-one");

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final MailRepositoryPath repositoryPath;
        private final ReprocessingService.Configuration configuration;
        private final MailKey mailKey;
        private final Instant timestamp;

        public AdditionalInformation(MailRepositoryPath repositoryPath, ReprocessingService.Configuration configuration, MailKey mailKey, Instant timestamp) {
            this.repositoryPath = repositoryPath;
            this.configuration = configuration;
            this.mailKey = mailKey;
            this.timestamp = timestamp;
        }

        public String getMailKey() {
            return mailKey.asString();
        }

        public ReprocessingService.Configuration getConfiguration() {
            return configuration;
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
    private final ReprocessingService.Configuration configuration;
    private final MailKey mailKey;
    private final AdditionalInformation additionalInformation;

    public ReprocessingOneMailTask(ReprocessingService reprocessingService,
                                   MailRepositoryPath repositoryPath,
                                   ReprocessingService.Configuration configuration,
                                   MailKey mailKey,
                                   Clock clock) {
        this.reprocessingService = reprocessingService;
        this.repositoryPath = repositoryPath;
        this.configuration = configuration;
        this.mailKey = mailKey;
        this.additionalInformation = new AdditionalInformation(repositoryPath, configuration, mailKey, clock.instant());
    }

    @Override
    public Result run() {
        try {
            reprocessingService.reprocess(repositoryPath, mailKey, configuration);
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

    MailKey getMailKey() {
        return mailKey;
    }

    public ReprocessingService.Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(additionalInformation);
    }

}
