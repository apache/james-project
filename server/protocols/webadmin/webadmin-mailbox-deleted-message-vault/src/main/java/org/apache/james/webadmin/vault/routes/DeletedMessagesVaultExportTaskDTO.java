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
import javax.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.vault.dto.query.QueryDTO;
import org.apache.james.vault.dto.query.QueryTranslator;
import org.apache.james.vault.search.Query;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeletedMessagesVaultExportTaskDTO implements TaskDTO {

    public static TaskDTOModule<DeletedMessagesVaultExportTask, DeletedMessagesVaultExportTaskDTO> module(Factory factory) {
        return DTOModule
            .forDomainObject(DeletedMessagesVaultExportTask.class)
            .convertToDTO(DeletedMessagesVaultExportTaskDTO.class)
            .toDomainObjectConverter(dto -> {
                try {
                    return factory.create(dto);
                } catch (AddressException e) {
                    throw new RuntimeException(e);
                }
            })
            .toDTOConverter(factory::createDTO)
            .typeName(DeletedMessagesVaultExportTask.TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    public static class Factory {

        private final ExportService exportService;
        private final QueryTranslator queryTranslator;

        @Inject
        public Factory(ExportService exportService, QueryTranslator queryTranslator) {
            this.exportService = exportService;
            this.queryTranslator = queryTranslator;
        }

        public DeletedMessagesVaultExportTask create(DeletedMessagesVaultExportTaskDTO dto) throws AddressException {
            Username userExportFrom = Username.of(dto.userExportFrom);
            Query exportQuery = queryTranslator.translate(dto.exportQuery);
            MailAddress exportTo = new MailAddress(dto.exportTo);
            return new DeletedMessagesVaultExportTask(exportService, userExportFrom, exportQuery, exportTo);
        }

        DeletedMessagesVaultExportTaskDTO createDTO(DeletedMessagesVaultExportTask task, String type) {
            return new DeletedMessagesVaultExportTaskDTO(type, task.getUserExportFrom().asString(), queryTranslator.toDTO(task.getExportQuery()), task.getExportTo().asString());
        }
    }

    private final String type;
    private final String userExportFrom;
    private final QueryDTO exportQuery;
    private final String exportTo;

    public DeletedMessagesVaultExportTaskDTO(@JsonProperty("type") String type,
                                             @JsonProperty("userExportFrom") String userExportFrom,
                                             @JsonProperty("exportQuery") QueryDTO exportQuery,
                                             @JsonProperty("exportTo") String exportTo) {
        this.type = type;
        this.userExportFrom = userExportFrom;
        this.exportQuery = exportQuery;
        this.exportTo = exportTo;
    }

    public String getUserExportFrom() {
        return userExportFrom;
    }

    public QueryDTO getExportQuery() {
        return exportQuery;
    }

    public String getExportTo() {
        return exportTo;
    }

    public String getType() {
        return type;
    }

}
