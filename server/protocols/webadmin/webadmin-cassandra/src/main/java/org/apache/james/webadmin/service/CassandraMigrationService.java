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

package org.apache.james.webadmin.service;

import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.mailbox.cassandra.mail.migration.Migration;
import org.apache.james.webadmin.dto.CassandraVersionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public class CassandraMigrationService {
    public static final String LATEST_VERSION = "latestVersion";
    private final CassandraSchemaVersionDAO schemaVersionDAO;
    private final int latestVersion;
    private final Map<Integer, Migration> allMigrationClazz;
    private final Logger LOG = LoggerFactory.getLogger(CassandraMigrationService.class);

    @Inject
    public CassandraMigrationService(CassandraSchemaVersionDAO schemaVersionDAO, Map<Integer, Migration> allMigrationClazz, @Named(LATEST_VERSION) int latestVersion) {
        Preconditions.checkArgument(latestVersion >= 0, "The latest version must be positive");
        this.schemaVersionDAO = schemaVersionDAO;
        this.latestVersion = latestVersion;
        this.allMigrationClazz = allMigrationClazz;
    }

    public CassandraVersionResponse getCurrentVersion() {
        return new CassandraVersionResponse(schemaVersionDAO.getCurrentSchemaVersion().join());
    }

    public CassandraVersionResponse getLatestVersion() {
        return new CassandraVersionResponse(Optional.of(latestVersion));
    }

    public synchronized void upgradeToVersion(int newVersion) {
        int currentVersion = schemaVersionDAO.getCurrentSchemaVersion().join().orElse(CassandraSchemaVersionManager.DEFAULT_VERSION);
        if (currentVersion >= newVersion) {
            throw new IllegalStateException("Current version is already up to date");
        }

        IntStream.range(currentVersion, newVersion)
            .boxed()
            .forEach(this::doMigration);
    }

    public void upgradeToLastVersion() {
        upgradeToVersion(latestVersion);
    }

    private void doMigration(Integer version) {
        if (allMigrationClazz.containsKey(version)) {
            LOG.info("Migrating to version {} ", version + 1);
            Migration.MigrationResult migrationResult = allMigrationClazz.get(version).run();
            if (migrationResult == Migration.MigrationResult.COMPLETED) {
                schemaVersionDAO.updateVersion(version + 1);
                LOG.info("Migrating to version {} done", version + 1);
            } else {
                String message = String.format("Migrating to version %d partially done. " +
                    "Please check logs for cause of failure and re-run this migration.",
                    version + 1);
                LOG.warn(message);
                throw new MigrationException(message);
            }
        } else {
            String message = String.format("Can not migrate to %d. No migration class registered.", version + 1);
            LOG.error(message);
            throw new NotImplementedException(message);
        }
    }

}
