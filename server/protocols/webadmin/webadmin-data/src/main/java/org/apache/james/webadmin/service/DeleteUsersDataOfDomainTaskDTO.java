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

import org.apache.james.core.Domain;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.user.api.UsersRepository;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeleteUsersDataOfDomainTaskDTO implements TaskDTO {

    public static TaskDTOModule<DeleteUsersDataOfDomainTask, DeleteUsersDataOfDomainTaskDTO> module(DeleteUserDataService service, UsersRepository usersRepository) {
        return DTOModule
            .forDomainObject(DeleteUsersDataOfDomainTask.class)
            .convertToDTO(DeleteUsersDataOfDomainTaskDTO.class)
            .toDomainObjectConverter(dto -> dto.fromDTO(service, usersRepository))
            .toDTOConverter(DeleteUsersDataOfDomainTaskDTO::toDTO)
            .typeName(DeleteUsersDataOfDomainTask.TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    public static DeleteUsersDataOfDomainTaskDTO toDTO(DeleteUsersDataOfDomainTask domainObject, String typeName) {
        return new DeleteUsersDataOfDomainTaskDTO(typeName,
            domainObject.getDomain().asString());
    }

    private final String type;
    private final String domain;

    public DeleteUsersDataOfDomainTaskDTO(@JsonProperty("type") String type,
                                          @JsonProperty("domain") String domain) {
        this.type = type;
        this.domain = domain;
    }

    public DeleteUsersDataOfDomainTask fromDTO(DeleteUserDataService service, UsersRepository usersRepository) {
        return new DeleteUsersDataOfDomainTask(service, Domain.of(domain), usersRepository);
    }

    @Override
    public String getType() {
        return type;
    }

    public String getDomain() {
        return domain;
    }
}
