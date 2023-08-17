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
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WebAdminDeletedMessagesVaultRestoreTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    private static WebAdminDeletedMessagesVaultRestoreTaskAdditionalInformationDTO fromDomainObject(DeletedMessagesVaultRestoreTask.AdditionalInformation additionalInformation, String type) {
        return new WebAdminDeletedMessagesVaultRestoreTaskAdditionalInformationDTO(
            type,
            additionalInformation.getUsername(),
            additionalInformation.getSuccessfulRestoreCount(),
            additionalInformation.getErrorRestoreCount(),
            additionalInformation.timestamp()
        );
    }

    public static AdditionalInformationDTOModule<DeletedMessagesVaultRestoreTask.AdditionalInformation, WebAdminDeletedMessagesVaultRestoreTaskAdditionalInformationDTO> module() {
        return DTOModule.forDomainObject(DeletedMessagesVaultRestoreTask.AdditionalInformation.class)
            .convertToDTO(WebAdminDeletedMessagesVaultRestoreTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(WebAdminDeletedMessagesVaultRestoreTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(WebAdminDeletedMessagesVaultRestoreTaskAdditionalInformationDTO::fromDomainObject)
            .typeName(DeletedMessagesVaultRestoreTask.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private final String type;
    private final String username;
    private final Long successfulRestoreCount;
    private final Long errorRestoreCount;
    private final Instant timestamp;

    public WebAdminDeletedMessagesVaultRestoreTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                                           @JsonProperty("username") String username,
                                                                           @JsonProperty("successfulRestoreCount") Long successfulRestoreCount,
                                                                           @JsonProperty("errorRestoreCount") Long errorRestoreCount,
                                                                           @JsonProperty("timestamp") Instant timestamp) {
        this.type = type;
        this.username = username;
        this.successfulRestoreCount = successfulRestoreCount;
        this.errorRestoreCount = errorRestoreCount;
        this.timestamp = timestamp;
    }

    public String getUsername() {
        return username;
    }

    public Long getSuccessfulRestoreCount() {
        return successfulRestoreCount;
    }

    public Long getErrorRestoreCount() {
        return errorRestoreCount;
    }

    DeletedMessagesVaultRestoreTask.AdditionalInformation toDomainObject() {
        return new DeletedMessagesVaultRestoreTask.AdditionalInformation(
            Username.of(username),
            successfulRestoreCount,
            errorRestoreCount,
            timestamp
        );
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
