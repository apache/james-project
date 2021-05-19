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
import java.util.List;

import javax.inject.Inject;

import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandHandler;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.POP3StreamResponse;
import org.apache.james.protocols.pop3.mailbox.MessageMetaData;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * Handles RETR command
 */
public class RetrCmdHandler implements CommandHandler<POP3Session> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RetrCmdHandler.class);
    private static final Collection<String> COMMANDS = ImmutableSet.of("RETR");
    @VisibleForTesting
    static final Response SYNTAX_ERROR = new POP3Response(POP3Response.ERR_RESPONSE, "Usage: RETR [mail number]").immutable();
    private static final Response ERROR_MESSAGE_RETRIEVE = new POP3Response(POP3Response.ERR_RESPONSE, "Error while retrieving message.").immutable();

    private final MetricFactory metricFactory;

    @Inject
    public RetrCmdHandler(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
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
                () -> retr(session, request)));
    }

    private Response retr(POP3Session session, Request request) {
        LOGGER.trace("RETR command received");
        POP3Response response = null;
        String parameters = request.getArgument();
        if (session.getHandlerState() == POP3Session.TRANSACTION) {
            int num = 0;
            try {
                num = Integer.parseInt(parameters.trim());
            } catch (Exception e) {
                return SYNTAX_ERROR;
            }
            try {
                MessageMetaData data = MessageMetaDataUtils.getMetaData(session, num);

                if (data == null) {
                    StringBuilder responseBuffer = new StringBuilder(64).append("Message (").append(num).append(") does not exist.");
                    response = new POP3Response(POP3Response.ERR_RESPONSE, responseBuffer.toString());
                    return response;
                }
                List<String> deletedUidList = session.getAttachment(POP3Session.DELETED_UID_LIST, State.Transaction).orElse(ImmutableList.of());

                String uid = data.getUid();
                if (!deletedUidList.contains(uid)) {
                    InputStream content = session.getUserMailbox().getMessage(uid);

                    if (content != null) {
                        InputStream in = new CRLFTerminatedInputStream(new ExtraDotInputStream(content));
                        response = new POP3StreamResponse(POP3Response.OK_RESPONSE, "Message follows", in);
                        return response;
                    } else {
                        StringBuilder responseBuffer = new StringBuilder(64).append("Message (").append(num).append(") does not exist.");
                        response = new POP3Response(POP3Response.ERR_RESPONSE, responseBuffer.toString());
                    }
                } else {
                    StringBuilder responseBuffer = new StringBuilder(64).append("Message (").append(num).append(") already deleted.");
                    response = new POP3Response(POP3Response.ERR_RESPONSE, responseBuffer.toString());
                }
            } catch (IOException ioe) {
                return ERROR_MESSAGE_RETRIEVE;
            }
        } else {
            return POP3Response.ERR;
        }
        return response;
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

}
