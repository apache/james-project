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

package org.apache.james.rrt.cassandra.migration;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.migration.Migration;
import org.apache.james.backends.cassandra.migration.MigrationException;
import org.apache.james.rrt.cassandra.CassandraMappingsSourcesDAO;
import org.apache.james.rrt.cassandra.CassandraRecipientRewriteTableDAO;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

public class MappingsSourcesMigration implements Migration {

    private static class MappingsSourcesMigrationTask implements Task {

        private final MappingsSourcesMigration migration;

        public MappingsSourcesMigrationTask(MappingsSourcesMigration migration) {
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
            return Optional.of(migration.createAdditionalInformation());
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(MappingsSourcesMigration.class);
    private static final TaskType TYPE = TaskType.of("mappingsSourcesMigration");

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final long successfulMappingsCount;
        private final long errorMappingsCount;

        AdditionalInformation(long successfulMappingsCount, long errorMappingsCount) {
            this.successfulMappingsCount = successfulMappingsCount;
            this.errorMappingsCount = errorMappingsCount;
        }

        public long getSuccessfulMappingsCount() {
            return successfulMappingsCount;
        }

        public long getErrorMappingsCount() {
            return errorMappingsCount;
        }
    }

    private final CassandraRecipientRewriteTableDAO cassandraRecipientRewriteTableDAO;
    private final CassandraMappingsSourcesDAO cassandraMappingsSourcesDAO;
    private final AtomicLong successfulMappingsCount;
    private final AtomicLong errorMappingsCount;

    @Inject
    public MappingsSourcesMigration(CassandraRecipientRewriteTableDAO cassandraRecipientRewriteTableDAO,
                                    CassandraMappingsSourcesDAO cassandraMappingsSourcesDAO) {
        this.cassandraRecipientRewriteTableDAO = cassandraRecipientRewriteTableDAO;
        this.cassandraMappingsSourcesDAO = cassandraMappingsSourcesDAO;
        this.successfulMappingsCount = new AtomicLong(0);
        this.errorMappingsCount = new AtomicLong(0);
    }

    @Override
    public void apply() {
        cassandraRecipientRewriteTableDAO.getAllMappings()
            .flatMap(this::migrate)
            .then(Mono.fromRunnable(() -> {
                if (errorMappingsCount.get() > 0) {
                    throw new MigrationException("MappingsSourcesMigration failed");
                }
            }))
            .doOnError(t -> LOGGER.error("Error while migrating mappings sources", t))
            .block();
    }

    private Mono<Void> migrate(Pair<MappingSource, Mapping> mappingEntry) {
        return cassandraMappingsSourcesDAO.addMapping(mappingEntry.getRight(), mappingEntry.getLeft())
            .then(Mono.fromCallable(successfulMappingsCount::incrementAndGet))
            .then()
            .onErrorResume(t -> {
                LOGGER.error("Error while performing migration of mapping source: {} with mapping: {}",
                    mappingEntry.getLeft().asString(), mappingEntry.getRight().asString(), t);
                errorMappingsCount.incrementAndGet();
                return Mono.empty();
            });
    }

    @Override
    public Task asTask() {
        return new MappingsSourcesMigrationTask(this);
    }

    AdditionalInformation createAdditionalInformation() {
        return new AdditionalInformation(
            successfulMappingsCount.get(),
            errorMappingsCount.get());
    }
}
