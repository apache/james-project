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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.apache.james.core.Username;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslFailure;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismNames;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.AuthHook;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookResultHook;
import org.apache.james.protocols.smtp.hook.HookReturnCode;

import com.google.common.collect.ImmutableList;

/**
 * Legacy AuthHook adapter exposed as a standard PLAIN SASL mechanism.
 */
class AuthHookSaslMechanism implements SaslMechanism {
    static Optional<Response> terminalResponse(SaslExchange exchange) {
        if (exchange instanceof AuthHookSaslMechanism.Exchange authHookExchange) {
            return authHookExchange.terminalResponse();
        }
        return LoginSaslMechanism.delegatedExchange(exchange)
            .flatMap(AuthHookSaslMechanism::terminalResponse);
    }

    static ImmutableList<SaslMechanism> withLegacyAuthHooks(ImmutableList<SaslMechanism> mechanisms, List<AuthHook> authHooks,
                                                            List<HookResultHook> hookResultHooks, SMTPSession session) {
        return mechanisms.stream()
            .map(mechanism -> adaptMechanism(mechanism, authHooks, hookResultHooks, session))
            .collect(ImmutableList.toImmutableList());
    }

    private static SaslMechanism adaptMechanism(SaslMechanism mechanism, List<AuthHook> authHooks,
                                                List<HookResultHook> hookResultHooks, SMTPSession session) {
        if (mechanism instanceof LoginSaslMechanism loginSaslMechanism) {
            return new LoginSaslMechanism(new AuthHookSaslMechanism(
                loginSaslMechanism.plainMechanism(), authHooks, hookResultHooks, session));
        }
        if (mechanism.name().equalsIgnoreCase(SaslMechanismNames.PLAIN)) {
            return new AuthHookSaslMechanism(mechanism, authHooks, hookResultHooks, session);
        }
        return mechanism;
    }

    private final SaslMechanism plainMechanism;
    private final ImmutableList<AuthHook> authHooks;
    private final ImmutableList<HookResultHook> hookResultHooks;
    private final SMTPSession session;

    AuthHookSaslMechanism(SaslMechanism plainMechanism, List<AuthHook> authHooks, List<HookResultHook> hookResultHooks, SMTPSession session) {
        this.plainMechanism = plainMechanism;
        this.authHooks = ImmutableList.copyOf(authHooks);
        this.hookResultHooks = ImmutableList.copyOf(hookResultHooks);
        this.session = session;
    }

    @Override
    public String name() {
        return SaslMechanismNames.PLAIN;
    }

    @Override
    public boolean isAvailableOnTransport(boolean channelEncrypted) {
        return plainMechanism.isAvailableOnTransport(channelEncrypted);
    }

    @Override
    public SaslExchange start(SaslInitialRequest request, SaslAuthenticator authenticator) {
        return new Exchange(request.initialResponse(), session);
    }

    final class Exchange implements SaslExchange {
        private final Optional<byte[]> initialResponse;
        private final SMTPSession session;
        private Optional<Response> terminalResponse;

        private Exchange(Optional<byte[]> initialResponse, SMTPSession session) {
            this.initialResponse = initialResponse;
            this.session = session;
            this.terminalResponse = Optional.empty();
        }

        @Override
        public SaslStep firstStep() {
            return initialResponse
                .map(this::authenticate)
                .orElseGet(() -> new SaslStep.Challenge(Optional.empty()));
        }

        @Override
        public SaslStep onResponse(byte[] clientResponse) {
            return authenticate(clientResponse);
        }

        @Override
        public void close() {
        }

        Optional<Response> terminalResponse() {
            return terminalResponse;
        }

        private SaslStep authenticate(byte[] clientResponse) {
            return parse(clientResponse)
                .map(this::authenticate)
                .orElseGet(() -> new SaslStep.Failure(SaslFailure.malformed("Malformed authentication command.")));
        }

        private SaslStep authenticate(PlainCredentials credentials) {
            if (credentials.password().isEmpty()) {
                return delegateWithHooks(credentials);
            }
            return authenticateWithHooks(credentials);
        }

        private SaslStep authenticateWithHooks(PlainCredentials credentials) {
            return authHooks.stream()
                .map(hook -> executeHook(hook, hook2 -> hook2.doAuth(session, credentials.authenticationId(), credentials.password()), credentials))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseGet(() -> invalidCredentials(credentials));
        }

        private SaslStep delegateWithHooks(PlainCredentials credentials) {
            return authHooks.stream()
                .map(hook -> executeHook(hook, hook2 -> hook2.doDelegation(session, credentials.authenticationId()), credentials))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseGet(() -> invalidCredentials(credentials));
        }

        private Optional<SaslStep> executeHook(AuthHook hook, Function<AuthHook, HookResult> operation, PlainCredentials credentials) {
            long start = System.currentTimeMillis();
            HookResult hookResult = operation.apply(hook);
            long executionTime = System.currentTimeMillis() - start;
            HookResult decoratedHookResult = hookResultHooks.stream()
                .reduce(hookResult,
                    (result, hookResultHook) -> hookResultHook.onHookResult(session, result, executionTime, hook),
                    (left, right) -> {
                        throw new UnsupportedOperationException();
                    });

            return toSaslStep(decoratedHookResult, credentials);
        }

        private Optional<SaslStep> toSaslStep(HookResult hookResult, PlainCredentials credentials) {
            if (hookResult == null) {
                return Optional.empty();
            }
            HookReturnCode.Action action = hookResult.getResult().getAction();
            Optional<Response> response = toSmtpResponse(hookResult);
            if (response.isEmpty()) {
                return Optional.empty();
            }
            terminalResponse = response;
            return switch (action) {
                case OK -> {
                    Username authorizedUser = Optional.ofNullable(session.getUsername())
                        .or(() -> credentials.authorizationId())
                        .orElse(credentials.authenticationId());
                    yield Optional.of(new SaslStep.Success(
                        new SaslIdentity(credentials.authenticationId(), authorizedUser),
                        Optional.empty()));
                }
                case DENY -> Optional.of(new SaslStep.Failure(SaslFailure.invalidCredentials(
                    credentials.authenticationId(),
                    credentials.authorizationId(),
                    failureReason(hookResult, "Invalid credentials"))));
                case DENYSOFT, DECLINED, NONE -> Optional.of(new SaslStep.Failure(SaslFailure.serverError(
                    Optional.of(credentials.authenticationId()),
                    credentials.authorizationId(),
                    failureReason(hookResult, "Authentication failed"))));
            };
        }

        private Optional<Response> toSmtpResponse(HookResult hookResult) {
            HookReturnCode returnCode = hookResult.getResult();
            if (!HookReturnCode.Action.ACTIVE_ACTIONS.contains(returnCode.getAction())) {
                return returnCode.isDisconnected() ? Optional.of(Response.DISCONNECT) : Optional.empty();
            }

            String smtpReturnCode = Optional.ofNullable(hookResult.getSmtpRetCode())
                .orElseGet(() -> defaultSmtpReturnCode(returnCode.getAction()));
            String smtpDescription = Optional.ofNullable(hookResult.getSmtpDescription())
                .orElseGet(() -> defaultSmtpDescription(returnCode.getAction()));
            SMTPResponse response = new SMTPResponse(smtpReturnCode, smtpDescription);
            if (returnCode.isDisconnected() && returnCode.getAction() != HookReturnCode.Action.OK) {
                response.setEndSession(true);
            }
            return Optional.of(response);
        }

        private String defaultSmtpReturnCode(HookReturnCode.Action action) {
            return switch (action) {
                case OK -> SMTPRetCode.AUTH_OK;
                case DENY -> SMTPRetCode.AUTH_FAILED;
                case DENYSOFT -> SMTPRetCode.LOCAL_ERROR;
                case DECLINED, NONE -> throw new IllegalArgumentException("No SMTP response for declined AuthHook result");
            };
        }

        private String defaultSmtpDescription(HookReturnCode.Action action) {
            return switch (action) {
                case OK -> "Authentication Successful";
                case DENY -> "Authentication Failed";
                case DENYSOFT -> "Temporary problem. Please try again later";
                case DECLINED, NONE -> throw new IllegalArgumentException("No SMTP response for declined AuthHook result");
            };
        }

        private SaslStep invalidCredentials(PlainCredentials credentials) {
            return new SaslStep.Failure(SaslFailure.invalidCredentials(
                credentials.authenticationId(),
                credentials.authorizationId(),
                "Invalid credentials"));
        }

        private String failureReason(HookResult hookResult, String defaultReason) {
            return Optional.ofNullable(hookResult.getSmtpDescription())
                .orElse(defaultReason);
        }

    }

    private Optional<PlainCredentials> parse(byte[] clientResponse) {
        ImmutableList<String> tokens = Arrays.stream(new String(clientResponse, StandardCharsets.UTF_8).split("\0", -1))
            .collect(ImmutableList.toImmutableList());

        if (tokens.size() == 4 && tokens.get(3).isEmpty()) {
            return credentials(tokens.subList(0, 3));
        }
        return credentials(tokens);
    }

    private Optional<PlainCredentials> credentials(List<String> tokens) {
        try {
            if (tokens.size() == 2) {
                return Optional.of(new PlainCredentials(Optional.empty(), Username.of(tokens.get(0)), tokens.get(1)));
            }
            if (tokens.size() == 3) {
                Optional<Username> authorizationId = Optional.of(tokens.get(0))
                    .filter(value -> !value.isEmpty())
                    .map(Username::of);
                return Optional.of(new PlainCredentials(authorizationId, Username.of(tokens.get(1)), tokens.get(2)));
            }
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private record PlainCredentials(Optional<Username> authorizationId, Username authenticationId, String password) {
    }
}
