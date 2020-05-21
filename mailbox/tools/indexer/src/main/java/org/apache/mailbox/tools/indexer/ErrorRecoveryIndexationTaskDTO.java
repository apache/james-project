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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.indexer.ReIndexingExecutionFailures;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.Multimap;

public class ErrorRecoveryIndexationTaskDTO implements TaskDTO {

    public static TaskDTOModule<ErrorRecoveryIndexationTask, ErrorRecoveryIndexationTaskDTO> module(ErrorRecoveryIndexationTask.Factory factory) {
        return DTOModule
            .forDomainObject(ErrorRecoveryIndexationTask.class)
            .convertToDTO(ErrorRecoveryIndexationTaskDTO.class)
            .toDomainObjectConverter(factory::create)
            .toDTOConverter(ErrorRecoveryIndexationTaskDTO::of)
            .typeName(ErrorRecoveryIndexationTask.PREVIOUS_FAILURES_INDEXING.asString())
            .withFactory(TaskDTOModule::new);
    }

    public static ErrorRecoveryIndexationTaskDTO of(ErrorRecoveryIndexationTask task, String type) {
        Multimap<MailboxId, ReIndexingExecutionFailures.ReIndexingFailure> failuresByMailboxId = task.getPreviousFailures()
            .failures()
            .stream()
            .collect(Guavate.toImmutableListMultimap(ReIndexingExecutionFailures.ReIndexingFailure::getMailboxId, Function.identity()));

        List<ReindexingFailureDTO> failureDTOs = failuresByMailboxId.asMap()
            .entrySet()
            .stream()
            .map(ErrorRecoveryIndexationTaskDTO::failuresByMailboxToReindexingFailureDTO)
            .collect(Guavate.toImmutableList());
        return new ErrorRecoveryIndexationTaskDTO(type, failureDTOs, Optional.of(RunningOptionsDTO.toDTO(task.getRunningOptions())));
    }

    private static ReindexingFailureDTO failuresByMailboxToReindexingFailureDTO(Map.Entry<MailboxId,
        Collection<ReIndexingExecutionFailures.ReIndexingFailure>> entry) {
        List<Long> uids = entry
            .getValue()
            .stream()
            .map(ReIndexingExecutionFailures.ReIndexingFailure::getUid)
            .map(MessageUid::asLong)
            .collect(Guavate.toImmutableList());
        return new ReindexingFailureDTO(entry.getKey().serialize(), uids);
    }

    public static class ReindexingFailureDTO {

        private final String mailboxId;
        private final List<Long> uids;

        private ReindexingFailureDTO(@JsonProperty("mailboxId") String mailboxId, @JsonProperty("uids") List<Long> uids) {
            this.mailboxId = mailboxId;
            this.uids = uids;
        }

        public String getMailboxId() {
            return mailboxId;
        }

        public List<Long> getUids() {
            return uids;
        }
    }

    private final String type;
    private final List<ReindexingFailureDTO> previousFailures;
    private final Optional<RunningOptionsDTO> runningOptions;

    private ErrorRecoveryIndexationTaskDTO(@JsonProperty("type") String type,
                                           @JsonProperty("previousFailures") List<ReindexingFailureDTO> previousFailures,
                                           @JsonProperty("runningOptions") Optional<RunningOptionsDTO> runningOptions) {
        this.type = type;
        this.previousFailures = previousFailures;
        this.runningOptions = runningOptions;
    }

    @Override
    public String getType() {
        return type;
    }

    public List<ReindexingFailureDTO> getPreviousFailures() {
        return previousFailures;
    }

    public Optional<RunningOptionsDTO> getRunningOptions() {
        return runningOptions;
    }
}
