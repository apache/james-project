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

package org.apache.james.webadmin.tasks;

import java.time.Instant;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.webadmin.services.TasksCleanupService;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TasksCleanupTaskDTO implements TaskDTO {

    public static TaskDTOModule<TasksCleanupTask, TasksCleanupTaskDTO> module(TasksCleanupService service) {
        return DTOModule
            .forDomainObject(TasksCleanupTask.class)
            .convertToDTO(TasksCleanupTaskDTO.class)
            .toDomainObjectConverter(dto -> new TasksCleanupTask(service, dto.getOlderThan()))
            .toDTOConverter((domain, type) -> new TasksCleanupTaskDTO(type, domain.getBeforeDate()))
            .typeName(TasksCleanupTask.TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }


    private final String type;
    private final Instant olderThan;

    public TasksCleanupTaskDTO(@JsonProperty("type") String type,
                               @JsonProperty("olderThan") Instant olderThan) {
        this.type = type;
        this.olderThan = olderThan;
    }

    @Override
    public String getType() {
        return type;
    }

    public Instant getOlderThan() {
        return olderThan;
    }
}
