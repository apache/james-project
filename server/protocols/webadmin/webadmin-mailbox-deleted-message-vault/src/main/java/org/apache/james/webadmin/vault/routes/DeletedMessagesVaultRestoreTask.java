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

import static org.apache.james.webadmin.vault.routes.RestoreService.RestoreResult.RESTORE_SUCCEED;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import javax.inject.Inject;

import org.apache.james.core.User;
import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.exception.MailboxException;
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

class DeletedMessagesVaultRestoreTask implements Task {

    static final String TYPE = "deletedMessages/restore";

    public static final Function<DeletedMessagesVaultRestoreTask.Factory, TaskDTOModule> MODULE = (factory) ->
        DTOModule
            .forDomainObject(DeletedMessagesVaultRestoreTask.class)
            .convertToDTO(DeletedMessagesVaultRestoreTask.DeletedMessagesVaultRestoreTaskDTO.class)
            .toDomainObjectConverter(factory::create)
            .toDTOConverter(factory::createDTO)
            .typeName(TYPE)
            .withFactory(TaskDTOModule::new);

    public static class DeletedMessagesVaultRestoreTaskDTO implements TaskDTO {

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

    public static class Factory {

        private final RestoreService restoreService;
        private final QueryTranslator queryTranslator;

        @Inject
        public Factory(RestoreService restoreService, QueryTranslator queryTranslator) {
            this.restoreService = restoreService;
            this.queryTranslator = queryTranslator;
        }

        public DeletedMessagesVaultRestoreTask create(DeletedMessagesVaultRestoreTask.DeletedMessagesVaultRestoreTaskDTO dto) {
            User userToRestore = User.fromUsername(dto.userToRestore);
            Query query = queryTranslator.translate(dto.query);
            return new DeletedMessagesVaultRestoreTask(restoreService, userToRestore, query);
        }

        public DeletedMessagesVaultRestoreTask.DeletedMessagesVaultRestoreTaskDTO createDTO(DeletedMessagesVaultRestoreTask task, String type) {
            return new DeletedMessagesVaultRestoreTask.DeletedMessagesVaultRestoreTaskDTO(type, task.userToRestore.asString(), queryTranslator.toDTO(task.query));
        }
    }

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final User user;
        private final AtomicLong successfulRestoreCount;
        private final AtomicLong errorRestoreCount;

        AdditionalInformation(User user) {
            this.user = user;
            this.successfulRestoreCount = new AtomicLong();
            this.errorRestoreCount = new AtomicLong();
        }

        public long getSuccessfulRestoreCount() {
            return successfulRestoreCount.get();
        }

        public long getErrorRestoreCount() {
            return errorRestoreCount.get();
        }

        public String getUser() {
            return user.asString();
        }

        void incrementSuccessfulRestoreCount() {
            successfulRestoreCount.incrementAndGet();
        }

        void incrementErrorRestoreCount() {
            errorRestoreCount.incrementAndGet();
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(DeletedMessagesVaultRestoreTask.class);

    private final User userToRestore;
    private final RestoreService vaultRestore;
    private final AdditionalInformation additionalInformation;
    @VisibleForTesting
    final Query query;

    DeletedMessagesVaultRestoreTask(RestoreService vaultRestore, User userToRestore, Query query) {
        this.query = query;
        this.userToRestore = userToRestore;
        this.vaultRestore = vaultRestore;
        this.additionalInformation = new AdditionalInformation(userToRestore);
    }

    @Override
    public Result run() {
        try {
            return vaultRestore.restore(userToRestore, query).toStream()
                .peek(this::updateInformation)
                .map(this::restoreResultToTaskResult)
                .reduce(Task::combine)
                .orElse(Result.COMPLETED);
        } catch (MailboxException e) {
            LOGGER.error("Error happens while restoring user {}", userToRestore.asString(), e);
            return Result.PARTIAL;
        }
    }

    private Task.Result restoreResultToTaskResult(RestoreService.RestoreResult restoreResult) {
        if (restoreResult.equals(RESTORE_SUCCEED)) {
            return Result.COMPLETED;
        }
        return Result.PARTIAL;
    }

    private void updateInformation(RestoreService.RestoreResult restoreResult) {
        switch (restoreResult) {
            case RESTORE_FAILED:
                additionalInformation.incrementErrorRestoreCount();
                break;
            case RESTORE_SUCCEED:
                additionalInformation.incrementSuccessfulRestoreCount();
                break;
        }
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(additionalInformation);
    }
}
