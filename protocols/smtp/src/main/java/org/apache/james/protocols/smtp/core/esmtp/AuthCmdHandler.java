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
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.core.Username;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandHandler;
import org.apache.james.protocols.api.handler.ExtensibleHandler;
import org.apache.james.protocols.api.handler.LineHandler;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslFailure;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismNames;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.AuthHook;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookResultHook;
import org.apache.james.protocols.smtp.hook.MailParametersHook;
import org.apache.james.protocols.smtp.hook.SaslAuthResultHook;
import org.apache.james.util.AuditTrail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;


/**
 * handles AUTH command
 *
 * Authentication is delegated to configured SASL mechanisms.
 */
public class AuthCmdHandler
    implements CommandHandler<SMTPSession>, EhloExtension, ExtensibleHandler, MailParametersHook {
    private static final Collection<String> COMMANDS = ImmutableSet.of("AUTH");
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthCmdHandler.class);
    private static final Logger AUTHENTICATION_DEDICATED_LOGGER = LoggerFactory.getLogger("org.apache.james.protocols.smtp.AUTHENTICATION");
    private static final String[] MAIL_PARAMS = { "AUTH" };
    private static final String AUTH_TYPES_DELIMITER = " ";

    private static final Response AUTH_ABORTED = new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS, DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.SECURITY_AUTH) + " Authentication aborted").immutable();
    private static final Response ALREADY_AUTH = new SMTPResponse(SMTPRetCode.BAD_SEQUENCE, DSNStatus.getStatus(DSNStatus.PERMANENT,DSNStatus.DELIVERY_OTHER) + " User has previously authenticated. "
            + " Further authentication is not required!").immutable();
    private static final Response SYNTAX_ERROR = new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS, DSNStatus.getStatus(DSNStatus.PERMANENT,DSNStatus.DELIVERY_INVALID_ARG) + " Usage: AUTH (authentication type) <challenge>").immutable();
    private static final Response AUTH_READY_USERNAME_LOGIN = new SMTPResponse(SMTPRetCode.AUTH_READY, "VXNlcm5hbWU6").immutable(); // base64 encoded "Username:"
    private static final Response AUTH_READY_PASSWORD_LOGIN = new SMTPResponse(SMTPRetCode.AUTH_READY, "UGFzc3dvcmQ6").immutable(); // base64 encoded "Password:
    private static final Response AUTH_SUCCEEDED = new SMTPResponse(SMTPRetCode.AUTH_OK, "Authentication Successful").immutable();
    private static final Response AUTH_FAILED = new SMTPResponse(SMTPRetCode.AUTH_FAILED, "Authentication Failed").immutable();
    private static final Response UNKNOWN_AUTH_TYPE = new SMTPResponse(SMTPRetCode.PARAMETER_NOT_IMPLEMENTED, "Unrecognized Authentication Type").immutable();
    private static final Response SERVER_ERROR = new SMTPResponse(SMTPRetCode.LOCAL_ERROR, "Unable to process request").immutable();

    private static final SmtpSaslBridge SASL_BRIDGE = new SmtpSaslBridge();

    private abstract static class AbstractSMTPLineHandler implements LineHandler<SMTPSession> {

        @Override
        public Response onLine(SMTPSession session, byte[] line) {
            return handleCommand(session, new String(line, session.getCharset()));
        }

        private Response handleCommand(SMTPSession session, String line) {
            // See JAMES-939

            // According to RFC2554:
            // "If the client wishes to cancel an authentication exchange, it issues a line with a single "*".
            // If the server receives such an answer, it MUST reject the AUTH
            // command by sending a 501 reply."
            if (line.equals("*\r\n")) {
                session.popLineHandler();
                return AUTH_ABORTED;
            }
            return onCommand(session, line);
        }

        protected abstract Response onCommand(SMTPSession session, String l);
    }



    /**
     * The text string for the SMTP AUTH type PLAIN.
     */
    protected static final String AUTH_TYPE_PLAIN = "PLAIN";

    /**
     * The text string for the SMTP AUTH type LOGIN.
     */
    protected static final String AUTH_TYPE_LOGIN = "LOGIN";

    private ImmutableList<SaslMechanism> saslMechanisms = ImmutableList.of();
    private ImmutableList<SaslMechanism> effectiveSaslMechanisms = ImmutableList.of();
    private Optional<SaslAuthenticator> saslAuthenticator = Optional.empty();
    private ImmutableList<AuthHook> authHooks = ImmutableList.of();
    private ImmutableList<HookResultHook> hookResultHooks = ImmutableList.of();
    private ImmutableList<SaslAuthResultHook> saslAuthResultHooks = ImmutableList.of();

    public void configureSaslMechanisms(ImmutableList<SaslMechanism> saslMechanisms, SaslAuthenticator saslAuthenticator) {
        this.saslMechanisms = saslMechanisms;
        this.saslAuthenticator = Optional.of(saslAuthenticator);
        updateEffectiveSaslMechanisms();
    }

    /**
     * handles AUTH command
     *
     */
    @Override
    public Response onCommand(SMTPSession session, Request request) {
        return doAUTH(session, request.getArgument());
    }



    /**
     * Handler method called upon receipt of a AUTH command.
     * Handles client authentication to the SMTP server.
     *
     * @param session SMTP session
     * @param argument the argument passed in with the command by the SMTP client
     */
    private Response doAUTH(SMTPSession session, String argument) {
        if (session.getUsername() != null) {
            return ALREADY_AUTH;
        } else if (argument == null) {
            return SYNTAX_ERROR;
        } else {
            String initialResponse = null;
            if (argument.indexOf(" ") > 0) {
                initialResponse = argument.substring(argument.indexOf(" ") + 1);
                argument = argument.substring(0,argument.indexOf(" "));
            }
            String authType = argument.toUpperCase(Locale.US);
            return handleSaslAuthentication(session, authType, Optional.ofNullable(initialResponse).map(String::trim));
        }
    }

    private Response handleSaslAuthentication(SMTPSession session, String authType, Optional<String> initialResponse) {
        if (authType.equals(AUTH_TYPE_LOGIN)) {
            return handleLoginFraming(session, initialResponse);
        }

        Optional<SaslMechanism> maybeMechanism = findAvailableMechanism(session, authType);
        if (maybeMechanism.isEmpty()) {
            return doUnknownAuth(authType);
        }

        SaslMechanism mechanism = maybeMechanism.get();
        SaslExchange exchange;
        try {
            SaslInitialRequest request = SASL_BRIDGE.initialRequest(authType, initialResponse);
            exchange = startExchange(session, mechanism, request);
            return handleFirstSaslStep(session, authType, exchange);
        } catch (IllegalArgumentException e) {
            LOGGER.info("Could not decode parameters for AUTH {}", authType, e);
            return new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS, "Could not decode parameters for AUTH " + authType);
        }
    }

    private Response handleFirstSaslStep(SMTPSession session, String authType, SaslExchange exchange) {
        try {
            SaslStep step = exchange.firstStep();
            return handleSaslStep(session, authType, exchange, step);
        } catch (RuntimeException e) {
            SASL_BRIDGE.close(exchange);
            throw e;
        }
    }

    private Response handleSaslStep(SMTPSession session, String authType, SaslExchange exchange, SaslStep step) {
        return switch (step) {
            case SaslStep.Challenge challenge -> {
                session.pushLineHandler(saslLineHandler(authType, exchange));
                yield SASL_BRIDGE.challenge(challenge);
            }
            case SaslStep.Success success -> handleSaslSuccess(session, authType, exchange, success);
            case SaslStep.Failure failure -> handleSaslFailure(session, authType, exchange, failure.failure());
        };
    }

    private Response handleSaslContinuation(SMTPSession session, String authType, SaslExchange exchange, String line) {
        try {
            SaslStep step = SASL_BRIDGE.onClientResponse(exchange, line.getBytes(session.getCharset()));
            return switch (step) {
                case SaslStep.Challenge challenge -> SASL_BRIDGE.challenge(challenge);
                case SaslStep.Success success -> {
                    session.popLineHandler();
                    yield handleSaslSuccess(session, authType, exchange, success);
                }
                case SaslStep.Failure failure -> {
                    session.popLineHandler();
                    yield handleSaslFailure(session, authType, exchange, failure.failure());
                }
            };
        } catch (IllegalArgumentException e) {
            LOGGER.info("Could not decode parameters for AUTH {}", authType, e);
            session.popLineHandler();
            SASL_BRIDGE.close(exchange);
            return new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS, "Could not decode parameters for AUTH " + authType);
        } catch (RuntimeException e) {
            session.popLineHandler();
            SASL_BRIDGE.close(exchange);
            throw e;
        }
    }

    private LineHandler<SMTPSession> saslLineHandler(String authType, SaslExchange exchange) {
        return (session, line) -> {
            if (SASL_BRIDGE.isAbort(line)) {
                session.popLineHandler();
                SASL_BRIDGE.abort(exchange);
                return AUTH_ABORTED;
            }
            return handleSaslContinuation(session, authType, exchange, new String(line, session.getCharset()));
        };
    }

    private Response handleSaslSuccess(SMTPSession session, String authType, SaslExchange exchange, SaslStep.Success success) {
        if (success.serverData().isPresent()) {
            session.pushLineHandler(successDataAcknowledgementLineHandler(authType, exchange, success));
            return SASL_BRIDGE.successData(success);
        }
        try {
            return applySaslSuccess(session, authType, exchange, success);
        } finally {
            SASL_BRIDGE.close(exchange);
        }
    }

    private LineHandler<SMTPSession> successDataAcknowledgementLineHandler(String authType, SaslExchange exchange, SaslStep.Success success) {
        return (session, line) -> handleSaslSuccessDataAcknowledgement(session, authType, exchange, success, new String(line, session.getCharset()));
    }

    private Response handleSaslSuccessDataAcknowledgement(SMTPSession session, String authType, SaslExchange exchange,
                                                          SaslStep.Success success, String line) {
        session.popLineHandler();
        boolean aborted = false;
        try {
            byte[] bytes = line.getBytes(session.getCharset());
            if (SASL_BRIDGE.isAbort(bytes)) {
                aborted = true;
                SASL_BRIDGE.abort(exchange);
                return AUTH_ABORTED;
            }
            if (!SASL_BRIDGE.isEmptyClientResponse(bytes)) {
                return new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS, "Could not decode parameters for AUTH " + authType);
            }
            return applySaslSuccess(session, authType, exchange, success);
        } finally {
            if (!aborted) {
                SASL_BRIDGE.close(exchange);
            }
        }
    }

    private Response applySaslSuccess(SMTPSession session, String authType, SaslExchange exchange, SaslStep.Success success) {
        Username username = success.identity().authorizationId();
        session.setUsername(username);
        session.setRelayingAllowed(true);
        saslAuthResultHooks.forEach(hook -> hook.onSuccess(session, authType, success.identity()));

        AUTHENTICATION_DEDICATED_LOGGER.debug("AUTH method {} succeeded", authType);

        AuditTrail.entry()
            .username(username::asString)
            .remoteIP(() -> Optional.ofNullable(session.getRemoteAddress()))
            .sessionId(session::getSessionID)
            .protocol("SMTP")
            .action("AUTH")
            .parameters(() -> ImmutableMap.of("authType", authType))
            .log("SMTP Authentication succeeded.");

        return AuthHookSaslMechanism.terminalResponse(exchange)
            .orElse(AUTH_SUCCEEDED);
    }

    private Response handleSaslFailure(SMTPSession session, String authType, SaslExchange exchange, SaslFailure failure) {
        try {
            saslAuthResultHooks.forEach(hook -> hook.onFailure(session, authType, failure));

            failure.authenticationId().ifPresent(username -> AuditTrail.entry()
                .username(username::asString)
                .remoteIP(() -> Optional.ofNullable(session.getRemoteAddress()))
                .protocol("SMTP")
                .action("AUTH")
                .parameters(() -> ImmutableMap.of("authType", authType))
                .log("SMTP Authentication failed."));

            return AuthHookSaslMechanism.terminalResponse(exchange)
                .orElseGet(() -> switch (failure.type()) {
                    case MALFORMED -> new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS, "Could not decode parameters for AUTH " + authType);
                    case SERVER_ERROR -> SERVER_ERROR;
                    case INVALID_CREDENTIALS, AUTHENTICATION_FAILED, USER_DOES_NOT_EXIST, DELEGATION_FORBIDDEN -> AUTH_FAILED;
                });
        } finally {
            SASL_BRIDGE.close(exchange);
        }
    }

    private Response handleLoginFraming(SMTPSession session, Optional<String> initialResponse) {
        Optional<SaslMechanism> plain = findAvailableMechanism(session, SaslMechanismNames.PLAIN);
        if (plain.isEmpty()) {
            return doUnknownAuth(AUTH_TYPE_LOGIN);
        }

        return initialResponse
            .map(user -> promptLoginPasswordThenDelegateToPlain(session, plain.get(), user))
            .orElseGet(() -> {
                session.pushLineHandler(new AbstractSMTPLineHandler() {
                    @Override
                    protected Response onCommand(SMTPSession session, String line) {
                        session.popLineHandler();
                        return promptLoginPasswordThenDelegateToPlain(session, plain.get(), line);
                    }
                });
                return AUTH_READY_USERNAME_LOGIN;
            });
    }

    private Response promptLoginPasswordThenDelegateToPlain(SMTPSession session, SaslMechanism plain, String encodedUsername) {
        session.pushLineHandler(new AbstractSMTPLineHandler() {
            @Override
            protected Response onCommand(SMTPSession session, String encodedPassword) {
                session.popLineHandler();
                return delegateLoginCredentialsToPlain(session, plain, decodeLoginUsername(encodedUsername), encodedPassword);
            }
        });
        return AUTH_READY_PASSWORD_LOGIN;
    }

    private Response delegateLoginCredentialsToPlain(SMTPSession session, SaslMechanism plain, Optional<Username> username, String encodedPassword) {
        Optional<String> password = decodeLoginPassword(username, encodedPassword);
        if (username.isEmpty() || password.isEmpty()) {
            return new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS, "Could not decode parameters for AUTH " + AUTH_TYPE_LOGIN);
        }
        SaslInitialRequest request = new SaslInitialRequest(SaslMechanismNames.PLAIN,
            Optional.of(toPlainInitialResponse(username.get(), password.get())));
        SaslExchange exchange = startExchange(session, plain, request);
        return handleFirstSaslStep(session, AUTH_TYPE_LOGIN, exchange);
    }

    private byte[] toPlainInitialResponse(Username username, String password) {
        return ("\0" + username.asString() + "\0" + password).getBytes(StandardCharsets.UTF_8);
    }

    private Optional<Username> decodeLoginUsername(String response) {
        try {
            return Optional.of(Username.of(decodeSaslLoginResponse(response)));
        } catch (IllegalArgumentException e) {
            LOGGER.info("Could not decode LOGIN username", e);
            return Optional.empty();
        }
    }

    private Optional<String> decodeLoginPassword(Optional<Username> username, String response) {
        try {
            return Optional.of(decodeSaslLoginResponse(response));
        } catch (IllegalArgumentException e) {
            LOGGER.info("Could not decode LOGIN password for user {}", username, e);
            return Optional.empty();
        }
    }

    private String decodeSaslLoginResponse(String response) {
        return new String(Base64.getDecoder().decode(response.replace("\r\n", "")), StandardCharsets.UTF_8);
    }

    private Optional<SaslMechanism> findAvailableMechanism(SMTPSession session, String authType) {
        return effectiveSaslMechanisms
            .stream()
            .filter(mechanism -> mechanism.name().equalsIgnoreCase(authType))
            .filter(mechanism -> mechanism.isAvailableOnTransport(session.isTLSStarted()))
            .findFirst();
    }

    private SaslExchange startExchange(SMTPSession session, SaslMechanism mechanism, SaslInitialRequest request) {
        if (mechanism instanceof AuthHookSaslMechanism authHookSaslMechanism) {
            return authHookSaslMechanism.start(request, session);
        }
        return mechanism.start(request, saslAuthenticator());
    }

    private SaslAuthenticator saslAuthenticator() {
        return saslAuthenticator
            .orElseThrow(() -> new IllegalStateException("SASL authenticator is not configured"));
    }

    private void updateEffectiveSaslMechanisms() {
        this.effectiveSaslMechanisms = saslMechanisms.stream()
            .map(this::adaptPlainMechanismForLegacyAuthHooks)
            .collect(ImmutableList.toImmutableList());
    }

    private SaslMechanism adaptPlainMechanismForLegacyAuthHooks(SaslMechanism mechanism) {
        if (!authHooks.isEmpty() && mechanism.name().equalsIgnoreCase(SaslMechanismNames.PLAIN)) {
            // Legacy AuthHooks own PLAIN authentication: replace, rather than append to, the configured mechanism
            // so a declined hook remains a terminal authentication failure as it was before SASL modularization.
            return new AuthHookSaslMechanism(mechanism, authHooks, hookResultHooks);
        }
        return mechanism;
    }

    /**
     * Handles the case of an unrecognized auth type.
     *
     * @param authType the unknown auth type
     */
    private Response doUnknownAuth(String authType) {
        LOGGER.info("AUTH method {} is an unrecognized authentication type", authType);
        return UNKNOWN_AUTH_TYPE;
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

    @Override
    public List<String> getImplementedEsmtpFeatures(SMTPSession session) {
        if (session.isAuthAnnounced()) {
            ImmutableList<String> authTypes = saslAuthTypes(session);
            if (authTypes.isEmpty()) {
                return Collections.emptyList();
            }
            String joined = Joiner.on(AUTH_TYPES_DELIMITER).join(authTypes);
            return ImmutableList.of("AUTH " + joined, "AUTH=" + joined);
        }
        return Collections.emptyList();
    }

    private ImmutableList<String> saslAuthTypes(SMTPSession session) {
        return effectiveSaslMechanisms
            .stream()
            .filter(mechanism -> mechanism.isAvailableOnTransport(session.isTLSStarted()))
            .flatMap(mechanism -> {
                if (mechanism.name().equalsIgnoreCase(SaslMechanismNames.PLAIN)) {
                    return Stream.of(AUTH_TYPE_LOGIN, AUTH_TYPE_PLAIN);
                }
                return Stream.of(mechanism.name());
            })
            .distinct()
            .collect(ImmutableList.toImmutableList());
    }

    @Override
    public List<Class<?>> getMarkerInterfaces() {
        return ImmutableList.of(AuthHook.class, HookResultHook.class, SaslAuthResultHook.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void wireExtensions(Class<?> interfaceName, List<?> extension) throws WiringException {
        if (AuthHook.class.equals(interfaceName)) {
            this.authHooks = ImmutableList.copyOf((List<AuthHook>) extension);
        } else if (HookResultHook.class.equals(interfaceName)) {
            this.hookResultHooks = ImmutableList.copyOf((List<HookResultHook>) extension);
        } else if (SaslAuthResultHook.class.equals(interfaceName)) {
            this.saslAuthResultHooks = ImmutableList.copyOf((List<SaslAuthResultHook>) extension);
        }
        updateEffectiveSaslMechanisms();
    }

    @Override
    public HookResult doMailParameter(SMTPSession session, String paramName, String paramValue) {
        // Ignore the AUTH command.
        // TODO we should at least check for correct syntax and put the result in session
        return HookResult.DECLINED;
    }

    @Override
    public String[] getMailParamNames() {
        return MAIL_PARAMS;
    }

}
