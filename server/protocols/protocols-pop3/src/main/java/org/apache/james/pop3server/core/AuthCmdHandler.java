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

package org.apache.james.pop3server.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.pop3server.mailbox.MailboxAdapterFactory;
import org.apache.james.protocols.api.ProtocolSession.AttachmentKey;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.DisconnectHandler;
import org.apache.james.protocols.api.handler.LineHandler;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslCodec;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslFailure;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.apache.james.protocols.lib.POP3BeforeSMTPHelper;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.core.AbstractPOP3CommandHandler;
import org.apache.james.protocols.pop3.core.CapaCapability;
import org.apache.james.protocols.pop3.core.MDCConstants;
import org.apache.james.protocols.pop3.mailbox.Mailbox;
import org.apache.james.protocols.pop3.mailbox.MessageMetaData;
import org.apache.james.protocols.sasl.JamesSaslAuthenticator;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class AuthCmdHandler extends AbstractPOP3CommandHandler implements CapaCapability, DisconnectHandler<POP3Session> {
    private record AuthRequest(String mechanismName, Optional<String> initialResponse) {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthCmdHandler.class);
    private static final boolean CONTINUATION_ACTIVE = true;
    private static final boolean CONTINUATION_INACTIVE = false;
    private static final Collection<String> COMMANDS = ImmutableSet.of("AUTH");
    private static final Response AUTH_FAILED = new POP3Response(POP3Response.ERR_RESPONSE, "Authentication failed.").immutable();
    private static final Response AUTH_ABORTED = new POP3Response(POP3Response.ERR_RESPONSE, "Authentication aborted.").immutable();
    public static final Response AUTH_REQUIRES_TLS = new POP3Response(POP3Response.ERR_RESPONSE, "Authentication requires TLS.").immutable();
    private static final Response INVALID_AUTH_REQUEST = new POP3Response(POP3Response.ERR_RESPONSE, "Invalid AUTH request.").immutable();
    private static final Response UNSUPPORTED_MECHANISM = new POP3Response(POP3Response.ERR_RESPONSE, "Unsupported authentication mechanism.").immutable();
    private static final Response UNEXPECTED_ERROR = new POP3Response(POP3Response.ERR_RESPONSE, "Unexpected error accessing mailbox").immutable();
    private static final AttachmentKey<SaslExchange> ACTIVE_SASL_EXCHANGE = AttachmentKey.of("ACTIVE_SASL_EXCHANGE", SaslExchange.class);

    private final Pop3MailboxProvider mailboxProvider;
    private final SaslAuthenticator saslAuthenticator;
    private final MetricFactory metricFactory;
    private ImmutableList<SaslMechanism> saslMechanisms = ImmutableList.of();

    @Inject
    public AuthCmdHandler(@Named("mailboxmanager") MailboxManager mailboxManager, MailboxAdapterFactory mailboxAdapterFactory, MetricFactory metricFactory) {
        this.mailboxProvider = new Pop3MailboxProvider(mailboxManager, mailboxAdapterFactory);
        this.saslAuthenticator = JamesSaslAuthenticator.jamesSaslAuthenticator(mailboxManager);
        this.metricFactory = metricFactory;
    }

    public void configureSaslMechanisms(ImmutableList<SaslMechanism> saslMechanisms) {
        this.saslMechanisms = saslMechanisms;
    }

    @Override
    public Response onCommand(POP3Session session, Request request) {
        return metricFactory.decorateSupplierWithTimerMetric("pop3-auth", () ->
            MDCBuilder.withMdc(
                MDCBuilder.create()
                    .addToContext(MDCBuilder.ACTION, "AUTH")
                    .addToContext(MDCConstants.withSession(session)),
                () -> authenticate(session, request)));
    }

    private Response authenticate(POP3Session session, Request request) {
        if (session.getHandlerState() == POP3Session.TRANSACTION) {
            return AUTH_FAILED;
        }
        try {
            AuthRequest authRequest = parse(request);
            return findMechanism(authRequest.mechanismName())
                .map(mechanism -> authenticate(session, authRequest, mechanism))
                .orElse(UNSUPPORTED_MECHANISM);
        } catch (IllegalArgumentException e) {
            LOGGER.info("Invalid POP3 AUTH request: {}", e.getMessage());
            return INVALID_AUTH_REQUEST;
        }
    }

    private AuthRequest parse(Request request) {
        String argument = request.getArgument();
        if (argument == null) {
            throw new IllegalArgumentException("Invalid AUTH command");
        }

        String[] arguments = argument.trim().split(" +", 2);
        if (arguments.length == 0 || arguments[0].isBlank()) {
            throw new IllegalArgumentException("Missing SASL mechanism");
        }
        return new AuthRequest(arguments[0].toUpperCase(Locale.US), arguments.length == 2 ? Optional.of(arguments[1]) : Optional.empty());
    }

    private Optional<SaslMechanism> findMechanism(String mechanismName) {
        return saslMechanisms.stream()
            .filter(mechanism -> mechanism.name().equalsIgnoreCase(mechanismName))
            .findFirst();
    }

    private Response authenticate(POP3Session session, AuthRequest request, SaslMechanism mechanism) {
        if (!mechanism.isAvailableOnTransport(session.isTLSStarted())) {
            if (!session.isTLSStarted() && mechanism.isAvailableOnTransport(true)) {
                return AUTH_REQUIRES_TLS;
            }
            return UNSUPPORTED_MECHANISM;
        }
        return start(session, mechanism, SaslCodec.initialRequest(request.mechanismName(), request.initialResponse()));
    }

    private Response start(POP3Session session, SaslMechanism mechanism, SaslInitialRequest request) {
        SaslExchange exchange = mechanism.start(request, saslAuthenticator);
        registerExchange(session, exchange);
        return handleStep(session, exchange, firstStep(session, exchange), CONTINUATION_INACTIVE);
    }

    private void registerExchange(POP3Session session, SaslExchange exchange) {
        try {
            session.setAttachment(ACTIVE_SASL_EXCHANGE, exchange, State.Connection)
                .ifPresent(SaslExchange::close);
        } catch (RuntimeException e) {
            exchange.close();
            throw e;
        }
    }

    private SaslStep firstStep(POP3Session session, SaslExchange exchange) {
        try {
            return exchange.firstStep();
        } catch (RuntimeException e) {
            closeExchange(session, exchange);
            throw e;
        }
    }

    private Response handleStep(POP3Session session, SaslExchange exchange, SaslStep step, boolean continuationActive) {
        return switch (step) {
            case SaslStep.Challenge challenge -> {
                if (!continuationActive) {
                    pushContinuationHandler(session, exchange);
                }
                yield respondActiveContinuation(session, exchange, () -> challengeResponse(challenge.payload()));
            }
            case SaslStep.Success success -> {
                if (continuationActive) {
                    popActiveContinuation(session, exchange);
                }
                yield handleSuccess(session, exchange, success);
            }
            case SaslStep.Failure failure -> {
                if (continuationActive) {
                    popActiveContinuation(session, exchange);
                }
                yield handleFailure(session, exchange, failure.failure());
            }
        };
    }

    private void pushContinuationHandler(POP3Session session, SaslExchange exchange) {
        try {
            session.pushLineHandler(continuationHandler(exchange));
        } catch (RuntimeException e) {
            closeExchange(session, exchange);
            throw e;
        }
    }

    private LineHandler<POP3Session> continuationHandler(SaslExchange exchange) {
        return (session, line) -> {
            if (isAbort(session, exchange, line)) {
                abortActiveContinuation(session, exchange);
                return AUTH_ABORTED;
            }
            return nextStep(session, exchange, line)
                .map(step -> handleStep(session, exchange, step, CONTINUATION_ACTIVE))
                .orElse(INVALID_AUTH_REQUEST);
        };
    }

    private Optional<SaslStep> nextStep(POP3Session session, SaslExchange exchange, byte[] line) {
        try {
            return Optional.of(exchange.onResponse(SaslCodec.decodeClientResponse(line)));
        } catch (IllegalArgumentException e) {
            closeActiveContinuation(session, exchange);
            return Optional.empty();
        } catch (RuntimeException e) {
            closeActiveContinuation(session, exchange);
            throw e;
        }
    }

    private Response handleSuccess(POP3Session session, SaslExchange exchange, SaslStep.Success success) {
        if (success.serverData().isPresent()) {
            pushSuccessDataAcknowledgementHandler(session, exchange, success);
            return respondActiveContinuation(session, exchange, () -> challengeResponse(success.serverData()));
        }
        return completeAuthentication(session, exchange, success);
    }

    private void pushSuccessDataAcknowledgementHandler(POP3Session session, SaslExchange exchange, SaslStep.Success success) {
        try {
            session.pushLineHandler(successDataAcknowledgementHandler(exchange, success));
        } catch (RuntimeException e) {
            closeExchange(session, exchange);
            throw e;
        }
    }

    private LineHandler<POP3Session> successDataAcknowledgementHandler(SaslExchange exchange, SaslStep.Success success) {
        return (session, line) -> {
            if (isAbort(session, exchange, line)) {
                abortActiveContinuation(session, exchange);
                return AUTH_ABORTED;
            }
            if (!isEmptyClientResponse(session, exchange, line)) {
                closeActiveContinuation(session, exchange);
                return INVALID_AUTH_REQUEST;
            }
            popActiveContinuation(session, exchange);
            return completeAuthentication(session, exchange, success);
        };
    }

    private boolean isAbort(POP3Session session, SaslExchange exchange, byte[] line) {
        try {
            return SaslCodec.isAbort(line);
        } catch (RuntimeException e) {
            closeActiveContinuation(session, exchange);
            throw e;
        }
    }

    private boolean isEmptyClientResponse(POP3Session session, SaslExchange exchange, byte[] line) {
        try {
            return SaslCodec.isEmptyClientResponse(line);
        } catch (RuntimeException e) {
            closeActiveContinuation(session, exchange);
            throw e;
        }
    }

    private Response respondActiveContinuation(POP3Session session, SaslExchange exchange, Supplier<Response> responseSupplier) {
        try {
            return responseSupplier.get();
        } catch (RuntimeException e) {
            closeActiveContinuation(session, exchange);
            throw e;
        }
    }

    private Response challengeResponse(Optional<byte[]> payload) {
        return new POP3Response("+", SaslCodec.encode(payload)).immutable();
    }

    private void popActiveContinuation(POP3Session session, SaslExchange exchange) {
        try {
            session.popLineHandler();
        } catch (RuntimeException e) {
            closeExchange(session, exchange);
            throw e;
        }
    }

    private void closeActiveContinuation(POP3Session session, SaslExchange exchange) {
        try {
            session.popLineHandler();
        } finally {
            closeExchange(session, exchange);
        }
    }

    private void abortActiveContinuation(POP3Session session, SaslExchange exchange) {
        try {
            session.popLineHandler();
        } finally {
            abortExchange(session, exchange);
        }
    }

    private Response completeAuthentication(POP3Session session, SaslExchange exchange, SaslStep.Success success) {
        Username authorizationId = success.identity().authorizationId();
        try {
            Mailbox mailbox = mailboxProvider.open(session, authorizationId);
            session.setUsername(authorizationId);
            Response response = completeMailboxAuthentication(session, mailbox);
            POP3BeforeSMTPHelper.addIPAddress(session.getRemoteAddress().getAddress().getHostAddress());
            return response;
        } catch (IOException e) {
            LOGGER.error("Unable to complete POP3 SASL authentication for {}", authorizationId, e);
            return UNEXPECTED_ERROR;
        } finally {
            closeExchange(session, exchange);
        }
    }

    private Response completeMailboxAuthentication(POP3Session session, Mailbox mailbox) {
        session.setUserMailbox(mailbox);
        try {
            List<MessageMetaData> messages = mailbox.getMessages();
            session.setAttachment(POP3Session.UID_LIST, messages, State.Transaction);
            session.setAttachment(POP3Session.DELETED_UID_LIST, new ArrayList<>(), State.Transaction);
        } catch (IOException e) {
            LOGGER.error("Unable to initialize POP3 mailbox state", e);
        }
        session.setHandlerState(POP3Session.TRANSACTION);
        return new POP3Response(POP3Response.OK_RESPONSE, "Welcome " + session.getUsername().asString());
    }

    private Response handleFailure(POP3Session session, SaslExchange exchange, SaslFailure failure) {
        try {
            return switch (failure.type()) {
                case MALFORMED -> INVALID_AUTH_REQUEST;
                case INVALID_CREDENTIALS, AUTHENTICATION_FAILED, USER_DOES_NOT_EXIST, DELEGATION_FORBIDDEN -> AUTH_FAILED;
                case SERVER_ERROR -> {
                    failure.cause()
                        .ifPresentOrElse(
                            cause -> LOGGER.error("POP3 SASL authentication failed", cause),
                            () -> LOGGER.error("POP3 SASL authentication failed: {}", failure.reason()));
                    yield UNEXPECTED_ERROR;
                }
            };
        } finally {
            closeExchange(session, exchange);
        }
    }

    private void closeExchange(POP3Session session, SaslExchange exchange) {
        session.removeAttachment(ACTIVE_SASL_EXCHANGE, State.Connection);
        exchange.close();
    }

    private void abortExchange(POP3Session session, SaslExchange exchange) {
        session.removeAttachment(ACTIVE_SASL_EXCHANGE, State.Connection);
        exchange.abort();
    }

    @Override
    public void onDisconnect(POP3Session session) {
        if (session != null) {
            session.removeAttachment(ACTIVE_SASL_EXCHANGE, State.Connection)
                .ifPresent(SaslExchange::close);
        }
    }

    @Override
    public Set<String> getImplementedCapabilities(POP3Session session) {
        ImmutableList<String> mechanisms = saslMechanisms.stream()
            .filter(mechanism -> mechanism.isAvailableOnTransport(session.isTLSStarted()))
            .map(SaslMechanism::name)
            .distinct()
            .collect(ImmutableList.toImmutableList());
        if (mechanisms.isEmpty()) {
            return ImmutableSet.of();
        }
        return ImmutableSet.of("SASL " + Joiner.on(' ').join(mechanisms));
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }
}
