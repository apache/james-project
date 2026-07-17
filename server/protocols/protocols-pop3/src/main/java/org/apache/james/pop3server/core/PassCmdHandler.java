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

import static org.apache.james.pop3server.core.AuthCmdHandler.AUTH_REQUIRES_TLS;

import java.io.IOException;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.pop3server.mailbox.MailboxAdapterFactory;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslFailure;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.apache.james.protocols.lib.POP3BeforeSMTPHelper;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.core.AbstractPassCmdHandler;
import org.apache.james.protocols.pop3.mailbox.Mailbox;
import org.apache.james.protocols.sasl.JamesSaslAuthenticator;
import org.apache.james.protocols.sasl.plain.PlainSaslMechanism;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

/**
 * {@link PassCmdHandler} which also handles POP3 Before SMTP
 * 
 */
public class PassCmdHandler extends AbstractPassCmdHandler  {
    private static final Logger LOGGER = LoggerFactory.getLogger(PassCmdHandler.class);

    private final Pop3MailboxProvider mailboxProvider;
    private final SaslAuthenticator saslAuthenticator;
    private Optional<PlainSaslMechanism> plainSaslMechanism;

    @Inject
    public PassCmdHandler(@Named("mailboxmanager") MailboxManager manager, MailboxAdapterFactory mailboxAdapterFactory, MetricFactory metricFactory) {
        super(metricFactory);
        this.mailboxProvider = new Pop3MailboxProvider(manager, mailboxAdapterFactory);
        this.saslAuthenticator = JamesSaslAuthenticator.jamesSaslAuthenticator(manager);
        this.plainSaslMechanism = Optional.empty();
    }

    public void configurePlainSaslMechanism(PlainSaslMechanism plainSaslMechanism) {
        this.plainSaslMechanism = Optional.of(plainSaslMechanism);
    }

    @Override
    public Response onCommand(POP3Session session, Request request) {
        boolean authenticationRequiresTls = authenticationRequiresTls(session, request);
        Response response =  super.onCommand(session, request);
        if (authenticationRequiresTls) {
            response = AUTH_REQUIRES_TLS;
        }
        if (POP3Response.OK_RESPONSE.equals(response.getRetCode())) {
            POP3BeforeSMTPHelper.addIPAddress(session.getRemoteAddress().getAddress().getHostAddress());
        }
        return response;
    }

    private boolean authenticationRequiresTls(POP3Session session, Request request) {
        return session.getHandlerState() == POP3Session.AUTHENTICATION_USERSET
            && request.getArgument() != null
            && !session.isTLSStarted()
            && availablePlainSaslMechanism(session).isEmpty()
            && plainSaslMechanism.filter(mechanism -> mechanism.isAvailableOnTransport(true)).isPresent();
    }


    @Override
    protected Mailbox auth(POP3Session session, Username username, String password) throws Exception {
        return MDCBuilder.withMdc(
            MDCBuilder.create()
                .addToContext(MDCBuilder.USER, username.asString()),
            Throwing.supplier(() -> authenticate(session, username, password)).sneakyThrow());
    }

    private Mailbox authenticate(POP3Session session, Username username, String password) throws IOException {
        Optional<PlainSaslMechanism> availablePlainSaslMechanism = availablePlainSaslMechanism(session);
        if (availablePlainSaslMechanism.isEmpty()) {
            LOGGER.warn("PASS rejected because authentication is unavailable on the current transport");
            return null;
        }

        return switch (availablePlainSaslMechanism.orElseThrow().authenticate(username, password, saslAuthenticator)) {
            case SaslStep.Success success -> mailboxProvider.open(session, success.identity().authorizationId());
            case SaslStep.Failure failure -> handleAuthenticationFailure(session, failure.failure());
            case SaslStep.Challenge ignored -> throw new IllegalStateException("Direct PLAIN authentication must be terminal");
        };
    }

    private Optional<PlainSaslMechanism> availablePlainSaslMechanism(POP3Session session) {
        return plainSaslMechanism
            .filter(mechanism -> mechanism.isAvailableOnTransport(session.isTLSStarted()));
    }

    private Mailbox handleAuthenticationFailure(POP3Session session, SaslFailure failure) throws IOException {
        if (failure.type() == SaslFailure.Type.SERVER_ERROR) {
            throw failure.cause()
                .map(cause -> new IOException("Unable to authenticate POP3 user " + session.getUsername().asString(), cause))
                .orElseGet(() -> new IOException("Unable to authenticate POP3 user " + session.getUsername().asString()));
        }

        LOGGER.info("Bad credential supplied for {} with remote address {}",
            session.getUsername().asString(),
            session.getRemoteAddress().getAddress().getHostAddress());
        return null;
    }

}
