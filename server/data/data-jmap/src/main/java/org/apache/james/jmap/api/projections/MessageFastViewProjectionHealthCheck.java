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

package org.apache.james.jmap.api.projections;

import static org.apache.james.jmap.api.projections.MessageFastViewProjection.METRIC_RETRIEVE_HIT_COUNT;
import static org.apache.james.jmap.api.projections.MessageFastViewProjection.METRIC_RETRIEVE_MISS_COUNT;

import javax.inject.Inject;

import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;

import reactor.core.publisher.Mono;

public class MessageFastViewProjectionHealthCheck implements HealthCheck {

    private static final ComponentName COMPONENT_NAME = new ComponentName("MessageFastViewProjection");
    private static final double MAXIMUM_MISS_PERCENTAGE_ACCEPTED = 10;

    private final Metric retrieveHitCountMetric;
    private final Metric retrieveMissCountMetric;

    @Inject
    MessageFastViewProjectionHealthCheck(MetricFactory metricFactory) {
        retrieveHitCountMetric = metricFactory.generate(METRIC_RETRIEVE_HIT_COUNT);
        retrieveMissCountMetric = metricFactory.generate(METRIC_RETRIEVE_MISS_COUNT);
    }

    @Override
    public ComponentName componentName() {
        return COMPONENT_NAME;
    }

    @Override
    public Mono<Result> checkReactive() {
        return Mono.fromCallable(() -> retrieveMissCountMetric.getCount())
            .flatMap(missCount -> {
                if (missCount == 0) {
                    return Mono.just(Result.healthy(COMPONENT_NAME));
                } else {
                    return Mono.fromCallable(() -> retrieveHitCountMetric.getCount())
                        .map(hitCount -> check(hitCount, missCount));
                }
            });
    }

    private Result check(long hitCount, long missCount) {
        long totalCount = hitCount + missCount;
        double missCountPercentage = missCount * 100.0d / totalCount;
        if (missCountPercentage > MAXIMUM_MISS_PERCENTAGE_ACCEPTED) {
            return Result.degraded(COMPONENT_NAME,
                String.format("retrieveMissCount percentage %s%% (%d/%d) is higher than the threshold %s%%",
                    missCountPercentage, missCount, totalCount, MAXIMUM_MISS_PERCENTAGE_ACCEPTED));
        }

        return Result.healthy(COMPONENT_NAME);
    }
}
