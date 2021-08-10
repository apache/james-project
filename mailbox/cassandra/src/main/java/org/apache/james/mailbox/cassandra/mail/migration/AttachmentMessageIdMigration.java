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

package org.apache.james.mailbox.cassandra.mail.migration;

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.migration.Migration;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAOV2;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentMessageIdDAO;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

public class AttachmentMessageIdMigration implements Migration {
    static class AttachmentMessageIdMigrationTask implements Task {
        private final AttachmentMessageIdMigration migration;

        AttachmentMessageIdMigrationTask(AttachmentMessageIdMigration migration) {
            this.migration = migration;
        }

        @Override
        public Result run() throws InterruptedException {
            return migration.runTask();
        }

        @Override
        public TaskType type() {
            return TYPE;
        }

        @Override
        public Optional<TaskExecutionDetails.AdditionalInformation> details() {
            return Optional.of(migration.getAdditionalInformation());
        }
    }

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final Instant timestamp;

        public AdditionalInformation(Instant timestamp) {
            this.timestamp = timestamp;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }
    }

    public static final Logger LOGGER = LoggerFactory.getLogger(AttachmentMessageIdMigration.class);
    public static final TaskType TYPE = TaskType.of("cassandra-attachment-messageid-migration");
    private final CassandraAttachmentMessageIdDAO attachmentMessageIdDAO;
    private final CassandraAttachmentDAOV2 attachmentDAO;

    @Inject
    public AttachmentMessageIdMigration(CassandraAttachmentMessageIdDAO attachmentMessageIdDAO, CassandraAttachmentDAOV2 attachmentDAO) {
        this.attachmentMessageIdDAO = attachmentMessageIdDAO;
        this.attachmentDAO = attachmentDAO;
    }

    @Override
    public void apply() {
        attachmentMessageIdDAO.listAll()
            .flatMap(this::migrate, DEFAULT_CONCURRENCY)
            .blockLast();
    }

    private Mono<Void> migrate(Pair<AttachmentId, MessageId> entry) {
        return attachmentDAO.insertMessageId(entry.getKey(), entry.getValue());
    }

    @Override
    public Task asTask() {
        return new AttachmentMessageIdMigrationTask(this);
    }

    AdditionalInformation getAdditionalInformation() {
        return new AdditionalInformation(Clock.systemUTC().instant());
    }
}
