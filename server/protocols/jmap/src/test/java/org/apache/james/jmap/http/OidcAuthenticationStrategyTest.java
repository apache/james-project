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
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.util.List;

import org.apache.james.jmap.oidc.OidcTokenCache;
import org.apache.james.mailbox.SessionProvider;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

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
}
