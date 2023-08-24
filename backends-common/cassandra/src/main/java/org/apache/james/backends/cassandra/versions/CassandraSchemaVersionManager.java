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

package org.apache.james.backends.cassandra.versions;

import static org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager.SchemaState.TOO_OLD;
import static org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager.SchemaState.TOO_RECENT;
import static org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager.SchemaState.UPGRADABLE;
import static org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager.SchemaState.UP_TO_DATE;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class CassandraSchemaVersionManager {
    public static final SchemaVersion MIN_VERSION = new SchemaVersion(8);
    public static final SchemaVersion MAX_VERSION = new SchemaVersion(14);
    public static final SchemaVersion DEFAULT_VERSION = MIN_VERSION;

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraSchemaVersionManager.class);

    private final SchemaVersion minVersion;
    private final SchemaVersion maxVersion;
    private final SchemaVersion initialSchemaVersion;
    private final CassandraSchemaVersionDAO schemaVersionDAO;

    public enum SchemaState {
        UP_TO_DATE,
        TOO_RECENT,
        TOO_OLD,
        UPGRADABLE
    }

    @Inject
    public CassandraSchemaVersionManager(CassandraSchemaVersionDAO schemaVersionDAO) {
        this(schemaVersionDAO, MIN_VERSION, MAX_VERSION);
    }

    @VisibleForTesting
    public CassandraSchemaVersionManager(CassandraSchemaVersionDAO schemaVersionDAO, SchemaVersion minVersion, SchemaVersion maxVersion) {
        Preconditions.checkArgument(maxVersion.isAfterOrEquals(minVersion),
            "maxVersion should not be inferior to minVersion");

        this.schemaVersionDAO = schemaVersionDAO;
        this.minVersion = minVersion;
        this.maxVersion = maxVersion;

        this.initialSchemaVersion = computeVersion().block();
    }

    public Mono<Boolean> isBefore(SchemaVersion minimum) {
        if (initialSchemaVersion.isBefore(minimum)) {
            // If we started with a legacy james then maybe schema version had been updated since then
            return computeVersion()
                .map(computedVersion -> computedVersion.isBefore(minimum));
        }
        return Mono.just(false);
    }

    public Mono<SchemaVersion> computeVersion() {
        return schemaVersionDAO
            .getCurrentSchemaVersion()
            .map(maybeVersion -> maybeVersion.orElseGet(() -> {
                LOGGER.warn("No schema version information found on Cassandra, we assume schema is at version {}",
                    CassandraSchemaVersionManager.DEFAULT_VERSION);
                return DEFAULT_VERSION;
            }));
    }

    public SchemaVersion getMinimumSupportedVersion() {
        return minVersion;
    }

    public SchemaVersion getMaximumSupportedVersion() {
        return maxVersion;
    }

    public SchemaState computeSchemaState() {
        SchemaVersion version = computeVersion().block();
        if (version.isBefore(minVersion)) {
            return TOO_OLD;
        } else if (version.isBefore(maxVersion)) {
            return UPGRADABLE;
        } else if (version.equals(maxVersion)) {
            return UP_TO_DATE;
        } else {
            return TOO_RECENT;
        }
    }
}
