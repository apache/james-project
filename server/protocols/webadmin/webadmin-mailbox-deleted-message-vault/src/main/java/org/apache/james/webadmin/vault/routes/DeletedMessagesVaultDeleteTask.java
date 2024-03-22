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

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.apache.james.vault.DeletedMessageVault;

import reactor.core.publisher.Mono;

public class DeletedMessagesVaultDeleteTask implements Task {

    public static final TaskType TYPE = TaskType.of("deleted-messages-delete");

    public static class Factory {

        private final DeletedMessageVault deletedMessageVault;
        private final MessageId.Factory messageIdFactory;

        @Inject
        public Factory(DeletedMessageVault deletedMessageVault, MessageId.Factory messageIdFactory) {
            this.deletedMessageVault = deletedMessageVault;
            this.messageIdFactory = messageIdFactory;
        }

        public DeletedMessagesVaultDeleteTask create(DeletedMessagesVaultDeleteTaskDTO dto) {
            MessageId messageId = messageIdFactory.fromString(dto.getMessageId());
            Username username = Username.of(dto.getUserName());
            return new DeletedMessagesVaultDeleteTask(deletedMessageVault, username, messageId);
        }
    }

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {

        private final Username username;
        private final MessageId deleteMessageId;
        private final Instant timestamp;

        AdditionalInformation(Username username, MessageId deleteMessageId, Instant timestamp) {
            this.username = username;
            this.deleteMessageId = deleteMessageId;
            this.timestamp = timestamp;
        }

        public String getUsername() {
            return username.asString();
        }

        public String getDeleteMessageId() {
            return deleteMessageId.serialize();
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }
    }

    private final DeletedMessageVault vault;
    private final Username username;
    private final MessageId messageId;

    DeletedMessagesVaultDeleteTask(DeletedMessageVault vault, Username username, MessageId messageId) {
        this.vault = vault;
        this.username = username;
        this.messageId = messageId;
    }

    @Override
    public Result run() {
        return Mono.from(vault.delete(username, messageId))
            .doOnError(e -> LOGGER.error("Error while deleting message {} for user {} in DeletedMessageVault: {}", messageId, username, e))
            .thenReturn(Result.COMPLETED)
            .blockOptional()
            .orElse(Result.PARTIAL);
    }

    @Override
    public TaskType type() {
        return TYPE;
    }

    MessageId getMessageId() {
        return messageId;
    }

    Username getUsername() {
        return username;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(new AdditionalInformation(username, messageId, Clock.systemUTC().instant()));
    }
}
