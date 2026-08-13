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

package org.apache.james.managesieve.transcode;

import java.util.Optional;

import org.apache.james.managesieve.api.Session;
import org.apache.james.managesieve.api.Session.ActiveSaslExchange;
import org.apache.james.managesieve.api.SyntaxException;
import org.apache.james.managesieve.sasl.ManageSieveSaslCodec;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslFailure;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

class ManageSieveSaslProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ManageSieveSaslProcessor.class);

    private final ImmutableList<SaslMechanism> saslMechanisms;
    private final SaslAuthenticator saslAuthenticator;

    ManageSieveSaslProcessor(ImmutableList<SaslMechanism> saslMechanisms, SaslAuthenticator saslAuthenticator) {
        this.saslMechanisms = saslMechanisms;
        this.saslAuthenticator = saslAuthenticator;
    }

    String startAuthentication(Session session, String arguments) {
        if (session.isAuthenticated()) {
            return "NO \"already authenticated\"";
        }
        try {
            ManageSieveSaslCodec.InitialRequest request = ManageSieveSaslCodec.parseInitialRequest(arguments);
            Optional<SaslMechanism> mechanism = findMechanism(request.mechanismName());
            if (mechanism.isEmpty()) {
                return "NO \"Unknown SASL mechanism\"";
            }
            SaslMechanism selectedMechanism = mechanism.get();
            if (!selectedMechanism.isAvailableOnTransport(session.isSslEnabled())) {
                if (!session.isSslEnabled() && selectedMechanism.isAvailableOnTransport(true)) {
                    return "NO (ENCRYPT-NEEDED) \"Authentication requires an encrypted channel\"";
                }
                return "NO \"Unknown SASL mechanism\"";
            }
            return startExchange(session, request, selectedMechanism);
        } catch (NotEnoughDataException e) {
            // RFC 5804 section 2.1 permits an initial SASL response as a literal spanning multiple network frames.
            throw e;
        } catch (SyntaxException e) {
            resetAuthentication(session);
            return syntaxFailure(e);
        } catch (RuntimeException e) {
            closeAndReset(session);
            return unexpectedAuthenticationFailure(e);
        }
    }

    String handleContinuation(Session session, String suppliedData) {
        ActiveSaslExchange active = session.getActiveSaslExchange()
            .orElseThrow(() -> new IllegalStateException("Missing active SASL exchange"));
        if (ManageSieveSaslCodec.isAbort(suppliedData)) {
            try {
                return "NO \"Authentication failed with: authentication aborted by client\"";
            } finally {
                closeAndReset(session);
            }
        }
        try {
            byte[] clientResponse = parseClientResponse(session, active, suppliedData);
            return handleSaslStep(session, onResponse(session, active.exchange(), clientResponse));
        } catch (NotEnoughDataException e) {
            // Keep both the channel cumulation and the active exchange until the complete literal arrives.
            throw e;
        } catch (SyntaxException e) {
            closeAndReset(session);
            return syntaxFailure(e);
        } catch (RuntimeException e) {
            closeAndReset(session);
            return unexpectedAuthenticationFailure(e);
        }
    }

    void close(Session session) {
        try {
            closeActiveSaslExchange(session);
        } finally {
            resetAuthentication(session);
        }
    }

    private String startExchange(Session session, ManageSieveSaslCodec.InitialRequest request, SaslMechanism mechanism) {
        ActiveSaslExchange active = new ActiveSaslExchange(request.mechanismName(), mechanism.start(request.saslInitialRequest(), saslAuthenticator));
        registerActiveSaslExchange(session, active);
        return handleSaslStep(session, firstStep(session, active.exchange()));
    }

    private void registerActiveSaslExchange(Session session, ActiveSaslExchange active) {
        if (session.getActiveSaslExchange().isPresent()) {
            active.exchange().close();
            throw new IllegalStateException("A SASL exchange is already active");
        }
        try {
            session.setActiveSaslExchange(active);
        } catch (RuntimeException e) {
            active.exchange().close();
            throw e;
        }
    }

    private SaslStep firstStep(Session session, SaslExchange exchange) {
        try {
            return exchange.firstStep();
        } catch (RuntimeException e) {
            closeAndReset(session);
            throw e;
        }
    }

    private byte[] parseClientResponse(Session session, ActiveSaslExchange active, String suppliedData) throws SyntaxException {
        try {
            return ManageSieveSaslCodec.parseClientResponse(active.mechanismName(), suppliedData);
        } catch (NotEnoughDataException | SyntaxException e) {
            throw e;
        } catch (RuntimeException e) {
            closeAndReset(session);
            throw e;
        }
    }

    private SaslStep onResponse(Session session, SaslExchange exchange, byte[] clientResponse) {
        try {
            return exchange.onResponse(clientResponse);
        } catch (RuntimeException e) {
            closeAndReset(session);
            throw e;
        }
    }

    private String handleSaslStep(Session session, SaslStep step) {
        return switch (step) {
            case SaslStep.Challenge challenge -> handleChallenge(session, challenge);
            case SaslStep.Success success -> handleSuccess(session, success);
            case SaslStep.Failure failure -> handleFailure(session, failure);
        };
    }

    private String handleChallenge(Session session, SaslStep.Challenge challenge) {
        try {
            String response = ManageSieveSaslCodec.challenge(challenge);
            session.setState(Session.State.AUTHENTICATION_IN_PROGRESS);
            return response;
        } catch (RuntimeException e) {
            closeAndReset(session);
            throw e;
        }
    }

    private String handleSuccess(Session session, SaslStep.Success success) {
        try {
            String response = ManageSieveSaslCodec.success(success);
            session.setUser(success.identity().authorizationId());
            session.setState(Session.State.AUTHENTICATED);
            return response;
        } catch (RuntimeException e) {
            resetAuthentication(session);
            throw e;
        } finally {
            closeActiveSaslExchange(session);
        }
    }

    private String handleFailure(Session session, SaslStep.Failure failure) {
        try {
            return authenticationFailure(failure.failure());
        } finally {
            closeAndReset(session);
        }
    }

    private Optional<SaslMechanism> findMechanism(String mechanismName) {
        return saslMechanisms.stream()
            .filter(mechanism -> mechanism.name().equalsIgnoreCase(mechanismName))
            .findFirst();
    }

    private String authenticationFailure(SaslFailure failure) {
        if (failure.type() == SaslFailure.Type.SERVER_ERROR) {
            failure.cause().ifPresentOrElse(
                cause -> LOGGER.error("ManageSieve SASL authentication failed", cause),
                () -> LOGGER.error("ManageSieve SASL authentication failed: {}", failure.reason()));
        }
        if (failure.type() == SaslFailure.Type.MALFORMED) {
            return "NO \"ManageSieve syntax is incorrect: authentication data is malformed\"";
        }
        if (failure.type() == SaslFailure.Type.INVALID_CREDENTIALS) {
            return "NO \"Authentication failed with: Verification of credentials failed\"";
        }
        return "NO \"authentication failed\"";
    }

    private String unexpectedAuthenticationFailure(RuntimeException exception) {
        LOGGER.error("ManageSieve SASL authentication failed", exception);
        return "NO \"authentication failed\"";
    }

    private String syntaxFailure(SyntaxException e) {
        return "NO \"ManageSieve syntax is incorrect: " + e.getMessage() + "\"";
    }

    private void resetAuthentication(Session session) {
        session.setState(Session.State.UNAUTHENTICATED);
        session.setUser(null);
    }

    private void closeAndReset(Session session) {
        try {
            closeActiveSaslExchange(session);
        } finally {
            resetAuthentication(session);
        }
    }

    private void closeActiveSaslExchange(Session session) {
        session.clearActiveSaslExchange()
            .ifPresent(active -> active.exchange().close());
    }
}
