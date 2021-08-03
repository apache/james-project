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

import org.apache.james.core.Username;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.webadmin.validation.MailboxName;

public class ClearMailboxContentTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    public static final AdditionalInformationDTOModule<ClearMailboxContentTask.AdditionalInformation, ClearMailboxContentTaskAdditionalInformationDTO> SERIALIZATION_MODULE =
        DTOModule.forDomainObject(ClearMailboxContentTask.AdditionalInformation.class)
            .convertToDTO(ClearMailboxContentTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(ClearMailboxContentTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(ClearMailboxContentTaskAdditionalInformationDTO::toDto)
            .typeName(ClearMailboxContentTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private static ClearMailboxContentTask.AdditionalInformation toDomainObject(ClearMailboxContentTaskAdditionalInformationDTO dto) {
        return new ClearMailboxContentTask.AdditionalInformation(
            Username.of(dto.getUsername()),
            new MailboxName(dto.getMailboxName()),
            dto.getTimestamp(),
            dto.getMessagesSuccessCount(),
            dto.getMessagesFailCount());
    }

    private static ClearMailboxContentTaskAdditionalInformationDTO toDto(ClearMailboxContentTask.AdditionalInformation domain, String type) {
        return new ClearMailboxContentTaskAdditionalInformationDTO(
            type,
            domain.getUsername().asString(),
            domain.getMailboxName().asString(),
            domain.getTimestamp(),
            domain.getMessagesSuccessCount(),
            domain.getMessagesFailCount());
    }

    private final String type;
    private final String username;
    private final String mailboxName;
    private final Instant timestamp;
    private final long messagesSuccessCount;
    private final long messagesFailCount;

    public ClearMailboxContentTaskAdditionalInformationDTO(String type, String username, String mailboxName, Instant timestamp, long messagesSuccessCount, long messagesFailCount) {
        this.type = type;
        this.username = username;
        this.mailboxName = mailboxName;
        this.timestamp = timestamp;
        this.messagesSuccessCount = messagesSuccessCount;
        this.messagesFailCount = messagesFailCount;
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

    public String getMailboxName() {
        return mailboxName;
    }

    public long getMessagesSuccessCount() {
        return messagesSuccessCount;
    }

    public long getMessagesFailCount() {
        return messagesFailCount;
    }
}
