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

public class AccessTokenManagerImplTest {
    private static final Username USERNAME = Username.of("username");

    private AccessTokenManager accessTokenManager;
    private AccessTokenRepository accessTokenRepository;
    
    @BeforeEach
    void setUp() {
        accessTokenRepository = new MemoryAccessTokenRepository(100);
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
        AccessToken token = accessTokenManager.grantAccessToken(USERNAME);
        assertThat(accessTokenRepository.getUsernameFromToken(token).block()).isEqualTo(USERNAME);
    }
    
    @Test
    void getUsernameShouldThrowWhenNullToken() {
        assertThatNullPointerException()
            .isThrownBy(() -> accessTokenManager.getUsernameFromToken(null));
    }

    @Test
    void getUsernameShouldThrowWhenUnknownToken() {
        assertThatThrownBy(() -> accessTokenManager.getUsernameFromToken(AccessToken.generate()))
            .isExactlyInstanceOf(InvalidAccessToken.class);
    }

    @Test
    void getUsernameShouldThrowWhenOtherToken() {
        accessTokenManager.grantAccessToken(USERNAME);
        assertThatThrownBy(() -> accessTokenManager.getUsernameFromToken(AccessToken.generate()))
            .isExactlyInstanceOf(InvalidAccessToken.class);
    }

    @Test
    void getUsernameShouldReturnUsernameWhenExistingUsername() {
        AccessToken token = accessTokenManager.grantAccessToken(USERNAME);
        assertThat(accessTokenManager.getUsernameFromToken(token)).isEqualTo(USERNAME);
    }
    
    @Test
    void isValidShouldThrowOnNullToken() {
        assertThatNullPointerException()
            .isThrownBy(() -> accessTokenManager.isValid(null));
    }
    
    @Test
    void isValidShouldReturnFalseOnUnknownToken() {
        assertThat(accessTokenManager.isValid(AccessToken.generate())).isFalse();
    }
    
    @Test
    void isValidShouldReturnFalseWhenOtherToken() {
        accessTokenManager.grantAccessToken(USERNAME);
        assertThat(accessTokenManager.isValid(AccessToken.generate())).isFalse();
    }
    
    @Test
    void isValidShouldReturnTrueWhenValidToken() {
        AccessToken accessToken = accessTokenManager.grantAccessToken(USERNAME);
        assertThat(accessTokenManager.isValid(accessToken)).isTrue();
    }
    
    @Test
    void revokeShouldThrowWhenNullToken() {
        assertThatNullPointerException()
            .isThrownBy(() -> accessTokenManager.revoke(null));
    }
    
    @Test
    void revokeShouldNoopOnUnknownToken() {
        accessTokenManager.revoke(AccessToken.generate());
    }
    
    @Test
    void revokeShouldNoopOnRevokingTwice() {
        AccessToken token = AccessToken.generate();
        accessTokenManager.revoke(token);
        accessTokenManager.revoke(token);
    }
    
    @Test
    void revokeShouldInvalidExistingToken() {
        AccessToken token = accessTokenManager.grantAccessToken(USERNAME);
        accessTokenManager.revoke(token);
        assertThat(accessTokenManager.isValid(token)).isFalse();
    }

    @Test
    void getUsernameShouldThrowWhenRepositoryThrows() {
        accessTokenRepository = mock(AccessTokenRepository.class);
        accessTokenManager = new AccessTokenManagerImpl(accessTokenRepository);

        AccessToken accessToken = AccessToken.generate();
        when(accessTokenRepository.getUsernameFromToken(accessToken)).thenReturn(Mono.error(new InvalidAccessToken(accessToken)));

        assertThatThrownBy(() -> accessTokenManager.getUsernameFromToken(accessToken))
            .isExactlyInstanceOf(InvalidAccessToken.class);
    }
}
