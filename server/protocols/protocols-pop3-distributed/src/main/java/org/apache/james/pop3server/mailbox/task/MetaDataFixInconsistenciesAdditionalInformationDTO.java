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

package org.apache.james.pop3server.mailbox.task;

import java.time.Instant;

import org.apache.james.json.DTOModule;
import org.apache.james.pop3server.mailbox.task.MetaDataFixInconsistenciesService.RunningOptions;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

public class MetaDataFixInconsistenciesAdditionalInformationDTO implements AdditionalInformationDTO {

    public static AdditionalInformationDTOModule<MetaDataFixInconsistenciesTask.AdditionalInformation, MetaDataFixInconsistenciesAdditionalInformationDTO> module() {
        return DTOModule.forDomainObject(MetaDataFixInconsistenciesTask.AdditionalInformation.class)
            .convertToDTO(MetaDataFixInconsistenciesAdditionalInformationDTO.class)
            .toDomainObjectConverter(MetaDataFixInconsistenciesAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(MetaDataFixInconsistenciesAdditionalInformationDTO::toDto)
            .typeName(MetaDataFixInconsistenciesTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private static MetaDataFixInconsistenciesTask.AdditionalInformation toDomainObject(MetaDataFixInconsistenciesAdditionalInformationDTO dto) {
        return new MetaDataFixInconsistenciesTask.AdditionalInformation(
            dto.getTimestamp(),
            dto.getRunningOptions(),
            dto.getProcessedImapUidEntries(),
            dto.getProcessedPop3MetaDataStoreEntries(),
            dto.getStalePOP3Entries(),
            dto.getMissingPOP3Entries(),
            dto.getFixedInconsistencies(),
            dto.getErrors());
    }

    private static MetaDataFixInconsistenciesAdditionalInformationDTO toDto(MetaDataFixInconsistenciesTask.AdditionalInformation details, String type) {
        return new MetaDataFixInconsistenciesAdditionalInformationDTO(
            details.timestamp(),
            type,
            details.getRunningOptions(),
            details.getProcessedImapUidEntries(),
            details.getProcessedPop3MetaDataStoreEntries(),
            details.getStalePOP3Entries(),
            details.getMissingPOP3Entries(),
            details.getFixedInconsistencies(),
            details.getErrors());
    }

    private final Instant timestamp;
    private final String type;
    private final RunningOptions runningOptions;
    private final long processedImapUidEntries;
    private final long processedPop3MetaDataStoreEntries;
    private final long stalePOP3Entries;
    private final long missingPOP3Entries;
    private final ImmutableList<MessageInconsistenciesEntry> fixedInconsistencies;
    private final ImmutableList<MessageInconsistenciesEntry> errors;

    @JsonCreator
    public MetaDataFixInconsistenciesAdditionalInformationDTO(@JsonProperty("timestamp") Instant timestamp,
                                                              @JsonProperty("type") String type,
                                                              @JsonProperty("runningOptions") RunningOptions runningOptions,
                                                              @JsonProperty("processedImapUidEntries") long processedImapUidEntries,
                                                              @JsonProperty("processedPop3MetaDataStoreEntries") long processedPop3MetaDataStoreEntries,
                                                              @JsonProperty("stalePOP3Entries") long stalePOP3Entries,
                                                              @JsonProperty("missingPOP3Entries") long missingPOP3Entries,
                                                              @JsonProperty("fixedInconsistencies") ImmutableList<MessageInconsistenciesEntry> fixedInconsistencies,
                                                              @JsonProperty("errors") ImmutableList<MessageInconsistenciesEntry> errors) {
        this.timestamp = timestamp;
        this.type = type;
        this.runningOptions = runningOptions;
        this.processedImapUidEntries = processedImapUidEntries;
        this.processedPop3MetaDataStoreEntries = processedPop3MetaDataStoreEntries;
        this.stalePOP3Entries = stalePOP3Entries;
        this.missingPOP3Entries = missingPOP3Entries;
        this.fixedInconsistencies = fixedInconsistencies;
        this.errors = errors;
    }

    public RunningOptions getRunningOptions() {
        return runningOptions;
    }

    public long getProcessedImapUidEntries() {
        return processedImapUidEntries;
    }

    public long getProcessedPop3MetaDataStoreEntries() {
        return processedPop3MetaDataStoreEntries;
    }

    public long getStalePOP3Entries() {
        return stalePOP3Entries;
    }

    public long getMissingPOP3Entries() {
        return missingPOP3Entries;
    }

    public ImmutableList<MessageInconsistenciesEntry> getFixedInconsistencies() {
        return fixedInconsistencies;
    }

    public ImmutableList<MessageInconsistenciesEntry> getErrors() {
        return errors;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }
}
