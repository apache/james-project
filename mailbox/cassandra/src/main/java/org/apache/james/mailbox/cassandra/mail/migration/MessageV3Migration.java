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

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.migration.Migration;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAOV3;
import org.apache.james.mailbox.cassandra.mail.MessageRepresentation;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

public class MessageV3Migration implements Migration {
    private static final int CONCURRENCY = 50;

    static class MessageV3MigrationTask implements Task {
        private final MessageV3Migration migration;

        MessageV3MigrationTask(MessageV3Migration migration) {
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

    public static final Logger LOGGER = LoggerFactory.getLogger(MessageV3Migration.class);
    public static final TaskType TYPE = TaskType.of("cassandra-message-v3-migration");
    private final CassandraMessageDAO daoV2;
    private final CassandraMessageDAOV3 daoV3;

    @Inject
    public MessageV3Migration(CassandraMessageDAO daoV2, CassandraMessageDAOV3 daoV3) {
        this.daoV2 = daoV2;
        this.daoV3 = daoV3;
    }

    @Override
    public void apply() {
        daoV2.list()
            .flatMap(this::migrate, CONCURRENCY)
            .doOnError(t -> LOGGER.error("Error while performing migration", t))
            .blockLast();
    }

    private Mono<Void> migrate(MessageRepresentation messageRepresentation) {
        return daoV3.save(messageRepresentation)
            .then(daoV2.delete((CassandraMessageId) messageRepresentation.getMessageId()))
            .onErrorResume(error -> handleErrorMigrate(messageRepresentation, error))
            .then();
    }

    private Mono<Void> handleErrorMigrate(MessageRepresentation messageRepresentation, Throwable throwable) {
        LOGGER.error("Error while performing migration for {}", messageRepresentation.getMessageId(), throwable);
        return Mono.empty();
    }

    @Override
    public Task asTask() {
        return new MessageV3MigrationTask(this);
    }

    AdditionalInformation getAdditionalInformation() {
        return new AdditionalInformation(Clock.systemUTC().instant());
    }
}
