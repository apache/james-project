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

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.migration.Migration;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAOV3;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageMetadata;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class MessageDenormalizationMigration implements Migration {
    static class MessageDenormalizationMigrationTask implements Task {
        private final MessageDenormalizationMigration migration;

        MessageDenormalizationMigrationTask(MessageDenormalizationMigration migration) {
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
            return Optional.empty();
        }
    }

    public static final Logger LOGGER = LoggerFactory.getLogger(MessageDenormalizationMigration.class);
    public static final TaskType TYPE = TaskType.of("message-denormalization-migration");

    private final CassandraMessageDAOV3 messageDAOV3;
    private final CassandraMessageIdToImapUidDAO imapUidDAO;
    private final CassandraMessageIdDAO messageIdDAO;

    @Inject
    public MessageDenormalizationMigration(CassandraMessageDAOV3 messageDAOV3, CassandraMessageIdToImapUidDAO imapUidDAO, CassandraMessageIdDAO messageIdDAO) {
        this.messageDAOV3 = messageDAOV3;
        this.imapUidDAO = imapUidDAO;
        this.messageIdDAO = messageIdDAO;
    }

    @Override
    public void apply() {
        messageIdDAO.retrieveAllMessages()
            .filter(metadata -> !metadata.isComplete())
            .flatMap(this::retrieveFullMetadata, ReactorUtils.DEFAULT_CONCURRENCY)
            .flatMap(messageIdDAO::insert, 4)
            .subscribeOn(Schedulers.elastic())
            .then()
            .block();

        imapUidDAO.retrieveAllMessages()
            .filter(metadata -> !metadata.isComplete())
            .flatMap(this::retrieveFullMetadata, ReactorUtils.DEFAULT_CONCURRENCY)
            .concatMap(imapUidDAO::insertForce, 4)
            .subscribeOn(Schedulers.elastic())
            .then()
            .block();
    }

    private Mono<CassandraMessageMetadata> retrieveFullMetadata(CassandraMessageMetadata metadata) {
        return messageDAOV3.retrieveMessage(metadata.getComposedMessageId(), MessageMapper.FetchType.Metadata)
            .map(messageRepresentation -> CassandraMessageMetadata.builder()
                .ids(metadata.getComposedMessageId())
                .bodyStartOctet(messageRepresentation.getBodyStartOctet())
                .size(messageRepresentation.getSize())
                .internalDate(messageRepresentation.getInternalDate())
                .headerContent(Optional.of(messageRepresentation.getHeaderId()))
                .build());
    }

    @Override
    public Task asTask() {
        return new MessageDenormalizationMigrationTask(this);
    }
}
