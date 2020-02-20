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

import org.apache.james.core.MailAddress;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.apache.james.util.OptionalUtils;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.primitives.Booleans;

public class DeleteMailsFromMailQueueTask implements Task {

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final MailQueueName mailQueueName;
        private final long remainingCount;
        private final long initialCount;

        private final Optional<String> sender;
        private final Optional<String> name;
        private final Optional<String> recipient;
        private final Instant timestamp;

        public AdditionalInformation(MailQueueName mailQueueName, long initialCount, long remainingCount,
                                     Optional<MailAddress> maybeSender, Optional<String> maybeName,
                                     Optional<MailAddress> maybeRecipient, Instant timestamp) {
            this.mailQueueName = mailQueueName;
            this.initialCount = initialCount;
            this.remainingCount = remainingCount;

            sender = maybeSender.map(MailAddress::asString);
            name = maybeName;
            recipient = maybeRecipient.map(MailAddress::asString);
            this.timestamp = timestamp;
        }

        public String getMailQueueName() {
            return mailQueueName.asString();
        }

        public long getRemainingCount() {
            return remainingCount;
        }

        public long getInitialCount() {
            return initialCount;
        }

        public Optional<String> getSender() {
            return sender;
        }

        public Optional<String> getName() {
            return name;
        }

        public Optional<String> getRecipient() {
            return recipient;
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

    public static final TaskType TYPE = TaskType.of("delete-mails-from-mail-queue");

    private final Optional<MailAddress> maybeSender;
    private final Optional<String> maybeName;
    private final Optional<MailAddress> maybeRecipient;
    private final MailQueueFactory factory;
    private final MailQueueName queueName;
    private Optional<Long> initialCount;
    private Optional<ManageableMailQueue> queue;
    private Optional<TaskExecutionDetails.AdditionalInformation> lastAdditionalInformation;

    public DeleteMailsFromMailQueueTask(MailQueueName queueName, MailQueueFactory factory,
                                        Optional<MailAddress> maybeSender,
                                        Optional<String> maybeName, Optional<MailAddress> maybeRecipient) {
        this.factory = factory;
        this.queueName = queueName;
        Preconditions.checkArgument(
            Booleans.countTrue(maybeSender.isPresent(), maybeName.isPresent(), maybeRecipient.isPresent()) == 1,
            "You should provide one and only one of the query parameters 'sender', 'name' or 'recipient'.");

        this.maybeSender = maybeSender;
        this.maybeName = maybeName;
        this.maybeRecipient = maybeRecipient;
        this.initialCount = Optional.empty();
        this.queue = Optional.empty();
        this.lastAdditionalInformation = Optional.empty();

    }

    @Override
    public Result run() {
        try (ManageableMailQueue queue = factory.create(queueName)) {
            this.initialCount = Optional.of(getRemainingSize(queue));
            this.queue = Optional.of(queue);
            maybeSender.ifPresent(Throwing.consumer(
                (MailAddress sender) -> queue.remove(ManageableMailQueue.Type.Sender, sender.asString())));
            maybeName.ifPresent(Throwing.consumer(
                (String name) -> queue.remove(ManageableMailQueue.Type.Name, name)));
            maybeRecipient.ifPresent(Throwing.consumer(
                (MailAddress recipient) -> queue.remove(ManageableMailQueue.Type.Recipient, recipient.asString())));

            this.lastAdditionalInformation = details();

            return Result.COMPLETED;
        } catch (IOException | MailQueue.MailQueueException e) {
            LOGGER.error("Delete mails from MailQueue got an exception", e);
            return Result.PARTIAL;
        } finally {
            this.queue = Optional.empty();
        }
    }

    public MailQueueName getQueueName() {
        return queueName;
    }

    @Override
    public TaskType type() {
        return TYPE;
    }

    Optional<String> getMaybeName() {
        return maybeName;
    }

    Optional<MailAddress> getMaybeRecipient() {
        return maybeRecipient;
    }

    Optional<MailAddress> getMaybeSender() {
        return maybeSender;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return OptionalUtils.orSuppliers(
            () -> this.lastAdditionalInformation,
            () -> this.queue.map(queue ->
                new AdditionalInformation(
                    queueName,
                    initialCount.get(),
                    getRemainingSize(queue), maybeSender,
                    maybeName, maybeRecipient, Clock.systemUTC().instant())));
    }

    public long getRemainingSize(ManageableMailQueue queue) {
        try {
            return queue.getSize();
        } catch (MailQueue.MailQueueException e) {
            throw new RuntimeException(e);
        }
    }
}
