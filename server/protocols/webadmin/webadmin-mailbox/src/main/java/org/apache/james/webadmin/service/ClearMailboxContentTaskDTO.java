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

import org.apache.james.core.Username;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.webadmin.validation.MailboxName;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ClearMailboxContentTaskDTO implements TaskDTO {
    private final String type;
    private final String username;
    private final String mailboxName;

    public ClearMailboxContentTaskDTO(@JsonProperty("type") String type,
                                      @JsonProperty("username") String username,
                                      @JsonProperty("mailboxName") String mailboxName) {
        this.type = type;
        this.username = username;
        this.mailboxName = mailboxName;
    }

    @Override
    public String getType() {
        return type;
    }

    public String getUsername() {
        return username;
    }

    public String getMailboxName() {
        return mailboxName;
    }

    public static TaskDTOModule<ClearMailboxContentTask, ClearMailboxContentTaskDTO> module(UserMailboxesService userMailboxesService) {
        return DTOModule
            .forDomainObject(ClearMailboxContentTask.class)
            .convertToDTO(ClearMailboxContentTaskDTO.class)
            .toDomainObjectConverter(dto -> dto.fromDTO(userMailboxesService))
            .toDTOConverter(ClearMailboxContentTaskDTO::toDTO)
            .typeName(ClearMailboxContentTask.TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    public ClearMailboxContentTask fromDTO(UserMailboxesService userMailboxesService) {
        return new ClearMailboxContentTask(Username.of(username), new MailboxName(mailboxName), userMailboxesService);
    }

    public static ClearMailboxContentTaskDTO toDTO(ClearMailboxContentTask domainObject, String typeName) {
        return new ClearMailboxContentTaskDTO(typeName, domainObject.getUsername().asString(), domainObject.getMailboxName().asString());
    }
}
