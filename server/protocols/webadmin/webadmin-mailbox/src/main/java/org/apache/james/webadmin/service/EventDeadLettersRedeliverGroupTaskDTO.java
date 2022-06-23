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

import org.apache.james.events.Group;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EventDeadLettersRedeliverGroupTaskDTO implements TaskDTO {

    public static TaskDTOModule<EventDeadLettersRedeliverGroupTask, EventDeadLettersRedeliverGroupTaskDTO> module(EventDeadLettersRedeliverService service) {
        return DTOModule
            .forDomainObject(EventDeadLettersRedeliverGroupTask.class)
            .convertToDTO(EventDeadLettersRedeliverGroupTaskDTO.class)
            .toDomainObjectConverter(dto -> dto.toDomainObject(service))
            .toDTOConverter((domainObject, typeName) -> new EventDeadLettersRedeliverGroupTaskDTO(typeName, domainObject.getGroup().asString(), domainObject.getRunningOptions()))
            .typeName(EventDeadLettersRedeliverGroupTask.TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    private final String type;
    private final String group;
    private final RunningOptions runningOptions;

    public EventDeadLettersRedeliverGroupTaskDTO(@JsonProperty("type") String type,
                                                 @JsonProperty("group") String group,
                                                 @JsonProperty("runningOptions") RunningOptions runningOptions) {
        this.type = type;
        this.group = group;
        this.runningOptions = Optional.ofNullable(runningOptions).orElse(RunningOptions.DEFAULT);
    }

    EventDeadLettersRedeliverGroupTask toDomainObject(EventDeadLettersRedeliverService service) {
        try {
            return new EventDeadLettersRedeliverGroupTask(service, Group.deserialize(getGroup()), runningOptions);
        } catch (Group.GroupDeserializationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getType() {
        return type;
    }

    public String getGroup() {
        return group;
    }

    public RunningOptions getRunningOptions() {
        return runningOptions;
    }
}
