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

import org.apache.james.backends.jpa.EntityManagerUtils;
import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;

import jakarta.persistence.EntityManagerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class JPAHealthCheck implements HealthCheck {

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
    public Mono<Result> check() {
        return Mono.usingWhen(Mono.fromCallable(entityManagerFactory::createEntityManager).subscribeOn(Schedulers.boundedElastic()),
            entityManager -> {
                if (entityManager.isOpen()) {
                    return Mono.just(healthy(componentName()));
                } else {
                    return Mono.just(unhealthy(componentName(), "entityManager is not open"));
                }
            },
            entityManager -> Mono.fromRunnable(() -> EntityManagerUtils.safelyClose(entityManager)).subscribeOn(Schedulers.boundedElastic()))
            .onErrorResume(IllegalStateException.class,
                e -> Mono.just(unhealthy(componentName(), "EntityManagerFactory or EntityManager thrown an IllegalStateException, the connection is unhealthy", e)))
            .onErrorResume(e -> Mono.just(unhealthy(componentName(), "Unexpected exception upon checking JPA driver", e)));
    }
}
