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

import com.fasterxml.jackson.annotation.JsonProperty;

public class EventDeadLettersRedeliverAllTaskDTO implements TaskDTO {

    public static TaskDTOModule<EventDeadLettersRedeliverAllTask, EventDeadLettersRedeliverAllTaskDTO> module(EventDeadLettersRedeliverService service) {
        return DTOModule
            .forDomainObject(EventDeadLettersRedeliverAllTask.class)
            .convertToDTO(EventDeadLettersRedeliverAllTaskDTO.class)
            .toDomainObjectConverter(dto -> new EventDeadLettersRedeliverAllTask(service))
            .toDTOConverter((domainObject, typeName) -> new EventDeadLettersRedeliverAllTaskDTO(typeName))
            .typeName(EventDeadLettersRedeliverAllTask.TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    private final String type;

    public EventDeadLettersRedeliverAllTaskDTO(@JsonProperty("type") String type) {
        this.type = type;
    }

    @Override
    public String getType() {
        return type;
    }
}
