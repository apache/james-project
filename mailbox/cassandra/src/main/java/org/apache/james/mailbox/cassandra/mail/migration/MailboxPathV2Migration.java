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
        this.additionalInformation = new AdditionalInformation(() -> daoV1.countAll().join());
    }

    @Override
    public Result run() {
        try {
            return daoV1.readAll()
                .join()
                .map(this::migrate)
                .reduce(Result.COMPLETED, Task::combine);
        } catch (Exception e) {
            LOGGER.error("Error while performing migration", e);
            return Result.PARTIAL;
        }
    }

    public Result migrate(CassandraIdAndPath idAndPath) {
        try {
            daoV2.save(idAndPath.getMailboxPath(), idAndPath.getCassandraId()).join();

            daoV1.delete(idAndPath.getMailboxPath()).join();
            return Result.COMPLETED;
        } catch (Exception e) {
            LOGGER.error("Error while performing migration for path {}", idAndPath.getMailboxPath(), e);
            return Result.PARTIAL;
        }
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
