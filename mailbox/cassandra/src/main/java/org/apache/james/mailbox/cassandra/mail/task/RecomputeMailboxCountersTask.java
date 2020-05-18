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

import static org.apache.james.mailbox.cassandra.mail.task.RecomputeMailboxCountersService.Context.Snapshot;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

import reactor.core.scheduler.Schedulers;

public class RecomputeMailboxCountersTask implements Task {
    public static final Logger LOGGER = LoggerFactory.getLogger(RecomputeMailboxCountersTask.class);
    static final TaskType RECOMPUTE_MAILBOX_COUNTERS = TaskType.of("recompute-mailbox-counters");

    public static class Details implements TaskExecutionDetails.AdditionalInformation {
        private final Instant instant;
        private final long processedMailboxes;
        private final ImmutableList<String> failedMailboxes;

        Details(Instant instant, long processedMailboxes, ImmutableList<String> failedMailboxes) {
            this.instant = instant;
            this.processedMailboxes = processedMailboxes;
            this.failedMailboxes = failedMailboxes;
        }

        @Override
        public Instant timestamp() {
            return instant;
        }

        long getProcessedMailboxes() {
            return processedMailboxes;
        }

        ImmutableList<String> getFailedMailboxes() {
            return failedMailboxes;
        }
    }

    private final RecomputeMailboxCountersService service;
    private final RecomputeMailboxCountersService.Options options;
    private RecomputeMailboxCountersService.Context context;

    public RecomputeMailboxCountersTask(RecomputeMailboxCountersService service, RecomputeMailboxCountersService.Options options) {
        this.service = service;
        this.options = options;
        this.context = new RecomputeMailboxCountersService.Context();
    }

    @Override
    public Result run() {
        return service.recomputeMailboxCounters(context, options)
            .subscribeOn(Schedulers.elastic())
            .block();
    }

    @Override
    public TaskType type() {
        return RECOMPUTE_MAILBOX_COUNTERS;
    }

    public RecomputeMailboxCountersService.Options getOptions() {
        return options;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        Snapshot snapshot = context.snapshot();

        return Optional.of(new Details(Clock.systemUTC().instant(),
            snapshot.getProcessedMailboxCount(),
            snapshot.getFailedMailboxes().stream()
                .map(MailboxId::serialize)
                .collect(Guavate.toImmutableList())));
    }
}
