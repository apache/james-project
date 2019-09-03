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

import java.util.function.Function;

import org.apache.james.core.User;
import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeletedMessagesVaultDeleteTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    static final Function<MessageId.Factory, AdditionalInformationDTOModule<DeletedMessagesVaultDeleteTask.AdditionalInformation, DeletedMessagesVaultDeleteTaskAdditionalInformationDTO>> SERIALIZATION_MODULE =
        factory ->
            DTOModule.forDomainObject(DeletedMessagesVaultDeleteTask.AdditionalInformation.class)
                .convertToDTO(DeletedMessagesVaultDeleteTaskAdditionalInformationDTO.class)
                .toDomainObjectConverter(dto -> new DeletedMessagesVaultDeleteTask.AdditionalInformation(User.fromUsername(dto.userName), factory.fromString(dto.getMessageId())))
                .toDTOConverter((details, type) -> new DeletedMessagesVaultDeleteTaskAdditionalInformationDTO(details.getUser(), details.getDeleteMessageId()))
                .typeName(DeletedMessagesVaultDeleteTask.TYPE.asString())
                .withFactory(AdditionalInformationDTOModule::new);

    private final String userName;
    private final String messageId;

    public DeletedMessagesVaultDeleteTaskAdditionalInformationDTO(@JsonProperty("userName") String userName, @JsonProperty("messageId") String messageId) {
        this.userName = userName;
        this.messageId = messageId;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getUserName() {
        return userName;
    }
}
