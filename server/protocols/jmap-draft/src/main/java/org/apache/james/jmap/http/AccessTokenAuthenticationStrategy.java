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

import jakarta.inject.Inject;

import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.api.access.exceptions.InvalidAccessToken;
import org.apache.james.jmap.api.access.exceptions.NotAnAccessTokenException;
import org.apache.james.jmap.draft.api.AccessTokenManager;
import org.apache.james.jmap.exceptions.UnauthorizedException;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

public class AccessTokenAuthenticationStrategy implements AuthenticationStrategy {
    private final AccessTokenManager accessTokenManager;
    private final MailboxManager mailboxManager;

    @Inject
    @VisibleForTesting
    AccessTokenAuthenticationStrategy(AccessTokenManager accessTokenManager, MailboxManager mailboxManager) {
        this.accessTokenManager = accessTokenManager;
        this.mailboxManager = mailboxManager;
    }

    @Override
    public Mono<MailboxSession> createMailboxSession(HttpServerRequest httpRequest) {
        return Mono.fromCallable(() -> authHeaders(httpRequest))
            .filter(tokenString -> !tokenString.startsWith("Bearer"))
            .map(AccessToken::fromString)
            .flatMap(item -> Mono.from(accessTokenManager.getUsernameFromToken(item)))
            .map(Throwing.function(user -> mailboxManager.authenticate(user).withoutDelegation()))
            .onErrorResume(InvalidAccessToken.class, error -> Mono.error(new UnauthorizedException("Invalid access token", error)))
            .onErrorResume(NotAnAccessTokenException.class, error -> Mono.error(new UnauthorizedException("Not an access token", error)));
    }

    @Override
    public AuthenticationChallenge correspondingChallenge() {
        return AuthenticationChallenge.of(
            AuthenticationScheme.of("Bearer"),
            ImmutableMap.of("realm", "JMAP Draft access token"));
    }
}
