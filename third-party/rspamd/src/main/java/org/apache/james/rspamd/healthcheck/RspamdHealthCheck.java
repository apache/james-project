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

package org.apache.james.rspamd.healthcheck;

import java.time.Duration;

import javax.inject.Inject;

import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.apache.james.rspamd.client.RspamdHttpClient;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

public class RspamdHealthCheck implements HealthCheck {
    private static final ComponentName COMPONENT_NAME = new ComponentName("Rspamd");
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(5);

    private final RspamdHttpClient rspamdHttpClient;

    @Inject
    public RspamdHealthCheck(RspamdHttpClient rspamdHttpClient) {
        this.rspamdHttpClient = rspamdHttpClient;
    }

    @Override
    public ComponentName componentName() {
        return COMPONENT_NAME;
    }

    @Override
    public Publisher<Result> check() {
        return rspamdHttpClient.ping()
            .timeout(HEALTH_CHECK_TIMEOUT)
            .thenReturn(Result.healthy(COMPONENT_NAME))
            .onErrorResume(e -> Mono.just(Result.unhealthy(COMPONENT_NAME, "Can not connect to Rspamd.", e)));
    }
}
