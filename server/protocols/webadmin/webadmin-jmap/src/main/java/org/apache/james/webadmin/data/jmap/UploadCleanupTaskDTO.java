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

import java.util.Locale;

import org.apache.james.jmap.api.upload.UploadRepository;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.webadmin.data.jmap.UploadRepositoryCleanupTask.CleanupScope;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UploadCleanupTaskDTO implements TaskDTO {
    private final String scope;
    private final String type;

    public UploadCleanupTaskDTO(@JsonProperty("scope") String scope,
                                @JsonProperty("type") String type) {
        this.scope = scope;
        this.type = type;
    }

    @Override
    public String getType() {
        return type;
    }

    public String getScope() {
        return scope;
    }

    public static TaskDTOModule<UploadRepositoryCleanupTask, UploadCleanupTaskDTO> module(UploadRepository uploadRepository) {
        return DTOModule
            .forDomainObject(UploadRepositoryCleanupTask.class)
            .convertToDTO(UploadCleanupTaskDTO.class)
            .toDomainObjectConverter(dto -> new UploadRepositoryCleanupTask(uploadRepository,
                CleanupScope.from(dto.getScope()).orElseThrow(CleanupScope.CleanupScopeInvalidException::new)))
            .toDTOConverter((domain, type) -> new UploadCleanupTaskDTO(domain.getScope().name().toLowerCase(Locale.US), type))
            .typeName(UploadRepositoryCleanupTask.TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }
}
