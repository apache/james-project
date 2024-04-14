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

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.util.Set;
import java.util.concurrent.TimeoutException;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.apache.james.lifecycle.api.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class PeriodicalHealthChecks implements Startable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PeriodicalHealthChecks.class);

    private final Set<HealthCheck> healthChecks;
    private final PeriodicalHealthChecksConfiguration configuration;
    private Disposable disposable;

    @Inject
    PeriodicalHealthChecks(@Named("resolved-checks") Set<HealthCheck> healthChecks, PeriodicalHealthChecksConfiguration configuration) {
        this.healthChecks = healthChecks;
        this.configuration = configuration;
    }

    public void start() {
        disposable = Flux.interval(configuration.getPeriod(), Schedulers.parallel())
            .flatMapIterable(any -> healthChecks)
            .flatMap(healthCheck -> Mono.from(healthCheck.check())
                .timeout(configuration.getPeriod())
                .onErrorResume(TimeoutException.class, e -> Mono.just(Result.unhealthy(healthCheck.componentName(), e.getMessage()))),
                DEFAULT_CONCURRENCY)
            .doOnNext(this::logResult)
            .onErrorContinue(this::logError)
            .subscribeOn(Schedulers.parallel())
            .subscribe();
    }

    @PreDestroy
    public void stop() {
        disposable.dispose();
    }

    private void logResult(Result result) {
        switch (result.getStatus()) {
            case HEALTHY:
                break;
            case DEGRADED:
                LOGGER.warn("DEGRADED: {} : {}",
                    result.getComponentName().getName(),
                    result.getCause().orElse(""));
                break;
            case UNHEALTHY:
                logUnhealthy(result);
                break;
        }
    }

    private void logUnhealthy(Result result) {
        result.getError()
            .ifPresentOrElse(
                e -> LOGGER.error("UNHEALTHY: {} : {}",
                    result.getComponentName().getName(),
                    result.getCause().orElse(""),
                    e),
                () -> LOGGER.error("UNHEALTHY: {} : {}",
                    result.getComponentName().getName(),
                    result.getCause().orElse("")));
    }

    private void logError(Throwable error, Object triggeringValue) {
        if (triggeringValue instanceof Result) {
            Result result = (Result) triggeringValue;
            LOGGER.error("HealthCheck error for: {}, Cause: {}",
                result.getComponentName(),
                error);
            return;
        }
        LOGGER.error("HealthCheck error. Triggering value: {}, Cause: ",
            triggeringValue,
            error);
    }
}