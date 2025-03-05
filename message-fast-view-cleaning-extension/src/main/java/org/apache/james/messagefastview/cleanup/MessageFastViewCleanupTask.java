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

package org.apache.james.messagefastview.cleanup;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.apache.james.messagefastview.cleanup.MessageFastViewCleanupService.Context;
import org.apache.james.messagefastview.cleanup.MessageFastViewCleanupService.Context.Snapshot;
import org.apache.james.messagefastview.cleanup.MessageFastViewCleanupService.RunningOptions;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

public class MessageFastViewCleanupTask implements Task {
    static final TaskType CLEANUP_MESSAGE_FAST_VIEW = TaskType.of("cleanup-message-fast-view");

    public static class Details implements TaskExecutionDetails.AdditionalInformation {
        private final Instant instant;
        private final long deletedMessageFastViews;
        private final RunningOptions runningOptions;

        Details(Instant instant, long deletedMessageFastViews, RunningOptions runningOptions) {
            this.instant = instant;
            this.deletedMessageFastViews = deletedMessageFastViews;
            this.runningOptions = runningOptions;
        }

        @Override
        public Instant timestamp() {
            return instant;
        }

        public long getDeletedMessageFastViews() {
            return deletedMessageFastViews;
        }

        public RunningOptions getRunningOptions() {
            return runningOptions;
        }
    }

    private final MessageFastViewCleanupService service;
    private final RunningOptions runningOptions;
    private final Context context;

    public MessageFastViewCleanupTask(MessageFastViewCleanupService service, RunningOptions runningOptions) {
        this.service = service;
        this.runningOptions = runningOptions;
        this.context = new Context();
    }

    @Override
    public Task.Result run() {
        return service.cleanup(context, runningOptions)
            .block();
    }

    @Override
    public TaskType type() {
        return CLEANUP_MESSAGE_FAST_VIEW;
    }

    public RunningOptions getRunningOptions() {
        return runningOptions;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        Snapshot snapshot = context.snapshot();

        return Optional.of(new Details(Clock.systemUTC().instant(),
            snapshot.getDeletedMessageFastViewCount(),
            runningOptions));
    }
}
