/**
 * *************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.rrt.cassandra.migration;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MappingsSourcesMigrationTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    private static MappingsSourcesMigrationTaskAdditionalInformationDTO fromDomainObject(MappingsSourcesMigration.AdditionalInformation additionalInformation, String type) {
        return new MappingsSourcesMigrationTaskAdditionalInformationDTO(
            additionalInformation.getSuccessfulMappingsCount(),
            additionalInformation.getErrorMappingsCount()
        );
    }

    public static final AdditionalInformationDTOModule<MappingsSourcesMigration.AdditionalInformation, MappingsSourcesMigrationTaskAdditionalInformationDTO> MODULE =
        DTOModule
            .forDomainObject(MappingsSourcesMigration.AdditionalInformation.class)
            .convertToDTO(MappingsSourcesMigrationTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(MappingsSourcesMigrationTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(MappingsSourcesMigrationTaskAdditionalInformationDTO::fromDomainObject)
            .typeName(MappingsSourcesMigration.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private final long successfulMappingsCount;
    private final long errorMappinsCount;

    public MappingsSourcesMigrationTaskAdditionalInformationDTO(@JsonProperty("successfulMappingsCount") long successfulMappingsCount, @JsonProperty("errorMappinsCount") long errorMappinsCount) {
        this.successfulMappingsCount = successfulMappingsCount;
        this.errorMappinsCount = errorMappinsCount;
    }

    public long getSuccessfulMappingsCount() {
        return successfulMappingsCount;
    }

    public long getErrorMappinsCount() {
        return errorMappinsCount;
    }

    private MappingsSourcesMigration.AdditionalInformation toDomainObject() {
        return new MappingsSourcesMigration.AdditionalInformation(
            successfulMappingsCount,
            errorMappinsCount
        );
    }
}
