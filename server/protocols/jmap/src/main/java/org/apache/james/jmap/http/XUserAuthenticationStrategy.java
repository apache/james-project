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

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.exceptions.UnauthorizedException;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.util.ReactorUtils;

import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

public class XUserAuthenticationStrategy implements AuthenticationStrategy {
    private static final String X_USER_HEADER_NAME = "X-User";
    private static final AuthenticationChallenge X_USER_CHALLENGE = AuthenticationChallenge.of(
        AuthenticationScheme.of("XUserHeader"),
        ImmutableMap.of());

    private final UsersRepository usersRepository;
    private final MailboxManager mailboxManager;

    @Inject
    public XUserAuthenticationStrategy(UsersRepository usersRepository, MailboxManager mailboxManager) {
        this.usersRepository = usersRepository;
        this.mailboxManager = mailboxManager;
    }

    @Override
    public Mono<MailboxSession> createMailboxSession(HttpServerRequest httpRequest) {
        return Optional.ofNullable(httpRequest.requestHeaders().get(X_USER_HEADER_NAME))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(Username::of)
            .map(this::createMailboxSession)
            .orElse(Mono.empty());
    }

    private Mono<MailboxSession> createMailboxSession(Username username) {
        return Mono.fromCallable(() -> {
            try {
                usersRepository.assertValid(username);
            } catch (UsersRepositoryException e) {
                throw new UnauthorizedException("Invalid username", e);
            }
            return mailboxManager.authenticate(username).withoutDelegation();
        }).subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER);
    }

    @Override
    public AuthenticationChallenge correspondingChallenge() {
        return X_USER_CHALLENGE;
    }
}
