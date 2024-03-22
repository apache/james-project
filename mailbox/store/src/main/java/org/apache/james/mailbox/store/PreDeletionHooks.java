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

package org.apache.james.mailbox.store;

import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.mailbox.extension.PreDeletionHook;
import org.apache.james.metrics.api.MetricFactory;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PreDeletionHooks {
    private static final int CONCURRENCY = 1;
    public static final PreDeletionHooks NO_PRE_DELETION_HOOK = new PreDeletionHooks(ImmutableSet.of(), Optional.empty());

    static final String PRE_DELETION_HOOK_METRIC_NAME = "preDeletionHook";

    private final Set<PreDeletionHook> hooks;
    private final Optional<MetricFactory> metricFactory;

    @Inject
    public PreDeletionHooks(Set<PreDeletionHook> hooks, MetricFactory metricFactory) {
        this(hooks, Optional.of(metricFactory));
    }

    private PreDeletionHooks(Set<PreDeletionHook> hooks, Optional<MetricFactory> metricFactory) {
        this.hooks = hooks;
        this.metricFactory = metricFactory;
    }

    public Mono<Void> runHooks(PreDeletionHook.DeleteOperation deleteOperation) {
        return Flux.fromIterable(hooks)
            .flatMap(hook -> metricFactory.map(factory -> publishMetric(deleteOperation, hook, factory))
                    .orElse(Mono.empty()),
                CONCURRENCY)
            .then();
    }

    private Mono<Void> publishMetric(PreDeletionHook.DeleteOperation deleteOperation, PreDeletionHook hook, MetricFactory factory) {
        return Mono.from(
            factory.decoratePublisherWithTimerMetric(PRE_DELETION_HOOK_METRIC_NAME, hook.notifyDelete(deleteOperation)));
    }
}
