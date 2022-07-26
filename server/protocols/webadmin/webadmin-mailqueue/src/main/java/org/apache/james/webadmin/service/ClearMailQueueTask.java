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
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

public class ClearMailQueueTask implements Task {

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {

        private final MailQueueName mailQueueName;
        private final long initialCount;
        private final long remainingCount;
        private final Instant timestamp;

        public AdditionalInformation(MailQueueName mailQueueName, long initialCount, long remainingCount, Instant timestamp) {
            this.mailQueueName = mailQueueName;
            this.initialCount = initialCount;
            this.remainingCount = remainingCount;
            this.timestamp = timestamp;
        }

        public String getMailQueueName() {
            return mailQueueName.asString();
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
        ManageableMailQueue create(MailQueueName mailQueueName) throws MailQueue.MailQueueException;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ClearMailQueueTask.class);
    public static final TaskType TYPE = TaskType.of("clear-mail-queue");

    private final MailQueueName queueName;
    private final ClearMailQueueTask.MailQueueFactory factory;
    private Optional<Long> initialCount;
    private Optional<ManageableMailQueue> queue;
    private Optional<TaskExecutionDetails.AdditionalInformation> lastAdditionalInformation;

    public ClearMailQueueTask(MailQueueName queueName, ClearMailQueueTask.MailQueueFactory factory) {
        this.queueName = queueName;
        this.factory = factory;
        this.initialCount = Optional.empty();
        this.queue = Optional.empty();
        this.lastAdditionalInformation = Optional.empty();
    }

    @Override
    public Result run() {
        try (ManageableMailQueue queue = factory.create(queueName)) {
            this.initialCount = Mono.justOrEmpty(queue)
                .flatMap(q -> Mono.from(q.getSizeReactive()))
                .blockOptional();
            this.queue = Optional.of(queue);
            queue.clear();
            this.lastAdditionalInformation = Mono.from(detailsReactive())
                .block();
        } catch (MailQueue.MailQueueException | IOException e) {
            LOGGER.error("Clear MailQueue got an exception", e);
            return Result.PARTIAL;
        } finally {
            this.queue = Optional.empty();
        }

        return Result.COMPLETED;
    }

    @Override
    public TaskType type() {
        return TYPE;
    }

    @Override
    public Publisher<Optional<TaskExecutionDetails.AdditionalInformation>> detailsReactive() {
        return Mono.justOrEmpty(lastAdditionalInformation)
            .switchIfEmpty(getAdditionalInformation())
            .map(Optional::of)
            .switchIfEmpty(Mono.just(Optional.empty()));
    }

    MailQueueName getQueueName() {
        return queueName;
    }

    private Mono<TaskExecutionDetails.AdditionalInformation> getAdditionalInformation() {
        return Mono.justOrEmpty(queue)
            .flatMap(q -> Mono.from(q.getSizeReactive()))
            .map(remainingSize -> new AdditionalInformation(queueName, initialCount.get(), remainingSize, Clock.systemUTC().instant()));
    }
}
