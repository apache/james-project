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

import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.user.api.UsernameChangeTaskStep.StepName;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UsernameChangeTaskDTO implements TaskDTO {

    public static TaskDTOModule<UsernameChangeTask, UsernameChangeTaskDTO> module(UsernameChangeService service) {
        return DTOModule
            .forDomainObject(UsernameChangeTask.class)
            .convertToDTO(UsernameChangeTaskDTO.class)
            .toDomainObjectConverter(dto -> dto.fromDTO(service))
            .toDTOConverter(UsernameChangeTaskDTO::toDTO)
            .typeName(UsernameChangeTask.TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    public static UsernameChangeTaskDTO toDTO(UsernameChangeTask domainObject, String typeName) {
        return new UsernameChangeTaskDTO(typeName,
            domainObject.getOldUser().asString(),
            domainObject.getNewUser().asString(),
            domainObject.getFromStep().map(StepName::asString));
    }

    private final String type;
    private final String oldUser;
    private final String newUser;
    private final Optional<String> fromStep;

    public UsernameChangeTaskDTO(@JsonProperty("type") String type,
                                 @JsonProperty("oldUser") String oldUser,
                                 @JsonProperty("newUser") String newUser,
                                 @JsonProperty("fromStep") Optional<String> fromStep) {
        this.type = type;
        this.oldUser = oldUser;
        this.newUser = newUser;
        this.fromStep = fromStep;
    }

    public UsernameChangeTask fromDTO(UsernameChangeService service) {
        return new UsernameChangeTask(service, Username.of(oldUser), Username.of(newUser), fromStep.map(StepName::new));
    }

    @Override
    public String getType() {
        return type;
    }

    public String getOldUser() {
        return oldUser;
    }

    public String getNewUser() {
        return newUser;
    }

    public Optional<String> getFromStep() {
        return fromStep;
    }
}
