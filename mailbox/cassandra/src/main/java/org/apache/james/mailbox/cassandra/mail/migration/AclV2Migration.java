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
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.CassandraACLMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxDAO;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.scheduler.Schedulers;

public class AclV2Migration implements Migration {
    private static final int CONCURRENCY = 20;

    static class AclV2MigrationTask implements Task {
        private final AclV2Migration migration;

        AclV2MigrationTask(AclV2Migration migration) {
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

    public static final Logger LOGGER = LoggerFactory.getLogger(AclV2Migration.class);
    public static final TaskType TYPE = TaskType.of("acl-v2-migration");

    private final CassandraMailboxDAO mailboxDAO;
    private final CassandraACLMapper.StoreV1 storeV1;
    private final CassandraACLMapper.StoreV2 storeV2;

    @Inject
    public AclV2Migration(CassandraMailboxDAO mailboxDAO, CassandraACLMapper.StoreV1 storeV1, CassandraACLMapper.StoreV2 storeV2) {
        this.mailboxDAO = mailboxDAO;
        this.storeV1 = storeV1;
        this.storeV2 = storeV2;
    }

    @Override
    public void apply() {
        mailboxDAO.retrieveAllMailboxes()
            .flatMap(mailbox -> {
                CassandraId id = (CassandraId) mailbox.getMailboxId();
                return storeV1.getACL(id)
                    .flatMap(acl -> storeV2.setACL(id, acl));
            }, CONCURRENCY)
            .doOnError(t -> LOGGER.error("Error while performing migration", t))
            .subscribeOn(Schedulers.elastic())
            .blockLast();
    }

    @Override
    public Task asTask() {
        return new AclV2MigrationTask(this);
    }

    AdditionalInformation getAdditionalInformation() {
        return new AdditionalInformation(Clock.systemUTC().instant());
    }
}
