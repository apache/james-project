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

import static org.apache.james.webadmin.service.EventDeadLettersRedeliverService.RunningOptions;

import java.util.Optional;
import java.util.Set;

import org.apache.james.events.Group;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EventDeadLettersRedeliverAllTaskDTO implements TaskDTO {

    public static TaskDTOModule<EventDeadLettersRedeliverAllTask, EventDeadLettersRedeliverAllTaskDTO> module(EventDeadLettersRedeliverService service,
                                                                                                              Set<Group> nonCriticalGroups) {
        return DTOModule
            .forDomainObject(EventDeadLettersRedeliverAllTask.class)
            .convertToDTO(EventDeadLettersRedeliverAllTaskDTO.class)
            .toDomainObjectConverter(dto -> new EventDeadLettersRedeliverAllTask(service, dto.getRunningOptions(), nonCriticalGroups))
            .toDTOConverter((domainObject, typeName) -> new EventDeadLettersRedeliverAllTaskDTO(typeName, domainObject.getRunningOptions()))
            .typeName(EventDeadLettersRedeliverAllTask.TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    private final String type;
    private final RunningOptions runningOptions;

    public EventDeadLettersRedeliverAllTaskDTO(@JsonProperty("type") String type,
                                               @JsonProperty("runningOptions") RunningOptions runningOptions) {
        this.type = type;
        this.runningOptions = Optional.ofNullable(runningOptions).orElse(RunningOptions.DEFAULT);
    }

    @Override
    public String getType() {
        return type;
    }

    public RunningOptions getRunningOptions() {
        return runningOptions;
    }
}
