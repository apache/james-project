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

package org.apache.james.protocols.sasl.kerberos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;

import org.apache.james.core.Username;
import org.apache.james.protocols.api.sasl.SaslAuthenticationResult;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslFailure;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GssapiAuthorizeCallbackHandlerTest {
    private static Stream<Arguments> normalizationCollisions() {
        return Stream.of(
            Arguments.of("alice@[EXAMPLE.COM]", "alice@EXAMPLE.COM"),
            Arguments.of("alice@EXAMPLE.K", "alice@EXAMPLE.K"));
    }

    @Test
    void shouldAuthorizeCanonicalSelfIdentity() throws Exception {
        AtomicReference<SaslIdentity> identity = new AtomicReference<>();
        GssapiAuthorizeCallbackHandler testee = new GssapiAuthorizeCallbackHandler(authorizing(identity));
        AuthorizeCallback callback = new AuthorizeCallback("alice@EXAMPLE.COM", "alice@EXAMPLE.COM");

        testee.handle(new AuthorizeCallback[] {callback});

        SaslIdentity expectedIdentity = new SaslIdentity(Username.of("alice@example.com"), Username.of("alice@example.com"));
        assertThat(identity.get()).isEqualTo(expectedIdentity);
        assertThat(callback.isAuthorized()).isTrue();
        assertThat(callback.getAuthorizedID()).isEqualTo("alice@example.com");
    }

    @Test
    void shouldRejectNonCanonicalPrincipalComponentCase() throws Exception {
        AtomicReference<SaslIdentity> identity = new AtomicReference<>();
        GssapiAuthorizeCallbackHandler testee = new GssapiAuthorizeCallbackHandler(authorizing(identity));
        AuthorizeCallback callback = new AuthorizeCallback("Alice@EXAMPLE.COM", "Alice@EXAMPLE.COM");

        testee.handle(new AuthorizeCallback[] {callback});

        assertThat(identity.get()).isNull();
        assertThat(callback.isAuthorized()).isFalse();
        assertThat(testee.result()).hasValueSatisfying(result -> assertThat(result)
            .isInstanceOfSatisfying(SaslAuthenticationResult.Failure.class,
                actual -> assertThat(actual.failure().type()).isEqualTo(SaslFailure.Type.MALFORMED)));
    }

    @Test
    void shouldRejectNonCanonicalRealmCase() throws Exception {
        AtomicReference<SaslIdentity> identity = new AtomicReference<>();
        GssapiAuthorizeCallbackHandler testee = new GssapiAuthorizeCallbackHandler(authorizing(identity));
        AuthorizeCallback callback = new AuthorizeCallback("alice@example.com", "alice@example.com");

        testee.handle(new AuthorizeCallback[] {callback});

        assertThat(identity.get()).isNull();
        assertThat(callback.isAuthorized()).isFalse();
        assertThat(testee.result()).hasValueSatisfying(result -> assertThat(result)
            .isInstanceOfSatisfying(SaslAuthenticationResult.Failure.class,
                actual -> assertThat(actual.failure().type()).isEqualTo(SaslFailure.Type.MALFORMED)));
    }

    @ParameterizedTest
    @MethodSource("normalizationCollisions")
    void shouldRejectAuthenticationIdentityNormalizationCollisions(String authenticationId, String collidingCanonicalIdentity) throws Exception {
        AtomicReference<SaslIdentity> identity = new AtomicReference<>();
        GssapiAuthorizeCallbackHandler testee = new GssapiAuthorizeCallbackHandler(authorizing(identity));
        AuthorizeCallback callback = new AuthorizeCallback(authenticationId, authenticationId);

        assertThat(Username.of(authenticationId)).isEqualTo(Username.of(collidingCanonicalIdentity));

        testee.handle(new AuthorizeCallback[] {callback});

        assertThat(identity.get()).isNull();
        assertThat(callback.isAuthorized()).isFalse();
        assertThat(testee.result()).hasValueSatisfying(result -> assertThat(result)
            .isInstanceOfSatisfying(SaslAuthenticationResult.Failure.class,
                actual -> assertThat(actual.failure().type()).isEqualTo(SaslFailure.Type.MALFORMED)));
    }

    @Test
    void shouldPreserveTypedAuthorizationFailure() throws Exception {
        SaslFailure failure = SaslFailure.delegationForbidden(
            Username.of("alice@example.com"), Username.of("bob@example.com"), "Delegation is forbidden.");
        GssapiAuthorizeCallbackHandler testee = new GssapiAuthorizeCallbackHandler(failing(failure));
        AuthorizeCallback callback = new AuthorizeCallback("alice@EXAMPLE.COM", "bob@example.com");

        testee.handle(new AuthorizeCallback[] {callback});

        assertThat(callback.isAuthorized()).isFalse();
        assertThat(testee.result()).contains(new SaslAuthenticationResult.Failure(failure));
    }

    @Test
    void shouldRejectMalformedIdentity() throws Exception {
        GssapiAuthorizeCallbackHandler testee = new GssapiAuthorizeCallbackHandler(failing(
            SaslFailure.authenticationFailed(Optional.empty(), Optional.empty(), "unused")));
        AuthorizeCallback callback = new AuthorizeCallback("", "");

        testee.handle(new AuthorizeCallback[] {callback});

        assertThat(callback.isAuthorized()).isFalse();
        assertThat(testee.result()).hasValueSatisfying(result -> assertThat(result)
            .isInstanceOfSatisfying(SaslAuthenticationResult.Failure.class,
                actual -> assertThat(actual.failure().type()).isEqualTo(SaslFailure.Type.MALFORMED)));
    }

    @Test
    void shouldRejectUnexpectedCallback() {
        GssapiAuthorizeCallbackHandler testee = new GssapiAuthorizeCallbackHandler(failing(
            SaslFailure.authenticationFailed(Optional.empty(), Optional.empty(), "unused")));

        assertThatThrownBy(() -> testee.handle(new NameCallback[] {new NameCallback("name")}))
            .isInstanceOf(UnsupportedCallbackException.class);
    }

    private SaslAuthenticator authorizing(AtomicReference<SaslIdentity> identity) {
        return new SaslAuthenticator() {
            @Override
            public SaslAuthenticationResult authenticatePassword(Username authenticationId, Optional<Username> authorizationId, String password) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SaslAuthenticationResult authorize(SaslIdentity value) {
                identity.set(value);
                return new SaslAuthenticationResult.Success(value);
            }
        };
    }

    private SaslAuthenticator failing(SaslFailure failure) {
        return new SaslAuthenticator() {
            @Override
            public SaslAuthenticationResult authenticatePassword(Username authenticationId, Optional<Username> authorizationId, String password) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SaslAuthenticationResult authorize(SaslIdentity identity) {
                return new SaslAuthenticationResult.Failure(failure);
            }
        };
    }
}
