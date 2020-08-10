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
package org.apache.james.jmap.draft.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.api.access.AccessTokenRepository;
import org.apache.james.jmap.api.access.exceptions.InvalidAccessToken;
import org.apache.james.jmap.draft.api.AccessTokenManager;
import org.apache.james.jmap.memory.access.MemoryAccessTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class AccessTokenManagerImplTest {
    private static final Username USERNAME = Username.of("username");

    private AccessTokenManager accessTokenManager;
    private AccessTokenRepository accessTokenRepository;
    
    @BeforeEach
    void setUp() {
        accessTokenRepository = new MemoryAccessTokenRepository(500);
        accessTokenManager = new AccessTokenManagerImpl(accessTokenRepository);
    }

    @Test
    void grantShouldThrowOnNullUsername() {
        assertThatNullPointerException()
            .isThrownBy(() -> accessTokenManager.grantAccessToken(null));
    }
    
    @Test
    void grantShouldGenerateATokenOnUsername() {
        assertThat(accessTokenManager.grantAccessToken(USERNAME)).isNotNull();
    }

    @Test
    void grantShouldStoreATokenOnUsername() {
        AccessToken token = Mono.from(accessTokenManager.grantAccessToken(USERNAME)).block();
        assertThat(accessTokenRepository.getUsernameFromToken(token).block()).isEqualTo(USERNAME);
    }
    
    @Test
    void getUsernameShouldThrowWhenNullToken() {
        assertThatNullPointerException()
            .isThrownBy(() -> Mono.from(accessTokenManager.getUsernameFromToken(null)).block());
    }

    @Test
    void getUsernameShouldThrowWhenUnknownToken() {
        assertThatThrownBy(() -> Mono.from(accessTokenManager.getUsernameFromToken(AccessToken.generate())).block())
            .isExactlyInstanceOf(InvalidAccessToken.class);
    }

    @Test
    void getUsernameShouldThrowWhenOtherToken() {
        accessTokenManager.grantAccessToken(USERNAME);
        assertThatThrownBy(() -> Mono.from(accessTokenManager.getUsernameFromToken(AccessToken.generate())).block())
            .isExactlyInstanceOf(InvalidAccessToken.class);
    }

    @Test
    void getUsernameShouldReturnUsernameWhenExistingUsername() {
        AccessToken token = Mono.from(accessTokenManager.grantAccessToken(USERNAME)).block();
        assertThat(Mono.from(accessTokenManager.getUsernameFromToken(token)).block()).isEqualTo(USERNAME);
    }
    
    @Test
    void isValidShouldThrowOnNullToken() {
        assertThatNullPointerException()
            .isThrownBy(() -> accessTokenManager.isValid(null));
    }
    
    @Test
    void isValidShouldReturnFalseOnUnknownToken() {
        assertThat(Mono.from(accessTokenManager.isValid(AccessToken.generate())).block()).isFalse();
    }
    
    @Test
    void isValidShouldReturnFalseWhenOtherToken() {
        accessTokenManager.grantAccessToken(USERNAME);
        assertThat(Mono.from(accessTokenManager.isValid(AccessToken.generate())).block()).isFalse();
    }
    
    @Test
    void isValidShouldReturnTrueWhenValidToken() {
        AccessToken accessToken = Mono.from(accessTokenManager.grantAccessToken(USERNAME)).block();
        assertThat(Mono.from(accessTokenManager.isValid(accessToken)).block()).isTrue();
    }
    
    @Test
    void revokeShouldThrowWhenNullToken() {
        assertThatNullPointerException()
            .isThrownBy(() -> Mono.from(accessTokenManager.revoke(null)).block());
    }
    
    @Test
    void revokeShouldNoopOnUnknownToken() {
        Mono.from(accessTokenManager.revoke(AccessToken.generate())).block();
    }
    
    @Test
    void revokeShouldNoopOnRevokingTwice() {
        AccessToken token = AccessToken.generate();
        Mono.from(accessTokenManager.revoke(token)).block();
        Mono.from(accessTokenManager.revoke(token)).block();
    }
    
    @Test
    void revokeShouldInvalidExistingToken() {
        AccessToken token = Mono.from(accessTokenManager.grantAccessToken(USERNAME)).block();
        Mono.from(accessTokenManager.revoke(token)).block();
        assertThat(Mono.from(accessTokenManager.isValid(token)).block()).isFalse();
    }

    @Test
    void getUsernameShouldThrowWhenRepositoryThrows() {
        accessTokenRepository = mock(AccessTokenRepository.class);
        accessTokenManager = new AccessTokenManagerImpl(accessTokenRepository);

        AccessToken accessToken = AccessToken.generate();
        when(accessTokenRepository.getUsernameFromToken(accessToken)).thenReturn(Mono.error(new InvalidAccessToken(accessToken)));

        assertThatThrownBy(() -> Mono.from(accessTokenManager.getUsernameFromToken(accessToken)).block())
            .isExactlyInstanceOf(InvalidAccessToken.class);
    }
}
