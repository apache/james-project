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

import org.apache.james.events.EventDeadLetters;
import org.apache.james.events.Group;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EventDeadLettersRedeliverOneTaskDTO implements TaskDTO {

    public static TaskDTOModule<EventDeadLettersRedeliverOneTask, EventDeadLettersRedeliverOneTaskDTO> module(EventDeadLettersRedeliverService service) {
        return DTOModule
            .forDomainObject(EventDeadLettersRedeliverOneTask.class)
            .convertToDTO(EventDeadLettersRedeliverOneTaskDTO.class)
            .toDomainObjectConverter(dto -> dto.toDomainObject(service))
            .toDTOConverter((domainObject, typeName) -> new EventDeadLettersRedeliverOneTaskDTO(typeName, domainObject.getGroup().asString(), domainObject.getInsertionId().getId().toString()))
            .typeName(EventDeadLettersRedeliverOneTask.TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    private final String type;
    private final String group;
    private final String insertionId;

    public EventDeadLettersRedeliverOneTaskDTO(@JsonProperty("type") String type, @JsonProperty("group") String group, @JsonProperty("intertionId") String insertionId) {
        this.type = type;
        this.group = group;
        this.insertionId = insertionId;
    }

    EventDeadLettersRedeliverOneTask toDomainObject(EventDeadLettersRedeliverService service) {
        try {
            return new EventDeadLettersRedeliverOneTask(service, Group.deserialize(getGroup()), EventDeadLetters.InsertionId.of(getInsertionId()));
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

    public String getInsertionId() {
        return insertionId;
    }
}
