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
import java.util.function.Supplier;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.migration.Migration;
import org.apache.james.mailbox.cassandra.mail.CassandraIdAndPath;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathDAOImpl;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathV2DAO;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

public class MailboxPathV2Migration implements Migration {

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final Supplier<Long> countSupplier;
        private final long initialCount;

        public AdditionalInformation(Supplier<Long> countSupplier) {
            this.countSupplier = countSupplier;
            this.initialCount = countSupplier.get();
        }

        public long getRemainingCount() {
            return countSupplier.get();
        }

        public long getInitialCount() {
            return initialCount;
        }
    }

    public static final Logger LOGGER = LoggerFactory.getLogger(MailboxPathV2Migration.class);
    private final CassandraMailboxPathDAOImpl daoV1;
    private final CassandraMailboxPathV2DAO daoV2;
    private final AdditionalInformation additionalInformation;

    @Inject
    public MailboxPathV2Migration(CassandraMailboxPathDAOImpl daoV1, CassandraMailboxPathV2DAO daoV2) {
        this.daoV1 = daoV1;
        this.daoV2 = daoV2;
        this.additionalInformation = new AdditionalInformation(() -> daoV1.countAll().block());
    }

    @Override
    public Result run() {
        return daoV1.readAll()
            .flatMap(this::migrate)
            .reduce(Result.COMPLETED, Task::combine)
            .onErrorResume(this::handleErrorRun)
            .block();
    }

    private Mono<Result> handleErrorRun(Throwable throwable) {
            LOGGER.error("Error while performing migration", throwable);
            return Mono.just(Result.PARTIAL);
    }

    public Mono<Result> migrate(CassandraIdAndPath idAndPath) {
        return daoV2.save(idAndPath.getMailboxPath(), idAndPath.getCassandraId())
            .then(daoV1.delete(idAndPath.getMailboxPath()))
            .thenReturn(Result.COMPLETED)
            .onErrorResume(error -> handleErrorMigrate(idAndPath, error));
    }

    private Mono<Result> handleErrorMigrate(CassandraIdAndPath idAndPath, Throwable throwable) {
        LOGGER.error("Error while performing migration for path {}", idAndPath.getMailboxPath(), throwable);
        return Mono.just(Result.PARTIAL);
    }

    @Override
    public String type() {
        return "Cassandra_mailboxPathV2Migration";
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(additionalInformation);
    }
}
