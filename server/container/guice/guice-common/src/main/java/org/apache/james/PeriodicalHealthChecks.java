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

import java.time.Duration;
import java.util.Set;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.apache.james.lifecycle.api.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

public class PeriodicalHealthChecks implements Startable {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeriodicalHealthChecks.class);
    private final Set<HealthCheck> healthChecks;
    private final Scheduler scheduler;
    private final Duration period;
    private Disposable disposable;

    @Inject
    PeriodicalHealthChecks(Set<HealthCheck> healthChecks, Scheduler scheduler, PeriodicalHealthChecksConfiguration config) {
        this.healthChecks = healthChecks;
        this.scheduler = scheduler;
        this.period = config.getPeriod();
    }

    public void start() {
        disposable = Flux.interval(period, scheduler)
            .flatMap(any -> Flux.fromIterable(healthChecks)
                .flatMap(healthCheck ->
                    Mono.fromCallable(healthCheck::check)))
                .flatMap(result ->
                    Mono.fromRunnable(() -> logResult(result)))
            .onErrorContinue(this::logError)
            .subscribeOn(Schedulers.elastic())
            .subscribe();
    }

    private void logResult(Result result) {
        switch (result.getStatus()) {
            case HEALTHY:
                break;
            case DEGRADED:
                LOGGER.warn("DEGRADED: {} : {}", result.getComponentName().getName(), result.getCause());
                break;
            case UNHEALTHY:
                if (result.getError().isPresent()) {
                    LOGGER.error("UNHEALTHY: {} : {} : {}", result.getComponentName().getName(), result.getCause(), result.getError().get());
                    break;
                }

                LOGGER.error("UNHEALTHY: {} : {}", result.getComponentName().getName(), result.getCause());
                break;
        }
    }

    private void logError(Throwable error, Object triggeringValue) {
        if (triggeringValue instanceof Result) {
            Result result = (Result) triggeringValue;
            LOGGER.error("HealthCheck error for: {}, Cause: {}", result.getComponentName(), error);
        }
    }

    @PreDestroy
    public void stop() {
        disposable.dispose();
    }
}