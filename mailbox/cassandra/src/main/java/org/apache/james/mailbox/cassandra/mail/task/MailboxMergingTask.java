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

package org.apache.james.mailbox.cassandra.mail.task;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MailboxMergingTask implements Task {
    public static final TaskType MAILBOX_MERGING = TaskType.of("mailboxMerging");

    public static class Details implements TaskExecutionDetails.AdditionalInformation {
        private final CassandraId oldMailboxId;
        private final CassandraId newMailboxId;
        private final long totalMessageCount;
        private final long messageMovedCount;
        private final long messageFailedCount;


        public Details(CassandraId oldId, CassandraId newId, long totalMessageCount, long messageMovedCount, long messageFailedCount) {
            this.oldMailboxId = oldId;
            this.newMailboxId = newId;
            this.totalMessageCount = totalMessageCount;
            this.messageMovedCount = messageMovedCount;
            this.messageFailedCount = messageFailedCount;
        }

        public String getOldMailboxId() {
            return oldMailboxId.serialize();
        }

        public String getNewMailboxId() {
            return newMailboxId.serialize();
        }

        public long getTotalMessageCount() {
            return totalMessageCount;
        }

        public long getMessageMovedCount() {
            return messageMovedCount;
        }

        public long getMessageFailedCount() {
            return messageFailedCount;
        }
    }

    public static class Context {
        private final long totalMessageCount;
        private final AtomicLong totalMessageMoved;
        private final AtomicLong totalMessageFailed;

        public Context(long totalMessagesCount) {
            this.totalMessageCount = totalMessagesCount;
            this.totalMessageMoved = new AtomicLong(0L);
            this.totalMessageFailed = new AtomicLong(0L);
        }

        public long getTotalMessageCount() {
            return totalMessageCount;
        }

        public long getMessageMovedCount() {
            return totalMessageMoved.get();
        }

        public long getMessageFailedCount() {
            return totalMessageFailed.get();
        }

        public void incrementMovedCount() {
            totalMessageMoved.incrementAndGet();
        }

        public void incrementFailedCount() {
            totalMessageFailed.incrementAndGet();
        }
    }

    private static class MailboxMergingTaskDTO implements TaskDTO {
        private static final CassandraId.Factory CASSANDRA_ID_FACTORY = new CassandraId.Factory();

        public static MailboxMergingTaskDTO fromDTO(MailboxMergingTask domainObject, String typeName) {
            return new MailboxMergingTaskDTO(
                typeName,
                domainObject.context.totalMessageCount,
                domainObject.oldMailboxId.serialize(),
                domainObject.newMailboxId.serialize()
            );
        }

        private final String type;

        private final long totalMessageCount;
        private final String oldMailboxId;
        private final String newMailboxId;

        public MailboxMergingTaskDTO(@JsonProperty("type") String type,
                                     @JsonProperty("totalMessageCount") long totalMessageCount,
                                     @JsonProperty("oldMailboxId") String oldMailboxId,
                                     @JsonProperty("newMailboxId") String newMailboxId) {
            this.type = type;
            this.totalMessageCount = totalMessageCount;
            this.oldMailboxId = oldMailboxId;
            this.newMailboxId = newMailboxId;
        }

        private MailboxMergingTask toDTO(MailboxMergingTaskRunner taskRunner) {
            return new MailboxMergingTask(
                taskRunner,
                totalMessageCount,
                CASSANDRA_ID_FACTORY.fromString(oldMailboxId),
                CASSANDRA_ID_FACTORY.fromString(newMailboxId)
            );
        }

        @Override
        public String getType() {
            return type;
        }

        public long getTotalMessageCount() {
            return totalMessageCount;
        }

        public String getOldMailboxId() {
            return oldMailboxId;
        }

        public String getNewMailboxId() {
            return newMailboxId;
        }
    }

    public static final Function<MailboxMergingTaskRunner, TaskDTOModule<MailboxMergingTask, MailboxMergingTaskDTO>> MODULE = (taskRunner) ->
        DTOModule
            .forDomainObject(MailboxMergingTask.class)
            .convertToDTO(MailboxMergingTaskDTO.class)
            .toDomainObjectConverter(dto -> dto.toDTO(taskRunner))
            .toDTOConverter(MailboxMergingTaskDTO::fromDTO)
            .typeName(MAILBOX_MERGING.asString())
            .withFactory(TaskDTOModule::new);

    private final MailboxMergingTaskRunner taskRunner;
    private final CassandraId oldMailboxId;
    private final CassandraId newMailboxId;
    private final Context context;

    public MailboxMergingTask(MailboxMergingTaskRunner taskRunner, long totalMessagesToMove, CassandraId oldMailboxId, CassandraId newMailboxId) {
        this.taskRunner = taskRunner;
        this.oldMailboxId = oldMailboxId;
        this.newMailboxId = newMailboxId;
        this.context = new Context(totalMessagesToMove);
    }

    @Override
    public Result run() {
        return taskRunner.run(oldMailboxId, newMailboxId, context);
    }

    @Override
    public TaskType type() {
        return MAILBOX_MERGING;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(new Details(oldMailboxId, newMailboxId,
            context.getTotalMessageCount(),
            context.getMessageMovedCount(),
            context.getMessageFailedCount()));
    }
}
