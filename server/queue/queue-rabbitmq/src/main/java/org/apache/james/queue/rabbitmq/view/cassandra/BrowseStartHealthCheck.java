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

package org.apache.james.queue.rabbitmq.view.cassandra;

import java.time.Clock;
import java.time.Duration;

import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

public class BrowseStartHealthCheck implements HealthCheck {
    private static final ComponentName COMPONENT_NAME = new ComponentName("RabbitMQMailQueue BrowseStart");
    private static final Duration GRACE_PERIOD = Duration.ofDays(7);

    private final BrowseStartDAO browseStartDAO;
    private final Clock clock;

    @Inject
    public BrowseStartHealthCheck(BrowseStartDAO browseStartDAO, Clock clock) {
        this.browseStartDAO = browseStartDAO;
        this.clock = clock;
    }

    @Override
    public ComponentName componentName() {
        return COMPONENT_NAME;
    }

    @Override
    public Publisher<Result> check() {
        return browseStartDAO.listAll()
            .filter(pair -> pair.getValue().isBefore(clock.instant().minus(GRACE_PERIOD)))
            .map(Pair::getKey)
            .collect(ImmutableList.toImmutableList())
            .filter(list -> !list.isEmpty())
            .map(tooOldBrowseStart -> Result.degraded(COMPONENT_NAME, String.format("The following mail queues %s have out of date browse starts (older than 7 days)" +
                " which can cause performance issues. We recommend auditing the mail queue content, and resuming the delivery of oldest items, which would " +
                "allow browse start updates to take place correctly again.", tooOldBrowseStart.toString())))
            .switchIfEmpty(Mono.just(Result.healthy(COMPONENT_NAME)))
            .onErrorResume(e -> Mono.just(
                Result.unhealthy(COMPONENT_NAME, "Error while checking browse start", e)));
    }
}
