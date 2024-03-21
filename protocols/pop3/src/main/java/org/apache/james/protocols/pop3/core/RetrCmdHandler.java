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
import java.io.InputStream;
import java.util.Collection;

import jakarta.inject.Inject;

import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.POP3StreamResponse;
import org.apache.james.protocols.pop3.mailbox.MessageMetaData;
import org.apache.james.util.MDCBuilder;

import com.google.common.collect.ImmutableSet;

/**
 * Handles RETR command
 */
public class RetrCmdHandler extends AbstractPOP3CommandHandler {
    private static final Collection<String> COMMANDS = ImmutableSet.of("RETR");

    private final MetricFactory metricFactory;
    private final POP3MessageCommandDelegate commandDelegate;

    @Inject
    public RetrCmdHandler(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
        this.commandDelegate = new POP3MessageCommandDelegate(COMMANDS) {
            @Override
            protected Response handleMessageExists(POP3Session session, MessageMetaData data, POP3MessageCommandArguments args) throws IOException {
                InputStream content = getMessageContent(session, data);
                InputStream in = new CRLFTerminatedInputStream(new ExtraDotInputStream(content));
                return new POP3StreamResponse(POP3Response.OK_RESPONSE, "Message follows", in);
            }
        };
    }

    /**
     * Handler method called upon receipt of a RETR command. This command
     * retrieves a particular mail message from the mailbox.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Response onCommand(POP3Session session, Request request) {
        return metricFactory.decorateSupplierWithTimerMetric("pop3-retr", () ->
            MDCBuilder.withMdc(
                MDCBuilder.create()
                    .addToContext(MDCBuilder.ACTION, "RETR")
                    .addToContext(MDCConstants.withSession(session))
                    .addToContext(MDCConstants.forRequest(request)),
                () -> commandDelegate.handleMessageRequest(session, request)));
    }
    
    protected InputStream getMessageContent(POP3Session session, MessageMetaData data) throws IOException {
        return session.getUserMailbox().getMessage(data.getUid());
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

}
