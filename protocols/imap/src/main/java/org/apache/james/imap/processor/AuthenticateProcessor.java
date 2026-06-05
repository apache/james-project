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
import org.apache.james.imap.api.message.request.ImapRequest;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.AuthenticateRequest;
import org.apache.james.imap.message.request.IRAuthenticateRequest;
import org.apache.james.imap.message.response.AuthenticateResponse;
import org.apache.james.imap.processor.sasl.ImapBearerTokenSaslAuthenticationServiceFactory;
import org.apache.james.imap.processor.sasl.ImapPasswordSaslAuthenticationServiceFactory;
import org.apache.james.imap.processor.sasl.ImapSaslBridge;
import org.apache.james.imap.processor.sasl.ImapSaslSessionContext;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.sasl.OauthBearerSaslMechanism;
import org.apache.james.protocols.api.sasl.PlainSaslMechanism;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismRegistry;
import org.apache.james.protocols.api.sasl.SaslProtocol;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.apache.james.protocols.api.sasl.XOauth2SaslMechanism;
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
    private static final ImmutableList<SaslMechanism> DEFAULT_SASL_MECHANISMS = ImmutableList.of(
        new PlainSaslMechanism(),
        new OauthBearerSaslMechanism(),
        new XOauth2SaslMechanism());

    private final ImapSaslBridge saslBridge;
    private SaslMechanismRegistry saslMechanisms;

    @Inject
    public AuthenticateProcessor(MailboxManager mailboxManager, StatusResponseFactory factory,
                                 MetricFactory metricFactory, PathConverter.Factory pathConverterFactory) {
        super(AuthenticateRequest.class, mailboxManager, factory, metricFactory, pathConverterFactory);
        this.saslBridge = new ImapSaslBridge();
        this.saslMechanisms = defaultSaslMechanisms();
    }

    @Override
    public List<Class<? extends AuthenticateRequest>> acceptableClasses() {
        return ImmutableList.of(AuthenticateRequest.class, IRAuthenticateRequest.class);
    }

    @Override
    protected void processRequest(AuthenticateRequest request, ImapSession session, final Responder responder) {
        ImapSaslSessionContext context = buildContext(session);
        Optional<SaslMechanism> mechanism = saslMechanisms.find(request.getAuthType(), SaslProtocol.IMAP);

        if (mechanism.isEmpty()) {
            LOGGER.debug("Unsupported authentication mechanism '{}'", request.getAuthType());
            no(request, responder, HumanReadableText.UNSUPPORTED_AUTHENTICATION_MECHANISM);
            return;
        }

        if (!mechanism.get().isAvailable(context)) {
            rejectUnavailable(request, responder, mechanism.get());
            return;
        }

        try {
            SaslInitialRequest initialRequest = saslBridge.initialRequest(request.getAuthType(), initialClientResponse(request));
            SaslExchange exchange = mechanism.get().start(initialRequest, context);
            handleFirstStep(exchange, exchange.firstStep(), context, session, request, responder);
        } catch (IllegalArgumentException e) {
            LOGGER.info("Invalid syntax in AUTHENTICATE initial client response", e);
            authFailure(session, request, responder, HumanReadableText.AUTHENTICATION_FAILED, Optional.empty(),
                Optional.empty(), "Malformed authentication command.");
        }
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        ImapSaslSessionContext context = buildContext(session);
        List<Capability> caps = saslMechanisms.availableFor(SaslProtocol.IMAP, context)
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

    public void configureSaslMechanisms(SaslMechanismRegistry saslMechanisms) {
        this.saslMechanisms = saslMechanisms;
    }

    private Optional<String> initialClientResponse(AuthenticateRequest request) {
        if (request instanceof IRAuthenticateRequest irAuthenticateRequest) {
            return Optional.of(irAuthenticateRequest.getInitialClientResponse());
        }
        return Optional.empty();
    }

    private ImapSaslSessionContext buildContext(ImapSession session) {
        ImapSaslSessionContext context = new ImapSaslSessionContext(session, withAdminUsers());
        saslMechanisms.initialize(SaslProtocol.IMAP, context);
        return context;
    }

    private SaslMechanismRegistry defaultSaslMechanisms() {
        return new SaslMechanismRegistry(DEFAULT_SASL_MECHANISMS,
            ImmutableList.of(
                new ImapPasswordSaslAuthenticationServiceFactory(getMailboxManager()),
                new ImapBearerTokenSaslAuthenticationServiceFactory(getMailboxManager())));
    }

    private void rejectUnavailable(AuthenticateRequest request, Responder responder, SaslMechanism mechanism) {
        if (PlainSaslMechanism.NAME.equals(mechanism.name())) {
            LOGGER.warn("Plain authentication rejected because it is disabled or not allowed over insecure channel");
            no(request, responder, HumanReadableText.DISABLED_LOGIN);
        } else {
            LOGGER.warn("{} authentication rejected because it is disabled", mechanism.name());
            no(request, responder, HumanReadableText.UNSUPPORTED_AUTHENTICATION_MECHANISM);
        }
    }

    private void handleFirstStep(SaslExchange exchange, SaslStep step, ImapSaslSessionContext context,
                                 ImapSession session, AuthenticateRequest request, Responder responder) {
        if (step instanceof SaslStep.Challenge challenge) {
            session.executeSafely(() -> {
                responder.respond(new AuthenticateResponse(saslBridge.continuation(challenge)));
                responder.flush();
                session.pushLineHandler((requestSession, data) -> Mono.fromRunnable(() -> handleContinuationLine(exchange, context, requestSession, request, responder, data))
                    .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
                    .then());
            });
            return;
        }
        handleTerminalStep(exchange, step, context, session, request, responder);
    }

    private void handleContinuationLine(SaslExchange exchange, ImapSaslSessionContext context,
                                        ImapSession session, AuthenticateRequest request, Responder responder, byte[] data) {
        if (saslBridge.isAbort(data)) {
            saslBridge.abort(exchange);
            session.popLineHandler();
            no(request, responder, HumanReadableText.AUTHENTICATION_FAILED);
            responder.flush();
            return;
        }

        try {
            SaslStep step = saslBridge.onClientResponse(exchange, data);
            if (step instanceof SaslStep.Challenge challenge) {
                responder.respond(new AuthenticateResponse(saslBridge.continuation(challenge)));
                responder.flush();
            } else {
                session.popLineHandler();
                handleTerminalStep(exchange, step, context, session, request, responder);
                responder.flush();
            }
        } catch (IllegalArgumentException e) {
            LOGGER.info("Invalid syntax in AUTHENTICATE client response", e);
            session.popLineHandler();
            saslBridge.close(exchange);
            authFailure(session, request, responder, HumanReadableText.AUTHENTICATION_FAILED, Optional.empty(),
                Optional.empty(), "Malformed authentication command.");
            responder.flush();
        }
    }

    private void handleTerminalStep(SaslExchange exchange, SaslStep step, ImapSaslSessionContext context,
                                    ImapSession session, AuthenticateRequest request, Responder responder) {
        if (step instanceof SaslStep.Success success) {
            handleSuccess(context, session, request, responder, success.identity());
        } else if (step instanceof SaslStep.Failure failure) {
            handleFailure(context, session, request, responder, failure.reason());
        }
        saslBridge.close(exchange);
    }

    private void handleSuccess(ImapSaslSessionContext context, ImapSession session, AuthenticateRequest request, Responder responder, SaslIdentity identity) {
        context.mailboxSession()
            .ifPresentOrElse(mailboxSession -> authSuccess(session, mailboxSession, request, responder, successLog(request, identity)),
                () -> handleMissingMailboxSession(session, request, responder, identity));
    }

    private void handleMissingMailboxSession(ImapSession session, AuthenticateRequest request, Responder responder, SaslIdentity identity) {
        LOGGER.error("SASL mechanism {} returned Success without creating a mailbox session for authenticationId={} authorizationId={}",
            request.getAuthType(), identity.authenticationId(), identity.authorizationId());
        authFailure(session, request, responder, HumanReadableText.AUTHENTICATION_FAILED, Optional.of(identity.authenticationId()),
            Optional.of(identity.authorizationId()), "Authentication failed.");
    }

    private String successLog(AuthenticateRequest request, SaslIdentity identity) {
        String authType = request.getAuthType().toUpperCase(Locale.US);
        if (!identity.authenticationId().equals(identity.authorizationId())) {
            return "Authentication with delegation succeeded using " + authType + ".";
        }
        return authType + " authentication succeeded.";
    }

    private void handleFailure(ImapSaslSessionContext context, ImapSession session, ImapRequest request, Responder responder, String reason) {
        if (context.hasProcessingFailure()) {
            no(request, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
            return;
        }
        context.failureDetails()
            .ifPresentOrElse(failure -> authFailure(session, request, responder, failure.text(), failure.username(), failure.assumedUser(), failure.reason()),
                () -> authFailure(session, request, responder, HumanReadableText.AUTHENTICATION_FAILED, Optional.empty(), Optional.empty(), reason));
    }
}
