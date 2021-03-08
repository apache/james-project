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
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SubscribeAllTaskDTO implements TaskDTO {
    public static TaskDTOModule<SubscribeAllTask, SubscribeAllTaskDTO> module(MailboxManager mailboxManager, SubscriptionManager subscriptionManager) {
        return DTOModule
            .forDomainObject(SubscribeAllTask.class)
            .convertToDTO(SubscribeAllTaskDTO.class)
            .toDomainObjectConverter(dto -> dto.toDomainObject(mailboxManager, subscriptionManager))
            .toDTOConverter((domainObject, typeName) -> new SubscribeAllTaskDTO(typeName, domainObject.getUsername().asString()))
            .typeName(SubscribeAllTask.TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    private final String type;
    private final String username;

    public SubscribeAllTaskDTO(@JsonProperty("type") String type,
                               @JsonProperty("username") String username) {
        this.type = type;
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public String getType() {
        return type;
    }

    public SubscribeAllTask toDomainObject(MailboxManager mailboxManager, SubscriptionManager subscriptionManager) {
        return new SubscribeAllTask(mailboxManager, subscriptionManager, Username.of(username));
    }
}
