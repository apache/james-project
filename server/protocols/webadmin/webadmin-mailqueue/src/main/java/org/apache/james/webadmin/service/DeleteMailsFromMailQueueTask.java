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

import org.apache.james.core.MailAddress;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.primitives.Booleans;

public class DeleteMailsFromMailQueueTask implements Task {

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final String mailQueueName;
        private final Supplier<Long> countSupplier;
        private final long initialCount;

        private final Optional<String> sender;
        private final Optional<String> name;
        private final Optional<String> recipient;

        public AdditionalInformation(String mailQueueName, Supplier<Long> countSupplier,
                                     Optional<MailAddress> maybeSender, Optional<String> maybeName,
                                     Optional<MailAddress> maybeRecipient) {
            this.mailQueueName = mailQueueName;
            this.initialCount = countSupplier.get();
            this.countSupplier = countSupplier;

            sender = maybeSender.map(MailAddress::asString);
            name = maybeName;
            recipient = maybeRecipient.map(MailAddress::asString);
        }

        public String getMailQueueName() {
            return mailQueueName;
        }

        public long getRemainingCount() {
            return countSupplier.get();
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
    }

    public static final String TYPE = "deleteMailsFromMailQueue";

    private final ManageableMailQueue queue;
    private final Optional<MailAddress> maybeSender;
    private final Optional<String> maybeName;
    private final Optional<MailAddress> maybeRecipient;
    private final AdditionalInformation additionalInformation;

    public DeleteMailsFromMailQueueTask(ManageableMailQueue queue, Optional<MailAddress> maybeSender,
                                        Optional<String> maybeName, Optional<MailAddress> maybeRecipient) {
        Preconditions.checkArgument(
                Booleans.countTrue(maybeSender.isPresent(), maybeName.isPresent(), maybeRecipient.isPresent()) == 1,
                "You should provide one and only one of the query parameters 'sender', 'name' or 'recipient'.");

        this.queue = queue;
        this.maybeSender = maybeSender;
        this.maybeName = maybeName;
        this.maybeRecipient = maybeRecipient;

        additionalInformation = new AdditionalInformation(queue.getName(), this::getRemainingSize, maybeSender,
                maybeName, maybeRecipient);
    }

    @Override
    public Result run() {
        maybeSender.ifPresent(Throwing.consumer(
                (MailAddress sender) -> queue.remove(ManageableMailQueue.Type.Sender, sender.asString())));
        maybeName.ifPresent(Throwing.consumer(
                (String name) -> queue.remove(ManageableMailQueue.Type.Name, name)));
        maybeRecipient.ifPresent(Throwing.consumer(
                (MailAddress recipient) -> queue.remove(ManageableMailQueue.Type.Recipient, recipient.asString())));

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

    public long getRemainingSize() {
        try {
            return queue.getSize();
        } catch (MailQueue.MailQueueException e) {
            throw new RuntimeException(e);
        }
    }
}
