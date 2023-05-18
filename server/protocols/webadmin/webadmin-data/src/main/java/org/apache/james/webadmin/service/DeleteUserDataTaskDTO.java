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
import org.apache.james.user.api.DeleteUserDataTaskStep.StepName;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeleteUserDataTaskDTO implements TaskDTO {

    public static TaskDTOModule<DeleteUserDataTask, DeleteUserDataTaskDTO> module(DeleteUserDataService service) {
        return DTOModule
            .forDomainObject(DeleteUserDataTask.class)
            .convertToDTO(DeleteUserDataTaskDTO.class)
            .toDomainObjectConverter(dto -> dto.fromDTO(service))
            .toDTOConverter(DeleteUserDataTaskDTO::toDTO)
            .typeName(DeleteUserDataTask.TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    public static DeleteUserDataTaskDTO toDTO(DeleteUserDataTask domainObject, String typeName) {
        return new DeleteUserDataTaskDTO(typeName,
            domainObject.getUsername().asString(),
            domainObject.getFromStep().map(StepName::asString));
    }

    private final String type;
    private final String username;
    private final Optional<String> fromStep;

    public DeleteUserDataTaskDTO(@JsonProperty("type") String type,
                                 @JsonProperty("username") String username,
                                 @JsonProperty("fromStep") Optional<String> fromStep) {
        this.type = type;
        this.username = username;
        this.fromStep = fromStep;
    }

    public DeleteUserDataTask fromDTO(DeleteUserDataService service) {
        return new DeleteUserDataTask(service, Username.of(username), fromStep.map(StepName::new));
    }

    @Override
    public String getType() {
        return type;
    }

    public String getUsername() {
        return username;
    }

    public Optional<String> getFromStep() {
        return fromStep;
    }
}
