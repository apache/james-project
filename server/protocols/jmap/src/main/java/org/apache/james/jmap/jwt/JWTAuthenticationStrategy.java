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
package org.apache.james.jmap.jwt;

import static org.apache.james.util.ReactorUtils.publishIfPresent;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.core.Username;
import org.apache.james.jmap.exceptions.UnauthorizedException;
import org.apache.james.jmap.http.AuthenticationStrategy;
import org.apache.james.jmap.http.Authenticator;
import org.apache.james.jwt.JwtTokenVerifier;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;

import com.google.common.annotations.VisibleForTesting;

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
            .handle(publishIfPresent())
            .flatMap(this::createMailboxSession);
    }

    @Override
    public Mono<MailboxSession> createMailboxSession(Authenticator.Authorization authorization) {
        if (authorization.asString().startsWith(AUTHORIZATION_HEADER_PREFIX)) {
            return Mono.fromCallable(() -> {
                String userJWTToken = authorization.asString().substring(AUTHORIZATION_HEADER_PREFIX.length());
                if (!tokenManager.verify(userJWTToken)) {
                    throw new UnauthorizedException("Failed Jwt verification");
                }

                Username username = Username.of(tokenManager.extractLogin(userJWTToken));
                try {
                    usersRepository.assertValid(username);
                } catch (UsersRepositoryException e) {
                    throw new UnauthorizedException("Invalid username", e);
                }

                return mailboxManager.createSystemSession(username);
            });
        }
        return Mono.empty();
    }
}
