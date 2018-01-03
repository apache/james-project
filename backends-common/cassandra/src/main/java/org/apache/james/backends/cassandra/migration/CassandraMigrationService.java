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

import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CassandraMigrationService {
    public static final String LATEST_VERSION = "latestVersion";
    private final CassandraSchemaVersionDAO schemaVersionDAO;
    private final SchemaVersion latestVersion;
    private final Map<SchemaVersion, Migration> allMigrationClazz;
    private final Logger LOG = LoggerFactory.getLogger(CassandraMigrationService.class);

    @Inject
    public CassandraMigrationService(CassandraSchemaVersionDAO schemaVersionDAO, Map<SchemaVersion, Migration> allMigrationClazz, @Named(LATEST_VERSION) SchemaVersion latestVersion) {
        this.schemaVersionDAO = schemaVersionDAO;
        this.latestVersion = latestVersion;
        this.allMigrationClazz = allMigrationClazz;
    }

    public Optional<SchemaVersion> getCurrentVersion() {
        return schemaVersionDAO.getCurrentSchemaVersion().join();
    }

    public Optional<SchemaVersion> getLatestVersion() {
        return Optional.of(latestVersion);
    }

    public Migration upgradeToVersion(SchemaVersion newVersion) {
        SchemaVersion currentVersion = getCurrentVersion().orElse(DEFAULT_VERSION);

        Migration migrationCombination = IntStream.range(currentVersion.getValue(), newVersion.getValue())
            .boxed()
            .map(SchemaVersion::new)
            .map(this::validateVersionNumber)
            .map(this::toMigration)
            .reduce(Migration.IDENTITY, Migration::combine);
        return new MigrationTask(migrationCombination, newVersion);
    }

    private SchemaVersion validateVersionNumber(SchemaVersion versionNumber) {
        if (!allMigrationClazz.containsKey(versionNumber)) {
            String message = String.format("Can not migrate to %d. No migration class registered.", versionNumber.getValue());
            LOG.error(message);
            throw new NotImplementedException(message);
        }
        return versionNumber;
    }

    public Migration upgradeToLastVersion() {
        return upgradeToVersion(latestVersion);
    }

    private Migration toMigration(SchemaVersion version) {
        return () -> {
            SchemaVersion newVersion = version.next();
            SchemaVersion currentVersion = getCurrentVersion().orElse(DEFAULT_VERSION);
            if (currentVersion.isAfterOrEquals(newVersion)) {
                return Migration.Result.COMPLETED;
            }

            LOG.info("Migrating to version {} ", newVersion);
            return allMigrationClazz.get(version).run()
                .onComplete(() -> schemaVersionDAO.updateVersion(newVersion),
                    () -> LOG.info("Migrating to version {} done", newVersion))
                .onFailure(() -> LOG.warn(failureMessage(newVersion)),
                    () -> throwMigrationException(newVersion));
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
