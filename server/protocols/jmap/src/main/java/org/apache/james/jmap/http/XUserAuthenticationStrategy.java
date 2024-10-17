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

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import jakarta.inject.Inject;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.core.Username;
import org.apache.james.jmap.exceptions.UnauthorizedException;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

public class XUserAuthenticationStrategy implements AuthenticationStrategy {
    private static final String X_USER_HEADER_NAME = "X-User";
    private static final String X_USER_SECRET_HEADER_NAME = "X-User-Secret";
    private static final String AUTHENTICATION_STRATEGY_XUSER_SECRET = "authentication.strategy.rfc8621.xUser.secret";

    private static final Logger LOGGER = LoggerFactory.getLogger(XUserAuthenticationStrategy.class);
    private static final AuthenticationChallenge X_USER_CHALLENGE = AuthenticationChallenge.of(
        AuthenticationScheme.of("XUserHeader"),
        ImmutableMap.of());

    private static Optional<List<String>> extractXUserSecretFromConfig(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            return Optional.ofNullable(propertiesProvider.getConfiguration("jmap"))
                .map(config -> config.getList(String.class, AUTHENTICATION_STRATEGY_XUSER_SECRET, null))
                .map(list -> {
                    Preconditions.checkArgument(!list.isEmpty(), AUTHENTICATION_STRATEGY_XUSER_SECRET + " must not be empty");
                    return list;
                });
        } catch (FileNotFoundException e) {
            return Optional.empty();
        }
    }

    private final UsersRepository usersRepository;
    private final MailboxManager mailboxManager;
    private final Function<HttpServerRequest, Optional<Username>> usernameExtractor;

    @Inject
    public XUserAuthenticationStrategy(UsersRepository usersRepository,
                                       MailboxManager mailboxManager,
                                       PropertiesProvider configuration) throws ConfigurationException {
        this(usersRepository, mailboxManager, extractXUserSecretFromConfig(configuration));
    }

    public XUserAuthenticationStrategy(UsersRepository usersRepository,
                                       MailboxManager mailboxManager,
                                       Optional<List<String>> xUserSecret) {
        this.usersRepository = usersRepository;
        this.mailboxManager = mailboxManager;
        this.usernameExtractor = xUserSecret
            .map(this::createUsernameExtractorWithSecretValidation)
            .orElseGet(() -> {
                LOGGER.warn("No X-User-Secret value found. X-User header will be used without secret validation which can pose a security risk if an attacker gains access to the JMAP endpoint. " +
                    "Secret validation can be set up via the authentication.strategy.rfc8621.xUser.secret jmap configuration property.");
                return createUsernameExtractorWithoutSecretValidation();
            });
    }

    @Override
    public Mono<MailboxSession> createMailboxSession(HttpServerRequest httpRequest) {
        return usernameExtractor.apply(httpRequest)
            .map(this::createMailboxSession)
            .orElse(Mono.empty());
    }

    private Mono<MailboxSession> createMailboxSession(Username username) {
        return usersRepository.assertValidReactive(username)
            .onErrorMap(e -> new UnauthorizedException("Invalid username", e))
            .then(Mono.fromCallable(() -> mailboxManager.authenticate(username).withoutDelegation()));
    }

    @Override
    public AuthenticationChallenge correspondingChallenge() {
        return X_USER_CHALLENGE;
    }

    private Function<HttpServerRequest, Optional<Username>> createUsernameExtractorWithoutSecretValidation() {
        return httpRequest -> Optional.ofNullable(httpRequest.requestHeaders().get(X_USER_HEADER_NAME))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(Username::of);
    }

    private Function<HttpServerRequest, Optional<Username>> createUsernameExtractorWithSecretValidation(List<String> validatedSecretList) {
        return httpRequest -> createUsernameExtractorWithoutSecretValidation().apply(httpRequest)
            .filter(username -> Optional.ofNullable(httpRequest.requestHeaders().get(X_USER_SECRET_HEADER_NAME))
                .map(validatedSecretList::contains)
                .orElse(false));
    }
}
