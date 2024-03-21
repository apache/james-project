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
import jakarta.inject.Named;

import org.apache.james.core.Username;
import org.apache.james.jmap.exceptions.UnauthorizedException;
import org.apache.james.jwt.JwtTokenVerifier;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.util.ReactorUtils;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

public class JWTAuthenticationStrategy implements AuthenticationStrategy {

    @VisibleForTesting
    public static final String AUTHORIZATION_HEADER_PREFIX = "Bearer ";
    private final JwtTokenVerifier tokenManager;
    private final MailboxManager mailboxManager;
    private final UsersRepository usersRepository;

    @Inject
    @VisibleForTesting
    public JWTAuthenticationStrategy(@Named("jmap") JwtTokenVerifier tokenManager,
                                     MailboxManager mailboxManager,
                                     UsersRepository usersRepository) {
        this.tokenManager = tokenManager;
        this.mailboxManager = mailboxManager;
        this.usersRepository = usersRepository;
    }

    @Override
    public Mono<MailboxSession> createMailboxSession(HttpServerRequest httpRequest) {
        return Mono.fromCallable(() -> authHeaders(httpRequest))
            .filter(header -> header.startsWith(AUTHORIZATION_HEADER_PREFIX))
            .map(header -> header.substring(AUTHORIZATION_HEADER_PREFIX.length()))
            .flatMap(userJWTToken -> Mono.fromCallable(() -> {
                Username username = tokenManager.verifyAndExtractLogin(userJWTToken)
                    .map(Username::of)
                    .orElseThrow(() -> new UnauthorizedException("Failed Jwt verification"));
                try {
                    usersRepository.assertValid(username);
                } catch (UsersRepositoryException e) {
                    throw new UnauthorizedException("Invalid username", e);
                }

                return username;
            }).subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER))
            .map(Throwing.function(user -> mailboxManager.authenticate(user).withoutDelegation()));
    }

    @Override
    public AuthenticationChallenge correspondingChallenge() {
        return AuthenticationChallenge.of(
            AuthenticationScheme.of("Bearer"),
            ImmutableMap.of("realm", "JWT"));
    }
}
