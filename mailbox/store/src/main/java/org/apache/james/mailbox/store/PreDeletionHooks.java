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

import java.util.Set;

import javax.inject.Inject;

import org.apache.james.mailbox.extension.PreDeletionHook;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.NoopMetricFactory;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class PreDeletionHooks {
    private static final int CONCURRENCY = 1;
    public static final PreDeletionHooks NO_PRE_DELETION_HOOK = new PreDeletionHooks(ImmutableSet.of(), new NoopMetricFactory());

    static final String PRE_DELETION_HOOK_METRIC_NAME = "preDeletionHook";

    private final Set<PreDeletionHook> hooks;
    private final MetricFactory metricFactory;

    @Inject
    public PreDeletionHooks(Set<PreDeletionHook> hooks, MetricFactory metricFactory) {
        this.hooks = hooks;
        this.metricFactory = metricFactory;
    }

    public Mono<Void> runHooks(PreDeletionHook.DeleteOperation deleteOperation) {
        return Flux.fromIterable(hooks)
            .publishOn(Schedulers.elastic())
            .flatMap(hook -> metricFactory.runPublishingTimerMetric(PRE_DELETION_HOOK_METRIC_NAME,
                Mono.from(hook.notifyDelete(deleteOperation))), CONCURRENCY)
            .then();
    }
}
