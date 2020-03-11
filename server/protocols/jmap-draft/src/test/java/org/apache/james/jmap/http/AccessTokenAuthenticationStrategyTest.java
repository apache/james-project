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

import org.apache.james.core.Username;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.api.access.exceptions.NotAnAccessTokenException;
import org.apache.james.jmap.draft.crypto.AccessTokenManagerImpl;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
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
        when(mockedHeaders.getAll(AUTHORIZATION_HEADERS))
            .thenReturn(ImmutableList.of());

        assertThat(testee.createMailboxSession(mockedRequest).blockOptional())
            .isEmpty();
    }

    @Test
    public void createMailboxSessionShouldThrowWhenAuthHeaderIsNotAnUUID() {
        when(mockedHeaders.getAll(AUTHORIZATION_HEADERS))
            .thenReturn(ImmutableList.of("bad"));

        assertThatThrownBy(() -> testee.createMailboxSession(mockedRequest).block())
                .isExactlyInstanceOf(NotAnAccessTokenException.class);
    }

    @Test
    public void createMailboxSessionShouldReturnEmptyWhenAuthHeaderIsInvalid() {
        Username username = Username.of("123456789");
        MailboxSession fakeMailboxSession = mock(MailboxSession.class);

        when(mockedMailboxManager.createSystemSession(eq(username)))
            .thenReturn(fakeMailboxSession);

        UUID authHeader = UUID.randomUUID();
        AccessToken accessToken = AccessToken.fromString(authHeader.toString());
        when(mockedAccessTokenManager.getUsernameFromToken(accessToken))
                .thenReturn(Mono.just(username));
        when(mockedHeaders.getAll(AUTHORIZATION_HEADERS))
            .thenReturn(ImmutableList.of(authHeader.toString()));
        when(mockedAccessTokenManager.isValid(accessToken))
            .thenReturn(Mono.just(false));

        assertThat(testee.createMailboxSession(mockedRequest).blockOptional())
            .isEmpty();
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
            .thenReturn(Mono.just(username));
        when(mockedHeaders.getAll(AUTHORIZATION_HEADERS))
            .thenReturn(ImmutableList.of(authHeader.toString()));
        when(mockedAccessTokenManager.isValid(accessToken))
            .thenReturn(Mono.just(true));


        MailboxSession result = testee.createMailboxSession(mockedRequest).block();
        assertThat(result).isEqualTo(fakeMailboxSession);
    }
}
