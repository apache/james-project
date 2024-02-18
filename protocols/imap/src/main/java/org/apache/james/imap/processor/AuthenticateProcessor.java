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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.Username;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.request.ImapRequest;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.AuthenticateRequest;
import org.apache.james.imap.message.request.IRAuthenticateRequest;
import org.apache.james.imap.message.response.AuthenticateResponse;
import org.apache.james.jwt.OidcJwtTokenVerifier;
import org.apache.james.jwt.introspection.IntrospectionEndpoint;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.OIDCSASLParser;
import org.apache.james.protocols.api.OidcSASLConfiguration;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

/**
 * Processor which handles the AUTHENTICATE command. Only authtype of PLAIN is supported ATM.
 */
public class AuthenticateProcessor extends AbstractAuthProcessor<AuthenticateRequest> implements CapabilityImplementingProcessor {
    public static final String AUTH_PLAIN = "AUTH=PLAIN";
    public static final Capability AUTH_PLAIN_CAPABILITY = Capability.of(AUTH_PLAIN);
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticateProcessor.class);
    private static final String AUTH_TYPE_PLAIN = "PLAIN";
    private static final String AUTH_TYPE_OAUTHBEARER = "OAUTHBEARER";
    private static final String AUTH_TYPE_XOAUTH2 = "XOAUTH2";
    private static final List<Capability> OAUTH_CAPABILITIES = ImmutableList.of(Capability.of("AUTH=" + AUTH_TYPE_OAUTHBEARER), Capability.of("AUTH=" + AUTH_TYPE_XOAUTH2));
    public static final Capability SASL_CAPABILITY = Capability.of("SASL-IR");

    @Inject
    public AuthenticateProcessor(MailboxManager mailboxManager, StatusResponseFactory factory,
                                 MetricFactory metricFactory) {
        super(AuthenticateRequest.class, mailboxManager, factory, metricFactory);
    }

    @Override
    public List<Class<? extends AuthenticateRequest>> acceptableClasses() {
        return ImmutableList.of(AuthenticateRequest.class, IRAuthenticateRequest.class);
    }

    @Override
    protected void processRequest(AuthenticateRequest request, ImapSession session, final Responder responder) {
        final String authType = request.getAuthType();

        if (authType.equalsIgnoreCase(AUTH_TYPE_PLAIN)) {
            // See if AUTH=PLAIN is allowed. See IMAP-304
            if (session.isPlainAuthDisallowed()) {
                LOGGER.warn("Login attempt over clear channel rejected");
                no(request, responder, HumanReadableText.DISABLED_LOGIN);
            } else {
                if (request instanceof IRAuthenticateRequest) {
                    IRAuthenticateRequest irRequest = (IRAuthenticateRequest) request;
                    doPlainAuth(irRequest.getInitialClientResponse(), session, request, responder);
                } else {
                    session.executeSafely(() -> {
                        responder.respond(new AuthenticateResponse());
                        responder.flush();
                        session.pushLineHandler((requestSession, data) -> {
                            doPlainAuth(extractInitialClientResponse(data), requestSession, request, responder);
                            // remove the handler now
                            requestSession.popLineHandler();
                            responder.flush();
                        });
                    });
                }
            }
        } else if (authType.equalsIgnoreCase(AUTH_TYPE_OAUTHBEARER) || authType.equalsIgnoreCase(AUTH_TYPE_XOAUTH2)) {
            if (request instanceof IRAuthenticateRequest) {
                IRAuthenticateRequest irRequest = (IRAuthenticateRequest) request;
                doOAuth(irRequest.getInitialClientResponse(), session, request, responder);
            } else {
                session.executeSafely(() -> {
                    responder.respond(new AuthenticateResponse());
                    responder.flush();
                    session.pushLineHandler((requestSession, data) -> {
                        doOAuth(extractInitialClientResponse(data), requestSession, request, responder);
                        requestSession.popLineHandler();
                        responder.flush();
                    });
                });
            }
        } else {
            LOGGER.debug("Unsupported authentication mechanism '{}'", authType);
            no(request, responder, HumanReadableText.UNSUPPORTED_AUTHENTICATION_MECHANISM);
        }
    }

    /**
     * Parse the initialClientResponse and do a PLAIN AUTH with it
     */
    protected void doPlainAuth(String initialClientResponse, ImapSession session, ImapRequest request, Responder responder) {
        AuthenticationAttempt authenticationAttempt = parseDelegationAttempt(initialClientResponse);
        if (authenticationAttempt.isDelegation()) {
            doAuthWithDelegation(authenticationAttempt, session, request, responder);
        } else {
            doAuth(authenticationAttempt, session, request, responder, HumanReadableText.AUTHENTICATION_FAILED);
        }
        session.stopDetectingCommandInjection();
    }

    private AuthenticationAttempt parseDelegationAttempt(String initialClientResponse) {
        try {
            String userpass = new String(Base64.getDecoder().decode(initialClientResponse));
            List<String> tokens = Arrays.stream(userpass.split("\0"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toList());
            Preconditions.checkArgument(tokens.size() == 2 || tokens.size() == 3);
            if (tokens.size() == 2) {
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
                return noDelegation(Username.of(tokens.get(0)), tokens.get(1));
            } else {
                return delegation(Username.of(tokens.get(0)), Username.of(tokens.get(1)), tokens.get(2));
            }
        } catch (Exception e) {
            // Ignored - this exception in parsing will be dealt
            // with in the if clause below
            LOGGER.info("Invalid syntax in AUTHENTICATE initial client response", e);
            return noDelegation(null, null);
        }
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        List<Capability> caps = new ArrayList<>();
        // Only ounce AUTH=PLAIN if the session does allow plain auth or TLS is active.
        // See IMAP-304
        if (!session.isPlainAuthDisallowed()) {
            caps.add(AUTH_PLAIN_CAPABILITY);
        }
        // Support for SASL-IR. See RFC4959
        caps.add(SASL_CAPABILITY);
        if (session.supportsOAuth()) {
            caps.addAll(OAUTH_CAPABILITIES);
        }
        return ImmutableList.copyOf(caps);
    }

    @Override
    protected MDCBuilder mdc(AuthenticateRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "AUTHENTICATE")
            .addToContext("authType", request.getAuthType());
    }

    protected void doOAuth(String initialResponse, ImapSession session, ImapRequest request, Responder responder) {
        if (!session.supportsOAuth()) {
            no(request, responder, HumanReadableText.UNSUPPORTED_AUTHENTICATION_MECHANISM);
        } else {
            OIDCSASLParser.parse(initialResponse)
                .flatMap(oidcInitialResponseValue -> session.oidcSaslConfiguration().map(configure -> Pair.of(oidcInitialResponseValue, configure)))
                .ifPresentOrElse(pair -> doOAuth(pair.getLeft(), pair.getRight(), session, request, responder),
                    () -> manageFailureCount(session, request, responder));
        }
        session.stopDetectingCommandInjection();
    }

    private void doOAuth(OIDCSASLParser.OIDCInitialResponse oidcInitialResponse, OidcSASLConfiguration oidcSASLConfiguration,
                         ImapSession session, ImapRequest request, Responder responder) {
        validateToken(oidcSASLConfiguration, oidcInitialResponse.getToken())
            .ifPresentOrElse(authenticatedUser -> {
                Username associatedUser = Username.of(oidcInitialResponse.getAssociatedUser());
                if (!associatedUser.equals(authenticatedUser)) {
                    doAuthWithDelegation(() -> getMailboxManager()
                            .authenticate(authenticatedUser)
                            .as(associatedUser),
                        session, request, responder, authenticatedUser, associatedUser);
                } else {
                    authSuccess(authenticatedUser, session, request, responder);
                }
            }, () -> manageFailureCount(session, request, responder));
    }

    private Optional<Username> validateToken(OidcSASLConfiguration oidcSASLConfiguration, String token) {
        if (oidcSASLConfiguration.isCheckTokenByIntrospectionEndpoint()) {
            return validTokenWithIntrospection(oidcSASLConfiguration, token);
        } else if (oidcSASLConfiguration.isCheckTokenByUserinfoEndpoint()) {
            return validTokenWithUserInfo(oidcSASLConfiguration, token);
        } else {
            return OidcJwtTokenVerifier.verifySignatureAndExtractClaim(token, oidcSASLConfiguration.getJwksURL(), oidcSASLConfiguration.getClaim())
                .map(Username::of);
        }
    }

    private static Optional<Username> validTokenWithUserInfo(OidcSASLConfiguration oidcSASLConfiguration, String token) {
        return Mono.from(OidcJwtTokenVerifier.verifyWithUserinfo(token,
                oidcSASLConfiguration.getJwksURL(),
                oidcSASLConfiguration.getClaim(),
                oidcSASLConfiguration.getUserInfoEndpoint().orElseThrow()))
            .blockOptional()
            .map(Username::of);
    }

    private static Optional<Username> validTokenWithIntrospection(OidcSASLConfiguration oidcSASLConfiguration, String token) {
        return Mono.from(OidcJwtTokenVerifier.verifyWithIntrospection(token,
                oidcSASLConfiguration.getJwksURL(),
                oidcSASLConfiguration.getClaim(),
                oidcSASLConfiguration.getIntrospectionEndpoint()
                    .map(endpoint -> new IntrospectionEndpoint(endpoint, oidcSASLConfiguration.getIntrospectionEndpointAuthorization()))
                    .orElseThrow()))
            .blockOptional()
            .map(Username::of);
    }

    private static String extractInitialClientResponse(byte[] data) {
        // cut of the CRLF
        return new String(data, 0, data.length - 2, StandardCharsets.US_ASCII);
    }

}
