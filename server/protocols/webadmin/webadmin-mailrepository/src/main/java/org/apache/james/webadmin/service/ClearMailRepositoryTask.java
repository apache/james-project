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
import java.util.function.Supplier;

import javax.mail.MessagingException;

import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;

import com.google.common.base.Throwables;

public class ClearMailRepositoryTask implements Task {

    public static final String TYPE = "clearMailRepository";

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final String repositoryUrl;
        private final Supplier<Long> countSupplier;
        private final long initialCount;

        public AdditionalInformation(String repositoryUrl, Supplier<Long> countSupplier) {
            this.repositoryUrl = repositoryUrl;
            this.initialCount = countSupplier.get();
            this.countSupplier = countSupplier;
        }

        public String getRepositoryUrl() {
            return repositoryUrl;
        }

        public long getRemainingCount() {
            return countSupplier.get();
        }

        public long getInitialCount() {
            return initialCount;
        }
    }

    private final MailRepository mailRepository;
    private final AdditionalInformation additionalInformation;

    public ClearMailRepositoryTask(MailRepository mailRepository, String url) {
        this.mailRepository = mailRepository;
        this.additionalInformation = new AdditionalInformation(url, this::getRemainingSize);
    }

    @Override
    public Result run() {
        try {
            mailRepository.removeAll();
            return Result.COMPLETED;
        } catch (MessagingException e) {
            LOGGER.error("Encountered error while clearing repository", e);
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

    public long getRemainingSize() {
        try {
            return mailRepository.size();
        } catch (MessagingException e) {
            throw Throwables.propagate(e);
        }
    }
}
