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

package org.apache.james.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.james.core.Username;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

public abstract class OidcTokenCacheContract {
    protected static final String EMAIL = "user@example.com";
    protected static final String SID_STRING = "sid-1";
    protected static final Sid SID = new Sid(SID_STRING);
    protected static final Optional<List<Aud>> AUD = Optional.of(ImmutableList.of(new Aud("james")));
    protected static final Instant EXPIRES_AT = Instant.now().plus(Duration.ofMinutes(1)).truncatedTo(ChronoUnit.SECONDS);
    protected static final TokenInfo TOKEN_INFO = new TokenInfo(EMAIL, Optional.of(SID), EXPIRES_AT, AUD);

    protected static final String EMAIL_2 = "user2@example.com";
    protected static final String SID_STRING_2 = "sid-2";
    protected static final Sid SID_2 = new Sid(SID_STRING_2);
    protected static final TokenInfo TOKEN_INFO_2 = new TokenInfo(EMAIL_2, Optional.of(SID_2), EXPIRES_AT, AUD);

    protected TokenInfoResolver tokenInfoResolver = mock(TokenInfoResolver.class);

    protected Token token;
    protected Token token2;

    public abstract OidcTokenCache testee();

    public abstract Optional<Username> getUsernameFromCache(Token token);

    @BeforeEach
    void beforeEach() {
        token = newToken();
        token2 = newToken();
        mockTokenInfoResolverSuccess(token, TOKEN_INFO);
    }

    @AfterEach
    void afterEach() {
        reset(tokenInfoResolver);
    }

    protected Token newToken() {
        return new Token("token-" + UUID.randomUUID());
    }

    @Test
    void invalidateShouldRemoveSidFromCache() {
        testee().associatedInformation(token).block();

        assertThat(getUsernameFromCache(token)).contains(Username.of(EMAIL));

        testee().invalidate(SID).block();
        assertThat(getUsernameFromCache(token)).isEmpty();
    }

    @Test
    void invalidateShouldRemoveTokenFromCache() {
        testee().associatedInformation(token).block();
        verify(tokenInfoResolver, times(1)).apply(token);
        testee().invalidate(SID).block();

        testee().associatedInformation(token).block();
        verify(tokenInfoResolver, times(2)).apply(token);
    }

    @Test
    void invalidateShouldRemoveAllTokensForSid() {
        TokenInfo tokenInfo1 = new TokenInfo(EMAIL, Optional.of(SID), EXPIRES_AT, AUD);
        TokenInfo tokenInfo2 = new TokenInfo(EMAIL_2, Optional.of(SID), EXPIRES_AT, AUD);

        mockTokenInfoResolverSuccess(token, tokenInfo1);
        mockTokenInfoResolverSuccess(token2, tokenInfo2);

        testee().associatedInformation(token).block();
        testee().associatedInformation(token2).block();

        testee().invalidate(SID).block();

        assertThat(getUsernameFromCache(token)).isEmpty();
        assertThat(getUsernameFromCache(token2)).isEmpty();
    }

    @Test
    void invalidateShouldNotThrowWhenSidNotCached() {
        assertThatCode(() -> testee().invalidate(new Sid(UUID.randomUUID().toString())).block())
            .doesNotThrowAnyException();
    }

    @Test
    void invalidateShouldNotAffectOtherTokens() {
        mockTokenInfoResolverSuccess(token, TOKEN_INFO);
        mockTokenInfoResolverSuccess(token2, TOKEN_INFO_2);
        testee().associatedInformation(token).block();
        testee().associatedInformation(token2).block();

        verify(tokenInfoResolver, times(1)).apply(token2);

        testee().invalidate(SID).block();

        testee().associatedInformation(token2).block();
        verify(tokenInfoResolver, times(1)).apply(token2);
    }

    @Test
    void associatedUsernameShouldReturnUsername() {
        assertThat(testee().associatedInformation(token).block().email())
            .isEqualTo(EMAIL);
    }

    @Test
    void associatedUsernameShouldThrowErrorWhenTokenCouldNotBeResolved() {
        Token token = new Token("token-" + UUID.randomUUID());
        mockTokenInfoResolverFailure(token, new RuntimeException("Token not found"));

        assertThatThrownBy(() -> testee().associatedInformation(token).block())
            .hasMessage("Token not found");
    }

    @Test
    void associatedUsernameShouldPopulateCache() {
        testee().associatedInformation(token).block();
        assertThat(getUsernameFromCache(token))
            .contains(Username.of(EMAIL));
    }

    @Test
    void associatedUsernameShouldNotPopulateCacheWhenCacheHit() {
        for (int i = 0; i < 5; i++) {
            testee().associatedInformation(token).block();
        }
        verify(tokenInfoResolver, times(1)).apply(token);
    }

    @Test
    void associatedUsernameShouldThrowWhenInvalidateRelatedSid() {
        Token token = new Token("token-" + UUID.randomUUID());
        mockTokenInfoResolverSuccess(token, TOKEN_INFO);
        testee().associatedInformation(token).block();
        testee().invalidate(SID).block();

        mockTokenInfoResolverFailure(token, new RuntimeException("Token expired"));

        assertThatThrownBy(() -> testee().associatedInformation(token).block())
            .hasMessage("Token expired");
    }

    @Test
    void associatedUsernameShouldCachedWhenAbsentSidInTokenInfo() {
        Token token = new Token("token-" + UUID.randomUUID());
        mockTokenInfoResolverSuccess(token, new TokenInfo(EMAIL, Optional.empty(), EXPIRES_AT, AUD));

        for (int i = 0; i < 5; i++) {
            testee().associatedInformation(token).block();
        }
        verify(tokenInfoResolver, times(1)).apply(token);
        assertThat(getUsernameFromCache(token)).contains(Username.of(EMAIL));
    }

    @Test
    void associatedUsernameShouldBeCachedWhenAbsentAudInTokenInfo() {
        Token token = new Token("token-" + UUID.randomUUID());
        mockTokenInfoResolverSuccess(token, new TokenInfo(EMAIL, Optional.of(SID), EXPIRES_AT, Optional.empty()));

        for (int i = 0; i < 5; i++) {
            assertThat(testee().associatedInformation(token).block().aud())
                .isEmpty();
        }

        verify(tokenInfoResolver, times(1)).apply(token);
        assertThat(getUsernameFromCache(token)).contains(Username.of(EMAIL));
    }

    public void mockTokenInfoResolverSuccess(Token token, TokenInfo expected) {
        when(tokenInfoResolver.apply(token))
            .thenReturn(Mono.just(expected));
    }

    public void mockTokenInfoResolverFailure(Token token, Exception expected) {
        when(tokenInfoResolver.apply(token))
            .thenReturn(Mono.error(expected));
    }
}
