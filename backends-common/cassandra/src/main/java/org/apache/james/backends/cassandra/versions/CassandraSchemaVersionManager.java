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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

public class CassandraSchemaVersionManager {
    public static final int MIN_VERSION = 1;
    public static final int MAX_VERSION = 1;
    public static final int DEFAULT_VERSION = 1;

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraSchemaVersionManager.class);

    private final int minVersion;
    private final int maxVersion;
    private final int version;

    @Inject
    public CassandraSchemaVersionManager(CassandraSchemaVersionDAO schemaVersionDAO) {
        this(schemaVersionDAO, MIN_VERSION, MAX_VERSION);
    }

    @VisibleForTesting
    public CassandraSchemaVersionManager(CassandraSchemaVersionDAO schemaVersionDAO, int minVersion, int maxVersion) {
        Preconditions.checkArgument(minVersion > 0, "minVersion needs to be strictly positive");
        Preconditions.checkArgument(maxVersion > 0, "maxVersion needs to be strictly positive");
        Preconditions.checkArgument(maxVersion >= minVersion,
            "maxVersion should not be inferior to minVersion");

        this.minVersion = minVersion;
        this.maxVersion = maxVersion;
        this.version = schemaVersionDAO
            .getCurrentSchemaVersion()
            .join()
            .orElseGet(() -> {
                LOGGER.warn("No schema version information found on Cassandra, we assume schema is at version {}",
                    CassandraSchemaVersionManager.DEFAULT_VERSION);
                return DEFAULT_VERSION;
            });
    }

    public int getVersion() {
        return version;
    }

    public int getMinimumSupportedVersion() {
        return minVersion;
    }

    public int getMaximumSupportedVersion() {
        return maxVersion;
    }

    public void ensureSchemaIsSupported() {
        if (version < minVersion) {

            throw new IllegalStateException(
                String.format("Current schema version is %d whereas minimum required version is %d. " +
                    "Recommended version is %d", version, minVersion, maxVersion));
        } else if (version < maxVersion) {

            LOGGER.warn("Current schema version is %d. Recommended version is %d", version, maxVersion);
        } else if (version == maxVersion){

            LOGGER.info("Schema version is up-to-date");
        } else {

            throw new IllegalStateException(
                String.format("Current schema version is %d whereas the minimum supported version is %d. " +
                    "Recommended version is %d.", version, minVersion, maxVersion));
        }
    }

}
