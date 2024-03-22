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
import java.util.Date;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.task.AsyncSafeTask;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.apache.james.webadmin.service.ExpireMailboxService.Context;
import org.apache.james.webadmin.service.ExpireMailboxService.RunningOptions;
import org.reactivestreams.Publisher;

public class ExpireMailboxTask implements AsyncSafeTask {
    public static final TaskType TASK_TYPE = TaskType.of("ExpireMailboxTask");

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {

        public static ExpireMailboxTask.AdditionalInformation from(Context context,
                                                                   RunningOptions runningOptions) {
            return new ExpireMailboxTask.AdditionalInformation(
                Clock.systemUTC().instant(),
                runningOptions,
                context.getInboxesExpired(),
                context.getInboxesFailed(),
                context.getInboxesProcessed(),
                context.getMessagesDeleted());
        }

        private final Instant timestamp;
        private final RunningOptions runningOptions;
        private final long mailboxesExpired;
        private final long mailboxesFailed;
        private final long mailboxesProcessed;
        private final long messagesDeleted;

        public AdditionalInformation(Instant timestamp,
                                     RunningOptions runningOptions,
                                     long mailboxesExpired,
                                     long mailboxesFailed,
                                     long mailboxesProcessed,
                                     long messagesDeleted) {
            this.timestamp = timestamp;
            this.runningOptions = runningOptions;
            this.mailboxesExpired = mailboxesExpired;
            this.mailboxesFailed = mailboxesFailed;
            this.mailboxesProcessed = mailboxesProcessed;
            this.messagesDeleted = messagesDeleted;
        }

        public RunningOptions getRunningOptions() {
            return runningOptions;
        }

        public long getMailboxesExpired() {
            return mailboxesExpired;
        }

        public long getMailboxesFailed() {
            return mailboxesFailed;
        }

        public long getMailboxesProcessed() {
            return mailboxesProcessed;
        }

        public long getMessagesDeleted() {
            return messagesDeleted;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }
    }

    private final ExpireMailboxService expireMailboxService;
    private final Context context;
    private final RunningOptions runningOptions;

    @Inject
    public ExpireMailboxTask(ExpireMailboxService expireMailboxService,
                             RunningOptions runningOptions) {
        this.expireMailboxService = expireMailboxService;
        this.context = new Context();
        this.runningOptions = runningOptions;
    }

    @Override
    public Publisher<Result> runAsync() {
        return expireMailboxService.expireMailboxes(context, runningOptions, new Date());
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(ExpireMailboxTask.AdditionalInformation.from(context, runningOptions));
    }

    public RunningOptions getRunningOptions() {
        return runningOptions;
    }
}
