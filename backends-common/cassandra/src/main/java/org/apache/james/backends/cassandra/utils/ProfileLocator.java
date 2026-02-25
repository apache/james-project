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

package org.apache.james.backends.cassandra.utils;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverConfig;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;

public enum ProfileLocator {
    READ(profileLocatorFunction("READ")),
    WRITE(profileLocatorFunction("WRITE"));

    private static  BiFunction<CqlSession, String, DriverExecutionProfile> profileLocatorFunction(String baseProfileName) {
        return (session, daoName) -> {
            DriverConfig config = session.getContext().getConfig();
            Map<String, ? extends DriverExecutionProfile> profiles = config.getProfiles();
            String profileName = baseProfileName + "-" + daoName;

            return Optional.ofNullable(profiles.get(profileName))
                .map(DriverExecutionProfile.class::cast)
                .or(() -> Optional.ofNullable(profiles.get(baseProfileName)))
                .orElseGet(config::getDefaultProfile);
        };
    }

    private final BiFunction<CqlSession, String, DriverExecutionProfile> profileLocatorFunction;

    ProfileLocator(BiFunction<CqlSession, String, DriverExecutionProfile> profileLocatorFunction) {
        this.profileLocatorFunction = profileLocatorFunction;
    }

    public DriverExecutionProfile locateProfile(CqlSession session, String daoName) {
        return profileLocatorFunction.apply(session, daoName);
    }
}
