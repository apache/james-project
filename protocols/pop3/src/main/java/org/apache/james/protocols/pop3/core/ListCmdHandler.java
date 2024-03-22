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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * Handles LIST command
 */
public class ListCmdHandler extends AbstractPOP3CommandHandler {

    private static final Collection<String> COMMANDS = ImmutableSet.of("LIST");

    private final MetricFactory metricFactory;
    private final POP3MessageCommandDelegate commandDelegate;

    @Inject
    public ListCmdHandler(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
        this.commandDelegate = new POP3MessageCommandDelegate(COMMANDS) {
            @Override
            protected POP3Response handleMessageExists(POP3Session session, MessageMetaData data, POP3MessageCommandArguments args) {
                return new POP3Response(POP3Response.OK_RESPONSE, args.getMessageNumber() + " " + data.getSize());
            }
        };
    }

    /**
     * Handler method called upon receipt of a LIST command. Returns the number
     * of messages in the mailbox and its aggregate size, or optionally, the
     * number and size of a single message.
     * 
     * @param session
     *            the pop3 session
     * @param request
     *            the request to process
     */

    @Override
    @SuppressWarnings("unchecked")
    public Response onCommand(POP3Session session, Request request) {
        return metricFactory.decorateSupplierWithTimerMetric("pop3-list", () ->
            MDCBuilder.withMdc(MDCBuilder.create()
                    .addToContext(MDCBuilder.ACTION, "LIST")
                    .addToContext(MDCConstants.withSession(session))
                    .addToContext(MDCConstants.forRequest(request)),
                () -> handleMessageRequest(session, request)));
    }

    private Response handleMessageRequest(POP3Session session, Request request) {
        if (request.getArgument() != null) {
            return commandDelegate.handleMessageRequest(session, request);
        }

        if (session.getHandlerState() == POP3Session.TRANSACTION) {

            List<MessageMetaData> uidList = session.getAttachment(POP3Session.UID_LIST, State.Transaction).orElse(ImmutableList.of());
            List<String> deletedUidList = session.getAttachment(POP3Session.DELETED_UID_LIST, State.Transaction).orElse(ImmutableList.of());

            long totalSize = 0;
            List<String> validResults = new ArrayList<>();
            for (int i = 0; i < uidList.size(); i++) {
                MessageMetaData data = uidList.get(i);
                if (!deletedUidList.contains(data.getUid())) {
                    totalSize += data.getSize();
                    validResults.add((i + 1) + " " + data.getSize());
                }
            }

            POP3Response response = new POP3Response(POP3Response.OK_RESPONSE, validResults.size() + " " + totalSize);
            validResults.forEach(response::appendLine);
            response.appendLine(".");

            return response;
        } else {
            return POP3Response.ERR;
        }
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

}
