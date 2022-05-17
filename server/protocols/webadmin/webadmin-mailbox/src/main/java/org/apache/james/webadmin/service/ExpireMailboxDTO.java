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
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.webadmin.service.ExpireMailboxService.RunningOptions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ExpireMailboxDTO implements TaskDTO {
    public static TaskDTOModule<ExpireMailboxTask, ExpireMailboxDTO> module(ExpireMailboxService service) {
        return DTOModule.forDomainObject(ExpireMailboxTask.class)
            .convertToDTO(ExpireMailboxDTO.class)
            .toDomainObjectConverter(dto -> toDomainObject(dto, service))
            .toDTOConverter(ExpireMailboxDTO::toDto)
            .typeName(ExpireMailboxTask.TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    private static ExpireMailboxTask toDomainObject(ExpireMailboxDTO dto, ExpireMailboxService service) {
        return new ExpireMailboxTask(service, dto.getRunningOptions());
    }

    private static ExpireMailboxDTO toDto(ExpireMailboxTask details, String type) {
        return new ExpireMailboxDTO(details.getRunningOptions(), type);
    }

    private final RunningOptions runningOptions;
    private final String type;

    @JsonCreator
    public ExpireMailboxDTO(@JsonProperty("runningOptions") RunningOptions runningOptions,
                            @JsonProperty("type") String type) {
        this.runningOptions = runningOptions;
        this.type = type;
    }

    @Override
    public String getType() {
        return type;
    }

    public RunningOptions getRunningOptions() {
        return runningOptions;
    }
}
