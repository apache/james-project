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
package org.apache.james.webadmin.vault.routes;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeletedMessagesVaultDeleteTaskDTO implements TaskDTO {

    public static TaskDTOModule<DeletedMessagesVaultDeleteTask, DeletedMessagesVaultDeleteTaskDTO> module(DeletedMessagesVaultDeleteTask.Factory factory) {
        return DTOModule
            .forDomainObject(DeletedMessagesVaultDeleteTask.class)
            .convertToDTO(DeletedMessagesVaultDeleteTaskDTO.class)
            .toDomainObjectConverter(factory::create)
            .toDTOConverter(DeletedMessagesVaultDeleteTaskDTO::of)
            .typeName(DeletedMessagesVaultDeleteTask.TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    private final String type;
    private final String userName;
    private final String messageId;

    public DeletedMessagesVaultDeleteTaskDTO(@JsonProperty("type") String type, @JsonProperty("userName") String userName, @JsonProperty("messageId") String messageId) {
        this.type = type;
        this.userName = userName;
        this.messageId = messageId;
    }

    public String getUserName() {
        return userName;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getType() {
        return type;
    }

    public static DeletedMessagesVaultDeleteTaskDTO of(DeletedMessagesVaultDeleteTask task, String type) {
        return new DeletedMessagesVaultDeleteTaskDTO(type, task.getUsername().asString(), task.getMessageId().serialize());
    }
}
