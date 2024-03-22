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
import java.util.Collections;
import java.util.List;
import java.util.Set;

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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * Handles UIDL command
 */
public class UidlCmdHandler extends AbstractPOP3CommandHandler implements CapaCapability {
    private static final Logger LOGGER = LoggerFactory.getLogger(UidlCmdHandler.class);
    private static final Collection<String> COMMANDS = ImmutableSet.of("UIDL");
    private static final Set<String> CAPS = ImmutableSet.of("UIDL");

    private final MetricFactory metricFactory;
    private final POP3MessageCommandDelegate commandDelegate;

    @Inject
    public UidlCmdHandler(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
        this.commandDelegate = new POP3MessageCommandDelegate(COMMANDS) {
            @Override
            protected Response handleMessageExists(POP3Session session, MessageMetaData data, POP3MessageCommandArguments args) throws IOException {
                String identifier = session.getUserMailbox().getIdentifier();
                return new POP3Response(POP3Response.OK_RESPONSE, args.getMessageNumber() + " " + data.getUid(identifier));
            }
        };
    }

    /**
     * Handler method called upon receipt of a UIDL command. Returns a listing
     * of message ids to the client.
     */
    @Override
    public Response onCommand(POP3Session session, Request request) {
        return metricFactory.decorateSupplierWithTimerMetric("pop3-uidl", () ->
            MDCBuilder.withMdc(
                MDCBuilder.create()
                    .addToContext(MDCBuilder.ACTION, "UIDL")
                    .addToContext(MDCConstants.withSession(session))
                    .addToContext(MDCConstants.forRequest(request)),
                () -> handleMessageRequest(session, request)));
    }

    private Response handleMessageRequest(POP3Session session, Request request) {
        LOGGER.trace("UIDL command received");
        if (request.getArgument() != null) {
            return commandDelegate.handleMessageRequest(session, request);
        }
        
        if (session.getHandlerState() == POP3Session.TRANSACTION) {
            List<MessageMetaData> uidList = session.getAttachment(POP3Session.UID_LIST, State.Transaction).orElse(ImmutableList.of());
            List<String> deletedUidList = session.getAttachment(POP3Session.DELETED_UID_LIST, State.Transaction).orElse(ImmutableList.of());
            try {
                String identifier = session.getUserMailbox().getIdentifier();
                POP3Response response = new POP3Response(POP3Response.OK_RESPONSE, "unique-id listing follows");

                for (int i = 0; i < uidList.size(); i++) {
                    MessageMetaData metadata = uidList.get(i);
                    if (!deletedUidList.contains(metadata.getUid())) {
                        response.appendLine((i + 1) + " " + metadata.getUid(identifier));
                    }
                }

                response.appendLine(".");
                return response;
            } catch (IOException ioe) {
                return POP3Response.ERR;
            }
        } else {
            return POP3Response.ERR;
        }
    }

    @Override
    public Set<String> getImplementedCapabilities(POP3Session session) {
        if (session.getHandlerState() == POP3Session.TRANSACTION) {
            return CAPS;
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }
}
