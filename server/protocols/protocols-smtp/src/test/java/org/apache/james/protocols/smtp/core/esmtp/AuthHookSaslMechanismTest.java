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

package org.apache.james.protocols.smtp.core.esmtp;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslFailure;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslMechanismNames;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.apache.james.protocols.sasl.plain.PlainSaslMechanism;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.AuthHook;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class AuthHookSaslMechanismTest {
    private static final Username USERNAME = Username.of("user@domain.tld");

    private final SMTPSession session = mock(SMTPSession.class);
    private final SaslAuthenticator authenticator = mock(SaslAuthenticator.class);

    @Test
    void shouldDelegatePlainAvailabilityToWrappedMechanism() {
        // GIVEN a PLAIN mechanism disabled by the SMTP configuration
        AuthHookSaslMechanism mechanism = new AuthHookSaslMechanism(
            new PlainSaslMechanism(false, true),
            ImmutableList.of(mock(AuthHook.class)),
            ImmutableList.of(),
            session);

        // WHEN checking whether it is available on either transport
        // THEN the legacy adapter preserves the configured policy
        assertThat(mechanism.isAvailableOnTransport(false)).isFalse();
        assertThat(mechanism.isAvailableOnTransport(true)).isFalse();
    }

    @Test
    void shouldFailWhenAllAuthHooksDecline() {
        // GIVEN a legacy hook declining the provided credentials
        AuthHook authHook = mock(AuthHook.class);
        when(authHook.doAuth(any(SMTPSession.class), eq(USERNAME), eq("password"))).thenReturn(HookResult.DECLINED);
        AuthHookSaslMechanism mechanism = new AuthHookSaslMechanism(
            new PlainSaslMechanism(),
            ImmutableList.of(authHook),
            ImmutableList.of(),
            session);

        // WHEN authenticating through the adapter
        SaslStep step = mechanism
            .start(initialRequest("\0user@domain.tld\0password"), authenticator)
            .firstStep();

        // THEN the legacy terminal failure is preserved without a fallback login
        assertThat(step).isEqualTo(new SaslStep.Failure(SaslFailure.invalidCredentials(
            USERNAME,
            Optional.empty(),
            "Invalid credentials")));
    }

    @Test
    void shouldDelegateEmptyPasswordToAuthHooks() {
        // GIVEN the legacy empty-password delegation form
        AuthHook authHook = mock(AuthHook.class);
        when(authHook.doDelegation(eq(session), eq(USERNAME))).thenReturn(HookResult.builder()
            .hookReturnCode(HookReturnCode.ok())
            .build());
        AuthHookSaslMechanism mechanism = new AuthHookSaslMechanism(
            new PlainSaslMechanism(),
            ImmutableList.of(authHook),
            ImmutableList.of(),
            session);

        // WHEN authenticating through the adapter
        SaslStep step = mechanism
            .start(initialRequest("\0user@domain.tld\0"), authenticator)
            .firstStep();

        // THEN the legacy hook receives the delegation call
        assertThat(step).isEqualTo(new SaslStep.Success(
            new SaslIdentity(USERNAME, USERNAME),
            Optional.empty()));
        verify(authHook).doDelegation(session, USERNAME);
    }

    @Test
    void shouldPreserveCustomDeniedResponseAndDisconnect() {
        // GIVEN a legacy hook denying authentication with a custom response and disconnect policy
        AuthHook authHook = mock(AuthHook.class);
        when(authHook.doAuth(eq(session), eq(USERNAME), eq("password"))).thenReturn(HookResult.builder()
            .hookReturnCode(HookReturnCode.disconnected(HookReturnCode.Action.DENY))
            .smtpReturnCode("421")
            .smtpDescription("Too many authentication attempts")
            .build());
        AuthHookSaslMechanism mechanism = new AuthHookSaslMechanism(
            new PlainSaslMechanism(),
            ImmutableList.of(authHook),
            ImmutableList.of(),
            session);

        // WHEN the adapter processes the PLAIN credentials
        AuthHookSaslMechanism.Exchange exchange = (AuthHookSaslMechanism.Exchange) mechanism
            .start(initialRequest("\0user@domain.tld\0password"), authenticator);
        SaslStep step = exchange.firstStep();

        // THEN the SASL failure keeps the legacy SMTP response for the protocol driver
        assertThat(step).isInstanceOf(SaslStep.Failure.class);
        Response response = exchange.terminalResponse().orElseThrow();
        assertThat(response.getRetCode()).isEqualTo("421");
        assertThat(response.getLines()).containsExactly("421 Too many authentication attempts");
        assertThat(response.isEndSession()).isTrue();
    }

    @Test
    void shouldKeepConnectionOpenForSuccessfulAuthHookResponse() {
        // GIVEN a legacy hook accepting authentication while requesting the connection to close
        AuthHook authHook = mock(AuthHook.class);
        when(authHook.doAuth(eq(session), eq(USERNAME), eq("password"))).thenReturn(HookResult.builder()
            .hookReturnCode(HookReturnCode.disconnected(HookReturnCode.Action.OK))
            .smtpReturnCode(SMTPRetCode.AUTH_OK)
            .smtpDescription("Authentication successful")
            .build());
        AuthHookSaslMechanism mechanism = new AuthHookSaslMechanism(
            new PlainSaslMechanism(),
            ImmutableList.of(authHook),
            ImmutableList.of(),
            session);

        // WHEN the adapter processes the PLAIN credentials
        AuthHookSaslMechanism.Exchange exchange = (AuthHookSaslMechanism.Exchange) mechanism
            .start(initialRequest("\0user@domain.tld\0password"), authenticator);
        SaslStep step = exchange.firstStep();

        // THEN SMTP success keeps the connection open
        assertThat(step).isInstanceOf(SaslStep.Success.class);
        assertThat(exchange.terminalResponse().orElseThrow().isEndSession()).isFalse();
    }

    private SaslInitialRequest initialRequest(String value) {
        return new SaslInitialRequest(SaslMechanismNames.PLAIN, Optional.of(value.getBytes(UTF_8)));
    }
}
