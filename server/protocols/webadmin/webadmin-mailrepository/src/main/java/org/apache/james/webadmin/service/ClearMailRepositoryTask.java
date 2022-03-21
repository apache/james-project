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
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import jakarta.mail.MessagingException;

import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

public class ClearMailRepositoryTask implements Task {

    public static final TaskType TYPE = TaskType.of("clear-mail-repository");

    public static class Factory {
        private final MailRepositoryStore mailRepositoryStore;

        @Inject
        public Factory(MailRepositoryStore mailRepositoryStore) {
            this.mailRepositoryStore = mailRepositoryStore;
        }

        public ClearMailRepositoryTask create(MailRepositoryPath mailRepositoryPath) throws MailRepositoryStore.MailRepositoryStoreException {
            List<MailRepository> mailRepositories = mailRepositoryStore.getByPath(mailRepositoryPath)
                .collect(ImmutableList.toImmutableList());
            return new ClearMailRepositoryTask(mailRepositories, mailRepositoryPath);
        }
    }

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final MailRepositoryPath repositoryPath;
        private final long initialCount;
        private final long remainingCount;
        private final Instant timestamp;

        public AdditionalInformation(MailRepositoryPath repositoryPath, long initialCount, long remainingCount, Instant timestamp) {
            this.repositoryPath = repositoryPath;
            this.initialCount = initialCount;
            this.remainingCount = remainingCount;
            this.timestamp = timestamp;
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
            super("Unable to serialize: '" + mailRepositoryPath.asString() + "' can not be url encoded");
        }
    }

    public static class InvalidMailRepositoryPathDeserializationException extends RuntimeException {

        public InvalidMailRepositoryPathDeserializationException(String mailRepositoryPath) {
            super("Unable to deserialize: '" + mailRepositoryPath + "' can not be url decoded");
        }
    }

    private final List<MailRepository> mailRepositories;
    private final MailRepositoryPath mailRepositoryPath;
    private final long initialCount;

    public ClearMailRepositoryTask(List<MailRepository> mailRepositories, MailRepositoryPath path) {
        this.mailRepositories = mailRepositories;
        this.mailRepositoryPath = path;
        this.initialCount = getRemainingSize();
    }

    @Override
    public Result run() {
        try {
            removeAllInAllRepositories();
            return Result.COMPLETED;
        } catch (MessagingException e) {
            LOGGER.error("Encountered error while clearing repository", e);
            return Result.PARTIAL;
        }
    }

    private void removeAllInAllRepositories() throws MessagingException {
        mailRepositories.forEach(Throwing.consumer(MailRepository::removeAll).sneakyThrow());
    }

    @Override
    public TaskType type() {
        return TYPE;
    }

    MailRepositoryPath getMailRepositoryPath() {
        return mailRepositoryPath;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(new AdditionalInformation(mailRepositoryPath, initialCount, getRemainingSize(), Clock.systemUTC().instant()));
    }

    public long getRemainingSize() {
        return mailRepositories
            .stream()
            .map(Throwing.function(MailRepository::size).sneakyThrow())
            .mapToLong(Long::valueOf)
            .sum();
    }
}
