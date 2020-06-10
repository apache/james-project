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
package org.apache.james.webadmin.dto;

import java.time.Instant;

import org.apache.james.backends.cassandra.migration.MigrationTask;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WebAdminMigrationTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    public static AdditionalInformationDTOModule<MigrationTask.AdditionalInformation, WebAdminMigrationTaskAdditionalInformationDTO> module() {
        return DTOModule.forDomainObject(MigrationTask.AdditionalInformation.class)
            .convertToDTO(WebAdminMigrationTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(dto -> new MigrationTask.AdditionalInformation(new SchemaVersion(dto.getToVersion()), dto.timestamp))
            .toDTOConverter((details, type) -> new WebAdminMigrationTaskAdditionalInformationDTO(type, details.getToVersion(), details.timestamp()))
            .typeName(MigrationTask.CASSANDRA_MIGRATION.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private final String type;
    private final int toVersion;
    private final Instant timestamp;

    public WebAdminMigrationTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                         @JsonProperty("toVersion") int toVersion,
                                                         @JsonProperty("timestamp") Instant timestamp) {
        this.type = type;
        this.toVersion = toVersion;
        this.timestamp = timestamp;
    }

    @Override
    public String getType() {
        return type;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public int getToVersion() {
        return toVersion;
    }
}
