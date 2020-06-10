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

import org.apache.james.core.Username;
import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.mailbox.tools.indexer.ReprocessingContextInformationDTO.ReindexingFailureDTO;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public class UserReindexingTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    public static AdditionalInformationDTOModule<UserReindexingTask.AdditionalInformation, UserReindexingTaskAdditionalInformationDTO> module(MailboxId.Factory factory) {
        return DTOModule.forDomainObject(UserReindexingTask.AdditionalInformation.class)
            .convertToDTO(UserReindexingTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(dto -> new UserReindexingTask.AdditionalInformation(Username.of(dto.getUser()),
                dto.getSuccessfullyReprocessedMailCount(),
                dto.getFailedReprocessedMailCount(),
                ReprocessingContextInformationDTO.deserializeFailures(factory, dto.getMessageFailures(), dto.getMailboxFailures().orElse(ImmutableList.of())),
                dto.getTimestamp(),
                dto.getRunningOptions()
                    .map(RunningOptionsDTO::toDomainObject)
                    .orElse(ReIndexer.RunningOptions.DEFAULT)
                ))
            .toDTOConverter((details, type) -> new UserReindexingTaskAdditionalInformationDTO(
                type,
                details.getUsername(),
                details.getSuccessfullyReprocessedMailCount(),
                details.getFailedReprocessedMailCount(),
                Optional.empty(),
                Optional.of(ReprocessingContextInformationDTO.serializeFailures(details.failures())),
                Optional.of(details.failures().mailboxFailures().stream().map(MailboxId::serialize).collect(Guavate.toImmutableList())),
                details.timestamp(),
                Optional.of(RunningOptionsDTO.toDTO(details.getRunningOptions()))))
            .typeName(UserReindexingTask.USER_RE_INDEXING.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private final ReprocessingContextInformationDTO reprocessingContextInformationDTO;
    private final String user;

    @JsonCreator
    private UserReindexingTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                       @JsonProperty("user") String user,
                                                       @JsonProperty("successfullyReprocessedMailCount") int successfullyReprocessedMailCount,
                                                       @JsonProperty("failedReprocessedMailCount") int failedReprocessedMailCount,
                                                       @JsonProperty("failures") Optional<List<ReindexingFailureDTO>> failures,
                                                       @JsonProperty("messageFailures") Optional<List<ReindexingFailureDTO>> messageFailures,
                                                       @JsonProperty("mailboxFailures") Optional<List<String>> mailboxFailures,
                                                       @JsonProperty("timestamp") Instant timestamp,
                                                       @JsonProperty("runningOptions") Optional<RunningOptionsDTO> runningOptions
                                                       ) {
        this.user = user;
        this.reprocessingContextInformationDTO = new ReprocessingContextInformationDTO(type,
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

    public String getUser() {
        return user;
    }

    public int getSuccessfullyReprocessedMailCount() {
        return reprocessingContextInformationDTO.getSuccessfullyReprocessedMailCount();
    }

    public int getFailedReprocessedMailCount() {
        return reprocessingContextInformationDTO.getFailedReprocessedMailCount();
    }

    public List<ReindexingFailureDTO> getMessageFailures() {
        return reprocessingContextInformationDTO.getMessageFailures();
    }

    public Optional<List<String>> getMailboxFailures() {
        return reprocessingContextInformationDTO.getMailboxFailures();
    }

    public Optional<RunningOptionsDTO> getRunningOptions() {
        return reprocessingContextInformationDTO.getRunningOptions();
    }
}
