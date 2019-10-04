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

import org.apache.james.json.DTOModule;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ClearMailRepositoryTaskDTO implements TaskDTO {

    public static TaskDTOModule<ClearMailRepositoryTask, ClearMailRepositoryTaskDTO> module(ClearMailRepositoryTask.Factory factory) {
        return DTOModule
            .forDomainObject(ClearMailRepositoryTask.class)
            .convertToDTO(ClearMailRepositoryTaskDTO.class)
            .toDomainObjectConverter(dto -> dto.fromDTO(factory))
            .toDTOConverter(ClearMailRepositoryTaskDTO::toDTO)
            .typeName(ClearMailRepositoryTask.TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    public static ClearMailRepositoryTaskDTO toDTO(ClearMailRepositoryTask domainObject, String typeName) {
        try {
            return new ClearMailRepositoryTaskDTO(typeName, domainObject.getMailRepositoryPath().urlEncoded());
        } catch (Exception e) {
            throw new ClearMailRepositoryTask.UrlEncodingFailureSerializationException(domainObject.getMailRepositoryPath());
        }
    }

    private final String type;
    private final String mailRepositoryPath;

    public ClearMailRepositoryTaskDTO(@JsonProperty("type") String type, @JsonProperty("mailRepositoryPath") String mailRepositoryPath) {
        this.type = type;
        this.mailRepositoryPath = mailRepositoryPath;
    }

    public ClearMailRepositoryTask fromDTO(ClearMailRepositoryTask.Factory factory) {
        try {
            return factory.create(MailRepositoryPath.fromEncoded(mailRepositoryPath));
        } catch (Exception e) {
            throw new ClearMailRepositoryTask.InvalidMailRepositoryPathDeserializationException(mailRepositoryPath);
        }
    }

    @Override
    public String getType() {
        return type;
    }

    public String getMailRepositoryPath() {
        return mailRepositoryPath;
    }
}
