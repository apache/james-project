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

import static org.apache.james.mailbox.cassandra.mail.task.SolveMessageInconsistenciesTask.SOLVE_MESSAGE_INCONSISTENCIES;

import java.time.Instant;
import java.util.Optional;

import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.cassandra.mail.task.SolveMessageInconsistenciesService.RunningOptions;
import org.apache.james.mailbox.cassandra.mail.task.SolveMessageInconsistenciesTask.Details;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

public class SolveMessageInconsistenciesTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    private static SolveMessageInconsistenciesTaskAdditionalInformationDTO fromDomainObject(Details details, String type) {
        return new SolveMessageInconsistenciesTaskAdditionalInformationDTO(
            details.timestamp(),
            type,
            details.getProcessedImapUidEntries(),
            details.getProcessedMessageIdEntries(),
            details.getAddedMessageIdEntries(),
            details.getUpdatedMessageIdEntries(),
            details.getRemovedMessageIdEntries(),
            Optional.of(RunningOptionsDTO.asDTO(details.getRunningOptions())),
            details.getFixedInconsistencies(),
            details.getErrors());
    }

    public static AdditionalInformationDTOModule<Details, SolveMessageInconsistenciesTaskAdditionalInformationDTO> module() {
        return  DTOModule.forDomainObject(Details.class)
            .convertToDTO(SolveMessageInconsistenciesTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(SolveMessageInconsistenciesTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(SolveMessageInconsistenciesTaskAdditionalInformationDTO::fromDomainObject)
            .typeName(SOLVE_MESSAGE_INCONSISTENCIES.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private final Instant timestamp;
    private final String type;
    private final long processedImapUidEntries;
    private final long processedMessageIdEntries;
    private final long addedMessageIdEntries;
    private final long updatedMessageIdEntries;
    private final long removedMessageIdEntries;
    private final Optional<RunningOptionsDTO> runningOptions;
    private final ImmutableList<MessageInconsistenciesEntry> fixedInconsistencies;
    private final ImmutableList<MessageInconsistenciesEntry> errors;

    public SolveMessageInconsistenciesTaskAdditionalInformationDTO(@JsonProperty("timestamp") Instant timestamp, @JsonProperty("type") String type,
                                                                   @JsonProperty("processedImapUidEntries") long processedImapUidEntries,
                                                                   @JsonProperty("processedMessageIdEntries") long processedMessageIdEntries,
                                                                   @JsonProperty("addedMessageIdEntries") long addedMessageIdEntries,
                                                                   @JsonProperty("updatedMessageIdEntries") long updatedMessageIdEntries,
                                                                   @JsonProperty("removedMessageIdEntries")long removedMessageIdEntries,
                                                                   @JsonProperty("runningOptions") Optional<RunningOptionsDTO> runningOptions,
                                                                   @JsonProperty("fixedInconsistencies") ImmutableList<MessageInconsistenciesEntry> fixedInconsistencies,
                                                                   @JsonProperty("errors") ImmutableList<MessageInconsistenciesEntry> errors) {
        this.timestamp = timestamp;
        this.type = type;
        this.processedImapUidEntries = processedImapUidEntries;
        this.processedMessageIdEntries = processedMessageIdEntries;
        this.addedMessageIdEntries = addedMessageIdEntries;
        this.updatedMessageIdEntries = updatedMessageIdEntries;
        this.removedMessageIdEntries = removedMessageIdEntries;
        this.runningOptions = runningOptions;
        this.fixedInconsistencies = fixedInconsistencies;
        this.errors = errors;
    }

    public long getProcessedImapUidEntries() {
        return processedImapUidEntries;
    }

    public long getProcessedMessageIdEntries() {
        return processedMessageIdEntries;
    }

    public long getAddedMessageIdEntries() {
        return addedMessageIdEntries;
    }

    public long getUpdatedMessageIdEntries() {
        return updatedMessageIdEntries;
    }

    public long getRemovedMessageIdEntries() {
        return removedMessageIdEntries;
    }

    public Optional<RunningOptionsDTO> getRunningOptions() {
        return runningOptions;
    }

    public ImmutableList<MessageInconsistenciesEntry> getFixedInconsistencies() {
        return fixedInconsistencies;
    }

    public ImmutableList<MessageInconsistenciesEntry> getErrors() {
        return errors;
    }

    @Override
    public String getType() {
        return this.type;
    }

    @Override
    public Instant getTimestamp() {
        return this.timestamp;
    }

    private Details toDomainObject() {
        return new Details(timestamp,
            processedImapUidEntries,
            processedMessageIdEntries,
            addedMessageIdEntries,
            updatedMessageIdEntries,
            removedMessageIdEntries,
            runningOptions
                .map(RunningOptionsDTO::asDomainObject)
                .orElse(RunningOptions.DEFAULT),
            fixedInconsistencies,
            errors);
    }
}
