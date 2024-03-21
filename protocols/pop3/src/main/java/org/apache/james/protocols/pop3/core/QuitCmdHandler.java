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

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import jakarta.inject.Inject;

import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.mailbox.Mailbox;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * Handles QUIT command
 */
public class QuitCmdHandler extends AbstractPOP3CommandHandler {
    private static final Collection<String> COMMANDS = ImmutableSet.of("QUIT");
    private static final Logger LOGGER = LoggerFactory.getLogger(QuitCmdHandler.class);
    private static final Response SIGN_OFF;
    private static final Response SIGN_OFF_NOT_CLEAN;

    static {
        POP3Response response = new POP3Response(POP3Response.OK_RESPONSE, "Apache James POP3 Server signing off.");
        response.setEndSession(true);
        SIGN_OFF = response.immutable();
        
        response = new POP3Response(POP3Response.ERR_RESPONSE, "Some deleted messages were not removed");
        response.setEndSession(true);
        SIGN_OFF_NOT_CLEAN = response.immutable();
    }

    private final MetricFactory metricFactory;

    @Inject
    public QuitCmdHandler(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
    }

    /**
     * Handler method called upon receipt of a QUIT command. This method handles
     * cleanup of the POP3Handler state.
     */
    @Override
    public Response onCommand(POP3Session session, Request request) {
        return metricFactory.decorateSupplierWithTimerMetric("pop3-quit", () ->
            MDCBuilder.withMdc(
                MDCBuilder.create()
                    .addToContext(MDCBuilder.ACTION, "QUIT")
                    .addToContext(MDCConstants.withSession(session)),
                () -> quit(session)));
    }

    protected Response quit(POP3Session session) {
        LOGGER.trace("QUIT command received");
        Response response = null;
        if (session.getHandlerState() == POP3Session.AUTHENTICATION_READY || session.getHandlerState() == POP3Session.AUTHENTICATION_USERSET) {
            return SIGN_OFF;
        }
        List<String> toBeRemoved = session.getAttachment(POP3Session.DELETED_UID_LIST, State.Transaction).orElse(ImmutableList.of());
        Mailbox mailbox = session.getUserMailbox();
        try {
            String[] uids = toBeRemoved.toArray(String[]::new);
            mailbox.remove(uids);
            response = SIGN_OFF;
        } catch (Exception ex) {
            response = SIGN_OFF_NOT_CLEAN;
            LOGGER.error("Some deleted messages were not removed", ex);
        }
        try {
            mailbox.close();
        } catch (IOException e) {
            // ignore on close
        }
        return response;
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

}
