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

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.StringTokenizer;

import org.apache.james.core.Username;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandHandler.class);
    private static final String[] MAIL_PARAMS = { "AUTH" };
    private static final List<String> ESMTP_FEATURES = ImmutableList.of("AUTH LOGIN PLAIN", "AUTH=LOGIN PLAIN");
    
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
        public Response onLine(SMTPSession session, ByteBuffer line) {
            String charset = session.getCharset().name();
            try {
                byte[] l;
                if (line.hasArray()) {
                    l = line.array();
                } else {
                    l = new byte[line.remaining()];
                    line.get(l);
                }
                return handleCommand(session, new String(l, charset));
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("No " + charset + " support!");
            }
           
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
            if ((argument != null) && (argument.indexOf(" ") > 0)) {
                initialResponse = argument.substring(argument.indexOf(" ") + 1);
                argument = argument.substring(0,argument.indexOf(" "));
            }
            String authType = argument.toUpperCase(Locale.US);
            if (authType.equals(AUTH_TYPE_PLAIN)) {
                String userpass;
                if (initialResponse == null) {
                    session.pushLineHandler(new AbstractSMTPLineHandler() {
                        @Override
                        protected Response onCommand(SMTPSession session, String l) {
                            return doPlainAuthPass(session, l);
                        }
                    });
                    return AUTH_READY_PLAIN;
                } else {
                    userpass = initialResponse.trim();
                    return doPlainAuthPass(session, userpass);
                }
            } else if (authType.equals(AUTH_TYPE_LOGIN)) {
                
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
            } else {
                return doUnknownAuth(session, authType, initialResponse);
            }
        }
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
    private Response doPlainAuthPass(SMTPSession session, String line) {
        String user = null;
        String pass = null;
        try {
            String userpass = decodeBase64(line);
            if (userpass != null) {
                /*  See: RFC 2595, Section 6
                    The mechanism consists of a single message from the client to the
                    server.  The client sends the authorization identity (identity to
                    login as), followed by a US-ASCII NUL character, followed by the
                    authentication identity (identity whose password will be used),
                    followed by a US-ASCII NUL character, followed by the clear-text
                    password.  The client may leave the authorization identity empty to
                    indicate that it is the same as the authentication identity.

                    The server will verify the authentication identity and password with
                    the system authentication database and verify that the authentication
                    credentials permit the client to login as the authorization identity.
                    If both steps succeed, the user is logged in.
                */
                StringTokenizer authTokenizer = new StringTokenizer(userpass, "\0");
                String authorizeId = authTokenizer.nextToken();  // Authorization Identity
                user = authTokenizer.nextToken();                 // Authentication Identity
                try {
                    pass = authTokenizer.nextToken();             // Password
                } catch (java.util.NoSuchElementException ignored) {
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
                    pass = user;
                    user = authorizeId;
                }

                authTokenizer = null;
            }
        } catch (Exception e) {
            // Ignored - this exception in parsing will be dealt
            // with in the if clause below
        }
        // Authenticate user
        Response response = doAuthTest(session, Username.of(user), pass, "PLAIN");
        
        session.popLineHandler();

        return response;
    }

    private String decodeBase64(String line) {
        if (line != null) {
            String lineWithoutTrailingCrLf = line.replace("\r\n", "");
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
        if (user != null) {
            try {
                user = decodeBase64(user);
            } catch (Exception e) {
                // Ignored - this parse error will be
                // addressed in the if clause below
                user = null;
            }
        }
        
        session.popLineHandler();
        
        session.pushLineHandler(new AbstractSMTPLineHandler() {

            private Username username;

            public LineHandler<SMTPSession> setUsername(Username username) {
                this.username = username;
                return this;
            }

            @Override
            protected Response onCommand(SMTPSession session, String l) {
                return doLoginAuthPassCheck(session, username, l);
            }
        }.setUsername(Username.of(user)));
        return AUTH_READY_PASSWORD_LOGIN;
    }
    
    private Response doLoginAuthPassCheck(SMTPSession session, Username username, String pass) {
        if (pass != null) {
            try {
                pass = decodeBase64(pass);
            } catch (Exception e) {
                // Ignored - this parse error will be
                // addressed in the if clause below
                pass = null;
            }
        }
       
        session.popLineHandler();

        // Authenticate user
        return doAuthTest(session, username, pass, "LOGIN");
    }

    protected Response doAuthTest(SMTPSession session, Username username, String pass, String authType) {
        if ((username == null) || (pass == null)) {
            return new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS,"Could not decode parameters for AUTH " + authType);
        }

        Response res = null;
        
        List<AuthHook> hooks = getHooks();
        
        if (hooks != null) {
            for (AuthHook rawHook : hooks) {
                LOGGER.debug("executing  hook {}", rawHook);

                long start = System.currentTimeMillis();
                HookResult hRes = rawHook.doAuth(session, username, pass);
                long executionTime = System.currentTimeMillis() - start;

                if (rHooks != null) {
                    for (HookResultHook rHook : rHooks) {
                        LOGGER.debug("executing  hook {}", rHook);
                        hRes = rHook.onHookResult(session, hRes, executionTime, rawHook);
                    }
                }

                res = calcDefaultSMTPResponse(hRes);

                if (res != null) {
                    if (SMTPRetCode.AUTH_FAILED.equals(res.getRetCode())) {
                        LOGGER.info("AUTH method {} failed", authType);
                    } else if (SMTPRetCode.AUTH_OK.equals(res.getRetCode())) {
                        // TODO: Make this string a more useful debug message
                        LOGGER.debug("AUTH method {} succeeded", authType);
                    }
                    return res;
                }
            }
        }

        res = AUTH_FAILED;
        LOGGER.error("AUTH method {} failed from {}@{}", authType, username, session.getRemoteAddress().getAddress().getHostAddress());
        return res;
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
                SMTPResponse response =  new SMTPResponse("");
                response.setEndSession(true);
                return response;
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
     * @param session SMTP session object
     * @param authType the unknown auth type
     * @param initialResponse the initial response line passed in with the AUTH command
     */
    private Response doUnknownAuth(SMTPSession session, String authType, String initialResponse) {
        LOGGER.info("AUTH method {} is an unrecognized authentication type", authType);
        return UNKNOWN_AUTH_TYPE;
    }



    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

    @Override
    public List<String> getImplementedEsmtpFeatures(SMTPSession session) {
        if (session.isAuthSupported()) {
            return ESMTP_FEATURES;
        } else {
            return Collections.emptyList();
        }
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
