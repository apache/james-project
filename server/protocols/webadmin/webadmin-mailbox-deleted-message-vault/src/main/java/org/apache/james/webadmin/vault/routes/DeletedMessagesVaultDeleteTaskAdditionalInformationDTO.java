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
package org.apache.james.webadmin.vault.routes;

import java.time.Instant;

import org.apache.james.core.Username;
import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeletedMessagesVaultDeleteTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    public static AdditionalInformationDTOModule<DeletedMessagesVaultDeleteTask.AdditionalInformation, DeletedMessagesVaultDeleteTaskAdditionalInformationDTO> module(MessageId.Factory factory) {
        return DTOModule.forDomainObject(DeletedMessagesVaultDeleteTask.AdditionalInformation.class)
            .convertToDTO(DeletedMessagesVaultDeleteTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(dto -> new DeletedMessagesVaultDeleteTask.AdditionalInformation(Username.of(dto.userName), factory.fromString(dto.getMessageId()), dto.getTimestamp()))
            .toDTOConverter((details, type) -> new DeletedMessagesVaultDeleteTaskAdditionalInformationDTO(type, details.getUsername(), details.getDeleteMessageId(), details.timestamp()))
            .typeName(DeletedMessagesVaultDeleteTask.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private final String type;
    private final String userName;
    private final String messageId;
    private final Instant timestamp;

    public DeletedMessagesVaultDeleteTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                                  @JsonProperty("userName") String userName,
                                                                  @JsonProperty("messageId") String messageId,
                                                                  @JsonProperty("timestamp") Instant timestamp) {
        this.type = type;
        this.userName = userName;
        this.messageId = messageId;
        this.timestamp = timestamp;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getUserName() {
        return userName;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String getType() {
        return type;
    }
}
