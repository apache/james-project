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


package org.apache.james.rrt.cassandra.migration;

import java.time.Instant;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.task.TaskType;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MappingsSourcesMigrationTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    private static MappingsSourcesMigrationTaskAdditionalInformationDTO fromDomainObject(MappingsSourcesMigration.AdditionalInformation additionalInformation, String type) {
        return new MappingsSourcesMigrationTaskAdditionalInformationDTO(
            type,
            additionalInformation.getSuccessfulMappingsCount(),
            additionalInformation.getErrorMappingsCount(),
            additionalInformation.timestamp()
        );
    }

    public static AdditionalInformationDTOModule<MappingsSourcesMigration.AdditionalInformation, MappingsSourcesMigrationTaskAdditionalInformationDTO> module(TaskType type) {
        return DTOModule
            .forDomainObject(MappingsSourcesMigration.AdditionalInformation.class)
            .convertToDTO(MappingsSourcesMigrationTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(MappingsSourcesMigrationTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(MappingsSourcesMigrationTaskAdditionalInformationDTO::fromDomainObject)
            .typeName(type.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private final String type;
    private final long successfulMappingsCount;
    private final long errorMappingsCount;
    private final Instant timestamp;

    public MappingsSourcesMigrationTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                                @JsonProperty("successfulMappingsCount") long successfulMappingsCount,
                                                                @JsonProperty("errorMappingsCount") long errorMappingsCount,
                                                                @JsonProperty("timestamp") Instant timestamp) {
        this.type = type;
        this.successfulMappingsCount = successfulMappingsCount;
        this.errorMappingsCount = errorMappingsCount;
        this.timestamp = timestamp;
    }

    public long getSuccessfulMappingsCount() {
        return successfulMappingsCount;
    }

    public long getErrorMappingsCount() {
        return errorMappingsCount;
    }

    @Override
    public String getType() {
        return type;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    private MappingsSourcesMigration.AdditionalInformation toDomainObject() {
        return new MappingsSourcesMigration.AdditionalInformation(
            successfulMappingsCount,
            errorMappingsCount,
            timestamp
        );
    }
}
