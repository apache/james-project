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
import java.util.Map;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.user.api.UsernameChangeTaskStep.StepName;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

public class UsernameChangeTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    public static AdditionalInformationDTOModule<UsernameChangeTask.AdditionalInformation, UsernameChangeTaskAdditionalInformationDTO> module() {
        return DTOModule.forDomainObject(UsernameChangeTask.AdditionalInformation.class)
            .convertToDTO(UsernameChangeTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(dto -> new UsernameChangeTask.AdditionalInformation(
                dto.timestamp,
                Username.of(dto.oldUser),
                Username.of(dto.newUser),
                dto.status.entrySet().stream()
                    .collect(ImmutableMap.toImmutableMap(
                        entry -> new StepName(entry.getKey()),
                        entry -> UsernameChangeService.StepState.valueOf(entry.getValue()))),
                dto.fromStep.map(StepName::new)))
            .toDTOConverter((details, type) -> new UsernameChangeTaskAdditionalInformationDTO(
                type,
                details.getOldUser().asString(),
                details.getNewUser().asString(),
                details.getStatus().entrySet().stream()
                    .collect(ImmutableMap.toImmutableMap(
                        entry -> entry.getKey().asString(),
                        entry -> entry.getValue().toString())),
                details.getFromStep().map(StepName::asString),
                details.timestamp()))
            .typeName(UsernameChangeTask.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private final String type;
    private final String oldUser;
    private final String newUser;
    private final Map<String, String> status;
    private final Optional<String> fromStep;
    private final Instant timestamp;

    public UsernameChangeTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                      @JsonProperty("oldUser") String oldUser,
                                                      @JsonProperty("newUser") String newUser,
                                                      @JsonProperty("status") Map<String, String> status,
                                                      @JsonProperty("fromStep") Optional<String> fromStep,
                                                      @JsonProperty("timestamp") Instant timestamp) {
        this.type = type;
        this.oldUser = oldUser;
        this.newUser = newUser;
        this.status = status;
        this.timestamp = timestamp;
        this.fromStep = fromStep;
    }

    public String getOldUser() {
        return oldUser;
    }

    public String getNewUser() {
        return newUser;
    }

    public Map<String, String> getStatus() {
        return status;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Optional<String> getFromStep() {
        return fromStep;
    }

    @Override
    public String getType() {
        return type;
    }
}
