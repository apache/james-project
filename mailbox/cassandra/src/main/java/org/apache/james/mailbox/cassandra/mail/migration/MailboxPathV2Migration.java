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
import org.apache.james.mailbox.cassandra.mail.CassandraIdAndPath;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathDAOImpl;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathV2DAO;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

public class MailboxPathV2Migration implements Migration {

    static class MailboxPathV2MigrationTask implements Task {
        private final MailboxPathV2Migration migration;

        MailboxPathV2MigrationTask(MailboxPathV2Migration migration) {
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
        private final long remainingCount;
        private final long initialCount;
        private final Instant timestamp;

        public AdditionalInformation(long remainingCount, long initialCount, Instant timestamp) {
            this.remainingCount = remainingCount;
            this.initialCount = initialCount;
            this.timestamp = timestamp;
        }

        public long getRemainingCount() {
            return remainingCount;
        }

        public long getInitialCount() {
            return initialCount;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }
    }

    public static final Logger LOGGER = LoggerFactory.getLogger(MailboxPathV2Migration.class);
    public static final TaskType TYPE = TaskType.of("cassandra-mailbox-path-v2-migration");
    private final CassandraMailboxPathDAOImpl daoV1;
    private final CassandraMailboxPathV2DAO daoV2;
    private final long initialCount;

    @Inject
    public MailboxPathV2Migration(CassandraMailboxPathDAOImpl daoV1, CassandraMailboxPathV2DAO daoV2) {
        this.daoV1 = daoV1;
        this.daoV2 = daoV2;
        this.initialCount = getCurrentCount();
    }

    @Override
    public void apply() {
        daoV1.readAll()
            .flatMap(this::migrate)
            .doOnError(t -> LOGGER.error("Error while performing migration", t))
            .blockLast();
    }

    private Mono<Void> migrate(CassandraIdAndPath idAndPath) {
        return daoV2.save(idAndPath.getMailboxPath(), idAndPath.getCassandraId())
            .then(daoV1.delete(idAndPath.getMailboxPath()))
            .onErrorResume(error -> handleErrorMigrate(idAndPath, error))
            .then();
    }

    private Mono<Void> handleErrorMigrate(CassandraIdAndPath idAndPath, Throwable throwable) {
        LOGGER.error("Error while performing migration for path {}", idAndPath.getMailboxPath(), throwable);
        return Mono.error(throwable);
    }

    @Override
    public Task asTask() {
        return new MailboxPathV2MigrationTask(this);
    }

    AdditionalInformation getAdditionalInformation() {
        return new AdditionalInformation(getCurrentCount(), initialCount, Clock.systemUTC().instant());
    }

    private Long getCurrentCount() {
        return daoV1.countAll().block();
    }
}
