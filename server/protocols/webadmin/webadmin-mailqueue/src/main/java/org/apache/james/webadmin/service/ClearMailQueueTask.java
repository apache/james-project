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

import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClearMailQueueTask implements Task {

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final String mailQueueName;
        private final long initialCount;
        private final Supplier<Long> countSupplier;

        public AdditionalInformation(String mailQueueName, Supplier<Long> countSupplier) {
            this.mailQueueName = mailQueueName;
            this.countSupplier = countSupplier;
            this.initialCount = countSupplier.get();
        }

        public String getMailQueueName() {
            return mailQueueName;
        }

        public long getInitialCount() {
            return initialCount;
        }

        public long getRemainingCount() {
            return countSupplier.get();
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ClearMailQueueTask.class);
    public static final String TYPE = "clearMailQueue";

    private final ManageableMailQueue queue;
    private final AdditionalInformation additionalInformation;

    public ClearMailQueueTask(ManageableMailQueue queue) {
        this.queue = queue;
        additionalInformation = new AdditionalInformation(queue.getName(), this::getRemainingSize);
    }

    @Override
    public Result run() {
        try {
            queue.clear();
        } catch (MailQueue.MailQueueException e) {
            LOGGER.error("Clear MailQueue got an exception", e);
            return Result.PARTIAL;
        }

        return Result.COMPLETED;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(additionalInformation);
    }

    private long getRemainingSize() {
        try {
            return queue.getSize();
        } catch (MailQueue.MailQueueException e) {
            throw new RuntimeException(e);
        }
    }
}
