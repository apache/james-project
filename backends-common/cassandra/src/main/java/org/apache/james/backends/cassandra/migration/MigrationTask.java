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

package org.apache.james.backends.cassandra.migration;

import static org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager.DEFAULT_VERSION;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.SchemaTransition;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;

import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class MigrationTask implements Task {

    public interface Factory {
        MigrationTask create(SchemaVersion target);
    }

    public static class Impl implements Factory {
        private final CassandraSchemaVersionDAO schemaVersionDAO;
        private final CassandraSchemaTransitions transitions;

        @Inject
        private Impl(CassandraSchemaVersionDAO schemaVersionDAO, CassandraSchemaTransitions transitions) {
            this.schemaVersionDAO = schemaVersionDAO;
            this.transitions = transitions;
        }

        @Override
        public MigrationTask create(SchemaVersion target) {
            return new MigrationTask(schemaVersionDAO, transitions, target);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationTask.class);

    public static final TaskType CASSANDRA_MIGRATION = TaskType.of("cassandra-migration");

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {

        private final SchemaVersion toVersion;
        private final Instant timestamp;

        public AdditionalInformation(SchemaVersion toVersion, Instant timestamp) {
            this.toVersion = toVersion;
            this.timestamp = timestamp;
        }

        public int getToVersion() {
            return toVersion.getValue();
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }
    }

    private final CassandraSchemaVersionDAO schemaVersionDAO;
    private final CassandraSchemaTransitions transitions;
    private final SchemaVersion target;

    @VisibleForTesting
    public MigrationTask(CassandraSchemaVersionDAO schemaVersionDAO, CassandraSchemaTransitions transitions, SchemaVersion target) {
        this.schemaVersionDAO = schemaVersionDAO;
        this.transitions = transitions;
        this.target = target;
    }

    @Override
    public Result run() {
        getCurrentVersion().listTransitionsForTarget(target)
            .stream()
            .map(this::migration)
            .forEach(Throwing.consumer(this::runMigration).sneakyThrow());
        return Result.COMPLETED;
    }

    private SchemaVersion getCurrentVersion() {
        return schemaVersionDAO.getCurrentSchemaVersion().block().orElse(DEFAULT_VERSION);
    }

    private Tuple2<SchemaTransition, Migration> migration(SchemaTransition transition) {
        return Tuples.of(
            transition,
            transitions.findMigration(transition)
                .orElseThrow(() -> new MigrationException("unable to find a required Migration for transition " + transition)));
    }

    private void runMigration(Tuple2<SchemaTransition, Migration> tuple) throws InterruptedException {
        SchemaVersion currentVersion = getCurrentVersion();
        SchemaTransition transition = tuple.getT1();
        SchemaVersion targetVersion = transition.to();
        if (currentVersion.isAfterOrEquals(targetVersion)) {
            return;
        }

        LOGGER.info("Migrating to version {} ", transition.toAsString());
        Migration migration = tuple.getT2();
        migration.asTask().run()
            .onComplete(
                () -> schemaVersionDAO.updateVersion(transition.to()).block(),
                () -> LOGGER.info("Migrating to version {} done", transition.toAsString()))
            .onFailure(
                () -> LOGGER.warn(failureMessage(transition.to())),
                () -> throwMigrationException(transition.to()));
    }

    private void throwMigrationException(SchemaVersion newVersion) {
        throw new MigrationException(failureMessage(newVersion));
    }

    private String failureMessage(SchemaVersion newVersion) {
        return String.format("Migrating to version %d partially done. " +
            "Please check logs for cause of failure and re-run this migration.", newVersion.getValue());
    }

    SchemaVersion getTarget() {
        return target;
    }

    @Override
    public TaskType type() {
        return CASSANDRA_MIGRATION;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(new AdditionalInformation(target, Clock.systemUTC().instant()));
    }

}
