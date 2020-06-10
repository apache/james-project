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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.indexer.ReIndexer.RunningOptions;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public class SingleMailboxReindexingTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    public static AdditionalInformationDTOModule<SingleMailboxReindexingTask.AdditionalInformation, SingleMailboxReindexingTaskAdditionalInformationDTO> module(MailboxId.Factory factory) {
        return DTOModule.forDomainObject(SingleMailboxReindexingTask.AdditionalInformation.class)
            .convertToDTO(SingleMailboxReindexingTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(dto -> new SingleMailboxReindexingTask.AdditionalInformation(factory.fromString(dto.getMailboxId()),
                dto.getSuccessfullyReprocessedMailCount(),
                dto.getFailedReprocessedMailCount(),
                ReprocessingContextInformationDTO.deserializeFailures(factory, dto.getMessageFailures(), dto.getMailboxFailures().orElse(ImmutableList.of())),
                dto.getTimestamp(),
                dto.getRunningOptions()
                    .map(RunningOptionsDTO::toDomainObject)
                    .orElse(RunningOptions.DEFAULT)
                ))
            .toDTOConverter((details, type) -> new SingleMailboxReindexingTaskAdditionalInformationDTO(
                type,
                details.getMailboxId(),
                details.getSuccessfullyReprocessedMailCount(),
                details.getFailedReprocessedMailCount(),
                Optional.empty(),
                Optional.of(ReprocessingContextInformationDTO.serializeFailures(details.failures())),
                Optional.of(details.failures().mailboxFailures().stream().map(MailboxId::serialize).collect(Guavate.toImmutableList())),
                details.timestamp(),
                Optional.of(RunningOptionsDTO.toDTO(details.getRunningOptions()))
                ))
            .typeName(SingleMailboxReindexingTask.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private final ReprocessingContextInformationDTO reprocessingContextInformationDTO;
    private final String mailboxId;

    @JsonCreator
    private SingleMailboxReindexingTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                                @JsonProperty("mailboxId") String mailboxId,
                                                                @JsonProperty("successfullyReprocessedMailCount") int successfullyReprocessedMailCount,
                                                                @JsonProperty("failedReprocessedMailCount") int failedReprocessedMailCount,
                                                                @JsonProperty("failures") Optional<List<ReprocessingContextInformationDTO.ReindexingFailureDTO>> failures,
                                                                @JsonProperty("messageFailures") Optional<List<ReprocessingContextInformationDTO.ReindexingFailureDTO>> messageFailures,
                                                                @JsonProperty("mailboxFailures") Optional<List<String>> mailboxFailures,
                                                                @JsonProperty("timestamp") Instant timestamp,
                                                                @JsonProperty("runningOptions") Optional<RunningOptionsDTO> runningOptions) {
        this.mailboxId = mailboxId;
        this.reprocessingContextInformationDTO = new ReprocessingContextInformationDTO(
            type,
            successfullyReprocessedMailCount,
            failedReprocessedMailCount,
            failures,
            messageFailures,
            mailboxFailures,
            timestamp,
            runningOptions);
    }

    @Override
    public String getType() {
        return reprocessingContextInformationDTO.getType();
    }

    public Instant getTimestamp() {
        return reprocessingContextInformationDTO.getTimestamp();
    }

    public String getMailboxId() {
        return mailboxId;
    }

    public int getSuccessfullyReprocessedMailCount() {
        return reprocessingContextInformationDTO.getSuccessfullyReprocessedMailCount();
    }

    public int getFailedReprocessedMailCount() {
        return reprocessingContextInformationDTO.getFailedReprocessedMailCount();
    }

    public List<ReprocessingContextInformationDTO.ReindexingFailureDTO> getMessageFailures() {
        return reprocessingContextInformationDTO.getMessageFailures();
    }

    public Optional<List<String>> getMailboxFailures() {
        return reprocessingContextInformationDTO.getMailboxFailures();
    }

    public Optional<RunningOptionsDTO> getRunningOptions() {
        return reprocessingContextInformationDTO.getRunningOptions();
    }
}
