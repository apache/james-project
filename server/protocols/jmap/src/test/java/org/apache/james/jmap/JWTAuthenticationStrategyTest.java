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

import java.util.stream.Stream;

import org.apache.james.jmap.crypto.JwtTokenVerifier;
import org.apache.james.jmap.exceptions.MailboxSessionCreationException;
import org.apache.james.jmap.exceptions.NoValidAuthHeaderException;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;


public class JWTAuthenticationStrategyTest {

    private JWTAuthenticationStrategy testee;
    private MailboxManager mockedMailboxManager;
    private JwtTokenVerifier stubTokenVerifier;

    @Before
    public void setup() {
        mockedMailboxManager = mock(MailboxManager.class);

        stubTokenVerifier = mock(JwtTokenVerifier.class);

        testee = new JWTAuthenticationStrategy(stubTokenVerifier, mockedMailboxManager);
    }


    @Test
    public void createMailboxSessionShouldThrowWhenAuthHeaderIsEmpty() throws Exception {
        assertThatThrownBy(() -> testee.createMailboxSession(Stream.empty()))
            .isExactlyInstanceOf(NoValidAuthHeaderException.class);
    }

    @Test
    public void createMailboxSessionShouldReturnEmptyWhenAuthHeaderIsInvalid() throws Exception {
        assertThatThrownBy(() -> testee.createMailboxSession(Stream.of("bad")))
            .isExactlyInstanceOf(NoValidAuthHeaderException.class);
    }

    @Test
    public void createMailboxSessionShouldThrowWhenMailboxExceptionHasOccurred() throws Exception {
        String username = "username";
        String validAuthHeader = "valid";
        String fakeAuthHeaderWithPrefix = JWTAuthenticationStrategy.AUTHORIZATION_HEADER_PREFIX + validAuthHeader;

        when(stubTokenVerifier.verify(validAuthHeader)).thenReturn(true);
        when(stubTokenVerifier.extractLogin(validAuthHeader)).thenReturn(username);
        when(mockedMailboxManager.createSystemSession(eq(username), any(Logger.class)))
                .thenThrow(new MailboxException());

        assertThatThrownBy(() -> testee.createMailboxSession(Stream.of(fakeAuthHeaderWithPrefix)))
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
        when(mockedMailboxManager.createSystemSession(eq(username), any(Logger.class)))
                .thenReturn(fakeMailboxSession);

        MailboxSession result = testee.createMailboxSession(Stream.of(fakeAuthHeaderWithPrefix));
        assertThat(result).isEqualTo(fakeMailboxSession);
    }

    @Test
    public void checkAuthorizationHeaderShouldReturnFalsewWhenAuthHeaderIsEmpty() {
        assertThat(testee.checkAuthorizationHeader(Stream.empty())).isFalse();
    }

    @Test
    public void checkAuthorizationHeaderShouldReturnFalseWhenAuthHeaderIsInvalid() {
        String wrongAuthHeader = "invalid";
        String fakeAuthHeaderWithPrefix = JWTAuthenticationStrategy.AUTHORIZATION_HEADER_PREFIX + wrongAuthHeader;

        when(stubTokenVerifier.verify(wrongAuthHeader)).thenReturn(false);

        assertThat(testee.checkAuthorizationHeader(Stream.of(fakeAuthHeaderWithPrefix))).isFalse();
    }

    @Test
    public void checkAuthorizationHeaderShouldReturnFalseWhenAuthHeadersAreInvalid() {
        String wrongAuthHeader = "invalid";
        String invalidAuthHeader = "INVALID";

        when(stubTokenVerifier.verify(wrongAuthHeader)).thenReturn(false);
        when(stubTokenVerifier.verify(invalidAuthHeader)).thenReturn(false);

        Stream<String> authHeadersStream = Stream.of(wrongAuthHeader, invalidAuthHeader)
                .map(h -> JWTAuthenticationStrategy.AUTHORIZATION_HEADER_PREFIX + h);
        assertThat(testee.checkAuthorizationHeader(authHeadersStream)).isFalse();
    }

    @Test
    public void checkAuthorizationHeaderShouldReturnTrueWhenAuthHeaderIsValid() {
        String validAuthHeader = "valid";
        String validAuthHeaderWithPrefix = JWTAuthenticationStrategy.AUTHORIZATION_HEADER_PREFIX + validAuthHeader;

        when(stubTokenVerifier.verify(validAuthHeader)).thenReturn(true);

        assertThat(testee.checkAuthorizationHeader(Stream.of(validAuthHeaderWithPrefix))).isTrue();
    }

    @Test
    public void checkAuthorizationHeaderShouldReturnTrueWhenOneAuthHeaderIsValid() {
        String dummyAuthHeader = "invalid";
        String validAuthHeader = "correct";

        when(stubTokenVerifier.verify(dummyAuthHeader)).thenReturn(false);
        when(stubTokenVerifier.verify(validAuthHeader)).thenReturn(true);

        Stream<String> authHeadersStream = Stream.of(dummyAuthHeader, validAuthHeader)
                .map(h -> JWTAuthenticationStrategy.AUTHORIZATION_HEADER_PREFIX + h);
        assertThat(testee.checkAuthorizationHeader(authHeadersStream)).isTrue();
    }

}