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

public class WebAdminDeletedMessagesVaultDeleteTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    public static AdditionalInformationDTOModule<DeletedMessagesVaultDeleteTask.AdditionalInformation, WebAdminDeletedMessagesVaultDeleteTaskAdditionalInformationDTO> serializationModule(MessageId.Factory factory) {
        return DTOModule.forDomainObject(DeletedMessagesVaultDeleteTask.AdditionalInformation.class)
            .convertToDTO(WebAdminDeletedMessagesVaultDeleteTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(dto -> new DeletedMessagesVaultDeleteTask.AdditionalInformation(Username.of(dto.username), factory.fromString(dto.getDeleteMessageId()), dto.getTimestamp()))
            .toDTOConverter((details, type) -> new WebAdminDeletedMessagesVaultDeleteTaskAdditionalInformationDTO(type, details.getUsername(), details.getDeleteMessageId(), details.timestamp()))
            .typeName(DeletedMessagesVaultDeleteTask.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private final String type;
    private final String username;
    private final String deleteMessageId;
    private final Instant timestamp;

    public WebAdminDeletedMessagesVaultDeleteTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                                          @JsonProperty("username") String username,
                                                                          @JsonProperty("deleteMessageId") String messageId,
                                                                          @JsonProperty("timestamp") Instant timestamp) {
        this.type = type;
        this.username = username;
        this.deleteMessageId = messageId;
        this.timestamp = timestamp;
    }

    public String getDeleteMessageId() {
        return deleteMessageId;
    }

    public String getUsername() {
        return username;
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
