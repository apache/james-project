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

import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.jmap.exceptions.UnauthorizedException;
import org.apache.james.jmap.jwt.JWTAuthenticationStrategy;
import org.apache.james.jwt.JwtTokenVerifier;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import com.google.common.collect.ImmutableList;

import io.netty.handler.codec.http.HttpHeaders;
import reactor.netty.http.server.HttpServerRequest;

public class JWTAuthenticationStrategyTest {
    private static final String AUTHORIZATION_HEADERS = "Authorization";
    private static final DomainList NO_DOMAIN_LIST = null;

    private JWTAuthenticationStrategy testee;
    private MailboxManager mockedMailboxManager;
    private JwtTokenVerifier stubTokenVerifier;
    private HttpServerRequest mockedRequest;
    private HttpHeaders mockedHeaders;

    @BeforeEach
    public void setup() {
        stubTokenVerifier = mock(JwtTokenVerifier.class);
        mockedMailboxManager = mock(MailboxManager.class);
        mockedRequest = mock(HttpServerRequest.class);
        mockedHeaders = mock(HttpHeaders.class);
        MemoryUsersRepository usersRepository = MemoryUsersRepository.withoutVirtualHosting(NO_DOMAIN_LIST);

        when(mockedRequest.requestHeaders())
            .thenReturn(mockedHeaders);

        testee = new JWTAuthenticationStrategy(stubTokenVerifier, mockedMailboxManager, usersRepository);
    }

    @Nested
    class HTTP {
        @Test
        public void createMailboxSessionShouldReturnEmptyWhenAuthHeaderIsEmpty() {
            when(mockedHeaders.get(AUTHORIZATION_HEADERS))
                .thenReturn("");

            assertThat(testee.createMailboxSession(mockedRequest).blockOptional())
                .isEmpty();
        }

        @Test
        public void createMailboxSessionShouldThrowWhenAuthHeadersIsInvalid() {
            String username = "123456789";
            String validAuthHeader = "valid";
            String fakeAuthHeaderWithPrefix = JWTAuthenticationStrategy.AUTHORIZATION_HEADER_PREFIX + validAuthHeader;
            MailboxSession fakeMailboxSession = mock(MailboxSession.class);

            when(stubTokenVerifier.verify(validAuthHeader)).thenReturn(false);
            when(stubTokenVerifier.extractLogin(validAuthHeader)).thenReturn(username);
            when(mockedMailboxManager.createSystemSession(eq(Username.of(username))))
                .thenReturn(fakeMailboxSession);

            when(mockedHeaders.get(AUTHORIZATION_HEADERS))
                .thenReturn(fakeAuthHeaderWithPrefix);

            assertThatThrownBy(() -> testee.createMailboxSession(mockedRequest).blockOptional())
                .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        public void createMailboxSessionShouldReturnEmptyWhenAuthHeaderIsInvalid() {
            when(mockedHeaders.get(AUTHORIZATION_HEADERS))
                .thenReturn("bad");

            assertThat(testee.createMailboxSession(mockedRequest).blockOptional())
                .isEmpty();
        }

        @Test
        public void createMailboxSessionShouldThrowWhenMultipleHeaders() {
            String authHeader1 = JWTAuthenticationStrategy.AUTHORIZATION_HEADER_PREFIX + "token1";
            String authHeader2 = JWTAuthenticationStrategy.AUTHORIZATION_HEADER_PREFIX + "token2";

            when(mockedHeaders.getAll(AUTHORIZATION_HEADERS))
                .thenReturn(ImmutableList.of(authHeader1, authHeader2));

            assertThatThrownBy(() -> testee.createMailboxSession(mockedRequest).blockOptional())
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        public void createMailboxSessionShouldReturnWhenAuthHeadersAreValid() {
            String username = "123456789";
            String validAuthHeader = "valid";
            String fakeAuthHeaderWithPrefix = JWTAuthenticationStrategy.AUTHORIZATION_HEADER_PREFIX + validAuthHeader;
            MailboxSession fakeMailboxSession = mock(MailboxSession.class);

            when(stubTokenVerifier.verify(validAuthHeader)).thenReturn(true);
            when(stubTokenVerifier.extractLogin(validAuthHeader)).thenReturn(username);
            when(mockedMailboxManager.createSystemSession(eq(Username.of(username))))
                .thenReturn(fakeMailboxSession);
            when(mockedHeaders.get(AUTHORIZATION_HEADERS))
                .thenReturn(fakeAuthHeaderWithPrefix);

            MailboxSession result = testee.createMailboxSession(mockedRequest).block();
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
            when(mockedHeaders.get(AUTHORIZATION_HEADERS))
                .thenReturn(fakeAuthHeaderWithPrefix);


            assertThatThrownBy(() -> testee.createMailboxSession(mockedRequest).block())
                .isInstanceOf(UnauthorizedException.class);
        }
    }

    @Nested
    class Authoriztion {
        @Test
        public void createMailboxSessionShouldReturnEmptyWhenAuthHeaderIsEmpty() {

            assertThat(testee.createMailboxSession(new Authenticator.Authorization("")).blockOptional())
                .isEmpty();
        }

        @Test
        public void createMailboxSessionShouldThrowWhenAuthHeadersIsInvalid() {
            String username = "123456789";
            String validAuthHeader = "valid";
            String fakeAuthHeaderWithPrefix = JWTAuthenticationStrategy.AUTHORIZATION_HEADER_PREFIX + validAuthHeader;
            MailboxSession fakeMailboxSession = mock(MailboxSession.class);

            when(stubTokenVerifier.verify(validAuthHeader)).thenReturn(false);
            when(stubTokenVerifier.extractLogin(validAuthHeader)).thenReturn(username);
            when(mockedMailboxManager.createSystemSession(eq(Username.of(username))))
                .thenReturn(fakeMailboxSession);

            assertThatThrownBy(() -> testee.createMailboxSession(new Authenticator.Authorization(fakeAuthHeaderWithPrefix)).blockOptional())
                .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        public void createMailboxSessionShouldReturnEmptyWhenAuthHeaderIsInvalid() {
            assertThat(testee.createMailboxSession(new Authenticator.Authorization("bad")).blockOptional())
                .isEmpty();
        }

        @Test
        public void createMailboxSessionShouldReturnWhenAuthHeadersAreValid() {
            String username = "123456789";
            String validAuthHeader = "valid";
            String fakeAuthHeaderWithPrefix = JWTAuthenticationStrategy.AUTHORIZATION_HEADER_PREFIX + validAuthHeader;
            MailboxSession fakeMailboxSession = mock(MailboxSession.class);

            when(stubTokenVerifier.verify(validAuthHeader)).thenReturn(true);
            when(stubTokenVerifier.extractLogin(validAuthHeader)).thenReturn(username);
            when(mockedMailboxManager.createSystemSession(eq(Username.of(username))))
                .thenReturn(fakeMailboxSession);
            when(mockedHeaders.get(AUTHORIZATION_HEADERS))
                .thenReturn(fakeAuthHeaderWithPrefix);

            MailboxSession result = testee.createMailboxSession(new Authenticator.Authorization(fakeAuthHeaderWithPrefix)).block();
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
            when(mockedHeaders.get(AUTHORIZATION_HEADERS))
                .thenReturn(fakeAuthHeaderWithPrefix);


            assertThatThrownBy(() -> testee.createMailboxSession(new Authenticator.Authorization(fakeAuthHeaderWithPrefix)).block())
                .isInstanceOf(UnauthorizedException.class);
        }
    }
}
