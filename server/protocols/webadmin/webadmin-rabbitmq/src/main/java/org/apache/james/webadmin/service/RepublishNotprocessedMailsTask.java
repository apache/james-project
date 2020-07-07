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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.rabbitmq.RabbitMQMailQueue;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;


public class RepublishNotprocessedMailsTask implements Task {

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {

        private final Instant timestamp;
        private final long nbRequeuedMails;
        private final MailQueueName mailQueue;
        private final Instant olderThan;

        public AdditionalInformation(MailQueueName mailQueue, Instant olderThan, long nbRequeuedMails, Instant timestamp) {
            this.mailQueue = mailQueue;
            this.olderThan = olderThan;
            this.timestamp = timestamp;
            this.nbRequeuedMails = nbRequeuedMails;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }

        public Instant getOlderThan() {
            return olderThan;
        }

        public MailQueueName getMailQueue() {
            return mailQueue;
        }

        public long getNbRequeuedMails() {
            return nbRequeuedMails;
        }
    }

    public static final TaskType TYPE = TaskType.of("republish-not-processed-mails");

    private final Instant olderThan;
    private final RabbitMQMailQueue mailQueue;
    private final AtomicInteger nbRequeuedMails;

    public RepublishNotprocessedMailsTask(RabbitMQMailQueue mailQueue, Instant olderThan) {
        this.olderThan = olderThan;
        this.mailQueue = mailQueue;
        this.nbRequeuedMails = new AtomicInteger(0);
    }

    @Override
    public Result run() {
        mailQueue.republishNotProcessedMails(olderThan)
            .doOnNext(mailName -> nbRequeuedMails.getAndIncrement())
            .then()
            .block();

        return Result.COMPLETED;
    }

    @Override
    public TaskType type() {
        return TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(new AdditionalInformation(mailQueue.getName(), olderThan, nbRequeuedMails.get(), Clock.systemUTC().instant()));
    }

    public Instant getOlderThan() {
        return olderThan;
    }

    public MailQueueName getMailQueue() {
        return mailQueue.getName();
    }
}
