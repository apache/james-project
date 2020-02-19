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

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

public class ClearMailQueueTask implements Task {

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {

        private final String mailQueueName;
        private final long initialCount;
        private final long remainingCount;
        private final Instant timestamp;

        public AdditionalInformation(String mailQueueName, long initialCount, long remainingCount, Instant timestamp) {
            this.mailQueueName = mailQueueName;
            this.initialCount = initialCount;
            this.remainingCount = remainingCount;
            this.timestamp = timestamp;
        }

        public String getMailQueueName() {
            return mailQueueName;
        }

        public long getInitialCount() {
            return initialCount;
        }

        public long getRemainingCount() {
            return remainingCount;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }

    }

    public static class UnknownSerializedQueue extends RuntimeException {

        public UnknownSerializedQueue(String queueName) {
            super("Unable to retrieve '" + queueName + "' queue");
        }
    }

    @FunctionalInterface
    public interface MailQueueFactory {
        ManageableMailQueue create(String mailQueueName);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ClearMailQueueTask.class);
    public static final TaskType TYPE = TaskType.of("clear-mail-queue");

    private final String queueName;
    private final ClearMailQueueTask.MailQueueFactory factory;
    private Optional<Long> initialCount;
    private Optional<ManageableMailQueue> queue;

    public ClearMailQueueTask(String queueName, ClearMailQueueTask.MailQueueFactory factory) {
        this.queueName = queueName;
        this.factory = factory;
        this.initialCount = Optional.empty();
        this.queue = Optional.empty();
    }

    @Override
    public Result run() {
        try (ManageableMailQueue queue = factory.create(queueName)) {
            this.initialCount = Optional.of(getRemainingSize(queue));
            this.queue = Optional.of(queue);
            queue.clear();
            this.queue = Optional.empty();
        } catch (MailQueue.MailQueueException | IOException e) {
            LOGGER.error("Clear MailQueue got an exception", e);
            return Result.PARTIAL;
        }

        return Result.COMPLETED;
    }

    @Override
    public TaskType type() {
        return TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return queue.map(q -> new AdditionalInformation(queueName, initialCount.get(), getRemainingSize(q), Clock.systemUTC().instant()));
    }

    String getQueueName() {
        return queueName;
    }

    @VisibleForTesting
    Optional<Long> initialCount() {
        return initialCount;
    }

    private long getRemainingSize(ManageableMailQueue mailQueue) {
        try {
            return mailQueue.getSize();
        } catch (MailQueue.MailQueueException e) {
            throw new RuntimeException(e);
        }
    }
}
