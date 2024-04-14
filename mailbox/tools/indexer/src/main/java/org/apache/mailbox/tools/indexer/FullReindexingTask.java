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

package org.apache.mailbox.tools.indexer;

import java.time.Clock;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.mailbox.indexer.ReIndexer.RunningOptions;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import reactor.core.publisher.Mono;

public class FullReindexingTask implements Task {

    public static final TaskType FULL_RE_INDEXING = TaskType.of("full-reindexing");

    private final ReIndexerPerformer reIndexerPerformer;
    private final ReIndexingContext reIndexingContext;
    private final RunningOptions runningOptions;

    @Inject
    public FullReindexingTask(ReIndexerPerformer reIndexerPerformer, RunningOptions runningOptions) {
        this.reIndexerPerformer = reIndexerPerformer;
        this.reIndexingContext = new ReIndexingContext();
        this.runningOptions = runningOptions;
    }

    @Override
    public Result run() {
        return reIndexerPerformer.reIndexAllMessages(reIndexingContext, runningOptions)
            .onErrorResume(e -> Mono.just(Result.PARTIAL))
            .block();
    }

    @Override
    public TaskType type() {
        return FULL_RE_INDEXING;
    }

    public RunningOptions getRunningOptions() {
        return runningOptions;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(new ReIndexingContextInformationDTO.ReIndexingContextInformationForFullReindexingTask(
            reIndexingContext.successfullyReprocessedMailCount(),
            reIndexingContext.failedReprocessingMailCount(),
            reIndexingContext.failures(),
            Clock.systemUTC().instant(),
            runningOptions));
    }
}
