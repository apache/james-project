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

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import javax.inject.Inject;
import javax.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.User;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.vault.dto.query.QueryDTO;
import org.apache.james.vault.dto.query.QueryTranslator;
import org.apache.james.vault.search.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;

class DeletedMessagesVaultExportTask implements Task {

    static final String TYPE = "deletedMessages/export";

    public static final Function<DeletedMessagesVaultExportTask.Factory, TaskDTOModule<DeletedMessagesVaultExportTask, DeletedMessagesVaultExportTaskDTO>> MODULE = (factory) ->
        DTOModule
            .forDomainObject(DeletedMessagesVaultExportTask.class)
            .convertToDTO(DeletedMessagesVaultExportTask.DeletedMessagesVaultExportTaskDTO.class)
            .toDomainObjectConverter(dto -> {
                try {
                    return factory.create(dto);
                } catch (AddressException e) {
                    throw new RuntimeException(e);
                }
            })
            .toDTOConverter(factory::createDTO)
            .typeName(TYPE)
            .withFactory(TaskDTOModule::new);

    public static class DeletedMessagesVaultExportTaskDTO implements TaskDTO {

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

    public static class Factory {

        private final ExportService exportService;
        private final QueryTranslator queryTranslator;

        @Inject
        public Factory(ExportService exportService, QueryTranslator queryTranslator) {
            this.exportService = exportService;
            this.queryTranslator = queryTranslator;
        }

        public DeletedMessagesVaultExportTask create(DeletedMessagesVaultExportTask.DeletedMessagesVaultExportTaskDTO dto) throws AddressException {
            User userExportFrom = User.fromUsername(dto.userExportFrom);
            Query exportQuery = queryTranslator.translate(dto.exportQuery);
            MailAddress exportTo = new MailAddress(dto.exportTo);
            return new DeletedMessagesVaultExportTask(exportService, userExportFrom, exportQuery, exportTo);
        }

        public DeletedMessagesVaultExportTask.DeletedMessagesVaultExportTaskDTO createDTO(DeletedMessagesVaultExportTask task, String type) {
            return new DeletedMessagesVaultExportTask.DeletedMessagesVaultExportTaskDTO(type, task.userExportFrom.asString(), queryTranslator.toDTO(task.exportQuery), task.exportTo.asString());
        }
    }

    public class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {

        private final User userExportFrom;
        private final MailAddress exportTo;
        private final long totalExportedMessages;

        public AdditionalInformation(User userExportFrom, MailAddress exportTo, long totalExportedMessages) {
            this.userExportFrom = userExportFrom;
            this.exportTo = exportTo;
            this.totalExportedMessages = totalExportedMessages;
        }

        public String getUserExportFrom() {
            return userExportFrom.asString();
        }

        public String getExportTo() {
            return exportTo.asString();
        }

        public long getTotalExportedMessages() {
            return totalExportedMessages;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(DeletedMessagesVaultExportTask.class);

    private final ExportService exportService;
    private final User userExportFrom;
    @VisibleForTesting
    public final Query exportQuery;
    private final MailAddress exportTo;
    private final AtomicLong totalExportedMessages;

    DeletedMessagesVaultExportTask(ExportService exportService, User userExportFrom, Query exportQuery, MailAddress exportTo) {
        this.exportService = exportService;
        this.userExportFrom = userExportFrom;
        this.exportQuery = exportQuery;
        this.exportTo = exportTo;
        this.totalExportedMessages = new AtomicLong();
    }

    @Override
    public Result run() {
        try {
            Runnable messageToShareCallback = totalExportedMessages::incrementAndGet;
            exportService.export(userExportFrom, exportQuery, exportTo, messageToShareCallback);
            return Result.COMPLETED;
        } catch (IOException e) {
            LOGGER.error("Error happens when exporting deleted messages from {} to {}", userExportFrom.asString(), exportTo.asString());
            return Result.PARTIAL;
        }
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(new AdditionalInformation(userExportFrom, exportTo, totalExportedMessages.get()));
    }
}
