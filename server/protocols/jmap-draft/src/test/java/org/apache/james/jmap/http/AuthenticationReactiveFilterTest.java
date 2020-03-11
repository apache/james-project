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
package org.apache.james.jmap.http;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.api.access.AccessTokenRepository;
import org.apache.james.jmap.draft.exceptions.MailboxSessionCreationException;
import org.apache.james.jmap.draft.exceptions.UnauthorizedException;
import org.apache.james.jmap.memory.access.MemoryAccessTokenRepository;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

public class AuthenticationReactiveFilterTest {
    private static final boolean AUTHORIZED = true;
    private static final String TOKEN = "df991d2a-1c5a-4910-a90f-808b6eda133e";
    private static final String AUTHORIZATION_HEADERS = "Authorization";
    private static final Username USERNAME = Username.of("user@domain.tld");

    private HttpServerRequest mockedRequest;
    private HttpHeaders mockedHeaders;
    private AccessTokenRepository accessTokenRepository;
    private AuthenticationReactiveFilter testee;

    @Before
    public void setup() throws Exception {
        mockedRequest = mock(HttpServerRequest.class);
        mockedHeaders = mock(HttpHeaders.class);

        accessTokenRepository = new MemoryAccessTokenRepository(TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS));

        when(mockedRequest.method())
            .thenReturn(HttpMethod.POST);

        when(mockedRequest.requestHeaders())
            .thenReturn(mockedHeaders);

        List<AuthenticationStrategy> fakeAuthenticationStrategies = ImmutableList.of(new FakeAuthenticationStrategy(!AUTHORIZED));

        testee = new AuthenticationReactiveFilter(fakeAuthenticationStrategies, new RecordingMetricFactory());
    }

    @Test
    public void filterShouldReturnUnauthorizedOnNullAuthorizationHeader() {
        when(mockedHeaders.get(AUTHORIZATION_HEADERS))
            .thenReturn(null);

        assertThatThrownBy(() -> testee.authenticate(mockedRequest).block())
            .isInstanceOf(UnauthorizedException.class);
    }
    
    @Test
    public void filterShouldReturnUnauthorizedOnInvalidAuthorizationHeader() {
        when(mockedHeaders.get(AUTHORIZATION_HEADERS))
            .thenReturn(TOKEN);

        assertThatThrownBy(() -> testee.authenticate(mockedRequest).block())
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    public void filterShouldReturnUnauthorizedOnBadAuthorizationHeader() {
        when(mockedHeaders.get(AUTHORIZATION_HEADERS))
            .thenReturn("bad");

        assertThatThrownBy(() -> testee.authenticate(mockedRequest).block())
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    public void filterShouldReturnUnauthorizedWhenNoStrategy() {
        when(mockedHeaders.get(AUTHORIZATION_HEADERS))
            .thenReturn(TOKEN);

        AuthenticationReactiveFilter authFilter = new AuthenticationReactiveFilter(ImmutableList.of(), new RecordingMetricFactory());
        assertThatThrownBy(() -> authFilter.authenticate(mockedRequest).block())
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    public void filterShouldNotThrowOnValidAuthorizationHeader() {
        AccessToken token = AccessToken.fromString(TOKEN);
        when(mockedHeaders.get(AUTHORIZATION_HEADERS))
            .thenReturn(TOKEN);

        accessTokenRepository.addToken(USERNAME, token).block();

        AuthenticationReactiveFilter authFilter = new AuthenticationReactiveFilter(ImmutableList.of(new FakeAuthenticationStrategy(AUTHORIZED)), new RecordingMetricFactory());

        assertThatCode(() -> authFilter.authenticate(mockedRequest).block())
            .doesNotThrowAnyException();
    }

    @Test
    public void filterShouldNotThrowWhenChainingAuthorizationStrategies() {
        AccessToken token = AccessToken.fromString(TOKEN);
        when(mockedHeaders.get(AUTHORIZATION_HEADERS))
            .thenReturn(TOKEN);

        accessTokenRepository.addToken(USERNAME, token).block();

        AuthenticationReactiveFilter authFilter = new AuthenticationReactiveFilter(ImmutableList.of(new FakeAuthenticationStrategy(!AUTHORIZED), new FakeAuthenticationStrategy(AUTHORIZED)), new RecordingMetricFactory());

        assertThatCode(() -> authFilter.authenticate(mockedRequest).block())
            .doesNotThrowAnyException();
    }

    private static class FakeAuthenticationStrategy implements AuthenticationStrategy {

        private final boolean isAuthorized;

        private FakeAuthenticationStrategy(boolean isAuthorized) {
            this.isAuthorized = isAuthorized;
        }

        @Override
        public Mono<MailboxSession> createMailboxSession(HttpServerRequest httpRequest) {
            if (!isAuthorized) {
                return Mono.error(new MailboxSessionCreationException(null));
            }
            return Mono.just(mock(MailboxSession.class));
        }
    }
}
