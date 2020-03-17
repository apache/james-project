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

package org.apache.james;

import javax.inject.Inject;

import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GuiceLifecycleHealthCheck implements HealthCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuiceLifecycleHealthCheck.class);
    private final IsStartedProbe probe;

    @Inject
    public GuiceLifecycleHealthCheck(IsStartedProbe probe) {
        this.probe = probe;
    }

    @Override
    public ComponentName componentName() {
        return new ComponentName("Guice application lifecycle");
    }

    @Override
    public Result check() {
        if (probe.isStarted()) {
            return Result.healthy(componentName());
        } else {
            LOGGER.error("James server is not started");
            return Result.unhealthy(componentName(), "James server is not started.");
        }
    }
}
