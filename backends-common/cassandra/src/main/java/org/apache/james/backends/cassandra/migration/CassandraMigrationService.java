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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.SchemaTransition;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CassandraMigrationService {
    public static final String LATEST_VERSION = "latestVersion";
    private final CassandraSchemaVersionDAO schemaVersionDAO;
    private final SchemaVersion latestVersion;
    private final Map<SchemaTransition, Migration> allMigrationClazz;
    private final Logger logger = LoggerFactory.getLogger(CassandraMigrationService.class);

    @Inject
    public CassandraMigrationService(CassandraSchemaVersionDAO schemaVersionDAO, Map<SchemaTransition, Migration> allMigrationClazz, @Named(LATEST_VERSION) SchemaVersion latestVersion) {
        this.schemaVersionDAO = schemaVersionDAO;
        this.latestVersion = latestVersion;
        this.allMigrationClazz = allMigrationClazz;
    }

    public Optional<SchemaVersion> getCurrentVersion() {
        return schemaVersionDAO.getCurrentSchemaVersion().block();
    }

    public Optional<SchemaVersion> getLatestVersion() {
        return Optional.of(latestVersion);
    }

    public Task upgradeToVersion(SchemaVersion newVersion) {
        SchemaVersion currentVersion = getCurrentVersion().orElse(DEFAULT_VERSION);

        List<Migration> migrations = new ArrayList<>();
        SchemaVersion migrateTo = currentVersion.next();
        while (newVersion.isAfterOrEquals(migrateTo)) {
            SchemaTransition transition = SchemaTransition.to(migrateTo);
            validateTransitionExists(transition);
            migrations.add(toMigration(transition));
            migrateTo = migrateTo.next();
        }
        return new MigrationTask(migrations, newVersion);
    }

    private SchemaTransition validateTransitionExists(SchemaTransition transition) {
        if (!allMigrationClazz.containsKey(transition)) {
            String message = String.format("Can not migrate from %s to %s. No migration class registered.", transition.fromAsString(), transition.toAsString());
            logger.error(message);
            throw new NotImplementedException(message);
        }
        return transition;
    }

    public Task upgradeToLastVersion() {
        return upgradeToVersion(latestVersion);
    }

    private Migration toMigration(SchemaTransition transition) {
        return () -> {
            SchemaVersion currentVersion = getCurrentVersion().orElse(DEFAULT_VERSION);
            SchemaVersion targetVersion = transition.to();
            if (currentVersion.isAfterOrEquals(targetVersion)) {
                return;
            }

            logger.info("Migrating to version {} ", transition.toAsString());
            allMigrationClazz.get(transition).asTask().run()
                .onComplete(() -> schemaVersionDAO.updateVersion(transition.to()).block(),
                    () -> logger.info("Migrating to version {} done", transition.toAsString()))
                .onFailure(() -> logger.warn(failureMessage(transition.to())),
                    () -> throwMigrationException(transition.to()));
        };
    }

    private void throwMigrationException(SchemaVersion newVersion) {
        throw new MigrationException(failureMessage(newVersion));
    }

    private String failureMessage(SchemaVersion newVersion) {
        return String.format("Migrating to version %d partially done. " +
                "Please check logs for cause of failure and re-run this migration.", newVersion.getValue());
    }

}
