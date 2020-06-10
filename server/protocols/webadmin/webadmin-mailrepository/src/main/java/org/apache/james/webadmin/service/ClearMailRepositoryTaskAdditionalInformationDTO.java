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
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ClearMailRepositoryTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    public static AdditionalInformationDTOModule<ClearMailRepositoryTask.AdditionalInformation, ClearMailRepositoryTaskAdditionalInformationDTO> module() {
        return DTOModule.forDomainObject(ClearMailRepositoryTask.AdditionalInformation.class)
            .convertToDTO(ClearMailRepositoryTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(dto -> new ClearMailRepositoryTask.AdditionalInformation(
                MailRepositoryPath.from(dto.mailRepositoryPath),
                dto.initialCount,
                dto.remainingCount,
                dto.timestamp))
            .toDTOConverter((details, type) -> new ClearMailRepositoryTaskAdditionalInformationDTO(
                type,
                details.getRepositoryPath(),
                details.getInitialCount(),
                details.getRemainingCount(),
                details.timestamp()))
            .typeName(ClearMailRepositoryTask.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private final String mailRepositoryPath;
    private final String type;
    private final long initialCount;
    private final long remainingCount;
    private final Instant timestamp;

    public ClearMailRepositoryTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                           @JsonProperty("mailRepositoryPath") String mailRepositoryPath,
                                                           @JsonProperty("initialCount") long initialCount,
                                                           @JsonProperty("remainingCount") long remainingCount,
                                                           @JsonProperty("timestamp") Instant timestamp) {
        this.type = type;
        this.mailRepositoryPath = mailRepositoryPath;
        this.initialCount = initialCount;
        this.remainingCount = remainingCount;
        this.timestamp = timestamp;
    }

    public String getMailRepositoryPath() {
        return mailRepositoryPath;
    }

    public long getInitialCount() {
        return initialCount;
    }

    public long getRemainingCount() {
        return remainingCount;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String getType() {
        return type;
    }
}
