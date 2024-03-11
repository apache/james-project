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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.Username;
import org.apache.james.protocols.api.OidcSASLConfiguration;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandHandler;
import org.apache.james.protocols.api.handler.ExtensibleHandler;
import org.apache.james.protocols.api.handler.LineHandler;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.AuthHook;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookResultHook;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.MailParametersHook;
import org.apache.james.util.AuditTrail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;


/**
 * handles AUTH command
 *
 * Note: we could extend this to use java5 sasl standard libraries and provide client
 * support against a server implemented via non-james specific hooks.
 * This would allow us to reuse hooks between imap4/pop3/smtp and eventually different
 * system (simple pluggabilty against external authentication services).
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
    private static final Response AUTH_READY_PLAIN = new SMTPResponse(SMTPRetCode.AUTH_READY, "OK. Continue authentication").immutable();
    private static final Response AUTH_READY_USERNAME_LOGIN = new SMTPResponse(SMTPRetCode.AUTH_READY, "VXNlcm5hbWU6").immutable(); // base64 encoded "Username:"
    private static final Response AUTH_READY_PASSWORD_LOGIN = new SMTPResponse(SMTPRetCode.AUTH_READY, "UGFzc3dvcmQ6").immutable(); // base64 encoded "Password:
    private static final Response AUTH_FAILED = new SMTPResponse(SMTPRetCode.AUTH_FAILED, "Authentication Failed").immutable();
    private static final Response UNKNOWN_AUTH_TYPE = new SMTPResponse(SMTPRetCode.PARAMETER_NOT_IMPLEMENTED, "Unrecognized Authentication Type").immutable();

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

    /**
     * The text string for the SMTP AUTH type OAUTHBEARER.
     */
    protected static final String AUTH_TYPE_OAUTHBEARER = "OAUTHBEARER";
    protected static final String AUTH_TYPE_XOAUTH2 = "XOAUTH2";

    /**
     * The AuthHooks
     */
    private List<AuthHook> hooks;

    private List<HookResultHook> rHooks;

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
            if (authType.equals(AUTH_TYPE_PLAIN) && session.getConfiguration().isPlainAuthEnabled()) {
                String userpass;
                if (initialResponse == null) {
                    session.pushLineHandler(new AbstractSMTPLineHandler() {
                        @Override
                        protected Response onCommand(SMTPSession session, String l) {
                            return doPlainAuth(session, l);
                        }
                    });
                    return AUTH_READY_PLAIN;
                } else {
                    userpass = initialResponse.trim();
                    return doPlainAuth(session, userpass);
                }
            } else if (authType.equals(AUTH_TYPE_LOGIN) && session.getConfiguration().isPlainAuthEnabled()) {

                if (initialResponse == null) {
                    session.pushLineHandler(new AbstractSMTPLineHandler() {
                        @Override
                        protected Response onCommand(SMTPSession session, String l) {
                            return doLoginAuthPass(session, l);
                        }
                    });
                    return AUTH_READY_USERNAME_LOGIN;
                } else {
                    String user = initialResponse.trim();
                    return doLoginAuthPass(session, user);
                }
            } else if ((authType.equals(AUTH_TYPE_OAUTHBEARER) || authType.equals(AUTH_TYPE_XOAUTH2))
                && session.supportsOAuth()) {
                return doSASLAuthentication(session, initialResponse);
            } else {
                return doUnknownAuth(authType);
            }
        }
    }

    private Response doSASLAuthentication(SMTPSession session, String initialResponseString) {
        return session.getConfiguration().saslConfiguration()
            .map(oidcSASLConfiguration -> hooks.stream()
                .flatMap(hook -> Optional.ofNullable(executeHook(session, hook,
                    hook2 -> hook2.doSasl(session, oidcSASLConfiguration, initialResponseString))).stream())
                .filter(response -> !SMTPRetCode.AUTH_FAILED.equals(response.getRetCode()))
                .findFirst()
                .orElseGet(() -> failSasl(oidcSASLConfiguration, session)))
            .orElseGet(() -> doUnknownAuth(AUTH_TYPE_OAUTHBEARER));
    }

    private Response failSasl(OidcSASLConfiguration saslConfiguration, SMTPSession session) {
        String rawResponse = String.format("{\"status\":\"invalid_token\",\"scope\":\"%s\",\"schemes\":\"%s\"}",
            saslConfiguration.getScope(),
            saslConfiguration.getOidcConfigurationURL().toString());

        session.pushLineHandler(new AbstractSMTPLineHandler() {
            @Override
            protected Response onCommand(SMTPSession session, String l) {
                session.popLineHandler();

                return AUTH_FAILED;
            }
        });
        return new SMTPResponse("334", Base64.getEncoder().encodeToString(rawResponse.getBytes()));
    }

    /**
     * Carries out the Plain AUTH SASL exchange.
     *
     * According to RFC 2595 the client must send: [authorize-id] \0 authenticate-id \0 password.
     *
     * >>> AUTH PLAIN dGVzdAB0ZXN0QHdpei5leGFtcGxlLmNvbQB0RXN0NDI=
     * Decoded: test\000test@wiz.example.com\000tEst42
     *
     * >>> AUTH PLAIN dGVzdAB0ZXN0AHRFc3Q0Mg==
     * Decoded: test\000test\000tEst42
     *
     * @param session SMTP session object
     * @param line the initial response line passed in with the AUTH command
     */
    private Response doPlainAuth(SMTPSession session, String line) {
        try {
            List<String> tokens = Optional.ofNullable(decodeBase64(line))
                .map(userpass1 -> Arrays.stream(userpass1.split("\0"))
                    .filter(token -> !token.isBlank())
                    .collect(Collectors.toList()))
                .orElse(List.of());
            Preconditions.checkArgument(tokens.size() == 1 || tokens.size() == 2 || tokens.size() == 3);
            Response response = null;

            if (tokens.size() == 1) {
                response = doDelegation(session, Username.of(tokens.get(0)));
            } else if (tokens.size() == 2) {
                // If we got here, this is what happened.  RFC 2595
                // says that "the client may leave the authorization
                // identity empty to indicate that it is the same as
                // the authentication identity."  As noted above,
                // that would be represented as a decoded string of
                // the form: "\0authenticate-id\0password".  The
                // first call to nextToken will skip the empty
                // authorize-id, and give us the authenticate-id,
                // which we would store as the authorize-id.  The
                // second call will give us the password, which we
                // think is the authenticate-id (user).  Then when
                // we ask for the password, there are no more
                // elements, leading to the exception we just
                // caught.  So we need to move the user to the
                // password, and the authorize_id to the user.
                response = doAuthTest(session, Username.of(tokens.get(0)), tokens.get(1), AUTH_TYPE_PLAIN);
            } else {
                response = doAuthTest(session, Username.of(tokens.get(1)), tokens.get(2), AUTH_TYPE_PLAIN);
            }
            session.popLineHandler();
            return response;
        } catch (Exception e) {
            return new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS,"Could not decode parameters for AUTH PLAIN");
        }
    }

    private String decodeBase64(String line) {
        if (line != null) {
            String lineWithoutTrailingCrLf = StringUtils.replace(line, "\r\n", "");
            return new String(Base64.getDecoder().decode(lineWithoutTrailingCrLf), StandardCharsets.UTF_8);
        }
        return null;
    }

    /**
     * Carries out the Login AUTH SASL exchange.
     *
     * @param session SMTP session object
     * @param user the user passed in with the AUTH command
     */
    private Response doLoginAuthPass(SMTPSession session, String user) {
        session.popLineHandler();
        session.pushLineHandler(new AbstractSMTPLineHandler() {
            @Override
            protected Response onCommand(SMTPSession session, String l) {
                return doLoginAuthPassCheck(session, asUsername(user), l);
            }
        });
        return AUTH_READY_PASSWORD_LOGIN;
    }

    private Username asUsername(String user) {
        if (user != null) {
            try {
                return Username.of(decodeBase64(user));
            } catch (Exception e) {
                LOGGER.info("Failed parsing base64 username {}", user, e);
                return null;
            }
        }
        LOGGER.info("Missing username");
        return null;
    }

    private Response doLoginAuthPassCheck(SMTPSession session, Username username, String pass) {
        session.popLineHandler();
        // Authenticate user
        return doAuthTest(session, username, sanitizePassword(username, pass), "LOGIN");
    }

    private String sanitizePassword(Username username, String pass) {
        if (pass != null) {
            try {
                return decodeBase64(pass);
            } catch (Exception e) {
                LOGGER.info("Failed parsing base64 password for user {}", username, e);
                // Ignored - this parse error will be
                // addressed in the if clause below
                return null;
            }
        }
        LOGGER.info("Login attempt without a password for {}", username);
        return null;
    }

    protected Response doDelegation(SMTPSession session, Username username) {
        List<AuthHook> hooks = Optional.ofNullable(getHooks())
            .orElse(List.of());

        for (AuthHook rawHook : hooks) {
            rawHook.doDelegation(session, username);
            Response res = executeHook(session, rawHook, hook -> rawHook.doDelegation(session, username));

            if (res != null) {
                if (SMTPRetCode.AUTH_FAILED.equals(res.getRetCode())) {
                    LOGGER.warn("{} was not authorized to connect as {}", session.getUsername(), username);
                } else if (SMTPRetCode.AUTH_OK.equals(res.getRetCode())) {
                    LOGGER.info("{} was authorized to connect as {}", session.getUsername(), username);
                }
                return res;
            }
        }

        LOGGER.info("DELEGATE failed from {}@{}", username, session.getRemoteAddress().getAddress().getHostAddress());
        return AUTH_FAILED;
    }

    protected Response doAuthTest(SMTPSession session, Username username, String pass, String authType) {
        if ((username == null) || (pass == null)) {
            return new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS,"Could not decode parameters for AUTH " + authType);
        }

        List<AuthHook> hooks = getHooks();

        if (hooks != null) {
            for (AuthHook rawHook : hooks) {
                Response res = executeHook(session, rawHook, hook -> hook.doAuth(session, username, pass));

                if (res != null) {
                    if (SMTPRetCode.AUTH_FAILED.equals(res.getRetCode())) {
                        AUTHENTICATION_DEDICATED_LOGGER.info("AUTH method {} failed", authType);
                    } else if (SMTPRetCode.AUTH_OK.equals(res.getRetCode())) {
                        // TODO: Make this string a more useful debug message
                        AUTHENTICATION_DEDICATED_LOGGER.debug("AUTH method {} succeeded", authType);

                        AuditTrail.entry()
                            .username(username::asString)
                            .remoteIP(() -> Optional.ofNullable(session.getRemoteAddress()))
                            .sessionId(session::getSessionID)
                            .protocol("SMTP")
                            .action("AUTH")
                            .parameters(() -> ImmutableMap.of("authType", authType))
                            .log("SMTP Authentication succeeded.");
                    }
                    return res;
                }
            }
        }

        AUTHENTICATION_DEDICATED_LOGGER.info("AUTH method {} failed from {}@{}", authType, username, session.getRemoteAddress().getAddress().getHostAddress());

        AuditTrail.entry()
            .username(username::asString)
            .remoteIP(() -> Optional.ofNullable(session.getRemoteAddress()))
            .protocol("SMTP")
            .action("AUTH")
            .parameters(() -> ImmutableMap.of("authType", authType))
            .log("SMTP Authentication failed.");

        return AUTH_FAILED;
    }

    private Response executeHook(SMTPSession session, AuthHook rawHook, Function<AuthHook, HookResult> tc) {
        LOGGER.debug("executing  hook {}", rawHook);

        long start = System.currentTimeMillis();
        HookResult hRes = tc.apply(rawHook);
        long executionTime = System.currentTimeMillis() - start;

        HookResult finalHookResult = Optional.ofNullable(rHooks)
                .orElse(ImmutableList.of()).stream()
                .peek(rHook -> LOGGER.debug("executing  hook {}", rHook))
                .reduce(hRes, (a, b) -> b.onHookResult(session, a, executionTime, rawHook), (a, b) -> {
                    throw new UnsupportedOperationException();
                });

        return calcDefaultSMTPResponse(finalHookResult);
    }

    /**
     * Calculate the SMTPResponse for the given result
     *
     * @param result the HookResult which should converted to SMTPResponse
     * @return the calculated SMTPResponse for the given HookReslut
     */
    protected Response calcDefaultSMTPResponse(HookResult result) {
        if (result != null) {
            HookReturnCode returnCode = result.getResult();

            String smtpReturnCode = Optional.ofNullable(result.getSmtpRetCode())
                .or(() -> retrieveDefaultSmtpReturnCode(returnCode))
                .orElse(null);

            String smtpDescription = Optional.ofNullable(result.getSmtpDescription())
                .or(() -> retrieveDefaultSmtpDescription(returnCode))
                .orElse(null);

            if (HookReturnCode.Action.ACTIVE_ACTIONS.contains(returnCode.getAction())) {
                SMTPResponse response =  new SMTPResponse(smtpReturnCode, smtpDescription);

                if (returnCode.isDisconnected()) {
                    response.setEndSession(true);
                }
                return response;
            } else if (returnCode.isDisconnected()) {
                return Response.DISCONNECT;
            }
        }
        return null;

    }

    private Optional<String> retrieveDefaultSmtpDescription(HookReturnCode returnCode) {
        switch (returnCode.getAction()) {
            case DENY:
                return Optional.of("Authentication Failed");
            case DENYSOFT:
                return Optional.of("Temporary problem. Please try again later");
            case OK:
                return Optional.of("Authentication Succesfull");
            case DECLINED:
            case NONE:
                break;
        }
        return Optional.empty();
    }

    private Optional<String> retrieveDefaultSmtpReturnCode(HookReturnCode returnCode) {
        switch (returnCode.getAction()) {
            case DENY:
                return Optional.of(SMTPRetCode.AUTH_FAILED);
            case DENYSOFT:
                return Optional.of(SMTPRetCode.LOCAL_ERROR);
            case OK:
                return Optional.of(SMTPRetCode.AUTH_OK);
            case DECLINED:
            case NONE:
                break;
        }
        return Optional.empty();
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
            ImmutableList.Builder<String> authTypesBuilder = ImmutableList.builder();
            if (session.getConfiguration().isPlainAuthEnabled()) {
                authTypesBuilder.add(AUTH_TYPE_LOGIN, AUTH_TYPE_PLAIN);
            }
            if (session.getConfiguration().saslConfiguration().isPresent()) {
                authTypesBuilder.add(AUTH_TYPE_OAUTHBEARER);
                authTypesBuilder.add(AUTH_TYPE_XOAUTH2);
            }
            ImmutableList<String> authTypes = authTypesBuilder.build();
            if (authTypes.isEmpty()) {
                return Collections.emptyList();
            }
            String joined = Joiner.on(AUTH_TYPES_DELIMITER).join(authTypes);
            return ImmutableList.of("AUTH " + joined, "AUTH=" + joined);
        }
        return Collections.emptyList();
    }

    @Override
    public List<Class<?>> getMarkerInterfaces() {
        List<Class<?>> classes = new ArrayList<>(1);
        classes.add(AuthHook.class);
        return classes;
    }


    @Override
    @SuppressWarnings("unchecked")
    public void wireExtensions(Class<?> interfaceName, List<?> extension) throws WiringException {
        if (AuthHook.class.equals(interfaceName)) {
            this.hooks = (List<AuthHook>) extension;
            // If no AuthHook is configured then we revert to the default LocalUsersRespository check
            if (hooks == null || hooks.size() == 0) {
                throw new WiringException("AuthCmdHandler used without AuthHooks");
            }
        } else if (HookResultHook.class.equals(interfaceName)) {
            this.rHooks = (List<HookResultHook>) extension;
        }
    }


    /**
     * Return a list which holds all hooks for the cmdHandler
     *
     * @return list containing all hooks for the cmd handler
     */
    protected List<AuthHook> getHooks() {
        return hooks;
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
