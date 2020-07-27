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
package org.apache.james.webadmin.dto;

import java.time.Instant;
import java.util.List;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.indexer.ReIndexingExecutionFailures;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.mailbox.tools.indexer.ErrorRecoveryIndexationTask;
import org.apache.mailbox.tools.indexer.FullReindexingTask;
import org.apache.mailbox.tools.indexer.ReprocessingContextInformationDTO;
import org.apache.mailbox.tools.indexer.RunningOptionsDTO;

import com.github.steveash.guavate.Guavate;

public class WebAdminReprocessingContextInformationDTO implements AdditionalInformationDTO {
    public static class WebAdminErrorRecoveryIndexationDTO extends WebAdminReprocessingContextInformationDTO {
        public static AdditionalInformationDTOModule<ReprocessingContextInformationDTO.ReprocessingContextInformationForErrorRecoveryIndexationTask, WebAdminErrorRecoveryIndexationDTO> serializationModule(MailboxId.Factory mailboxIdFactory) {
            return DTOModule.forDomainObject(ReprocessingContextInformationDTO.ReprocessingContextInformationForErrorRecoveryIndexationTask.class)
                .convertToDTO(WebAdminErrorRecoveryIndexationDTO.class)
                .toDomainObjectConverter(dto -> {
                    throw new NotImplementedException("Deserialization not implemented for this DTO");
                })
                .toDTOConverter((details, type) -> new WebAdminErrorRecoveryIndexationDTO(
                    type,
                    RunningOptionsDTO.toDTO(details.getRunningOptions()),
                    details.getSuccessfullyReprocessedMailCount(),
                    details.getFailedReprocessedMailCount(),
                    details.failures(),
                    details.timestamp()))
                .typeName(ErrorRecoveryIndexationTask.PREVIOUS_FAILURES_INDEXING.asString())
                .withFactory(AdditionalInformationDTOModule::new);
        }

        WebAdminErrorRecoveryIndexationDTO(String type, RunningOptionsDTO runningOptionsDTO, int successfullyReprocessedMailCount, int failedReprocessedMailCount,
                                           ReIndexingExecutionFailures failures, Instant timestamp) {
            super(type, runningOptionsDTO, successfullyReprocessedMailCount, failedReprocessedMailCount, failures, timestamp);
        }
    }

    public static class WebAdminFullIndexationDTO extends WebAdminReprocessingContextInformationDTO {
        public static AdditionalInformationDTOModule<ReprocessingContextInformationDTO.ReprocessingContextInformationForFullReindexingTask, WebAdminFullIndexationDTO> serializationModule(MailboxId.Factory mailboxIdFactory) {
            return DTOModule.forDomainObject(ReprocessingContextInformationDTO.ReprocessingContextInformationForFullReindexingTask.class)
                .convertToDTO(WebAdminFullIndexationDTO.class)
                .toDomainObjectConverter(dto -> {
                    throw new NotImplementedException("Deserialization not implemented for this DTO");
                })
                .toDTOConverter((details, type) -> new WebAdminFullIndexationDTO(
                    type,
                    RunningOptionsDTO.toDTO(details.getRunningOptions()),
                    details.getSuccessfullyReprocessedMailCount(),
                    details.getFailedReprocessedMailCount(),
                    details.failures(),
                    details.timestamp()))
                .typeName(FullReindexingTask.FULL_RE_INDEXING.asString())
                .withFactory(AdditionalInformationDTOModule::new);
        }

        WebAdminFullIndexationDTO(String type, RunningOptionsDTO runningOptions, int successfullyReprocessedMailCount, int failedReprocessedMailCount,
                                  ReIndexingExecutionFailures failures, Instant timestamp) {
            super(type, runningOptions, successfullyReprocessedMailCount, failedReprocessedMailCount, failures, timestamp);
        }
    }

    protected final String type;
    protected final RunningOptionsDTO runningOptions;
    protected final int successfullyReprocessedMailCount;
    protected final int failedReprocessedMailCount;
    protected final SerializableReIndexingExecutionFailures messageFailures;
    private final List<String> mailboxFailures;
    protected final Instant timestamp;

    WebAdminReprocessingContextInformationDTO(String type, RunningOptionsDTO runningOptions, int successfullyReprocessedMailCount, int failedReprocessedMailCount,
                                              ReIndexingExecutionFailures failures,
                                              Instant timestamp) {
        this.type = type;
        this.runningOptions = runningOptions;
        this.successfullyReprocessedMailCount = successfullyReprocessedMailCount;
        this.failedReprocessedMailCount = failedReprocessedMailCount;
        this.messageFailures = SerializableReIndexingExecutionFailures.from(failures);
        this.mailboxFailures = failures.mailboxFailures().stream()
            .map(MailboxId::serialize)
            .collect(Guavate.toImmutableList());
        this.timestamp = timestamp;
    }

    public RunningOptionsDTO getRunningOptions() {
        return runningOptions;
    }

    public int getSuccessfullyReprocessedMailCount() {
        return successfullyReprocessedMailCount;
    }

    public int getFailedReprocessedMailCount() {
        return failedReprocessedMailCount;
    }

    public SerializableReIndexingExecutionFailures getMessageFailures() {
        return messageFailures;
    }

    public List<String> getMailboxFailures() {
        return mailboxFailures;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String getType() {
        return type;
    }
}
