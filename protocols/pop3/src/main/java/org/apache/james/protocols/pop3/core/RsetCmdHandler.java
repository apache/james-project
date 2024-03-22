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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jakarta.inject.Inject;

import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.mailbox.MessageMetaData;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

/**
 * Handles RSET command
 */
public class RsetCmdHandler extends AbstractPOP3CommandHandler {
    private static final Collection<String> COMMANDS = ImmutableSet.of("RSET");
    private static final Logger LOGGER = LoggerFactory.getLogger(RsetCmdHandler.class);

    private final MetricFactory metricFactory;

    @Inject
    public RsetCmdHandler(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
    }

    /**
     * Handler method called upon receipt of a RSET command. Calls stat() to
     * reset the mailbox.
     */
    @Override
    public Response onCommand(POP3Session session, Request request) {
        return metricFactory.decorateSupplierWithTimerMetric("pop3-rset", () ->
            MDCBuilder.withMdc(
                MDCBuilder.create()
                    .addToContext(MDCBuilder.ACTION, "RSET")
                    .addToContext(MDCConstants.withSession(session)),
                () -> rset(session)));
    }

    private Response rset(POP3Session session) {
        LOGGER.trace("RETR command received");
        if (session.getHandlerState() == POP3Session.TRANSACTION) {
            stat(session);
            return POP3Response.OK;
        } else {
            return POP3Response.ERR;
        }
    }

    /**
     * Implements a "stat". If the handler is currently in a transaction state,
     * this amounts to a rollback of the mailbox contents to the beginning of
     * the transaction. This method is also called when first entering the
     * transaction state to initialize the handler copies of the user inbox.
     */
    protected void stat(POP3Session session) {
        try {
            List<MessageMetaData> messages = session.getUserMailbox().getMessages();

            session.setAttachment(POP3Session.UID_LIST, messages, State.Transaction);
            session.setAttachment(POP3Session.DELETED_UID_LIST, new ArrayList<>(), State.Transaction);
        } catch (IOException e) {
            // In the event of an exception being thrown there may or may not be
            // anything in userMailbox
            LOGGER.error("Unable to STAT mail box ", e);
        }

    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

}
