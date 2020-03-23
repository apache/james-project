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
package org.apache.james.jmap.http;

import java.util.List;

import javax.inject.Inject;

import org.apache.james.jmap.draft.exceptions.UnauthorizedException;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.metrics.api.MetricFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

public class AuthenticationFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationFilter.class);

    static AuthenticationFilter of(MetricFactory metricFactory, AuthenticationStrategy... authenticationStrategies) {
        return new AuthenticationFilter(ImmutableList.copyOf(authenticationStrategies), metricFactory);
    }

    private final List<AuthenticationStrategy> authMethods;
    private final MetricFactory metricFactory;

    @Inject
    @VisibleForTesting
    AuthenticationFilter(List<AuthenticationStrategy> authMethods, MetricFactory metricFactory) {
        this.authMethods = authMethods;
        this.metricFactory = metricFactory;
    }

    public Mono<MailboxSession> authenticate(HttpServerRequest request) {
        return Mono.from(metricFactory.runPublishingTimerMetric("JMAP-authentication-filter",
            Flux.fromIterable(authMethods)
                .concatMap(auth -> auth.createMailboxSession(request))
                .onErrorContinue((throwable, nothing) -> LOGGER.error("Error while trying to authenticate with JMAP", throwable))
                .next()
                .switchIfEmpty(Mono.error(new UnauthorizedException()))));
    }
}
