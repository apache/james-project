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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.core.Username;
import org.apache.james.protocols.api.sasl.SaslAuthenticationResult;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslFailure;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GssapiSaslMechanismTest {
    private record TestExchange(SaslExchange exchange, SaslServer saslServer, KerberosLoginContext loginContext) {
    }

    private static final Username USERNAME = Username.of("alice@example.com");
    private static final SaslIdentity IDENTITY = new SaslIdentity(USERNAME, USERNAME);
    private static final byte[] INITIAL_TOKEN = {1, 2, 3};
    private static final byte[] SERVER_TOKEN = {4, 5, 6};

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldRequireEncryptedTransportByDefault() {
        GssapiSaslMechanism testee = mechanism(true, mock(KerberosLoginContextFactory.class), mock(GssapiSaslServerFactory.class));

        assertThat(testee.isAvailableOnTransport(false)).isFalse();
        assertThat(testee.isAvailableOnTransport(true)).isTrue();
    }

    @Test
    void shouldAllowClearTransportWhenConfigured() {
        GssapiSaslMechanism testee = mechanism(false, mock(KerberosLoginContextFactory.class), mock(GssapiSaslServerFactory.class));

        assertThat(testee.isAvailableOnTransport(false)).isTrue();
    }

    @Test
    void shouldChallengeWithoutAcquiringCredentialsWhenInitialResponseIsOmitted() throws Exception {
        KerberosLoginContextFactory loginContextFactory = mock(KerberosLoginContextFactory.class);
        GssapiSaslMechanism testee = mechanism(true, loginContextFactory, mock(GssapiSaslServerFactory.class));

        SaslStep step = testee.start(request(Optional.empty()), authenticating()).firstStep();

        assertThat(step).isEqualTo(new SaslStep.Challenge(Optional.empty()));
        verify(loginContextFactory, never()).login(any());
    }

    @Test
    void shouldCompleteInitialResponseAndCleanup() throws Exception {
        TestExchange testExchange = completedExchange(null, "auth", authenticating());

        SaslStep step = testExchange.exchange().firstStep();

        assertThat(step).isEqualTo(new SaslStep.Success(IDENTITY, Optional.empty()));
        verify(testExchange.saslServer()).dispose();
        verify(testExchange.loginContext()).close();
    }

    @Test
    void shouldPreserveMultipleChallengeRounds() throws Exception {
        KerberosLoginContextFactory loginContextFactory = mock(KerberosLoginContextFactory.class);
        KerberosLoginContext loginContext = loginContext(loginContextFactory);
        SaslServer saslServer = mock(SaslServer.class);
        AtomicReference<CallbackHandler> callbackHandler = new AtomicReference<>();
        AtomicInteger evaluation = new AtomicInteger();
        when(saslServer.evaluateResponse(any())).thenAnswer(invocation -> {
            if (evaluation.getAndIncrement() == 0) {
                return SERVER_TOKEN;
            }
            authorize(callbackHandler.get());
            return null;
        });
        when(saslServer.isComplete()).thenReturn(false, true);
        when(saslServer.getNegotiatedProperty(Sasl.QOP)).thenReturn("auth");
        GssapiSaslServerFactory saslServerFactory = capturingFactory(saslServer, callbackHandler);
        SaslExchange exchange = mechanism(true, loginContextFactory, saslServerFactory)
            .start(request(Optional.of(INITIAL_TOKEN)), authenticating());

        assertThat(exchange.firstStep()).isInstanceOfSatisfying(SaslStep.Challenge.class,
            challenge -> assertThat(challenge.payload()).hasValueSatisfying(payload -> assertThat(payload).containsExactly(SERVER_TOKEN)));
        assertThat(exchange.onResponse(new byte[] {7, 8, 9})).isEqualTo(new SaslStep.Success(IDENTITY, Optional.empty()));
        verify(saslServer).dispose();
        verify(loginContext).close();
    }

    @Test
    void shouldPreserveTypedAuthorizationFailure() throws Exception {
        SaslFailure failure = SaslFailure.delegationForbidden(USERNAME, Username.of("bob@example.com"), "Delegation is forbidden.");
        TestExchange testExchange = exchangeWhoseProviderRejectsAuthorization(failure);

        assertThat(testExchange.exchange().firstStep()).isEqualTo(new SaslStep.Failure(failure));
    }

    @Test
    void shouldReleaseLoginContextWhenSaslServerCreationFails() throws Exception {
        KerberosLoginContextFactory loginContextFactory = mock(KerberosLoginContextFactory.class);
        KerberosLoginContext loginContext = loginContext(loginContextFactory);
        GssapiSaslServerFactory saslServerFactory = (configuration, callbackHandler) -> {
            throw new IllegalStateException("provider failure");
        };
        SaslExchange exchange = mechanism(true, loginContextFactory, saslServerFactory)
            .start(request(Optional.of(INITIAL_TOKEN)), authenticating());

        assertThat(exchange.firstStep()).isInstanceOfSatisfying(SaslStep.Failure.class,
            failure -> assertThat(failure.failure().type()).isEqualTo(SaslFailure.Type.SERVER_ERROR));
        verify(loginContext).close();
    }

    @Test
    void shouldCleanupWhenTokenEvaluationFails() throws Exception {
        KerberosLoginContextFactory loginContextFactory = mock(KerberosLoginContextFactory.class);
        KerberosLoginContext loginContext = loginContext(loginContextFactory);
        SaslServer saslServer = mock(SaslServer.class);
        when(saslServer.evaluateResponse(any())).thenThrow(new SaslException("invalid token"));
        GssapiSaslServerFactory saslServerFactory = (configuration, callbackHandler) -> saslServer;
        SaslExchange exchange = mechanism(true, loginContextFactory, saslServerFactory)
            .start(request(Optional.of(INITIAL_TOKEN)), authenticating());

        assertThat(exchange.firstStep()).isInstanceOfSatisfying(SaslStep.Failure.class,
            failure -> assertThat(failure.failure().type()).isEqualTo(SaslFailure.Type.AUTHENTICATION_FAILED));
        verify(saslServer).dispose();
        verify(loginContext).close();
    }

    @Test
    void shouldRejectFinalServerData() throws Exception {
        TestExchange testExchange = completedExchange(SERVER_TOKEN, "auth", authenticating());

        assertThat(testExchange.exchange().firstStep()).isInstanceOfSatisfying(SaslStep.Failure.class,
            failure -> assertThat(failure.failure().type()).isEqualTo(SaslFailure.Type.SERVER_ERROR));
    }

    @Test
    void shouldRejectUnsupportedQop() throws Exception {
        TestExchange testExchange = completedExchange(null, "auth-int", authenticating());

        assertThat(testExchange.exchange().firstStep()).isInstanceOfSatisfying(SaslStep.Failure.class,
            failure -> assertThat(failure.failure().type()).isEqualTo(SaslFailure.Type.SERVER_ERROR));
    }

    @Test
    void closeShouldBeIdempotent() throws Exception {
        KerberosLoginContextFactory loginContextFactory = mock(KerberosLoginContextFactory.class);
        KerberosLoginContext loginContext = loginContext(loginContextFactory);
        SaslServer saslServer = mock(SaslServer.class);
        GssapiSaslServerFactory saslServerFactory = (configuration, callbackHandler) -> saslServer;
        SaslExchange exchange = mechanism(true, loginContextFactory, saslServerFactory)
            .start(request(Optional.of(INITIAL_TOKEN)), authenticating());
        when(saslServer.evaluateResponse(any())).thenReturn(SERVER_TOKEN);
        when(saslServer.isComplete()).thenReturn(false);
        exchange.firstStep();

        exchange.close();
        exchange.close();

        verify(saslServer, times(1)).dispose();
        verify(loginContext, times(1)).close();
    }

    @Test
    void factoryShouldProbeAndReleaseAcceptorCredentials() throws Exception {
        Path keyTab = Files.createFile(temporaryDirectory.resolve("imap.keytab"));
        KerberosLoginContextFactory loginContextFactory = mock(KerberosLoginContextFactory.class);
        KerberosLoginContext loginContext = loginContext(loginContextFactory);
        SaslServer saslServer = mock(SaslServer.class);
        GssapiSaslMechanismFactory testee = new GssapiSaslMechanismFactory(mock(KeyTabPrincipalVerifier.class), loginContextFactory,
            (configuration, callbackHandler) -> saslServer);

        assertThat(testee.create(configuration(keyTab))).isInstanceOf(GssapiSaslMechanism.class);
        verify(saslServer).dispose();
        verify(loginContext).close();
    }

    @Test
    void factoryShouldRejectUnavailableAcceptorCredentials() throws Exception {
        Path keyTab = Files.createFile(temporaryDirectory.resolve("imap.keytab"));
        KerberosLoginContextFactory loginContextFactory = mock(KerberosLoginContextFactory.class);
        when(loginContextFactory.login(any())).thenThrow(new javax.security.auth.login.LoginException("unavailable"));
        GssapiSaslMechanismFactory testee = new GssapiSaslMechanismFactory(mock(KeyTabPrincipalVerifier.class), loginContextFactory,
            mock(GssapiSaslServerFactory.class));

        assertThatThrownBy(() -> testee.create(configuration(keyTab)))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("Unable to acquire configured GSSAPI acceptor credentials");
    }

    @Test
    void factoryShouldRejectKeyTabWithoutConfiguredPrincipalBeforeLogin() throws Exception {
        Path keyTab = Files.createFile(temporaryDirectory.resolve("imap.keytab"));
        KeyTabPrincipalVerifier keyTabPrincipalVerifier = mock(KeyTabPrincipalVerifier.class);
        doThrow(new ConfigurationException("missing principal"))
            .when(keyTabPrincipalVerifier)
            .verify(any());
        KerberosLoginContextFactory loginContextFactory = mock(KerberosLoginContextFactory.class);
        GssapiSaslMechanismFactory testee = new GssapiSaslMechanismFactory(keyTabPrincipalVerifier, loginContextFactory,
            mock(GssapiSaslServerFactory.class));

        assertThatThrownBy(() -> testee.create(configuration(keyTab)))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("missing principal");
        verify(loginContextFactory, never()).login(any());
    }

    private TestExchange completedExchange(byte[] output, String qop, SaslAuthenticator authenticator) throws Exception {
        KerberosLoginContextFactory loginContextFactory = mock(KerberosLoginContextFactory.class);
        KerberosLoginContext loginContext = loginContext(loginContextFactory);
        SaslServer saslServer = mock(SaslServer.class);
        AtomicReference<CallbackHandler> callbackHandler = new AtomicReference<>();
        when(saslServer.evaluateResponse(any())).thenAnswer(invocation -> {
            authorize(callbackHandler.get());
            return output;
        });
        when(saslServer.isComplete()).thenReturn(true);
        when(saslServer.getNegotiatedProperty(Sasl.QOP)).thenReturn(qop);
        GssapiSaslServerFactory saslServerFactory = capturingFactory(saslServer, callbackHandler);
        SaslExchange exchange = mechanism(true, loginContextFactory, saslServerFactory)
            .start(request(Optional.of(INITIAL_TOKEN)), authenticator);
        return new TestExchange(exchange, saslServer, loginContext);
    }

    private TestExchange exchangeWhoseProviderRejectsAuthorization(SaslFailure failure) throws Exception {
        KerberosLoginContextFactory loginContextFactory = mock(KerberosLoginContextFactory.class);
        KerberosLoginContext loginContext = loginContext(loginContextFactory);
        SaslServer saslServer = mock(SaslServer.class);
        AtomicReference<CallbackHandler> callbackHandler = new AtomicReference<>();
        when(saslServer.evaluateResponse(any())).thenAnswer(invocation -> {
            authorize(callbackHandler.get(), "alice@EXAMPLE.COM", "bob@example.com");
            throw new SaslException("not authorized");
        });
        GssapiSaslServerFactory saslServerFactory = capturingFactory(saslServer, callbackHandler);
        SaslExchange exchange = mechanism(true, loginContextFactory, saslServerFactory)
            .start(request(Optional.of(INITIAL_TOKEN)), failing(failure));
        return new TestExchange(exchange, saslServer, loginContext);
    }

    private KerberosLoginContext loginContext(KerberosLoginContextFactory loginContextFactory) throws Exception {
        KerberosLoginContext loginContext = mock(KerberosLoginContext.class);
        when(loginContext.subject()).thenReturn(new Subject());
        when(loginContextFactory.login(any())).thenReturn(loginContext);
        return loginContext;
    }

    private GssapiSaslServerFactory capturingFactory(SaslServer saslServer, AtomicReference<CallbackHandler> callbackHandler) {
        return (configuration, handler) -> {
            callbackHandler.set(handler);
            return saslServer;
        };
    }

    private void authorize(CallbackHandler callbackHandler) throws Exception {
        authorize(callbackHandler, "alice@EXAMPLE.COM", "alice@EXAMPLE.COM");
    }

    private void authorize(CallbackHandler callbackHandler, String authenticationId, String authorizationId) throws Exception {
        callbackHandler.handle(new AuthorizeCallback[] {new AuthorizeCallback(authenticationId, authorizationId)});
    }

    private SaslInitialRequest request(Optional<byte[]> initialResponse) {
        return new SaslInitialRequest(GssapiSaslMechanism.NAME, initialResponse);
    }

    private GssapiSaslMechanism mechanism(boolean requireSSL,
                                          KerberosLoginContextFactory loginContextFactory,
                                          GssapiSaslServerFactory saslServerFactory) {
        GssapiSaslConfiguration configuration = new GssapiSaslConfiguration(
            "imap", "mail.example.com", "imap/mail.example.com@EXAMPLE.COM", Path.of("imap.keytab"), requireSSL);
        return new GssapiSaslMechanism(configuration, loginContextFactory, saslServerFactory);
    }

    private SaslAuthenticator authenticating() {
        return new SaslAuthenticator() {
            @Override
            public SaslAuthenticationResult authenticatePassword(Username authenticationId, Optional<Username> authorizationId, String password) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SaslAuthenticationResult authorize(SaslIdentity identity) {
                return new SaslAuthenticationResult.Success(identity);
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

    private BaseHierarchicalConfiguration configuration(Path keyTab) {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("auth.gssapi.serviceName", "imap");
        configuration.addProperty("auth.gssapi.serverName", "mail.example.com");
        configuration.addProperty("auth.gssapi.principal", "imap/mail.example.com@EXAMPLE.COM");
        configuration.addProperty("auth.gssapi.keyTab", keyTab.toString());
        return configuration;
    }
}
