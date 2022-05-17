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

package org.apache.james.webadmin.service;

import java.time.Instant;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.webadmin.service.ExpireMailboxService.RunningOptions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ExpireMailboxAdditionalInformationDTO implements AdditionalInformationDTO {

    public static AdditionalInformationDTOModule<ExpireMailboxTask.AdditionalInformation, ExpireMailboxAdditionalInformationDTO> module() {
        return DTOModule.forDomainObject(ExpireMailboxTask.AdditionalInformation.class)
            .convertToDTO(ExpireMailboxAdditionalInformationDTO.class)
            .toDomainObjectConverter(ExpireMailboxAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(ExpireMailboxAdditionalInformationDTO::toDto)
            .typeName(ExpireMailboxTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private static ExpireMailboxTask.AdditionalInformation toDomainObject(ExpireMailboxAdditionalInformationDTO dto) {
        return new ExpireMailboxTask.AdditionalInformation(
            dto.getTimestamp(),
            dto.getRunningOptions(),
            dto.getMailboxesExpired(),
            dto.getMailboxesFailed(),
            dto.getMailboxesProcessed(),
            dto.getMessagesDeleted());
    }

    private static ExpireMailboxAdditionalInformationDTO toDto(ExpireMailboxTask.AdditionalInformation details, String type) {
        return new ExpireMailboxAdditionalInformationDTO(
            details.timestamp(),
            type,
            details.getRunningOptions(),
            details.getMailboxesExpired(),
            details.getMailboxesFailed(),
            details.getMailboxesProcessed(),
            details.getMessagesDeleted());
    }

    private final Instant timestamp;
    private final String type;
    private final RunningOptions runningOptions;
    private final long mailboxesExpired;
    private final long mailboxesFailed;
    private final long mailboxesProcessed;
    private final long messagesDeleted;

    @JsonCreator
    public ExpireMailboxAdditionalInformationDTO(@JsonProperty("timestamp") Instant timestamp,
                                                 @JsonProperty("type") String type,
                                                 @JsonProperty("runningOptions") RunningOptions runningOptions,
                                                 @JsonProperty("mailboxesExpired") long mailboxesExpired,
                                                 @JsonProperty("mailboxesFailed") long mailboxesFailed,
                                                 @JsonProperty("mailboxesProcessed") long mailboxesProcessed,
                                                 @JsonProperty("messagesDeleted") long messagesDeleted) {
        this.timestamp = timestamp;
        this.type = type;
        this.runningOptions = runningOptions;
        this.mailboxesExpired = mailboxesExpired;
        this.mailboxesFailed = mailboxesFailed;
        this.mailboxesProcessed = mailboxesProcessed;
        this.messagesDeleted = messagesDeleted;
    }

    public RunningOptions getRunningOptions() {
        return runningOptions;
    }

    public long getMailboxesExpired() {
        return mailboxesExpired;
    }

    public long getMailboxesFailed() {
        return mailboxesFailed;
    }

    public long getMailboxesProcessed() {
        return mailboxesProcessed;
    }

    public long getMessagesDeleted() {
        return messagesDeleted;
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
