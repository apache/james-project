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

package org.apache.james.mailbox.cassandra.mail.task;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.apache.james.mailbox.cassandra.mail.task.SolveMessageInconsistenciesService.Context;
import org.apache.james.mailbox.cassandra.mail.task.SolveMessageInconsistenciesService.Context.Snapshot;
import org.apache.james.mailbox.cassandra.mail.task.SolveMessageInconsistenciesService.RunningOptions;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public class SolveMessageInconsistenciesTask implements Task {

    static final TaskType SOLVE_MESSAGE_INCONSISTENCIES = TaskType.of("solve-message-inconsistencies");

    public static class Details implements TaskExecutionDetails.AdditionalInformation {
        private final Instant instant;
        private final long processedImapUidEntries;
        private final long processedMessageIdEntries;
        private final long addedMessageIdEntries;
        private final long updatedMessageIdEntries;
        private final long removedMessageIdEntries;
        private final RunningOptions runningOptions;
        private final ImmutableList<MessageInconsistenciesEntry> fixedInconsistencies;
        private final ImmutableList<MessageInconsistenciesEntry> errors;

        public Details(Instant instant, long processedImapUidEntries, long processedMessageIdEntries,
                       long addedMessageIdEntries, long updatedMessageIdEntries, long removedMessageIdEntries, RunningOptions runningOptions,
                       ImmutableList<MessageInconsistenciesEntry> fixedInconsistencies, ImmutableList<MessageInconsistenciesEntry> errors) {
            this.instant = instant;
            this.processedImapUidEntries = processedImapUidEntries;
            this.processedMessageIdEntries = processedMessageIdEntries;
            this.addedMessageIdEntries = addedMessageIdEntries;
            this.updatedMessageIdEntries = updatedMessageIdEntries;
            this.removedMessageIdEntries = removedMessageIdEntries;
            this.runningOptions = runningOptions;
            this.fixedInconsistencies = fixedInconsistencies;
            this.errors = errors;
        }

        @Override
        public Instant timestamp() {
            return instant;
        }

        public long getProcessedImapUidEntries() {
            return processedImapUidEntries;
        }

        public long getProcessedMessageIdEntries() {
            return processedMessageIdEntries;
        }

        public long getAddedMessageIdEntries() {
            return addedMessageIdEntries;
        }

        public long getUpdatedMessageIdEntries() {
            return updatedMessageIdEntries;
        }

        public long getRemovedMessageIdEntries() {
            return removedMessageIdEntries;
        }

        public RunningOptions getRunningOptions() {
            return runningOptions;
        }

        public ImmutableList<MessageInconsistenciesEntry> getFixedInconsistencies() {
            return fixedInconsistencies;
        }

        public ImmutableList<MessageInconsistenciesEntry> getErrors() {
            return errors;
        }
    }

    private final SolveMessageInconsistenciesService service;
    private Context context;
    private RunningOptions runningOptions;

    public SolveMessageInconsistenciesTask(SolveMessageInconsistenciesService service, RunningOptions runningOptions) {
        this.service = service;
        this.runningOptions = runningOptions;
        this.context = new Context();
    }

    @Override
    public Result run() {
        return service.fixMessageInconsistencies(context, runningOptions)
            .block();
    }

    @Override
    public TaskType type() {
        return SOLVE_MESSAGE_INCONSISTENCIES;
    }

    public RunningOptions getRunningOptions() {
        return this.runningOptions;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        Snapshot snapshot = context.snapshot();
        return Optional.of(new Details(Clock.systemUTC().instant(), snapshot.getProcessedImapUidEntries(), snapshot.getProcessedMessageIdEntries(),
            snapshot.getAddedMessageIdEntries(), snapshot.getUpdatedMessageIdEntries(), snapshot.getRemovedMessageIdEntries(), runningOptions,
            snapshot.getFixedInconsistencies().stream()
                .map(this::toMessageInconsistenciesEntry)
                .collect(Guavate.toImmutableList()),
            snapshot.getErrors().stream()
                .map(this::toMessageInconsistenciesEntry)
                .collect(Guavate.toImmutableList())));
    }

    private MessageInconsistenciesEntry toMessageInconsistenciesEntry(ComposedMessageId composedMessageId) {
        return MessageInconsistenciesEntry.builder()
            .mailboxId(composedMessageId.getMailboxId().serialize())
            .messageId(composedMessageId.getMessageId().serialize())
            .messageUid(composedMessageId.getUid().asLong());
    }
}
