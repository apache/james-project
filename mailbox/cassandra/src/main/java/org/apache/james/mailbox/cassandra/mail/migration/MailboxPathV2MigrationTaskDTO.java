/**
 * *************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.mailbox.cassandra.mail.migration;

import java.util.function.Function;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MailboxPathV2MigrationTaskDTO implements TaskDTO {

    private static MailboxPathV2MigrationTaskDTO fromDomainObject(MailboxPathV2Migration.MailboxPathV2MigrationTask task, String type) {
        return new MailboxPathV2MigrationTaskDTO(type);
    }

    public static final Function<MailboxPathV2Migration, TaskDTOModule<MailboxPathV2Migration.MailboxPathV2MigrationTask, MailboxPathV2MigrationTaskDTO>> MODULE = (migration) ->
        DTOModule
            .forDomainObject(MailboxPathV2Migration.MailboxPathV2MigrationTask.class)
            .convertToDTO(MailboxPathV2MigrationTaskDTO.class)
            .toDomainObjectConverter(dto -> dto.toDomainObject(migration))
            .toDTOConverter(MailboxPathV2MigrationTaskDTO::fromDomainObject)
            .typeName(MailboxPathV2Migration.TYPE.asString())
            .withFactory(TaskDTOModule::new);

    private final String type;

    public MailboxPathV2MigrationTaskDTO(@JsonProperty("type") String type) {
        this.type = type;
    }

    @Override
    public String getType() {
        return type;
    }

    private MailboxPathV2Migration.MailboxPathV2MigrationTask toDomainObject(MailboxPathV2Migration migration) {
        return new MailboxPathV2Migration.MailboxPathV2MigrationTask(migration);
    }
}
