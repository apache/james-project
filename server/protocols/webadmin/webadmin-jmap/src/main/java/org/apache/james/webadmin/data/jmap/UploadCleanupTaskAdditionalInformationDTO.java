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

package org.apache.james.webadmin.data.jmap;

import java.time.Instant;
import java.util.Locale;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.webadmin.data.jmap.UploadRepositoryCleanupTask.CleanupScope;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UploadCleanupTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    public static final AdditionalInformationDTOModule<UploadRepositoryCleanupTask.AdditionalInformation, UploadCleanupTaskAdditionalInformationDTO> SERIALIZATION_MODULE =
        DTOModule.forDomainObject(UploadRepositoryCleanupTask.AdditionalInformation.class)
            .convertToDTO(UploadCleanupTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(dto ->
                new UploadRepositoryCleanupTask.AdditionalInformation(
                    CleanupScope.from(dto.getScope()).orElseThrow(CleanupScope.CleanupScopeInvalidException::new),
                    dto.getTimestamp()))
            .toDTOConverter((domain, type) ->
                new UploadCleanupTaskAdditionalInformationDTO(
                    type,
                    domain.timestamp(),
                    domain.getScope().name().toLowerCase(Locale.US)))
            .typeName(UploadRepositoryCleanupTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);


    private final String type;
    private final Instant timestamp;
    private final String scope;

    public UploadCleanupTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                     @JsonProperty("timestamp") Instant timestamp,
                                                     @JsonProperty("scope") String scope) {
        this.type = type;
        this.timestamp = timestamp;
        this.scope = scope;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    public String getScope() {
        return scope;
    }
}
