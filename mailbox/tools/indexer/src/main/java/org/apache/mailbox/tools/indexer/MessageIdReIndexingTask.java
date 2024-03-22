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
package org.apache.mailbox.tools.indexer;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.mailbox.model.MessageId;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

public class MessageIdReIndexingTask implements Task {
    public static final TaskType TYPE = TaskType.of("messageId-reindexing");

    public static class Factory {
        private final ReIndexerPerformer reIndexerPerformer;
        private final MessageId.Factory messageIdFactory;

        @Inject
        public Factory(ReIndexerPerformer reIndexerPerformer, MessageId.Factory messageIdFactory) {
            this.reIndexerPerformer = reIndexerPerformer;
            this.messageIdFactory = messageIdFactory;
        }

        public MessageIdReIndexingTask create(MessageIdReindexingTaskDTO dto) {
            MessageId messageId = messageIdFactory.fromString(dto.getMessageId());
            return new MessageIdReIndexingTask(reIndexerPerformer, messageId);
        }
    }

    public static final class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final MessageId messageId;
        private final Instant timestamp;

        AdditionalInformation(MessageId messageId, Instant timestamp) {
            this.messageId = messageId;
            this.timestamp = timestamp;
        }

        public String getMessageId() {
            return messageId.serialize();
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }
    }


    private final ReIndexerPerformer reIndexerPerformer;
    private final MessageId messageId;

    MessageIdReIndexingTask(ReIndexerPerformer reIndexerPerformer, MessageId messageId) {
        this.reIndexerPerformer = reIndexerPerformer;
        this.messageId = messageId;
    }

    @Override
    public Result run() {
        return reIndexerPerformer.reIndexMessageId(messageId).block();
    }

    @Override
    public TaskType type() {
        return TYPE;
    }

    MessageId getMessageId() {
        return messageId;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(new AdditionalInformation(messageId, Clock.systemUTC().instant()));
    }
}
