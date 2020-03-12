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

import org.apache.james.core.Username;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MailboxesExportTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    public static final AdditionalInformationDTOModule<MailboxesExportTask.AdditionalInformation, MailboxesExportTaskAdditionalInformationDTO> SERIALIZATION_MODULE =
        DTOModule.forDomainObject(MailboxesExportTask.AdditionalInformation.class)
            .convertToDTO(MailboxesExportTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(dto -> new MailboxesExportTask.AdditionalInformation(
                Username.of(dto.username),
                dto.getStage(),
                dto.timestamp))
            .toDTOConverter((details, type) -> new MailboxesExportTaskAdditionalInformationDTO(
                type,
                details.timestamp(),
                details.getUsername(),
                details.getStage()))
            .typeName(MailboxesExportTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private final String username;
    private final Instant timestamp;
    private final String type;
    private final ExportService.Stage stage;

    private MailboxesExportTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                        @JsonProperty("timestamp") Instant timestamp,
                                                        @JsonProperty("username") String username,
                                                        @JsonProperty("stage") ExportService.Stage stage) {
        this.type = type;
        this.timestamp = timestamp;
        this.username = username;
        this.stage = stage;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    public String getUsername() {
        return username;
    }

    public ExportService.Stage getStage() {
        return stage;
    }
}
