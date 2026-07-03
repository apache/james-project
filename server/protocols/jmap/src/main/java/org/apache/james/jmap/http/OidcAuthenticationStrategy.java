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

import static org.apache.james.jmap.http.JWTAuthenticationStrategy.AUTHORIZATION_HEADER_PREFIX;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.exceptions.UnauthorizedException;
import org.apache.james.jwt.introspection.TokenIntrospectionException;
import org.apache.james.jwt.userinfo.UserInfoCheckException;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.oidc.Aud;
import org.apache.james.oidc.OidcTokenCache;
import org.apache.james.oidc.Token;
import org.apache.james.oidc.TokenInfo;

import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

public class OidcAuthenticationStrategy implements AuthenticationStrategy {
    public static final String DEFAULT_AUTHENTICATION_CHALLENGE_REALM = "james";

    private final SessionProvider sessionProvider;
    private final OidcTokenCache oidcTokenCache;
    private final Clock clock;
    private final List<Aud> auds;
    private final String authenticationChallengeRealm;

    @Inject
    public OidcAuthenticationStrategy(SessionProvider sessionProvider, OidcTokenCache oidcTokenCache, Clock clock, List<Aud> auds) {
        this(sessionProvider, oidcTokenCache, clock, auds, DEFAULT_AUTHENTICATION_CHALLENGE_REALM);
    }

    public OidcAuthenticationStrategy(SessionProvider sessionProvider, OidcTokenCache oidcTokenCache, Clock clock, List<Aud> auds,
                                      String authenticationChallengeRealm) {
        this.sessionProvider = sessionProvider;
        this.oidcTokenCache = oidcTokenCache;
        this.clock = clock;
        this.auds = auds;
        this.authenticationChallengeRealm = authenticationChallengeRealm;
    }

    @Override
    public Mono<MailboxSession> createMailboxSession(HttpServerRequest httpRequest) {
        return Mono.fromCallable(() -> authHeaders(httpRequest))
            .filter(header -> header.startsWith(AUTHORIZATION_HEADER_PREFIX))
            .map(header -> header.substring(AUTHORIZATION_HEADER_PREFIX.length()))
            .map(Token::new)
            .flatMap(oidcTokenCache::associatedInformation)
            .<TokenInfo>handle((tokenInfo, sink) -> {
                if (!auds.isEmpty() && !isAudienceAccepted(tokenInfo.aud())) {
                    sink.error(new UnauthorizedException("Wrong audience. Expected " + auds + " got " + tokenInfo.aud()));
                    return;
                }
                if (clock.instant().isAfter(tokenInfo.exp())) {
                    sink.error(new UnauthorizedException("Expired token"));
                    return;
                }

                sink.next(tokenInfo);
            })
            .map(tokenInfo -> Username.of(tokenInfo.email()))
            .flatMap(username -> Mono.fromCallable(() -> sessionProvider.authenticate(username)
                .withoutDelegation()))
            .onErrorMap(TokenIntrospectionException.class, e -> new UnauthorizedException("Invalid OIDC token when introspection check", e))
            .onErrorMap(UserInfoCheckException.class, e -> new UnauthorizedException("Invalid OIDC token when user info check", e));
    }

    private boolean isAudienceAccepted(Optional<List<Aud>> maybeTokenAudiences) {
        return maybeTokenAudiences.map(tokenAudiences -> {
                for (Aud aud: auds) {
                    if (tokenAudiences.contains(aud)) {
                        return true;
                    }
                }
                return false;
            })
            .orElse(true); // if no audience is present in the introspection response, we ignore audience validation
    }

    @Override
    public AuthenticationChallenge correspondingChallenge() {
        return AuthenticationChallenge.of(
            AuthenticationScheme.of("Bearer"),
            ImmutableMap.of("realm", authenticationChallengeRealm,
                "error", "invalid_token",
                "scope", "openid profile email"));
    }
}
