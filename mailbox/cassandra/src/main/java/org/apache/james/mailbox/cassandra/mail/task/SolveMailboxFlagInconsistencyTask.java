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

import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.task.SolveMailboxFlagInconsistenciesService.Context;
import org.apache.james.mailbox.cassandra.mail.task.SolveMailboxFlagInconsistenciesService.TargetFlag;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.google.common.collect.ImmutableList;

public class SolveMailboxFlagInconsistencyTask implements Task {

    public record Details(Instant timestamp,
                          long processedMailboxEntries,
                          ImmutableList<String> errors,
                          String targetFlag) implements TaskExecutionDetails.AdditionalInformation {
    }

    public static final String TYPE = "solve-mailbox-flag-inconsistencies";
    public static final TaskType TASK_TYPE = TaskType.of(TYPE);

    public SolveMailboxFlagInconsistencyTask(SolveMailboxFlagInconsistenciesService service, String targetFlag) {
        this(service, Optional.ofNullable(TargetFlag.from(targetFlag))
            .orElseThrow(() -> new IllegalArgumentException("Invalid target flag: " + targetFlag)));
    }

    public SolveMailboxFlagInconsistencyTask(SolveMailboxFlagInconsistenciesService service, TargetFlag targetFlag) {
        this.context = new Context();
        this.service = service;
        this.targetFlag = targetFlag;
    }

    private final Context context;
    private final SolveMailboxFlagInconsistenciesService service;
    private final TargetFlag targetFlag;

    @Override
    public Result run() throws InterruptedException {
        return service.fixInconsistencies(context, targetFlag).block();
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        Context.Snapshot snapshot = context.snapshot();

        return Optional.of(new Details(Clock.systemUTC().instant(),
            snapshot.processedMailboxEntries(),
            ImmutableList.copyOf(snapshot.errors().stream()
                .map(CassandraId::serialize).toList()),
                targetFlag.name()));
    }

    public String targetFlag() {
        return targetFlag.name();
    }
}
