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
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;

import org.apache.james.jmap.exceptions.MailboxSessionCreationException;
import org.apache.james.jmap.exceptions.NoValidAuthHeaderException;
import org.apache.james.jmap.utils.HeadersAuthenticationExtractor;
import org.apache.james.jwt.JwtTokenVerifier;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.junit.Before;
import org.junit.Test;


public class JWTAuthenticationStrategyTest {

    private JWTAuthenticationStrategy testee;
    private MailboxManager mockedMailboxManager;
    private JwtTokenVerifier stubTokenVerifier;
    private HttpServletRequest request;
    private HeadersAuthenticationExtractor mockAuthenticationExtractor;

    @Before
    public void setup() {
        stubTokenVerifier = mock(JwtTokenVerifier.class);
        mockedMailboxManager = mock(MailboxManager.class);
        mockAuthenticationExtractor = mock(HeadersAuthenticationExtractor.class);
        request = mock(HttpServletRequest.class);

        testee = new JWTAuthenticationStrategy(stubTokenVerifier, mockedMailboxManager, mockAuthenticationExtractor);
    }


    @Test
    public void createMailboxSessionShouldThrowWhenAuthHeaderIsEmpty() throws Exception {
        when(mockAuthenticationExtractor.authHeaders(request))
            .thenReturn(Stream.empty());

        assertThatThrownBy(() -> testee.createMailboxSession(request))
            .isExactlyInstanceOf(NoValidAuthHeaderException.class);
    }

    @Test
    public void createMailboxSessionShouldThrownWhenAuthHeadersIsInvalid() throws Exception {
        String username = "123456789";
        String validAuthHeader = "valid";
        String fakeAuthHeaderWithPrefix = JWTAuthenticationStrategy.AUTHORIZATION_HEADER_PREFIX + validAuthHeader;
        MailboxSession fakeMailboxSession = mock(MailboxSession.class);

        when(stubTokenVerifier.verify(validAuthHeader)).thenReturn(false);
        when(stubTokenVerifier.extractLogin(validAuthHeader)).thenReturn(username);
        when(mockedMailboxManager.createSystemSession(eq(username)))
                .thenReturn(fakeMailboxSession);
        when(mockAuthenticationExtractor.authHeaders(request))
            .thenReturn(Stream.of(fakeAuthHeaderWithPrefix));


        assertThatThrownBy(() -> testee.createMailboxSession(request))
            .isExactlyInstanceOf(NoValidAuthHeaderException.class);
    }

    @Test
    public void createMailboxSessionShouldReturnEmptyWhenAuthHeaderIsInvalid() throws Exception {
        when(mockAuthenticationExtractor.authHeaders(request))
            .thenReturn(Stream.of("bad"));

        assertThatThrownBy(() -> testee.createMailboxSession(request))
            .isExactlyInstanceOf(NoValidAuthHeaderException.class);
    }

    @Test
    public void createMailboxSessionShouldThrowWhenMailboxExceptionHasOccurred() throws Exception {
        String username = "username";
        String validAuthHeader = "valid";
        String fakeAuthHeaderWithPrefix = JWTAuthenticationStrategy.AUTHORIZATION_HEADER_PREFIX + validAuthHeader;

        when(stubTokenVerifier.verify(validAuthHeader)).thenReturn(true);
        when(stubTokenVerifier.extractLogin(validAuthHeader)).thenReturn(username);
        when(mockedMailboxManager.createSystemSession(eq(username)))
                .thenThrow(new MailboxException());
        when(mockAuthenticationExtractor.authHeaders(request))
            .thenReturn(Stream.of(fakeAuthHeaderWithPrefix));

        assertThatThrownBy(() -> testee.createMailboxSession(request))
                .isExactlyInstanceOf(MailboxSessionCreationException.class);
    }

    @Test
    public void createMailboxSessionShouldReturnWhenAuthHeadersAreValid() throws Exception {
        String username = "123456789";
        String validAuthHeader = "valid";
        String fakeAuthHeaderWithPrefix = JWTAuthenticationStrategy.AUTHORIZATION_HEADER_PREFIX + validAuthHeader;
        MailboxSession fakeMailboxSession = mock(MailboxSession.class);

        when(stubTokenVerifier.verify(validAuthHeader)).thenReturn(true);
        when(stubTokenVerifier.extractLogin(validAuthHeader)).thenReturn(username);
        when(mockedMailboxManager.createSystemSession(eq(username)))
                .thenReturn(fakeMailboxSession);
        when(mockAuthenticationExtractor.authHeaders(request))
            .thenReturn(Stream.of(fakeAuthHeaderWithPrefix));


        MailboxSession result = testee.createMailboxSession(request);
        assertThat(result).isEqualTo(fakeMailboxSession);
    }
}