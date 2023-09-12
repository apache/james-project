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

package org.apache.james.mailbox.quota.task;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.apache.james.mailbox.quota.task.RecomputeCurrentQuotasService.Context;
import org.apache.james.mailbox.quota.task.RecomputeCurrentQuotasService.Context.Snapshot;
import org.apache.james.mailbox.quota.task.RecomputeCurrentQuotasService.RunningOptions;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

public class RecomputeCurrentQuotasTask implements Task {
    static final TaskType RECOMPUTE_CURRENT_QUOTAS = TaskType.of("recompute-current-quotas");

    public static class Details implements TaskExecutionDetails.AdditionalInformation {
        private final Instant instant;
        private final List<RecomputeSingleQuotaComponentResult> recomputeSingleQuotaComponentResults;
        private final RunningOptions runningOptions;

        Details(Instant instant, List<RecomputeSingleQuotaComponentResult> recomputeSingleQuotaComponentResults, RunningOptions runningOptions) {
            this.instant = instant;
            this.recomputeSingleQuotaComponentResults = recomputeSingleQuotaComponentResults;
            this.runningOptions = runningOptions;
        }

        @Override
        public Instant timestamp() {
            return instant;
        }

        public List<RecomputeSingleQuotaComponentResult> getResults() {
            return recomputeSingleQuotaComponentResults;
        }

        public RunningOptions getRunningOptions() {
            return runningOptions;
        }
    }

    private final RecomputeCurrentQuotasService service;
    private final RunningOptions runningOptions;
    private final Context context;

    public RecomputeCurrentQuotasTask(RecomputeCurrentQuotasService service, RunningOptions runningOptions) {
        this.service = service;
        this.runningOptions = runningOptions;
        this.context = new Context();
    }

    @Override
    public Task.Result run() {
        return service.recomputeCurrentQuotas(context, runningOptions)
            .block();
    }

    @Override
    public TaskType type() {
        return RECOMPUTE_CURRENT_QUOTAS;
    }

    public RunningOptions getRunningOptions() {
        return runningOptions;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        Snapshot snapshot = context.snapshot();

        return Optional.of(new Details(Clock.systemUTC().instant(),
            snapshot.getResults(),
            runningOptions));
    }
}
