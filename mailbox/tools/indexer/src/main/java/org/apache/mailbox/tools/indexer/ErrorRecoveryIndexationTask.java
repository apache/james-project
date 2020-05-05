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

import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.indexer.ReIndexingExecutionFailures;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.github.steveash.guavate.Guavate;

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

        private List<ReIndexingExecutionFailures.ReIndexingFailure> failuresFromDTO(List<ErrorRecoveryIndexationTaskDTO.ReindexingFailureDTO> failureDTOs) {
            return failureDTOs
                .stream()
                .flatMap(dto -> dto.getUids()
                    .stream()
                    .map(uid -> new ReIndexingExecutionFailures.ReIndexingFailure(mailboxIdFactory.fromString(dto.getMailboxId()), MessageUid.of(uid))))
                .collect(Guavate.toImmutableList());
        }

        public ErrorRecoveryIndexationTask create(ErrorRecoveryIndexationTaskDTO dto) {
            return new ErrorRecoveryIndexationTask(reIndexerPerformer, new ReIndexingExecutionFailures(failuresFromDTO(dto.getPreviousFailures())));
        }
    }

    private final ReIndexerPerformer reIndexerPerformer;
    private final ReprocessingContext reprocessingContext;
    private final ReIndexingExecutionFailures previousFailures;

    public ErrorRecoveryIndexationTask(ReIndexerPerformer reIndexerPerformer, ReIndexingExecutionFailures previousFailures) {
        this.reIndexerPerformer = reIndexerPerformer;
        this.previousFailures = previousFailures;
        this.reprocessingContext = new ReprocessingContext();
    }

    @Override
    public Result run() {
        return reIndexerPerformer.reIndex(reprocessingContext, previousFailures).block();
    }

    @Override
    public TaskType type() {
        return PREVIOUS_FAILURES_INDEXING;
    }

    public ReIndexingExecutionFailures getPreviousFailures() {
        return previousFailures;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(ReprocessingContextInformation.forErrorRecoveryIndexationTask(reprocessingContext));
    }
}
