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
package org.apache.james.jmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.stream.Stream;

import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.api.access.exceptions.NotAnAccessTokenException;
import org.apache.james.jmap.crypto.AccessTokenManagerImpl;
import org.apache.james.jmap.exceptions.MailboxSessionCreationException;
import org.apache.james.jmap.exceptions.NoValidAuthHeaderException;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

public class AccessTokenAuthenticationStrategyTest {

    private AccessTokenManagerImpl mockedAccessTokenManager;
    private MailboxManager mockedMailboxManager;
    private AccessTokenAuthenticationStrategy testee;

    @Before
    public void setup() {
        mockedAccessTokenManager = mock(AccessTokenManagerImpl.class);
        mockedMailboxManager = mock(MailboxManager.class);

        testee = new AccessTokenAuthenticationStrategy(mockedAccessTokenManager, mockedMailboxManager);
    }

    @Test
    public void createMailboxSessionShouldThrowWhenNoAuthProvided() {
        assertThatThrownBy(() -> testee.createMailboxSession(Stream.empty()))
            .isExactlyInstanceOf(NoValidAuthHeaderException.class);
    }

    @Test
    public void createMailboxSessionShouldThrowWhenAuthHeaderIsNotAnUUID() {
        assertThatThrownBy(() -> testee.createMailboxSession(Stream.of("bad")))
                .isExactlyInstanceOf(NotAnAccessTokenException.class);
    }

    @Test
    public void createMailboxSessionShouldThrowWhenMailboxExceptionHasOccurred() throws Exception {
        String username = "username";
        when(mockedMailboxManager.createSystemSession(eq(username), any(Logger.class)))
                .thenThrow(new MailboxException());

        UUID authHeader = UUID.randomUUID();
        when(mockedAccessTokenManager.getUsernameFromToken(AccessToken.fromString(authHeader.toString())))
                .thenReturn(username);

        assertThatThrownBy(() -> testee.createMailboxSession(Stream.of(authHeader.toString())))
                .isExactlyInstanceOf(MailboxSessionCreationException.class);
    }

    @Test
    public void createMailboxSessionShouldReturnWhenAuthHeadersAreValid() throws Exception {
        String username = "123456789";
        MailboxSession fakeMailboxSession = mock(MailboxSession.class);

        when(mockedMailboxManager.createSystemSession(eq(username), any(Logger.class)))
                .thenReturn(fakeMailboxSession);

        UUID authHeader = UUID.randomUUID();
        when(mockedAccessTokenManager.getUsernameFromToken(AccessToken.fromString(authHeader.toString())))
                .thenReturn(username);

        MailboxSession result = testee.createMailboxSession(Stream.of(authHeader.toString()));
        assertThat(result).isEqualTo(fakeMailboxSession);
    }

    @Test
    public void checkAuthorizationHeaderShouldReturnFalseWhenAuthHeaderIsEmpty() {
        assertThat(testee.checkAuthorizationHeader(Stream.empty())).isFalse();
    }

    @Test
    public void checkAuthorizationHeaderShouldReturnFalseWhenAuthHeaderIsInvalid() {
        assertThat(testee.checkAuthorizationHeader(Stream.of("bad"))).isFalse();
    }

    @Test
    public void checkAuthorizationHeaderShouldReturnFalseWhenAuthHeadersAreInvalid() {
        assertThat(testee.checkAuthorizationHeader(Stream.of("bad", "alsobad"))).isFalse();
    }

    @Test
    public void checkAuthorizationHeaderShouldReturnTrueWhenAuthHeaderIsValid() {

        String validToken = UUID.randomUUID().toString();
        when(mockedAccessTokenManager.isValid(AccessToken.fromString(validToken)))
                .thenReturn(true);

        assertThat(testee.checkAuthorizationHeader(Stream.of(validToken))).isTrue();
    }

    @Test
    public void checkAuthorizationHeaderShouldReturnTrueWhenOneAuthHeaderIsValid() {

        String validToken = UUID.randomUUID().toString();
        when(mockedAccessTokenManager.isValid(AccessToken.fromString(validToken)))
                .thenReturn(true);

        assertThat(testee.checkAuthorizationHeader(Stream.of("bad", validToken))).isTrue();
    }
}