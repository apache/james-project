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

import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;

import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.jmap.draft.exceptions.MailboxSessionCreationException;
import org.apache.james.jmap.draft.exceptions.NoValidAuthHeaderException;
import org.apache.james.jmap.draft.utils.HeadersAuthenticationExtractor;
import org.apache.james.jwt.JwtTokenVerifier;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.junit.Before;
import org.junit.Test;

public class JWTAuthenticationStrategyTest {
    private static final DomainList NO_DOMAIN_LIST = null;

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
        MemoryUsersRepository usersRepository = MemoryUsersRepository.withoutVirtualHosting(NO_DOMAIN_LIST);

        testee = new JWTAuthenticationStrategy(stubTokenVerifier, mockedMailboxManager, mockAuthenticationExtractor, usersRepository);
    }


    @Test
    public void createMailboxSessionShouldThrowWhenAuthHeaderIsEmpty() {
        when(mockAuthenticationExtractor.authHeaders(request))
            .thenReturn(Stream.empty());

        assertThatThrownBy(() -> testee.createMailboxSession(request))
            .isExactlyInstanceOf(NoValidAuthHeaderException.class);
    }

    @Test
    public void createMailboxSessionShouldThrownWhenAuthHeadersIsInvalid() {
        String username = "123456789";
        String validAuthHeader = "valid";
        String fakeAuthHeaderWithPrefix = JWTAuthenticationStrategy.AUTHORIZATION_HEADER_PREFIX + validAuthHeader;
        MailboxSession fakeMailboxSession = mock(MailboxSession.class);

        when(stubTokenVerifier.verify(validAuthHeader)).thenReturn(false);
        when(stubTokenVerifier.extractLogin(validAuthHeader)).thenReturn(username);
        when(mockedMailboxManager.createSystemSession(eq(Username.of(username))))
                .thenReturn(fakeMailboxSession);
        when(mockAuthenticationExtractor.authHeaders(request))
            .thenReturn(Stream.of(fakeAuthHeaderWithPrefix));


        assertThatThrownBy(() -> testee.createMailboxSession(request))
            .isExactlyInstanceOf(NoValidAuthHeaderException.class);
    }

    @Test
    public void createMailboxSessionShouldReturnEmptyWhenAuthHeaderIsInvalid() {
        when(mockAuthenticationExtractor.authHeaders(request))
            .thenReturn(Stream.of("bad"));

        assertThatThrownBy(() -> testee.createMailboxSession(request))
            .isExactlyInstanceOf(NoValidAuthHeaderException.class);
    }

    @Test
    public void createMailboxSessionShouldReturnWhenAuthHeadersAreValid(){
        String username = "123456789";
        String validAuthHeader = "valid";
        String fakeAuthHeaderWithPrefix = JWTAuthenticationStrategy.AUTHORIZATION_HEADER_PREFIX + validAuthHeader;
        MailboxSession fakeMailboxSession = mock(MailboxSession.class);

        when(stubTokenVerifier.verify(validAuthHeader)).thenReturn(true);
        when(stubTokenVerifier.extractLogin(validAuthHeader)).thenReturn(username);
        when(mockedMailboxManager.createSystemSession(eq(Username.of(username))))
                .thenReturn(fakeMailboxSession);
        when(mockAuthenticationExtractor.authHeaders(request))
            .thenReturn(Stream.of(fakeAuthHeaderWithPrefix));


        MailboxSession result = testee.createMailboxSession(request);
        assertThat(result).isEqualTo(fakeMailboxSession);
    }

    @Test
    public void createMailboxSessionShouldThrowUponInvalidVirtualHosting() {
        String username = "123456789@domain.tld";
        String validAuthHeader = "valid";
        String fakeAuthHeaderWithPrefix = JWTAuthenticationStrategy.AUTHORIZATION_HEADER_PREFIX + validAuthHeader;
        MailboxSession fakeMailboxSession = mock(MailboxSession.class);

        when(stubTokenVerifier.verify(validAuthHeader)).thenReturn(true);
        when(stubTokenVerifier.extractLogin(validAuthHeader)).thenReturn(username);
        when(mockedMailboxManager.createSystemSession(eq(Username.of(username))))
                .thenReturn(fakeMailboxSession);
        when(mockAuthenticationExtractor.authHeaders(request))
            .thenReturn(Stream.of(fakeAuthHeaderWithPrefix));


        assertThatThrownBy(() -> testee.createMailboxSession(request))
            .isInstanceOf(MailboxSessionCreationException.class);
    }
}