/**
 * *************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.webadmin.vault.routes;

import javax.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.User;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeletedMessagesVaultExportTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    private static DeletedMessagesVaultExportTaskAdditionalInformationDTO fromDomainObject(DeletedMessagesVaultExportTask.AdditionalInformation additionalInformation, String type) {
        return new DeletedMessagesVaultExportTaskAdditionalInformationDTO(
            additionalInformation.getUserExportFrom(),
            additionalInformation.getExportTo(),
            additionalInformation.getTotalExportedMessages()
        );
    }

    public static final AdditionalInformationDTOModule<DeletedMessagesVaultExportTask.AdditionalInformation, DeletedMessagesVaultExportTaskAdditionalInformationDTO> MODULE =
        DTOModule
            .forDomainObject(DeletedMessagesVaultExportTask.AdditionalInformation.class)
            .convertToDTO(DeletedMessagesVaultExportTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(DeletedMessagesVaultExportTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(DeletedMessagesVaultExportTaskAdditionalInformationDTO::fromDomainObject)
            .typeName(DeletedMessagesVaultExportTask.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private final String userExportFrom;
    private final String exportTo;
    private final Long totalExportedMessages;

    public DeletedMessagesVaultExportTaskAdditionalInformationDTO(@JsonProperty("user") String userExportFrom,
                                                                  @JsonProperty("exportTo") String exportTo,
                                                                  @JsonProperty("errorRestoreCount") Long totalExportedMessages) {
        this.userExportFrom = userExportFrom;
        this.exportTo = exportTo;
        this.totalExportedMessages = totalExportedMessages;
    }

    public String getUserExportFrom() {
        return userExportFrom;
    }

    public String getExportTo() {
        return exportTo;
    }

    public Long getTotalExportedMessages() {
        return totalExportedMessages;
    }

    DeletedMessagesVaultExportTask.AdditionalInformation toDomainObject() {
        try {
            return new DeletedMessagesVaultExportTask.AdditionalInformation(
                User.fromUsername(userExportFrom),
                new MailAddress(exportTo),
                totalExportedMessages
            );
        } catch (AddressException e) {
            throw new RuntimeException(e);
        }
    }
}
