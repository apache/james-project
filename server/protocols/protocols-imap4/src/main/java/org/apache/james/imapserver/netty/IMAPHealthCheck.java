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

package org.apache.james.imapserver.netty;

import jakarta.inject.Inject;

import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;

import reactor.core.publisher.Mono;

public class IMAPHealthCheck implements HealthCheck {
    private static final ComponentName COMPONENT_NAME = new ComponentName("IMAPHealthCheck");

    private final IMAPServerFactory imapServerFactory;

    @Inject
    public IMAPHealthCheck(IMAPServerFactory imapServerFactory) {
        this.imapServerFactory = imapServerFactory;
    }

    @Override
    public ComponentName componentName() {
        return COMPONENT_NAME;
    }

    @Override
    public Mono<Result> check() {
        return Mono.fromCallable(() -> {
            if (isAnyQueueFull()) {
                return Result.degraded(COMPONENT_NAME, "ReactiveThrottler queue is full");
            } else {
                return Result.healthy(COMPONENT_NAME);
            }
        });
    }

    private boolean isAnyQueueFull() {
        return imapServerFactory.getImapServers().stream().anyMatch(IMAPServer::isReactiveThrottlerQueueFull);
    }
}
