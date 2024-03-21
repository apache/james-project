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
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.indexer.ReIndexer.RunningOptions;
import org.apache.james.mailbox.indexer.ReIndexingExecutionFailures;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.google.common.collect.ImmutableList;

public class ErrorRecoveryIndexationTask implements Task {
    public static final TaskType PREVIOUS_FAILURES_INDEXING = TaskType.of("error-recovery-indexation");

    public static class Factory {

        private final ReIndexerPerformer reIndexerPerformer;
        private final MailboxId.Factory mailboxIdFactory;

        @Inject
        public Factory(ReIndexerPerformer reIndexerPerformer, MailboxId.Factory mailboxIdFactory) {
            this.reIndexerPerformer = reIndexerPerformer;
            this.mailboxIdFactory = mailboxIdFactory;
        }

        private List<ReIndexingExecutionFailures.ReIndexingFailure> messageFailuresFromDTO(List<ErrorRecoveryIndexationTaskDTO.ReindexingFailureDTO> messageFailures) {
            return messageFailures
                .stream()
                .flatMap(dto -> dto.getUids()
                    .stream()
                    .map(uid -> new ReIndexingExecutionFailures.ReIndexingFailure(mailboxIdFactory.fromString(dto.getMailboxId()), MessageUid.of(uid))))
                .collect(ImmutableList.toImmutableList());
        }

        private List<MailboxId> mailboxFailuresFromDTO(Optional<List<String>> mailboxFailures) {
            return mailboxFailures.map(mailboxIdList ->
                    mailboxIdList.stream()
                        .map(mailboxIdFactory::fromString)
                        .collect(ImmutableList.toImmutableList()))
                .orElse(ImmutableList.of());
        }

        public ErrorRecoveryIndexationTask create(ErrorRecoveryIndexationTaskDTO dto) {
            return new ErrorRecoveryIndexationTask(reIndexerPerformer,
                new ReIndexingExecutionFailures(messageFailuresFromDTO(dto.getPreviousMessageFailures()),
                    mailboxFailuresFromDTO(dto.getPreviousMailboxFailures())),
                dto.getRunningOptions()
                    .map(RunningOptionsDTO::toDomainObject)
                    .orElse(RunningOptions.DEFAULT));
        }
    }

    private final ReIndexerPerformer reIndexerPerformer;
    private final ReIndexingContext reIndexingContext;
    private final ReIndexingExecutionFailures previousFailures;
    private final RunningOptions runningOptions;

    public ErrorRecoveryIndexationTask(ReIndexerPerformer reIndexerPerformer, ReIndexingExecutionFailures previousFailures, RunningOptions runningOptions) {
        this.reIndexerPerformer = reIndexerPerformer;
        this.previousFailures = previousFailures;
        this.reIndexingContext = new ReIndexingContext();
        this.runningOptions = runningOptions;
    }

    @Override
    public Result run() {
        return reIndexerPerformer.reIndexErrors(reIndexingContext, previousFailures, runningOptions).block();
    }

    @Override
    public TaskType type() {
        return PREVIOUS_FAILURES_INDEXING;
    }

    public ReIndexingExecutionFailures getPreviousFailures() {
        return previousFailures;
    }

    public RunningOptions getRunningOptions() {
        return runningOptions;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(new ReIndexingContextInformationDTO.ReIndexingContextInformationForErrorRecoveryIndexationTask(
            reIndexingContext.successfullyReprocessedMailCount(),
            reIndexingContext.failedReprocessingMailCount(),
            reIndexingContext.failures(),
            Clock.systemUTC().instant(),
            runningOptions));
    }
}
