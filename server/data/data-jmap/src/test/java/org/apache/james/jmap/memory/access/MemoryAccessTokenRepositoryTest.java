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

package org.apache.james.jmap.memory.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.api.access.exceptions.AccessTokenAlreadyStored;
import org.junit.Before;
import org.junit.Test;

public class MemoryAccessTokenRepositoryTest {

    private static final AccessToken TOKEN = AccessToken.fromString("TOKEN");
    private static final int TTL_IN_MS = 100;

    private MemoryAccessTokenRepository accessTokenRepository;

    @Before
    public void setUp() {
        accessTokenRepository = new MemoryAccessTokenRepository(TTL_IN_MS);
    }

    @Test
    public void validTokenMustWork() throws Exception {
        accessTokenRepository.addToken(TOKEN);
        assertThat(accessTokenRepository.verifyToken(TOKEN)).isTrue();
    }

    @Test
    public void nonStoredTokensMustBeInvalid() throws Exception {
        assertThat(accessTokenRepository.verifyToken(TOKEN)).isFalse();
    }

    @Test
    public void removedTokensMustBeInvalid() throws Exception {
        accessTokenRepository.addToken(TOKEN);
        accessTokenRepository.removeToken(TOKEN);
        assertThat(accessTokenRepository.verifyToken(TOKEN)).isFalse();
    }

    @Test(expected = AccessTokenAlreadyStored.class)
    public void addTokenMustThrowWhenTokenIsAlreadyStored() throws Exception {
        try {
            accessTokenRepository.addToken(TOKEN);
        } catch(Exception e) {
            fail("Exception caught", e);
        }
        accessTokenRepository.addToken(TOKEN);
    }

    @Test
    public void outDatedTokenMustBeInvalid() throws Exception {
        accessTokenRepository.addToken(TOKEN);
        Thread.sleep(200);
        assertThat(accessTokenRepository.verifyToken(TOKEN)).isFalse();
    }

    @Test(expected = NullPointerException.class)
    public void addTokenMustThrowWhenTokenIsNull() throws Exception {
        accessTokenRepository.addToken(null);
    }

    @Test(expected = NullPointerException.class)
    public void removeTokenTokenMustThrowWhenTokenIsNull() throws Exception {
        accessTokenRepository.removeToken(null);
    }

    @Test(expected = NullPointerException.class)
    public void verifyTokenTokenMustThrowWhenTokenIsNull() throws Exception {
        accessTokenRepository.verifyToken(null);
    }

}
