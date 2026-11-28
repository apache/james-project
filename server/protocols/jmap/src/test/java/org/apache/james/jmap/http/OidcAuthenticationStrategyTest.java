/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License. You may obtain a copy of the License at    *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied. See the License for the     *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;

import org.apache.james.jmap.exceptions.UnauthorizedException;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.oidc.LoggedOutSessionException;
import org.apache.james.oidc.OidcTokenCache;
import org.apache.james.oidc.Sid;
import org.apache.james.oidc.Token;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

class OidcAuthenticationStrategyTest {
    @Test
    void correspondingChallengeShouldUseJamesRealmByDefault() {
        OidcAuthenticationStrategy testee = new OidcAuthenticationStrategy(
            mock(SessionProvider.class),
            mock(OidcTokenCache.class),
            Clock.systemUTC(),
            List.of());

        assertThat(testee.correspondingChallenge())
            .isEqualTo(AuthenticationChallenge.of(
                AuthenticationScheme.of("Bearer"),
                ImmutableMap.of("realm", "james",
                    "error", "invalid_token",
                    "scope", "openid profile email")));
    }

    @Test
    void correspondingChallengeShouldAllowRealmOverride() {
        OidcAuthenticationStrategy testee = new OidcAuthenticationStrategy(
            mock(SessionProvider.class),
            mock(OidcTokenCache.class),
            Clock.systemUTC(),
            List.of(),
            "twake_mail");

        assertThat(testee.correspondingChallenge())
            .isEqualTo(AuthenticationChallenge.of(
                AuthenticationScheme.of("Bearer"),
                ImmutableMap.of("realm", "twake_mail",
                    "error", "invalid_token",
                    "scope", "openid profile email")));
    }

    @Test
    void createMailboxSessionShouldRejectATokenOfALoggedOutSession() {
        OidcTokenCache oidcTokenCache = mock(OidcTokenCache.class);
        when(oidcTokenCache.associatedInformation(new Token("token")))
            .thenReturn(Mono.error(new LoggedOutSessionException(new Sid("sid"))));
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.requestHeaders()).thenReturn(new DefaultHttpHeaders().add("Authorization", "Bearer token"));
        OidcAuthenticationStrategy testee = new OidcAuthenticationStrategy(
            mock(SessionProvider.class),
            oidcTokenCache,
            Clock.systemUTC(),
            List.of());

        assertThatThrownBy(() -> testee.createMailboxSession(request).block())
            .isInstanceOf(UnauthorizedException.class);
    }
}
