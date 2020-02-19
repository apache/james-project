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

import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public class SolveMailboxInconsistenciesTask implements Task {
    static final TaskType SOLVE_MAILBOX_INCONSISTENCIES = TaskType.of("solve-mailbox-inconsistencies");
    public static final Logger LOGGER = LoggerFactory.getLogger(SolveMailboxInconsistenciesTask.class);
    private SolveMailboxInconsistenciesService.Context context;

    public static class Details implements TaskExecutionDetails.AdditionalInformation {
        private final Instant instant;
        private final long processedMailboxEntries;
        private final long processedMailboxPathEntries;
        private final ImmutableList<String> fixedInconsistencies;
        private final ImmutableList<ConflictingEntry> conflictingEntries;
        private final long errors;

        Details(Instant instant, long processedMailboxEntries, long processedMailboxPathEntries, ImmutableList<String> fixedInconsistencies,
                ImmutableList<ConflictingEntry> conflictingEntries, long errors) {
            this.instant = instant;
            this.processedMailboxEntries = processedMailboxEntries;
            this.processedMailboxPathEntries = processedMailboxPathEntries;
            this.fixedInconsistencies = fixedInconsistencies;
            this.conflictingEntries = conflictingEntries;
            this.errors = errors;
        }

        @Override
        public Instant timestamp() {
            return instant;
        }

        @JsonProperty("processedMailboxEntries")
        long getProcessedMailboxEntries() {
            return processedMailboxEntries;
        }

        @JsonProperty("processedMailboxPathEntries")
        long getProcessedMailboxPathEntries() {
            return processedMailboxPathEntries;
        }

        @JsonProperty("fixedInconsistencies")
        ImmutableList<String> getFixedInconsistencies() {
            return fixedInconsistencies;
        }

        @JsonProperty("conflictingEntries")
        ImmutableList<ConflictingEntry> getConflictingEntries() {
            return conflictingEntries;
        }

        @JsonProperty("errors")
        long getErrors() {
            return errors;
        }
    }

    private final SolveMailboxInconsistenciesService service;

    public SolveMailboxInconsistenciesTask(SolveMailboxInconsistenciesService service) {
        this.service = service;
        this.context = new SolveMailboxInconsistenciesService.Context();
    }

    @Override
    public Result run() {
        return service.fixMailboxInconsistencies(context)
            .block();
    }

    @Override
    public TaskType type() {
        return SOLVE_MAILBOX_INCONSISTENCIES;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        SolveMailboxInconsistenciesService.Context.Snapshot snapshot = context.snapshot();
        return Optional.of(new Details(Clock.systemUTC().instant(),
            snapshot.getProcessedMailboxEntries(),
            snapshot.getProcessedMailboxPathEntries(),
            snapshot.getFixedInconsistencies().stream()
                .map(MailboxId::serialize)
                .collect(Guavate.toImmutableList()),
            snapshot.getConflictingEntries(),
            snapshot.getErrors()));
    }
}
