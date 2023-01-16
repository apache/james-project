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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.function.Predicate;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.api.access.exceptions.InvalidAccessToken;
import org.apache.james.jmap.draft.crypto.AccessTokenManagerImpl;
import org.apache.james.jmap.exceptions.UnauthorizedException;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.exception.MailboxException;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

import io.netty.handler.codec.http.HttpHeaders;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

public class AccessTokenAuthenticationStrategyTest {
    private static final String AUTHORIZATION_HEADERS = "Authorization";

    private AccessTokenManagerImpl mockedAccessTokenManager;
    private MailboxManager mockedMailboxManager;
    private AccessTokenAuthenticationStrategy testee;
    private HttpServerRequest mockedRequest;
    private HttpHeaders mockedHeaders;

    @Before
    public void setup() {
        mockedAccessTokenManager = mock(AccessTokenManagerImpl.class);
        mockedMailboxManager = mock(MailboxManager.class);
        mockedRequest = mock(HttpServerRequest.class);
        mockedHeaders = mock(HttpHeaders.class);

        when(mockedRequest.requestHeaders())
            .thenReturn(mockedHeaders);

        testee = new AccessTokenAuthenticationStrategy(mockedAccessTokenManager, mockedMailboxManager);
    }

    @Test
    public void createMailboxSessionShouldReturnEmptyWhenNoAuthProvided() {
        assertThat(testee.createMailboxSession(mockedRequest).blockOptional())
            .isEmpty();
    }

    @Test
    public void createMailboxSessionShouldThrowWhenAuthHeaderIsNotAnUUID() {
        when(mockedHeaders.get(AUTHORIZATION_HEADERS))
            .thenReturn("bad");

        assertThatThrownBy(() -> testee.createMailboxSession(mockedRequest).block())
                .isExactlyInstanceOf(UnauthorizedException.class);
    }

    @Test
    public void createMailboxSessionShouldThrowWhenAuthHeaderIsInvalid() {
        Username username = Username.of("123456789");
        MailboxSession fakeMailboxSession = mock(MailboxSession.class);

        when(mockedMailboxManager.createSystemSession(eq(username)))
            .thenReturn(fakeMailboxSession);

        UUID authHeader = UUID.randomUUID();
        AccessToken accessToken = AccessToken.fromString(authHeader.toString());
        when(mockedAccessTokenManager.getUsernameFromToken(accessToken))
            .thenReturn(Mono.error(new InvalidAccessToken(accessToken)));
        when(mockedHeaders.get(AUTHORIZATION_HEADERS))
            .thenReturn(authHeader.toString());

        assertThatThrownBy(() -> testee.createMailboxSession(mockedRequest).blockOptional())
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    public void createMailboxSessionShouldThrowWhenMultipleAuthHeaders() {
        UUID authHeader1 = UUID.randomUUID();
        UUID authHeader2 = UUID.randomUUID();

        when(mockedHeaders.getAll(AUTHORIZATION_HEADERS))
            .thenReturn(ImmutableList.of(authHeader1.toString(), authHeader2.toString()));

        assertThatThrownBy(() -> testee.createMailboxSession(mockedRequest).block())
            .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void createMailboxSessionShouldReturnWhenAuthHeadersAreValid() {
        Username username = Username.of("123456789");
        MailboxSession fakeMailboxSession = mock(MailboxSession.class);

        when(mockedMailboxManager.authenticate(eq(username)))
            .thenReturn(new SessionProvider.AuthorizationStep() {
                @Override
                public MailboxSession as(Username other) {
                    throw new NotImplementedException();
                }

                @Override
                public MailboxSession withoutDelegation() {
                    return fakeMailboxSession;
                }

                @Override
                public MailboxSession forMatchingUser(Predicate<Username> other) throws MailboxException {
                    throw new NotImplementedException();
                }
            });

        UUID authHeader = UUID.randomUUID();
        AccessToken accessToken = AccessToken.fromString(authHeader.toString());
        when(mockedAccessTokenManager.getUsernameFromToken(accessToken))
            .thenReturn(Mono.just(username));
        when(mockedHeaders.get(AUTHORIZATION_HEADERS))
            .thenReturn(authHeader.toString());


        MailboxSession result = testee.createMailboxSession(mockedRequest).block();
        assertThat(result).isEqualTo(fakeMailboxSession);
    }
}
