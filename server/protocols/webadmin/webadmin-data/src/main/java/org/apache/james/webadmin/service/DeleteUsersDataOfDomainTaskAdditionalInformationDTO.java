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

import org.apache.james.core.Domain;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeleteUsersDataOfDomainTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    public static AdditionalInformationDTOModule<DeleteUsersDataOfDomainTask.AdditionalInformation, DeleteUsersDataOfDomainTaskAdditionalInformationDTO> module() {
        return DTOModule.forDomainObject(DeleteUsersDataOfDomainTask.AdditionalInformation.class)
            .convertToDTO(DeleteUsersDataOfDomainTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(dto -> new DeleteUsersDataOfDomainTask.AdditionalInformation(
                dto.timestamp, Domain.of(dto.domain), dto.successfulUsersCount, dto.failedUsersCount))
            .toDTOConverter((details, type) -> new DeleteUsersDataOfDomainTaskAdditionalInformationDTO(
                type, details.getDomain().asString(), details.getSuccessfulUsersCount(), details.getFailedUsersCount(), details.timestamp()))
            .typeName(DeleteUsersDataOfDomainTask.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private final String type;
    private final String domain;
    private final long successfulUsersCount;
    private final long failedUsersCount;
    private final Instant timestamp;

    public DeleteUsersDataOfDomainTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                               @JsonProperty("domain") String domain,
                                                               @JsonProperty("successfulUsersCount") long successfulUsersCount,
                                                               @JsonProperty("failedUsersCount") long failedUsersCount,
                                                               @JsonProperty("timestamp") Instant timestamp) {
        this.type = type;
        this.domain = domain;
        this.successfulUsersCount = successfulUsersCount;
        this.failedUsersCount = failedUsersCount;
        this.timestamp = timestamp;
    }

    public String getDomain() {
        return domain;
    }

    public long getSuccessfulUsersCount() {
        return successfulUsersCount;
    }

    public long getFailedUsersCount() {
        return failedUsersCount;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String getType() {
        return type;
    }
}
