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
import java.util.concurrent.atomic.AtomicLong;

import javax.mail.MessagingException;

import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class ReprocessingAllMailsTask implements Task {

    public static final String TYPE = "reprocessingAllTask";

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final MailRepositoryPath repositoryPath;
        private final String targetQueue;
        private final Optional<String> targetProcessor;
        private final long initialCount;
        private final AtomicLong processedCount;

        public AdditionalInformation(MailRepositoryPath repositoryPath, String targetQueue, Optional<String> targetProcessor, long initialCount) {
            this.repositoryPath = repositoryPath;
            this.targetQueue = targetQueue;
            this.targetProcessor = targetProcessor;
            this.initialCount = initialCount;
            this.processedCount = new AtomicLong(0);
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

        public long getRemainingCount() {
            return initialCount - processedCount.get();
        }

        public long getInitialCount() {
            return initialCount;
        }

        @JsonIgnore
        public void notifyProgress(MailKey key) {
            processedCount.incrementAndGet();
        }
    }

    private final ReprocessingService reprocessingService;
    private final MailRepositoryPath repositoryPath;
    private final String targetQueue;
    private final Optional<String> targetProcessor;
    private final AdditionalInformation additionalInformation;

    public ReprocessingAllMailsTask(ReprocessingService reprocessingService, long repositorySize,
                                    MailRepositoryPath repositoryPath, String targetQueue, Optional<String> targetProcessor) {
        this.reprocessingService = reprocessingService;
        this.repositoryPath = repositoryPath;
        this.targetQueue = targetQueue;
        this.targetProcessor = targetProcessor;
        this.additionalInformation = new AdditionalInformation(
            repositoryPath, targetQueue, targetProcessor, repositorySize);
    }

    @Override
    public Result run() {
        try {
            reprocessingService.reprocessAll(repositoryPath, targetProcessor, targetQueue, additionalInformation::notifyProgress);
            return Result.COMPLETED;
        } catch (MessagingException | MailRepositoryStore.MailRepositoryStoreException e) {
            LOGGER.error("Encountered error while reprocessing repository", e);
            return Result.PARTIAL;
        }
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(additionalInformation);
    }

}
