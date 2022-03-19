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

import javax.inject.Inject;

import jakarta.mail.MessagingException;

import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ClearMailRepositoryTask implements Task {

    public static final TaskType TYPE = TaskType.of("clear-mail-repository");

    public static class Factory {
        private final MailRepositoryStore mailRepositoryStore;

        @Inject
        public Factory(MailRepositoryStore mailRepositoryStore) {
            this.mailRepositoryStore = mailRepositoryStore;
        }

        public ClearMailRepositoryTask create(MailRepositoryPath mailRepositoryPath) {
            return new ClearMailRepositoryTask(mailRepositoryStore, mailRepositoryPath);
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

    private final MailRepositoryStore mailRepositoryStore;
    private final MailRepositoryPath mailRepositoryPath;
    private long initialCount = 0;

    public ClearMailRepositoryTask(MailRepositoryStore mailRepositoryStore, MailRepositoryPath path) {
        this.mailRepositoryStore = mailRepositoryStore;
        this.mailRepositoryPath = path;
    }

    @Override
    public Result run() {
        initialCount = getRemainingSize().block();
        try {
            removeAllInAllRepositories();
            return Result.COMPLETED;
        } catch (MailRepositoryStore.MailRepositoryStoreException e) {
            LOGGER.error("Encountered error while clearing repository", e);
            return Result.PARTIAL;
        }
    }

    private void removeAllInAllRepositories() throws MailRepositoryStore.MailRepositoryStoreException {
        mailRepositoryStore.getByPath(mailRepositoryPath)
            .forEach(Throwing.consumer(MailRepository::removeAll).sneakyThrow());
    }

    @Override
    public TaskType type() {
        return TYPE;
    }

    MailRepositoryPath getMailRepositoryPath() {
        return mailRepositoryPath;
    }

    @Override
    public Publisher<Optional<TaskExecutionDetails.AdditionalInformation>> detailsReactive() {
        return getRemainingSize()
            .map(remainingSize -> new AdditionalInformation(mailRepositoryPath, initialCount, remainingSize, Clock.systemUTC().instant()))
            .map(Optional::of);
    }

    public Mono<Long> getRemainingSize() {
        try {
            return Flux.fromStream(mailRepositoryStore.getByPath(mailRepositoryPath))
                .flatMap(MailRepository::sizeReactive)
                .reduce(0L, Long::sum);
        } catch (MailRepositoryStore.MailRepositoryStoreException e) {
            throw new RuntimeException(e);
    }
}
}
