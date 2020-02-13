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

import java.time.Instant;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

public class SolveMailboxInconsistenciesTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    private static SolveMailboxInconsistenciesTaskAdditionalInformationDTO fromDomainObject(SolveMailboxInconsistenciesTask.Details details, String type) {
        return new SolveMailboxInconsistenciesTaskAdditionalInformationDTO(
            type,
            details.getProcessedMailboxEntries(),
            details.getProcessedMailboxPathEntries(),
            details.getFixedInconsistencies(),
            details.getConflictingEntries(),
            details.getErrors(),
            details.timestamp());
    }

    public static final AdditionalInformationDTOModule<SolveMailboxInconsistenciesTask.Details, SolveMailboxInconsistenciesTaskAdditionalInformationDTO> MODULE =
        DTOModule
            .forDomainObject(SolveMailboxInconsistenciesTask.Details.class)
            .convertToDTO(SolveMailboxInconsistenciesTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(SolveMailboxInconsistenciesTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(SolveMailboxInconsistenciesTaskAdditionalInformationDTO::fromDomainObject)
            .typeName(SolveMailboxInconsistenciesTask.SOLVE_MAILBOX_INCONSISTENCIES.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private final String type;
    private final long processedMailboxEntries;
    private final long processedMailboxPathEntries;
    private final long fixedInconsistencies;
    private final ImmutableList<ConflictingEntry> conflictingEntries;
    private final long errors;
    private final Instant timestamp;

    public SolveMailboxInconsistenciesTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                                   @JsonProperty("processedMailboxEntries") long processedMailboxEntries,
                                                                   @JsonProperty("processedMailboxPathEntries") long processedMailboxPathEntries,
                                                                   @JsonProperty("fixedInconsistencies") long fixedInconsistencies,
                                                                   @JsonProperty("conflictingEntries") ImmutableList<ConflictingEntry> conflictingEntries,
                                                                   @JsonProperty("errors") long errors,
                                                                   @JsonProperty("timestamp") Instant timestamp) {
        this.type = type;
        this.processedMailboxEntries = processedMailboxEntries;
        this.timestamp = timestamp;
        this.processedMailboxPathEntries = processedMailboxPathEntries;
        this.fixedInconsistencies = fixedInconsistencies;
        this.conflictingEntries = conflictingEntries;
        this.errors = errors;
    }

    public long getProcessedMailboxEntries() {
        return processedMailboxEntries;
    }

    public long getProcessedMailboxPathEntries() {
        return processedMailboxPathEntries;
    }

    public long getFixedInconsistencies() {
        return fixedInconsistencies;
    }

    public ImmutableList<ConflictingEntry> getConflictingEntries() {
        return conflictingEntries;
    }

    public long getErrors() {
        return errors;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String getType() {
        return type;
    }

    private SolveMailboxInconsistenciesTask.Details toDomainObject() {
        return new SolveMailboxInconsistenciesTask.Details(timestamp,
            processedMailboxEntries,
            processedMailboxPathEntries,
            fixedInconsistencies,
            conflictingEntries,
            errors);
    }
}
