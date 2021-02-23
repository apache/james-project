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

package org.apache.james.protocols.pop3.core;

import java.util.Collection;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.mailbox.Mailbox;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

/**
 * Handles PASS commands.
 */
public abstract class AbstractPassCmdHandler extends RsetCmdHandler {
    private static final Collection<String> COMMANDS = ImmutableSet.of("PASS");
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractPassCmdHandler.class);
    private static final Response UNEXPECTED_ERROR = new POP3Response(POP3Response.ERR_RESPONSE, "Unexpected error accessing mailbox").immutable();
    protected static final Response AUTH_FAILED = new POP3Response(POP3Response.ERR_RESPONSE, "Authentication failed.").immutable();

    private final MetricFactory metricFactory;

    public AbstractPassCmdHandler(MetricFactory metricFactory) {
        super(metricFactory);
        this.metricFactory = metricFactory;
    }

    /**
     * Handler method called upon receipt of a PASS command. Reads in and
     * validates the password.
     */
    @Override
    public Response onCommand(POP3Session session, Request request) {
        return metricFactory.decorateSupplierWithTimerMetric("pop3-pass", () ->
            MDCBuilder.withMdc(
            MDCBuilder.create()
                .addContext(MDCBuilder.ACTION, "AUTH")
                .addContext(MDCConstants.withSession(session)),
            () -> doAuth(session, request)));
    }

    private Response doAuth(POP3Session session, Request request) {
        LOGGER.trace("PASS command received");
        String parameters = request.getArgument();
        if (session.getHandlerState() == POP3Session.AUTHENTICATION_USERSET && parameters != null) {
            return doAuth(session, session.getUsername(), parameters);
        } else {
            session.setHandlerState(POP3Session.AUTHENTICATION_READY);
            return AUTH_FAILED;
        }
    }

    /**
     * Authenticate a user and return the {@link Response}
     */
    protected final Response doAuth(POP3Session session, Username user, String pass) {
        try {
            Mailbox mailbox = auth(session, user, pass);

            if (mailbox != null) {
                session.setUserMailbox(mailbox);
                stat(session);

                session.setHandlerState(POP3Session.TRANSACTION);

                StringBuilder responseBuffer = new StringBuilder(64).append("Welcome ")
                    .append(Optional.ofNullable(session.getUsername())
                        .map(Username::asString)
                        .orElse(null));
                return  new POP3Response(POP3Response.OK_RESPONSE, responseBuffer.toString());
            } else {
                session.setHandlerState(POP3Session.AUTHENTICATION_READY);
                return AUTH_FAILED;
            }
        } catch (Exception e) {
            LOGGER.error("Unexpected error accessing mailbox for {}", session.getUsername(), e);
            session.setHandlerState(POP3Session.AUTHENTICATION_READY);
            return UNEXPECTED_ERROR;
        }
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

    /**
     * Authenticate a {@link POP3Session} and returns the {@link Mailbox} for it. If it can not get authenticated it
     * will return <code>null</code>.
     *
     * @return mailbox
     */
    protected abstract Mailbox auth(POP3Session session, Username username, String password) throws Exception;
}
