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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.api.access.AccessTokenRepository;
import org.apache.james.jmap.draft.exceptions.MailboxSessionCreationException;
import org.apache.james.jmap.memory.access.MemoryAccessTokenRepository;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class AuthenticationFilterTest {
    private static final String TOKEN = "df991d2a-1c5a-4910-a90f-808b6eda133e";
    public static final Username USERNAME = Username.of("user@domain.tld");

    private HttpServletRequest mockedRequest;
    private HttpServletResponse mockedResponse;
    private AccessTokenRepository accessTokenRepository;
    private AuthenticationFilter testee;
    private FilterChain filterChain;

    @Before
    public void setup() throws Exception {
        mockedRequest = mock(HttpServletRequest.class);
        mockedResponse = mock(HttpServletResponse.class);

        accessTokenRepository = new MemoryAccessTokenRepository(TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS));

        when(mockedRequest.getMethod()).thenReturn("POST");
        List<AuthenticationStrategy> fakeAuthenticationStrategies = ImmutableList.of(new FakeAuthenticationStrategy(false));

        testee = new AuthenticationFilter(fakeAuthenticationStrategies, new RecordingMetricFactory());
        filterChain = mock(FilterChain.class);
    }

    @Test
    public void filterShouldReturnUnauthorizedOnNullAuthorizationHeader() throws Exception {
        when(mockedRequest.getHeader("Authorization"))
            .thenReturn(null);

        testee.doFilter(mockedRequest, mockedResponse, filterChain);

        verify(mockedResponse).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
    
    @Test
    public void filterShouldReturnUnauthorizedOnInvalidAuthorizationHeader() throws Exception {
        when(mockedRequest.getHeader("Authorization"))
            .thenReturn(TOKEN);

        testee.doFilter(mockedRequest, mockedResponse, filterChain);

        verify(mockedResponse).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    public void filterShouldChainOnValidAuthorizationHeader() throws Exception {
        AccessToken token = AccessToken.fromString(TOKEN);
        when(mockedRequest.getHeader("Authorization"))
            .thenReturn(TOKEN);

        accessTokenRepository.addToken(USERNAME, token).block();

        AuthenticationFilter sut = new AuthenticationFilter(ImmutableList.of(new FakeAuthenticationStrategy(true)), new RecordingMetricFactory());
        sut.doFilter(mockedRequest, mockedResponse, filterChain);

        verify(filterChain).doFilter(any(ServletRequest.class), eq(mockedResponse));
    }

    @Test
    public void filterShouldChainAuthorizationStrategy() throws Exception {
        AccessToken token = AccessToken.fromString(TOKEN);
        when(mockedRequest.getHeader("Authorization"))
            .thenReturn(TOKEN);

        accessTokenRepository.addToken(USERNAME, token).block();

        AuthenticationFilter sut = new AuthenticationFilter(ImmutableList.of(new FakeAuthenticationStrategy(false), new FakeAuthenticationStrategy(true)), new RecordingMetricFactory());
        sut.doFilter(mockedRequest, mockedResponse, filterChain);

        verify(filterChain).doFilter(any(ServletRequest.class), eq(mockedResponse));
    }

    @Test
    public void filterShouldReturnUnauthorizedOnBadAuthorizationHeader() throws Exception {
        when(mockedRequest.getHeader("Authorization"))
            .thenReturn("bad");

        testee.doFilter(mockedRequest, mockedResponse, filterChain);
        
        verify(mockedResponse).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    public void filterShouldReturnUnauthorizedWhenNoStrategy() throws Exception {
        when(mockedRequest.getHeader("Authorization"))
                .thenReturn(TOKEN);

        AuthenticationFilter sut = new AuthenticationFilter(ImmutableList.of(), new RecordingMetricFactory());
        sut.doFilter(mockedRequest, mockedResponse, filterChain);

        verify(mockedResponse).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

    private static class FakeAuthenticationStrategy implements AuthenticationStrategy {

        private final boolean isAuthorized;

        private FakeAuthenticationStrategy(boolean isAuthorized) {
            this.isAuthorized = isAuthorized;
        }

        @Override
        public MailboxSession createMailboxSession(HttpServletRequest httpRequest) {
            if (!isAuthorized) {
                throw new MailboxSessionCreationException(null);
            }
            return mock(MailboxSession.class);
        }
    }
}
