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

import org.apache.james.jmap.exceptions.UnauthorizedException;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.metrics.api.MetricFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

public class Authenticator {
    public static class Authorization {
        public static Authorization of(String value) {
            return new Authorization(value);
        }

        private final String value;

        public Authorization(String value) {
            this.value = value;
        }

        public String asString() {
            return value;
        }
    }

    public static Authenticator of(MetricFactory metricFactory, AuthenticationStrategy... authenticationStrategies) {
        return new Authenticator(ImmutableList.copyOf(authenticationStrategies), metricFactory);
    }

    private final List<AuthenticationStrategy> authMethods;
    private final MetricFactory metricFactory;

    @VisibleForTesting
    Authenticator(List<AuthenticationStrategy> authMethods, MetricFactory metricFactory) {
        this.authMethods = authMethods;
        this.metricFactory = metricFactory;
    }

    public Mono<MailboxSession> authenticate(HttpServerRequest request) {
        return authenticateIfPossible(request)
            .switchIfEmpty(Mono.error(new UnauthorizedException("No valid authentication methods provided")));
    }

    public Mono<MailboxSession> authenticate(Authorization authorization) {
        return Mono.from(metricFactory.decoratePublisherWithTimerMetric("JMAP-authentication-filter",
            Flux.fromIterable(authMethods)
                .concatMap(auth -> auth.createMailboxSession(authorization))
                .next()
                .switchIfEmpty(Mono.error(new UnauthorizedException("No valid authentication methods provided")))));
    }

    public Mono<MailboxSession> authenticateIfPossible(HttpServerRequest request) {
        return Mono.from(metricFactory.decoratePublisherWithTimerMetric("JMAP-authentication-filter",
            Flux.fromIterable(authMethods)
                .concatMap(auth -> auth.createMailboxSession(request))
                .next()));
    }
}
