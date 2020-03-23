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
import org.apache.james.lifecycle.api.Startable;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class PeriodicalHealthChecks implements Startable {

    private final Flux<HealthCheck> healthChecks;
    private final long initialDelay;
    private final long period;
    private Disposable disposable;

    @Inject
    PeriodicalHealthChecks(Set<HealthCheck> healthChecks, PeriodicalHealthChecksConfiguration config) {
        this.healthChecks = Flux.fromIterable(healthChecks);
        this.initialDelay = config.getInitialDelay();
        this.period = config.getPeriod();
    }

    public void start() {
        disposable = Flux.interval(Duration.ofSeconds(initialDelay), Duration.ofSeconds(period))
            .flatMap(any ->
                healthChecks.flatMap(healthCheck -> Mono.just(healthCheck.check())))
            .subscribeOn(Schedulers.elastic())
            .subscribe();
    }

    @PreDestroy
    public void stop() {
        disposable.dispose();
    }
}
