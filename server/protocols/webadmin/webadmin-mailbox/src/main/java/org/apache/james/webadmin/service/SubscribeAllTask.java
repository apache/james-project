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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

public class SubscribeAllTask implements Task {
    static final TaskType TASK_TYPE = TaskType.of("SubscribeAllTask");
    private static final Logger LOGGER = LoggerFactory.getLogger(SubscribeAllTask.class);

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final Username username;
        private final long subscribedCount;
        private final long unsubscribedCount;
        private final Instant timestamp;

        public AdditionalInformation(Username username, long subscribedCount, long unsubscribedCount, Instant timestamp) {
            this.username = username;
            this.subscribedCount = subscribedCount;
            this.unsubscribedCount = unsubscribedCount;
            this.timestamp = timestamp;
        }

        public Username getUsername() {
            return username;
        }

        public long getSubscribedCount() {
            return subscribedCount;
        }

        public long getUnsubscribedCount() {
            return unsubscribedCount;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }
    }

    private final MailboxManager mailboxManager;
    private final SubscriptionManager subscriptionManager;
    private final Username username;
    private final AtomicLong subscribedCount;
    private final AtomicLong unsubscribedCount;

    public SubscribeAllTask(MailboxManager mailboxManager, SubscriptionManager subscriptionManager, Username username) {
        this.mailboxManager = mailboxManager;
        this.subscriptionManager = subscriptionManager;
        this.username = username;
        subscribedCount = new AtomicLong(0);
        unsubscribedCount = new AtomicLong(0);
    }

    @Override
    public Result run() {
        final MailboxSession session = mailboxManager.createSystemSession(username);
        try {
            Collection<String> subscriptions = subscriptionManager.subscriptions(session);
            List<String> names = mailboxManager.search(MailboxQuery.privateMailboxesBuilder(session).build(), session)
                .map(mailbox -> mailbox.getPath().getName())
                .collectList()
                .block();

            subscriptions.stream()
                .filter(subscription -> !names.contains(subscription))
                .forEach(Throwing.consumer(subscription -> {
                    subscriptionManager.unsubscribe(session, subscription);
                    unsubscribedCount.incrementAndGet();
                }));
            names.stream()
                .filter(name -> !subscriptions.contains(name))
                .forEach(Throwing.consumer(name -> {
                    subscriptionManager.subscribe(session, name);
                    subscribedCount.incrementAndGet();
                }));

        } catch (SubscriptionException e) {
            LOGGER.error("Could not retrieve subscriptions for {}", username, e);
            return Result.PARTIAL;
        }
        return Result.COMPLETED;
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(new AdditionalInformation(username, subscribedCount.get(), unsubscribedCount.get(), Clock.systemUTC().instant()));
    }

    public Username getUsername() {
        return username;
    }
}
