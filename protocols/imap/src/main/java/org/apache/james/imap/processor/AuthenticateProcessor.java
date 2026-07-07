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

package org.apache.james.imap.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.AuthenticateRequest;
import org.apache.james.imap.message.request.IRAuthenticateRequest;
import org.apache.james.imap.message.response.AuthenticateResponse;
import org.apache.james.imap.processor.sasl.ImapSaslBridge;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismNames;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.apache.james.protocols.sasl.JamesSaslAuthenticator;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

/**
 * Processor which handles the AUTHENTICATE command. Supported authentication mechanisms are PLAIN, XOAUTH2, and OAUTHBEARER.
 */
public class AuthenticateProcessor extends AbstractAuthProcessor<AuthenticateRequest> implements CapabilityImplementingProcessor {
    public static final String AUTH_PLAIN = "AUTH=PLAIN";
    public static final Capability AUTH_PLAIN_CAPABILITY = Capability.of(AUTH_PLAIN);
    public static final Capability SASL_CAPABILITY = Capability.of("SASL-IR");
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticateProcessor.class);

    private final ImapSaslBridge saslBridge;
    private final JamesSaslAuthenticator jamesSaslAuthenticator;
    private ImmutableList<SaslMechanism> saslMechanisms;

    @Inject
    public AuthenticateProcessor(MailboxManager mailboxManager, StatusResponseFactory factory,
                                 MetricFactory metricFactory, PathConverter.Factory pathConverterFactory,
                                 JamesSaslAuthenticator jamesSaslAuthenticator) {
        super(AuthenticateRequest.class, mailboxManager, factory, metricFactory, pathConverterFactory);
        this.saslBridge = new ImapSaslBridge();
        this.jamesSaslAuthenticator = jamesSaslAuthenticator;
        this.saslMechanisms = ImmutableList.of();
    }

    @Override
    public List<Class<? extends AuthenticateRequest>> acceptableClasses() {
        return ImmutableList.of(AuthenticateRequest.class, IRAuthenticateRequest.class);
    }

    @Override
    protected void processRequest(AuthenticateRequest request, ImapSession session, final Responder responder) {
        Optional<SaslMechanism> mechanism = findMechanism(request.getAuthType());

        if (mechanism.isEmpty()) {
            LOGGER.debug("Unsupported authentication mechanism '{}'", request.getAuthType());
            no(request, responder, HumanReadableText.UNSUPPORTED_AUTHENTICATION_MECHANISM);
            return;
        }

        if (!isAvailable(mechanism.get(), session)) {
            rejectUnavailable(request, responder, mechanism.get());
            return;
        }

        try {
            SaslInitialRequest initialRequest = saslBridge.initialRequest(request.getAuthType(), initialClientResponse(request));
            SaslAuthenticator authenticator = jamesSaslAuthenticator.withExtraAuthorizator(withAdminUsers());
            SaslExchange exchange = mechanism.get().start(initialRequest, authenticator);
            handleFirstStep(exchange, firstStep(exchange), session, request, responder);
        } catch (IllegalArgumentException e) {
            LOGGER.info("Invalid syntax in AUTHENTICATE initial client response", e);
            authFailure(session, request, responder, HumanReadableText.AUTHENTICATION_FAILED, Optional.empty(),
                Optional.empty(), "Malformed authentication command.");
        }
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        List<Capability> caps = saslMechanisms.stream()
            .filter(mechanism -> isAvailable(mechanism, session))
            .map(mechanism -> Capability.of("AUTH=" + mechanism.name()))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        // Support for SASL-IR. See RFC4959
        caps.add(SASL_CAPABILITY);
        return ImmutableList.copyOf(caps);
    }

    @Override
    protected MDCBuilder mdc(AuthenticateRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "AUTHENTICATE")
            .addToContext("authType", request.getAuthType());
    }

    public void configureSaslMechanisms(ImmutableList<SaslMechanism> saslMechanisms) {
        this.saslMechanisms = saslMechanisms;
    }

    private Optional<String> initialClientResponse(AuthenticateRequest request) {
        if (request instanceof IRAuthenticateRequest irAuthenticateRequest) {
            return Optional.of(irAuthenticateRequest.getInitialClientResponse());
        }
        return Optional.empty();
    }

    private SaslStep firstStep(SaslExchange exchange) {
        try {
            return exchange.firstStep();
        } catch (RuntimeException e) {
            saslBridge.close(exchange);
            throw e;
        }
    }

    private Optional<SaslMechanism> findMechanism(String mechanismName) {
        String normalizedName = mechanismName.toUpperCase(Locale.US);
        return saslMechanisms.stream()
            .filter(mechanism -> mechanism.name().toUpperCase(Locale.US).equals(normalizedName))
            .findFirst();
    }

    private boolean isAvailable(SaslMechanism mechanism, ImapSession session) {
        return mechanism.isAvailableOnTransport(session.isTLSActive());
    }

    private void rejectUnavailable(AuthenticateRequest request, Responder responder, SaslMechanism mechanism) {
        LOGGER.warn("{} authentication rejected because it is not allowed over current transport", mechanism.name());
        if (SaslMechanismNames.PLAIN.equalsIgnoreCase(mechanism.name())) {
            no(request, responder, HumanReadableText.DISABLED_LOGIN);
            return;
        }
        no(request, responder, HumanReadableText.UNSUPPORTED_AUTHENTICATION_MECHANISM);
    }

    private void handleFirstStep(SaslExchange exchange, SaslStep step, ImapSession session, AuthenticateRequest request, Responder responder) {
        if (step instanceof SaslStep.Challenge challenge) {
            handleInitialChallenge(exchange, challenge, session, request, responder);
            return;
        }
        if (step instanceof SaslStep.Success success && success.serverData().isPresent()) {
            handleSuccessWithServerData(exchange, success, session, request, responder);
            return;
        }
        handleTerminalStep(exchange, step, session, request, responder);
    }

    private void handleInitialChallenge(SaslExchange exchange, SaslStep.Challenge challenge,
                                        ImapSession session, AuthenticateRequest request, Responder responder) {
        pushContinuationHandlerAndRespond(exchange, challenge, session, request, responder);
    }

    private void pushContinuationHandlerAndRespond(SaslExchange exchange, SaslStep.Challenge challenge,
                                                   ImapSession session, AuthenticateRequest request, Responder responder) {
        pushContinuationHandler(exchange, session, request, responder);
        respondActiveContinuation(exchange, session, () ->
            responder.respond(new AuthenticateResponse(saslBridge.continuation(challenge))));
    }

    private void pushContinuationHandler(SaslExchange exchange, ImapSession session, AuthenticateRequest request, Responder responder) {
        try {
            session.pushLineHandler((requestSession, data) -> Mono.fromRunnable(() -> handleContinuationLine(exchange, requestSession, request, responder, data))
                .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
                .then());
        } catch (RuntimeException e) {
            saslBridge.close(exchange);
            throw e;
        }
    }

    private void handleContinuationLine(SaslExchange exchange, ImapSession session, AuthenticateRequest request, Responder responder, byte[] data) {
        if (isAbort(exchange, session, data)) {
            abortActiveContinuation(exchange, session);
            no(request, responder, HumanReadableText.AUTHENTICATION_FAILED);
            responder.flush();
            return;
        }

        nextStep(exchange, session, request, responder, data)
            .ifPresent(step -> handleContinuationStep(exchange, step, session, request, responder));
    }

    private Optional<SaslStep> nextStep(SaslExchange exchange, ImapSession session, AuthenticateRequest request, Responder responder, byte[] data) {
        try {
            return Optional.of(saslBridge.onClientResponse(exchange, data));
        } catch (IllegalArgumentException e) {
            LOGGER.info("Invalid syntax in AUTHENTICATE client response", e);
            closeActiveContinuation(exchange, session);
            authFailure(session, request, responder, HumanReadableText.AUTHENTICATION_FAILED, Optional.empty(),
                Optional.empty(), "Malformed authentication command.");
            responder.flush();
            return Optional.empty();
        } catch (RuntimeException e) {
            closeActiveContinuation(exchange, session);
            throw e;
        }
    }

    private void handleContinuationStep(SaslExchange exchange, SaslStep step, ImapSession session, AuthenticateRequest request, Responder responder) {
        if (step instanceof SaslStep.Challenge challenge) {
            try {
                responder.respond(new AuthenticateResponse(saslBridge.continuation(challenge)));
                responder.flush();
            } catch (RuntimeException e) {
                closeActiveContinuation(exchange, session);
                throw e;
            }
            return;
        }

        popActiveContinuation(exchange, session);
        if (step instanceof SaslStep.Success success && success.serverData().isPresent()) {
            handleSuccessWithServerData(exchange, success, session, request, responder);
            return;
        }

        handleTerminalStep(exchange, step, session, request, responder);
        responder.flush();
    }

    private void respondActiveContinuation(SaslExchange exchange, ImapSession session, Runnable runnable) {
        try {
            runnable.run();
        } catch (RuntimeException e) {
            closeActiveContinuation(exchange, session);
            throw e;
        }
    }

    private boolean isAbort(SaslExchange exchange, ImapSession session, byte[] data) {
        try {
            return saslBridge.isAbort(data);
        } catch (RuntimeException e) {
            closeActiveContinuation(exchange, session);
            throw e;
        }
    }

    private boolean isEmptyClientResponse(SaslExchange exchange, ImapSession session, byte[] data) {
        try {
            return saslBridge.isEmptyClientResponse(data);
        } catch (RuntimeException e) {
            closeActiveContinuation(exchange, session);
            throw e;
        }
    }

    private void closeActiveContinuation(SaslExchange exchange, ImapSession session) {
        try {
            session.popLineHandler();
        } finally {
            saslBridge.close(exchange);
        }
    }

    private void abortActiveContinuation(SaslExchange exchange, ImapSession session) {
        try {
            session.popLineHandler();
        } finally {
            saslBridge.abort(exchange);
        }
    }

    private void popActiveContinuation(SaslExchange exchange, ImapSession session) {
        try {
            session.popLineHandler();
        } catch (RuntimeException e) {
            saslBridge.close(exchange);
            throw e;
        }
    }

    private void handleSuccessWithServerData(SaslExchange exchange, SaslStep.Success success, ImapSession session,
                                             AuthenticateRequest request, Responder responder) {
        pushSuccessDataAcknowledgementHandler(exchange, success, session, request, responder);
        respondActiveContinuation(exchange, session, () -> {
            responder.respond(new AuthenticateResponse(saslBridge.successData(success)));
            responder.flush();
        });
    }

    private void pushSuccessDataAcknowledgementHandler(SaslExchange exchange, SaslStep.Success success, ImapSession session,
                                                       AuthenticateRequest request, Responder responder) {
        try {
            session.pushLineHandler((requestSession, data) -> Mono.fromRunnable(() -> handleSuccessDataAcknowledgement(exchange, success, requestSession, request, responder, data))
                .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
                .then());
        } catch (RuntimeException e) {
            saslBridge.close(exchange);
            throw e;
        }
    }

    private void handleSuccessDataAcknowledgement(SaslExchange exchange, SaslStep.Success success, ImapSession session,
                                                  AuthenticateRequest request, Responder responder, byte[] data) {
        if (isAbort(exchange, session, data)) {
            abortActiveContinuation(exchange, session);
            no(request, responder, HumanReadableText.AUTHENTICATION_FAILED);
            responder.flush();
            return;
        }
        if (!isEmptyClientResponse(exchange, session, data)) {
            closeActiveContinuation(exchange, session);
            authFailure(session, request, responder, HumanReadableText.AUTHENTICATION_FAILED, Optional.empty(),
                Optional.empty(), "Malformed authentication command.");
            responder.flush();
            return;
        }

        popActiveContinuation(exchange, session);
        handleTerminalStep(exchange, success, session, request, responder);
        responder.flush();
    }

    private void handleTerminalStep(SaslExchange exchange, SaslStep step, ImapSession session, AuthenticateRequest request, Responder responder) {
        try {
            handleSaslStep(step, session, request, responder, successLog(request));
        } finally {
            saslBridge.close(exchange);
        }
    }

    private String successLog(AuthenticateRequest request) {
        String authType = request.getAuthType().toUpperCase(Locale.US);
        return authType + " authentication succeeded.";
    }
}
