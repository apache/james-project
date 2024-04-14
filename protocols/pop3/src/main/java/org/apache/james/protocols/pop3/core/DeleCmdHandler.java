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

import com.google.common.collect.ImmutableSet;

/**
 * Handles DELE command
 */
public class DeleCmdHandler extends AbstractPOP3CommandHandler {
    private static final Collection<String> COMMANDS = ImmutableSet.of("DELE");

    private static final Response DELETED = new POP3Response(POP3Response.OK_RESPONSE, "Message deleted").immutable();

    private final MetricFactory metricFactory;
    private final POP3MessageCommandDelegate commandDelegate;
    
    @Inject
    public DeleCmdHandler(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
        this.commandDelegate = new POP3MessageCommandDelegate(COMMANDS) {
            @Override
            protected Response handleMessageExists(POP3Session session, MessageMetaData data, POP3MessageCommandArguments args) {
                return scheduleMessageDeletion(session, data);
            }
        };
    }

    protected Response scheduleMessageDeletion(POP3Session session, MessageMetaData data) {
        List<String> deletedUidList = session.getAttachment(POP3Session.DELETED_UID_LIST, State.Transaction)
            .orElseGet(() -> {
                ArrayList<String> uidList = new ArrayList<>();
                session.setAttachment(POP3Session.DELETED_UID_LIST, uidList, State.Transaction);
                return uidList;
            });

        deletedUidList.add(data.getUid());
        return DELETED;
    }

    /**
     * Handler method called upon receipt of a DELE command. This command
     * deletes a particular mail message from the mailbox.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Response onCommand(POP3Session session, Request request) {
        return metricFactory.decorateSupplierWithTimerMetric("pop3-dele", () ->
            MDCBuilder.withMdc(MDCBuilder.create()
                    .addToContext(MDCBuilder.ACTION, "DELE")
                    .addToContext(MDCConstants.withSession(session))
                    .addToContext(MDCConstants.forRequest(request)),
                () -> commandDelegate.handleMessageRequest(session, request)));
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

}
