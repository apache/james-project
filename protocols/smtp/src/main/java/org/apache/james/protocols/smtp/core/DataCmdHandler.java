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
package org.apache.james.protocols.smtp.core;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import jakarta.inject.Inject;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandHandler;
import org.apache.james.protocols.api.handler.ExtensibleHandler;
import org.apache.james.protocols.api.handler.LineHandler;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.smtp.MailEnvelope;
import org.apache.james.protocols.smtp.MailEnvelopeImpl;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.util.MDCBuilder;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;


/**
 * handles DATA command
 */
public class DataCmdHandler implements CommandHandler<SMTPSession>, ExtensibleHandler {

    private static final Response NO_RECIPIENT = new SMTPResponse(SMTPRetCode.BAD_SEQUENCE, DSNStatus.getStatus(DSNStatus.PERMANENT,DSNStatus.DELIVERY_OTHER) + " No recipients specified").immutable();
    private static final Response NO_SENDER = new SMTPResponse(SMTPRetCode.BAD_SEQUENCE, DSNStatus.getStatus(DSNStatus.PERMANENT,DSNStatus.DELIVERY_OTHER) + " No sender specified").immutable();
    private static final Response UNEXPECTED_ARG = new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_COMMAND_UNRECOGNIZED, DSNStatus.getStatus(DSNStatus.PERMANENT,DSNStatus.DELIVERY_INVALID_ARG) + " Unexpected argument provided with DATA command").immutable();
    private static final Response DATA_READY = new SMTPResponse(SMTPRetCode.DATA_READY, "Ok Send data ending with <CRLF>.<CRLF>").immutable();
    private static final Collection<String> COMMANDS = ImmutableSet.of("DATA");

    public static final class DataConsumerLineHandler implements LineHandler<SMTPSession> {

        @Override
        public SMTPResponse onLine(SMTPSession session, byte[] line) {
            // Discard everything until the end of DATA session
            if (line.length == 3 && line[0] == 46) {
                session.popLineHandler();
            }
            return null;
        }
    }

    public static final class DataLineFilterWrapper implements LineHandler<SMTPSession> {

        private final DataLineFilter filter;
        private final LineHandler<SMTPSession> next;
        
        public DataLineFilterWrapper(DataLineFilter filter, LineHandler<SMTPSession> next) {
            this.filter = filter;
            this.next = next;
        }

        @Override
        public Response onLine(SMTPSession session, byte[] line) {
            return filter.onLine(session, line, next);
        }
    }
   
    public static final ProtocolSession.AttachmentKey<MailEnvelope> MAILENV = ProtocolSession.AttachmentKey.of("MAILENV", MailEnvelope.class);

    private final MetricFactory metricFactory;

    @Inject
    public DataCmdHandler(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
    }

    private LineHandler<SMTPSession> lineHandler;

    /**
     * process DATA command
     */
    @Override
    public Response onCommand(SMTPSession session, Request request) {
        TimeMetric timeMetric = metricFactory.timer("SMTP-" + request.getCommand());
        session.stopDetectingCommandInjection();
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.ACTION, request.getCommand())
                     .build()) {
            String parameters = request.getArgument();
            Response response = doDATAFilter(session, parameters);

            if (response == null) {
                return doDATA(session, parameters);
            } else {
                return response;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            timeMetric.stopAndPublish();
            session.needsCommandInjectionDetection();
        }
    }


    /**
     * Handler method called upon receipt of a DATA command.
     * Reads in message data, creates header, and delivers to
     * mail server service for delivery.
     *
     * @param session SMTP session object
     * @param argument the argument passed in with the command by the SMTP client
     */
    @SuppressWarnings("unchecked")
    protected Response doDATA(SMTPSession session, String argument) {
        MaybeSender sender = session.getAttachment(SMTPSession.SENDER, ProtocolSession.State.Transaction).orElse(MaybeSender.nullSender());
        MailEnvelope env = createEnvelope(sender, session.getAttachment(SMTPSession.RCPT_LIST, ProtocolSession.State.Transaction).orElse(ImmutableList.of()));
        session.setAttachment(MAILENV, env, ProtocolSession.State.Transaction);
        session.pushLineHandler(lineHandler);
        
        return DATA_READY;
    }
    
    protected MailEnvelope createEnvelope(MaybeSender sender, List<MailAddress> recipients) {
        MailEnvelopeImpl env = new MailEnvelopeImpl();
        env.setRecipients(recipients);
        env.setSender(sender);
        return env;
    }
    
    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public List getMarkerInterfaces() {
        List classes = new LinkedList();
        classes.add(DataLineFilter.class);
        return classes;
    }


    @Override
    @SuppressWarnings("rawtypes")
    public void wireExtensions(Class interfaceName, List extension) throws WiringException {
        if (DataLineFilter.class.equals(interfaceName)) {

            LineHandler<SMTPSession> lineHandler = new DataConsumerLineHandler();
            for (int i = extension.size() - 1; i >= 0; i--) {
                lineHandler = new DataLineFilterWrapper((DataLineFilter) extension.get(i), lineHandler);
            }

            this.lineHandler = lineHandler;
        }
    }

    protected Response doDATAFilter(SMTPSession session, String argument) {
        if ((argument != null) && (argument.length() > 0)) {
            return UNEXPECTED_ARG;
        }
        if (!session.getAttachment(SMTPSession.SENDER, ProtocolSession.State.Transaction).isPresent()) {
            return NO_SENDER;
        } else if (!session.getAttachment(SMTPSession.RCPT_LIST, ProtocolSession.State.Transaction).isPresent()) {
            return NO_RECIPIENT;
        }
        return null;
    }

    protected LineHandler<SMTPSession> getLineHandler() {
        return lineHandler;
    }

}
