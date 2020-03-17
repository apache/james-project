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
package org.apache.james.jpa.healthcheck;

import static org.apache.james.core.healthcheck.Result.healthy;
import static org.apache.james.core.healthcheck.Result.unhealthy;

import javax.inject.Inject;
import javax.persistence.EntityManagerFactory;

import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class JPAHealthCheck implements HealthCheck {

    private static final Logger LOGGER = LoggerFactory.getLogger(JPAHealthCheck.class);
    private final EntityManagerFactory entityManagerFactory;

    @Inject
    public JPAHealthCheck(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    @Override
    public ComponentName componentName() {
        return new ComponentName("JPA Backend");
    }

    @Override
    public Result check() {
        LOGGER.debug("Checking if EntityManager is created successfully");
        try {
            if (entityManagerFactory.createEntityManager().isOpen()) {
                LOGGER.debug("EntityManager can execute queries, the connection is healthy");
                return healthy(componentName());
            }
        } catch (IllegalStateException stateException) {
            LOGGER.debug("EntityManagerFactory or EntityManager threw an IllegalStateException, the connection is unhealthy");
            return unhealthy(componentName(), stateException.getMessage());
        }

        LOGGER.error("EntityManager is not open, the connection is unhealthy");
        return unhealthy(componentName(), "entityManager is not open");
    }
}
