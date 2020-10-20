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

package org.apache.james.mailbox.cassandra.mail.migration;

import java.util.function.Function;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MessageV3MigrationTaskDTO implements TaskDTO {

    private static MessageV3MigrationTaskDTO fromDomainObject(MessageV3Migration.MessageV3MigrationTask task, String type) {
        return new MessageV3MigrationTaskDTO(type);
    }

    public static final Function<MessageV3Migration, TaskDTOModule<MessageV3Migration.MessageV3MigrationTask, MessageV3MigrationTaskDTO>> MODULE = (migration) ->
        DTOModule
            .forDomainObject(MessageV3Migration.MessageV3MigrationTask.class)
            .convertToDTO(MessageV3MigrationTaskDTO.class)
            .toDomainObjectConverter(dto -> dto.toDomainObject(migration))
            .toDTOConverter(MessageV3MigrationTaskDTO::fromDomainObject)
            .typeName(MessageV3Migration.TYPE.asString())
            .withFactory(TaskDTOModule::new);

    private final String type;

    public MessageV3MigrationTaskDTO(@JsonProperty("type") String type) {
        this.type = type;
    }

    @Override
    public String getType() {
        return type;
    }

    private MessageV3Migration.MessageV3MigrationTask toDomainObject(MessageV3Migration migration) {
        return new MessageV3Migration.MessageV3MigrationTask(migration);
    }
}
