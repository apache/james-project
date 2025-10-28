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

package org.apache.james.webadmin.data.jmap;

import java.time.Instant;

import org.apache.james.core.Username;
import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RunRulesOnMailboxTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    public static final AdditionalInformationDTOModule<RunRulesOnMailboxTask.AdditionalInformation, RunRulesOnMailboxTaskAdditionalInformationDTO> SERIALIZATION_MODULE =
        DTOModule.forDomainObject(RunRulesOnMailboxTask.AdditionalInformation.class)
            .convertToDTO(RunRulesOnMailboxTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(RunRulesOnMailboxTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(RunRulesOnMailboxTaskAdditionalInformationDTO::toDto)
            .typeName(RunRulesOnMailboxTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private static RunRulesOnMailboxTask.AdditionalInformation toDomainObject(RunRulesOnMailboxTaskAdditionalInformationDTO dto) {
        return new RunRulesOnMailboxTask.AdditionalInformation(
            Username.of(dto.getUsername()),
            MailboxPath.parseEscaped(dto.getMailboxPath()).orElseThrow(),
            dto.getTimestamp(),
            dto.getRulesOnMessagesApplySuccessfully(),
            dto.getRulesOnMessagesApplyFailed(),
            dto.getMaximumAppliedActionExceeded(),
            dto.getProcessedMessagesCount());
    }

    private static RunRulesOnMailboxTaskAdditionalInformationDTO toDto(RunRulesOnMailboxTask.AdditionalInformation domain, String type) {
        return new RunRulesOnMailboxTaskAdditionalInformationDTO(
            type,
            domain.getUsername().asString(),
            domain.getMailboxPath().asString(),
            domain.getTimestamp(),
            domain.getRulesOnMessagesApplySuccessfully(),
            domain.getRulesOnMessagesApplyFailed(),
            domain.maximumAppliedActionExceeded(),
            domain.getProcessedMessagesCount());
    }

    private final String type;
    private final String username;
    private final String mailboxPath;
    private final Instant timestamp;
    private final long rulesOnMessagesApplySuccessfully;
    private final long rulesOnMessagesApplyFailed;
    private final boolean maximumAppliedActionExceeded;
    private final long processedMessagesCount;

    public RunRulesOnMailboxTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                         @JsonProperty("username") String username,
                                                         @JsonProperty("mailboxPath") String mailboxPath,
                                                         @JsonProperty("timestamp") Instant timestamp,
                                                         @JsonProperty("rulesOnMessagesApplySuccessfully") long rulesOnMessagesApplySuccessfully,
                                                         @JsonProperty("rulesOnMessagesApplyFailed") long rulesOnMessagesApplyFailed,
                                                         @JsonProperty("maximumAppliedActionExceeded") boolean maximumAppliedActionExceeded,
                                                         @JsonProperty("processedMessagesCount") long processedMessagesCount) {
        this.type = type;
        this.username = username;
        this.mailboxPath = mailboxPath;
        this.timestamp = timestamp;
        this.rulesOnMessagesApplySuccessfully = rulesOnMessagesApplySuccessfully;
        this.rulesOnMessagesApplyFailed = rulesOnMessagesApplyFailed;
        this.maximumAppliedActionExceeded = maximumAppliedActionExceeded;
        this.processedMessagesCount = processedMessagesCount;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    public String getUsername() {
        return username;
    }

    public String getMailboxPath() {
        return mailboxPath;
    }

    public long getRulesOnMessagesApplySuccessfully() {
        return rulesOnMessagesApplySuccessfully;
    }

    public long getRulesOnMessagesApplyFailed() {
        return rulesOnMessagesApplyFailed;
    }

    public boolean getMaximumAppliedActionExceeded() {
        return maximumAppliedActionExceeded;
    }

    public long getProcessedMessagesCount() {
        return processedMessagesCount;
    }
}
