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

import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.SchemaTransition;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CassandraMigrationService {
    public static final String LATEST_VERSION = "latestVersion";
    private final CassandraSchemaVersionDAO schemaVersionDAO;
    private final CassandraSchemaTransitions transitions;
    private final MigrationTask.Factory taskFactory;
    private final SchemaVersion latestVersion;
    private final Logger logger = LoggerFactory.getLogger(CassandraMigrationService.class);

    @Inject
    public CassandraMigrationService(CassandraSchemaVersionDAO schemaVersionDAO, CassandraSchemaTransitions transitions,
                                     MigrationTask.Factory factory, @Named(LATEST_VERSION) SchemaVersion latestVersion) {
        this.schemaVersionDAO = schemaVersionDAO;
        this.transitions = transitions;
        this.taskFactory = factory;
        this.latestVersion = latestVersion;
    }

    public Optional<SchemaVersion> getCurrentVersion() {
        return schemaVersionDAO.getCurrentSchemaVersion().block();
    }

    public Optional<SchemaVersion> getLatestVersion() {
        return Optional.of(latestVersion);
    }

    public Task upgradeToVersion(SchemaVersion target) {
        checkTarget(target);
        return taskFactory.create(target);
    }

    private void checkTarget(SchemaVersion target) {
        getCurrentVersion()
            .orElse(DEFAULT_VERSION)
            .listTransitionsForTarget(target)
            .forEach(this::checkMigration);
    }

    private void checkMigration(SchemaTransition transition) {
        transitions.findMigration(transition).orElseThrow(() -> {
            String message = String.format("Can not migrate from %s to %s. No migration class registered.", transition.fromAsString(), transition.toAsString());
            logger.error(message);
            return new NotImplementedException(message);
        });
    }

    public Task upgradeToLastVersion() {
        return upgradeToVersion(latestVersion);
    }

}
