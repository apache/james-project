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

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import javax.mail.MessagingException;

import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;

import com.github.fge.lambdas.Throwing;

public class ClearMailRepositoryTask implements Task {

    public static final String TYPE = "clearMailRepository";

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final MailRepositoryPath repositoryPath;
        private final Supplier<Long> countSupplier;
        private final long initialCount;

        public AdditionalInformation(MailRepositoryPath repositoryPath, Supplier<Long> countSupplier) {
            this.repositoryPath = repositoryPath;
            this.initialCount = countSupplier.get();
            this.countSupplier = countSupplier;
        }

        public String getRepositoryPath() {
            return repositoryPath.asString();
        }

        public long getRemainingCount() {
            return countSupplier.get();
        }

        public long getInitialCount() {
            return initialCount;
        }
    }

    private final List<MailRepository> mailRepositories;
    private final AdditionalInformation additionalInformation;

    public ClearMailRepositoryTask(List<MailRepository> mailRepositories, MailRepositoryPath path) {
        this.mailRepositories = mailRepositories;
        this.additionalInformation = new AdditionalInformation(path, this::getRemainingSize);
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
        mailRepositories.forEach(Throwing.consumer(MailRepository::removeAll));
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(additionalInformation);
    }

    public long getRemainingSize() {
        return mailRepositories
                .stream()
                .map(Throwing.function(MailRepository::size).sneakyThrow())
                .mapToLong(Long::valueOf)
                .sum();
    }
}
