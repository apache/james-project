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

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.vault.dto.query.QueryDTO;
import org.apache.james.vault.dto.query.QueryTranslator;
import org.apache.james.vault.search.Query;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeletedMessagesVaultRestoreTaskDTO implements TaskDTO {

    public static class Factory {

        private final RestoreService restoreService;
        private final QueryTranslator queryTranslator;

        @Inject
        public Factory(RestoreService restoreService, QueryTranslator queryTranslator) {
            this.restoreService = restoreService;
            this.queryTranslator = queryTranslator;
        }

        public DeletedMessagesVaultRestoreTask create(DeletedMessagesVaultRestoreTaskDTO dto) {
            Username usernameToRestore = Username.of(dto.userToRestore);
            Query query = queryTranslator.translate(dto.query);
            return new DeletedMessagesVaultRestoreTask(restoreService, usernameToRestore, query);
        }

        public DeletedMessagesVaultRestoreTaskDTO createDTO(DeletedMessagesVaultRestoreTask task, String type) {
            return new DeletedMessagesVaultRestoreTaskDTO(type, task.getUserToRestore().asString(), queryTranslator.toDTO(task.query));
        }
    }

    public static TaskDTOModule<DeletedMessagesVaultRestoreTask, DeletedMessagesVaultRestoreTaskDTO> module(DeletedMessagesVaultRestoreTaskDTO.Factory factory) {
        return DTOModule
            .forDomainObject(DeletedMessagesVaultRestoreTask.class)
            .convertToDTO(DeletedMessagesVaultRestoreTaskDTO.class)
            .toDomainObjectConverter(factory::create)
            .toDTOConverter(factory::createDTO)
            .typeName(DeletedMessagesVaultRestoreTask.TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    private final String type;
    private final String userToRestore;
    private final QueryDTO query;

    public DeletedMessagesVaultRestoreTaskDTO(@JsonProperty("type") String type,
                                              @JsonProperty("userToRestore") String userToRestore,
                                              @JsonProperty("query") QueryDTO query) {
        this.type = type;
        this.userToRestore = userToRestore;
        this.query = query;
    }

    public String getUserToRestore() {
        return userToRestore;
    }

    public QueryDTO getQuery() {
        return query;
    }

    public String getType() {
        return type;
    }

}
