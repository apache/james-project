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

import java.io.Closeable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.james.core.Username;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.request.ImapRequest;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.AuthenticateRequest;
import org.apache.james.imap.message.request.IRAuthenticateRequest;
import org.apache.james.imap.message.response.AuthenticateResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

/**
 * Processor which handles the AUTHENTICATE command. Only authtype of PLAIN is supported ATM.
 */
public class AuthenticateProcessor extends AbstractAuthProcessor<AuthenticateRequest> implements CapabilityImplementingProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticateProcessor.class);
    private static final String PLAIN = "PLAIN";
    
    public AuthenticateProcessor(ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory,
            MetricFactory metricFactory) {
        super(AuthenticateRequest.class, next, mailboxManager, factory, metricFactory);
    }

    @Override
    protected void processRequest(AuthenticateRequest request, ImapSession session, final Responder responder) {
        final String authType = request.getAuthType();
        if (authType.equalsIgnoreCase(PLAIN)) {
            // See if AUTH=PLAIN is allowed. See IMAP-304
            if (session.isPlainAuthDisallowed() && session.isTLSActive() == false) {
                no(request, responder, HumanReadableText.DISABLED_LOGIN);
            } else {
                if (request instanceof IRAuthenticateRequest) {
                    IRAuthenticateRequest irRequest = (IRAuthenticateRequest) request;
                    doPlainAuth(irRequest.getInitialClientResponse(), session, request, responder);
                } else {
                    responder.respond(new AuthenticateResponse());
                    session.pushLineHandler((requestSession, data) -> {
                        // cut of the CRLF
                        String initialClientResponse = new String(data, 0, data.length - 2, Charset.forName("US-ASCII"));

                        doPlainAuth(initialClientResponse, requestSession, request, responder);

                        // remove the handler now
                        requestSession.popLineHandler();
                    });
                }
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
            doAuthWithDelegation(authenticationAttempt, session, request, responder, HumanReadableText.AUTHENTICATION_FAILED);
        } else {
            doAuth(authenticationAttempt, session, request, responder, HumanReadableText.AUTHENTICATION_FAILED);
        }
    }

    private AuthenticationAttempt parseDelegationAttempt(String initialClientResponse) {
        String token2;
        try {
            String userpass = new String(Base64.getDecoder().decode(initialClientResponse));
            StringTokenizer authTokenizer = new StringTokenizer(userpass, "\0");
            String token1 = authTokenizer.nextToken();  // Authorization Identity
            token2 = authTokenizer.nextToken();                 // Authentication Identity
            try {
                return delegation(Username.of(token1), Username.of(token2), authTokenizer.nextToken());
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
                return noDelegation(Username.of(token1), token2);
            } finally {
                authTokenizer = null;
            }
        } catch (Exception e) {
            // Ignored - this exception in parsing will be dealt
            // with in the if clause below
            return noDelegation(null, null);
        }
    }

    @Override
    public List<String> getImplementedCapabilities(ImapSession session) {
        List<String> caps = new ArrayList<>();
        // Only ounce AUTH=PLAIN if the session does allow plain auth or TLS is active.
        // See IMAP-304
        if (session.isPlainAuthDisallowed()  == false || session.isTLSActive()) {
            caps.add("AUTH=PLAIN");
        }
        // Support for SASL-IR. See RFC4959
        caps.add("SASL-IR");
        return ImmutableList.copyOf(caps);
    }

    @Override
    protected Closeable addContextToMDC(AuthenticateRequest request) {
        return MDCBuilder.create()
            .addContext(MDCBuilder.ACTION, "AUTHENTICATE")
            .addContext("authType", request.getAuthType())
            .build();
    }
}
