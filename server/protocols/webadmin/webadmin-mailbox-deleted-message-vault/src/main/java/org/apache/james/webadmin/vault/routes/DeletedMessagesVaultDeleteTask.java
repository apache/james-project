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

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.core.User;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.apache.james.vault.DeletedMessageVault;

import reactor.core.publisher.Mono;

public class DeletedMessagesVaultDeleteTask implements Task {

    public static final TaskType TYPE = TaskType.of("deletedMessages/delete");

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
            User user = User.fromUsername(dto.getUserName());
            return new DeletedMessagesVaultDeleteTask(deletedMessageVault, user, messageId);
        }
    }

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {

        private final User user;
        private final MessageId deleteMessageId;

        AdditionalInformation(User user, MessageId deleteMessageId) {
            this.user = user;
            this.deleteMessageId = deleteMessageId;
        }

        public String getUser() {
            return user.asString();
        }

        public String getDeleteMessageId() {
            return deleteMessageId.serialize();
        }
    }

    private final DeletedMessageVault vault;
    private final User user;
    private final MessageId messageId;

    DeletedMessagesVaultDeleteTask(DeletedMessageVault vault, User user, MessageId messageId) {
        this.vault = vault;
        this.user = user;
        this.messageId = messageId;
    }

    @Override
    public Result run() {
        return Mono.from(vault.delete(user, messageId))
            .doOnError(e -> LOGGER.error("Error while deleting message {} for user {} in DeletedMessageVault: {}", messageId, user, e))
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

    User getUser() {
        return user;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(new AdditionalInformation(user, messageId));
    }

}
