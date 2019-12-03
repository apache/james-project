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
package org.apache.james.jmap.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.api.access.exceptions.NotAnAccessTokenException;
import org.apache.james.jmap.draft.crypto.AccessTokenManagerImpl;
import org.apache.james.jmap.draft.exceptions.NoValidAuthHeaderException;
import org.apache.james.jmap.draft.utils.HeadersAuthenticationExtractor;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.junit.Before;
import org.junit.Test;

public class AccessTokenAuthenticationStrategyTest {

    private AccessTokenManagerImpl mockedAccessTokenManager;
    private MailboxManager mockedMailboxManager;
    private AccessTokenAuthenticationStrategy testee;
    private HttpServletRequest request;
    private HeadersAuthenticationExtractor mockAuthenticationExtractor;

    @Before
    public void setup() {
        mockedAccessTokenManager = mock(AccessTokenManagerImpl.class);
        mockedMailboxManager = mock(MailboxManager.class);
        mockAuthenticationExtractor = mock(HeadersAuthenticationExtractor.class);
        request = mock(HttpServletRequest.class);

        testee = new AccessTokenAuthenticationStrategy(mockedAccessTokenManager, mockedMailboxManager, mockAuthenticationExtractor);
    }

    @Test
    public void createMailboxSessionShouldThrowWhenNoAuthProvided() {
        when(mockAuthenticationExtractor.authHeaders(request))
            .thenReturn(Stream.empty());

        assertThatThrownBy(() -> testee.createMailboxSession(request))
            .isExactlyInstanceOf(NoValidAuthHeaderException.class);
    }

    @Test
    public void createMailboxSessionShouldThrowWhenAuthHeaderIsNotAnUUID() {
        when(mockAuthenticationExtractor.authHeaders(request))
            .thenReturn(Stream.of("bad"));

        assertThatThrownBy(() -> testee.createMailboxSession(request))
                .isExactlyInstanceOf(NotAnAccessTokenException.class);
    }

    @Test
    public void createMailboxSessionShouldThrowWhenAuthHeaderIsInvalid() {
        Username username = Username.of("123456789");
        MailboxSession fakeMailboxSession = mock(MailboxSession.class);

        when(mockedMailboxManager.createSystemSession(eq(username)))
                .thenReturn(fakeMailboxSession);

        UUID authHeader = UUID.randomUUID();
        when(mockedAccessTokenManager.getUsernameFromToken(AccessToken.fromString(authHeader.toString())))
                .thenReturn(username);
        when(mockAuthenticationExtractor.authHeaders(request))
            .thenReturn(Stream.of(authHeader.toString()));


        assertThatThrownBy(() -> testee.createMailboxSession(request))
            .isExactlyInstanceOf(NoValidAuthHeaderException.class);
    }

    @Test
    public void createMailboxSessionShouldReturnWhenAuthHeadersAreValid() {
        Username username = Username.of("123456789");
        MailboxSession fakeMailboxSession = mock(MailboxSession.class);

        when(mockedMailboxManager.createSystemSession(eq(username)))
                .thenReturn(fakeMailboxSession);

        UUID authHeader = UUID.randomUUID();
        AccessToken accessToken = AccessToken.fromString(authHeader.toString());
        when(mockedAccessTokenManager.getUsernameFromToken(accessToken))
                .thenReturn(username);
        when(mockAuthenticationExtractor.authHeaders(request))
            .thenReturn(Stream.of(authHeader.toString()));
        when(mockedAccessTokenManager.isValid(accessToken))
            .thenReturn(true);


        MailboxSession result = testee.createMailboxSession(request);
        assertThat(result).isEqualTo(fakeMailboxSession);
    }
}