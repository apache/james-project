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
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

public class ReprocessingAllMailsTask implements Task {

    public static final TaskType TYPE = TaskType.of("reprocessing-all");

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final MailRepositoryPath repositoryPath;
        private final ReprocessingService.Configuration configuration;
        private final long initialCount;
        private final long remainingCount;
        private final Instant timestamp;

        public AdditionalInformation(MailRepositoryPath repositoryPath, ReprocessingService.Configuration configuration, long initialCount, long remainingCount, Instant timestamp) {
            this.repositoryPath = repositoryPath;
            this.configuration = configuration;
            this.initialCount = initialCount;
            this.remainingCount = remainingCount;
            this.timestamp = timestamp;
        }

        public ReprocessingService.Configuration getConfiguration() {
            return configuration;
        }

        public String getRepositoryPath() {
            return repositoryPath.asString();
        }

        public long getRemainingCount() {
            return remainingCount;
        }

        public long getInitialCount() {
            return initialCount;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }
    }

    public static class UrlEncodingFailureSerializationException extends RuntimeException {

        public UrlEncodingFailureSerializationException(MailRepositoryPath mailRepositoryPath) {
            super("Unable to serialize: '" + mailRepositoryPath + "' can not be url encoded");
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
    private final long repositorySize;
    private final AtomicLong processedCount;

    public ReprocessingAllMailsTask(ReprocessingService reprocessingService, long repositorySize,
                                    MailRepositoryPath repositoryPath, ReprocessingService.Configuration configuration) {
        this.reprocessingService = reprocessingService;
        this.repositoryPath = repositoryPath;
        this.configuration = configuration;
        this.repositorySize = repositorySize;
        this.processedCount = new AtomicLong(0);
    }

    private void notifyProgress(MailKey key) {
        processedCount.incrementAndGet();
    }

    @Override
    public Result run() {
        return reprocessingService.reprocessAll(repositoryPath, configuration, this::notifyProgress)
            .block();
    }

    MailRepositoryPath getRepositoryPath() {
        return repositoryPath;
    }

    long getRepositorySize() {
        return repositorySize;
    }

    ReprocessingService.Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public TaskType type() {
        return TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(new AdditionalInformation(
            repositoryPath, configuration, repositorySize, repositorySize - processedCount.get(),
            Clock.systemUTC().instant()));
    }

}
