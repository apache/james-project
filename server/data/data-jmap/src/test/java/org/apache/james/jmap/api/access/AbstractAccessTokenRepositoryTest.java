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

package org.apache.james.jmap.api.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CompletionException;

import org.apache.james.jmap.api.access.exceptions.InvalidAccessToken;
import org.junit.Before;
import org.junit.Test;

public abstract class AbstractAccessTokenRepositoryTest {

    private static final AccessToken TOKEN = AccessToken.generate();
    private static final String USERNAME = "username";
    protected static final long TTL_IN_MS = 1000;

    private AccessTokenRepository accessTokenRepository;

    @Before
    public void setUp() {
        accessTokenRepository = createAccessTokenRepository();
    }

    protected abstract AccessTokenRepository createAccessTokenRepository();

    @Test
    public void validTokenMustBeRetrieved() throws Throwable {
        accessTokenRepository.addToken(USERNAME, TOKEN).join();
        assertThat(accessTokenRepository.getUsernameFromToken(TOKEN).join()).isEqualTo(USERNAME);
    }

    @Test
    public void absentTokensMustBeInvalid() throws Exception {
        assertThatThrownBy(() -> accessTokenRepository.getUsernameFromToken(TOKEN).join()).isInstanceOf(CompletionException.class);
        assertThatThrownBy(() -> accessTokenRepository.getUsernameFromToken(TOKEN).join()).hasCauseInstanceOf(InvalidAccessToken.class);
    }

    @Test
    public void removedTokensMustBeInvalid() throws Exception {
        accessTokenRepository.addToken(USERNAME, TOKEN).join();
        accessTokenRepository.removeToken(TOKEN).join();
        assertThatThrownBy(() -> accessTokenRepository.getUsernameFromToken(TOKEN).join()).isInstanceOf(CompletionException.class);
        assertThatThrownBy(() -> accessTokenRepository.getUsernameFromToken(TOKEN).join()).hasCauseInstanceOf(InvalidAccessToken.class);
    }

    @Test
    public void outDatedTokenMustBeInvalid() throws Exception {
        accessTokenRepository.addToken(USERNAME, TOKEN).join();
        Thread.sleep(2 * TTL_IN_MS);
        assertThatThrownBy(() -> accessTokenRepository.getUsernameFromToken(TOKEN).join()).isInstanceOf(CompletionException.class);
        assertThatThrownBy(() -> accessTokenRepository.getUsernameFromToken(TOKEN).join()).hasCauseInstanceOf(InvalidAccessToken.class);
    }

    @Test(expected = NullPointerException.class)
    public void addTokenMustThrowWhenUsernameIsNull() throws Exception {
        accessTokenRepository.addToken(null, TOKEN);
    }

    @Test(expected = IllegalArgumentException.class)
    public void addTokenMustThrowWhenUsernameIsEmpty() throws Exception {
        accessTokenRepository.addToken("", TOKEN);
    }

    @Test(expected = NullPointerException.class)
    public void addTokenMustThrowWhenTokenIsNull() throws Exception {
        accessTokenRepository.addToken(USERNAME, null);
    }

    @Test(expected = NullPointerException.class)
    public void removeTokenTokenMustThrowWhenTokenIsNull() throws Exception {
        accessTokenRepository.removeToken(null);
    }

    @Test(expected = NullPointerException.class)
    public void getUsernameFromTokenMustThrowWhenTokenIsNull() throws Exception {
        accessTokenRepository.getUsernameFromToken(null);
    }

}
