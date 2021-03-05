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
import java.util.Set;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CreateMissingParentsTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    public static final AdditionalInformationDTOModule<CreateMissingParentsTask.AdditionalInformation, CreateMissingParentsTaskAdditionalInformationDTO> SERIALIZATION_MODULE =
        DTOModule.forDomainObject(CreateMissingParentsTask.AdditionalInformation.class)
            .convertToDTO(CreateMissingParentsTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(dto -> new CreateMissingParentsTask.AdditionalInformation(
                dto.timestamp,
                dto.created,
                dto.totalCreated,
                dto.failures,
                dto.totalFailure))
            .toDTOConverter((details, type) -> new CreateMissingParentsTaskAdditionalInformationDTO(
                type,
                details.timestamp(),
                details.getCreated(),
                details.getTotalCreated(),
                details.getFailures(),
                details.getTotalFailure()))
            .typeName(CreateMissingParentsTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private final String type;
    private final Set<String> created;
    private final long totalCreated;
    private final Set<String> failures;
    private final long totalFailure;
    private final Instant timestamp;

    private CreateMissingParentsTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                             @JsonProperty("timestamp") Instant timestamp,
                                                             @JsonProperty("created") Set<String> created,
                                                             @JsonProperty("totalCreated") long totalCreated,
                                                             @JsonProperty("failures") Set<String> failures,
                                                             @JsonProperty("totalFailure") long totalFailure) {
        this.type = type;
        this.created = created;
        this.totalCreated = totalCreated;
        this.failures = failures;
        this.totalFailure = totalFailure;
        this.timestamp = timestamp;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    public Set<String> getCreated() {
        return created;
    }

    public long getTotalCreated() {
        return totalCreated;
    }

    public Set<String> getFailures() {
        return failures;
    }

    public long getTotalFailure() {
        return totalFailure;
    }
}
