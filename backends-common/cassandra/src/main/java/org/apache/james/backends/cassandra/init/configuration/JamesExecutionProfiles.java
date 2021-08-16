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

package org.apache.james.backends.cassandra.init.configuration;

import java.util.Optional;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;

public interface JamesExecutionProfiles {
    /**
     * James' application configuration allows for some operation to choose
     * or not to enable LWTs.
     *
     * When LWTs are enabled underlying DAOs will use LWT execution profiles.
     */
    enum ConsistencyChoice {
        WEAK, // DAO should use default execution profile
        STRONG // DAO should use LWT execution profile
    }

    /**
     * Applied for SERIAL reads.
     *
     * This profile can allow, amongst other, to choose between SERIAL and LOCAL_SERIAL consistency level.
     */
    static DriverExecutionProfile getLWTProfile(CqlSession session) {
        DriverExecutionProfile executionProfile = session.getContext().getConfig().getProfiles().get("LWT");
        return Optional.ofNullable(executionProfile)
            .orElseGet(() -> defaultLWTProfile(session));
    }

    private static DriverExecutionProfile defaultLWTProfile(CqlSession session) {
        return session.getContext().getConfig().getDefaultProfile()
            .withString(DefaultDriverOption.REQUEST_CONSISTENCY, DefaultConsistencyLevel.SERIAL.name())
            .withString(DefaultDriverOption.REQUEST_SERIAL_CONSISTENCY, DefaultConsistencyLevel.SERIAL.name());
    }

    /**
     * Applied for Caching related data.
     *
     * Can be used to set either ONE or LOCAL_ONE consistency level.
     *
     * If missing (inconsistency) the data can safely and automatically be recomputed from the main data source.
     */
    static DriverExecutionProfile getCachingProfile(CqlSession session) {
        DriverExecutionProfile executionProfile = session.getContext().getConfig().getProfiles().get("CACHING");
        return Optional.ofNullable(executionProfile)
            .orElseGet(() -> defaultCachingProfile(session));
    }

    private static DriverExecutionProfile defaultCachingProfile(CqlSession session) {
        return session.getContext().getConfig().getDefaultProfile()
            .withString(DefaultDriverOption.REQUEST_CONSISTENCY, DefaultConsistencyLevel.ONE.name());
    }

    /**
     * For idempotent data (nether updated / deleted while referenced), we can downgrade the consistency level
     * to ONE and implement a fallback to QUORUM on missing data (inconsistency).
     *
     * This optional behaviour is based on the fact that data is generally well replicated.
     *
     * Can be used to set either ONE or LOCAL_ONE consistency level.
     */
    static DriverExecutionProfile getOptimisticConsistencyLevelProfile(CqlSession session) {
        DriverExecutionProfile executionProfile = session.getContext().getConfig().getProfiles().get("OPTIMISTIC_CONSISTENCY_LEVEL");
        return Optional.ofNullable(executionProfile)
            .orElseGet(() -> defaultOptimisticConsistencyLevelProfile(session));
    }

    private static DriverExecutionProfile defaultOptimisticConsistencyLevelProfile(CqlSession session) {
        return session.getContext().getConfig().getDefaultProfile()
            .withString(DefaultDriverOption.REQUEST_CONSISTENCY, DefaultConsistencyLevel.LOCAL_ONE.name());
    }

    /**
     * Applied for large background queries, EG applied for large entity listings upon tasks.
     */
    static DriverExecutionProfile getBatchProfile(CqlSession session) {
        DriverExecutionProfile executionProfile = session.getContext().getConfig().getProfiles().get("BATCH");
        return Optional.ofNullable(executionProfile)
            .orElseGet(() -> defaultBatchProfile(session));
    }

    private static DriverExecutionProfile defaultBatchProfile(CqlSession session) {
        return session.getContext().getConfig().getDefaultProfile()
            .withLong(DefaultDriverOption.REQUEST_TIMEOUT, 3600000);
    }

    /**
     * Table creation
     */
    static DriverExecutionProfile getTableCreationProfile(CqlSession session) {
        DriverExecutionProfile executionProfile = session.getContext().getConfig().getProfiles().get("TABLE_CREATION");
        return Optional.ofNullable(executionProfile)
            .orElseGet(() -> defaultTableCreationProfile(session));
    }

    private static DriverExecutionProfile defaultTableCreationProfile(CqlSession session) {
        return session.getContext().getConfig().getDefaultProfile()
            .withLong(DefaultDriverOption.REQUEST_TIMEOUT, 10000);
    }
}
